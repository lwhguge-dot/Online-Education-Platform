package com.eduplatform.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 确认密码重置请求。
 */
@Data
public class PasswordResetConfirmRequest {
    @NotBlank(message = "resetToken不能为空")
    @Size(min = 16, max = 128, message = "resetToken长度不合法")
    private String resetToken;

    @NotBlank(message = "newPassword不能为空")
    @Size(min = 8, max = 64, message = "newPassword长度需在8到64之间")
    @Pattern(
            regexp = "^(?=.*[A-Za-z])(?=.*\\d).+$",
            message = "newPassword必须同时包含字母和数字")
    private String newPassword;
}

