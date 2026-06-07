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
 * 课程写接口请求体校验测试。
 */
@DisplayName("Course 请求体校验测试")
class CourseRequestValidationTest {

    @Test
    @DisplayName("创建课程请求应要求标题和学科")
    void createCourseShouldRequireTitleAndSubject() {
        CourseDTO dto = new CourseDTO();

        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            Validator validator = factory.getValidator();
            Set<ConstraintViolation<CourseDTO>> violations = validator.validate(dto);

            assertTrue(violations.stream().anyMatch(v -> "title".equals(v.getPropertyPath().toString())));
            assertTrue(violations.stream().anyMatch(v -> "subject".equals(v.getPropertyPath().toString())));
        }
    }
}
