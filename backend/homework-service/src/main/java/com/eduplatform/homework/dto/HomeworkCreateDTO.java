package com.eduplatform.homework.dto;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class HomeworkCreateDTO {
    private Long chapterId;
    private Long courseId;
    private String title;
    private String description;
    private String homeworkType;
    private Integer totalScore;
    private LocalDateTime deadline;
    private List<QuestionDTO> questions;
    
    @Data
    public static class QuestionDTO {
        private String questionType;
        private String content;
        private String options;
        private String correctAnswer;
        private String answerAnalysis;
        private Integer score;
        private Integer sortOrder;
    }
}
