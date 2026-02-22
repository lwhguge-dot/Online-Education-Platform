package com.eduplatform.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 密码重置请求。
 */
@Data
public class ResetPasswordRequest {
    @NotBlank(message = "email不能为空")
    @Email(message = "email格式不正确")
    private String email;

    @NotBlank(message = "realName不能为空")
    @Size(max = 100, message = "realName长度不能超过100")
    private String realName;

    @NotBlank(message = "newPassword不能为空")
    @Size(min = 8, max = 64, message = "newPassword长度需在8到64之间")
    @Pattern(
            regexp = "^(?=.*[A-Za-z])(?=.*\\d).+$",
            message = "newPassword必须同时包含字母和数字")
    private String newPassword;
}
