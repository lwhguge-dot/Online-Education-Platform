package com.eduplatform.course.dto;

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
    private String title;

    /**
     * 课程描述
     */
    private String description;

    /**
     * 学科分类
     */
    private String subject;

    /**
     * 封面图片URL
     */
    private String coverImage;

    /**
     * 教师ID
     */
    private Long teacherId;

    /**
     * 课程状态
     */
    private String status;
}
