package com.eduplatform.homework.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 作业视图对象
 * 用于 API 响应，隐藏作业实体细节。
 *
 * @author Antigravity
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HomeworkVO {

    /**
     * 作业ID
     */
    private Long id;

    /**
     * 课程ID
     */
    private Long courseId;

    /**
     * 章节ID
     */
    private Long chapterId;

    /**
     * 授课教师ID
     */
    private Long teacherId;

    /**
     * 作业标题
     */
    private String title;

    /**
     * 作业具体描述
     */
    private String description;

    /**
     * 作业类型
     */
    private String homeworkType;

    /**
     * 作业总分
     */
    private Integer totalScore;

    /**
     * 截止日期
     */
    private LocalDateTime deadline;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;
}
