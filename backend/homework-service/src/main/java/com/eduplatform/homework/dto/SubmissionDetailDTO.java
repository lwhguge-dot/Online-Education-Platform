package com.eduplatform.homework.dto;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 提交详情DTO（用于批改工作台）
 */
@Data
public class SubmissionDetailDTO {
    
    /**
     * 提交基本信息
     */
    @Data
    public static class SubmissionInfo {
        private Long id;
        private Long studentId;
        private String studentName;
        private Long homeworkId;
        private String homeworkTitle;
        private String submitStatus;
        private Integer objectiveScore;
        private Integer subjectiveScore;
        private Integer totalScore;
        private LocalDateTime submittedAt;
        private LocalDateTime gradedAt;
        private String feedback;
    }
    
    /**
     * 答案详情
     */
    @Data
    public static class AnswerDetail {
        private Long questionId;
        private String questionType;
        private String questionContent;
        private String options;
        private String correctAnswer;
        private String studentAnswer;
        private Integer score;
        private Integer maxScore;
        private Integer isCorrect;
        private String aiFeedback;
        private String teacherFeedback;
        private Integer sortOrder;
    }
    
    private SubmissionInfo submission;
    private List<AnswerDetail> answers;
}
