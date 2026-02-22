package com.eduplatform.progress.controller;

import com.eduplatform.common.result.Result;
import com.eduplatform.progress.dto.QuizSubmitDTO;
import com.eduplatform.progress.dto.VideoProgressDTO;
import com.eduplatform.progress.entity.ChapterProgress;
import com.eduplatform.progress.service.ProgressCascadeDeleteService;
import com.eduplatform.progress.service.ProgressService;
import com.eduplatform.progress.vo.ChapterProgressVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 学习进度控制器。
 * 设计意图：统一学习进度、测验与学情分析入口，控制层仅输出 VO 或结构化视图。
 */
@RestController
@RequestMapping("/api/progress")
@RequiredArgsConstructor
public class ProgressController {

    @Value("${security.internal-token}")
    private String internalToken;

    private final ProgressService progressService;
    private final ProgressCascadeDeleteService progressCascadeDeleteService;

    /**
     * 上报视频播放进度。
     * 业务原因：视频学习是主路径，需要实时落库并触发解锁。
     */
    @PostMapping("/video/report")
    public Result<Map<String, Object>> reportVideoProgress(
            @Valid @RequestBody VideoProgressDTO dto,
            @RequestHeader(value = "X-User-Id", required = false) String currentUserIdHeader,
            @RequestHeader(value = "X-User-Role", required = false) String currentUserRole) {
        // 视频进度上报默认以网关注入身份为准，防止伪造 studentId
        Long currentUserId = parseUserId(currentUserIdHeader);
        Long targetStudentId = dto != null && dto.getStudentId() != null ? dto.getStudentId() : currentUserId;
        if (!canAccessStudentData(targetStudentId, currentUserId, currentUserRole)) {
            return Result.failure(403, "权限不足，仅本人、教师或管理员可上报学习进度");
        }
        if (!isAdminRole(currentUserRole) && dto != null) {
            dto.setStudentId(currentUserId);
        }

        Map<String, Object> result = progressService.reportVideoProgress(dto);
        // 转换 Map 中的进度实体为 VO
        if (result.containsKey("progress")) {
            ChapterProgress progress = (ChapterProgress) result.get("progress");
            result.put("progress", progressService.convertToVO(progress));
        }
        return Result.success("进度已更新", result);
    }

    /**
     * 提交章节测验。
     * 说明：提交后会触发测验得分与勋章判定。
     */
    @PostMapping("/quiz/submit")
    public Result<Map<String, Object>> submitQuiz(
            @Valid @RequestBody QuizSubmitDTO dto,
            @RequestHeader(value = "X-User-Id", required = false) String currentUserIdHeader,
            @RequestHeader(value = "X-User-Role", required = false) String currentUserRole) {
        // 测验提交默认以网关注入身份为准，防止伪造 studentId
        Long currentUserId = parseUserId(currentUserIdHeader);
        Long targetStudentId = dto != null && dto.getStudentId() != null ? dto.getStudentId() : currentUserId;
        if (!canAccessStudentData(targetStudentId, currentUserId, currentUserRole)) {
            return Result.failure(403, "权限不足，仅本人、教师或管理员可提交测验");
        }
        if (!isAdminRole(currentUserRole) && dto != null) {
            dto.setStudentId(currentUserId);
        }

        Map<String, Object> result = progressService.submitQuiz(dto);
        return Result.success("测验提交成功", result);
    }

    /**
     * 获取章节进度。
     */
    @GetMapping("/chapter/{chapterId}")
    public Result<ChapterProgressVO> getChapterProgress(
            @PathVariable("chapterId") Long chapterId,
            @RequestParam("studentId") Long studentId,
            @RequestHeader(value = "X-User-Id", required = false) String currentUserIdHeader,
            @RequestHeader(value = "X-User-Role", required = false) String currentUserRole) {
        // 章节进度仅允许本人、教师或管理员查看
        Long currentUserId = parseUserId(currentUserIdHeader);
        if (!canAccessStudentData(studentId, currentUserId, currentUserRole)) {
            return Result.failure(403, "权限不足，仅本人、教师或管理员可查看章节进度");
        }

        ChapterProgress progress = progressService.getProgress(studentId, chapterId);
        if (progress != null) {
            return Result.success(progressService.convertToVO(progress));
        }
        return Result.success("暂无进度记录", null);
    }

    /**
     * 获取学生某课程所有章节进度。
     */
    @GetMapping("/course/{courseId}")
    public Result<List<ChapterProgressVO>> getCourseProgress(
            @PathVariable("courseId") Long courseId,
            @RequestParam("studentId") Long studentId,
            @RequestHeader(value = "X-User-Id", required = false) String currentUserIdHeader,
            @RequestHeader(value = "X-User-Role", required = false) String currentUserRole) {
        // 课程进度仅允许本人、教师或管理员查看
        Long currentUserId = parseUserId(currentUserIdHeader);
        if (!canAccessStudentData(studentId, currentUserId, currentUserRole)) {
            return Result.failure(403, "权限不足，仅本人、教师或管理员可查看课程进度");
        }

        List<ChapterProgress> progressList = progressService.getStudentCourseProgress(studentId, courseId);
        return Result.success(progressService.convertToVOList(progressList));
    }

