package com.eduplatform.homework.vo;

import lombok.Data;
import java.time.LocalDateTime;

/**
 * 作业提交视图对象
 * 用于对外输出提交记录，隔离持久层实体。
 */
@Data
public class HomeworkSubmissionVO {

    /**
     * 提交记录ID
     */
    private Long id;

    /**
     * 学生ID
     */
    private Long studentId;

    /**
     * 作业ID
     */
    private Long homeworkId;

    /**
     * 提交状态
     */
    private String submitStatus;

    /**
     * 总分
     */
    private Integer totalScore;

    /**
     * 客观题得分
     */
    private Integer objectiveScore;

    /**
     * 主观题得分
     */
    private Integer subjectiveScore;

    /**
     * 提交时间
     */
    private LocalDateTime submittedAt;

    /**
     * 批改时间
     */
    private LocalDateTime gradedAt;

    /**
     * 批改人ID
     */
    private Long gradedBy;

    /**
     * 教师反馈
     */
    private String feedback;
}
