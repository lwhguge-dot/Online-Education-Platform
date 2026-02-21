package com.eduplatform.homework.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class HomeworkCreateDTO {
    @NotNull(message = "chapterId不能为空")
    @Positive(message = "chapterId必须为正数")
    private Long chapterId;

    @NotNull(message = "courseId不能为空")
    @Positive(message = "courseId必须为正数")
    private Long courseId;

    @NotBlank(message = "title不能为空")
    @Size(max = 200, message = "title长度不能超过200")
    private String title;

    @Size(max = 2000, message = "description长度不能超过2000")
    private String description;

    @NotBlank(message = "homeworkType不能为空")
    @Size(max = 32, message = "homeworkType长度不能超过32")
    private String homeworkType;

    @NotNull(message = "totalScore不能为空")
    @Positive(message = "totalScore必须为正数")
    private Integer totalScore;

    private LocalDateTime deadline;

    @Valid
    private List<QuestionDTO> questions;
    
    @Data
    public static class QuestionDTO {
        @NotBlank(message = "questionType不能为空")
        @Size(max = 32, message = "questionType长度不能超过32")
        private String questionType;

        @NotBlank(message = "content不能为空")
        @Size(max = 5000, message = "content长度不能超过5000")
        private String content;

        @Size(max = 10000, message = "options长度不能超过10000")
        private String options;

        @Size(max = 2000, message = "correctAnswer长度不能超过2000")
        private String correctAnswer;

        @Size(max = 5000, message = "answerAnalysis长度不能超过5000")
        private String answerAnalysis;

        @Positive(message = "score必须为正数")
        private Integer score;

        @Positive(message = "sortOrder必须为正数")
        private Integer sortOrder;
    }
}