    /**
     * 检查解锁条件。
     */
    @GetMapping("/check-unlock")
    public Result<Map<String, Object>> checkUnlockCondition(
            @RequestParam("studentId") Long studentId,
            @RequestParam("chapterId") Long chapterId,
            @RequestHeader(value = "X-User-Id", required = false) String currentUserIdHeader,
            @RequestHeader(value = "X-User-Role", required = false) String currentUserRole) {
        // 解锁条件仅允许本人、教师或管理员查看
        Long currentUserId = parseUserId(currentUserIdHeader);
        if (!canAccessStudentData(studentId, currentUserId, currentUserRole)) {
            return Result.failure(403, "权限不足，仅本人、教师或管理员可查看解锁条件");
        }

        Map<String, Object> result = progressService.checkUnlockCondition(studentId, chapterId);
        return Result.success(result);
    }

    /**
     * 获取学生某课程的上次学习位置。
     */
    @GetMapping("/course/{courseId}/last-position")
    public Result<Map<String, Object>> getLastStudyPosition(
            @PathVariable("courseId") Long courseId,
            @RequestParam("studentId") Long studentId,
            @RequestHeader(value = "X-User-Id", required = false) String currentUserIdHeader,
            @RequestHeader(value = "X-User-Role", required = false) String currentUserRole) {
        // 最后学习位置仅允许本人、教师或管理员查看
        Long currentUserId = parseUserId(currentUserIdHeader);
        if (!canAccessStudentData(studentId, currentUserId, currentUserRole)) {
            return Result.failure(403, "权限不足，仅本人、教师或管理员可查看学习位置");
        }

        Map<String, Object> result = progressService.getLastStudyPosition(studentId, courseId);
        return Result.success(result);
    }

    /**
     * 获取学生学习轨迹（真实数据）。
     */
    @GetMapping("/student/{studentId}/learning-track")
    public Result<Map<String, Object>> getLearningTrack(
            @PathVariable("studentId") Long studentId,
            @RequestHeader(value = "X-User-Id", required = false) String currentUserIdHeader,
            @RequestHeader(value = "X-User-Role", required = false) String currentUserRole) {
        // 学习轨迹仅允许本人、教师或管理员查看
        Long currentUserId = parseUserId(currentUserIdHeader);
        if (!canAccessStudentData(studentId, currentUserId, currentUserRole)) {
            return Result.failure(403, "权限不足，仅本人、教师或管理员可查看学习轨迹");
        }

        Map<String, Object> track = progressService.getLearningTrack(studentId);
        return Result.success(track);
    }

    /**
     * 获取知识点掌握度（真实数据）。
     */
    @GetMapping("/student/{studentId}/mastery")
    public Result<Map<String, Object>> getKnowledgeMastery(
            @PathVariable("studentId") Long studentId,
            @RequestHeader(value = "X-User-Id", required = false) String currentUserIdHeader,
            @RequestHeader(value = "X-User-Role", required = false) String currentUserRole) {
        // 知识掌握度仅允许本人、教师或管理员查看
        Long currentUserId = parseUserId(currentUserIdHeader);
        if (!canAccessStudentData(studentId, currentUserId, currentUserRole)) {
            return Result.failure(403, "权限不足，仅本人、教师或管理员可查看知识掌握度");
        }

        Map<String, Object> mastery = progressService.getKnowledgeMastery(studentId);
        return Result.success(mastery);
    }

    /**
     * 获取学生在特定课程的学习轨迹数据（教师端使用）。
     * 返回每日学习时长和完成章节数。
     */
    @GetMapping("/course/{courseId}/student/{studentId}/trajectory")
    public Result<List<Map<String, Object>>> getLearningTrajectory(
            @PathVariable("courseId") Long courseId,
            @PathVariable("studentId") Long studentId,
            @RequestHeader(value = "X-User-Id", required = false) String currentUserIdHeader,
            @RequestHeader(value = "X-User-Role", required = false) String currentUserRole) {
        // 轨迹分析仅允许本人、教师或管理员查看
        Long currentUserId = parseUserId(currentUserIdHeader);
        if (!canAccessStudentData(studentId, currentUserId, currentUserRole)) {
            return Result.failure(403, "权限不足，仅本人、教师或管理员可查看学习轨迹分析");
        }

        List<Map<String, Object>> trajectory = progressService.getLearningTrajectory(studentId, courseId);
        return Result.success(trajectory);
    }

