package com.eduplatform.homework.dto;

import com.eduplatform.homework.vo.HomeworkSubmissionVO;
import com.eduplatform.homework.vo.HomeworkVO;
import lombok.Data;

/**
 * 学生作业视图聚合对象
 * 用于返回学生章节作业列表，避免暴露实体。
 */
@Data
public class StudentHomeworkDTO {

    /**
     * 作业信息
     */
    private HomeworkVO homework;

    /**
     * 提交记录
     */
    private HomeworkSubmissionVO submission;

    /**
     * 题目数量
     */
    private Integer questionCount;

    /**
     * 是否已解锁
     */
    private Boolean unlocked;

    /**
     * 是否已提交
     */
    private Boolean submitted;
}
