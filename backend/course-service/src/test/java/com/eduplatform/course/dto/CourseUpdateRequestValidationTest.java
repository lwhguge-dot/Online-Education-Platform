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
 * 课程更新请求体校验测试。
 */
@DisplayName("Course 更新请求体校验测试")
class CourseUpdateRequestValidationTest {

    @Test
    @DisplayName("课程更新标题超长应被拒绝")
    void updateCourseShouldRejectTooLongTitle() {
        CourseUpdateRequest dto = new CourseUpdateRequest();
        dto.setTitle("a".repeat(201));

        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            Validator validator = factory.getValidator();
            Set<ConstraintViolation<CourseUpdateRequest>> violations = validator.validate(dto);
            assertTrue(violations.stream().anyMatch(v -> "title".equals(v.getPropertyPath().toString())));
        }
    }

    @Test
    @DisplayName("课程更新教师ID应拒绝非正数")
    void updateCourseShouldRejectNonPositiveTeacherId() {
        CourseUpdateRequest dto = new CourseUpdateRequest();
        dto.setTeacherId(0L);

        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            Validator validator = factory.getValidator();
            Set<ConstraintViolation<CourseUpdateRequest>> violations = validator.validate(dto);
            assertTrue(violations.stream().anyMatch(v -> "teacherId".equals(v.getPropertyPath().toString())));
        }
    }
}
