package com.eduplatform.homework.controller;

import com.eduplatform.common.result.Result;
import com.eduplatform.common.security.InternalTokenVerifier;
import com.eduplatform.common.security.RequestContext;
import com.eduplatform.homework.dto.*;
import com.eduplatform.homework.entity.Homework;
import com.eduplatform.homework.service.HomeworkCascadeDeleteService;
import com.eduplatform.homework.service.HomeworkService;
import com.eduplatform.homework.vo.HomeworkQuestionDiscussionVO;
import com.eduplatform.homework.vo.HomeworkStudentQuestionVO;
import com.eduplatform.homework.vo.HomeworkVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 作业控制器。
 */
@Slf4j
@RestController
@RequestMapping("/api/homeworks")
@RequiredArgsConstructor
public class HomeworkController {

    private final HomeworkService homeworkService;
    private final HomeworkCascadeDeleteService homeworkCascadeDeleteService;
    private final InternalTokenVerifier internalTokenVerifier;
    private final RequestContext requestContext;

    @PostMapping
    public Result<HomeworkVO> createHomework(@Valid @RequestBody HomeworkCreateDTO dto) {
        if (!requestContext.isTeacherOrAdmin()) {
            return Result.failure(403, "权限不足，仅教师或管理员可创建作业");
        }
        try {
            Homework homework = homeworkService.createHomework(dto);
            return Result.success("作业创建成功", homeworkService.convertToVO(homework));
        } catch (Exception e) {
            log.error("创建作业失败", e);
            return Result.error("创建失败，请稍后重试");
        }
    }

    @GetMapping("/{id}")
    public Result<Map<String, Object>> getHomeworkDetail(@PathVariable("id") Long id) {
        try {
            Map<String, Object> detail = homeworkService.getHomeworkDetail(id);
            if (detail != null) {
                return Result.success(detail);
            }
            return Result.error("作业不存在");
        } catch (Exception e) {
            log.error("获取作业详情失败", e);
            return Result.error("获取作业详情失败，请稍后重试");
        }
    }

    @GetMapping("/chapter/{chapterId}")
    public Result<List<HomeworkWithStatsDTO>> getHomeworksByChapter(@PathVariable Long chapterId) {
        if (!requestContext.isTeacherOrAdmin()) {
            return Result.failure(403, "权限不足，仅教师或管理员可查看章节作业");
        }
        try {
            List<HomeworkWithStatsDTO> homeworks = homeworkService.getHomeworksByChapterWithStats(chapterId);
            return Result.success(homeworks);
        } catch (Exception e) {
            log.error("获取章节作业列表失败: chapterId={}", chapterId, e);
            return Result.error("获取作业失败，请稍后重试");
        }
    }

    @GetMapping("/student")
    public Result<List<StudentHomeworkDTO>> getStudentHomeworks(
            @RequestParam("studentId") Long studentId,
            @RequestParam("chapterId") Long chapterId) {
        if (!requestContext.canAccessStudentData(studentId)) {
            return Result.failure(403, "权限不足，仅本人、教师或管理员可查看学生作业");
        }
        try {
            List<StudentHomeworkDTO> homeworks = homeworkService.getStudentHomeworks(studentId, chapterId);
            return Result.success(homeworks);
        } catch (Exception e) {
            log.error("获取作业详情失败", e);
            return Result.error("获取学生作业失败，请稍后重试");
        }
    }

    @PostMapping("/unlock")
    public Result<Void> unlockHomework(
            @RequestParam Long studentId,
            @RequestParam Long chapterId,
            @RequestHeader(value = "X-Internal-Token", required = false) String requestInternalToken) {
        if (!hasValidInternalToken(requestInternalToken)) {
            return Result.failure(403, "禁止外部访问内部作业解锁接口");
        }

        try {
            homeworkService.unlockHomeworkByChapter(studentId, chapterId);
            return Result.success("作业已解锁", null);
        } catch (Exception e) {
            return Result.error("解锁失败，请稍后重试");
        }
    }

    @PostMapping("/submit")
    public Result<Map<String, Object>> submitHomework(@Valid @RequestBody HomeworkSubmitDTO dto,
            @RequestHeader(value = "X-User-Id", required = false) String currentUserIdHeader,
            @RequestHeader(value = "X-User-Role", required = false) String currentUserRole) {
        // 非管理员强制使用网关注入的真实身份
        Long currentUserId = parseUserId(currentUserIdHeader);
        if (!"admin".equalsIgnoreCase(currentUserRole) && dto != null) {
            dto.setStudentId(currentUserId);
        }

        try {
            Map<String, Object> result = homeworkService.submitHomework(dto);
            return Result.success("作业提交成功", result);
        } catch (Exception e) {
            log.error("提交作业失败: homeworkId={}, studentId={}", dto != null ? dto.getHomeworkId() : null, dto != null ? dto.getStudentId() : null, e);
            return Result.error("提交失败，请稍后重试");
        }
    }

