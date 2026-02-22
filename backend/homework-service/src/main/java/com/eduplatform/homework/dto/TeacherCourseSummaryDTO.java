package com.eduplatform.homework.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 教师仪表盘课程摘要请求项。
 */
@Data
public class TeacherCourseSummaryDTO {

    /**
     * 课程ID。
     */
    @NotNull(message = "id不能为空")
    @Positive(message = "id必须为正数")
    private Long id;

    /**
     * 课程标题。
     */
    @NotBlank(message = "title不能为空")
    @Size(max = 200, message = "title长度不能超过200")
    private String title;

    /**
     * 课程状态。
     */
    @NotBlank(message = "status不能为空")
    @Size(max = 32, message = "status长度不能超过32")
    private String status;

    /**
     * 学生人数。
     */
    @NotNull(message = "studentCount不能为空")
    @PositiveOrZero(message = "studentCount不能为负数")
    private Integer studentCount;
}
