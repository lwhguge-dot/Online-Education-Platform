package com.eduplatform.course.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 章节写接口请求体校验测试。
 */
@DisplayName("Chapter 请求体校验测试")
class ChapterRequestValidationTest {

    @Test
    @DisplayName("创建章节请求应要求课程ID和标题")
    void createChapterShouldRequireCourseIdAndTitle() {
        ChapterDTO dto = new ChapterDTO();

        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            Validator validator = factory.getValidator();
            Set<ConstraintViolation<ChapterDTO>> violations = validator.validate(dto);

            assertTrue(violations.stream().anyMatch(v -> "courseId".equals(v.getPropertyPath().toString())));
            assertTrue(violations.stream().anyMatch(v -> "title".equals(v.getPropertyPath().toString())));
        }
    }

    @Test
    @DisplayName("添加测验请求应要求题干和题型")
    void addQuizShouldRequireQuestionAndType() {
        ChapterQuizDTO dto = new ChapterQuizDTO();

        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            Validator validator = factory.getValidator();
            Set<ConstraintViolation<ChapterQuizDTO>> violations = validator.validate(dto);

            assertTrue(violations.stream().anyMatch(v -> "question".equals(v.getPropertyPath().toString())));
            assertTrue(violations.stream().anyMatch(v -> "questionType".equals(v.getPropertyPath().toString())));
        }
    }
}
