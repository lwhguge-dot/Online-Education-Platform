package com.eduplatform.user.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 用户视图对象
 * 用于 API 响应，隐藏密码等敏感信息。
 *
 * @author Antigravity
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserVO {

    /**
     * 用户ID
     */
    private Long id;

    /**
     * 电子邮箱
     */
    private String email;

    /**
     * 用户名（昵称）
     */
    private String username;

    /**
     * 真实姓名
     */
    private String name;

    /**
     * 角色 (admin, teacher, student)
     */
    private String role;

    /**
     * 头像URL
     */
    private String avatar;

    /**
     * 手机号
     */
    private String phone;

    /**
     * 出生日期
     */
    private LocalDate birthday;

    /**
     * 性别
     */
    private String gender;

    /**
     * 账号状态 (1:正常, 0:禁用)
     */
    private Integer status;

    /**
     * 最后登录时间
     */
    private LocalDateTime lastLoginAt;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;
}
