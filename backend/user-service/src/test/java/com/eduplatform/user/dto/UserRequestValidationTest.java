package com.eduplatform.user.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * user-service 请求体校验测试。
 */
@DisplayName("User 请求体校验测试")
class UserRequestValidationTest {

    @Test
    @DisplayName("系统公告标题超长应被拒绝")
    void announcementShouldRejectTooLongTitle() {
        AnnouncementRequestDTO dto = new AnnouncementRequestDTO();
        dto.setTitle("a".repeat(201));

        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            Validator validator = factory.getValidator();
            Set<ConstraintViolation<AnnouncementRequestDTO>> violations = validator.validate(dto);
            assertTrue(violations.stream().anyMatch(v -> "title".equals(v.getPropertyPath().toString())));
        }
    }

    @Test
    @DisplayName("系统公告目标受众非法值应被拒绝")
    void announcementShouldRejectInvalidAudience() {
        AnnouncementRequestDTO dto = new AnnouncementRequestDTO();
        dto.setTargetAudience("HACKER");

        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            Validator validator = factory.getValidator();
            Set<ConstraintViolation<AnnouncementRequestDTO>> violations = validator.validate(dto);
            assertTrue(violations.stream().anyMatch(v -> "targetAudience".equals(v.getPropertyPath().toString())));
        }
    }

    @Test
    @DisplayName("教师公告目标受众非法值应被拒绝")
    void teacherAnnouncementShouldRejectInvalidAudience() {
        TeacherAnnouncementDTO dto = new TeacherAnnouncementDTO();
        dto.setTargetAudience("TEACHER");

        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            Validator validator = factory.getValidator();
            Set<ConstraintViolation<TeacherAnnouncementDTO>> violations = validator.validate(dto);
            assertTrue(violations.stream().anyMatch(v -> "targetAudience".equals(v.getPropertyPath().toString())));
        }
    }

    @Test
    @DisplayName("用户学习目标应拒绝非法数值")
    void userSettingsShouldRejectInvalidStudyGoal() {
        UserSettingsDTO.StudyGoal studyGoal = new UserSettingsDTO.StudyGoal();
        studyGoal.setDailyMinutes(0);
        studyGoal.setWeeklyHours(0);

        UserSettingsDTO dto = new UserSettingsDTO();
        dto.setStudyGoal(studyGoal);

        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            Validator validator = factory.getValidator();
            Set<ConstraintViolation<UserSettingsDTO>> violations = validator.validate(dto);
            assertTrue(violations.stream().anyMatch(v -> "studyGoal.dailyMinutes".equals(v.getPropertyPath().toString())));
            assertTrue(violations.stream().anyMatch(v -> "studyGoal.weeklyHours".equals(v.getPropertyPath().toString())));
        }
    }
}