    @GetMapping("/{id}/submission")
    public Result<Map<String, Object>> getSubmission(
            @PathVariable("id") Long homeworkId,
            @RequestParam("studentId") Long studentId) {
        if (!requestContext.canAccessStudentData(studentId)) {
            return Result.failure(403, "权限不足，仅本人、教师或管理员可查看提交详情");
        }
        try {
            Map<String, Object> submission = homeworkService.getSubmissionDetail(homeworkId, studentId);
            if (submission != null) {
                return Result.success(submission);
            }
            return Result.error("未找到提交记录");
        } catch (Exception e) {
            log.error("获取提交详情失败: homeworkId={}, studentId={}", homeworkId, studentId, e);
            return Result.error("获取提交详情失败，请稍后重试");
        }
    }

    @GetMapping("/{id}/report")
    public Result<Map<String, Object>> getErrorReport(
            @PathVariable Long id,
            @RequestParam Long studentId) {
        if (!requestContext.canAccessStudentData(studentId)) {
            return Result.failure(403, "权限不足，仅本人、教师或管理员可查看错题报告");
        }
        Map<String, Object> report = homeworkService.getErrorReport(studentId, id);
        if (report != null) {
            return Result.success(report);
        }
        return Result.error("未找到提交记录");
    }

    @PostMapping("/grade-subjective")
    public Result<Void> gradeSubjective(
            @RequestParam Long submissionId,
            @RequestParam Long questionId,
            @RequestParam Integer score,
            @RequestParam(required = false) String feedback) {
        if (!requestContext.isTeacherOrAdmin()) {
            return Result.failure(403, "权限不足，仅教师或管理员可批改作业");
        }
        try {
            homeworkService.gradeSubjective(submissionId, questionId, score, feedback);
            return Result.success("批改成功", null);
        } catch (Exception e) {
            log.error("批改主观题失败: submissionId={}, questionId={}", submissionId, questionId, e);
            return Result.error("批改失败，请稍后重试");
        }
    }

    @GetMapping("/{id}/submissions")
    public Result<List<Map<String, Object>>> getSubmissions(@PathVariable("id") Long homeworkId) {
        if (!requestContext.isTeacherOrAdmin()) {
            return Result.failure(403, "权限不足，仅教师或管理员可查看所有提交");
        }
        try {
            List<Map<String, Object>> submissions = homeworkService.getSubmissionsByHomework(homeworkId);
            return Result.success(submissions);
        } catch (Exception e) {
            log.error("获取提交记录失败: homeworkId={}", homeworkId, e);
            return Result.error("获取提交记录失败，请稍后重试");
        }
    }

    @GetMapping("/teacher/{teacherId}/todos")
    public Result<List<Map<String, Object>>> getTeacherTodos(@PathVariable Long teacherId) {
        if (!requestContext.canAccessTeacherData(teacherId)) {
            return Result.failure(403, "权限不足，仅教师本人或管理员可查看待办事项");
        }
        try {
            List<Map<String, Object>> todos = homeworkService.getTeacherTodos(teacherId);
            return Result.success(todos);
        } catch (Exception e) {
            log.error("获取作业详情失败", e);
            return Result.error("获取待办事项失败，请稍后重试");
        }
    }

    @GetMapping("/teacher/{teacherId}/activities")
    public Result<List<Map<String, Object>>> getTeacherActivities(@PathVariable Long teacherId) {
        if (!requestContext.canAccessTeacherData(teacherId)) {
            return Result.failure(403, "权限不足，仅教师本人或管理员可查看活动记录");
        }
        try {
            List<Map<String, Object>> activities = homeworkService.getTeacherActivities(teacherId);
            return Result.success(activities);
        } catch (Exception e) {
            log.error("获取作业详情失败", e);
            return Result.error("获取活动记录失败，请稍后重试");
        }
    }

    @GetMapping("/student/{studentId}/urgent")
    public Result<List<Map<String, Object>>> getStudentUrgentHomeworks(
            @PathVariable("studentId") Long studentId,
            @RequestParam(value = "days", defaultValue = "2") Integer days) {
        if (!requestContext.canAccessStudentData(studentId)) {
            return Result.failure(403, "权限不足，仅本人、教师或管理员可查看紧急作业");
        }
        try {
            List<Map<String, Object>> urgentHomeworks = homeworkService.getStudentUrgentHomeworks(studentId, days);
            return Result.success(urgentHomeworks);
        } catch (Exception e) {
            log.error("获取作业详情失败", e);
            return Result.error("获取紧急作业失败，请稍后重试");
        }
    }

