package com.eduplatform.course.dto;

import lombok.Data;

/**
 * 审计日志写入请求（供 Feign 调用 user-service）。
 */
@Data
public class AuditLogRequest {

    /**
     * 操作人ID。
     */
    private Long operatorId;

    /**
     * 操作人名称。
     */
    private String operatorName;

    /**
     * 操作类型。
     */
    private String actionType;

    /**
     * 目标类型。
     */
    private String targetType;

    /**
     * 目标ID。
     */
    private Long targetId;

    /**
     * 目标名称。
     */
    private String targetName;

    /**
     * 操作详情。
     */
    private String details;

    /**
     * 来源IP。
     */
    private String ipAddress;
}
