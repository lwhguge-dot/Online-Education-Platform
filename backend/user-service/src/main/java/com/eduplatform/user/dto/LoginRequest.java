package com.eduplatform.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 登录请求。
 */
@Data
public class LoginRequest {
    @NotBlank(message = "email不能为空")
    @Email(message = "email格式不正确")
    private String email;

    @NotBlank(message = "password不能为空")
    @Size(min = 6, max = 64, message = "password长度需在6到64之间")
    private String password;
}
