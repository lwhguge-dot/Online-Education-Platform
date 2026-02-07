package com.eduplatform.homework.dto;

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
        private Long questionId;
        private Integer score;
        private String feedback;
    }
    
    /**
     * 题目评分列表
     */
    private List<QuestionGrade> grades;
    
    /**
     * 整体评语
     */
    private String overallFeedback;
    
    /**
     * 批改教师ID
     */
    private Long gradedBy;
}