    @GetMapping("/student/{studentId}/pending-count")
    public Result<Integer> getStudentPendingHomeworkCount(@PathVariable("studentId") Long studentId) {
        if (!requestContext.canAccessStudentData(studentId)) {
            return Result.failure(403, "权限不足，仅本人、教师或管理员可查看待完成数量");
        }
        try {
            int count = homeworkService.getStudentPendingHomeworkCount(studentId);
            return Result.success(count);
        } catch (Exception e) {
            return Result.error("获取待完成作业数量失败，请稍后重试");
        }
    }

    @GetMapping("/{id}/submissions/pending")
    public Result<PendingSubmissionsDTO> getPendingSubmissions(@PathVariable("id") Long homeworkId) {
        if (!requestContext.isTeacherOrAdmin()) {
            return Result.failure(403, "权限不足，仅教师或管理员可查看待批改列表");
        }
        try {
            PendingSubmissionsDTO result = homeworkService.getPendingSubmissions(homeworkId);
            if (result != null) {
                return Result.success(result);
            }
            return Result.error("作业不存在");
        } catch (Exception e) {
            log.error("获取作业详情失败", e);
            return Result.error("获取待批改列表失败，请稍后重试");
        }
    }

    @GetMapping("/submissions/{id}/detail")
    public Result<SubmissionDetailDTO> getSubmissionDetail(@PathVariable("id") Long submissionId) {
        if (!requestContext.isTeacherOrAdmin()) {
            return Result.failure(403, "权限不足，仅教师或管理员可查看提交详情");
        }
        try {
            SubmissionDetailDTO result = homeworkService.getSubmissionDetailForGrading(submissionId);
            if (result != null) {
                return Result.success(result);
            }
            return Result.error("提交记录不存在");
        } catch (Exception e) {
            log.error("获取作业详情失败", e);
            return Result.error("获取提交详情失败，请稍后重试");
        }
    }

    @PostMapping("/submissions/{id}/grade")
    public Result<Void> gradeSubmission(
            @PathVariable("id") Long submissionId,
            @Valid @RequestBody GradeSubmissionDTO dto,
            @RequestHeader(value = "X-User-Id", required = false) String currentUserIdHeader,
            @RequestHeader(value = "X-User-Role", required = false) String currentUserRole) {
        if (!requestContext.isTeacherOrAdmin()) {
            return Result.failure(403, "权限不足，仅教师或管理员可批改提交");
        }
        if (!"admin".equalsIgnoreCase(currentUserRole) && dto != null) {
            dto.setGradedBy(parseUserId(currentUserIdHeader));
        }

        try {
            homeworkService.gradeSubmission(submissionId, dto);
            return Result.success("批改成功", null);
        } catch (Exception e) {
            log.error("批量批改失败: submissionId={}", submissionId, e);
            return Result.error("批改失败，请稍后重试");
        }
    }

    @GetMapping("/health")
    public Result<Map<String, Object>> healthCheck() {
        Map<String, Object> health = Map.of(
                "status", "UP",
                "service", "homework-service",
                "timestamp", System.currentTimeMillis());
        return Result.success("健康检查成功", health);
    }

    @PostMapping("/{id}/duplicate")
    public Result<HomeworkVO> duplicateHomework(
            @PathVariable("id") Long id,
            @Valid @RequestBody(required = false) DuplicateHomeworkRequest request) {
        if (!requestContext.isTeacherOrAdmin()) {
            return Result.failure(403, "权限不足，仅教师或管理员可复制作业");
        }
        try {
            Long targetChapterId = request != null ? request.getChapterId() : null;
            String newTitle = request != null ? request.getTitle() : null;

            Homework newHomework = homeworkService.duplicateHomework(id, targetChapterId, newTitle);
            return Result.success("作业复制成功", homeworkService.convertToVO(newHomework));
        } catch (Exception e) {
            log.error("复制作业失败: homeworkId={}", id, e);
            return Result.error("复制失败，请稍后重试");
        }
    }

    @PostMapping("/{id}/import-questions")
    public Result<Map<String, Object>> importQuestions(
            @PathVariable("id") Long id,
            @Valid @RequestBody ImportHomeworkQuestionsRequest dto) {
        if (!requestContext.isTeacherOrAdmin()) {
            return Result.failure(403, "权限不足，仅教师或管理员可导入题目");
        }
        try {
            if (dto.getQuestions() == null || dto.getQuestions().isEmpty()) {
                return Result.error("题目列表不能为空");
            }

            Map<String, Object> result = homeworkService.importQuestions(id, dto.getQuestions());
            return Result.success("题目导入完成", result);
        } catch (Exception e) {
            log.error("导入题目失败: homeworkId={}", id, e);
            return Result.error("导入失败，请稍后重试");
        }
    }

