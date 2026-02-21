package com.eduplatform.user.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

/**
 * 用户设置数据传输对象
 */
@Data
public class UserSettingsDTO {
    
    // 通知设置
    @Valid
    private NotificationSettings notificationSettings;
    
    // 学习目标
    @Valid
    private StudyGoal studyGoal;
    
    @Data
    public static class NotificationSettings {
        private Boolean homeworkReminder = true;   // 作业提醒
        private Boolean courseUpdate = true;       // 课程更新
        private Boolean teacherReply = true;       // 教师回复
        private Boolean systemNotice = false;      // 系统通知
        private Boolean emailNotify = true;        // 邮件通知
        private Boolean pushNotify = true;         // 推送通知
    }
    
    @Data
    public static class StudyGoal {
        @Min(value = 1, message = "dailyMinutes最小为1")
        @Max(value = 1440, message = "dailyMinutes最大为1440")
        private Integer dailyMinutes = 60;   // 每日学习目标（分钟）
        @Min(value = 1, message = "weeklyHours最小为1")
        @Max(value = 168, message = "weeklyHours最大为168")
        private Integer weeklyHours = 10;    // 每周学习目标（小时）
    }
}
