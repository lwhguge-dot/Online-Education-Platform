package com.eduplatform.course.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 评论点赞记录实体类
 * 对应数据库表 `comment_likes`，记录用户对章节评论的低频/高频互动行为。
 * 业务逻辑：单一用户对单一评论仅允许存在一条有效记录。
 */
@Data
@TableName("comment_likes")
public class CommentLike {
    /**
     * 点赞流水唯一标识
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 被点赞的评论 ID
     */
    private Long commentId;

    /**
     * 执行点赞操作的用户 ID
     */
    private Long userId;

    /**
     * 点赞动作发生的物理时间
     */
    @TableField("created_at")
    private LocalDateTime createdAt;
}
