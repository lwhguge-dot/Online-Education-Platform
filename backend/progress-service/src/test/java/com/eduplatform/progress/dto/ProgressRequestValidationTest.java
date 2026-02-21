package com.eduplatform.progress.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * progress-service 请求体校验测试。
 */
@DisplayName("Progress 请求体校验测试")
class ProgressRequestValidationTest {

    @Test
    @DisplayName("视频进度上报应要求章节和播放位置")
    void reportVideoProgressShouldRequireChapterAndPosition() {
        VideoProgressDTO dto = new VideoProgressDTO();

        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            Validator validator = factory.getValidator();
            Set<ConstraintViolation<VideoProgressDTO>> violations = validator.validate(dto);
            assertTrue(violations.stream().anyMatch(v -> "chapterId".equals(v.getPropertyPath().toString())));
            assertTrue(violations.stream().anyMatch(v -> "currentPosition".equals(v.getPropertyPath().toString())));
        }
    }

    @Test
    @DisplayName("测验提交应要求答案列表非空")
    void submitQuizShouldRequireAnswers() {
        QuizSubmitDTO dto = new QuizSubmitDTO();
        dto.setChapterId(1L);

        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            Validator validator = factory.getValidator();
            Set<ConstraintViolation<QuizSubmitDTO>> violations = validator.validate(dto);
            assertTrue(violations.stream().anyMatch(v -> "answers".equals(v.getPropertyPath().toString())));
        }
    }

    @Test
    @DisplayName("测验答案应要求题目ID")
    void submitQuizShouldRequireAnswerQuestionId() {
        QuizSubmitDTO.QuizAnswer answer = new QuizSubmitDTO.QuizAnswer();
        answer.setAnswer("A");

        QuizSubmitDTO dto = new QuizSubmitDTO();
        dto.setChapterId(1L);
        dto.setAnswers(List.of(answer));

        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            Validator validator = factory.getValidator();
            Set<ConstraintViolation<QuizSubmitDTO>> violations = validator.validate(dto);
            assertTrue(violations.stream().anyMatch(v -> "answers[0].questionId".equals(v.getPropertyPath().toString())));
        }
    }
}
