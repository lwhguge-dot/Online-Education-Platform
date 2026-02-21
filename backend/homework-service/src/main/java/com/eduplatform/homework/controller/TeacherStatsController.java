package com.eduplatform.homework.controller;

import com.eduplatform.common.result.Result;
import com.eduplatform.homework.dto.TeacherCourseSummaryDTO;
import com.eduplatform.homework.dto.TeacherDashboardDTO;
import com.eduplatform.homework.service.TeacherStatsService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 教师统计控制器。
 * 设计意图：为教师端仪表盘提供作业统计与教学概览数据。
 */
@Slf4j
@RestController
@RequestMapping("/api/stats/teacher")
@RequiredArgsConstructor
public class TeacherStatsController {

    private final TeacherStatsService teacherStatsService;

    /**
     * 获取教师仪表盘聚合数据。
     * 说明：课程列表由前端透传，减少跨服务调用开销。
     *
     * @param teacherId 教师ID
     * @param courses   教师的课程列表（由前端传入，避免跨服务调用）
     */
    @PostMapping("/dashboard")
    public Result<TeacherDashboardDTO> getTeacherDashboard(
            @RequestParam("teacherId") Long teacherId,
            @Valid @RequestBody List<@Valid TeacherCourseSummaryDTO> courses,
            @RequestHeader(value = "X-User-Id", required = false) String currentUserIdHeader,
            @RequestHeader(value = "X-User-Role", required = false) String currentUserRole) {
        try {
            // 仪表盘仅允许教师本人或管理员访问，避免 teacherId 越权读取
            Long currentUserId = parseUserId(currentUserIdHeader);
            if (!canAccessTeacherDashboard(teacherId, currentUserId, currentUserRole)) {
                return Result.failure(403, "权限不足，仅教师本人或管理员可访问教师仪表盘");
            }

            int coursesCount = courses != null ? courses.size() : 0;
            log.info("获取教师仪表盘数据, teacherId={}, coursesCount={}", teacherId, coursesCount);
            TeacherDashboardDTO dashboard = teacherStatsService.getTeacherDashboard(teacherId, courses);
            return Result.success(dashboard);
        } catch (Exception e) {
            log.error("获取教师仪表盘数据失败", e);
            return Result.error("获取仪表盘数据失败，请稍后重试");
        }
    }

    /**
     * 获取教师仪表盘聚合数据（GET方式，不需要课程列表）。
     * 返回基础统计数据。
     */
    @GetMapping("/dashboard")
    public Result<TeacherDashboardDTO> getTeacherDashboardSimple(
            @RequestParam("teacherId") Long teacherId,
            @RequestHeader(value = "X-User-Id", required = false) String currentUserIdHeader,
            @RequestHeader(value = "X-User-Role", required = false) String currentUserRole) {
        try {
            // 简版仪表盘同样执行身份校验
            Long currentUserId = parseUserId(currentUserIdHeader);
            if (!canAccessTeacherDashboard(teacherId, currentUserId, currentUserRole)) {
                return Result.failure(403, "权限不足，仅教师本人或管理员可访问教师仪表盘");
            }

            log.info("获取教师仪表盘基础数据, teacherId={}", teacherId);
            // 返回空课程列表的基础数据
            TeacherDashboardDTO dashboard = teacherStatsService.getTeacherDashboard(teacherId, List.of());
            return Result.success(dashboard);
        } catch (Exception e) {
            log.error("获取教师仪表盘数据失败", e);
            return Result.error("获取仪表盘数据失败，请稍后重试");
        }
    }

    /**
     * 解析网关注入的用户ID，非法值返回 null。
     */
    private Long parseUserId(String currentUserIdHeader) {
        if (currentUserIdHeader == null || currentUserIdHeader.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(currentUserIdHeader);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    /**
     * 判断是否允许访问教师仪表盘：管理员可跨账号访问，教师仅可访问本人数据。
     */
    private boolean canAccessTeacherDashboard(Long targetTeacherId, Long currentUserId, String currentUserRole) {
        if (currentUserRole != null && "admin".equalsIgnoreCase(currentUserRole)) {
            return true;
        }
        return currentUserRole != null
                && "teacher".equalsIgnoreCase(currentUserRole)
                && currentUserId != null
                && currentUserId.equals(targetTeacherId);
    }
}

