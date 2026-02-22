package com.eduplatform.progress.controller;

import com.eduplatform.common.result.Result;
import com.eduplatform.progress.dto.StudentStatsDTO;
import com.eduplatform.progress.service.StudentStatsService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 学生学习统计控制器
 */
@RestController
@RequestMapping("/api/stats")
@RequiredArgsConstructor
public class StatsController {

    private final StudentStatsService studentStatsService;

    /**
     * 获取学生仪表盘统计数据
     * 包含：总学习时长、今日学习、连续天数、本周学习时长、测验成绩等
     */
    @GetMapping("/student/{studentId}/dashboard")
    public Result<StudentStatsDTO> getStudentDashboard(
            @PathVariable("studentId") Long studentId,
            @RequestHeader(value = "X-User-Id", required = false) String currentUserIdHeader,
            @RequestHeader(value = "X-User-Role", required = false) String currentUserRole) {
        Long currentUserId = parseUserId(currentUserIdHeader);
        if (!canAccessStudentData(studentId, currentUserId, currentUserRole)) {
            return Result.failure(403, "权限不足，仅本人、教师或管理员可查看学习统计");
        }
        StudentStatsDTO stats = studentStatsService.getStudentDashboardStats(studentId);
        return Result.success(stats);
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
     * 判断是否可访问学生数据（本人、教师或管理员）。
     */
    private boolean canAccessStudentData(Long targetStudentId, Long currentUserId, String currentUserRole) {
        if (currentUserRole != null
                && ("teacher".equalsIgnoreCase(currentUserRole) || "admin".equalsIgnoreCase(currentUserRole))) {
            return true;
        }
        return currentUserId != null && currentUserId.equals(targetStudentId);
    }
}
