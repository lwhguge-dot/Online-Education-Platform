package com.eduplatform.user.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 公告创建/更新请求 DTO
 * 用于接收系统级公告的新增与修改请求，避免控制器直接接收实体。
 */
@Data
public class AnnouncementRequestDTO {

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
     * 发布时间
     */
    private LocalDateTime publishTime;

    /**
     * 过期时间
     */
    private LocalDateTime expireTime;
}
