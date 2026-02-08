package com.eduplatform.progress.controller;

import com.eduplatform.common.result.Result;
import com.eduplatform.progress.service.BadgeService;
import com.eduplatform.progress.vo.BadgeVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 徽章控制器。
 * 设计意图：提供学生荣誉与激励系统入口，统一返回徽章视图对象。
 */
@RestController
@RequestMapping("/api/progress/badges")
@RequiredArgsConstructor
public class BadgeController {

    private final BadgeService badgeService;

    /**
     * 获取学生的所有徽章（包含已解锁和未解锁）。
     */
    @GetMapping("/student/{studentId}")
    public Result<List<BadgeVO>> getStudentBadges(
            @PathVariable("studentId") Long studentId,
            @RequestHeader(value = "X-User-Id", required = false) String currentUserIdHeader,
            @RequestHeader(value = "X-User-Role", required = false) String currentUserRole) {
        // 勋章数据仅允许本人访问，教师和管理员允许教学管理场景查询
        Long currentUserId = parseUserId(currentUserIdHeader);
        if (!canAccessStudentData(studentId, currentUserId, currentUserRole)) {
            return Result.failure(403, "权限不足，仅本人、教师或管理员可查看勋章数据");
        }

        List<BadgeVO> badges = badgeService.getStudentBadges(studentId);
        return Result.success(badges);
    }

    /**
     * 检查并授予符合条件的徽章。
     */
    @PostMapping("/student/{studentId}/check")
    public Result<List<BadgeVO>> checkAndAwardBadges(
            @PathVariable("studentId") Long studentId,
            @RequestHeader(value = "X-User-Id", required = false) String currentUserIdHeader,
            @RequestHeader(value = "X-User-Role", required = false) String currentUserRole) {
        // 勋章检测涉及状态变更，仅允许本人、教师或管理员触发
        Long currentUserId = parseUserId(currentUserIdHeader);
        if (!canAccessStudentData(studentId, currentUserId, currentUserRole)) {
            return Result.failure(403, "权限不足，仅本人、教师或管理员可触发勋章检测");
        }

        List<BadgeVO> newlyAwarded = badgeService.checkAndAwardBadges(studentId);
        if (newlyAwarded.isEmpty()) {
            return Result.success("暂无新徽章", newlyAwarded);
        }
        return Result.success("恭喜获得新徽章！", newlyAwarded);
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
     * 学生数据访问控制：学生仅可访问本人；教师和管理员可用于教学管理查询。
     */
    private boolean canAccessStudentData(Long targetStudentId, Long currentUserId, String currentUserRole) {
        if (currentUserRole != null
                && ("teacher".equalsIgnoreCase(currentUserRole) || "admin".equalsIgnoreCase(currentUserRole))) {
            return true;
        }
        return currentUserId != null && currentUserId.equals(targetStudentId);
    }
}
