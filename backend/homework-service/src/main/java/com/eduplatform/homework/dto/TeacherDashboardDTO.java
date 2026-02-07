package com.eduplatform.homework.dto;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 教师仪表盘聚合数据DTO
 */
@Data
public class TeacherDashboardDTO {
    
    // 课程统计
    private Integer myCourses;
    private Integer publishedCourses;
    private Integer totalStudents;
    private Integer newStudentsToday;
    
    // 作业统计
    private Integer pendingHomework;
    private Integer weeklyInteractions;
    
    // 紧急事项
    private List<UrgentItem> urgentItems;
    
    // 课程完成率排名
    private List<CourseRanking> courseRankings;
    
    // 周趋势数据
    private WeeklyTrend weeklyTrend;
    
    // 时间戳
    private LocalDateTime timestamp;
    
    @Data
    public static class UrgentItem {
        private String type; // homework, question
        private Long id;
        private String title;
        private String deadline;
        private String studentName;
        private Long courseId;
        private String courseName;
    }
    
    @Data
    public static class CourseRanking {
        private Long courseId;
        private String title;
        private Double completionRate;
        private Integer studentCount;
    }
    
    @Data
    public static class WeeklyTrend {
        private List<String> labels;
        private List<Integer> studentActivity;
        private List<Integer> homeworkSubmissions;
        private List<Integer> quizCompletions;
    }
}
