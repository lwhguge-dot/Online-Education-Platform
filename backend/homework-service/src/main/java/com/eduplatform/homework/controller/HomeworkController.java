package com.eduplatform.homework.controller;

import com.eduplatform.common.result.Result;
import com.eduplatform.homework.dto.*;
import com.eduplatform.homework.entity.Homework;
import com.eduplatform.homework.service.HomeworkCascadeDeleteService;
import com.eduplatform.homework.service.HomeworkService;
import com.eduplatform.homework.vo.HomeworkQuestionDiscussionVO;
import com.eduplatform.homework.vo.HomeworkStudentQuestionVO;
import com.eduplatform.homework.vo.HomeworkVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 作业控制器。
 * 设计意图：统一作业全生命周期入口，控制层只接收 DTO 并输出 VO/结构化视图。
 *
 * @author Antigravity
 */
@Slf4j
@RestController
@RequestMapping("/api/homeworks")
@RequiredArgsConstructor
public class HomeworkController {

    private final HomeworkService homeworkService;
    private final HomeworkCascadeDeleteService homeworkCascadeDeleteService;

    /**
     * 创建作业（教师端接口）。
     * 业务原因：统一写入作业主表与题库子表，确保原子性。
     *
     * @param dto 作业创建 DTO，包含标题、描述、章节ID及题目列表
     * @return 创建成功的作业视图对象
     */
    @PostMapping
    public Result<HomeworkVO> createHomework(@RequestBody HomeworkCreateDTO dto) {
        try {
            Homework homework = homeworkService.createHomework(dto);
            return Result.success("作业创建成功", homeworkService.convertToVO(homework));
        } catch (Exception e) {
            return Result.error("创建失败: " + e.getMessage());
        }
    }