    /**
     * 获取学生在特定课程的测验分数趋势（教师端使用）。
     * 返回按时间排序的测验分数列表。
     */
    @GetMapping("/course/{courseId}/student/{studentId}/quiz-trend")
    public Result<List<Map<String, Object>>> getQuizScoreTrend(
            @PathVariable("courseId") Long courseId,
            @PathVariable("studentId") Long studentId,
            @RequestHeader(value = "X-User-Id", required = false) String currentUserIdHeader,
            @RequestHeader(value = "X-User-Role", required = false) String currentUserRole) {
        // 测验趋势仅允许本人、教师或管理员查看
        Long currentUserId = parseUserId(currentUserIdHeader);
        if (!canAccessStudentData(studentId, currentUserId, currentUserRole)) {
            return Result.failure(403, "权限不足，仅本人、教师或管理员可查看测验趋势");
        }

        List<Map<String, Object>> quizTrend = progressService.getQuizScoreTrend(studentId, courseId);
        return Result.success(quizTrend);
    }

    /**
     * 获取学生在特定课程的详细学情分析（教师端使用）。
     * 包含学习轨迹、测验分数趋势、章节进度。
     */
    @GetMapping("/course/{courseId}/student/{studentId}/analytics")
    public Result<Map<String, Object>> getStudentCourseAnalytics(
            @PathVariable("courseId") Long courseId,
            @PathVariable("studentId") Long studentId,
            @RequestHeader(value = "X-User-Id", required = false) String currentUserIdHeader,
            @RequestHeader(value = "X-User-Role", required = false) String currentUserRole) {
        // 学情分析仅允许本人、教师或管理员查看
        Long currentUserId = parseUserId(currentUserIdHeader);
        if (!canAccessStudentData(studentId, currentUserId, currentUserRole)) {
            return Result.failure(403, "权限不足，仅本人、教师或管理员可查看学情分析");
        }

        Map<String, Object> analytics = progressService.getStudentCourseAnalytics(studentId, courseId);
        return Result.success(analytics);
    }

    /**
     * 获取课程分析数据（教师端使用）。
     * 包含课程概览、章节分析、题目难度分析、平台对比。
     */
    @GetMapping("/course/{courseId}/analytics")
    public Result<Map<String, Object>> getCourseAnalytics(
            @PathVariable("courseId") Long courseId,
            @RequestHeader(value = "X-User-Role", required = false) String currentUserRole) {
        // 课程分析属于教学管理数据，仅允许教师或管理员查看
        if (!hasTeacherManageRole(currentUserRole)) {
            return Result.failure(403, "权限不足，仅教师或管理员可查看课程分析");
        }

        Map<String, Object> analytics = progressService.getCourseAnalytics(courseId);
        return Result.success(analytics);
    }

    /**
     * 健康检查。
     */
    @GetMapping("/health")
    public Result<Map<String, Object>> healthCheck() {
        Map<String, Object> health = Map.of(
                "status", "UP",
                "service", "progress-service",
                "timestamp", System.currentTimeMillis());
        return Result.success("健康检查成功", health);
    }

    /**
     * 删除课程相关的进度数据（供课程服务调用）。
     */
    @DeleteMapping("/cascade/course/{courseId}")
    public Result<Void> deleteCourseRelatedData(
            @PathVariable("courseId") Long courseId,
            @RequestHeader(value = "X-Internal-Token", required = false) String requestInternalToken) {
        // 内部高危接口：仅允许服务间令牌调用
        if (requestInternalToken == null || !requestInternalToken.equals(internalToken)) {
            return Result.failure(403, "禁止外部访问内部级联接口");
        }
        progressCascadeDeleteService.deleteCourseRelatedData(courseId);
        return Result.success("课程相关进度数据已删除", null);
    }

    /**
     * 删除用户相关的进度数据（供课程服务调用）。
     */
    @DeleteMapping("/cascade/user/{userId}")
    public Result<Void> deleteUserRelatedData(
            @PathVariable("userId") Long userId,
            @RequestHeader(value = "X-Internal-Token", required = false) String requestInternalToken) {
        // 内部高危接口：仅允许服务间令牌调用
        if (requestInternalToken == null || !requestInternalToken.equals(internalToken)) {
            return Result.failure(403, "禁止外部访问内部级联接口");
        }
        progressCascadeDeleteService.deleteUserRelatedData(userId);
        return Result.success("用户相关进度数据已删除", null);
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
     * 判断是否为管理员角色。
     */
    private boolean isAdminRole(String currentUserRole) {
        return currentUserRole != null && "admin".equalsIgnoreCase(currentUserRole);
    }

    /**
     * 判断是否具备教学管理权限（教师或管理员）。
     */
    private boolean hasTeacherManageRole(String currentUserRole) {
        return currentUserRole != null
                && ("teacher".equalsIgnoreCase(currentUserRole) || "admin".equalsIgnoreCase(currentUserRole));
    }

    /**
     * 学生数据访问控制：学生仅可访问本人，教师和管理员可用于教学管理查询。
     */
    private boolean canAccessStudentData(Long targetStudentId, Long currentUserId, String currentUserRole) {
        if (hasTeacherManageRole(currentUserRole)) {
            return true;
        }
        return currentUserId != null && currentUserId.equals(targetStudentId);
    }
}
