package com.eduplatform.progress.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
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
    @Positive(message = "studentId必须为正数")
    private Long studentId;
    /**
     * 章节ID。
     */
    @NotNull(message = "chapterId不能为空")
    @Positive(message = "chapterId必须为正数")
    private Long chapterId;
    /**
     * 答案列表。
     */
    @NotEmpty(message = "answers不能为空")
    @Valid
    private List<QuizAnswer> answers;

    /**
     * 单题作答 DTO。
     */
    @Data
    public static class QuizAnswer {
        /**
         * 题目ID。
         */
        @NotNull(message = "questionId不能为空")
        @Positive(message = "questionId必须为正数")
        private Long questionId;
        /**
         * 学生答案。
         */
        @Size(max = 2000, message = "answer长度不能超过2000")
        private String answer;
    }
}
