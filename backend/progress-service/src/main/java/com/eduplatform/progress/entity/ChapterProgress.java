package com.eduplatform.progress.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Getter;
import lombok.Setter;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Getter
@Setter
@TableName("chapter_progress")
public class ChapterProgress {
    @TableId(type = IdType.AUTO)
    private Long id;
    
    private Long studentId;
    private Long chapterId;
    private Long courseId;
    
    private BigDecimal videoRate;
    private Integer videoWatchTime;
    private Integer quizScore;
    private LocalDateTime quizSubmittedAt;
    private Integer isCompleted;
    private LocalDateTime completedAt;
    private Integer lastPosition;
    private LocalDateTime lastUpdateTime;
    
    @Version
    private Integer version;
    
    @TableField("created_at")
    private LocalDateTime createdAt;
    
    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
