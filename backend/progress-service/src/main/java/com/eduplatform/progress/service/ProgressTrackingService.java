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
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 学习进度写模型服务。
 * 说明：承接视频进度上报、测验提交与章节完成联动，降低 ProgressService 的流程耦合。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProgressTrackingService {

    private final ChapterProgressMapper progressMapper;
    private final ChapterMapper chapterMapper;
    private final ChapterQuizMapper quizMapper;
    private final HomeworkServiceClient homeworkServiceClient;
    private final BadgeService badgeService;
    private final RedisStreamPublisher redisStreamPublisher;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final CacheManager cacheManager;

    private static final String PROGRESS_KEY_PREFIX = "progress:";
    private static final long DB_SYNC_INTERVAL_MS = 30000;

    /**
     * 上报视频进度，包含异常快进检测、缓存写入与定时落库。
     */
    @Transactional
    public Map<String, Object> reportVideoProgress(VideoProgressDTO dto) {
        String redisKey = PROGRESS_KEY_PREFIX + dto.getStudentId() + ":" + dto.getChapterId();

        String cachedProgressJson = redisTemplate.opsForValue().get(redisKey);
        ChapterProgress progress = null;

        if (cachedProgressJson != null) {
            try {
                progress = objectMapper.readValue(cachedProgressJson, ChapterProgress.class);
            } catch (Exception e) {
                log.error("解析 Redis 进度缓存失败", e);
            }
        }

        if (progress == null) {
            progress = getOrCreateProgress(dto.getStudentId(), dto.getChapterId());
        }

        if (dto.getClientTimestamp() != null && progress.getLastUpdateTime() != null
                && dto.getCurrentPosition() != null) {
            long lastUpdateMillis = progress.getLastUpdateTime()
                    .atZone(java.time.ZoneId.systemDefault())
                    .toInstant()
                    .toEpochMilli();
            long elapsedRealTimeSec = (System.currentTimeMillis() - lastUpdateMillis) / 1000;

            int lastPosition = progress.getLastPosition() != null ? progress.getLastPosition() : 0;
            int reportedProgress = dto.getCurrentPosition() - lastPosition;

            if (reportedProgress > 0 && elapsedRealTimeSec > 0
                    && reportedProgress > elapsedRealTimeSec * 1.5 + 5) {
                log.warn("检测到疑似异常快进: studentId={}, chapterId={}, 实际耗时={}s, 上报进度增量={}s",
                        dto.getStudentId(), dto.getChapterId(), elapsedRealTimeSec, reportedProgress);

                Map<String, Object> result = new HashMap<>();
                result.put("success", false);
                result.put("message", "检测到异常播放行为，进度未保存");
                result.put("cheatDetected", true);
                result.put("elapsedTime", elapsedRealTimeSec);
                result.put("reportedProgress", reportedProgress);
                return result;
            }
        }

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

        boolean shouldSyncDb = isCompletedStatus;
        long now = System.currentTimeMillis();

        if (!shouldSyncDb) {
            String syncKey = redisKey + ":last_sync";
            String lastSyncStr = redisTemplate.opsForValue().get(syncKey);
            long lastSyncTime = lastSyncStr != null ? Long.parseLong(lastSyncStr) : 0;

            if (now - lastSyncTime > DB_SYNC_INTERVAL_MS) {
                shouldSyncDb = true;
            }
        }

        if (shouldSyncDb) {
            progressMapper.updateById(progress);
            redisTemplate.opsForValue().set(redisKey + ":last_sync", String.valueOf(now));
            log.info("同步视频进度到数据库: studentId={}, chapterId={}", dto.getStudentId(), dto.getChapterId());

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

        try {
            redisTemplate.opsForValue().set(redisKey, objectMapper.writeValueAsString(progress));
            redisTemplate.expire(redisKey, Duration.ofDays(7));
            redisTemplate.expire(redisKey + ":last_sync", Duration.ofDays(7));
        } catch (Exception e) {
            log.error("更新 Redis 进度缓存失败", e);
        }

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
     * 提交章节测验并更新测验成绩。
     */
    @Transactional
    public Map<String, Object> submitQuiz(QuizSubmitDTO dto) {
        List<ChapterQuiz> quizzes = quizMapper.selectList(
                new LambdaQueryWrapper<ChapterQuiz>()
                        .eq(ChapterQuiz::getChapterId, dto.getChapterId())
                        .orderByAsc(ChapterQuiz::getSortOrder));

        if (quizzes.isEmpty()) {
            Map<String, Object> result = new HashMap<>();
            result.put("score", 100);
            result.put("totalScore", 100);
            result.put("message", "该章节暂无测验题目");
            return result;
        }

        int totalScore = 0;
        int earnedScore = 0;
        Map<Long, Boolean> questionResults = new HashMap<>();

        Map<Long, String> answerMap = dto.getAnswers() == null
                ? Collections.emptyMap()
                : dto.getAnswers().stream()
                        .filter(answer -> answer != null && answer.getQuestionId() != null)
                        .collect(Collectors.toMap(
                                QuizSubmitDTO.QuizAnswer::getQuestionId,
                                answer -> answer.getAnswer() == null ? "" : answer.getAnswer(),
                                (left, right) -> left));

        for (ChapterQuiz quiz : quizzes) {
            totalScore += quiz.getScore();

            String studentAnswer = answerMap.getOrDefault(quiz.getId(), "");

            boolean isCorrect = quiz.getCorrectAnswer().trim().equalsIgnoreCase(studentAnswer.trim());
            questionResults.put(quiz.getId(), isCorrect);

            if (isCorrect) {
                earnedScore += quiz.getScore();
            }
        }

        ChapterProgress progress = getOrCreateProgress(dto.getStudentId(), dto.getChapterId());
        progress.setQuizScore(earnedScore);
        progress.setQuizSubmittedAt(LocalDateTime.now());
        progressMapper.updateById(progress);

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
     * 按学生和章节获取进度，不存在则初始化一条默认记录。
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
     * 检查是否达到章节完成条件，并触发联动动作。
     */
    private boolean checkAndTriggerUnlock(ChapterProgress progress) {
        if (progress.getIsCompleted() == 1) {
            return false;
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

            publishChapterCompletedEvent(progress, chapter);

            try {
                homeworkServiceClient.unlockHomework(progress.getStudentId(), progress.getChapterId());
                log.info("作业解锁成功: studentId={}, chapterId={}", progress.getStudentId(), progress.getChapterId());
            } catch (Exception e) {
                log.error("调用作业解锁服务失败: {}", e.getMessage());
            }

            try {
                badgeService.checkAndAwardBadges(progress.getStudentId());
            } catch (Exception e) {
                log.error("检查徽章授予失败: {}", e.getMessage());
            }

            return true;
        }

        return false;
    }

    /**
     * 发布章节完成事件，供下游服务消费。
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
