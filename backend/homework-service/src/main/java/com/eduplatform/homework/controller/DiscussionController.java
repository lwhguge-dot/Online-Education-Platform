package com.eduplatform.homework.controller;

import com.eduplatform.common.result.Result;
import com.eduplatform.homework.dto.DiscussionDTO;
import com.eduplatform.homework.dto.DiscussionGroupDTO;
import com.eduplatform.homework.dto.DiscussionReplyRequest;
import com.eduplatform.homework.dto.DiscussionStatsDTO;
import com.eduplatform.homework.service.DiscussionService;
import com.eduplatform.homework.vo.SubjectiveCommentVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 作业讨论控制器。
 * 设计意图：集中处理教师答疑与讨论统计，避免多入口导致的数据口径不一致。
 */
@RestController
@RequestMapping("/api/discussions")
@RequiredArgsConstructor
public class DiscussionController {

    private final DiscussionService discussionService;

    /**
     * 获取教师的所有讨论（按课程/章节分组）。
     * 业务原因：教师端需要分组聚合视图用于答疑管理。
     */
    @GetMapping("/teacher/{teacherId}")
    public Result<Map<String, Object>> getTeacherDiscussions(
            @PathVariable Long teacherId,
            @RequestHeader(value = "X-User-Id", required = false) String currentUserIdHeader,
            @RequestHeader(value = "X-User-Role", required = false) String currentUserRole) {
        Long currentUserId = parseUserId(currentUserIdHeader);
        if (!canAccessTeacherData(teacherId, currentUserId, currentUserRole)) {
            return Result.failure(403, "权限不足，仅目标教师本人或管理员可查看讨论");
        }
        List<DiscussionGroupDTO> groups = discussionService.getGroupedDiscussions(teacherId);
        DiscussionStatsDTO stats = discussionService.getStats(teacherId);
        return Result.success(Map.of(
                "groups", groups,
                "stats", stats));
    }

    /**
     * 获取讨论统计。
     * 说明：用于教师端仪表盘的待答疑统计。
     */
    @GetMapping("/teacher/{teacherId}/stats")
    public Result<DiscussionStatsDTO> getStats(
            @PathVariable Long teacherId,
            @RequestHeader(value = "X-User-Id", required = false) String currentUserIdHeader,
            @RequestHeader(value = "X-User-Role", required = false) String currentUserRole) {
        Long currentUserId = parseUserId(currentUserIdHeader);
        if (!canAccessTeacherData(teacherId, currentUserId, currentUserRole)) {
            return Result.failure(403, "权限不足，仅目标教师本人或管理员可查看统计");
        }
        DiscussionStatsDTO stats = discussionService.getStats(teacherId);
        return Result.success(stats);
    }

    /**
     * 按课程获取讨论。
     */
    @GetMapping("/course/{courseId}")
    public Result<List<DiscussionDTO>> getByCourse(
            @PathVariable Long courseId,
            @RequestHeader(value = "X-User-Role", required = false) String currentUserRole) {
        if (!hasTeacherManageRole(currentUserRole)) {
            return Result.failure(403, "权限不足，仅教师或管理员可查看课程讨论");
        }
        List<DiscussionDTO> discussions = discussionService.getByCourse(courseId);
        return Result.success(discussions);
    }

    /**
     * 更新回答状态。
     * 说明：用于标记是否已答复。
     */
    @PutMapping("/{id}/status")
    public Result<Void> updateStatus(
            @PathVariable Long id,
            @RequestParam String status,
            @RequestParam(required = false) Long answeredBy,
            @RequestHeader(value = "X-User-Role", required = false) String currentUserRole) {
        if (!hasTeacherManageRole(currentUserRole)) {
            return Result.failure(403, "权限不足，仅教师或管理员可更新讨论状态");
        }
        discussionService.updateAnswerStatus(id, status, answeredBy);
        return Result.success("状态更新成功", null);
    }

    /**
     * 切换置顶状态。
     * 说明：便于教师突出重点问题。
     */
    @PutMapping("/{id}/toggle-top")
    public Result<Void> toggleTop(
            @PathVariable Long id,
            @RequestHeader(value = "X-User-Role", required = false) String currentUserRole) {
        if (!hasTeacherManageRole(currentUserRole)) {
            return Result.failure(403, "权限不足，仅教师或管理员可设置置顶");
        }
        discussionService.toggleTop(id);
        return Result.success("置顶状态已切换", null);
    }

    /**
     * 回复讨论。
     */
    @PostMapping("/{parentId}/reply")
    public Result<SubjectiveCommentVO> reply(
            @PathVariable Long parentId,
            @Valid @RequestBody DiscussionReplyRequest body,
            @RequestHeader(value = "X-User-Id", required = false) String currentUserIdHeader,
            @RequestHeader(value = "X-User-Role", required = false) String currentUserRole) {
        if (!hasTeacherManageRole(currentUserRole)) {
            return Result.failure(403, "权限不足，仅教师或管理员可回复讨论");
        }
        Long userId = parseUserId(currentUserIdHeader);
        if (userId == null) {
            return Result.failure(401, "身份信息缺失，请重新登录");
        }
        String content = body.getContent();
        Long courseId = body.getCourseId();
        Long chapterId = body.getChapterId();

        SubjectiveCommentVO comment = discussionService.reply(parentId, userId, content, courseId, chapterId);
        return Result.success("回复成功", comment);
    }

    /**
     * 获取回复列表。
     */
    @GetMapping("/{parentId}/replies")
    public Result<List<DiscussionDTO>> getReplies(
            @PathVariable Long parentId,
            @RequestHeader(value = "X-User-Role", required = false) String currentUserRole) {
        if (!hasTeacherManageRole(currentUserRole)) {
            return Result.failure(403, "权限不足，仅教师或管理员可查看回复详情");
        }
        List<DiscussionDTO> replies = discussionService.getReplies(parentId);
        return Result.success(replies);
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
     * 判断是否具备教师管理权限（教师或管理员）。
     */
    private boolean hasTeacherManageRole(String currentUserRole) {
        return currentUserRole != null
                && ("teacher".equalsIgnoreCase(currentUserRole) || "admin".equalsIgnoreCase(currentUserRole));
    }

    /**
     * 判断是否可访问指定教师维度数据（管理员或教师本人）。
     */
    private boolean canAccessTeacherData(Long targetTeacherId, Long currentUserId, String currentUserRole) {
        if ("admin".equalsIgnoreCase(currentUserRole)) {
            return true;
        }
        return "teacher".equalsIgnoreCase(currentUserRole)
                && currentUserId != null
                && currentUserId.equals(targetTeacherId);
    }
}
