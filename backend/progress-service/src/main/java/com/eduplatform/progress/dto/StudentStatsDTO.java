package com.eduplatform.progress.dto;

import lombok.Data;
import java.util.List;

/**
 * 学生学习统计数据传输对象
 */
@Data
public class StudentStatsDTO {
    
    // 基础统计
    private Integer totalStudyMinutes;      // 总学习时长（分钟）
    private Integer todayStudyMinutes;      // 今日学习时长（分钟）
    private Integer streakDays;             // 连续学习天数
    private Integer completedChapters;      // 已完成章节数
    private Integer enrolledCourses;        // 已报名课程数
    private Integer pendingHomework;        // 待完成作业数
    
    // 今日目标相关
    private Integer dailyGoalMinutes;       // 每日目标学习分钟数
    private Boolean goalAchievedToday;      // 今日是否达成目标
    
    // 本周学习时长（按天分组）
    private List<DailyStudyDTO> weeklyStudyHours;
    
    // 上周学习时长（按天分组）- 用于周对比
    private List<DailyStudyDTO> lastWeekStudyHours;
    
    // 周对比数据
    private Integer thisWeekMinutes;        // 本周总学习分钟数
    private Integer lastWeekMinutes;        // 上周总学习分钟数
    private Integer weeklyChange;           // 周变化百分比
    
    // 紧急作业列表
    private List<UrgentHomeworkDTO> urgentHomeworks;
    
    // 测验成绩列表
    private List<QuizScoreDTO> quizScores;
    
    @Data
    public static class DailyStudyDTO {
        private String day;      // 周几（周一、周二...）
        private Double hours;    // 学习小时数
        private String date;     // 日期 yyyy-MM-dd
    }
    
    @Data
    public static class QuizScoreDTO {
        private Long courseId;
        private Long chapterId;
        private String title;        // 章节标题
        private String courseName;   // 课程名称
        private Integer score;       // 测验分数
        private String time;         // 提交时间
    }
    
    @Data
    public static class UrgentHomeworkDTO {
        private Long id;
        private String title;
        private String courseName;
        private String deadline;
        private Long daysLeft;
    }
}
