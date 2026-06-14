package com.eduplatform.homework.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

@Getter
@Setter
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
