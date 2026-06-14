package com.eduplatform.homework.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

@Getter
@Setter
@TableName("homework_answers")
public class HomeworkAnswer {
    @TableId(type = IdType.AUTO)
    private Long id;
    
    private Long submissionId;
    private Long questionId;
    private String studentAnswer;
    private Integer isCorrect;
    private Integer score;
    private String aiFeedback;
    private String teacherFeedback;
    
    @TableField("created_at")
    private LocalDateTime createdAt;
    
    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
