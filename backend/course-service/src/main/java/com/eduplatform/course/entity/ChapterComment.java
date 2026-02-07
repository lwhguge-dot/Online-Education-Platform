package com.eduplatform.course.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 章节评论实体类
 */
/**
 * 章节互动评论实体类
 * 对应数据库表 `chapter_comments`，支持课程章节下的即时评论、回复及置顶功能。
 */
@Data
@TableName("chapter_comments")
public class ChapterComment {
    /**
     * 评论唯一标识
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 所属章节 ID
     */
    private Long chapterId;

    /**
     * 所属课程 ID (冗余设计，方便课程维度快速清理评论)
     */
    private Long courseId;

    /**
     * 发表评论的用户 ID
     */
    private Long userId;

    /**
     * 父评论 ID (若为 0 或 Null 则代表顶级评论，否则为回复)
     */
    private Long parentId;

    /**
     * 评论正文内容 (支持纯文本及基础表情)
     */
    private String content;

    /**
     * 累计获赞数 (异步统计字段)
     */
    private Integer likeCount;

    /**
     * 下属回复总数
     */
    private Integer replyCount;

    /**
     * 置顶状态 (1: 已置顶, 0: 常规)
     */
    private Integer isPinned;

    /**
     * 评论可见性状态 (1: 正常, 0: 违规屏蔽/删除)
     */
    private Integer status;

    /**
     * 评论发布时间
     */
    @TableField("created_at")
    private LocalDateTime createdAt;

    /**
     * 最后修改时间
     */
    @TableField("updated_at")
    private LocalDateTime updatedAt;

    /** UI 辅助字段：发布者昵称 (不映射数据库) */
    @TableField(exist = false)
    private String userName;

    /** UI 辅助字段：发布者头像 URL (不映射数据库) */
    @TableField(exist = false)
    private String userAvatar;

    /** UI 辅助字段：当前登录用户是否已点赞 (不映射数据库) */
    @TableField(exist = false)
    private Boolean isLiked;
}
