package com.eduplatform.user.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 审计日志实体类
 * 对应数据库表 audit_logs，记录系统中关键的管理操作行为，用于安全审计。
 */
@Data
@TableName("audit_logs")
public class AuditLog {
    /**
     * 日志ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 操作执行人用户ID
     */
    private Long operatorId;

    /**
     * 操作执行人用户名/账号
     */
    private String operatorName;

    /**
     * 操作动作类型 (例如: USER_ENABLE, COURSE_APPROVE, SYSTEM_CONFIG_UPDATE)
     */
    private String actionType;

    /**
     * 被操作对象类型 (例如: USER, COURSE, ANNOUNCEMENT)
     */
    private String targetType;

    /**
     * 被操作对象的唯一标识 ID
     */
    private Long targetId;

    /**
     * 被操作对象的展示名称/标题
     */
    private String targetName;

    /**
     * 操作详细描述或变动记录 (通常为文本或 JSON 格式)
     */
    private String details;

    /**
     * 发起操作请求的客户端 IP 地址
     */
    private String ipAddress;

    /**
     * 审计记录生成时间
     */
    @TableField("created_at")
    private LocalDateTime createdAt;
}
