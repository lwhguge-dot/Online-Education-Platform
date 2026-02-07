package com.eduplatform.homework.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("homework_questions")
public class HomeworkQuestion {
    @TableId(type = IdType.AUTO)
    private Long id;
    
    private Long homeworkId;
    private String questionType;
    private String content;
    private String options;
    private String correctAnswer;
    private String answerAnalysis;
    private Integer score;
    private Integer sortOrder;
    
    @TableField("created_at")
    private LocalDateTime createdAt;
}
