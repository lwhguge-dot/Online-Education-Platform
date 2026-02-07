package com.eduplatform.user.dto;

import lombok.Data;

@Data
public class RegisterRequest {
    private String email;
    private String username;
    private String realName;
    private String password;
    private String role;
}
