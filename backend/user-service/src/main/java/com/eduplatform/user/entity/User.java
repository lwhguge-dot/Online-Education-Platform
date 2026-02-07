package com.eduplatform.user.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 用户实体类
 * 对应数据库表 users，存储用户的账号信息、个人资料及状态。
 *
 * @author Antigravity
 */
@Data
@TableName("users")
public class User {

    /**
     * 用户ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 电子邮箱（用于登录）
     */
    private String email;

    /**
     * 用户名/昵称
     */
    private String username;

    /**
     * 登录密码（加密存储）
     */
    private String password;

    /**
     * 真实姓名
     */
    private String name;

    /**
     * 用户角色 (admin, teacher, student)
     */
    private String role;

    /**
     * 头像图片链接
     */
    private String avatar;

    /**
     * 手机号码
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
     * 账号状态 (1: 正常, 0: 禁用, -1: 已删除)
     */
    private Integer status;

    /**
     * 最后登录时间
     */
    @TableField("last_login_at")
    private LocalDateTime lastLoginAt;

    /**
     * 记录创建时间
     */
    @TableField("created_at")
    private LocalDateTime createdAt;

    /**
     * 记录最后更新时间
     */
    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
