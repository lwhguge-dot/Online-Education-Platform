package com.eduplatform.user.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Data;
import java.util.List;

@Data
public class TeacherProfileDTO {
    @Positive(message = "userId必须为正数")
    private Long userId;
    @Size(max = 50, message = "username长度不能超过50")
    private String username;
    @Size(max = 100, message = "realName长度不能超过100")
    private String realName;
    @Email(message = "email格式不正确")
    @Size(max = 255, message = "email长度不能超过255")
    private String email;
    @Size(max = 20, message = "phone长度不能超过20")
    private String phone;
    @Size(max = 500, message = "avatar长度不能超过500")
    private String avatar;
    @Size(max = 100, message = "title长度不能超过100")
    private String title;
    @Size(max = 100, message = "department长度不能超过100")
    private String department;
    @Size(max = 2000, message = "introduction长度不能超过2000")
    private String introduction;
    @PositiveOrZero(message = "totalStudents不能为负数")
    private Integer totalStudents;
    @PositiveOrZero(message = "totalCourses不能为负数")
    private Integer totalCourses;
    
    // 教学科目设置
    @Size(max = 30, message = "teachingSubjects最多支持30项")
    private List<String> teachingSubjects;
    
    // 默认评分标准配置
    @Valid
    private GradingCriteria defaultGradingCriteria;
    
    // 仪表盘布局自定义
    @Valid
    private DashboardLayout dashboardLayout;
    
    // 细粒度通知设置
    @Valid
    private NotificationSettings notificationSettings;
    
    @Data
    public static class GradingCriteria {
        @Min(value = 0, message = "excellentThreshold最小为0")
        @Max(value = 100, message = "excellentThreshold最大为100")
        private Integer excellentThreshold = 90;  // 优秀分数线
        @Min(value = 0, message = "goodThreshold最小为0")
        @Max(value = 100, message = "goodThreshold最大为100")
        private Integer goodThreshold = 80;       // 良好分数线
        @Min(value = 0, message = "passThreshold最小为0")
        @Max(value = 100, message = "passThreshold最大为100")
        private Integer passThreshold = 60;       // 及格分数线
        private Boolean autoGradeObjective = true; // 自动批改客观题
        @Size(max = 2000, message = "feedbackTemplate长度不能超过2000")
        private String feedbackTemplate;          // 反馈模板
    }
    
    @Data
    public static class DashboardLayout {
        @Size(max = 20, message = "visibleCards最多支持20项")
        private List<String> visibleCards;        // 可见卡片
        @Size(max = 50, message = "defaultView长度不能超过50")
        private String defaultView = "overview";  // 默认视图
        @Min(value = 5, message = "refreshInterval最小为5秒")
        @Max(value = 3600, message = "refreshInterval最大为3600秒")
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
        @Pattern(regexp = "^(realtime|daily|weekly)$", message = "emailFrequency仅支持realtime/daily/weekly")
        private String emailFrequency = "daily";  // 邮件频率: realtime/daily/weekly
    }
}
