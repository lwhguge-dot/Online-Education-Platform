package com.eduplatform.user.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 公告阅读记录实体类
 * 对应数据库表 announcement_reads，用于追踪用户对特定公告的阅读状态，实现“红点”提醒功能。
 */
@Data
@TableName("announcement_reads")
public class AnnouncementRead {
    /**
     * 阅读记录ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 关联的公告ID
     */
    @TableField("announcement_id")
    private Long announcementId;

    /**
     * 阅读者的用户ID
     */
    @TableField("user_id")
    private Long userId;

    /**
     * 首次阅读的时间
     */
    @TableField("read_at")
    private LocalDateTime readAt;
}
