package com.eduplatform.progress.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.eduplatform.progress.client.HomeworkServiceClient;
import com.eduplatform.progress.dto.StudentStatsDTO;
import com.eduplatform.progress.entity.Chapter;
import com.eduplatform.progress.entity.ChapterProgress;
import com.eduplatform.progress.mapper.ChapterMapper;
import com.eduplatform.progress.mapper.ChapterProgressMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 学生学情统计与仪表盘服务
 * 负责聚合分布在各章节的学习元数据，构建面向学生的个性化“学习画像”与“成长仪表盘”。
 *
 * 核心指标体系：
 * 1. 时间轴维度：总学习分钟、今日实时分钟、本周/上周对比趋势。
 * 2. 状态机维度：连续学习天数（Streak）计算、每日目标（Goal）监测。
 * 3. 结果集维度：课程报名密度、章节完课率、测验成绩分布。
 * 4. 协同维度：集成 HomeworkServiceClient 抓取具有截止日临界点的紧急任务。
 *
 * @author Antigravity
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StudentStatsService {

    private final ChapterProgressMapper progressMapper;
    private final ChapterMapper chapterMapper;
    private final HomeworkServiceClient homeworkServiceClient;

    /** 默认每日学习目标：60分钟。未来可扩展为用户自定义配置。 */
    private static final int DEFAULT_DAILY_GOAL_MINUTES = 60;

    /**
     * 生成学生全方位学情仪表盘 (Dashboard Core)
     * 集合了实时行为数据与历史趋势分析。
     *
     * @param studentId 学生标识
     * @return 包含 12 项核心指标的 StudentStatsDTO 聚合包
     */
    public StudentStatsDTO getStudentDashboardStats(Long studentId) {
        StudentStatsDTO stats = new StudentStatsDTO();

        // 批量检索该学生的所有进度快照，规避 N+1 查询
        List<ChapterProgress> allProgress = progressMapper.selectList(
                new LambdaQueryWrapper<ChapterProgress>()
                        .eq(ChapterProgress::getStudentId, studentId));

        // 1. 生命总时长：累加所有章节的 VideoWatchTime
        int totalWatchTimeSeconds = allProgress.stream()
                .mapToInt(p -> p.getVideoWatchTime() != null ? p.getVideoWatchTime() : 0)
                .sum();
        stats.setTotalStudyMinutes(totalWatchTimeSeconds / 60);

        // 2. 今日投入：筛选 LastUpdateTime 为今天的记录
        LocalDate today = LocalDate.now();
        int todayWatchTimeSeconds = allProgress.stream()
                .filter(p -> p.getLastUpdateTime() != null &&
                        p.getLastUpdateTime().toLocalDate().equals(today))
                .mapToInt(p -> p.getVideoWatchTime() != null ? p.getVideoWatchTime() : 0)
                .sum();
        stats.setTodayStudyMinutes(todayWatchTimeSeconds / 60);

        // 3. 毅力评估：计算连续不断更天数
        stats.setStreakDays(calculateStreakDays(allProgress));

        // 4. 章节覆盖率：统计 IsCompleted=1 记录数
        long completedCount = allProgress.stream()
                .filter(p -> p.getIsCompleted() != null && p.getIsCompleted() == 1)
                .count();
        stats.setCompletedChapters((int) completedCount);

        // 5. 课程报名密度：基于 CourseId 的去重计数
        long enrolledCourses = allProgress.stream()
                .filter(p -> p.getCourseId() != null)
                .map(ChapterProgress::getCourseId)
                .distinct()
                .count();
        stats.setEnrolledCourses((int) enrolledCourses);

        // 6-7. 时间序列分析：构建本周/上周的 Daily 柱状图数据
        stats.setWeeklyStudyHours(calculateWeeklyStudyHours(allProgress));
        stats.setLastWeekStudyHours(calculateLastWeekStudyHours(allProgress));

        // 8. 环比分析：计算周增长百分比
        calculateWeeklyComparison(stats);

        // 9. 目标驱动：判定今日任务达成状态
        stats.setDailyGoalMinutes(DEFAULT_DAILY_GOAL_MINUTES);
        stats.setGoalAchievedToday(stats.getTodayStudyMinutes() >= DEFAULT_DAILY_GOAL_MINUTES);

        // 10. 成绩快照：提取最近 20 笔测验日志
        stats.setQuizScores(getQuizScores(allProgress));

        // 11-12. 外部协同：获取 Homework 系统的紧迫任务与待办
        stats.setUrgentHomeworks(getUrgentHomeworks(studentId));
        stats.setPendingHomework(getPendingHomeworkCount(studentId));

        return stats;
    }

    /**
     * 计算连续学习天数 (Streak Algorithm)
     * 判定逻辑：从今天开始回溯，若今天无记录则从昨天起，若中断则停止。
     */
    private int calculateStreakDays(List<ChapterProgress> allProgress) {
        if (allProgress.isEmpty()) {
            return 0;
        }

        Set<LocalDate> studyDates = allProgress.stream()
                .filter(p -> p.getLastUpdateTime() != null)
                .map(p -> p.getLastUpdateTime().toLocalDate())
                .collect(Collectors.toSet());

        if (studyDates.isEmpty()) {
            return 0;
        }

        LocalDate today = LocalDate.now();
        int streak = 0;
        LocalDate checkDate = today;

        if (!studyDates.contains(today)) {
            checkDate = today.minusDays(1);
            if (!studyDates.contains(checkDate)) {
                return 0;
            }
        }

        while (studyDates.contains(checkDate)) {
            streak++;
            checkDate = checkDate.minusDays(1);
        }

        return streak;
    }

    /**
     * 计算当前自然周的每日学时分布
     */
    private List<StudentStatsDTO.DailyStudyDTO> calculateWeeklyStudyHours(List<ChapterProgress> allProgress) {
        LocalDate today = LocalDate.now();
        LocalDate monday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));

        Map<LocalDate, Integer> dailySeconds = new HashMap<>();
        for (ChapterProgress p : allProgress) {
            if (p.getLastUpdateTime() != null && p.getVideoWatchTime() != null) {
                LocalDate date = p.getLastUpdateTime().toLocalDate();
                if (!date.isBefore(monday) && !date.isAfter(today)) {
                    dailySeconds.merge(date, p.getVideoWatchTime(), Integer::sum);
                }
            }
        }

        String[] dayNames = { "周一", "周二", "周三", "周四", "周五", "周六", "周日" };
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        List<StudentStatsDTO.DailyStudyDTO> result = new ArrayList<>();

        for (int i = 0; i < 7; i++) {
            LocalDate date = monday.plusDays(i);
            StudentStatsDTO.DailyStudyDTO daily = new StudentStatsDTO.DailyStudyDTO();
            daily.setDay(dayNames[i]);
            daily.setDate(date.format(formatter));

            int seconds = dailySeconds.getOrDefault(date, 0);
            daily.setHours(Math.round(seconds / 360.0) / 10.0);

            result.add(daily);
        }

        return result;
    }

    /**
     * 计算上一自然周的每日学时分布
     */
    private List<StudentStatsDTO.DailyStudyDTO> calculateLastWeekStudyHours(List<ChapterProgress> allProgress) {
        LocalDate today = LocalDate.now();
        LocalDate thisMonday = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate lastMonday = thisMonday.minusWeeks(1);
        LocalDate lastSunday = lastMonday.plusDays(6);

        Map<LocalDate, Integer> dailySeconds = new HashMap<>();
        for (ChapterProgress p : allProgress) {
            if (p.getLastUpdateTime() != null && p.getVideoWatchTime() != null) {
                LocalDate date = p.getLastUpdateTime().toLocalDate();
                if (!date.isBefore(lastMonday) && !date.isAfter(lastSunday)) {
                    dailySeconds.merge(date, p.getVideoWatchTime(), Integer::sum);
                }
            }
        }

        String[] dayNames = { "周一", "周二", "周三", "周四", "周五", "周六", "周日" };
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
        List<StudentStatsDTO.DailyStudyDTO> result = new ArrayList<>();

        for (int i = 0; i < 7; i++) {
            LocalDate date = lastMonday.plusDays(i);
            StudentStatsDTO.DailyStudyDTO daily = new StudentStatsDTO.DailyStudyDTO();
            daily.setDay(dayNames[i]);
            daily.setDate(date.format(formatter));

            int seconds = dailySeconds.getOrDefault(date, 0);
            daily.setHours(Math.round(seconds / 360.0) / 10.0);

            result.add(daily);
        }

        return result;
    }

    /**
     * 计算周增长对比系数 (Weekly Growth Ratio)
     */
    private void calculateWeeklyComparison(StudentStatsDTO stats) {
        int thisWeekMinutes = 0;
        if (stats.getWeeklyStudyHours() != null) {
            thisWeekMinutes = (int) (stats.getWeeklyStudyHours().stream()
                    .mapToDouble(d -> d.getHours() * 60)
                    .sum());
        }
        stats.setThisWeekMinutes(thisWeekMinutes);

        int lastWeekMinutes = 0;
        if (stats.getLastWeekStudyHours() != null) {
            lastWeekMinutes = (int) (stats.getLastWeekStudyHours().stream()
                    .mapToDouble(d -> d.getHours() * 60)
                    .sum());
        }
        stats.setLastWeekMinutes(lastWeekMinutes);

        if (lastWeekMinutes > 0) {
            int change = (int) (((double) (thisWeekMinutes - lastWeekMinutes) / lastWeekMinutes) * 100);
            stats.setWeeklyChange(change);
        } else {
            stats.setWeeklyChange(thisWeekMinutes > 0 ? 100 : 0);
        }
    }

    /**
     * 处理测验成绩时间轴映射
     */
    private List<StudentStatsDTO.QuizScoreDTO> getQuizScores(List<ChapterProgress> allProgress) {
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

        List<ChapterProgress> withQuiz = allProgress.stream()
                .filter(p -> p.getQuizScore() != null && p.getQuizSubmittedAt() != null)
                .sorted((a, b) -> b.getQuizSubmittedAt().compareTo(a.getQuizSubmittedAt()))
                .limit(20)
                .collect(Collectors.toList());

        List<Long> chapterIds = withQuiz.stream()
                .map(ChapterProgress::getChapterId)
                .distinct()
                .collect(Collectors.toList());

        Map<Long, Chapter> chapterMap = new HashMap<>();
        if (!chapterIds.isEmpty()) {
            List<Chapter> chapters = chapterMapper.selectBatchIds(chapterIds);
            chapters.forEach(c -> chapterMap.put(c.getId(), c));
        }

        return withQuiz.stream()
                .map(p -> {
                    StudentStatsDTO.QuizScoreDTO dto = new StudentStatsDTO.QuizScoreDTO();
                    dto.setCourseId(p.getCourseId());
                    dto.setChapterId(p.getChapterId());
                    dto.setScore(p.getQuizScore());
                    dto.setTime(p.getQuizSubmittedAt().format(formatter));

                    Chapter chapter = chapterMap.get(p.getChapterId());
                    if (chapter != null) {
                        dto.setTitle(chapter.getTitle());
                        dto.setCourseName("章节测验");
                    } else {
                        dto.setTitle("章节 " + p.getChapterId());
                        dto.setCourseName("未知课程");
                    }

                    return dto;
                })
                .collect(Collectors.toList());
    }

    /**
     * 跨服务获取作业系统中的红线任务 (Urgent Tasks)
     */
    private List<StudentStatsDTO.UrgentHomeworkDTO> getUrgentHomeworks(Long studentId) {
        List<StudentStatsDTO.UrgentHomeworkDTO> result = new ArrayList<>();

        try {
            Map<String, Object> response = homeworkServiceClient.getStudentUrgentHomeworks(studentId, 2);
            if (response != null && response.get("data") != null) {
                Object data = response.get("data");
                if (data instanceof List) {
                    List<Map<String, Object>> homeworks = (List<Map<String, Object>>) data;
                    for (Map<String, Object> hw : homeworks) {
                        StudentStatsDTO.UrgentHomeworkDTO dto = new StudentStatsDTO.UrgentHomeworkDTO();
                        dto.setId(hw.get("id") != null ? Long.valueOf(hw.get("id").toString()) : null);
                        dto.setTitle(hw.get("title") != null ? hw.get("title").toString() : "");
                        dto.setCourseName(hw.get("courseName") != null ? hw.get("courseName").toString() : "");
                        dto.setDeadline(hw.get("deadline") != null ? hw.get("deadline").toString() : "");
                        dto.setDaysLeft(hw.get("daysLeft") != null ? Long.valueOf(hw.get("daysLeft").toString()) : 0L);
                        result.add(dto);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("获取紧急作业失败: {}", e.getMessage());
        }

        return result;
    }

    /**
     * 获取全课程待办作业余量
     */
    private int getPendingHomeworkCount(Long studentId) {
        try {
            Map<String, Object> response = homeworkServiceClient.getStudentPendingHomeworkCount(studentId);
            if (response != null && response.get("data") != null) {
                return Integer.parseInt(response.get("data").toString());
            }
        } catch (Exception e) {
            log.warn("获取待完成作业数量失败: {}", e.getMessage());
        }
        return 0;
    }
}
