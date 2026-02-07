package com.eduplatform.homework.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@TableName("teaching_events")
public class TeachingEvent {
    @TableId(type = IdType.AUTO)
    private Long id;
    
    private Long teacherId;
    private String title;
    private String description;
    private String eventType;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Integer allDay;
    private Long courseId;
    private Long homeworkId;
    private String color;
    private Integer reminderMinutes;
    private Integer isRecurring;
    private String recurrenceRule;
    private String status;
    
    @TableField("created_at")
    private LocalDateTime createdAt;
    
    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
