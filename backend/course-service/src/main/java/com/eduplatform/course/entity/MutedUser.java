package com.eduplatform.course.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 禁言用户记录实体类
 * 对应数据库表 `muted_users`，记录因违规被限制交互权限的用户名单。
 * 禁言可针对特定的课程生效，支持设定自动解除时间。
 */
@Data
@TableName("muted_users")
public class MutedUser {

    /**
     * 禁言流水 ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 被执行禁言处罚的用户 ID
     */
    private Long userId;

    /**
     * 关联课程 ID (若为 0/Null 则可能为全局禁言，否则仅在该课程讨论区生效)
     */
    private Long courseId;

    /**
     * 执行禁言操作的管理人员 ID
     */
    private Long mutedBy;

    /**
     * 禁言的具体原因 (如：发布广告、言语攻击)
     */
    private String reason;

    /**
     * 处罚开始生效时间
     */
    private LocalDateTime mutedAt;

    /**
     * 预计自动解除禁言的时间 (若为空则代表永久禁言)
     */
    private LocalDateTime unmutedAt;

    /**
     * 处罚当前状态 (1: 有效禁言中, 0: 已撤销或已自动到期)
     */
    private Integer status;
}
