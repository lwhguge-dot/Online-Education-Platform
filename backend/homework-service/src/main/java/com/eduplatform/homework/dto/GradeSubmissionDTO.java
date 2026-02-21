package com.eduplatform.homework.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 批改作业提交的DTO
 */
@Data
public class GradeSubmissionDTO {
    
    /**
     * 单个题目的评分
     */
    @Data
    public static class QuestionGrade {
        @NotNull(message = "questionId不能为空")
        @Positive(message = "questionId必须为正数")
        private Long questionId;

        @NotNull(message = "score不能为空")
        @PositiveOrZero(message = "score不能为负数")
        private Integer score;

        @Size(max = 2000, message = "feedback长度不能超过2000")
        private String feedback;
    }
    
    /**
     * 题目评分列表
     */
    @Valid
    private List<QuestionGrade> grades;
    
    /**
     * 整体评语
     */
    @Size(max = 5000, message = "overallFeedback长度不能超过5000")
    private String overallFeedback;
    
    /**
     * 批改教师ID
     */
    @Positive(message = "gradedBy必须为正数")
    private Long gradedBy;
}
