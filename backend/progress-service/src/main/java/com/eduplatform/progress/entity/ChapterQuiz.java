package com.eduplatform.progress.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

@Getter
@Setter
@TableName("chapter_quizzes")
public class ChapterQuiz {
    @TableId(type = IdType.AUTO)
    private Long id;
    
    private Long chapterId;
    private String question;
    private String questionType;
    private String options;
    private String correctAnswer;
    private Integer score;
    private Integer sortOrder;
    
    @TableField("created_at")
    private LocalDateTime createdAt;
}
