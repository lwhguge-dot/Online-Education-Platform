package com.eduplatform.homework.controller;

import com.eduplatform.common.result.Result;
import com.eduplatform.homework.dto.DiscussionDTO;
import com.eduplatform.homework.dto.DiscussionGroupDTO;
import com.eduplatform.homework.dto.DiscussionStatsDTO;
import com.eduplatform.homework.service.DiscussionService;
import com.eduplatform.homework.vo.SubjectiveCommentVO;
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
    public Result<Map<String, Object>> getTeacherDiscussions(@PathVariable Long teacherId) {
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
    public Result<DiscussionStatsDTO> getStats(@PathVariable Long teacherId) {
        DiscussionStatsDTO stats = discussionService.getStats(teacherId);
        return Result.success(stats);
    }

    /**
     * 按课程获取讨论。
     */
    @GetMapping("/course/{courseId}")
    public Result<List<DiscussionDTO>> getByCourse(@PathVariable Long courseId) {
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
            @RequestParam(required = false) Long answeredBy) {
        discussionService.updateAnswerStatus(id, status, answeredBy);
        return Result.success("状态更新成功", null);
    }

    /**
     * 切换置顶状态。
     * 说明：便于教师突出重点问题。
     */
    @PutMapping("/{id}/toggle-top")
    public Result<Void> toggleTop(@PathVariable Long id) {
        discussionService.toggleTop(id);
        return Result.success("置顶状态已切换", null);
    }

    /**
     * 回复讨论。
     */
    @PostMapping("/{parentId}/reply")
    public Result<SubjectiveCommentVO> reply(
            @PathVariable Long parentId,
            @RequestBody Map<String, Object> body) {
        Long userId = Long.valueOf(body.get("userId").toString());
        String content = (String) body.get("content");
        Long courseId = body.get("courseId") != null ? Long.valueOf(body.get("courseId").toString()) : null;
        Long chapterId = body.get("chapterId") != null ? Long.valueOf(body.get("chapterId").toString()) : null;

        SubjectiveCommentVO comment = discussionService.reply(parentId, userId, content, courseId, chapterId);
        return Result.success("回复成功", comment);
    }

    /**
     * 获取回复列表。
     */
    @GetMapping("/{parentId}/replies")
    public Result<List<DiscussionDTO>> getReplies(@PathVariable Long parentId) {
        List<DiscussionDTO> replies = discussionService.getReplies(parentId);
        return Result.success(replies);
    }
}
