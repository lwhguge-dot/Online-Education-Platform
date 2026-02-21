package com.eduplatform.course.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 课程数据传输对象
 * 用于课程的创建和更新请求。
 *
 * @author Antigravity
 */
@Data
public class CourseDTO {

    /**
     * 课程标题
     */
    @NotBlank(message = "title不能为空")
    @Size(max = 200, message = "title长度不能超过200")
    private String title;

    /**
     * 课程描述
     */
    @Size(max = 10000, message = "description长度不能超过10000")
    private String description;

    /**
     * 学科分类
     */
    @NotBlank(message = "subject不能为空")
    @Size(max = 50, message = "subject长度不能超过50")
    private String subject;

    /**
     * 封面图片URL
     */
    @Size(max = 500, message = "coverImage长度不能超过500")
    private String coverImage;

    /**
     * 教师ID
     */
    @Positive(message = "teacherId必须为正数")
    private Long teacherId;

    /**
     * 课程状态
     */
    private String status;
}
