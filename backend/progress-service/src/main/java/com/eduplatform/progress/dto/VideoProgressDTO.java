package com.eduplatform.progress.dto;

import lombok.Data;

@Data
public class VideoProgressDTO {
    private Long studentId;
    private Long chapterId;
    private Long courseId;
    private Integer currentPosition;
    private Integer totalDuration;
    private Double videoRate;
    private Integer isCompleted;
    private Long clientTimestamp; // 客户端发送请求的时间戳（毫秒），用于防作弊校验
}
