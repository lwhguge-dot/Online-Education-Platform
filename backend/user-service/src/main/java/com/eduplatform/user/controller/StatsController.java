package com.eduplatform.user.controller;

import com.eduplatform.common.result.Result;
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

    /**
     * 获取管理员仪表盘统计数据。
     * 说明：用户与在线数据来自本服务，课程统计通过 Feign 聚合。
     */
    @GetMapping("/admin/dashboard")
    public Result<Map<String, Object>> getAdminDashboard(
            @RequestHeader(value = "X-User-Role", required = false) String currentUserRole) {
        if (!isAdminRole(currentUserRole)) {
            return Result.failure(403, "权限不足，仅管理员可访问管理统计");
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
     * 获取用户增长趋势数据。
     * 用于管理员仪表盘的趋势图表展示，包含每日新增用户、活跃用户和在线用户峰值。
     *
     * @param days 统计天数（默认7天，最大30天）
     * @return 包含标签和各项指标数组的趋势数据
     */
    @GetMapping("/admin/user-trends")
    public Result<Map<String, Object>> getUserTrends(
            @RequestParam(value = "days", defaultValue = "7") int days,
            @RequestHeader(value = "X-User-Role", required = false) String currentUserRole) {
        if (!isAdminRole(currentUserRole)) {
            return Result.failure(403, "权限不足，仅管理员可查看用户趋势");
        }
        // 限制最大查询天数为30天
        days = Math.min(days, 30);

        Map<String, Object> result = new HashMap<>();
        List<String> labels = new ArrayList<>();
        List<Long> newUsers = new ArrayList<>();
        List<Long> activeUsers = new ArrayList<>();
        List<Long> onlineUsers = new ArrayList<>();

        // 日期格式化器
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MM-dd");

        // 从 days 天前开始统计到昨天
        for (int i = days - 1; i >= 0; i--) {
            LocalDateTime date = LocalDateTime.now().minusDays(i);
            labels.add(date.format(formatter));

            // 统计每日新增用户
            newUsers.add(userService.countNewUsersByDate(date));

            // 统计每日活跃用户
            activeUsers.add(userService.countActiveUsersByDate(date));

            // 在线用户使用当前实时数据（历史数据需要额外存储机制）
            // 这里对于历史日期暂时使用活跃用户的比例估算
            if (i == 0) {
                onlineUsers.add((long) sessionService.countOnlineUsers());
            } else {
                // 历史在线用户估算：活跃用户的30%~50%
                long estimated = (long) (userService.countActiveUsersByDate(date) * 0.4);
                onlineUsers.add(estimated);
            }
        }

        result.put("labels", labels);
        result.put("newUsers", newUsers);
        result.put("activeUsers", activeUsers);
        result.put("onlineUsers", onlineUsers);

        return Result.success(result);
    }

    /**
     * 获取学生学习统计。
     * 说明：当前返回示例数据，后续应由进度/作业服务汇总。
     */
    @GetMapping("/student/dashboard")
    public Result<Map<String, Object>> getStudentDashboard(
            @RequestParam("studentId") Long studentId,
            @RequestHeader(value = "X-User-Id", required = false) String currentUserIdHeader,
            @RequestHeader(value = "X-User-Role", required = false) String currentUserRole) {
        Long currentUserId = parseUserId(currentUserIdHeader);
        if (!hasSelfOrAdminAccess(studentId, currentUserId, currentUserRole)) {
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

    /**
     * 判断是否管理员角色。
     */
    private boolean isAdminRole(String currentUserRole) {
        return currentUserRole != null && "admin".equalsIgnoreCase(currentUserRole);
    }

    /**
     * 解析网关注入的用户ID。
     */
    private Long parseUserId(String userIdHeader) {
        if (userIdHeader == null || userIdHeader.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(userIdHeader);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 判断是否具备本人或管理员访问权限。
     */
    private boolean hasSelfOrAdminAccess(Long targetUserId, Long currentUserId, String currentUserRole) {
        if (isAdminRole(currentUserRole)) {
            return true;
        }
        return currentUserId != null && currentUserId.equals(targetUserId);
    }
}
