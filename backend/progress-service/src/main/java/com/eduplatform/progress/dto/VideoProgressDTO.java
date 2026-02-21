package com.eduplatform.progress.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Data;

@Data
public class VideoProgressDTO {
    @Positive(message = "studentId必须为正数")
    private Long studentId;
    @NotNull(message = "chapterId不能为空")
    @Positive(message = "chapterId必须为正数")
    private Long chapterId;
    @Positive(message = "courseId必须为正数")
    private Long courseId;
    @NotNull(message = "currentPosition不能为空")
    @PositiveOrZero(message = "currentPosition不能为负数")
    private Integer currentPosition;
    @Positive(message = "totalDuration必须为正数")
    private Integer totalDuration;
    @DecimalMin(value = "0.0", message = "videoRate不能小于0")
    @DecimalMax(value = "1.0", message = "videoRate不能大于1")
    private Double videoRate;
    @Min(value = 0, message = "isCompleted仅支持0或1")
    @Max(value = 1, message = "isCompleted仅支持0或1")
    private Integer isCompleted;
    @Positive(message = "clientTimestamp必须为正数")
    private Long clientTimestamp; // 客户端发送请求的时间戳（毫秒），用于防作弊校验
}
