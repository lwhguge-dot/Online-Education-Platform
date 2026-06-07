package com.eduplatform.homework.dto;

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
 * 作业写接口请求体校验测试。
 */
@DisplayName("Homework 请求体校验测试")
class HomeworkRequestValidationTest {

    @Test
    @DisplayName("创建作业请求应要求核心字段")
    void createHomeworkShouldRequireCoreFields() {
        HomeworkCreateDTO dto = new HomeworkCreateDTO();

        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            Validator validator = factory.getValidator();
            Set<ConstraintViolation<HomeworkCreateDTO>> violations = validator.validate(dto);

            assertTrue(violations.stream().anyMatch(v -> "chapterId".equals(v.getPropertyPath().toString())));
            assertTrue(violations.stream().anyMatch(v -> "courseId".equals(v.getPropertyPath().toString())));
            assertTrue(violations.stream().anyMatch(v -> "title".equals(v.getPropertyPath().toString())));
            assertTrue(violations.stream().anyMatch(v -> "homeworkType".equals(v.getPropertyPath().toString())));
            assertTrue(violations.stream().anyMatch(v -> "totalScore".equals(v.getPropertyPath().toString())));
        }
    }

    @Test
    @DisplayName("提交作业请求应要求答案列表")
    void submitHomeworkShouldRequireAnswers() {
        HomeworkSubmitDTO dto = new HomeworkSubmitDTO();
        dto.setStudentId(1L);
        dto.setHomeworkId(2L);

        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            Validator validator = factory.getValidator();
            Set<ConstraintViolation<HomeworkSubmitDTO>> violations = validator.validate(dto);
            assertTrue(violations.stream().anyMatch(v -> "answers".equals(v.getPropertyPath().toString())));
        }
    }

    @Test
    @DisplayName("批量批改请求应拒绝负分")
    void gradeSubmissionShouldRejectNegativeScore() {
        GradeSubmissionDTO.QuestionGrade grade = new GradeSubmissionDTO.QuestionGrade();
        grade.setQuestionId(1L);
        grade.setScore(-1);

        GradeSubmissionDTO dto = new GradeSubmissionDTO();
        dto.setGrades(List.of(grade));

        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            Validator validator = factory.getValidator();
            Set<ConstraintViolation<GradeSubmissionDTO>> violations = validator.validate(dto);
            assertTrue(violations.stream().anyMatch(v -> v.getPropertyPath().toString().contains("score")));
        }
    }

    @Test
    @DisplayName("导入题目请求应要求题目列表非空")
    void importQuestionsShouldRequireNonEmptyQuestions() {
        ImportHomeworkQuestionsRequest dto = new ImportHomeworkQuestionsRequest();

        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            Validator validator = factory.getValidator();
            Set<ConstraintViolation<ImportHomeworkQuestionsRequest>> violations = validator.validate(dto);
            assertTrue(violations.stream().anyMatch(v -> "questions".equals(v.getPropertyPath().toString())));
        }
    }
}
