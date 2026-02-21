package com.eduplatform.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 申请密码重置令牌请求。
 */
@Data
public class PasswordResetIssueRequest {
    @NotBlank(message = "email不能为空")
    @Email(message = "email格式不正确")
    private String email;

    @NotBlank(message = "realName不能为空")
    @Size(max = 100, message = "realName长度不能超过100")
    private String realName;
}

