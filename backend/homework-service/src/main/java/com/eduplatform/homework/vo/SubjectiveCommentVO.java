package com.eduplatform.homework.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 主观题评论/问答视图对象
 * 用于 API 响应。
 *
 * @author Antigravity
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubjectiveCommentVO {

    /**
     * ID
     */
    private Long id;

    /**
     * 题目ID
     */
    private Long questionId;

    /**
     * 发言用户ID
     */
    private Long userId;

    /**
     * 父级ID（回复的目标）
     */
    private Long parentId;

    /**
     * 内容
     */
    private String content;

    /**
     * 是否为标准/教师答案
     */
    private Integer isAnswer;

    /**
     * 是否置顶
     */
    private Integer isTop;

    /**
     * 点赞数
     */
    private Integer likeCount;

    /**
     * 状态（1:正常, 0:删除/禁用）
     */
    private Integer status;

    /**
     * 回复状态
     */
    private String answerStatus;

    /**
     * 回复时间
     */
    private LocalDateTime answeredAt;

    /**
     * 回复人ID
     */
    private Long answeredBy;

    /**
     * 课程ID
     */
    private Long courseId;

    /**
     * 章节ID
     */
    private Long chapterId;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;
}
