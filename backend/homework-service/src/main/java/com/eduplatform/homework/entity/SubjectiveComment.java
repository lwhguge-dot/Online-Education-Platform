package com.eduplatform.homework.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 主观题评论/问答实体类
 * 对应数据库表 subjective_comments，存储针对主观题的讨论、追问及回复。
 *
 * @author Antigravity
 */
@Data
@TableName("subjective_comments")
public class SubjectiveComment {

    /**
     * 评论ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 关联的作业题目ID
     */
    private Long questionId;

    /**
     * 发言用户ID
     */
    private Long userId;

    /**
     * 父级评论ID（用于楼中楼回复）
     */
    private Long parentId;

    /**
     * 评论/回复内容
     */
    private String content;

    /**
     * 是否标记为标准答案/采纳答案 (1:是, 0:否)
     */
    private Integer isAnswer;

    /**
     * 是否置顶 (1:是, 0:否)
     */
    private Integer isTop;

    /**
     * 获赞总数
     */
    private Integer likeCount;

    /**
     * 状态 (1:显示, 0:隐藏/待审)
     */
    private Integer status;

    /**
     * 回复状态 (pending: 待处理, answered: 已回复, follow_up: 追问中)
     */
    private String answerStatus;

    /**
     * 被回复/被采纳的时间
     */
    private LocalDateTime answeredAt;

    /**
     * 执行回复操作的教师/管理员ID
     */
    private Long answeredBy;

    /**
     * 冗余关联课程ID
     */
    private Long courseId;

    /**
     * 冗余关联章节ID
     */
    private Long chapterId;

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
