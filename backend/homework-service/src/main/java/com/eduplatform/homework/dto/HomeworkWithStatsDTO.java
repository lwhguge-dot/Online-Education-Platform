package com.eduplatform.homework.dto;

import lombok.Data;
import java.time.LocalDateTime;

@Data
public class HomeworkWithStatsDTO {
    private Long id;
    private Long chapterId;
    private Long courseId;
    private String title;
    private String description;
    private String homeworkType;
    private Integer totalScore;
    private LocalDateTime deadline;
    private LocalDateTime createdAt;
    
    // 统计字段
    private Integer submissionCount = 0;
    private Integer gradedCount = 0;
}