    /**
     * 获取作业详情。
     * 说明：返回作业元数据与题目列表聚合结果，减少前端多次请求。
     */
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
            return Result.error("获取作业详情失败: " + e.getMessage());
        }
    }

    /**
     * 获取章节作业列表（带统计信息）。
     * 业务原因：教师端需要展示提交量与批改量概览。
     */
    @GetMapping("/chapter/{chapterId}")
    public Result<List<HomeworkWithStatsDTO>> getHomeworksByChapter(@PathVariable Long chapterId) {
        try {
            List<HomeworkWithStatsDTO> homeworks = homeworkService.getHomeworksByChapterWithStats(chapterId);
            return Result.success(homeworks);
        } catch (Exception e) {
            log.error("获取作业详情失败", e);
            return Result.error("获取作业失败: " + e.getMessage());
        }
    }

    /**
     * 获取学生可用作业。
     * 说明：结合解锁状态返回学生可操作的作业列表。
     */
    @GetMapping("/student")
    public Result<List<StudentHomeworkDTO>> getStudentHomeworks(
            @RequestParam("studentId") Long studentId,
            @RequestParam("chapterId") Long chapterId) {
        try {
            List<StudentHomeworkDTO> homeworks = homeworkService.getStudentHomeworks(studentId, chapterId);
            return Result.success(homeworks);
        } catch (Exception e) {
            log.error("获取作业详情失败", e);
            return Result.error("获取学生作业失败: " + e.getMessage());
        }
    }

    /**
     * 解锁作业（由进度服务调用）。
     * 业务原因：章节完成后自动开放作业，避免前端手动维护。
     */
    @PostMapping("/unlock")
    public Result<Void> unlockHomework(
            @RequestParam Long studentId,
            @RequestParam Long chapterId) {
        try {
            homeworkService.unlockHomeworkByChapter(studentId, chapterId);
            return Result.success("作业已解锁", null);
        } catch (Exception e) {
            return Result.error("解锁失败: " + e.getMessage());
        }
    }

    /**
     * 提交作业（学生）。
     * 说明：包含自动判分与主观题待批改状态流转。
     */
    @PostMapping("/submit")
    public Result<Map<String, Object>> submitHomework(@RequestBody HomeworkSubmitDTO dto) {
        try {
            Map<String, Object> result = homeworkService.submitHomework(dto);
            return Result.success("作业提交成功", result);
        } catch (Exception e) {
            return Result.error("提交失败: " + e.getMessage());
        }
    }

    /**
     * 获取学生作业提交详情。
     * 说明：用于学生查看提交快照与得分情况。
     */
    @GetMapping("/{id}/submission")
    public Result<Map<String, Object>> getSubmission(
            @PathVariable("id") Long homeworkId,
            @RequestParam("studentId") Long studentId) {
        try {
            Map<String, Object> submission = homeworkService.getSubmissionDetail(homeworkId, studentId);
            if (submission != null) {
                return Result.success(submission);
            }
            return Result.error("未找到提交记录");
        } catch (Exception e) {
            log.error("获取作业详情失败", e);
            return Result.error("获取提交详情失败: " + e.getMessage());
        }
    }

    /**
     * 获取错题报告。
     * 业务原因：输出薄弱点，供学生复盘。
     */
    @GetMapping("/{id}/report")
    public Result<Map<String, Object>> getErrorReport(
            @PathVariable Long id,
            @RequestParam Long studentId) {
        Map<String, Object> report = homeworkService.getErrorReport(studentId, id);
        if (report != null) {
            return Result.success(report);
        }
        return Result.error("未找到提交记录");
    }

    /**
     * 教师批改主观题。
     * 说明：更新主观题得分与反馈。
     */
    @PostMapping("/grade-subjective")
    public Result<Void> gradeSubjective(
            @RequestParam Long submissionId,
            @RequestParam Long questionId,
            @RequestParam Integer score,
            @RequestParam(required = false) String feedback) {
        try {
            homeworkService.gradeSubjective(submissionId, questionId, score, feedback);
            return Result.success("批改成功", null);
        } catch (Exception e) {
            return Result.error("批改失败: " + e.getMessage());
        }
    }

    /**
     * 获取作业的所有提交记录（教师端用于批改）。
     */
    @GetMapping("/{id}/submissions")
    public Result<List<Map<String, Object>>> getSubmissions(@PathVariable("id") Long homeworkId) {
        try {
            List<Map<String, Object>> submissions = homeworkService.getSubmissionsByHomework(homeworkId);
            return Result.success(submissions);
        } catch (Exception e) {
            log.error("获取作业详情失败", e);
            return Result.error("获取提交记录失败: " + e.getMessage());
        }
    }

    /**
     * 获取教师待办事项（真实数据）。
     */
    @GetMapping("/teacher/{teacherId}/todos")
    public Result<List<Map<String, Object>>> getTeacherTodos(@PathVariable Long teacherId) {
        try {
            List<Map<String, Object>> todos = homeworkService.getTeacherTodos(teacherId);
            return Result.success(todos);
        } catch (Exception e) {
            log.error("获取作业详情失败", e);
            return Result.error("获取待办事项失败: " + e.getMessage());
        }
    }

    /**
     * 获取教师最近活动（真实数据）。
     */
    @GetMapping("/teacher/{teacherId}/activities")
    public Result<List<Map<String, Object>>> getTeacherActivities(@PathVariable Long teacherId) {
        try {
            List<Map<String, Object>> activities = homeworkService.getTeacherActivities(teacherId);
            return Result.success(activities);
        } catch (Exception e) {
            log.error("获取作业详情失败", e);
            return Result.error("获取活动记录失败: " + e.getMessage());
        }
    }

    /**
     * 获取学生紧急作业（截止日期在指定天数内的未完成作业）。
     */
    @GetMapping("/student/{studentId}/urgent")
    public Result<List<Map<String, Object>>> getStudentUrgentHomeworks(
            @PathVariable("studentId") Long studentId,
            @RequestParam(value = "days", defaultValue = "2") Integer days) {
        try {
            List<Map<String, Object>> urgentHomeworks = homeworkService.getStudentUrgentHomeworks(studentId, days);
            return Result.success(urgentHomeworks);
        } catch (Exception e) {
            log.error("获取作业详情失败", e);
            return Result.error("获取紧急作业失败: " + e.getMessage());
        }
    }

    /**
     * 获取学生待完成作业数量。
     */
    @GetMapping("/student/{studentId}/pending-count")
    public Result<Integer> getStudentPendingHomeworkCount(@PathVariable("studentId") Long studentId) {
        try {
            int count = homeworkService.getStudentPendingHomeworkCount(studentId);
            return Result.success(count);
        } catch (Exception e) {
            return Result.error("获取待完成作业数量失败: " + e.getMessage());
        }
    }

    // ==================== 批改工作台相关接口 ====================

    /**
     * 获取作业的待批改提交列表。
     */
    @GetMapping("/{id}/submissions/pending")
    public Result<PendingSubmissionsDTO> getPendingSubmissions(@PathVariable("id") Long homeworkId) {
        try {
            PendingSubmissionsDTO result = homeworkService.getPendingSubmissions(homeworkId);
            if (result != null) {
                return Result.success(result);
            }
            return Result.error("作业不存在");
        } catch (Exception e) {
            log.error("获取作业详情失败", e);
            return Result.error("获取待批改列表失败: " + e.getMessage());
        }
    }

    /**
     * 获取提交详情（用于批改工作台）。
     */
    @GetMapping("/submissions/{id}/detail")
    public Result<SubmissionDetailDTO> getSubmissionDetail(@PathVariable("id") Long submissionId) {
        try {
            SubmissionDetailDTO result = homeworkService.getSubmissionDetailForGrading(submissionId);
            if (result != null) {
                return Result.success(result);
            }
            return Result.error("提交记录不存在");
        } catch (Exception e) {
            log.error("获取作业详情失败", e);
            return Result.error("获取提交详情失败: " + e.getMessage());
        }
    }

    /**
     * 批量批改提交。
     */
    @PostMapping("/submissions/{id}/grade")
    public Result<Void> gradeSubmission(
            @PathVariable("id") Long submissionId,
            @RequestBody GradeSubmissionDTO dto) {
        try {
            homeworkService.gradeSubmission(submissionId, dto);
            return Result.success("批改成功", null);
        } catch (Exception e) {
            log.error("获取作业详情失败", e);
            return Result.error("批改失败: " + e.getMessage());
        }
    }

    /**
     * 健康检查。
     */
    @GetMapping("/health")
    public Result<Map<String, Object>> healthCheck() {
        Map<String, Object> health = Map.of(
                "status", "UP",
                "service", "homework-service",
                "timestamp", System.currentTimeMillis());
        return Result.success("健康检查成功", health);
    }

    /**
     * 复制作业（教师端接口）。
     *
     * @param id   原作业ID
     * @param body 请求体，可选包含新标题 (title) 和目标章节ID (chapterId)
     * @return 复制后新生成的作业视图对象
     */
    @PostMapping("/{id}/duplicate")
    public Result<HomeworkVO> duplicateHomework(
            @PathVariable("id") Long id,
            @RequestBody(required = false) Map<String, Object> body) {
        try {
            Long targetChapterId = body != null && body.get("chapterId") != null
                    ? Long.parseLong(body.get("chapterId").toString())
                    : null;
            String newTitle = body != null ? (String) body.get("title") : null;

            Homework newHomework = homeworkService.duplicateHomework(id, targetChapterId, newTitle);
            return Result.success("作业复制成功", homeworkService.convertToVO(newHomework));
        } catch (Exception e) {
            return Result.error("复制失败: " + e.getMessage());
        }
    }

    /**
     * 批量导入题目。
     * 说明：用于题库批量导入场景。
     */
    @PostMapping("/{id}/import-questions")
    public Result<Map<String, Object>> importQuestions(
            @PathVariable("id") Long id,
            @RequestBody HomeworkCreateDTO dto) {
        try {
            if (dto.getQuestions() == null || dto.getQuestions().isEmpty()) {
                return Result.error("题目列表不能为空");
            }

            Map<String, Object> result = homeworkService.importQuestions(id, dto.getQuestions());
            return Result.success("题目导入完成", result);
        } catch (Exception e) {
            return Result.error("导入失败: " + e.getMessage());
        }
    }

    /**
     * 删除课程相关的作业数据（供课程服务调用）。
     */
    @DeleteMapping("/cascade/course/{courseId}")
    public Result<Void> deleteCourseRelatedData(@PathVariable("courseId") Long courseId) {
        try {
            homeworkCascadeDeleteService.deleteCourseRelatedData(courseId);
            return Result.success("课程相关作业数据已删除", null);
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 删除用户相关的作业数据（供课程服务调用）。
     */
    @DeleteMapping("/cascade/user/{userId}")
    public Result<Void> deleteUserRelatedData(@PathVariable("userId") Long userId) {
        try {
            homeworkCascadeDeleteService.deleteUserRelatedData(userId);
            return Result.success("用户相关作业数据已删除", null);
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    // ==================== 作业问答相关接口 ====================

    /**
     * 学生提问。
     */
    @PostMapping("/{homeworkId}/questions")
    public Result<Void> askQuestion(
            @PathVariable("homeworkId") Long homeworkId,
            @RequestParam Long studentId,
            @RequestParam(required = false) Long questionId,
            @RequestParam String content) {
        try {
            homeworkService.askQuestion(homeworkId, studentId, questionId, content);
            return Result.success("提问成功", null);
        } catch (Exception e) {
            return Result.error("提问失败: " + e.getMessage());
        }
    }

    /**
     * 教师回复问题。
     */
    @PostMapping("/questions/{discussionId}/reply")
    public Result<Void> replyQuestion(
            @PathVariable("discussionId") Long discussionId,
            @RequestParam Long teacherId,
            @RequestParam String reply) {
        try {
            homeworkService.replyQuestion(discussionId, teacherId, reply);
            return Result.success("回复成功", null);
        } catch (Exception e) {
            return Result.error("回复失败: " + e.getMessage());
        }
    }

    /**
     * 获取作业的所有问答。
     */
    @GetMapping("/{homeworkId}/questions")
    public Result<List<HomeworkQuestionDiscussionVO>> getHomeworkQuestions(
            @PathVariable("homeworkId") Long homeworkId) {
        try {
            List<HomeworkQuestionDiscussionVO> questions = homeworkService.getHomeworkQuestions(homeworkId);
            return Result.success(questions);
        } catch (Exception e) {
            log.error("获取作业详情失败", e);
            return Result.error("获取问答失败: " + e.getMessage());
        }
    }

    /**
     * 获取学生的所有提问。
     */
    @GetMapping("/student/{studentId}/questions")
    public Result<List<HomeworkStudentQuestionVO>> getStudentQuestions(@PathVariable("studentId") Long studentId) {
        try {
            List<HomeworkStudentQuestionVO> questions = homeworkService.getStudentQuestions(studentId);
            return Result.success(questions);
        } catch (Exception e) {
            log.error("获取作业详情失败", e);
            return Result.error("获取提问失败: " + e.getMessage());
        }
    }

    /**
     * 获取教师待回复的问题数量。
     */
    @GetMapping("/teacher/{teacherId}/pending-questions-count")
    public Result<Integer> getPendingQuestionsCount(@PathVariable("teacherId") Long teacherId) {
        try {
            int count = homeworkService.getTeacherPendingQuestionsCount(teacherId);
            return Result.success(count);
        } catch (Exception e) {
            return Result.error("获取待回复问题数量失败: " + e.getMessage());
        }
    }
}
