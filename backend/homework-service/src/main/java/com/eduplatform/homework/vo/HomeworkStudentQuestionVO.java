package com.eduplatform.homework.vo;

import lombok.Data;
import java.time.LocalDateTime;

/**
 * 学生作业问答视图对象
 * 用于学生侧问答列表展示，避免直接暴露实体。
 */
@Data
public class HomeworkStudentQuestionVO {

    /**
     * 记录ID
     */
    private Long id;

    /**
     * 作业ID
     */
    private Long homeworkId;

    /**
     * 提问内容
     */
    private String questionContent;

    /**
     * 教师回复内容
     */
    private String teacherReply;

    /**
     * 状态 (pending/answered)
     */
    private String status;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;

    /**
     * 回复时间
     */
    private LocalDateTime repliedAt;

    /**
     * 作业标题
     */
    private String homeworkTitle;
}
