package com.eduplatform.homework.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 教学日历请求体校验测试。
 */
@DisplayName("TeachingEvent 请求体校验测试")
class TeachingEventValidationTest {

    @Test
    @DisplayName("教学事件创建应要求核心字段")
    void createEventShouldRequireCoreFields() {
        TeachingEventDTO dto = new TeachingEventDTO();

        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            Validator validator = factory.getValidator();
            Set<ConstraintViolation<TeachingEventDTO>> violations = validator.validate(dto);

            assertTrue(violations.stream().anyMatch(v -> "title".equals(v.getPropertyPath().toString())));
            assertTrue(violations.stream().anyMatch(v -> "eventType".equals(v.getPropertyPath().toString())));
            assertTrue(violations.stream().anyMatch(v -> "startTime".equals(v.getPropertyPath().toString())));
        }
    }

    @Test
    @DisplayName("教学事件提醒时间应拒绝负数")
    void createEventShouldRejectNegativeReminderMinutes() {
        TeachingEventDTO dto = new TeachingEventDTO();
        dto.setTitle("课程答疑");
        dto.setEventType("MEETING");
        dto.setStartTime(LocalDateTime.now());
        dto.setReminderMinutes(-1);

        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            Validator validator = factory.getValidator();
            Set<ConstraintViolation<TeachingEventDTO>> violations = validator.validate(dto);
            assertTrue(violations.stream().anyMatch(v -> "reminderMinutes".equals(v.getPropertyPath().toString())));
        }
    }
}
