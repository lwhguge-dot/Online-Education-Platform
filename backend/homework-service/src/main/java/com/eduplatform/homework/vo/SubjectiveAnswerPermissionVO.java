package com.eduplatform.homework.vo;

import lombok.Data;
import java.time.LocalDateTime;

/**
 * 学生答案发布权限视图对象
 * 用于对外输出答题权限状态，避免直接暴露实体。
 */
@Data
public class SubjectiveAnswerPermissionVO {

    /**
     * 记录ID
     */
    private Long id;

    /**
     * 学生ID
     */
    private Long studentId;

    /**
     * 题目ID
     */
    private Long questionId;

    /**
     * 答案内容
     */
    private String answerContent;

    /**
     * 答案状态
     */
    private Integer answerStatus;

    /**
     * 评论区可见标识
     */
    private Integer commentVisible;

    /**
     * 答案提交时间
     */
    private LocalDateTime answeredAt;
}
