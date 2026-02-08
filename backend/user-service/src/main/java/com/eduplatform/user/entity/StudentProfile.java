package com.eduplatform.user.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.eduplatform.user.mybatis.JsonbTypeHandler;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 学生扩展信息实体
 * 对应数据库表 student_profiles，存储学生的学业统计、学校信息及个性化配置。
 */
@Data
@TableName(value = "student_profiles", autoResultMap = true)
public class StudentProfile {
    /**
     * 扩展资料ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 关联的用户ID
     */
    private Long userId;

    /**
     * 所在年级 (例如: 大一, 高三)
     */
    private String grade;

    /**
     * 就读学校名称
     */
    private String school;

    /**
     * 累计学习天数
     */
    private Integer studyDays;

    /**
     * 累计学习总时长（单位：分钟）
     */
    private Integer totalStudyTime;

    /**
     * 通知推送设置 (JSON 格式存储，如：{"email":true, "browser":false})
     */
    @TableField(value = "notification_settings", typeHandler = JsonbTypeHandler.class)
    private String notificationSettings;

    /**
     * 学习目标 (JSON 格式存储，如：{"courseId":1, "targetScore":90})
     */
    @TableField(value = "study_goal", typeHandler = JsonbTypeHandler.class)
    private String studyGoal;

    /**
     * 记录创建时间
     */
    @TableField("created_at")
    private LocalDateTime createdAt;

    /**
     * 记录最后更新时间
     */
    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
