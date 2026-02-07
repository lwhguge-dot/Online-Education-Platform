package com.eduplatform.user.dto;

import lombok.Data;

@Data
public class ResetPasswordRequest {
    private String email;
    private String realName;
    private String newPassword;
}
