package com.eduplatform.homework.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("homework_questions_discussion")
public class HomeworkQuestionDiscussion {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long homeworkId;
    private Long questionId;
    private Long studentId;
    private String questionContent;
    private String teacherReply;
    private Long repliedBy;
    private LocalDateTime repliedAt;
    private String status; // pending/answered
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
