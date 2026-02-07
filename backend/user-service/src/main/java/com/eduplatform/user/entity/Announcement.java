package com.eduplatform.user.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 系统公告实体类
 * 对应数据库表 announcements，存储全平台公告或特定课程公告。
 */
@Data
@TableName("announcements")
public class Announcement {
    /**
     * 公告ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 公告标题
     */
    private String title;

    /**
     * 公告详细内容
     */
    private String content;

    /**
     * 目标受众：ALL (所有人), TEACHER (教师), STUDENT (学生)
     */
    @TableField("target_audience")
    private String targetAudience;

    /**
     * 关联课程ID (NULL 表示全局系统公告)
     */
    @TableField("course_id")
    private Long courseId;

    /**
     * 发布状态：DRAFT (草稿), SCHEDULED (已排期), PUBLISHED (已发布), EXPIRED (已过期)
     */
    private String status;

    /**
     * 是否置顶显示
     */
    @TableField("is_pinned")
    private Boolean isPinned;

    /**
     * 发布时间 (用于定时发布)
     */
    @TableField("publish_time")
    private LocalDateTime publishTime;

    /**
     * 过期时间 (过期后自动转为 EXPIRED 状态)
     */
    @TableField("expire_time")
    private LocalDateTime expireTime;

    /**
     * 累计阅读次数统计
     */
    @TableField("read_count")
    private Integer readCount;

    /**
     * 创建该公告的管理人员/教师 ID
     */
    @TableField("created_by")
    private Long createdBy;

    /**
     * 创建人展示名称 (非持久化字段，由 Service 层填充)
     */
    @TableField(exist = false)
    private String creatorName;

    /**
     * 关联课程标题 (非持久化字段，由 Service 层填充)
     */
    @TableField(exist = false)
    private String courseName;

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
