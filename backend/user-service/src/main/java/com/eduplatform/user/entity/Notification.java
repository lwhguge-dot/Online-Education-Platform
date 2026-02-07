package com.eduplatform.user.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 系统通知实体
 * 对应 notifications 表，持久化所有面向用户的通知记录。
 *
 * @author Antigravity
 */
@Data
@TableName("notifications")
public class Notification {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 接收用户ID */
    private Long userId;

    /** 通知标题 */
    private String title;

    /** 通知内容 */
    private String content;

    /** 通知类型：system / course / homework / comment */
    private String type;

    /** 是否已读：0未读 1已读 */
    private Integer isRead;

    /** 关联业务ID（如作业ID、课程ID等） */
    private Long relatedId;

    /** 创建时间 */
    private LocalDateTime createdAt;
}
