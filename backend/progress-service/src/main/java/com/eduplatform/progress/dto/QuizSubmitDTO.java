package com.eduplatform.progress.dto;

import lombok.Data;
import java.util.List;

/**
 * 测验提交请求 DTO。
 * 设计意图：统一提交测验答案结构，便于服务端批量判分。
 */
@Data
public class QuizSubmitDTO {
    /**
     * 学生ID。
     */
    private Long studentId;
    /**
     * 章节ID。
     */
    private Long chapterId;
    /**
     * 答案列表。
     */
    private List<QuizAnswer> answers;

    /**
     * 单题作答 DTO。
     */
    @Data
    public static class QuizAnswer {
        /**
         * 题目ID。
         */
        private Long questionId;
        /**
         * 学生答案。
         */
        private String answer;
    }
}
