package com.eduplatform.user.controller;

import com.eduplatform.common.result.Result;
import com.eduplatform.common.security.RequestContext;
import com.eduplatform.user.feign.CourseServiceClient;
import com.eduplatform.user.service.UserService;
import com.eduplatform.user.service.UserSessionService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * 统计数据控制器。
 * 设计意图：为不同角色提供统一统计入口，减少前端多端拼接。
 */
@RestController
@RequestMapping("/api/stats")
@RequiredArgsConstructor
public class StatsController {

    private final UserService userService;
    private final UserSessionService sessionService;
    private final CourseServiceClient courseServiceClient;
    private final RequestContext requestContext;

    /**
     * 获取管理员仪表盘统计数据。
     * 说明：用户与在线数据来自本服务，课程统计通过 Feign 聚合。
     */
    @GetMapping("/admin/dashboard")
    public Result<Map<String, Object>> getAdminDashboard() {
        if (!requestContext.isAdmin()) {
            return Result.failure(403, "权限不足，仅管理员可查看管理仪表盘");
        }
        Map<String, Object> stats = new HashMap<>();

        // 用户统计 - 从数据库实时查询
        stats.put("totalUsers", userService.countTotal());
        stats.put("totalStudents", userService.countByRole("student"));
        stats.put("totalTeachers", userService.countByRole("teacher"));
        stats.put("totalAdmins", userService.countByRole("admin"));

        // 今日新增用户 - 基于数据库中的created_at字段
        stats.put("newUsersToday", userService.countNewUsersToday());

        // 今日活跃用户 - 基于数据库中的last_login_at字段
        stats.put("activeUsersToday", userService.countActiveUsersToday());

        // 在线用户数 - 基于活跃会话数
        stats.put("onlineUsers", sessionService.countOnlineUsers());

        // 课程统计 - 通过 Feign 调用 course-service
        Result<Map<String, Object>> courseStatsResult = courseServiceClient.getCourseStats();
        if (courseStatsResult != null && courseStatsResult.getData() != null) {
            Map<String, Object> courseStats = courseStatsResult.getData();
            stats.put("publishedCourses", courseStats.getOrDefault("published", 0L));
            stats.put("pendingCourses", courseStats.getOrDefault("reviewing", 0L));
            stats.put("totalCourses", courseStats.getOrDefault("total", 0L));
        } else {
            stats.put("publishedCourses", 0L);
            stats.put("pendingCourses", 0L);
            stats.put("totalCourses", 0L);
        }

        // 数据时间戳
        stats.put("timestamp", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));

        return Result.success(stats);
    }

    /**
     * 获取学生学习统计。
     * 说明：当前返回示例数据，后续应由进度/作业服务汇总。
     */
    @GetMapping("/student/dashboard")
    public Result<Map<String, Object>> getStudentDashboard(
            @RequestParam("studentId") Long studentId) {
        if (!requestContext.canAccessSelfOrAdmin(studentId)) {
            return Result.failure(403, "权限不足，仅本人或管理员可查看学生仪表盘");
        }
        Map<String, Object> stats = new HashMap<>();

        stats.put("enrolledCourses", 0);
        stats.put("completedChapters", 0);
        stats.put("totalStudyHours", 0);
        stats.put("pendingHomework", 0);
        stats.put("todayStudyMinutes", 0);
        stats.put("streakDays", 0);

        return Result.success(stats);
    }
}
