package com.eduplatform.homework.controller;

import com.eduplatform.common.result.Result;
import com.eduplatform.homework.dto.PostCommentRequest;
import com.eduplatform.homework.dto.PostQuestionRequest;
import com.eduplatform.homework.dto.PublishAnswerRequest;
import com.eduplatform.homework.entity.SubjectiveComment;
import com.eduplatform.homework.service.CommentService;
import com.eduplatform.homework.service.HomeworkService;
import com.eduplatform.homework.vo.SubjectiveAnswerPermissionVO;
import com.eduplatform.homework.vo.SubjectiveCommentVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 评论/问答控制器。
 * 设计意图：集中处理主观题评论与问答，避免多入口导致权限不一致。
 *
 * @author Antigravity
 */
@RestController
@RequestMapping("/api/comments")
@RequiredArgsConstructor
@Slf4j
public class CommentController {

    private final CommentService commentService;
    private final HomeworkService homeworkService;

    /**
     * 发布学生答案（解锁评论区）。
     * 业务原因：学生答案发布后才开放讨论，保证讨论基于真实作答。
     */
    @PostMapping("/publish-answer")
    public Result<SubjectiveAnswerPermissionVO> publishAnswer(
            @RequestHeader(value = "X-User-Id", required = false) String currentUserIdHeader,
            @RequestHeader(value = "X-User-Role", required = false) String currentUserRole,
            @RequestParam Long studentId,
            @RequestParam Long questionId,
            @Valid @RequestBody PublishAnswerRequest body) {
        if (!hasSelfOrAdminAccess(studentId, currentUserIdHeader, currentUserRole)) {
            return Result.failure(403, "权限不足，仅本人或管理员可发布答案");
        }
        try {
            String answerContent = body.getAnswerContent();
            return Result.success("答案已发布，评论区已解锁",
                    commentService.convertToPermissionVO(
                            commentService.publishAnswer(studentId, questionId, answerContent)));
        } catch (Exception e) {
            log.error("发布答案失败: studentId={}, questionId={}", studentId, questionId, e);
            return Result.error("发布失败，请稍后重试");
        }
    }

    /**
     * 获取评论列表。
     * 说明：按题目维度聚合评论流。
     */
    @GetMapping("/question/{questionId}")
    public Result<Map<String, Object>> getComments(
            @PathVariable Long questionId,
            @RequestHeader(value = "X-User-Id", required = false) String currentUserIdHeader,
            @RequestParam Long userId,
            @RequestParam(defaultValue = "false") boolean isTeacher) {
        Long currentUserId = parseUserId(currentUserIdHeader);
        if (currentUserId != null) {
            userId = currentUserId;
        }
        Map<String, Object> result = commentService.getComments(questionId, userId, isTeacher);
        return Result.success(result);
    }

    /**
     * 发表评论/回复。
     *
     * @param questionId 题目ID
     * @param userId     发言用户ID
     * @param parentId   父级评论ID（可选）
     * @param isTeacher  是否为教师身份
     * @param body       请求体，包含内容 (content)
     * @return 发表成功的评论视图对象
     */
    @PostMapping
    public Result<SubjectiveCommentVO> postComment(
            @RequestParam Long questionId,
            @RequestHeader(value = "X-User-Id", required = false) String currentUserIdHeader,
            @RequestParam Long userId,
            @RequestParam(required = false) Long parentId,
            @RequestParam(defaultValue = "false") boolean isTeacher,
            @Valid @RequestBody PostCommentRequest body) {
        Long currentUserId = parseUserId(currentUserIdHeader);
        if (currentUserId != null) {
            userId = currentUserId;
        }
        try {
            String content = body.getContent();
            SubjectiveComment comment = commentService.postComment(questionId, userId, content, parentId, isTeacher);
            return Result.success("评论发布成功", homeworkService.convertToCommentVO(comment));
        } catch (Exception e) {
            log.error("发布评论失败: questionId={}, userId={}", questionId, userId, e);
            return Result.error("发布失败，请稍后重试");
        }
    }

    /**
     * 教师发布题目/标准解答。
     *
     * @param questionId 题目ID
     * @param teacherId  教师ID
     * @param body       请求体，包含题目/解答内容 (questionContent)
     * @return 发布成功的评论视图对象
     */
    @PostMapping("/post-question")
    public Result<SubjectiveCommentVO> postQuestion(
            @RequestParam Long questionId,
            @RequestHeader(value = "X-User-Id", required = false) String currentUserIdHeader,
            @RequestHeader(value = "X-User-Role", required = false) String currentUserRole,
            @RequestParam Long teacherId,
            @Valid @RequestBody PostQuestionRequest body) {
        if (!isTeacherOrAdmin(currentUserRole)) {
            return Result.failure(403, "权限不足，仅教师或管理员可发布题目");
        }
        Long currentUserId = parseUserId(currentUserIdHeader);
        if (currentUserId != null) {
            teacherId = currentUserId;
        }
        try {
            String questionContent = body.getQuestionContent();
            SubjectiveComment comment = commentService.postQuestion(questionId, teacherId, questionContent);
            return Result.success("题目已发布", homeworkService.convertToCommentVO(comment));
        } catch (Exception e) {
            log.error("发布题目失败: questionId={}, teacherId={}", questionId, teacherId, e);
            return Result.error("发布失败，请稍后重试");
        }
    }

