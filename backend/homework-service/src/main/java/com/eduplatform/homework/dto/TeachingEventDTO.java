package com.eduplatform.homework.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Data;
import java.time.LocalDateTime;

@Data
public class TeachingEventDTO {
    private Long id;
    @Positive(message = "teacherId必须为正数")
    private Long teacherId;
    @NotBlank(message = "title不能为空")
    @Size(max = 200, message = "title长度不能超过200")
    private String title;
    @Size(max = 2000, message = "description长度不能超过2000")
    private String description;
    @NotBlank(message = "eventType不能为空")
    @Size(max = 50, message = "eventType长度不能超过50")
    private String eventType;
    @NotNull(message = "startTime不能为空")
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private Boolean allDay;
    @Positive(message = "courseId必须为正数")
    private Long courseId;
    private String courseTitle;
    @Positive(message = "homeworkId必须为正数")
    private Long homeworkId;
    @Size(max = 20, message = "color长度不能超过20")
    private String color;
    @PositiveOrZero(message = "reminderMinutes不能为负数")
    private Integer reminderMinutes;
    private Boolean isRecurring;
    @Size(max = 200, message = "recurrenceRule长度不能超过200")
    private String recurrenceRule;
    @Pattern(regexp = "^(active|cancelled|completed)$", message = "status仅支持active/cancelled/completed")
    private String status;
}
