package com.eduplatform.homework.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("homework_unlocks")
public class HomeworkUnlock {
    @TableId(type = IdType.AUTO)
    private Long id;
    
    private Long studentId;
    private Long homeworkId;
    private Integer unlockStatus;
    private LocalDateTime unlockedAt;
    
    @TableField("created_at")
    private LocalDateTime createdAt;
}
