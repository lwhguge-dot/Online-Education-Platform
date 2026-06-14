package com.eduplatform.homework.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

@Getter
@Setter
@TableName("subjective_answer_permission")
public class SubjectiveAnswerPermission {
    @TableId(type = IdType.AUTO)
    private Long id;
    
    private Long studentId;
    private Long questionId;
    private String answerContent;
    private Integer answerStatus;
    private Integer commentVisible;
    private LocalDateTime answeredAt;
    
    @TableField("created_at")
    private LocalDateTime createdAt;
    
    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
