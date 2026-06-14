package com.eduplatform.homework.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;

@Getter
@Setter
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
