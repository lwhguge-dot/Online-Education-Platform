package com.eduplatform.homework.dto;

import lombok.Data;

@Data
public class DiscussionStatsDTO {
    private Integer totalQuestions;
    private Integer pendingCount;
    private Integer answeredCount;
    private Integer followUpCount;
    private Integer overdueCount;
    private Double avgResponseTimeHours;
}
