package com.eduplatform.course.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 课程批量状态更新请求。
 */
@Data
public class CourseBatchStatusRequest {

    /**
     * 课程ID列表。
     */
    @NotEmpty(message = "courseIds不能为空")
    private List<@NotNull(message = "courseIds中存在空值") @Positive(message = "courseId必须为正数") Long> courseIds;

    /**
     * 目标状态。
     */
    @NotBlank(message = "status不能为空")
    @Size(max = 50, message = "status长度不能超过50")
    private String status;
}
