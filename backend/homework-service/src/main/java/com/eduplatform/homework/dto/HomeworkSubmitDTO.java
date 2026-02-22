package com.eduplatform.homework.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
public class HomeworkSubmitDTO {
    @Positive(message = "studentId必须为正数")
    private Long studentId;

    @NotNull(message = "homeworkId不能为空")
    @Positive(message = "homeworkId必须为正数")
    private Long homeworkId;

    @NotEmpty(message = "answers不能为空")
    @Valid
    private List<AnswerDTO> answers;
    
    @Data
    public static class AnswerDTO {
        @NotNull(message = "questionId不能为空")
        @Positive(message = "questionId必须为正数")
        private Long questionId;

        @NotNull(message = "answer不能为空")
        @Size(max = 5000, message = "answer长度不能超过5000")
        private String answer;
    }
}
