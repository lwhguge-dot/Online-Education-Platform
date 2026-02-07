package com.eduplatform.user.vo;

import lombok.Data;
import java.time.LocalDateTime;

/**
 * 用户会话视图对象
 * 用于对外输出会话信息，避免直接暴露持久层实体。
 */
@Data
public class UserSessionVO {

    /**
     * 会话记录ID
     */
    private Long id;

    /**
     * 关联的用户ID
     */
    private Long userId;

    /**
     * JWT Token 的唯一标识
     */
    private String jti;

    /**
     * 会话状态 (ONLINE/OFFLINE)
     */
    private String status;

    /**
     * 登录时间
     */
    private LocalDateTime loginTime;

    /**
     * 最后活跃时间
     */
    private LocalDateTime lastActiveTime;

    /**
     * 登出时间
     */
    private LocalDateTime logoutTime;
}
