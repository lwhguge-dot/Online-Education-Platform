package com.eduplatform.progress.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.eduplatform.progress.dto.QuizSubmitDTO;
import com.eduplatform.progress.dto.VideoProgressDTO;
import com.eduplatform.progress.entity.Chapter;
import com.eduplatform.progress.entity.ChapterProgress;
import com.eduplatform.progress.mapper.ChapterMapper;
import com.eduplatform.progress.mapper.ChapterProgressMapper;
import com.eduplatform.progress.vo.ChapterProgressVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 学习进度聚合服务。
 * 说明：读流程与分析入口保留在本服务，写流程委托 ProgressTrackingService 承担。
 */
@Service
@RequiredArgsConstructor
public class ProgressService {

    private final ChapterProgressMapper progressMapper;
    private final ChapterMapper chapterMapper;
    private final ProgressAnalyticsService progressAnalyticsService;
    private final ProgressTrackingService progressTrackingService;

    /**
     * 将进度实体转换为视图对象。
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
     * 批量转换进度实体。
     */
    public List<ChapterProgressVO> convertToVOList(List<ChapterProgress> list) {
        if (list == null) {
            return Collections.emptyList();
        }
        return list.stream().map(this::convertToVO).collect(Collectors.toList());
    }

    /**
     * 上报视频进度。
     * 说明：实际写流程已下沉到 ProgressTrackingService。
     */
    @Transactional
    public Map<String, Object> reportVideoProgress(VideoProgressDTO dto) {
        return progressTrackingService.reportVideoProgress(dto);
    }

    /**
     * 提交章节测验。
     * 说明：实际写流程已下沉到 ProgressTrackingService。
     */
    @Transactional
    public Map<String, Object> submitQuiz(QuizSubmitDTO dto) {
        return progressTrackingService.submitQuiz(dto);
    }

    public ChapterProgress getProgress(Long studentId, Long chapterId) {
        return progressMapper.selectOne(
                new LambdaQueryWrapper<ChapterProgress>()
                        .eq(ChapterProgress::getStudentId, studentId)
                        .eq(ChapterProgress::getChapterId, chapterId));
    }

    public List<ChapterProgress> getStudentCourseProgress(Long studentId, Long courseId) {
        return progressMapper.selectList(
                new LambdaQueryWrapper<ChapterProgress>()
                        .eq(ChapterProgress::getStudentId, studentId)
                        .eq(ChapterProgress::getCourseId, courseId));
    }

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

    public Map<String, Object> getLastStudyPosition(Long studentId, Long courseId) {
        Map<String, Object> result = new HashMap<>();

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

        ChapterProgress lastProgress = progressList.get(0);
        result.put("hasProgress", true);
        result.put("lastChapterId", lastProgress.getChapterId());
        result.put("lastPosition", lastProgress.getLastPosition() != null ? lastProgress.getLastPosition() : 0);
        result.put("lastUpdateTime", lastProgress.getLastUpdateTime());
        result.put("videoRate", lastProgress.getVideoRate());
        result.put("isCompleted", lastProgress.getIsCompleted() != null && lastProgress.getIsCompleted() == 1);

        Chapter chapter = chapterMapper.selectById(lastProgress.getChapterId());
        if (chapter != null) {
            result.put("lastChapterTitle", chapter.getTitle());
        } else {
            result.put("lastChapterTitle", "章节 " + lastProgress.getChapterId());
        }

        return result;
    }

    /**
     * 获取学生学习轨迹摘要。
     * 说明：分析查询逻辑下沉到 ProgressAnalyticsService，此处仅保留兼容入口。
     */
    public Map<String, Object> getLearningTrack(Long studentId) {
        return progressAnalyticsService.getLearningTrack(studentId);
    }

    /**
     * 获取学生知识掌握度指标。
     * 说明：委托独立分析服务，降低主服务职责复杂度。
     */
    public Map<String, Object> getKnowledgeMastery(Long studentId) {
        return progressAnalyticsService.getKnowledgeMastery(studentId);
    }

    /**
     * 获取学生课程学习轨迹。
     */
    public List<Map<String, Object>> getLearningTrajectory(Long studentId, Long courseId) {
        return progressAnalyticsService.getLearningTrajectory(studentId, courseId);
    }

    /**
     * 获取学生课程测验趋势。
     */
    public List<Map<String, Object>> getQuizScoreTrend(Long studentId, Long courseId) {
        return progressAnalyticsService.getQuizScoreTrend(studentId, courseId);
    }

    /**
     * 获取学生课程学情分析。
     */
    public Map<String, Object> getStudentCourseAnalytics(Long studentId, Long courseId) {
        return progressAnalyticsService.getStudentCourseAnalytics(studentId, courseId);
    }

    /**
     * 获取课程级分析指标，供教师端看板使用。
     */
    public Map<String, Object> getCourseAnalytics(Long courseId) {
        return progressAnalyticsService.getCourseAnalytics(courseId);
    }
}
