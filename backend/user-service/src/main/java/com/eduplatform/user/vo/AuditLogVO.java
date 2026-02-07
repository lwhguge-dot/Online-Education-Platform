package com.eduplatform.user.vo;

import java.time.LocalDateTime;
import lombok.Data;

/**
 * 审计日志视图对象。
 * 设计意图：隔离持久层实体，避免控制层直接返回数据库结构。
 */
@Data
public class AuditLogVO {
    /**
     * 审计记录唯一标识。
     */
    private Long id;
    /**
     * 操作人 ID。
     */
    private Long operatorId;
    /**
     * 操作人名称。
     */
    private String operatorName;
    /**
     * 行为类型标识。
     */
    private String actionType;
    /**
     * 目标对象类型。
     */
    private String targetType;
    /**
     * 目标对象 ID。
     */
    private Long targetId;
    /**
     * 目标对象名称。
     */
    private String targetName;
    /**
     * 变更详情。
     */
    private String details;
    /**
     * 操作来源 IP。
     */
    private String ipAddress;
    /**
     * 记录创建时间。
     */
    private LocalDateTime createdAt;
}
