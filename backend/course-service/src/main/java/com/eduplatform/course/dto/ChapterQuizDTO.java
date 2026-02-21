package com.eduplatform.course.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
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
    @NotBlank(message = "question不能为空")
    @Size(max = 5000, message = "question长度不能超过5000")
    private String question;

    /**
     * 题目类型
     */
    @NotBlank(message = "questionType不能为空")
    @Size(max = 32, message = "questionType长度不能超过32")
    private String questionType;

    /**
     * 选项 (JSON 字符串)
     */
    @Size(max = 20000, message = "options长度不能超过20000")
    private String options;

    /**
     * 正确答案
     */
    @Size(max = 2000, message = "correctAnswer长度不能超过2000")
    private String correctAnswer;

    /**
     * 分值
     */
    @Positive(message = "score必须为正数")
    private Integer score;

    /**
     * 排序权重
     */
    @Positive(message = "sortOrder必须为正数")
    private Integer sortOrder;
}
