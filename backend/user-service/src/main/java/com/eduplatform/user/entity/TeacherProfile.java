package com.eduplatform.user.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 教师扩展信息实体
 * 对应数据库表 teacher_profiles，存储教师的任教背景、统计数据及个性化教学配置。
 */
@Data
@TableName("teacher_profiles")
public class TeacherProfile {
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
     * 职称 (例如: 教授, 特级教师)
     */
    private String title;

    /**
     * 所属部门/学院
     */
    private String department;

    /**
     * 擅长科目/研究方向
     */
    private String subjects;

    /**
     * 教师个人简介
     */
    private String introduction;

    /**
     * 累计学生人数
     */
    private Integer totalStudents;

    /**
     * 累计开设课程数
     */
    private Integer totalCourses;

    /**
     * 教学科目设置 (JSON 格式存储)
     */
    private String teachingSubjects;

    /**
     * 默认评分标准配置 (JSON 格式存储)
     */
    private String defaultGradingCriteria;

    /**
     * 仪表盘布局自定义配置 (JSON 格式存储)
     */
    private String dashboardLayout;

    /**
     * 细粒度通知推送设置 (JSON 格式存储)
     */
    private String notificationSettings;

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
