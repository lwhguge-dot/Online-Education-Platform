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
 * 瀛︿範杩涘害涓庡鎯呮牳蹇冩湇鍔?
 * 璐熻矗杩借釜瀛︾敓鍦ㄨ绋嬩綋绯诲唴鐨勫叏閲忔垚闀胯建杩癸紝娑电洊瑙嗛瑙傜湅浠介璁＄畻銆佹祴楠岃瘎鍒嗐€佷互鍙婂缁村害鐨勫鎯呭垎鏋愭ā鍨嬨€?
 *
 * 鏍稿績鎶€鏈柟妗堬細
 * 1. 楂樺苟鍙戜笂鎶ワ細閲囩敤 Redis 浣滀负涓€绾х紦鍐插尯锛岄€氳繃鈥?0绉掑績璺?瀹岃瑙﹀彂鈥濈殑鍙岄噸绛栫暐瀹炵幇寮傛钀藉簱锛屾敮鎾戞捣閲忔挱鏀捐繘搴﹀悓姝ャ€?
 * 2. 琛屼负椋庢帶锛氬唴缃€滄挱鏀鹃€熺巼鍚彂寮忔娴嬧€濋槻浣滃紛绠楁硶锛屾嫤鎴潪娉曞揩杩涚瓑鐮村潖鏁欏璐ㄩ噺鐨勮涓恒€?
 * 3. 涓氬姟闂幆锛氫綔涓哄涔犻摼璺殑寮曟搸锛屽疄鏃堕┍鍔?Homework锛堜綔涓氳В閿侊級涓?Badge锛堝媼绔犳巿浜堬級绯荤粺鐨勭姸鎬佽縼绉汇€?
 * 4. 澶嶆潅璁＄畻锛氭彁渚涜绋嬪眰绾х殑鈥滄祦澶辩巼鈥濅笌鈥滅煡璇嗘帉鎻″害鈥濊绠楁ā鍨嬶紝杈呭姪鏁欏笀杩涜绮惧噯鏁欏鍐崇瓥銆?
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

        /** Redis 杩涘害缂撳瓨鍓嶇紑锛歱rogress:studentId:chapterId */
        private static final String PROGRESS_KEY_PREFIX = "progress:";

        /** 寮傛钀藉簱闂撮殧锛?0绉掞紝骞宠　 DB 鍘嬪姏涓庢暟鎹疄鏃舵€?*/
        private static final long DB_SYNC_INTERVAL_MS = 30000;

        /**
         * 灏嗚繘搴﹀疄浣撹浆鎹负瑙嗗浘瀵硅薄
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
         * 鎵归噺杞崲杩涘害瀹炰綋涓鸿鍥惧璞?
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
         * 涓婃姤骞舵寔涔呭寲瑙嗛鎾斁鍧愭爣 (鏍稿績閾捐矾)
         * 
         * 涓氬姟閫昏緫娴侊細
         * 1. 缂撳瓨浼樺厛锛氫紭鍏堜粠 Redis 妫€绱㈡渶鏂板揩鐓э紝瑙勯伩棰戠箒鐨?DB IO銆?
         * 2. 椋庢帶妫€鏌ワ細鎵ц elapsedRealTime vs reportedProgress 宸€煎垽瀹氾紝鎷︽埅闈炴硶鍒疯銆?
         * 3. 杩涘害鐔旀柇锛氫粎璁板綍鏈夋晥澧為暱锛屽巻鍙茶繘搴︿笉瑕嗙洊銆?
         * 4. 瀹岃鍒ゅ畾锛氬綋瑙﹀彂 isCompleted 鎴?杈惧埌鏃堕棿闃堝€兼椂锛屾墽琛屼簨鍔℃€ц惤搴撳苟瑙ｉ攣鍚庣画绔犺妭銆?
         * 
         * @param dto 鍖呭惈瀛︾敓ID銆佺珷鑺侷D銆佸綋鍓嶇鏁般€佹€绘椂闀垮強瀹岃鏍囪
         * @return 鍖呭惈鏈€鏂拌繘搴﹀揩鐓у強 unlockTriggered (鏄惁瑙﹀彂瑙ｉ攣) 鐨勫弽棣?
         */
        @Transactional
        public Map<String, Object> reportVideoProgress(VideoProgressDTO dto) {
                String redisKey = PROGRESS_KEY_PREFIX + dto.getStudentId() + ":" + dto.getChapterId();

                // 1. 鑾峰彇 Redis 涓殑褰撳墠鐘舵€?(鑻ュ瓨鍦?
                String cachedProgressJson = redisTemplate.opsForValue().get(redisKey);
                ChapterProgress progress = null;

                if (cachedProgressJson != null) {
                        try {
                                progress = objectMapper.readValue(cachedProgressJson, ChapterProgress.class);
                        } catch (Exception e) {
                                log.error("瑙ｆ瀽Redis杩涘害澶辫触", e);
                        }
                }

                // 濡傛灉Redis娌℃暟鎹紝鏌B
                if (progress == null) {
                        progress = getOrCreateProgress(dto.getStudentId(), dto.getChapterId());
                }

                // ===== 闃蹭綔寮婃牎楠岄€昏緫 =====
                // 妫€娴嬬枒浼奸潪娉曞揩杩涳細鑻ヤ笂鎶ヨ繘搴﹀閲忚秴杩囧疄闄呮椂闂村樊鐨?.5鍊?5绉掑宸紝鍒欐嫆缁濇洿鏂?
                if (dto.getClientTimestamp() != null && progress.getLastUpdateTime() != null
                                && dto.getCurrentPosition() != null) {
                        long lastUpdateMillis = progress.getLastUpdateTime()
                                        .atZone(java.time.ZoneId.systemDefault())
                                        .toInstant()
                                        .toEpochMilli();
                        long elapsedRealTimeSec = (System.currentTimeMillis() - lastUpdateMillis) / 1000;

                        int lastPosition = progress.getLastPosition() != null ? progress.getLastPosition() : 0;
                        int reportedProgress = dto.getCurrentPosition() - lastPosition;

                        // 鍏佽1.5鍊嶅宸?+ 5绉掑浐瀹氫綑閲忥紙澶勭悊缃戠粶寤惰繜銆佺紦鍐茬瓑锛?
                        if (reportedProgress > 0 && elapsedRealTimeSec > 0
                                        && reportedProgress > elapsedRealTimeSec * 1.5 + 5) {
                                log.warn("妫€娴嬪埌鐤戜技闈炴硶蹇繘锛歴tudentId={}, chapterId={}, 瀹為檯鏃堕棿={}s, 涓婃姤杩涘害澧為噺={}s",
                                                dto.getStudentId(), dto.getChapterId(), elapsedRealTimeSec,
                                                reportedProgress);

                                Map<String, Object> result = new HashMap<>();
                                result.put("success", false);
                                result.put("message", "妫€娴嬪埌寮傚父鎾斁琛屼负锛岃繘搴︽湭淇濆瓨");
                                result.put("cheatDetected", true);
                                result.put("elapsedTime", elapsedRealTimeSec);
                                result.put("reportedProgress", reportedProgress);
                                return result;
                        }
                }
                // ===== 闃蹭綔寮婃牎楠岀粨鏉?=====

                // 2. 鏇存柊鍐呭瓨瀵硅薄
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

                // 3. 鍒ゆ柇鏄惁闇€瑕佸悓姝B
                // 鏉′欢锛氬畬璇?鎴?璺濈涓婃鍚屾瓒呰繃闃堝€?
                boolean shouldSyncDb = isCompletedStatus;
                long now = System.currentTimeMillis();

                if (!shouldSyncDb) {
                        // 妫€鏌?Redis 涓殑鏈€鍚庡悓姝ユ椂闂存埑 (浣跨敤 Hash 鐨勯澶栧瓧娈垫垨绠€鍗曢€昏緫锛岃繖閲岀畝鍖栦娇鐢ㄥ璞″唴涓存椂瀛樺偍涓嶅お琛岋紝
                        // 鏇村ソ鐨勬柟寮忔槸 Redis 鍙﹀瓨涓€涓?key 璁板綍 timestamp锛屾垨鑰呭湪 progress 瀵硅薄鎵╁睍瀛楁)
                        // 绠€鍗曡捣瑙侊紝浠?Redis Key 鐨?TTL 鎴栬€?鍙︿竴涓?Key 鍒ゆ柇
                        String syncKey = redisKey + ":last_sync";
                        String lastSyncStr = redisTemplate.opsForValue().get(syncKey);
                        long lastSyncTime = lastSyncStr != null ? Long.parseLong(lastSyncStr) : 0;

                        if (now - lastSyncTime > DB_SYNC_INTERVAL_MS) {
                                shouldSyncDb = true;
                        }
                }

                // 4. 鎵ц鏇存柊
                if (shouldSyncDb) {
                        progressMapper.updateById(progress);
                        // 鏇存柊鍚屾鏃堕棿
                        redisTemplate.opsForValue().set(redisKey + ":last_sync", String.valueOf(now));
                        log.info("鍚屾瑙嗛杩涘害鍒癉B: studentId={}, chapterId={}", dto.getStudentId(), dto.getChapterId());

                        // 澶辨晥瀛︿範杞ㄨ抗缂撳瓨
                        try {
                                Cache trackCache = cacheManager.getCache("learning_track");
                                if (trackCache != null) {
                                        trackCache.evict(dto.getStudentId());
                                        log.debug("澶辨晥瀛︿範杞ㄨ抗缂撳瓨: studentId={}", dto.getStudentId());
                                }
                        } catch (Exception e) {
                                log.warn("澶辨晥缂撳瓨澶辫触", e);
                        }
                }

                // 5. 濮嬬粓鏇存柊 Redis 浣滀负鏈€鏂扮紦瀛?
                try {
                        redisTemplate.opsForValue().set(redisKey, objectMapper.writeValueAsString(progress));
                        // 7澶╄繃鏈燂紝闃叉鍍靛案鏁版嵁
                        redisTemplate.expire(redisKey, Duration.ofDays(7));
                        redisTemplate.expire(redisKey + ":last_sync", Duration.ofDays(7));
                } catch (Exception e) {
                        log.error("鏇存柊Redis杩涘害澶辫触", e);
                }

                // 妫€鏌ヨВ閿?(濡傛灉 DB 鏇存柊浜嗭紝鎴栬€?Redis 涓姸鎬佹弧瓒充簡瑙ｉ攣)
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
         * 鎵ц绔犺妭娴嬮獙璇勫垎骞惰褰?
         * 
         * 鏍稿績娴佺▼锛?
         * 1. 鑷姩鍒ゅ嵎锛氬姣?ChapterQuiz 鏍囧噯绛旀锛堝拷鐣ユ牸寮忋€佸ぇ灏忓啓锛夛紝璁＄畻鍔犳潈寰楀垎銆?
         * 2. 杩涘害閿氬畾锛氳褰?quizScore 涓庢彁浜ゆ椂闂存埑锛屼负鍚庣画 unlockCondition 鎻愪緵鍒ゅ畾渚濇嵁銆?
         * 3. 杩炲甫瑙﹀彂锛氳嫢寰楀垎杈炬爣锛岃嚜鍔ㄩ┍鍔ㄤ綔涓氱郴缁熸墽琛屸€滃叧鍗¤В閿佲€濄€?
         * 
         * @return 鍖呭惈寰楀垎鐜囧強鍏蜂綋棰樼洰姝ｈ鍒嗗竷鐨勭粨鏋滈泦
         */
        @Transactional
        public Map<String, Object> submitQuiz(QuizSubmitDTO dto) {
                // 鑾峰彇绔犺妭鎵€鏈夋祴楠岄鐩?
                List<ChapterQuiz> quizzes = quizMapper.selectList(
                                new LambdaQueryWrapper<ChapterQuiz>()
                                                .eq(ChapterQuiz::getChapterId, dto.getChapterId())
                                                .orderByAsc(ChapterQuiz::getSortOrder));

                if (quizzes.isEmpty()) {
                        Map<String, Object> result = new HashMap<>();
                        result.put("score", 100);
                        result.put("totalScore", 100);
                        result.put("message", "璇ョ珷鑺傛棤娴嬮獙棰樼洰");
                        return result;
                }

                // 璁＄畻寰楀垎
                int totalScore = 0;
                int earnedScore = 0;
                Map<Long, Boolean> questionResults = new HashMap<>();

                // 棰勬瀯寤洪鐩瓟妗堟槧灏勶紝閬垮厤鍦ㄦ瘡閬撻寰幆涓弽澶嶉亶鍘嗘彁浜ょ瓟妗堥€犳垚 O(n*m)
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

                        // 鏌ユ壘瀛︾敓绛旀
                        String studentAnswer = answerMap.getOrDefault(quiz.getId(), "");

                        // 姣斿绛旀锛堝拷鐣ュぇ灏忓啓鍜岀┖鏍硷級
                        boolean isCorrect = quiz.getCorrectAnswer().trim().equalsIgnoreCase(studentAnswer.trim());
                        questionResults.put(quiz.getId(), isCorrect);

                        if (isCorrect) {
                                earnedScore += quiz.getScore();
                        }
                }

                // 鏇存柊杩涘害璁板綍
                ChapterProgress progress = getOrCreateProgress(dto.getStudentId(), dto.getChapterId());
                progress.setQuizScore(earnedScore);
                progress.setQuizSubmittedAt(LocalDateTime.now());
                progressMapper.updateById(progress);

                // 妫€鏌ユ槸鍚︽弧瓒宠В閿佹潯浠?
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
         * 妫€绱㈢壒瀹氱珷鑺傜殑搴曞眰杩涘害璁板綍
         */
        public ChapterProgress getProgress(Long studentId, Long chapterId) {
                return progressMapper.selectOne(
                                new LambdaQueryWrapper<ChapterProgress>()
                                                .eq(ChapterProgress::getStudentId, studentId)
                                                .eq(ChapterProgress::getChapterId, chapterId));
        }

        /**
         * 妫€绱㈠鐢熷湪鎸囧畾璇剧▼涓嬬殑瀹屾暣杩涘害鐭╅樀
         */
        public List<ChapterProgress> getStudentCourseProgress(Long studentId, Long courseId) {
                // 鐩存帴浠巆hapter_progress琛ㄦ煡璇㈣瀛︾敓璇ヨ绋嬬殑鎵€鏈夎繘搴﹁褰?
                return progressMapper.selectList(
                                new LambdaQueryWrapper<ChapterProgress>()
                                                .eq(ChapterProgress::getStudentId, studentId)
                                                .eq(ChapterProgress::getCourseId, courseId));
        }

        /**
         * 瑙ｆ瀽绔犺妭瑙ｉ攣鐨勫悎瑙勬€ф寚绾?
         * 渚濇嵁锛氳棰戣鐪嬬巼闃堝€?(default 90%) + 娴嬮獙杈炬爣鍒?(default 60)銆?
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
         * 鑾峰彇鎴栧垵濮嬪寲杩涘害妗ｆ (淇濋殰骞傜瓑)
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
         * 鎵ц瑙ｉ攣鐘舵€佹満鏍￠獙涓庡閮ㄩ€氱煡
         * 褰撶珷鑺傝鏍囪涓哄畬鎴愭椂锛岃礋璐ｅ崗璋?Homework 鏈嶅姟涓?Badge 鏈嶅姟銆?
         */
        private boolean checkAndTriggerUnlock(ChapterProgress progress) {
                if (progress.getIsCompleted() == 1) {
                        return false; // 宸插畬鎴愶紝鏃犻渶鍐嶈Е鍙?
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

                        // 鍙戝竷绔犺妭瀹屾垚浜嬩欢鍒?Redis Stream
                        // 鐢?homework-service 娑堣垂锛堣В閿佷綔涓氾級鍜?user-service 娑堣垂锛堥€氱煡锛?
                        publishChapterCompletedEvent(progress, chapter);

                        try {
                                homeworkServiceClient.unlockHomework(progress.getStudentId(), progress.getChapterId());
                                log.info("浣滀笟瑙ｉ攣鎴愬姛: studentId={}, chapterId={}", progress.getStudentId(),
                                                progress.getChapterId());
                        } catch (Exception e) {
                                log.error("璋冪敤浣滀笟瑙ｉ攣鏈嶅姟澶辫触: {}", e.getMessage());
                        }

                        // 绔犺妭瀹屾垚鏃舵鏌ュ苟鎺堜簣寰界珷
                        try {
                                badgeService.checkAndAwardBadges(progress.getStudentId());
                        } catch (Exception e) {
                                log.error("妫€鏌ュ窘绔犲け璐? {}", e.getMessage());
                        }

                        return true;
                }

                return false;
        }

        /**
         * 婧簮璇剧▼鏈€鍚庣殑瀛︿範鍧愭爣
         * 鐢ㄦ埛琛屼负锛氬啀娆¤繘鍏ヨ绋嬮椤垫垨鎾斁椤垫椂锛岀簿鍑嗗畾浣嶅埌鏈€杩戜竴娆℃搷浣滅殑绔犺妭涓庣鏁般€?
         */
        public Map<String, Object> getLastStudyPosition(Long studentId, Long courseId) {
                Map<String, Object> result = new HashMap<>();

                // 鏌ヨ璇ュ鐢熻璇剧▼鐨勬墍鏈夎繘搴﹁褰曪紝鎸夋渶鍚庢洿鏂版椂闂存帓搴?
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

                // 鑾峰彇鏈€杩戝涔犵殑绔犺妭
                ChapterProgress lastProgress = progressList.get(0);

                result.put("hasProgress", true);
                result.put("lastChapterId", lastProgress.getChapterId());
                result.put("lastPosition", lastProgress.getLastPosition() != null ? lastProgress.getLastPosition() : 0);
                result.put("lastUpdateTime", lastProgress.getLastUpdateTime());
                result.put("videoRate", lastProgress.getVideoRate());
                result.put("isCompleted", lastProgress.getIsCompleted() != null && lastProgress.getIsCompleted() == 1);

                // 鑾峰彇绔犺妭鏍囬
                Chapter chapter = chapterMapper.selectById(lastProgress.getChapterId());
                if (chapter != null) {
                        result.put("lastChapterTitle", chapter.getTitle());
                } else {
                        result.put("lastChapterTitle", "绔犺妭 " + lastProgress.getChapterId());
                }

                return result;
        }

        /**
         * 鑾峰彇瀛︾敓澶氱淮瀛︿範杞ㄨ抗 (鐪熷疄鐢熶骇鏁版嵁鎸栨帢)
         * 鎸囨爣娑电洊锛氱疮璁″鏃躲€佸畬璇炬€绘暟銆佽繎 7 鏃ユ椿璺冨害鍙婅缁嗙殑瀛︿範鏃堕棿杞淬€?
         * 
         * 浼樺寲锛氶噰鐢?Spring Cache 杩涜瀛︾敓缁村害鐨勭粨鏋滅紦瀛樸€?
         */
        @Cacheable(value = "learning_track", key = "#p0")
        public Map<String, Object> getLearningTrack(Long studentId) {
                Map<String, Object> track = new HashMap<>();

                // 鏌ヨ璇ュ鐢熸墍鏈夎繘搴﹁褰?
                List<ChapterProgress> allProgress = progressMapper.selectList(
                                new LambdaQueryWrapper<ChapterProgress>()
                                                .eq(ChapterProgress::getStudentId, studentId)
                                                .orderByDesc(ChapterProgress::getLastUpdateTime));

                // 缁熻鎬诲涔犳椂闀匡紙绉掞級
                int totalWatchTime = allProgress.stream()
                                .mapToInt(p -> p.getVideoWatchTime() != null ? p.getVideoWatchTime() : 0)
                                .sum();

                // 缁熻瀹屾垚绔犺妭鏁?
                long completedChapters = allProgress.stream()
                                .filter(p -> p.getIsCompleted() != null && p.getIsCompleted() == 1)
                                .count();

                // 璁＄畻瀛︿範澶╂暟锛堟牴鎹渶杩?澶╂椿鍔級
                long activeDays = allProgress.stream()
                                .filter(p -> p.getLastUpdateTime() != null)
                                .map(p -> p.getLastUpdateTime().toLocalDate())
                                .distinct()
                                .count();

                // 鏈€杩戝涔犺褰?
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
         * 璁＄畻鐭ヨ瘑鐐规帉鎻″害鐢诲儚 (鍩轰簬璇勫垎鐨勬鎬佸垎甯冨缓妯?
         * 缁村害锛?
         * 1. 瑙傜湅瀹屾暣搴︼細琛￠噺鎶曞叆绋嬪害銆?
         * 2. 娴嬮獙寰楀垎鐜囷細琛￠噺鐞嗚В娣卞害銆?
         * 3. 鎺屾彙绛夌骇锛氬垝鍒嗏€滅簿閫?鐔熺粌/浜嗚В/闇€鍔犲己鈥濆洓涓搴︺€?
         */
        public Map<String, Object> getKnowledgeMastery(Long studentId) {
                Map<String, Object> mastery = new HashMap<>();

                // 鏌ヨ璇ュ鐢熸墍鏈夎繘搴﹁褰?
                List<ChapterProgress> allProgress = progressMapper.selectList(
                                new LambdaQueryWrapper<ChapterProgress>()
                                                .eq(ChapterProgress::getStudentId, studentId));

                // 璁＄畻骞冲潎瑙嗛瑙傜湅鐜?(鎶曞叆搴?
                double avgVideoRate = allProgress.stream()
                                .filter(p -> p.getVideoRate() != null)
                                .mapToDouble(p -> p.getVideoRate().doubleValue())
                                .average()
                                .orElse(0.0);

                // 璁＄畻骞冲潎娴嬮獙鍒嗘暟 (杈炬垚搴?
                double avgQuizScore = allProgress.stream()
                                .filter(p -> p.getQuizScore() != null)
                                .mapToInt(ChapterProgress::getQuizScore)
                                .average()
                                .orElse(0.0);

                // 鍚彂寮忔帉鎻＄瓑绾у垽瀹?
                String masteryLevel;
                if (avgQuizScore >= 90) {
                        masteryLevel = "精通";
                } else if (avgQuizScore >= 70) {
                        masteryLevel = "鐔熺粌";
                } else if (avgQuizScore >= 60) {
                        masteryLevel = "浜嗚В";
                } else {
                        masteryLevel = "闇€鍔犲己";
                }

                // 璺ㄨ绋嬪鎯呰仛鍚?
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
         * 鑱氬悎璇剧▼瀛︿範鏃堕棿杞?(鏁欏笀绔細涓綋娲昏穬搴︾敾鍍?
         * 灏嗗師濮嬭繘搴︿笂鎶ュ簭鍒楄仛鍚堜负姣忔棩鐨勨€滃涔犳椂闀库€濅笌鈥滄垚鏋滐紙瀹岃鏁帮級鈥濇暟鎹偣銆?
         */
        public List<Map<String, Object>> getLearningTrajectory(Long studentId, Long courseId) {
                // 鏌ヨ璇ュ鐢熻璇剧▼鐨勬墍鏈夎繘搴﹁褰?
                List<ChapterProgress> progressList = progressMapper.selectList(
                                new LambdaQueryWrapper<ChapterProgress>()
                                                .eq(ChapterProgress::getStudentId, studentId)
                                                .eq(ChapterProgress::getCourseId, courseId)
                                                .isNotNull(ChapterProgress::getLastUpdateTime)
                                                .orderByAsc(ChapterProgress::getLastUpdateTime));

                // 鎸夌収鑷劧鏃ユ墽琛?TreeMap 鎺掑簭鑱氱被
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

                        // 褰撳ぉ鎶曚骇姣旓細瀛︿範鎬绘椂闀?
                        int studyMinutes = entry.getValue().stream()
                                        .mapToInt(p -> p.getVideoWatchTime() != null ? p.getVideoWatchTime() / 60 : 0)
                                        .sum();
                        dayData.put("studyMinutes", studyMinutes);

                        // 褰撳ぉ鎴愭灉浜у嚭锛氭柊瀹屾垚鐨勭珷鑺?
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
         * 鏄犲皠娴嬮獙鍒嗘暟婕斿彉瓒嬪娍
         * 鐢ㄤ簬璇勪及瀛︾敓瀛︿範鏇茬嚎鏄惁鍋ュ悍锛堝钩绋炽€佷笂鍗囨垨娉㈠姩鏋佸ぇ锛夈€?
         */
        public List<Map<String, Object>> getQuizScoreTrend(Long studentId, Long courseId) {
                // 鑾峰彇澶х翰鎺掑簭
                List<Chapter> chapters = chapterMapper.selectList(
                                new LambdaQueryWrapper<Chapter>()
                                                .eq(Chapter::getCourseId, courseId)
                                                .orderByAsc(Chapter::getSortOrder));

                // 鑾峰彇杩涘害妗ｆ
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
         * 姹囨€诲鐢熻绋嬪叏缁村害瀛︽儏鍒嗘瀽 (鏁欏笀绔細缁煎悎瀛︽儏鎶ュ憡)
         * 鑱氬悎浜?杞ㄨ抗銆佹祴楠岃秼鍔裤€佺珷鑺傚畬鎴愮巼 鍙?鍏抽敭 KPI 鎸囨爣锛堟€绘椂闀裤€佸畬璇剧巼锛夈€?
         */
        public Map<String, Object> getStudentCourseAnalytics(Long studentId, Long courseId) {
                Map<String, Object> analytics = new HashMap<>();

                // 1. 娲昏穬搴︽椂闂磋酱
                List<Map<String, Object>> learningTrajectory = getLearningTrajectory(studentId, courseId);
                analytics.put("learningTrajectory", learningTrajectory);

                // 2. 鐭ヨ瘑鍚告敹瓒嬪娍锛堟祴楠岋級
                List<Map<String, Object>> quizScoreTrend = getQuizScoreTrend(studentId, courseId);
                analytics.put("quizScoreTrend", quizScoreTrend);

                // 3. 鏋勫缓璇剧▼澶х翰瑙嗗浘涓庤繘搴﹀鐓?
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

                // 4. 璁＄畻 KPI 鎽樿
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
         * 瀹忚璇剧▼杩愯惀鐪嬫澘 (鏁欏笀绔細鏁翠綋鏁欏鏁堣兘璇勪及)
         * 涓氬姟缁村害锛?
         * 1. 娲昏穬搴︼細7 鏃ョ暀瀛樹笌娲昏穬瀛︾敓鏁般€?
         * 2. 婕忔枟杞寲锛氱珷鑺傞棿鐨勨€滄祦澶辩巼鈥濓紙Drop-off Rate锛夊垎鏋愶紝瀹氫綅鍐呭鏂眰銆?
         * 3. 闅惧害鐢诲儚锛氬熀浜庡钩鍧囧緱鍒嗙巼鍒ゅ畾鍚勪釜绔犺妭鐨勯鐩毦搴﹀垎甯冦€?
         */
        public Map<String, Object> getCourseAnalytics(Long courseId) {
                Map<String, Object> analytics = new HashMap<>();

                // 1. 鑾峰彇璇ヨ绋嬬殑鎵€鏈夌珷鑺?
                List<Chapter> chapters = chapterMapper.selectList(
                                new LambdaQueryWrapper<Chapter>()
                                                .eq(Chapter::getCourseId, courseId)
                                                .orderByAsc(Chapter::getSortOrder));

                // 2. 鑾峰彇璇ヨ绋嬬殑鎵€鏈夊鐢熻繘搴﹁褰?
                List<ChapterProgress> allProgress = progressMapper.selectList(
                                new LambdaQueryWrapper<ChapterProgress>()
                                                .eq(ChapterProgress::getCourseId, courseId));

                // 3. 缁熻瀛︾敓鏁伴噺锛堝幓閲嶏級
                long totalStudents = allProgress.stream()
                                .map(ChapterProgress::getStudentId)
                                .distinct()
                                .count();

                // 4. 缁熻娲昏穬瀛︾敓锛?澶╁唴鏈夊涔犺褰曪級
                LocalDateTime sevenDaysAgo = LocalDateTime.now().minusDays(7);
                long activeStudents = allProgress.stream()
                                .filter(p -> p.getLastUpdateTime() != null
                                                && p.getLastUpdateTime().isAfter(sevenDaysAgo))
                                .map(ChapterProgress::getStudentId)
                                .distinct()
                                .count();

                // 5. 璁＄畻骞冲潎杩涘害
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

                // 6. 璁＄畻骞冲潎娴嬮獙鍒嗘暟
                double avgQuizScore = allProgress.stream()
                                .filter(p -> p.getQuizScore() != null)
                                .mapToInt(ChapterProgress::getQuizScore)
                                .average()
                                .orElse(0.0);

                // 7. 璁＄畻瀹岃鐜囷紙瀹屾垚鎵€鏈夌珷鑺傜殑瀛︾敓姣斾緥锛?
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

                // 璇剧▼姒傝
                Map<String, Object> overview = new HashMap<>();
                overview.put("totalStudents", totalStudents);
                overview.put("activeStudents", activeStudents);
                overview.put("avgProgress", Math.round(avgProgress * 10) / 10.0);
                overview.put("avgQuizScore", Math.round(avgQuizScore * 10) / 10.0);
                overview.put("completionRate", Math.round(completionRate * 10) / 10.0);
                analytics.put("overview", overview);

                // 8. 绔犺妭鍒嗘瀽
                List<Map<String, Object>> chapterAnalytics = new ArrayList<>();
                // 预计算每章已完成学生数，避免循环内重复扫描全量进度
                Map<Long, Long> completedByChapter = allProgress.stream()
                                .filter(progress -> progress.getChapterId() != null
                                                && progress.getIsCompleted() != null
                                                && progress.getIsCompleted() == 1)
                                .collect(Collectors.groupingBy(
                                                ChapterProgress::getChapterId,
                                                Collectors.collectingAndThen(
                                                                Collectors.mapping(ChapterProgress::getStudentId,
                                                                                Collectors.toSet()),
                                                                set -> (long) set.size())));

                for (int i = 0; i < chapters.size(); i++) {
                        Chapter chapter = chapters.get(i);
                        Map<String, Object> chapterData = new HashMap<>();
                        chapterData.put("chapterId", chapter.getId());
                        chapterData.put("title", chapter.getTitle());
                        chapterData.put("sortOrder", chapter.getSortOrder());

                        // 鑾峰彇璇ョ珷鑺傜殑鎵€鏈夎繘搴﹁褰?
                        List<ChapterProgress> chapterProgressList = allProgress.stream()
                                        .filter(p -> p.getChapterId().equals(chapter.getId()))
                                        .toList();

                        // 绔犺妭瀹屾垚鐜?
                        long chapterCompletedCount = chapterProgressList.stream()
                                        .filter(p -> p.getIsCompleted() != null && p.getIsCompleted() == 1)
                                        .count();
                        double chapterCompletionRate = totalStudents > 0
                                        ? (double) chapterCompletedCount / totalStudents * 100
                                        : 0;
                        chapterData.put("completionRate", Math.round(chapterCompletionRate * 10) / 10.0);

                        // 骞冲潎瑙嗛瑙傜湅鐜?
                        double avgVideoWatchRate = chapterProgressList.stream()
                                        .filter(p -> p.getVideoRate() != null)
                                        .mapToDouble(p -> p.getVideoRate().doubleValue() * 100)
                                        .average()
                                        .orElse(0.0);
                        chapterData.put("avgVideoWatchRate", Math.round(avgVideoWatchRate * 10) / 10.0);

                        // 骞冲潎娴嬮獙鍒嗘暟
                        double chapterAvgQuizScore = chapterProgressList.stream()
                                        .filter(p -> p.getQuizScore() != null)
                                        .mapToInt(ChapterProgress::getQuizScore)
                                        .average()
                                        .orElse(0.0);
                        chapterData.put("avgQuizScore", Math.round(chapterAvgQuizScore * 10) / 10.0);

                        // 娴佸け鐜囷紙鍒拌揪璇ョ珷鑺備絾鏈畬鎴愮殑姣斾緥锛?
                        // 绠€鍖栬绠楋細鍓嶄竴绔犲畬鎴愪絾鏈珷鏈畬鎴愮殑瀛︾敓姣斾緥
                        double dropOffRate = 0;
                        if (i > 0) {
                                Chapter prevChapter = chapters.get(i - 1);
                                long prevCompleted = completedByChapter.getOrDefault(prevChapter.getId(), 0L);
                                if (prevCompleted > 0) {
                                        dropOffRate = (1 - (double) chapterCompletedCount / prevCompleted) * 100;
                                        dropOffRate = Math.max(0, dropOffRate); // 纭繚涓嶄负璐?
                                }
                        }
                        chapterData.put("dropOffRate", Math.round(dropOffRate * 10) / 10.0);

                        chapterAnalytics.add(chapterData);
                }
                analytics.put("chapterAnalytics", chapterAnalytics);

                // 9. 棰樼洰闅惧害鍒嗘瀽锛堝熀浜庢祴楠屽垎鏁帮級
                List<Map<String, Object>> questionDifficulty = new ArrayList<>();

                // 鎵归噺鍔犺浇鏈绋嬫墍鏈夌珷鑺傞鐩紝閬垮厤绔犺妭寰幆鍐呴€愮珷鏌ヨ
                List<Long> chapterIds = chapters.stream()
                                .map(Chapter::getId)
                                .filter(Objects::nonNull)
                                .toList();
                Map<Long, List<ChapterQuiz>> quizByChapter = chapterIds.isEmpty()
                                ? Collections.emptyMap()
                                : quizMapper.selectList(
                                                new LambdaQueryWrapper<ChapterQuiz>()
                                                                .in(ChapterQuiz::getChapterId, chapterIds)
                                                                .orderByAsc(ChapterQuiz::getSortOrder))
                                                .stream()
                                                .filter(quiz -> quiz.getChapterId() != null)
                                                .collect(Collectors.groupingBy(ChapterQuiz::getChapterId));

                for (Chapter chapter : chapters) {
                        // 鑾峰彇璇ョ珷鑺傜殑娴嬮獙棰樼洰
                        List<ChapterQuiz> quizzes = quizByChapter.getOrDefault(chapter.getId(), Collections.emptyList());

                        // 鑾峰彇璇ョ珷鑺傜殑杩涘害璁板綍
                        List<ChapterProgress> chapterProgressList = allProgress.stream()
                                        .filter(p -> p.getChapterId().equals(chapter.getId())
                                                        && p.getQuizScore() != null)
                                        .toList();

                        if (!quizzes.isEmpty() && !chapterProgressList.isEmpty()) {
                                // 璁＄畻璇ョ珷鑺傜殑骞冲潎寰楀垎鐜?
                                double avgScoreRate = chapterProgressList.stream()
                                                .mapToInt(ChapterProgress::getQuizScore)
                                                .average()
                                                .orElse(0.0);

                                // 閿欒鐜?= 100 - 骞冲潎寰楀垎鐜?
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
                // 鎸夐敊璇巼鎺掑簭锛堥珮鍒颁綆锛?
                questionDifficulty.sort((a, b) -> Double.compare(
                                (Double) b.get("errorRate"), (Double) a.get("errorRate")));
                analytics.put("questionDifficulty", questionDifficulty);

                // 10. 骞冲彴骞冲潎鍊煎姣旓紙妯℃嫙鏁版嵁锛屽疄闄呭簲浠庡叏骞冲彴缁熻锛?
                Map<String, Object> platformComparison = new HashMap<>();
                platformComparison.put("avgProgress", 65.0);
                platformComparison.put("avgQuizScore", 72.0);
                platformComparison.put("completionRate", 30.0);
                analytics.put("platformComparison", platformComparison);

                return analytics;
        }

        /**
         * 鍙戝竷绔犺妭瀹屾垚浜嬩欢鍒?Redis Stream
         * 娑堣垂鑰咃細homework-service锛堣В閿佷綔涓氾級銆乽ser-service锛堥€氱煡锛?
         *
         * @param progress 绔犺妭杩涘害璁板綍
         * @param chapter  绔犺妭瀹炰綋
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
                        log.error("鍙戝竷绔犺妭瀹屾垚浜嬩欢澶辫触: studentId={}, chapterId={}, error={}",
                                        progress.getStudentId(), progress.getChapterId(), e.getMessage());
                }
        }
}

