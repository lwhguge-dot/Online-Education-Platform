package com.eduplatform.user.dto;

import lombok.Data;
import java.util.List;
import java.util.Map;

@Data
public class TeacherProfileDTO {
    private Long userId;
    private String username;
    private String realName;
    private String email;
    private String phone;
    private String avatar;
    private String title;
    private String department;
    private String introduction;
    private Integer totalStudents;
    private Integer totalCourses;
    
    // 教学科目设置
    private List<String> teachingSubjects;
    
    // 默认评分标准配置
    private GradingCriteria defaultGradingCriteria;
    
    // 仪表盘布局自定义
    private DashboardLayout dashboardLayout;
    
    // 细粒度通知设置
    private NotificationSettings notificationSettings;
    
    @Data
    public static class GradingCriteria {
        private Integer excellentThreshold = 90;  // 优秀分数线
        private Integer goodThreshold = 80;       // 良好分数线
        private Integer passThreshold = 60;       // 及格分数线
        private Boolean autoGradeObjective = true; // 自动批改客观题
        private String feedbackTemplate;          // 反馈模板
    }
    
    @Data
    public static class DashboardLayout {
        private List<String> visibleCards;        // 可见卡片
        private String defaultView = "overview";  // 默认视图
        private Integer refreshInterval = 30;     // 刷新间隔（秒）
    }
    
    @Data
    public static class NotificationSettings {
        private Boolean newStudent = true;        // 新学生选课
        private Boolean homeworkSubmit = true;    // 作业提交
        private Boolean studentQuestion = true;   // 学生提问
        private Boolean systemNotice = true;      // 系统公告
        private Boolean courseReview = true;      // 课程审核结果
        private Boolean deadlineReminder = true;  // 截止日期提醒
        private Boolean atRiskStudent = true;     // 预警学生通知
        private String emailFrequency = "daily";  // 邮件频率: realtime/daily/weekly
    }
}
