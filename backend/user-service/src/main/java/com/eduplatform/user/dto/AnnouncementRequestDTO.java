package com.eduplatform.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
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
    @NotBlank(message = "title不能为空")
    @Size(max = 200, message = "title长度不能超过200")
    private String title;

    /**
     * 内容
     */
    @NotBlank(message = "content不能为空")
    @Size(max = 10000, message = "content长度不能超过10000")
    private String content;

    /**
     * 目标受众 (ALL/STUDENT/TEACHER)
     */
    @Pattern(regexp = "^(ALL|STUDENT|TEACHER)$", message = "targetAudience仅支持ALL/STUDENT/TEACHER")
    private String targetAudience;

    /**
     * 关联课程ID
     */
    @Positive(message = "courseId必须为正数")
    private Long courseId;

    /**
     * 是否置顶
     */
    private Boolean isPinned;

    /**
     * 发布状态 (DRAFT/PUBLISHED/SCHEDULED)
     */
    @Pattern(regexp = "^(DRAFT|PUBLISHED|SCHEDULED|EXPIRED)$", message = "status仅支持DRAFT/PUBLISHED/SCHEDULED/EXPIRED")
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
