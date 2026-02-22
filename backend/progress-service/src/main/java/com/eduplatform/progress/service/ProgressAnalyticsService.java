package com.eduplatform.progress.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.eduplatform.progress.entity.Chapter;
import com.eduplatform.progress.entity.ChapterProgress;
import com.eduplatform.progress.entity.ChapterQuiz;
import com.eduplatform.progress.mapper.ChapterMapper;
import com.eduplatform.progress.mapper.ChapterProgressMapper;
import com.eduplatform.progress.mapper.ChapterQuizMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * 学情分析查询服务。
 * 说明：集中承接学习轨迹、掌握度、趋势和课程分析，降低 ProgressService 职责复杂度。
 */
@Service
@RequiredArgsConstructor
public class ProgressAnalyticsService {

    private final ChapterProgressMapper progressMapper;
    private final ChapterMapper chapterMapper;
    private final ChapterQuizMapper quizMapper;

    /**
     * 获取学生学习轨迹摘要。
     */
    @Cacheable(value = "learning_track", key = "#p0")
    public Map<String, Object> getLearningTrack(Long studentId) {
        Map<String, Object> track = new HashMap<>();

        List<ChapterProgress> allProgress = progressMapper.selectList(
                new LambdaQueryWrapper<ChapterProgress>()
                        .eq(ChapterProgress::getStudentId, studentId)
                        .orderByDesc(ChapterProgress::getLastUpdateTime));

        int totalWatchTime = allProgress.stream()
                .mapToInt(p -> p.getVideoWatchTime() != null ? p.getVideoWatchTime() : 0)
                .sum();

        long completedChapters = allProgress.stream()
                .filter(p -> p.getIsCompleted() != null && p.getIsCompleted() == 1)
                .count();

        long activeDays = allProgress.stream()
                .filter(p -> p.getLastUpdateTime() != null)
                .map(p -> p.getLastUpdateTime().toLocalDate())
                .distinct()
                .count();

        List<Map<String, Object>> recentLearning = allProgress.stream()
                .limit(10)
                .map(p -> {
                    Map<String, Object> item = new HashMap<>();
                    item.put("chapterId", p.getChapterId());
                    item.put("progress",
                            p.getVideoRate() != null
                                    ? p.getVideoRate().multiply(BigDecimal.valueOf(100)).intValue()
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
     * 获取学生知识掌握度指标。
     */
    public Map<String, Object> getKnowledgeMastery(Long studentId) {
        Map<String, Object> mastery = new HashMap<>();

        List<ChapterProgress> allProgress = progressMapper.selectList(
                new LambdaQueryWrapper<ChapterProgress>()
                        .eq(ChapterProgress::getStudentId, studentId));

        double avgVideoRate = allProgress.stream()
                .filter(p -> p.getVideoRate() != null)
                .mapToDouble(p -> p.getVideoRate().doubleValue())
                .average()
                .orElse(0.0);

        double avgQuizScore = allProgress.stream()
                .filter(p -> p.getQuizScore() != null)
                .mapToInt(ChapterProgress::getQuizScore)
                .average()
                .orElse(0.0);

        // 修复乱码文案，统一使用 UTF-8 可读中文等级。
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
     * 获取学生课程学习轨迹。
     */
    public List<Map<String, Object>> getLearningTrajectory(Long studentId, Long courseId) {
        List<ChapterProgress> progressList = progressMapper.selectList(
                new LambdaQueryWrapper<ChapterProgress>()
                        .eq(ChapterProgress::getStudentId, studentId)
                        .eq(ChapterProgress::getCourseId, courseId)
                        .isNotNull(ChapterProgress::getLastUpdateTime)
                        .orderByAsc(ChapterProgress::getLastUpdateTime));

        Map<LocalDate, List<ChapterProgress>> byDate = progressList.stream()
                .filter(p -> p.getLastUpdateTime() != null)
                .collect(Collectors.groupingBy(
                        p -> p.getLastUpdateTime().toLocalDate(),
                        TreeMap::new,
                        Collectors.toList()));

        List<Map<String, Object>> trajectory = new ArrayList<>();

        for (Map.Entry<LocalDate, List<ChapterProgress>> entry : byDate.entrySet()) {
            Map<String, Object> dayData = new HashMap<>();
            dayData.put("date", entry.getKey().toString());

            int studyMinutes = entry.getValue().stream()
                    .mapToInt(p -> p.getVideoWatchTime() != null ? p.getVideoWatchTime() / 60 : 0)
                    .sum();
            dayData.put("studyMinutes", studyMinutes);

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
     * 获取学生课程测验趋势。
     */
    public List<Map<String, Object>> getQuizScoreTrend(Long studentId, Long courseId) {
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
     * 获取学生课程学情分析。
     */
    public Map<String, Object> getStudentCourseAnalytics(Long studentId, Long courseId) {
        Map<String, Object> analytics = new HashMap<>();

        List<Map<String, Object>> learningTrajectory = getLearningTrajectory(studentId, courseId);
        analytics.put("learningTrajectory", learningTrajectory);

        List<Map<String, Object>> quizScoreTrend = getQuizScoreTrend(studentId, courseId);
        analytics.put("quizScoreTrend", quizScoreTrend);

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
     * 获取课程级分析指标，供教师端看板使用。
     */
    public Map<String, Object> getCourseAnalytics(Long courseId) {
        Map<String, Object> analytics = new HashMap<>();

        List<Chapter> chapters = chapterMapper.selectList(
                new LambdaQueryWrapper<Chapter>()
                        .eq(Chapter::getCourseId, courseId)
                        .orderByAsc(Chapter::getSortOrder));

        List<ChapterProgress> allProgress = progressMapper.selectList(
                new LambdaQueryWrapper<ChapterProgress>()
                        .eq(ChapterProgress::getCourseId, courseId));

        long totalStudents = allProgress.stream()
                .map(ChapterProgress::getStudentId)
                .distinct()
                .count();

        LocalDateTime sevenDaysAgo = LocalDateTime.now().minusDays(7);
        long activeStudents = allProgress.stream()
                .filter(p -> p.getLastUpdateTime() != null
                        && p.getLastUpdateTime().isAfter(sevenDaysAgo))
                .map(ChapterProgress::getStudentId)
                .distinct()
                .count();

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

        double avgQuizScore = allProgress.stream()
                .filter(p -> p.getQuizScore() != null)
                .mapToInt(ChapterProgress::getQuizScore)
                .average()
                .orElse(0.0);

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

        Map<String, Object> overview = new HashMap<>();
        overview.put("totalStudents", totalStudents);
        overview.put("activeStudents", activeStudents);
        overview.put("avgProgress", Math.round(avgProgress * 10) / 10.0);
        overview.put("avgQuizScore", Math.round(avgQuizScore * 10) / 10.0);
        overview.put("completionRate", Math.round(completionRate * 10) / 10.0);
        analytics.put("overview", overview);

        List<Map<String, Object>> chapterAnalytics = new ArrayList<>();
        Map<Long, Long> completedByChapter = allProgress.stream()
                .filter(progress -> progress.getChapterId() != null
                        && progress.getIsCompleted() != null
                        && progress.getIsCompleted() == 1)
                .collect(Collectors.groupingBy(
                        ChapterProgress::getChapterId,
                        Collectors.collectingAndThen(
                                Collectors.mapping(ChapterProgress::getStudentId, Collectors.toSet()),
                                set -> (long) set.size())));

        for (int i = 0; i < chapters.size(); i++) {
            Chapter chapter = chapters.get(i);
            Map<String, Object> chapterData = new HashMap<>();
            chapterData.put("chapterId", chapter.getId());
            chapterData.put("title", chapter.getTitle());
            chapterData.put("sortOrder", chapter.getSortOrder());

            List<ChapterProgress> chapterProgressList = allProgress.stream()
                    .filter(p -> p.getChapterId().equals(chapter.getId()))
                    .toList();

            long chapterCompletedCount = chapterProgressList.stream()
                    .filter(p -> p.getIsCompleted() != null && p.getIsCompleted() == 1)
                    .count();
            double chapterCompletionRate = totalStudents > 0
                    ? (double) chapterCompletedCount / totalStudents * 100
                    : 0;
            chapterData.put("completionRate", Math.round(chapterCompletionRate * 10) / 10.0);

            double avgVideoWatchRate = chapterProgressList.stream()
                    .filter(p -> p.getVideoRate() != null)
                    .mapToDouble(p -> p.getVideoRate().doubleValue() * 100)
                    .average()
                    .orElse(0.0);
            chapterData.put("avgVideoWatchRate", Math.round(avgVideoWatchRate * 10) / 10.0);

            double chapterAvgQuizScore = chapterProgressList.stream()
                    .filter(p -> p.getQuizScore() != null)
                    .mapToInt(ChapterProgress::getQuizScore)
                    .average()
                    .orElse(0.0);
            chapterData.put("avgQuizScore", Math.round(chapterAvgQuizScore * 10) / 10.0);

            double dropOffRate = 0;
            if (i > 0) {
                Chapter prevChapter = chapters.get(i - 1);
                long prevCompleted = completedByChapter.getOrDefault(prevChapter.getId(), 0L);
                if (prevCompleted > 0) {
                    dropOffRate = (1 - (double) chapterCompletedCount / prevCompleted) * 100;
                    dropOffRate = Math.max(0, dropOffRate);
                }
            }
            chapterData.put("dropOffRate", Math.round(dropOffRate * 10) / 10.0);

            chapterAnalytics.add(chapterData);
        }
        analytics.put("chapterAnalytics", chapterAnalytics);

        List<Map<String, Object>> questionDifficulty = new ArrayList<>();

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
            List<ChapterQuiz> quizzes = quizByChapter.getOrDefault(chapter.getId(), Collections.emptyList());

            List<ChapterProgress> chapterProgressList = allProgress.stream()
                    .filter(p -> p.getChapterId().equals(chapter.getId())
                            && p.getQuizScore() != null)
                    .toList();

            if (!quizzes.isEmpty() && !chapterProgressList.isEmpty()) {
                double avgScoreRate = chapterProgressList.stream()
                        .mapToInt(ChapterProgress::getQuizScore)
                        .average()
                        .orElse(0.0);

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
        questionDifficulty.sort((a, b) -> Double.compare(
                (Double) b.get("errorRate"), (Double) a.get("errorRate")));
        analytics.put("questionDifficulty", questionDifficulty);

        Map<String, Object> platformComparison = new HashMap<>();
        platformComparison.put("avgProgress", 65.0);
        platformComparison.put("avgQuizScore", 72.0);
        platformComparison.put("completionRate", 30.0);
        analytics.put("platformComparison", platformComparison);

        return analytics;
    }
}
