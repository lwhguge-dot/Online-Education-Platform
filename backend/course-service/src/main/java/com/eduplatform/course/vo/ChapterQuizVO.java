package com.eduplatform.course.vo;

import lombok.Data;
import java.time.LocalDateTime;

/**
 * 章节测验题目视图对象
 * 用于向前端展示测验题目，实现数据隔离。注意：正确答案通常不应在 VO 中返回，除非是教师端预览。
 *
 * @author Antigravity
 */
@Data
public class ChapterQuizVO {

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
     * 题目类型 (SINGLE_CHOICE, MULTI_CHOICE, etc.)
     */
    private String questionType;

    /**
     * 选项 (JSON 字符串)
     */
    private String options;

    /**
     * 分值
     */
    private Integer score;

    /**
     * 排序权重
     */
    private Integer sortOrder;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;
}
