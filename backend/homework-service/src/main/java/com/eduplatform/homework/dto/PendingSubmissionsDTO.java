package com.eduplatform.homework.dto;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 待批改提交列表DTO
 */
@Data
public class PendingSubmissionsDTO {
    
    /**
     * 作业基本信息
     */
    @Data
    public static class HomeworkInfo {
        private Long id;
        private String title;
        private Integer totalScore;
        private LocalDateTime deadline;
    }
    
    /**
     * 提交记录摘要
     */
    @Data
    public static class SubmissionSummary {
        private Long id;
        private Long studentId;
        private String studentName;
        private LocalDateTime submittedAt;
        private Integer objectiveScore;
        private Integer subjectiveScore;
        private String status;
        private Boolean hasUngraded;
    }
    
    private HomeworkInfo homework;
    private List<SubmissionSummary> submissions;
    private Integer gradedCount;
    private Integer totalCount;
}
