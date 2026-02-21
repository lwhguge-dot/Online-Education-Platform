package com.eduplatform.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 审计日志写入请求。
 */
@Data
public class CreateAuditLogRequest {

    /**
     * 操作人ID（非内部调用时会被服务端身份覆盖）。
     */
    private Long operatorId;

    /**
     * 操作人名称（非内部调用时会被服务端身份覆盖）。
     */
    @Size(max = 100, message = "operatorName长度不能超过100")
    private String operatorName;

    /**
     * 操作类型。
     */
    @NotBlank(message = "actionType不能为空")
    @Size(max = 100, message = "actionType长度不能超过100")
    private String actionType;

    /**
     * 目标类型。
     */
    @NotBlank(message = "targetType不能为空")
    @Size(max = 100, message = "targetType长度不能超过100")
    private String targetType;

    /**
     * 目标ID。
     */
    private Long targetId;

    /**
     * 目标名称。
     */
    @Size(max = 255, message = "targetName长度不能超过255")
    private String targetName;

    /**
     * 操作详情。
     */
    @Size(max = 2000, message = "details长度不能超过2000")
    private String details;

    /**
     * 来源IP。
     */
    @Size(max = 64, message = "ipAddress长度不能超过64")
    private String ipAddress;
}
