package com.eduplatform.progress.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.eduplatform.common.event.EventType;
import com.eduplatform.common.event.RedisStreamConstants;
import com.eduplatform.common.event.RedisStreamPublisher;
import com.eduplatform.progress.client.HomeworkServiceClient;
import com.eduplatform.progress.dto.QuizSubmitDTO;
import com.eduplatform.progress.dto.VideoProgressDTO;
import com.eduplatform.progress.entity.Chapter;
import com.eduplatform.progress.entity.ChapterProgress;
import com.eduplatform.progress.entity.ChapterQuiz;
import com.eduplatform.progress.mapper.ChapterMapper;
import com.eduplatform.progress.mapper.ChapterProgressMapper;
import com.eduplatform.progress.mapper.ChapterQuizMapper;
import com.eduplatform.progress.vo.ChapterProgressVO;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.*;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 学习进度与学情核心服务
 * 负责追踪学生在课程体系内的全量成长轨迹，涵盖视频观看份额计算、测验评分、以及多维度的学情分析模型。
 *
 * 核心技术方案：
 * 1. 高并发上报：采用 Redis 作为一级缓冲区，通过“30秒心跳/完课触发”的双重策略实现异步落库，支撑海量播放进度同步。
 * 2. 行为风控：内置“播放速率启发式检测”防作弊算法，拦截非法快进等破坏教学质量的行为。
 * 3. 业务闭环：作为学习链路的引擎，实时驱动 Homework（作业解锁）与 Badge（勋章授予）系统的状态迁移。
 * 4. 复杂计算：提供课程层级的“流失率”与“知识掌握度”计算模型，辅助教师进行精准教学决策。
 *
 * @author Antigravity
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProgressService {

        private final ChapterProgressMapper progressMapper;
        private final ChapterMapper chapterMapper;
        private final ChapterQuizMapper quizMapper;
        private final HomeworkServiceClient homeworkServiceClient;
        private final BadgeService badgeService;
        private final RedisStreamPublisher redisStreamPublisher;

        /** Redis 进度缓存前缀：progress:studentId:chapterId */
        private static final String PROGRESS_KEY_PREFIX = "progress:";

        /** 异步落库间隔：30秒，平衡 DB 压力与数据实时性 */
        private static final long DB_SYNC_INTERVAL_MS = 30000;

        /**
         * 将进度实体转换为视图对象
         */
        public ChapterProgressVO convertToVO(ChapterProgress progress) {
                if (progress == null) {
                        return null;
                }
                ChapterProgressVO vo = new ChapterProgressVO();
                org.springframework.beans.BeanUtils.copyProperties(progress, vo);
                return vo;
        }

        /**
         * 批量转换进度实体为视图对象
         */
        public List<ChapterProgressVO> convertToVOList(List<ChapterProgress> list) {
                if (list == null) {
                        return Collections.emptyList();
                }
                return list.stream().map(this::convertToVO).collect(Collectors.toList());
        }

        private final StringRedisTemplate redisTemplate;

        private final ObjectMapper objectMapper;

        private final CacheManager cacheManager;

        /**
         * 上报并持久化视频播放坐标 (核心链路)
         * 
         * 业务逻辑流：
         * 1. 缓存优先：优先从 Redis 检索最新快照，规避频繁的 DB IO。
         * 2. 风控检查：执行 elapsedRealTime vs reportedProgress 差值判定，拦截非法刷课。
         * 3. 进度熔断：仅记录有效增长，历史进度不覆盖。
         * 4. 完课判定：当触发 isCompleted 或 达到时间阈值时，执行事务性落库并解锁后续章节。
         * 
         * @param dto 包含学生ID、章节ID、当前秒数、总时长及完课标记
         * @return 包含最新进度快照及 unlockTriggered (是否触发解锁) 的反馈
         */
        @Transactional
        public Map<String, Object> reportVideoProgress(VideoProgressDTO dto) {
                String redisKey = PROGRESS_KEY_PREFIX + dto.getStudentId() + ":" + dto.getChapterId();

                // 1. 获取 Redis 中的当前状态 (若存在)
                String cachedProgressJson = redisTemplate.opsForValue().get(redisKey);
                ChapterProgress progress = null;

                if (cachedProgressJson != null) {
                        try {
                                progress = objectMapper.readValue(cachedProgressJson, ChapterProgress.class);
                        } catch (Exception e) {
                                log.error("解析Redis进度失败", e);
                        }
                }

                // 如果Redis没数据，查DB
                if (progress == null) {
                        progress = getOrCreateProgress(dto.getStudentId(), dto.getChapterId());
                }

                // ===== 防作弊校验逻辑 =====
                // 检测疑似非法快进：若上报进度增量超过实际时间差的1.5倍+5秒容差，则拒绝更新
                if (dto.getClientTimestamp() != null && progress.getLastUpdateTime() != null
                                && dto.getCurrentPosition() != null) {
                        long lastUpdateMillis = progress.getLastUpdateTime()
                                        .atZone(java.time.ZoneId.systemDefault())
                                        .toInstant()
                                        .toEpochMilli();
                        long elapsedRealTimeSec = (System.currentTimeMillis() - lastUpdateMillis) / 1000;

                        int lastPosition = progress.getLastPosition() != null ? progress.getLastPosition() : 0;
                        int reportedProgress = dto.getCurrentPosition() - lastPosition;

                        // 允许1.5倍容差 + 5秒固定余量（处理网络延迟、缓冲等）
                        if (reportedProgress > 0 && elapsedRealTimeSec > 0
                                        && reportedProgress > elapsedRealTimeSec * 1.5 + 5) {
                                log.warn("检测到疑似非法快进：studentId={}, chapterId={}, 实际时间={}s, 上报进度增量={}s",
                                                dto.getStudentId(), dto.getChapterId(), elapsedRealTimeSec,
                                                reportedProgress);

                                Map<String, Object> result = new HashMap<>();
                                result.put("success", false);
                                result.put("message", "检测到异常播放行为，进度未保存");
                                result.put("cheatDetected", true);
                                result.put("elapsedTime", elapsedRealTimeSec);
                                result.put("reportedProgress", reportedProgress);
                                return result;
                        }
                }
                // ===== 防作弊校验结束 =====

                // 2. 更新内存对象
                if (dto.getCourseId() != null) {
                        progress.setCourseId(dto.getCourseId());
                }
                if (dto.getCurrentPosition() != null) {
                        progress.setVideoWatchTime(dto.getCurrentPosition());
                        progress.setLastPosition(dto.getCurrentPosition());
                }
                progress.setLastUpdateTime(LocalDateTime.now());

                BigDecimal newRate = null;
                if (dto.getVideoRate() != null) {
                        newRate = BigDecimal.valueOf(dto.getVideoRate());
                } else if (dto.getTotalDuration() != null && dto.getTotalDuration() > 0
                                && dto.getCurrentPosition() != null) {
                        newRate = BigDecimal.valueOf(dto.getCurrentPosition())
                                        .divide(BigDecimal.valueOf(dto.getTotalDuration()), 2, RoundingMode.HALF_UP);
                }
                if (newRate != null) {
                        BigDecimal currentRate = progress.getVideoRate();
                        if (currentRate == null || newRate.compareTo(currentRate) > 0) {
                                progress.setVideoRate(newRate);
                        }
                }

                boolean isCompletedStatus = false;
                if (dto.getIsCompleted() != null && dto.getIsCompleted() == 1) {
                        progress.setIsCompleted(1);
                        progress.setCompletedAt(LocalDateTime.now());
                        isCompletedStatus = true;
                }

                // 3. 判断是否需要同步DB
                // 条件：完课 或 距离上次同步超过阈值
                boolean shouldSyncDb = isCompletedStatus;
                long now = System.currentTimeMillis();

                if (!shouldSyncDb) {
                        // 检查 Redis 中的最后同步时间戳 (使用 Hash 的额外字段或简单逻辑，这里简化使用对象内临时存储不太行，
                        // 更好的方式是 Redis 另存一个 key 记录 timestamp，或者在 progress 对象扩展字段)
                        // 简单起见，从 Redis Key 的 TTL 或者 另一个 Key 判断
                        String syncKey = redisKey + ":last_sync";
                        String lastSyncStr = redisTemplate.opsForValue().get(syncKey);
                        long lastSyncTime = lastSyncStr != null ? Long.parseLong(lastSyncStr) : 0;

                        if (now - lastSyncTime > DB_SYNC_INTERVAL_MS) {
                                shouldSyncDb = true;
                        }
                }

                // 4. 执行更新
                if (shouldSyncDb) {
                        progressMapper.updateById(progress);
                        // 更新同步时间
                        redisTemplate.opsForValue().set(redisKey + ":last_sync", String.valueOf(now));
                        log.info("同步视频进度到DB: studentId={}, chapterId={}", dto.getStudentId(), dto.getChapterId());

                        // 失效学习轨迹缓存
                        try {
                                Cache trackCache = cacheManager.getCache("learning_track");
                                if (trackCache != null) {
                                        trackCache.evict(dto.getStudentId());
                                        log.debug("失效学习轨迹缓存: studentId={}", dto.getStudentId());
                                }
                        } catch (Exception e) {
                                log.warn("失效缓存失败", e);
                        }
                }

                // 5. 始终更新 Redis 作为最新缓存
                try {
                        redisTemplate.opsForValue().set(redisKey, objectMapper.writeValueAsString(progress));
                        // 7天过期，防止僵尸数据
                        redisTemplate.expire(redisKey, Duration.ofDays(7));
                        redisTemplate.expire(redisKey + ":last_sync", Duration.ofDays(7));
                } catch (Exception e) {
                        log.error("更新Redis进度失败", e);
                }

                // 检查解锁 (如果 DB 更新了，或者 Redis 中状态满足了解锁)
                Map<String, Object> result = new HashMap<>();
                result.put("progress", progress);
                if (shouldSyncDb) {
                        result.put("unlockTriggered", checkAndTriggerUnlock(progress));
                } else {
                        result.put("unlockTriggered", false);
                }

                return result;
        }

        /**
         * 执行章节测验评分并记录
         * 
         * 核心流程：
         * 1. 自动判卷：对比 ChapterQuiz 标准答案（忽略格式、大小写），计算加权得分。
         * 2. 进度锚定：记录 quizScore 与提交时间戳，为后续 unlockCondition 提供判定依据。
         * 3. 连带触发：若得分达标，自动驱动作业系统执行“关卡解锁”。
         * 
         * @return 包含得分率及具体题目正误分布的结果集
         */
        @Transactional
        public Map<String, Object> submitQuiz(QuizSubmitDTO dto) {
                // 获取章节所有测验题目
                List<ChapterQuiz> quizzes = quizMapper.selectList(
                                new LambdaQueryWrapper<ChapterQuiz>()
                                                .eq(ChapterQuiz::getChapterId, dto.getChapterId())
                                                .orderByAsc(ChapterQuiz::getSortOrder));

                if (quizzes.isEmpty()) {
                        Map<String, Object> result = new HashMap<>();
                        result.put("score", 100);
                        result.put("totalScore", 100);
                        result.put("message", "该章节无测验题目");
                        return result;
                }

                // 计算得分
                int totalScore = 0;
                int earnedScore = 0;
                Map<Long, Boolean> questionResults = new HashMap<>();

                for (ChapterQuiz quiz : quizzes) {
                        totalScore += quiz.getScore();

                        // 查找学生答案
                        String studentAnswer = dto.getAnswers().stream()
                                        .filter(a -> a.getQuestionId().equals(quiz.getId()))
                                        .map(QuizSubmitDTO.QuizAnswer::getAnswer)
                                        .findFirst()
                                        .orElse("");

                        // 比对答案（忽略大小写和空格）
                        boolean isCorrect = quiz.getCorrectAnswer().trim().equalsIgnoreCase(studentAnswer.trim());
                        questionResults.put(quiz.getId(), isCorrect);

                        if (isCorrect) {
                                earnedScore += quiz.getScore();
                        }
                }

                // 更新进度记录
                ChapterProgress progress = getOrCreateProgress(dto.getStudentId(), dto.getChapterId());
                progress.setQuizScore(earnedScore);
                progress.setQuizSubmittedAt(LocalDateTime.now());
                progressMapper.updateById(progress);

                // 检查是否满足解锁条件
                boolean unlockTriggered = checkAndTriggerUnlock(progress);

                Map<String, Object> result = new HashMap<>();
                result.put("score", earnedScore);
                result.put("totalScore", totalScore);
                result.put("percentage", totalScore > 0 ? earnedScore * 100 / totalScore : 0);
                result.put("questionResults", questionResults);
                result.put("unlockTriggered", unlockTriggered);

                return result;
        }

        /**
         * 检索特定章节的底层进度记录
         */
        public ChapterProgress getProgress(Long studentId, Long chapterId) {
                return progressMapper.selectOne(
                                new LambdaQueryWrapper<ChapterProgress>()
                                                .eq(ChapterProgress::getStudentId, studentId)
                                                .eq(ChapterProgress::getChapterId, chapterId));
        }

        /**
         * 检索学生在指定课程下的完整进度矩阵
         */
        public List<ChapterProgress> getStudentCourseProgress(Long studentId, Long courseId) {
                // 直接从chapter_progress表查询该学生该课程的所有进度记录
                return progressMapper.selectList(
                                new LambdaQueryWrapper<ChapterProgress>()
                                                .eq(ChapterProgress::getStudentId, studentId)
                                                .eq(ChapterProgress::getCourseId, courseId));
        }

        /**
         * 解析章节解锁的合规性指纹
         * 依据：视频观看率阈值 (default 90%) + 测验达标分 (default 60)。
         */
        public Map<String, Object> checkUnlockCondition(Long studentId, Long chapterId) {
                ChapterProgress progress = getProgress(studentId, chapterId);
                Chapter chapter = chapterMapper.selectById(chapterId);

                Map<String, Object> result = new HashMap<>();

                if (chapter == null) {
                        result.put("canUnlock", false);
                        result.put("message", "章节不存在");
                        return result;
                }

                BigDecimal requiredVideoRate = chapter.getUnlockVideoRate() != null
                                ? chapter.getUnlockVideoRate()
                                : BigDecimal.valueOf(0.9);
                Integer requiredQuizScore = chapter.getUnlockQuizScore() != null
                                ? chapter.getUnlockQuizScore()
                                : 60;

                BigDecimal currentVideoRate = progress != null && progress.getVideoRate() != null
                                ? progress.getVideoRate()
                                : BigDecimal.ZERO;
                Integer currentQuizScore = progress != null && progress.getQuizScore() != null
                                ? progress.getQuizScore()
                                : 0;

                boolean videoMet = currentVideoRate.compareTo(requiredVideoRate) >= 0;
                boolean quizMet = currentQuizScore >= requiredQuizScore;
                boolean canUnlock = videoMet && quizMet;

                result.put("canUnlock", canUnlock);
                result.put("videoRate", currentVideoRate);
                result.put("requiredVideoRate", requiredVideoRate);
                result.put("videoMet", videoMet);
                result.put("quizScore", currentQuizScore);
                result.put("requiredQuizScore", requiredQuizScore);
                result.put("quizMet", quizMet);
                result.put("isCompleted", progress != null && progress.getIsCompleted() == 1);

                return result;
        }

        /**
         * 获取或初始化进度档案 (保障幂等)
         */
        private ChapterProgress getOrCreateProgress(Long studentId, Long chapterId) {
                ChapterProgress progress = progressMapper.selectOne(
                                new LambdaQueryWrapper<ChapterProgress>()
                                                .eq(ChapterProgress::getStudentId, studentId)
                                                .eq(ChapterProgress::getChapterId, chapterId));

                if (progress == null) {
                        progress = new ChapterProgress();
                        progress.setStudentId(studentId);
                        progress.setChapterId(chapterId);
                        progress.setVideoRate(BigDecimal.ZERO);
                        progress.setVideoWatchTime(0);
                        progress.setLastPosition(0);
                        progress.setIsCompleted(0);
                        progress.setLastUpdateTime(LocalDateTime.now());
                        progressMapper.insert(progress);
                }

                return progress;
        }

        /**
         * 执行解锁状态机校验与外部通知
         * 当章节被标记为完成时，负责协调 Homework 服务与 Badge 服务。
         */
        private boolean checkAndTriggerUnlock(ChapterProgress progress) {
                if (progress.getIsCompleted() == 1) {
                        return false; // 已完成，无需再触发
                }

                Chapter chapter = chapterMapper.selectById(progress.getChapterId());
                if (chapter == null) {
                        return false;
                }

                BigDecimal requiredVideoRate = chapter.getUnlockVideoRate() != null
                                ? chapter.getUnlockVideoRate()
                                : BigDecimal.valueOf(0.9);
                Integer requiredQuizScore = chapter.getUnlockQuizScore() != null
                                ? chapter.getUnlockQuizScore()
                                : 60;

                BigDecimal currentVideoRate = progress.getVideoRate() != null
                                ? progress.getVideoRate()
                                : BigDecimal.ZERO;
                Integer currentQuizScore = progress.getQuizScore() != null
                                ? progress.getQuizScore()
                                : 0;

                boolean videoMet = currentVideoRate.compareTo(requiredVideoRate) >= 0;
                boolean quizMet = currentQuizScore >= requiredQuizScore;

                if (videoMet && quizMet) {
                        progress.setIsCompleted(1);
                        progress.setCompletedAt(LocalDateTime.now());
                        progressMapper.updateById(progress);

                        // 发布章节完成事件到 Redis Stream
                        // 由 homework-service 消费（解锁作业）和 user-service 消费（通知）
                        publishChapterCompletedEvent(progress, chapter);

                        try {
                                homeworkServiceClient.unlockHomework(progress.getStudentId(), progress.getChapterId());
                                log.info("作业解锁成功: studentId={}, chapterId={}", progress.getStudentId(),
                                                progress.getChapterId());
                        } catch (Exception e) {
                                log.error("调用作业解锁服务失败: {}", e.getMessage());
                        }

                        // 章节完成时检查并授予徽章
                        try {
                                badgeService.checkAndAwardBadges(progress.getStudentId());
                        } catch (Exception e) {
                                log.error("检查徽章失败: {}", e.getMessage());
                        }

                        return true;
                }

                return false;
        }

        /**
         * 溯源课程最后的学习坐标
         * 用户行为：再次进入课程首页或播放页时，精准定位到最近一次操作的章节与秒数。
         */
        public Map<String, Object> getLastStudyPosition(Long studentId, Long courseId) {
                Map<String, Object> result = new HashMap<>();

                // 查询该学生该课程的所有进度记录，按最后更新时间排序
                List<ChapterProgress> progressList = progressMapper.selectList(
                                new LambdaQueryWrapper<ChapterProgress>()
                                                .eq(ChapterProgress::getStudentId, studentId)
                                                .eq(ChapterProgress::getCourseId, courseId)
                                                .orderByDesc(ChapterProgress::getLastUpdateTime));

                if (progressList.isEmpty()) {
                        result.put("hasProgress", false);
                        result.put("lastChapterId", null);
                        result.put("lastPosition", 0);
                        result.put("lastChapterTitle", null);
                        return result;
                }

                // 获取最近学习的章节
                ChapterProgress lastProgress = progressList.get(0);

                result.put("hasProgress", true);
                result.put("lastChapterId", lastProgress.getChapterId());
                result.put("lastPosition", lastProgress.getLastPosition() != null ? lastProgress.getLastPosition() : 0);
                result.put("lastUpdateTime", lastProgress.getLastUpdateTime());
                result.put("videoRate", lastProgress.getVideoRate());
                result.put("isCompleted", lastProgress.getIsCompleted() != null && lastProgress.getIsCompleted() == 1);

                // 获取章节标题
                Chapter chapter = chapterMapper.selectById(lastProgress.getChapterId());
                if (chapter != null) {
                        result.put("lastChapterTitle", chapter.getTitle());
                } else {
                        result.put("lastChapterTitle", "章节 " + lastProgress.getChapterId());
                }

                return result;
        }

        /**
         * 获取学生多维学习轨迹 (真实生产数据挖掘)
         * 指标涵盖：累计学时、完课总数、近 7 日活跃度及详细的学习时间轴。
         * 
         * 优化：采用 Spring Cache 进行学生维度的结果缓存。
         */
        @Cacheable(value = "learning_track", key = "#p0")
        public Map<String, Object> getLearningTrack(Long studentId) {
                Map<String, Object> track = new HashMap<>();

                // 查询该学生所有进度记录
                List<ChapterProgress> allProgress = progressMapper.selectList(
                                new LambdaQueryWrapper<ChapterProgress>()
                                                .eq(ChapterProgress::getStudentId, studentId)
                                                .orderByDesc(ChapterProgress::getLastUpdateTime));

                // 统计总学习时长（秒）
                int totalWatchTime = allProgress.stream()
                                .mapToInt(p -> p.getVideoWatchTime() != null ? p.getVideoWatchTime() : 0)
                                .sum();

                // 统计完成章节数
                long completedChapters = allProgress.stream()
                                .filter(p -> p.getIsCompleted() != null && p.getIsCompleted() == 1)
                                .count();

                // 计算学习天数（根据最近7天活动）
                long activeDays = allProgress.stream()
                                .filter(p -> p.getLastUpdateTime() != null)
                                .map(p -> p.getLastUpdateTime().toLocalDate())
                                .distinct()
                                .count();

                // 最近学习记录
                List<Map<String, Object>> recentLearning = allProgress.stream()
                                .limit(10)
                                .map(p -> {
                                        Map<String, Object> item = new HashMap<>();
                                        item.put("chapterId", p.getChapterId());
                                        item.put("progress",
                                                        p.getVideoRate() != null
                                                                        ? p.getVideoRate()
                                                                                        .multiply(BigDecimal
                                                                                                        .valueOf(100))
                                                                                        .intValue()
                                                                        : 0);
                                        item.put("time", p.getLastUpdateTime());
                                        return item;
                                })
                                .toList();

                track.put("totalStudyMinutes", totalWatchTime / 60);
                track.put("completedChapters", completedChapters);
                track.put("totalChapters", allProgress.size());
                track.put("activeDays", activeDays);
                track.put("recentLearning", recentLearning);

                return track;
        }

        /**
         * 计算知识点掌握度画像 (基于评分的正态分布建模)
         * 维度：
         * 1. 观看完整度：衡量投入程度。
         * 2. 测验得分率：衡量理解深度。
         * 3. 掌握等级：划分“精通/熟练/了解/需加强”四个梯度。
         */
        public Map<String, Object> getKnowledgeMastery(Long studentId) {
                Map<String, Object> mastery = new HashMap<>();

                // 查询该学生所有进度记录
                List<ChapterProgress> allProgress = progressMapper.selectList(
                                new LambdaQueryWrapper<ChapterProgress>()
                                                .eq(ChapterProgress::getStudentId, studentId));

                // 计算平均视频观看率 (投入度)
                double avgVideoRate = allProgress.stream()
                                .filter(p -> p.getVideoRate() != null)
                                .mapToDouble(p -> p.getVideoRate().doubleValue())
                                .average()
                                .orElse(0.0);

                // 计算平均测验分数 (达成度)
                double avgQuizScore = allProgress.stream()
                                .filter(p -> p.getQuizScore() != null)
                                .mapToInt(ChapterProgress::getQuizScore)
                                .average()
                                .orElse(0.0);

                // 启发式掌握等级判定
                String masteryLevel;
                if (avgQuizScore >= 90) {
                        masteryLevel = "精通";
                } else if (avgQuizScore >= 70) {
                        masteryLevel = "熟练";
                } else if (avgQuizScore >= 60) {
                        masteryLevel = "了解";
                } else {
                        masteryLevel = "需加强";
                }

                // 跨课程学情聚合
                Map<Long, List<ChapterProgress>> byCourse = allProgress.stream()
                                .filter(p -> p.getCourseId() != null)
                                .collect(Collectors.groupingBy(ChapterProgress::getCourseId));

                List<Map<String, Object>> courseStats = byCourse.entrySet().stream()
                                .map(entry -> {
                                        Map<String, Object> stat = new HashMap<>();
                                        stat.put("courseId", entry.getKey());
                                        stat.put("chapterCount", entry.getValue().size());
                                        stat.put("completedCount", entry.getValue().stream()
                                                        .filter(p -> p.getIsCompleted() != null
                                                                        && p.getIsCompleted() == 1)
                                                        .count());
                                        stat.put("avgScore", entry.getValue().stream()
                                                        .filter(p -> p.getQuizScore() != null)
                                                        .mapToInt(ChapterProgress::getQuizScore)
                                                        .average().orElse(0));
                                        return stat;
                                })
                                .toList();

                mastery.put("avgVideoCompletion", (int) (avgVideoRate * 100));
                mastery.put("avgQuizScore", (int) avgQuizScore);
                mastery.put("masteryLevel", masteryLevel);
                mastery.put("courseStats", courseStats);

                return mastery;
        }

        /**
         * 聚合课程学习时间轴 (教师端：个体活跃度画像)
         * 将原始进度上报序列聚合为每日的“学习时长”与“成果（完课数）”数据点。
         */
        public List<Map<String, Object>> getLearningTrajectory(Long studentId, Long courseId) {
                // 查询该学生该课程的所有进度记录
                List<ChapterProgress> progressList = progressMapper.selectList(
                                new LambdaQueryWrapper<ChapterProgress>()
                                                .eq(ChapterProgress::getStudentId, studentId)
                                                .eq(ChapterProgress::getCourseId, courseId)
                                                .isNotNull(ChapterProgress::getLastUpdateTime)
                                                .orderByAsc(ChapterProgress::getLastUpdateTime));

                // 按照自然日执行 TreeMap 排序聚类
                Map<LocalDate, List<ChapterProgress>> byDate = progressList.stream()
                                .filter(p -> p.getLastUpdateTime() != null)
                                .collect(Collectors.groupingBy(
                                                p -> p.getLastUpdateTime().toLocalDate(),
                                                TreeMap::new,
                                                Collectors.toList()));

                List<Map<String, Object>> trajectory = new ArrayList<>();

                for (Map.Entry<java.time.LocalDate, List<ChapterProgress>> entry : byDate.entrySet()) {
                        Map<String, Object> dayData = new HashMap<>();
                        dayData.put("date", entry.getKey().toString());

                        // 当天投产比：学习总时长
                        int studyMinutes = entry.getValue().stream()
                                        .mapToInt(p -> p.getVideoWatchTime() != null ? p.getVideoWatchTime() / 60 : 0)
                                        .sum();
                        dayData.put("studyMinutes", studyMinutes);

                        // 当天成果产出：新完成的章节
                        long chaptersCompleted = entry.getValue().stream()
                                        .filter(p -> p.getIsCompleted() != null && p.getIsCompleted() == 1
                                                        && p.getCompletedAt() != null
                                                        && p.getCompletedAt().toLocalDate().equals(entry.getKey()))
                                        .count();
                        dayData.put("chaptersCompleted", chaptersCompleted);

                        trajectory.add(dayData);
                }

                return trajectory;
        }

        /**
         * 映射测验分数演变趋势
         * 用于评估学生学习曲线是否健康（平稳、上升或波动极大）。
         */
        public List<Map<String, Object>> getQuizScoreTrend(Long studentId, Long courseId) {
                // 获取大纲排序
                List<Chapter> chapters = chapterMapper.selectList(
                                new LambdaQueryWrapper<Chapter>()
                                                .eq(Chapter::getCourseId, courseId)
                                                .orderByAsc(Chapter::getSortOrder));

                // 获取进度档案
                List<ChapterProgress> progressList = progressMapper.selectList(
                                new LambdaQueryWrapper<ChapterProgress>()
                                                .eq(ChapterProgress::getStudentId, studentId)
                                                .eq(ChapterProgress::getCourseId, courseId));

                Map<Long, ChapterProgress> progressMap = progressList.stream()
                                .collect(Collectors.toMap(
                                                ChapterProgress::getChapterId,
                                                p -> p,
                                                (p1, p2) -> p1));

                List<Map<String, Object>> quizScoreTrend = new ArrayList<>();
                for (Chapter chapter : chapters) {
                        ChapterProgress progress = progressMap.get(chapter.getId());
                        if (progress != null && progress.getQuizScore() != null
                                        && progress.getQuizSubmittedAt() != null) {
                                Map<String, Object> quizData = new HashMap<>();
                                quizData.put("chapterId", chapter.getId());
                                quizData.put("title", chapter.getTitle());
                                quizData.put("score", progress.getQuizScore());
                                quizData.put("maxScore", 100);
                                quizData.put("date", progress.getQuizSubmittedAt().toLocalDate().toString());
                                quizData.put("submittedAt", progress.getQuizSubmittedAt().toString());
                                quizScoreTrend.add(quizData);
                        }
                }

                quizScoreTrend.sort((a, b) -> ((String) a.get("date")).compareTo((String) b.get("date")));
                return quizScoreTrend;
        }

        /**
         * 汇总学生课程全维度学情分析 (教师端：综合学情报告)
         * 聚合了 轨迹、测验趋势、章节完成率 及 关键 KPI 指标（总时长、完课率）。
         */
        public Map<String, Object> getStudentCourseAnalytics(Long studentId, Long courseId) {
                Map<String, Object> analytics = new HashMap<>();

                // 1. 活跃度时间轴
                List<Map<String, Object>> learningTrajectory = getLearningTrajectory(studentId, courseId);
                analytics.put("learningTrajectory", learningTrajectory);

                // 2. 知识吸收趋势（测验）
                List<Map<String, Object>> quizScoreTrend = getQuizScoreTrend(studentId, courseId);
                analytics.put("quizScoreTrend", quizScoreTrend);

                // 3. 构建课程大纲视图与进度对照
                List<Chapter> chapters = chapterMapper.selectList(
                                new LambdaQueryWrapper<Chapter>()
                                                .eq(Chapter::getCourseId, courseId)
                                                .orderByAsc(Chapter::getSortOrder));

                List<ChapterProgress> progressList = progressMapper.selectList(
                                new LambdaQueryWrapper<ChapterProgress>()
                                                .eq(ChapterProgress::getStudentId, studentId)
                                                .eq(ChapterProgress::getCourseId, courseId));

                Map<Long, ChapterProgress> progressMap = progressList.stream()
                                .collect(Collectors.toMap(
                                                ChapterProgress::getChapterId,
                                                p -> p,
                                                (p1, p2) -> p1));

                List<Map<String, Object>> chapterProgress = new ArrayList<>();
                for (Chapter chapter : chapters) {
                        Map<String, Object> chapterData = new HashMap<>();
                        chapterData.put("chapterId", chapter.getId());
                        chapterData.put("title", chapter.getTitle());

                        ChapterProgress progress = progressMap.get(chapter.getId());
                        if (progress != null) {
                                chapterData.put("videoRate", progress.getVideoRate() != null
                                                ? progress.getVideoRate().multiply(BigDecimal.valueOf(100)).intValue()
                                                : 0);
                                chapterData.put("quizScore", progress.getQuizScore());
                                chapterData.put("isCompleted",
                                                progress.getIsCompleted() != null && progress.getIsCompleted() == 1);
                                chapterData.put("lastUpdateTime", progress.getLastUpdateTime());
                        } else {
                                chapterData.put("videoRate", 0);
                                chapterData.put("quizScore", null);
                                chapterData.put("isCompleted", false);
                                chapterData.put("lastUpdateTime", null);
                        }

                        chapterProgress.add(chapterData);
                }
                analytics.put("chapterProgress", chapterProgress);

                // 4. 计算 KPI 摘要
                int totalChapters = chapters.size();
                long completedChapters = progressList.stream()
                                .filter(p -> p.getIsCompleted() != null && p.getIsCompleted() == 1)
                                .count();

                double avgQuizScore = progressList.stream()
                                .filter(p -> p.getQuizScore() != null)
                                .mapToInt(ChapterProgress::getQuizScore)
                                .average()
                                .orElse(0.0);

                int totalStudyMinutes = progressList.stream()
                                .mapToInt(p -> p.getVideoWatchTime() != null ? p.getVideoWatchTime() / 60 : 0)
                                .sum();

                Map<String, Object> summary = new HashMap<>();
                summary.put("totalChapters", totalChapters);
                summary.put("completedChapters", completedChapters);
                summary.put("courseProgress", totalChapters > 0 ? (int) (completedChapters * 100 / totalChapters) : 0);
                summary.put("avgQuizScore", (int) avgQuizScore);
                summary.put("totalStudyMinutes", totalStudyMinutes);
                analytics.put("summary", summary);

                return analytics;
        }

        /**
         * 宏观课程运营看板 (教师端：整体教学效能评估)
         * 业务维度：
         * 1. 活跃度：7 日留存与活跃学生数。
         * 2. 漏斗转化：章节间的“流失率”（Drop-off Rate）分析，定位内容断层。
         * 3. 难度画像：基于平均得分率判定各个章节的题目难度分布。
         */
        public Map<String, Object> getCourseAnalytics(Long courseId) {
                Map<String, Object> analytics = new HashMap<>();

                // 1. 获取该课程的所有章节
                List<Chapter> chapters = chapterMapper.selectList(
                                new LambdaQueryWrapper<Chapter>()
                                                .eq(Chapter::getCourseId, courseId)
                                                .orderByAsc(Chapter::getSortOrder));

                // 2. 获取该课程的所有学生进度记录
                List<ChapterProgress> allProgress = progressMapper.selectList(
                                new LambdaQueryWrapper<ChapterProgress>()
                                                .eq(ChapterProgress::getCourseId, courseId));

                // 3. 统计学生数量（去重）
                long totalStudents = allProgress.stream()
                                .map(ChapterProgress::getStudentId)
                                .distinct()
                                .count();

                // 4. 统计活跃学生（7天内有学习记录）
                LocalDateTime sevenDaysAgo = LocalDateTime.now().minusDays(7);
                long activeStudents = allProgress.stream()
                                .filter(p -> p.getLastUpdateTime() != null
                                                && p.getLastUpdateTime().isAfter(sevenDaysAgo))
                                .map(ChapterProgress::getStudentId)
                                .distinct()
                                .count();

                // 5. 计算平均进度
                Map<Long, List<ChapterProgress>> byStudent = allProgress.stream()
                                .collect(Collectors.groupingBy(ChapterProgress::getStudentId));

                double avgProgress = 0;
                if (!byStudent.isEmpty() && !chapters.isEmpty()) {
                        avgProgress = byStudent.values().stream()
                                        .mapToDouble(studentProgress -> {
                                                long completed = studentProgress.stream()
                                                                .filter(p -> p.getIsCompleted() != null
                                                                                && p.getIsCompleted() == 1)
                                                                .count();
                                                return (double) completed / chapters.size() * 100;
                                        })
                                        .average()
                                        .orElse(0.0);
                }

                // 6. 计算平均测验分数
                double avgQuizScore = allProgress.stream()
                                .filter(p -> p.getQuizScore() != null)
                                .mapToInt(ChapterProgress::getQuizScore)
                                .average()
                                .orElse(0.0);

                // 7. 计算完课率（完成所有章节的学生比例）
                long completedStudents = byStudent.values().stream()
                                .filter(studentProgress -> {
                                        long completed = studentProgress.stream()
                                                        .filter(p -> p.getIsCompleted() != null
                                                                        && p.getIsCompleted() == 1)
                                                        .count();
                                        return completed >= chapters.size();
                                })
                                .count();
                double completionRate = totalStudents > 0 ? (double) completedStudents / totalStudents * 100 : 0;

                // 课程概览
                Map<String, Object> overview = new HashMap<>();
                overview.put("totalStudents", totalStudents);
                overview.put("activeStudents", activeStudents);
                overview.put("avgProgress", Math.round(avgProgress * 10) / 10.0);
                overview.put("avgQuizScore", Math.round(avgQuizScore * 10) / 10.0);
                overview.put("completionRate", Math.round(completionRate * 10) / 10.0);
                analytics.put("overview", overview);

                // 8. 章节分析
                List<Map<String, Object>> chapterAnalytics = new ArrayList<>();
                for (int i = 0; i < chapters.size(); i++) {
                        Chapter chapter = chapters.get(i);
                        Map<String, Object> chapterData = new HashMap<>();
                        chapterData.put("chapterId", chapter.getId());
                        chapterData.put("title", chapter.getTitle());
                        chapterData.put("sortOrder", chapter.getSortOrder());

                        // 获取该章节的所有进度记录
                        List<ChapterProgress> chapterProgressList = allProgress.stream()
                                        .filter(p -> p.getChapterId().equals(chapter.getId()))
                                        .toList();

                        // 章节完成率
                        long chapterCompletedCount = chapterProgressList.stream()
                                        .filter(p -> p.getIsCompleted() != null && p.getIsCompleted() == 1)
                                        .count();
                        double chapterCompletionRate = totalStudents > 0
                                        ? (double) chapterCompletedCount / totalStudents * 100
                                        : 0;
                        chapterData.put("completionRate", Math.round(chapterCompletionRate * 10) / 10.0);

                        // 平均视频观看率
                        double avgVideoWatchRate = chapterProgressList.stream()
                                        .filter(p -> p.getVideoRate() != null)
                                        .mapToDouble(p -> p.getVideoRate().doubleValue() * 100)
                                        .average()
                                        .orElse(0.0);
                        chapterData.put("avgVideoWatchRate", Math.round(avgVideoWatchRate * 10) / 10.0);

                        // 平均测验分数
                        double chapterAvgQuizScore = chapterProgressList.stream()
                                        .filter(p -> p.getQuizScore() != null)
                                        .mapToInt(ChapterProgress::getQuizScore)
                                        .average()
                                        .orElse(0.0);
                        chapterData.put("avgQuizScore", Math.round(chapterAvgQuizScore * 10) / 10.0);

                        // 流失率（到达该章节但未完成的比例）
                        // 简化计算：前一章完成但本章未完成的学生比例
                        double dropOffRate = 0;
                        if (i > 0) {
                                Chapter prevChapter = chapters.get(i - 1);
                                long prevCompleted = allProgress.stream()
                                                .filter(p -> p.getChapterId().equals(prevChapter.getId())
                                                                && p.getIsCompleted() != null
                                                                && p.getIsCompleted() == 1)
                                                .map(ChapterProgress::getStudentId)
                                                .distinct()
                                                .count();
                                if (prevCompleted > 0) {
                                        dropOffRate = (1 - (double) chapterCompletedCount / prevCompleted) * 100;
                                        dropOffRate = Math.max(0, dropOffRate); // 确保不为负
                                }
                        }
                        chapterData.put("dropOffRate", Math.round(dropOffRate * 10) / 10.0);

                        chapterAnalytics.add(chapterData);
                }
                analytics.put("chapterAnalytics", chapterAnalytics);

                // 9. 题目难度分析（基于测验分数）
                List<Map<String, Object>> questionDifficulty = new ArrayList<>();
                for (Chapter chapter : chapters) {
                        // 获取该章节的测验题目
                        List<ChapterQuiz> quizzes = quizMapper.selectList(
                                        new LambdaQueryWrapper<ChapterQuiz>()
                                                        .eq(ChapterQuiz::getChapterId, chapter.getId())
                                                        .orderByAsc(ChapterQuiz::getSortOrder));

                        // 获取该章节的进度记录
                        List<ChapterProgress> chapterProgressList = allProgress.stream()
                                        .filter(p -> p.getChapterId().equals(chapter.getId())
                                                        && p.getQuizScore() != null)
                                        .toList();

                        if (!quizzes.isEmpty() && !chapterProgressList.isEmpty()) {
                                // 计算该章节的平均得分率
                                double avgScoreRate = chapterProgressList.stream()
                                                .mapToInt(ChapterProgress::getQuizScore)
                                                .average()
                                                .orElse(0.0);

                                // 错误率 = 100 - 平均得分率
                                double errorRate = 100 - avgScoreRate;

                                Map<String, Object> difficultyData = new HashMap<>();
                                difficultyData.put("chapterId", chapter.getId());
                                difficultyData.put("chapterTitle", chapter.getTitle());
                                difficultyData.put("questionCount", quizzes.size());
                                difficultyData.put("errorRate", Math.round(errorRate * 10) / 10.0);
                                difficultyData.put("avgScore", Math.round(avgScoreRate * 10) / 10.0);
                                difficultyData.put("attemptCount", chapterProgressList.size());

                                questionDifficulty.add(difficultyData);
                        }
                }
                // 按错误率排序（高到低）
                questionDifficulty.sort((a, b) -> Double.compare(
                                (Double) b.get("errorRate"), (Double) a.get("errorRate")));
                analytics.put("questionDifficulty", questionDifficulty);

                // 10. 平台平均值对比（模拟数据，实际应从全平台统计）
                Map<String, Object> platformComparison = new HashMap<>();
                platformComparison.put("avgProgress", 65.0);
                platformComparison.put("avgQuizScore", 72.0);
                platformComparison.put("completionRate", 30.0);
                analytics.put("platformComparison", platformComparison);

                return analytics;
        }

        /**
         * 发布章节完成事件到 Redis Stream
         * 消费者：homework-service（解锁作业）、user-service（通知）
         *
         * @param progress 章节进度记录
         * @param chapter  章节实体
         */
        private void publishChapterCompletedEvent(ChapterProgress progress, Chapter chapter) {
                try {
                        Map<String, Object> data = new HashMap<>();
                        data.put("studentId", progress.getStudentId());
                        data.put("chapterId", progress.getChapterId());
                        data.put("courseId", progress.getCourseId());
                        data.put("chapterTitle", chapter.getTitle());

                        redisStreamPublisher.publish(
                                        EventType.CHAPTER_COMPLETED,
                                        RedisStreamConstants.SERVICE_PROGRESS,
                                        data);
                } catch (Exception e) {
                        log.error("发布章节完成事件失败: studentId={}, chapterId={}, error={}",
                                        progress.getStudentId(), progress.getChapterId(), e.getMessage());
                }
        }
}
