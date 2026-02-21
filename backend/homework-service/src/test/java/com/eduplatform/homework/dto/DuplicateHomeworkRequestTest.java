package com.eduplatform.homework.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * DuplicateHomeworkRequest 参数校验测试。
 */
@DisplayName("DuplicateHomeworkRequest 单元测试")
class DuplicateHomeworkRequestTest {

    @Test
    @DisplayName("chapterId 非法时应触发校验失败")
    void shouldFailWhenChapterIdIsNotPositive() {
        DuplicateHomeworkRequest request = new DuplicateHomeworkRequest();
        request.setChapterId(0L);

        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            Validator validator = factory.getValidator();
            Set<ConstraintViolation<DuplicateHomeworkRequest>> violations = validator.validate(request);
            assertEquals(1, violations.size());
            assertTrue(violations.iterator().next().getMessage().contains("chapterId"));
        }
    }
}
