package com.eduplatform.user.dto;

import lombok.Data;

/**
 * 用户设置数据传输对象
 */
@Data
public class UserSettingsDTO {
    
    // 通知设置
    private NotificationSettings notificationSettings;
    
    // 学习目标
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
        private Integer dailyMinutes = 60;   // 每日学习目标（分钟）
        private Integer weeklyHours = 10;    // 每周学习目标（小时）
    }
}
