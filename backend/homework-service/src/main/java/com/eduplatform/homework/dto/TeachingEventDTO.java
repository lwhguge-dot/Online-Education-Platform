package com.eduplatform.homework.dto;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class TeachingEventDTO {
    private Long id;
    private Long teacherId;
    private String title;
    private String description;
    private String eventType;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Boolean allDay;
    private Long courseId;
    private String courseTitle;
    private Long homeworkId;
    private String color;
    private Integer reminderMinutes;
    private Boolean isRecurring;
    private String recurrenceRule;
    private String status;
}
