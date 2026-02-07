package com.eduplatform.homework.vo;

import lombok.Data;

/**
 * 作业题目视图对象
 * 用于对外输出题目信息，避免直接暴露持久层实体。
 */
@Data
public class HomeworkQuestionVO {

    /**
     * 题目ID
     */
    private Long id;

    /**
     * 作业ID
     */
    private Long homeworkId;

    /**
     * 题目类型
     */
    private String questionType;

    /**
     * 题干内容
     */
    private String content;

    /**
     * 选项内容
     */
    private String options;

    /**
     * 正确答案
     */
    private String correctAnswer;

    /**
     * 答案解析
     */
    private String answerAnalysis;

    /**
     * 分值
     */
    private Integer score;

    /**
     * 排序权重
     */
    private Integer sortOrder;
}