    /**
     * 置顶/取消置顶。
     */
    @PutMapping("/{commentId}/toggle-top")
    public Result<Void> toggleTop(
            @PathVariable Long commentId,
            @RequestHeader(value = "X-User-Role", required = false) String currentUserRole) {
        if (!isTeacherOrAdmin(currentUserRole)) {
            return Result.failure(403, "权限不足，仅教师或管理员可置顶评论");
        }
        try {
            commentService.toggleTop(commentId);
            return Result.success("操作成功", null);
        } catch (Exception e) {
            log.error("置顶评论失败: commentId={}", commentId, e);
            return Result.error("操作失败，请稍后重试");
        }
    }

    /**
     * 删除评论。
     */
    @DeleteMapping("/{commentId}")
    public Result<Void> deleteComment(
            @PathVariable Long commentId,
            @RequestHeader(value = "X-User-Id", required = false) String currentUserIdHeader,
            @RequestHeader(value = "X-User-Role", required = false) String currentUserRole) {
        Long currentUserId = parseUserId(currentUserIdHeader);
        if (currentUserId == null) {
            return Result.failure(401, "身份认证失败");
        }
        try {
            commentService.deleteComment(commentId, currentUserId, isAdmin(currentUserRole));
            return Result.success("删除成功", null);
        } catch (Exception e) {
            log.error("删除评论失败: commentId={}, userId={}", commentId, currentUserId, e);
            return Result.error("删除失败，请稍后重试");
        }
    }

    /**
     * 获取权限状态。
     */
    @GetMapping("/permission")
    public Result<SubjectiveAnswerPermissionVO> getPermission(
            @RequestHeader(value = "X-User-Id", required = false) String currentUserIdHeader,
            @RequestHeader(value = "X-User-Role", required = false) String currentUserRole,
            @RequestParam Long studentId,
            @RequestParam Long questionId) {
        if (!hasSelfOrAdminAccess(studentId, currentUserIdHeader, currentUserRole)) {
            return Result.failure(403, "权限不足，仅本人或管理员可查看权限状态");
        }
        return Result.success(commentService.convertToPermissionVO(
                commentService.getPermission(studentId, questionId)));
    }

    /**
     * 获取学生的问题列表（学生中心用）。
     */
    @GetMapping("/student/{studentId}/questions")
    public Result<?> getStudentQuestions(
            @PathVariable Long studentId,
            @RequestHeader(value = "X-User-Id", required = false) String currentUserIdHeader,
            @RequestHeader(value = "X-User-Role", required = false) String currentUserRole) {
        if (!hasSelfOrAdminAccess(studentId, currentUserIdHeader, currentUserRole)) {
            return Result.failure(403, "权限不足，仅本人或管理员可查看学生问题列表");
        }
        try {
            return Result.success(commentService.getStudentQuestions(studentId));
        } catch (Exception e) {
            log.error("获取学生问题列表失败: studentId={}", studentId, e);
            return Result.error("获取问题列表失败，请稍后重试");
        }
    }

    /**
     * 获取教师的问题列表（教师中心用）。
     */
    @GetMapping("/teacher/{teacherId}/questions")
    public Result<?> getTeacherQuestions(
            @PathVariable Long teacherId,
            @RequestHeader(value = "X-User-Id", required = false) String currentUserIdHeader,
            @RequestHeader(value = "X-User-Role", required = false) String currentUserRole) {
        if (!isTeacherOrAdmin(currentUserRole) || !hasSelfOrAdminAccess(teacherId, currentUserIdHeader, currentUserRole)) {
            return Result.failure(403, "权限不足，仅本人教师或管理员可查看教师问题列表");
        }
        try {
            return Result.success(commentService.getTeacherQuestions(teacherId));
        } catch (Exception e) {
            log.error("获取教师问题列表失败: teacherId={}", teacherId, e);
            return Result.error("获取问题列表失败，请稍后重试");
        }
    }

    /**
     * 解析网关注入用户ID。
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
     * 判断是否管理员。
     */
    private boolean isAdmin(String role) {
        return role != null && "admin".equalsIgnoreCase(role);
    }

    /**
     * 判断是否教师或管理员。
     */
    private boolean isTeacherOrAdmin(String role) {
        return role != null && ("teacher".equalsIgnoreCase(role) || "admin".equalsIgnoreCase(role));
    }

    /**
     * 判断是否本人或管理员。
     */
    private boolean hasSelfOrAdminAccess(Long targetUserId, String currentUserIdHeader, String currentUserRole) {
        if (isAdmin(currentUserRole)) {
            return true;
        }
        Long currentUserId = parseUserId(currentUserIdHeader);
        return currentUserId != null && currentUserId.equals(targetUserId);
    }
}
