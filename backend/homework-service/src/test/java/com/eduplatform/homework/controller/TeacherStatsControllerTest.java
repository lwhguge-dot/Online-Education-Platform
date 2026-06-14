package com.eduplatform.homework.controller;

import com.eduplatform.common.result.Result;
import com.eduplatform.homework.dto.TeacherCourseSummaryDTO;
import com.eduplatform.homework.dto.TeacherDashboardDTO;
import com.eduplatform.homework.service.TeacherStatsService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * TeacherStatsController 权限与强类型入参测试。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("TeacherStatsController 单元测试")
class TeacherStatsControllerTest {

    @InjectMocks
    private TeacherStatsController teacherStatsController;

    @Mock
    private TeacherStatsService teacherStatsService;

    @Test
    @DisplayName("教师仪表盘-教师仅可访问本人")
    void getTeacherDashboardShouldDenyOtherTeacher() {
        TeacherCourseSummaryDTO course = new TeacherCourseSummaryDTO();
        course.setId(1L);
        course.setTitle("Java 入门");
        course.setStatus("PUBLISHED");
        course.setStudentCount(30);

        Result<TeacherDashboardDTO> result = teacherStatsController.getTeacherDashboard(
                2L,
                List.of(course),
                "1",
                "teacher");

        assertNotNull(result);
        assertEquals(403, result.getCode());
        assertEquals("权限不足，仅教师本人或管理员可访问教师仪表盘", result.getMessage());
        verifyNoInteractions(teacherStatsService);
    }

    @Test
    @DisplayName("教师仪表盘-应透传强类型课程列表")
    void getTeacherDashboardShouldPassTypedCourses() {
        TeacherCourseSummaryDTO course = new TeacherCourseSummaryDTO();
        course.setId(9L);
        course.setTitle("算法设计");
        course.setStatus("PUBLISHED");
        course.setStudentCount(42);
        List<TeacherCourseSummaryDTO> courses = List.of(course);

        when(teacherStatsService.getTeacherDashboard(1L, courses)).thenReturn(new TeacherDashboardDTO());

        Result<TeacherDashboardDTO> result = teacherStatsController.getTeacherDashboard(
                1L,
                courses,
                "1",
                "teacher");

        assertNotNull(result);
        assertEquals(200, result.getCode());
        verify(teacherStatsService).getTeacherDashboard(1L, courses);
    }
}
