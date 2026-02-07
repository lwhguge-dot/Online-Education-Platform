package com.eduplatform.homework.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("homework_submissions")
public class HomeworkSubmission {
    @TableId(type = IdType.AUTO)
    private Long id;
    
    private Long studentId;
    private Long homeworkId;
    private String submitStatus;
    private Integer totalScore;
    private Integer objectiveScore;
    private Integer subjectiveScore;
    private LocalDateTime submittedAt;
    private LocalDateTime gradedAt;
    private Long gradedBy;
    private String feedback;
    
    @TableField("created_at")
    private LocalDateTime createdAt;
    
    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
