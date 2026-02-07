package com.eduplatform.user.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 用户会话实体类
 * 对应数据库表 user_session，用于记录用户的登录状态、JWT 标识及活跃时间，支持强制下线功能。
 */
@Data
@TableName("user_session")
public class UserSession {
    /**
     * 会话记录ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 关联的用户ID
     */
    private Long userId;

    /**
     * JWT Token 的唯一标识 (jti)，用于黑名单校验
     */
    private String jti;

    /**
     * 会话状态 (ONLINE: 在线, OFFLINE: 已下线)
     */
    private String status;

    /**
     * 登录时间
     */
    @TableField("login_time")
    private LocalDateTime loginTime;

    /**
     * 最后一次请求活跃时间
     */
    @TableField("last_active_time")
    private LocalDateTime lastActiveTime;

    /**
     * 登出/注销时间
     */
    @TableField("logout_time")
    private LocalDateTime logoutTime;

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

    /** 在线状态常量 */
    public static final String STATUS_ONLINE = "ONLINE";
    /** 下线状态常量 */
    public static final String STATUS_OFFLINE = "OFFLINE";
}
