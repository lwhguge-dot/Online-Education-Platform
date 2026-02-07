package com.eduplatform.homework.vo;

import lombok.Data;
import java.time.LocalDateTime;

/**
 * 作业问答视图对象
 * 用于教师端作业问答列表展示，避免直接暴露实体。
 */
@Data
public class HomeworkQuestionDiscussionVO {

    /**
     * 记录ID
     */
    private Long id;

    /**
     * 作业ID
     */
    private Long homeworkId;

    /**
     * 题目ID
     */
    private Long questionId;

    /**
     * 学生ID
     */
    private Long studentId;

    /**
     * 学生姓名
     */
    private String studentName;

    /**
     * 提问内容
     */
    private String questionContent;

    /**
     * 教师回复内容
     */
    private String teacherReply;

    /**
     * 回复人ID
     */
    private Long repliedBy;

    /**
     * 回复时间
     */
    private LocalDateTime repliedAt;

    /**
     * 状态 (pending/answered)
     */
    private String status;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;

    /**
     * 题目标题
     */
    private String questionTitle;
}
