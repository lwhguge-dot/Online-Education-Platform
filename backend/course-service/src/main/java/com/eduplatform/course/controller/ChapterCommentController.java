package com.eduplatform.course.controller;

import com.eduplatform.common.result.Result;
import com.eduplatform.course.dto.AddBlockedWordRequest;
import com.eduplatform.course.dto.ChapterCommentCreateRequest;
import com.eduplatform.course.dto.CheckBlockedWordRequest;
import com.eduplatform.course.dto.CommentDTO;
import com.eduplatform.course.dto.MuteUserRequest;
import com.eduplatform.course.dto.UnmuteUserRequest;
import com.eduplatform.course.service.BlockedWordService;
import com.eduplatform.course.service.ChapterCommentService;
import com.eduplatform.course.service.EnrollmentService;
import com.eduplatform.course.service.MuteService;
import com.eduplatform.course.vo.BlockedWordVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 章节评论控制器。
 * 设计意图：集中处理课程评论、禁言与屏蔽词管理，避免多入口导致的权限不一致。
 */
@Slf4j
@RestController
@RequestMapping("/api/comments")
@RequiredArgsConstructor
public class ChapterCommentController {

    private final ChapterCommentService commentService;
    private final MuteService muteService;
    private final BlockedWordService blockedWordService;
    private final EnrollmentService enrollmentService;

