package com.eduplatform.user.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 用户状态更新请求。
 */
@Data
public class UserStatusUpdateRequest {

    /**
     * 目标状态值。
     */
    @NotNull(message = "status不能为空")
    @Min(value = 0, message = "status仅支持0或1")
    @Max(value = 1, message = "status仅支持0或1")
    private Integer status;
}
