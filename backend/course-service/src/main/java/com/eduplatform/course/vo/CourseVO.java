package com.eduplatform.course.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 课程视图对象
 * 用于 API 响应，隐藏内部数据库实体细节，确保数据隔离。
 *
 * @author Antigravity
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CourseVO {

    /**
     * 课程ID
     */
    private Long id;

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
     * 教师名称
     */
    private String teacherName;

    /**
     * 课程评分
     */
    private Double rating;

    /**
     * 学生人数
     */
    private Integer studentCount;

    /**
     * 课程状态
     */
    private String status;

    /**
     * 提交审核时间
     */
    private LocalDateTime submitTime;

    /**
     * 章节总数
     */
    private Integer totalChapters;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;

    /**
     * 更新时间
     */
    private LocalDateTime updatedAt;
}
