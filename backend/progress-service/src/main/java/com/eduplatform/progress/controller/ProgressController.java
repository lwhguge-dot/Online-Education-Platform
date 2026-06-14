package com.eduplatform.progress.controller;

import com.eduplatform.common.result.Result;
import com.eduplatform.common.security.InternalTokenVerifier;
import com.eduplatform.common.security.RequestContext;
import com.eduplatform.progress.dto.QuizSubmitDTO;
import com.eduplatform.progress.dto.VideoProgressDTO;
import com.eduplatform.progress.entity.ChapterProgress;
import com.eduplatform.progress.service.ProgressCascadeDeleteService;
import com.eduplatform.progress.service.ProgressService;
import com.eduplatform.progress.vo.ChapterProgressVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 学习进度控制器。
 */
@RestController
@RequestMapping("/api/progress")
@RequiredArgsConstructor
public class ProgressController {

    private final ProgressService progressService;
    private final ProgressCascadeDeleteService progressCascadeDeleteService;
    private final InternalTokenVerifier internalTokenVerifier;
    private final RequestContext requestContext;

    @PostMapping("/video/report")
    public Result<Map<String, Object>> reportVideoProgress(
            @Valid @RequestBody VideoProgressDTO dto,
            @RequestHeader(value = "X-User-Id", required = false) String currentUserIdHeader,
            @RequestHeader(value = "X-User-Role", required = false) String currentUserRole) {
        Long currentUserId = parseUserId(currentUserIdHeader);
        if (!"admin".equalsIgnoreCase(currentUserRole) && dto != null) {
            dto.setStudentId(currentUserId);
        }

        Map<String, Object> result = progressService.reportVideoProgress(dto);
        if (result.containsKey("progress")) {
            ChapterProgress progress = (ChapterProgress) result.get("progress");
            result.put("progress", progressService.convertToVO(progress));
        }
        return Result.success("进度已更新", result);
    }

    @PostMapping("/quiz/submit")
    public Result<Map<String, Object>> submitQuiz(
            @Valid @RequestBody QuizSubmitDTO dto,
            @RequestHeader(value = "X-User-Id", required = false) String currentUserIdHeader,
            @RequestHeader(value = "X-User-Role", required = false) String currentUserRole) {
        Long currentUserId = parseUserId(currentUserIdHeader);
        if (!"admin".equalsIgnoreCase(currentUserRole) && dto != null) {
            dto.setStudentId(currentUserId);
        }

        Map<String, Object> result = progressService.submitQuiz(dto);
        return Result.success("测验提交成功", result);
    }

    @GetMapping("/chapter/{chapterId}")
    public Result<ChapterProgressVO> getChapterProgress(
            @PathVariable("chapterId") Long chapterId,
            @RequestParam(name = "studentId") Long studentId) {
        if (!requestContext.canAccessStudentData(studentId)) {
            return Result.failure(403, "权限不足，仅本人、教师或管理员可查看章节进度");
        }
        ChapterProgress progress = progressService.getProgress(studentId, chapterId);
        if (progress != null) {
            return Result.success(progressService.convertToVO(progress));
        }
        return Result.success("暂无进度记录", null);
    }

    @GetMapping("/course/{courseId}")
    public Result<List<ChapterProgressVO>> getCourseProgress(
            @PathVariable("courseId") Long courseId,
            @RequestParam(name = "studentId") Long studentId) {
        if (!requestContext.canAccessStudentData(studentId)) {
            return Result.failure(403, "权限不足，仅本人、教师或管理员可查看课程进度");
        }
        List<ChapterProgress> progressList = progressService.getStudentCourseProgress(studentId, courseId);
        return Result.success(progressService.convertToVOList(progressList));
    }

    @GetMapping("/check-unlock")
    public Result<Map<String, Object>> checkUnlockCondition(
            @RequestParam(name = "studentId") Long studentId,
            @RequestParam(name = "chapterId") Long chapterId) {
        if (!requestContext.canAccessStudentData(studentId)) {
            return Result.failure(403, "权限不足，仅本人、教师或管理员可检查解锁条件");
        }
        Map<String, Object> result = progressService.checkUnlockCondition(studentId, chapterId);
        return Result.success(result);
    }

    @GetMapping("/course/{courseId}/last-position")
    public Result<Map<String, Object>> getLastStudyPosition(
            @PathVariable("courseId") Long courseId,
            @RequestParam(name = "studentId") Long studentId) {
        if (!requestContext.canAccessStudentData(studentId)) {
            return Result.failure(403, "权限不足，仅本人、教师或管理员可查看最后学习位置");
        }
        Map<String, Object> result = progressService.getLastStudyPosition(studentId, courseId);
        return Result.success(result);
    }

