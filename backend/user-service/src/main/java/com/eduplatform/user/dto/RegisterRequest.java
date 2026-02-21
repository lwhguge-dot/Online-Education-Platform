package com.eduplatform.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 注册请求。
 */
@Data
public class RegisterRequest {
    @NotBlank(message = "email不能为空")
    @Email(message = "email格式不正确")
    private String email;

    @NotBlank(message = "username不能为空")
    @Size(min = 2, max = 50, message = "username长度需在2到50之间")
    private String username;

    @NotBlank(message = "realName不能为空")
    @Size(min = 2, max = 100, message = "realName长度需在2到100之间")
    private String realName;

    @NotBlank(message = "password不能为空")
    @Size(min = 8, max = 64, message = "password长度需在8到64之间")
    @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).+$", message = "password必须同时包含字母和数字")
    private String password;

    @Pattern(regexp = "^(student|teacher|admin)$", message = "role仅支持student/teacher/admin")
    private String role;
}
