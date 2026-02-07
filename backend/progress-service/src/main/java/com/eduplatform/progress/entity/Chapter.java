package com.eduplatform.progress.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("chapters")
public class Chapter {
    @TableId(type = IdType.AUTO)
    private Long id;
    
    private Long courseId;
    private String title;
    private String description;
    private Integer sortOrder;
    private String videoUrl;
    private Integer videoDuration;
    private BigDecimal unlockVideoRate;
    private Integer unlockQuizScore;
    private Integer status;
    
    @TableField("created_at")
    private LocalDateTime createdAt;
    
    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