    @GetMapping("/student/{studentId}/learning-track")
    public Result<Map<String, Object>> getLearningTrack(@PathVariable("studentId") Long studentId) {
        if (!requestContext.canAccessStudentData(studentId)) {
            return Result.failure(403, "权限不足，仅本人、教师或管理员可查看学习轨迹");
        }
        Map<String, Object> track = progressService.getLearningTrack(studentId);
        return Result.success(track);
    }

    @GetMapping("/student/{studentId}/mastery")
    public Result<Map<String, Object>> getKnowledgeMastery(@PathVariable("studentId") Long studentId) {
        if (!requestContext.canAccessStudentData(studentId)) {
            return Result.failure(403, "权限不足，仅本人、教师或管理员可查看知识掌握度");
        }
        Map<String, Object> mastery = progressService.getKnowledgeMastery(studentId);
        return Result.success(mastery);
    }

    @GetMapping("/course/{courseId}/student/{studentId}/trajectory")
    public Result<List<Map<String, Object>>> getLearningTrajectory(
            @PathVariable("courseId") Long courseId,
            @PathVariable("studentId") Long studentId) {
        if (!requestContext.canAccessStudentData(studentId)) {
            return Result.failure(403, "权限不足，仅本人、教师或管理员可查看学习轨迹");
        }
        List<Map<String, Object>> trajectory = progressService.getLearningTrajectory(studentId, courseId);
        return Result.success(trajectory);
    }

    @GetMapping("/course/{courseId}/student/{studentId}/quiz-trend")
    public Result<List<Map<String, Object>>> getQuizScoreTrend(
            @PathVariable("courseId") Long courseId,
            @PathVariable("studentId") Long studentId) {
        if (!requestContext.canAccessStudentData(studentId)) {
            return Result.failure(403, "权限不足，仅本人、教师或管理员可查看测验趋势");
        }
        List<Map<String, Object>> quizTrend = progressService.getQuizScoreTrend(studentId, courseId);
        return Result.success(quizTrend);
    }

    @GetMapping("/course/{courseId}/student/{studentId}/analytics")
    public Result<Map<String, Object>> getStudentCourseAnalytics(
            @PathVariable("courseId") Long courseId,
            @PathVariable("studentId") Long studentId) {
        if (!requestContext.canAccessStudentData(studentId)) {
            return Result.failure(403, "权限不足，仅本人、教师或管理员可查看学情分析");
        }
        Map<String, Object> analytics = progressService.getStudentCourseAnalytics(studentId, courseId);
        return Result.success(analytics);
    }

    @GetMapping("/course/{courseId}/analytics")
    public Result<Map<String, Object>> getCourseAnalytics(@PathVariable("courseId") Long courseId) {
        if (!requestContext.isTeacherOrAdmin()) {
            return Result.failure(403, "权限不足，仅教师或管理员可查看课程分析");
        }
        Map<String, Object> analytics = progressService.getCourseAnalytics(courseId);
        return Result.success(analytics);
    }

    @GetMapping("/health")
    public Result<Map<String, Object>> healthCheck() {
        Map<String, Object> health = Map.of(
                "status", "UP",
                "service", "progress-service",
                "timestamp", System.currentTimeMillis());
        return Result.success("健康检查成功", health);
    }

    @DeleteMapping("/cascade/course/{courseId}")
    public Result<Void> deleteCourseRelatedData(
            @PathVariable("courseId") Long courseId,
            @RequestHeader(value = "X-Internal-Token", required = false) String requestInternalToken) {
        if (!hasValidInternalToken(requestInternalToken)) {
            return Result.failure(403, "禁止外部访问内部级联接口");
        }
        progressCascadeDeleteService.deleteCourseRelatedData(courseId);
        return Result.success("课程相关进度数据已删除", null);
    }

    @DeleteMapping("/cascade/user/{userId}")
    public Result<Void> deleteUserRelatedData(
            @PathVariable("userId") Long userId,
            @RequestHeader(value = "X-Internal-Token", required = false) String requestInternalToken) {
        if (!hasValidInternalToken(requestInternalToken)) {
            return Result.failure(403, "禁止外部访问内部级联接口");
        }
        progressCascadeDeleteService.deleteUserRelatedData(userId);
        return Result.success("用户相关进度数据已删除", null);
    }

    private boolean hasValidInternalToken(String requestInternalToken) {
        return internalTokenVerifier.isValid(requestInternalToken);
    }

    private Long parseUserId(String currentUserIdHeader) {
        return requestContext.parseUserId(currentUserIdHeader);
    }
}