    /**
     * 获取章节评论列表。
     * 说明：支持分页与排序，供章节详情页展示。
     */
    @GetMapping("/chapter/{chapterId}")
    public Result<Map<String, Object>> getComments(
            @PathVariable("chapterId") Long chapterId,
            @RequestHeader(value = "X-User-Id", required = false) String currentUserIdHeader,
            @RequestParam(name = "userId", required = false) Long userId,
            @RequestParam(name = "sort", defaultValue = "time") String sort,
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "size", defaultValue = "20") int size) {

        // 优先使用网关注入身份，避免信任可伪造的查询参数
        Long effectiveUserId = resolveUserId(currentUserIdHeader, userId);

        log.info("获取章节评论, chapterId={}, sort={}, page={}", chapterId, sort, page);
        Map<String, Object> data = commentService.getComments(chapterId, effectiveUserId, sort, page, size);
        return Result.success(data);
    }

    /**
     * 发表评论。
     * 业务原因：写入前需要校验禁言状态与屏蔽词策略。
     */
    @PostMapping
    public Result<CommentDTO> createComment(
            @RequestHeader(value = "X-User-Id", required = false) String currentUserIdHeader,
            @RequestHeader(value = "X-User-Role", required = false) String currentUserRoleHeader,
            @Valid @RequestBody ChapterCommentCreateRequest body) {
        Long chapterId = body.getChapterId();
        Long courseId = body.getCourseId();
        Long userId = resolveUserId(currentUserIdHeader, null);
        if (userId == null) {
            return Result.failure(401, "身份认证失败");
        }
        String content = body.getContent();
        Long parentId = body.getParentId();

        log.info("发表评论, chapterId={}, userId={}, parentId={}", chapterId, userId, parentId);

        // 学生发表评论/提问必须先报名该课程
        if (isStudent(currentUserRoleHeader) && !enrollmentService.isEnrolled(userId, courseId)) {
            return Result.failure(403, "请先报名该课程后再提问");
        }

        // 检查用户是否被禁言
        if (muteService.isMuted(userId, courseId)) {
            return Result.failure(403, "您已被禁言，无法发表评论");
        }

        // 检查内容是否包含屏蔽词
        Map<String, Object> checkResult = blockedWordService.checkContent(content, courseId);
        if ((Boolean) checkResult.get("hasBlockedWord")) {
            return Result.failure(400, "评论内容包含敏感词，请修改后重试");
        }

        CommentDTO comment = commentService.createComment(chapterId, courseId, userId, content, parentId);
        return Result.success("评论发表成功", comment);
    }

    /**
     * 获取学生在各课程下的顶级提问（学生中心“我的提问”）。
     */
    @GetMapping("/chapter/student/{studentId}/questions")
    public Result<List<Map<String, Object>>> getStudentQuestions(
            @PathVariable("studentId") Long studentId,
            @RequestHeader(value = "X-User-Id", required = false) String currentUserIdHeader,
            @RequestHeader(value = "X-User-Role", required = false) String currentUserRoleHeader) {
        Long currentUserId = resolveUserId(currentUserIdHeader, null);
        if (!canAccessStudentData(studentId, currentUserId, currentUserRoleHeader)) {
            return Result.failure(403, "权限不足，仅本人、教师或管理员可查看学生提问");
        }
        return Result.success(commentService.getStudentQuestions(studentId, 50));
    }

    /**
     * 点赞/取消点赞。
     * 说明：用于前端即时反馈互动状态。
     */
    @PostMapping("/{id}/like")
    public Result<Map<String, Object>> toggleLike(
            @PathVariable("id") Long id,
            @RequestHeader(value = "X-User-Id", required = false) String currentUserIdHeader,
            @RequestParam(name = "userId", required = false) Long ignoredUserId) {

        Long userId = resolveUserId(currentUserIdHeader, ignoredUserId);
        if (userId == null) {
            return Result.failure(401, "身份认证失败");
        }

        log.info("点赞/取消点赞, commentId={}, userId={}", id, userId);
        boolean isLiked = commentService.toggleLike(id, userId);

        return Result.success(isLiked ? "点赞成功" : "取消点赞成功",
                Map.of("isLiked", isLiked));
    }

    /**
     * 置顶/取消置顶评论。
     * 说明：便于教师突出高价值讨论。
     */
    @PostMapping("/{id}/pin")
    public Result<Void> togglePin(
            @PathVariable("id") Long id,
            @RequestHeader(value = "X-User-Role", required = false) String currentUserRoleHeader) {
        if (!isTeacherOrAdmin(currentUserRoleHeader)) {
            return Result.failure(403, "权限不足，仅教师或管理员可置顶评论");
        }
        log.info("置顶/取消置顶评论, commentId={}", id);
        commentService.togglePin(id);
        return Result.success("操作成功", null);
    }

    /**
     * 删除评论。
     * 说明：支持管理员或作者删除。
     */
    @DeleteMapping("/{id}")
    public Result<Void> deleteComment(
            @PathVariable("id") Long id,
            @RequestHeader(value = "X-User-Id", required = false) String currentUserIdHeader,
            @RequestHeader(value = "X-User-Role", required = false) String currentUserRoleHeader,
            @RequestParam(name = "userId", required = false) Long ignoredUserId,
            @RequestParam(name = "isAdmin", required = false) Boolean ignoredIsAdmin) {

        Long userId = resolveUserId(currentUserIdHeader, ignoredUserId);
        if (userId == null) {
            return Result.failure(401, "身份认证失败");
        }
        boolean isAdmin = isAdmin(currentUserRoleHeader);

        log.info("删除评论, commentId={}, userId={}, isAdmin={}", id, userId, isAdmin);
        commentService.deleteComment(id, userId, isAdmin);
        return Result.success("删除成功", null);
    }

    /**
     * 获取评论的所有回复。
     */
    @GetMapping("/{id}/replies")
    public Result<List<CommentDTO>> getReplies(
            @PathVariable("id") Long id,
            @RequestHeader(value = "X-User-Id", required = false) String currentUserIdHeader,
            @RequestParam(name = "userId", required = false) Long userId) {

        Long effectiveUserId = resolveUserId(currentUserIdHeader, userId);

        log.info("获取评论回复, commentId={}", id);
        List<CommentDTO> replies = commentService.getReplies(id, effectiveUserId);
        return Result.success(replies);
    }

    // ==================== 禁言管理 ====================

    /**
     * 禁言用户。
     * 业务原因：对违规内容进行即时管控。
     */
    @PostMapping("/mute")
    public Result<Void> muteUser(
            @RequestHeader(value = "X-User-Id", required = false) String currentUserIdHeader,
            @RequestHeader(value = "X-User-Role", required = false) String currentUserRoleHeader,
            @Valid @RequestBody MuteUserRequest body) {
        if (!isTeacherOrAdmin(currentUserRoleHeader)) {
            return Result.failure(403, "权限不足，仅教师或管理员可禁言");
        }
        Long userId = body.getUserId();
        Long courseId = body.getCourseId();
        Long mutedBy = resolveUserId(currentUserIdHeader, null);
        if (mutedBy == null) {
            return Result.failure(401, "身份认证失败");
        }
        String reason = body.getReason();

        log.info("禁言用户, userId={}, courseId={}, mutedBy={}", userId, courseId, mutedBy);

        try {
            muteService.muteUser(userId, courseId, mutedBy, reason);
            return Result.success("禁言成功", null);
        } catch (Exception e) {
            log.warn("禁言失败: userId={}, courseId={}", userId, courseId, e);
            return Result.failure(400, "禁言失败，请检查请求后重试");
        }
    }

    /**
     * 解除禁言。
     */
    @PostMapping("/unmute")
    public Result<Void> unmuteUser(
            @RequestHeader(value = "X-User-Role", required = false) String currentUserRoleHeader,
            @Valid @RequestBody UnmuteUserRequest body) {
        if (!isTeacherOrAdmin(currentUserRoleHeader)) {
            return Result.failure(403, "权限不足，仅教师或管理员可解除禁言");
        }
        Long userId = body.getUserId();
        Long courseId = body.getCourseId();

        log.info("解除禁言, userId={}, courseId={}", userId, courseId);

        try {
            muteService.unmuteUser(userId, courseId);
            return Result.success("解除禁言成功", null);
        } catch (Exception e) {
            log.warn("解除禁言失败: userId={}, courseId={}", userId, courseId, e);
            return Result.failure(400, "解除禁言失败，请检查请求后重试");
        }
    }

    /**
     * 检查用户禁言状态。
     */
    @GetMapping("/mute-status")
    public Result<Map<String, Object>> getMuteStatus(
            @RequestHeader(value = "X-User-Id", required = false) String currentUserIdHeader,
            @RequestHeader(value = "X-User-Role", required = false) String currentUserRoleHeader,
            @RequestParam("userId") Long userId,
            @RequestParam("courseId") Long courseId) {

        Long currentUserId = resolveUserId(currentUserIdHeader, null);
        // 学生仅允许查询自己的禁言状态，教师与管理员可查询任意用户
        if (isStudent(currentUserRoleHeader) && currentUserId != null) {
            userId = currentUserId;
        }

        log.info("检查禁言状态, userId={}, courseId={}", userId, courseId);
        Map<String, Object> muteInfo = muteService.getMuteInfo(userId, courseId);
        return Result.success(muteInfo);
    }

    /**
     * 获取课程禁言记录列表。
     */
    @GetMapping("/mute-records")
    public Result<List<Map<String, Object>>> getMuteRecords(@RequestParam("courseId") Long courseId) {
        log.info("获取禁言记录, courseId={}", courseId);
        List<Map<String, Object>> records = muteService.getMuteRecords(courseId);
        return Result.success(records);
    }

    // ==================== 屏蔽词管理 ====================

    /**
     * 获取屏蔽词列表。
     */
    @GetMapping("/blocked-words")
    public Result<List<BlockedWordVO>> getBlockedWords(
            @RequestParam(defaultValue = "global") String scope,
            @RequestParam(required = false) Long courseId) {

        log.info("获取屏蔽词列表, scope={}, courseId={}", scope, courseId);
        List<BlockedWordVO> words = blockedWordService.convertToVOList(
                blockedWordService.getWords(scope, courseId));
        return Result.success(words);
    }

    /**
     * 添加屏蔽词。
     * 业务原因：避免敏感内容进入讨论区。
     */
    @PostMapping("/blocked-words")
    public Result<BlockedWordVO> addBlockedWord(
            @RequestHeader(value = "X-User-Id", required = false) String currentUserIdHeader,
            @RequestHeader(value = "X-User-Role", required = false) String currentUserRoleHeader,
            @Valid @RequestBody AddBlockedWordRequest body) {
        if (!isTeacherOrAdmin(currentUserRoleHeader)) {
            return Result.failure(403, "权限不足，仅教师或管理员可添加屏蔽词");
        }
        String word = body.getWord();
        String scope = body.getScope() != null ? body.getScope() : "global";
        Long courseId = body.getCourseId();
        Long createdBy = resolveUserId(currentUserIdHeader, null);
        if (createdBy == null) {
            return Result.failure(401, "身份认证失败");
        }

        log.info("添加屏蔽词, word={}, scope={}, courseId={}", word, scope, courseId);

        try {
            BlockedWordVO blockedWord = blockedWordService.convertToVO(
                    blockedWordService.addWord(word, scope, courseId, createdBy));
            return Result.success("添加成功", blockedWord);
        } catch (Exception e) {
            log.warn("添加屏蔽词失败: word={}, scope={}, courseId={}", word, scope, courseId, e);
            return Result.failure(400, "添加失败，请检查请求后重试");
        }
    }

    /**
     * 删除屏蔽词。
     */
    @DeleteMapping("/blocked-words/{id}")
    public Result<Void> deleteBlockedWord(
            @PathVariable("id") Long id,
            @RequestHeader(value = "X-User-Role", required = false) String currentUserRoleHeader) {
        if (!isTeacherOrAdmin(currentUserRoleHeader)) {
            return Result.failure(403, "权限不足，仅教师或管理员可删除屏蔽词");
        }
        log.info("删除屏蔽词, id={}", id);

        try {
            blockedWordService.deleteWord(id);
            return Result.success("删除成功", null);
        } catch (Exception e) {
            log.warn("删除屏蔽词失败: id={}", id, e);
            return Result.failure(400, "删除失败，请检查请求后重试");
        }
    }

    /**
     * 检查内容是否包含屏蔽词。
     */
    @PostMapping("/blocked-words/check")
    public Result<Map<String, Object>> checkBlockedWords(@Valid @RequestBody CheckBlockedWordRequest body) {
        String content = body.getContent();
        Long courseId = body.getCourseId();

        log.info("检查屏蔽词, content长度={}, courseId={}", content.length(), courseId);
        Map<String, Object> checkResult = blockedWordService.checkContent(content, courseId);
        return Result.success(checkResult);
    }

    /**
     * 解析当前登录用户ID。
     * 规则：优先使用网关注入身份，其次兼容历史参数。
     */
    private Long resolveUserId(String currentUserIdHeader, Long fallbackUserId) {
        if (currentUserIdHeader != null && !currentUserIdHeader.isBlank()) {
            try {
                return Long.valueOf(currentUserIdHeader);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return fallbackUserId;
    }

    /**
     * 判断是否为管理员角色。
     */
    private boolean isAdmin(String role) {
        return role != null && "admin".equalsIgnoreCase(role);
    }

    /**
     * 判断是否为学生角色。
     */
    private boolean isStudent(String role) {
        return role != null && "student".equalsIgnoreCase(role);
    }

    /**
     * 判断是否具备教学管理权限（教师或管理员）。
     */
    private boolean isTeacherOrAdmin(String role) {
        return role != null && ("teacher".equalsIgnoreCase(role) || "admin".equalsIgnoreCase(role));
    }

    /**
     * 学生数据访问控制：学生仅可访问本人，教师和管理员可用于教学管理查询。
     */
    private boolean canAccessStudentData(Long targetStudentId, Long currentUserId, String currentUserRole) {
        if (isTeacherOrAdmin(currentUserRole)) {
            return true;
        }
        return currentUserId != null && currentUserId.equals(targetStudentId);
    }
}
