package com.eduplatform.course.vo;

import lombok.Data;
import java.time.LocalDateTime;

/**
 * 选课视图对象
 * 用于向前端展示用户的选课及学习概览，实现数据隔离。
 *
 * @author Antigravity
 */
@Data
public class EnrollmentVO {

    /**
     * 选课记录ID
     */
    private Long id;

    /**
     * 学生ID
     */
    private Long studentId;

    /**
     * 课程ID
     */
    private Long courseId;

    /**
     * 选课时间
     */
    private LocalDateTime enrolledAt;

    /**
     * 最后学习时间
     */
    private LocalDateTime lastStudyAt;

    /**
     * 学习进度 (百分比)
     */
    private Integer progress;

    /**
     * 选课状态 (active, completed, dropped)
     */
    private String status;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    private LocalDateTime updatedAt;
}
