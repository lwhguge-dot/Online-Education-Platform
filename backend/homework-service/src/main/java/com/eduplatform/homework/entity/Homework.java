package com.eduplatform.homework.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 作业实体类
 * 对应数据库表 homeworks，存储作业的基础配置。
 *
 * @author Antigravity
 */
@Data
@TableName("homeworks")
public class Homework {

    /**
     * 作业ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 所属课程ID
     */
    private Long courseId;

    /**
     * 所属章节ID
     */
    private Long chapterId;

    /**
     * 创建教师ID
     */
    private Long teacherId;

    /**
     * 作业标题
     */
    private String title;

    /**
     * 作业详细要求描述
     */
    private String description;

    /**
     * 作业类型 (quiz, subjective, etc.)
     */
    private String homeworkType;

    /**
     * 作业总分
     */
    private Integer totalScore;

    /**
     * 截止提交时间
     */
    private LocalDateTime deadline;

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
