package com.eduplatform.course.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 选课/报名记录实体类
 * 对应数据库表 `enrollments`，记录学生与课程的绑定关系及学习进度。
 */
@Data
@TableName("enrollments")
public class Enrollment {
    /**
     * 选课记录唯一标识
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 关联的学生用户 ID
     */
    @TableField("student_id")
    private Long studentId;

    /**
     * 关联的课程 ID
     */
    @TableField("course_id")
    private Long courseId;

    /**
     * 首次选课报名时间
     */
    @TableField("enrolled_at")
    private LocalDateTime enrolledAt;

    /**
     * 最近一次进入课程学习的时间
     */
    @TableField("last_study_at")
    private LocalDateTime lastStudyAt;

    /**
     * 课程总体完成进度 (整型：0-100，表示百分比)
     */
    private Integer progress;

    /**
     * 选课状态 (例如：active-学习中, completed-已完成)
     */
    private String status;

    /**
     * 映射 created_at
     */
    @TableField("created_at")
    private LocalDateTime createdAt;

    /**
     * 映射 updated_at
     */
    @TableField("updated_at")
    private LocalDateTime updatedAt;

    /** 状态：学习中 (学生已加入课程但未完成全部章节) */
    public static final String STATUS_ACTIVE = "active";
    /** 状态：已结业 (学生已通过所有强制性章节与测验) */
    public static final String STATUS_COMPLETED = "completed";
    /** 状态：中途退出 (学生已取消选课或被强制移除) */
    public static final String STATUS_DROPPED = "dropped";
}
