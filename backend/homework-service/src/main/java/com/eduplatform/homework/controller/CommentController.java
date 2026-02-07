package com.eduplatform.homework.controller;

import com.eduplatform.common.result.Result;
import com.eduplatform.homework.entity.SubjectiveComment;
import com.eduplatform.homework.service.CommentService;
import com.eduplatform.homework.service.HomeworkService;
import com.eduplatform.homework.vo.SubjectiveAnswerPermissionVO;
import com.eduplatform.homework.vo.SubjectiveCommentVO;
import lombok.RequiredArgsConstructor;
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
public class CommentController {

    private final CommentService commentService;
    private final HomeworkService homeworkService;

    /**
     * 发布学生答案（解锁评论区）。
     * 业务原因：学生答案发布后才开放讨论，保证讨论基于真实作答。
     */
    @PostMapping("/publish-answer")
    public Result<SubjectiveAnswerPermissionVO> publishAnswer(
            @RequestParam Long studentId,
            @RequestParam Long questionId,
            @RequestBody Map<String, String> body) {
        try {
            String answerContent = body.get("answerContent");
            return Result.success("答案已发布，评论区已解锁",
                    commentService.convertToPermissionVO(
                            commentService.publishAnswer(studentId, questionId, answerContent)));
        } catch (Exception e) {
            return Result.error("发布失败: " + e.getMessage());
        }
    }

    /**
     * 获取评论列表。
     * 说明：按题目维度聚合评论流。
     */
    @GetMapping("/question/{questionId}")
    public Result<Map<String, Object>> getComments(
            @PathVariable Long questionId,
            @RequestParam Long userId,
            @RequestParam(defaultValue = "false") boolean isTeacher) {
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
            @RequestParam Long userId,
            @RequestParam(required = false) Long parentId,
            @RequestParam(defaultValue = "false") boolean isTeacher,
            @RequestBody Map<String, String> body) {
        try {
            String content = body.get("content");
            SubjectiveComment comment = commentService.postComment(questionId, userId, content, parentId, isTeacher);
            return Result.success("评论发布成功", homeworkService.convertToCommentVO(comment));
        } catch (Exception e) {
            return Result.error(e.getMessage());
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
            @RequestParam Long teacherId,
            @RequestBody Map<String, String> body) {
        try {
            String questionContent = body.get("questionContent");
            SubjectiveComment comment = commentService.postQuestion(questionId, teacherId, questionContent);
            return Result.success("题目已发布", homeworkService.convertToCommentVO(comment));
        } catch (Exception e) {
            return Result.error("发布失败: " + e.getMessage());
        }
    }

    /**
     * 置顶/取消置顶。
     */
    @PutMapping("/{commentId}/toggle-top")
    public Result<Void> toggleTop(@PathVariable Long commentId) {
        try {
            commentService.toggleTop(commentId);
            return Result.success("操作成功", null);
        } catch (Exception e) {
            return Result.error("操作失败: " + e.getMessage());
        }
    }

    /**
     * 删除评论。
     */
    @DeleteMapping("/{commentId}")
    public Result<Void> deleteComment(@PathVariable Long commentId) {
        try {
            commentService.deleteComment(commentId);
            return Result.success("删除成功", null);
        } catch (Exception e) {
            return Result.error("删除失败: " + e.getMessage());
        }
    }

    /**
     * 获取权限状态。
     */
    @GetMapping("/permission")
    public Result<SubjectiveAnswerPermissionVO> getPermission(
            @RequestParam Long studentId,
            @RequestParam Long questionId) {
        return Result.success(commentService.convertToPermissionVO(
                commentService.getPermission(studentId, questionId)));
    }

    /**
     * 获取学生的问题列表（学生中心用）。
     */
    @GetMapping("/student/{studentId}/questions")
    public Result<?> getStudentQuestions(@PathVariable Long studentId) {
        try {
            return Result.success(commentService.getStudentQuestions(studentId));
        } catch (Exception e) {
            return Result.error("获取问题列表失败: " + e.getMessage());
        }
    }

    /**
     * 获取教师的问题列表（教师中心用）。
     */
    @GetMapping("/teacher/{teacherId}/questions")
    public Result<?> getTeacherQuestions(@PathVariable Long teacherId) {
        try {
            return Result.success(commentService.getTeacherQuestions(teacherId));
        } catch (Exception e) {
            return Result.error("获取问题列表失败: " + e.getMessage());
        }
    }
}
