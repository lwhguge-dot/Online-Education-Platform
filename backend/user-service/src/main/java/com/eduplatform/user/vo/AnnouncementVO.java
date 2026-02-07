package com.eduplatform.user.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 公告视图对象
 * 用于对外输出公告数据，避免直接暴露持久层实体。
 */
@Data
public class AnnouncementVO {

    /**
     * 公告ID
     */
    private Long id;

    /**
     * 标题
     */
    private String title;

    /**
     * 内容
     */
    private String content;

    /**
     * 目标受众 (ALL/STUDENT/TEACHER)
     */
    private String targetAudience;

    /**
     * 关联课程ID
     */
    private Long courseId;

    /**
     * 是否置顶
     */
    private Boolean isPinned;

    /**
     * 发布状态 (DRAFT/PUBLISHED/SCHEDULED)
     */
    private String status;

    /**
     * 阅读次数
     */
    private Integer readCount;

    /**
     * 创建人ID
     */
    private Long createdBy;

    /**
     * 发布时间
     */
    private LocalDateTime publishTime;

    /**
     * 过期时间
     */
    private LocalDateTime expireTime;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    private LocalDateTime updatedAt;
}