    @DeleteMapping("/cascade/course/{courseId}")
    public Result<Void> deleteCourseRelatedData(
            @PathVariable("courseId") Long courseId,
            @RequestHeader(value = "X-Internal-Token", required = false) String requestInternalToken) {
        try {
            if (!hasValidInternalToken(requestInternalToken)) {
                return Result.failure(403, "禁止外部访问内部级联接口");
            }
            homeworkCascadeDeleteService.deleteCourseRelatedData(courseId);
            return Result.success("课程相关作业数据已删除", null);
        } catch (Exception e) {
            return Result.error("操作失败，请稍后重试");
        }
    }

    @DeleteMapping("/cascade/user/{userId}")
    public Result<Void> deleteUserRelatedData(
            @PathVariable("userId") Long userId,
            @RequestHeader(value = "X-Internal-Token", required = false) String requestInternalToken) {
        try {
            if (!hasValidInternalToken(requestInternalToken)) {
                return Result.failure(403, "禁止外部访问内部级联接口");
            }
            homeworkCascadeDeleteService.deleteUserRelatedData(userId);
            return Result.success("用户相关作业数据已删除", null);
        } catch (Exception e) {
            return Result.error("操作失败，请稍后重试");
        }
    }

    @PostMapping("/{homeworkId}/questions")
    public Result<Void> askQuestion(
            @PathVariable("homeworkId") Long homeworkId,
            @RequestParam Long studentId,
            @RequestParam(required = false) Long questionId,
            @RequestParam String content) {
        if (!requestContext.canAccessStudentData(studentId)) {
            return Result.failure(403, "权限不足，仅本人、教师或管理员可提问");
        }
        try {
            homeworkService.askQuestion(homeworkId, studentId, questionId, content);
            return Result.success("提问成功", null);
        } catch (Exception e) {
            return Result.error("提问失败，请稍后重试");
        }
    }

    @PostMapping("/questions/{discussionId}/reply")
    public Result<Void> replyQuestion(
            @PathVariable("discussionId") Long discussionId,
            @RequestParam Long teacherId,
            @RequestParam String reply) {
        if (!requestContext.canAccessTeacherData(teacherId)) {
            return Result.failure(403, "权限不足，仅教师本人或管理员可回复问题");
        }
        try {
            homeworkService.replyQuestion(discussionId, teacherId, reply);
            return Result.success("回复成功", null);
        } catch (Exception e) {
            return Result.error("回复失败，请稍后重试");
        }
    }

    @GetMapping("/{homeworkId}/questions")
    public Result<List<HomeworkQuestionDiscussionVO>> getHomeworkQuestions(
            @PathVariable("homeworkId") Long homeworkId) {
        try {
            List<HomeworkQuestionDiscussionVO> questions = homeworkService.getHomeworkQuestions(homeworkId);
            return Result.success(questions);
        } catch (Exception e) {
            log.error("获取作业详情失败", e);
            return Result.error("获取问答失败，请稍后重试");
        }
    }

    @GetMapping("/student/{studentId}/questions")
    public Result<List<HomeworkStudentQuestionVO>> getStudentQuestions(@PathVariable("studentId") Long studentId) {
        if (!requestContext.canAccessStudentData(studentId)) {
            return Result.failure(403, "权限不足，仅本人、教师或管理员可查看学生提问");
        }
        try {
            List<HomeworkStudentQuestionVO> questions = homeworkService.getStudentQuestions(studentId);
            return Result.success(questions);
        } catch (Exception e) {
            log.error("获取作业详情失败", e);
            return Result.error("获取提问失败，请稍后重试");
        }
    }

    @GetMapping("/teacher/{teacherId}/pending-questions-count")
    public Result<Integer> getPendingQuestionsCount(@PathVariable("teacherId") Long teacherId) {
        if (!requestContext.canAccessTeacherData(teacherId)) {
            return Result.failure(403, "权限不足，仅教师本人或管理员可查看待回复问题数");
        }
        try {
            int count = homeworkService.getTeacherPendingQuestionsCount(teacherId);
            return Result.success(count);
        } catch (Exception e) {
            return Result.error("获取待回复问题数量失败，请稍后重试");
        }
    }

    private boolean hasValidInternalToken(String requestInternalToken) {
        return internalTokenVerifier.isValid(requestInternalToken);
    }

    private Long parseUserId(String currentUserIdHeader) {
        return requestContext.parseUserId(currentUserIdHeader);
    }
}
