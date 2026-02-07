package com.eduplatform.homework.dto;

import lombok.Data;
import java.util.List;

@Data
public class HomeworkSubmitDTO {
    private Long studentId;
    private Long homeworkId;
    private List<AnswerDTO> answers;
    
    @Data
    public static class AnswerDTO {
        private Long questionId;
        private String answer;
    }
}
