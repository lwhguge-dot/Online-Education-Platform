package com.eduplatform.course.dto;

import lombok.Data;

/**
 * 章节测验题目数据传输对象
 * 用于接收前端创建或更新测验题目的请求参数。
 *
 * @author Antigravity
 */
@Data
public class ChapterQuizDTO {

    /**
     * 题目ID
     */
    private Long id;

    /**
     * 所属章节ID
     */
    private Long chapterId;

    /**
     * 题目内容
     */
    private String question;

    /**
     * 题目类型
     */
    private String questionType;

    /**
     * 选项 (JSON 字符串)
     */
    private String options;

    /**
     * 正确答案
     */
    private String correctAnswer;

    /**
     * 分值
     */
    private Integer score;

    /**
     * 排序权重
     */
    private Integer sortOrder;
}
