package com.eduplatform.user.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 密码重置令牌响应。
 */
@Data
@AllArgsConstructor
public class PasswordResetTokenResponse {
    private String resetToken;
}

