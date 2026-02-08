package com.eduplatform.course.controller;

import com.eduplatform.common.result.Result;
import com.eduplatform.course.entity.Enrollment;
import com.eduplatform.course.service.EnrollmentService;
import com.eduplatform.course.vo.EnrollmentVO;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 课程报名控制器。
 * 设计意图：统一课程报名与学习进度维护入口，控制层仅输出 VO。
 */
@RestController
@RequestMapping("/api/enrollments")
@RequiredArgsConstructor
public class EnrollmentController {

    @Value("${security.internal-token}")
    private String internalToken;

    private final EnrollmentService enrollmentService;

    /**
     * 学生报名课程。
     * 业务原因：报名会初始化学习进度与统计数据。
     */
    @PostMapping("/enroll")
    public Result<EnrollmentVO> enroll(
            @RequestParam("studentId") Long studentId,
            @RequestParam("courseId") Long courseId,
            @RequestHeader(value = "X-User-Id", required = false) String currentUserIdHeader,
            @RequestHeader(value = "X-User-Role", required = false) String currentUserRole) {
        // 报名仅允许本人发起，管理员可代操作
        Long currentUserId = parseUserId(currentUserIdHeader);
        if (!canAccessStudentData(studentId, currentUserId, currentUserRole)) {
            return Result.failure(403, "权限不足，仅本人、教师或管理员可发起报名");
        }

        try {
            Enrollment enrollment = enrollmentService.enroll(studentId, courseId);
            return Result.success("报名成功", enrollmentService.convertToVO(enrollment));
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 学生退课。
     * 说明：退课会同步更新课程统计。
     */
    @PostMapping("/drop")
    public Result<Void> drop(
            @RequestParam("studentId") Long studentId,
            @RequestParam("courseId") Long courseId,
            @RequestHeader(value = "X-User-Id", required = false) String currentUserIdHeader,
            @RequestHeader(value = "X-User-Role", required = false) String currentUserRole) {
        // 退课仅允许本人发起，管理员可代操作
        Long currentUserId = parseUserId(currentUserIdHeader);
        if (!canAccessStudentData(studentId, currentUserId, currentUserRole)) {
            return Result.failure(403, "权限不足，仅本人、教师或管理员可发起退课");
        }

        try {
            enrollmentService.drop(studentId, courseId);
            return Result.success("退课成功", null);
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 校验报名关系。
     * 说明：用于前端判断是否展示报名/学习入口。
     */
    @GetMapping("/check")
    public Result<Map<String, Object>> checkEnrollment(
            @RequestParam("studentId") Long studentId,
            @RequestParam("courseId") Long courseId,
            @RequestHeader(value = "X-User-Id", required = false) String currentUserIdHeader,
            @RequestHeader(value = "X-User-Role", required = false) String currentUserRole) {
        // 报名关系仅允许本人、教师或管理员查看
        Long currentUserId = parseUserId(currentUserIdHeader);
        if (!canAccessStudentData(studentId, currentUserId, currentUserRole)) {
            return Result.failure(403, "权限不足，仅本人、教师或管理员可查看报名关系");
        }

        Map<String, Object> result = new HashMap<>();
        result.put("enrolled", enrollmentService.isEnrolled(studentId, courseId));
        Enrollment enrollment = enrollmentService.getEnrollment(studentId, courseId);
        if (enrollment != null) {
            result.put("enrollment", enrollmentService.convertToVO(enrollment));
        }
        return Result.success(result);
    }

    /**
     * 获取学生报名的课程列表。
     */
    @GetMapping("/student/{studentId}")
    public Result<List<EnrollmentVO>> getStudentEnrollments(
            @PathVariable("studentId") Long studentId,
            @RequestHeader(value = "X-User-Id", required = false) String currentUserIdHeader,
            @RequestHeader(value = "X-User-Role", required = false) String currentUserRole) {
        // 学生报名列表仅允许本人、教师或管理员查看
        Long currentUserId = parseUserId(currentUserIdHeader);
        if (!canAccessStudentData(studentId, currentUserId, currentUserRole)) {
            return Result.failure(403, "权限不足，仅本人、教师或管理员可查看学生报名列表");
        }

        List<Enrollment> enrollments = enrollmentService.getStudentEnrollments(studentId);
        return Result.success(enrollmentService.convertToVOList(enrollments));
    }

    /**
     * 获取课程的报名学生列表。
     */
    @GetMapping("/course/{courseId}")
    public Result<List<EnrollmentVO>> getCourseEnrollments(
            @PathVariable("courseId") Long courseId,
            @RequestHeader(value = "X-User-Role", required = false) String currentUserRole) {
        // 课程报名学生列表仅允许教师或管理员查看
        if (!hasTeacherManageRole(currentUserRole)) {
            return Result.failure(403, "权限不足，仅教师或管理员可查看课程报名列表");
        }

        List<Enrollment> enrollments = enrollmentService.getCourseEnrollments(courseId);
        return Result.success(enrollmentService.convertToVOList(enrollments));
    }

    /**
     * 更新学习进度。
     * 业务原因：用于学情统计与成就系统。
     */
    @PutMapping("/progress")
    public Result<Void> updateProgress(
            @RequestParam("studentId") Long studentId,
            @RequestParam("courseId") Long courseId,
            @RequestParam("progress") Integer progress,
            @RequestHeader(value = "X-User-Id", required = false) String currentUserIdHeader,
            @RequestHeader(value = "X-User-Role", required = false) String currentUserRole) {
        // 报名进度仅允许本人、教师或管理员更新
        Long currentUserId = parseUserId(currentUserIdHeader);
        if (!canAccessStudentData(studentId, currentUserId, currentUserRole)) {
            return Result.failure(403, "权限不足，仅本人、教师或管理员可更新报名进度");
        }

        try {
            enrollmentService.updateProgress(studentId, courseId, progress);
            return Result.success("进度已更新", null);
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 获取报名统计。
     */
    @GetMapping("/stats")
    public Result<Map<String, Object>> getStats(
            @RequestParam(required = false) Long studentId,
            @RequestParam(required = false) Long courseId,
            @RequestHeader(value = "X-User-Id", required = false) String currentUserIdHeader,
            @RequestHeader(value = "X-User-Role", required = false) String currentUserRole) {
        // 统计接口：含 studentId 时仅允许本人、教师或管理员；仅 courseId 时要求教师或管理员
        Long currentUserId = parseUserId(currentUserIdHeader);
        if (studentId != null && !canAccessStudentData(studentId, currentUserId, currentUserRole)) {
            return Result.failure(403, "权限不足，仅本人、教师或管理员可查看学生报名统计");
        }
        if (studentId == null && courseId != null && !hasTeacherManageRole(currentUserRole)) {
            return Result.failure(403, "权限不足，仅教师或管理员可查看课程报名统计");
        }

        Map<String, Object> stats = new HashMap<>();
        if (studentId != null) {
            stats.put("studentEnrollments", enrollmentService.countByStudent(studentId));
        }
        if (courseId != null) {
            stats.put("courseEnrollments", enrollmentService.countByCourse(courseId));
        }
        return Result.success(stats);
    }

    /**
     * 获取学生报名课程并标注新章节。
     */
    @GetMapping("/student/{studentId}/with-new-chapters")
    public Result<List<Map<String, Object>>> getStudentEnrollmentsWithNewChapters(
            @PathVariable("studentId") Long studentId,
            @RequestHeader(value = "X-User-Id", required = false) String currentUserIdHeader,
            @RequestHeader(value = "X-User-Role", required = false) String currentUserRole) {
        // 新章节提醒仅允许本人、教师或管理员查看
        Long currentUserId = parseUserId(currentUserIdHeader);
        if (!canAccessStudentData(studentId, currentUserId, currentUserRole)) {
            return Result.failure(403, "权限不足，仅本人、教师或管理员可查看新章节提醒");
        }

        List<Map<String, Object>> enrollments = enrollmentService.getStudentEnrollmentsWithNewChapters(studentId);
        // 转换 Map 中的 enrollment 实体为 VO
        enrollments.forEach(map -> {
            if (map.containsKey("enrollment")) {
                map.put("enrollment", enrollmentService.convertToVO((Enrollment) map.get("enrollment")));
            }
        });
        return Result.success(enrollments);
    }

    /**
     * 检查指定课程是否有新章节。
     */
    @GetMapping("/check-new-chapters")
    public Result<Map<String, Object>> checkNewChapters(
            @RequestParam("studentId") Long studentId,
            @RequestParam("courseId") Long courseId,
            @RequestHeader(value = "X-User-Id", required = false) String currentUserIdHeader,
            @RequestHeader(value = "X-User-Role", required = false) String currentUserRole) {
        // 新章节检查仅允许本人、教师或管理员查看
        Long currentUserId = parseUserId(currentUserIdHeader);
        if (!canAccessStudentData(studentId, currentUserId, currentUserRole)) {
            return Result.failure(403, "权限不足，仅本人、教师或管理员可检查新章节");
        }

        Map<String, Object> result = enrollmentService.checkNewChapters(studentId, courseId);
        return Result.success(result);
    }

    /**
     * 获取课程选课学生人数。
     * 业务原因：供 user-service 通过 Feign 调用，计算公告目标受众人数。
     */
    @GetMapping("/course/{courseId}/count")
    public Result<Long> getCourseStudentCount(
            @PathVariable("courseId") Long courseId,
            @RequestHeader(value = "X-User-Role", required = false) String currentUserRole,
            @RequestHeader(value = "X-Internal-Token", required = false) String requestInternalToken) {
        // 课程选课人数用于聚合统计，仅允许教师/管理员或内部服务访问
        if (!hasTeacherManageRole(currentUserRole) && !hasValidInternalToken(requestInternalToken)) {
            return Result.failure(403, "权限不足，仅教师、管理员或内部服务可查看选课人数");
        }

        long count = enrollmentService.countByCourse(courseId);
        return Result.success(count);
    }

    /**
     * 获取课程今日新增学生数。
     */
    @GetMapping("/course/{courseId}/today")
    public Result<Integer> getTodayEnrollments(
            @PathVariable("courseId") Long courseId,
            @RequestHeader(value = "X-User-Role", required = false) String currentUserRole,
            @RequestHeader(value = "X-Internal-Token", required = false) String requestInternalToken) {
        // 课程当日新增报名属于教学统计数据，仅教师/管理员或内部服务可访问
        if (!hasTeacherManageRole(currentUserRole) && !hasValidInternalToken(requestInternalToken)) {
            return Result.failure(403, "权限不足，仅教师、管理员或内部服务可查看当日报名统计");
        }

        int count = enrollmentService.countTodayEnrollments(courseId);
        return Result.success(count);
    }

    /**
     * 获取教师所有课程的今日新增学生总数。
     */
    @GetMapping("/teacher/{teacherId}/today")
    public Result<Integer> getTeacherTodayEnrollments(
            @PathVariable("teacherId") Long teacherId,
            @RequestHeader(value = "X-User-Id", required = false) String currentUserIdHeader,
            @RequestHeader(value = "X-User-Role", required = false) String currentUserRole) {
        // 教师统计仅允许教师本人或管理员查看
        Long currentUserId = parseUserId(currentUserIdHeader);
        if (!canAccessTeacherData(teacherId, currentUserId, currentUserRole)) {
            return Result.failure(403, "权限不足，仅教师本人或管理员可查看教师报名统计");
        }

        int count = enrollmentService.countTeacherTodayEnrollments(teacherId);
        return Result.success(count);
    }

    /**
     * 获取教师所有课程的学生列表（聚合数据，支持分页）。
     */
    @GetMapping("/teacher/{teacherId}/students")
    public Result<Map<String, Object>> getTeacherStudents(
            @PathVariable("teacherId") Long teacherId,
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "size", defaultValue = "20") int size,
            @RequestHeader(value = "X-User-Id", required = false) String currentUserIdHeader,
            @RequestHeader(value = "X-User-Role", required = false) String currentUserRole) {
        // 教师学生列表仅允许教师本人或管理员查看
        Long currentUserId = parseUserId(currentUserIdHeader);
        if (!canAccessTeacherData(teacherId, currentUserId, currentUserRole)) {
            return Result.failure(403, "权限不足，仅教师本人或管理员可查看教师学生列表");
        }

        Map<String, Object> result = enrollmentService.getTeacherStudents(teacherId, page, size);
        return Result.success(result);
    }

    /**
     * 获取教师所有课程的学生概览（按课程分组）。
     */
    @GetMapping("/teacher/{teacherId}/students/overview")
    public Result<Map<String, Object>> getTeacherStudentsOverview(
            @PathVariable("teacherId") Long teacherId,
            @RequestHeader(value = "X-User-Id", required = false) String currentUserIdHeader,
            @RequestHeader(value = "X-User-Role", required = false) String currentUserRole) {
        // 教师学生概览仅允许教师本人或管理员查看
        Long currentUserId = parseUserId(currentUserIdHeader);
        if (!canAccessTeacherData(teacherId, currentUserId, currentUserRole)) {
            return Result.failure(403, "权限不足，仅教师本人或管理员可查看教师学生概览");
        }

        Map<String, Object> result = enrollmentService.getTeacherStudentsOverview(teacherId);
        return Result.success(result);
    }

    /**
     * 获取课程学生列表（含学情状态）。
     */
    @GetMapping("/course/{courseId}/students")
    public Result<Map<String, Object>> getCourseStudentsWithStatus(
            @PathVariable("courseId") Long courseId,
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "size", defaultValue = "20") int size,
            @RequestParam(name = "status", defaultValue = "all") String status,
            @RequestHeader(value = "X-User-Role", required = false) String currentUserRole,
            @RequestHeader(value = "X-Internal-Token", required = false) String requestInternalToken) {
        // 课程学生列表包含学习状态，仅教师/管理员或内部服务可访问
        if (!hasTeacherManageRole(currentUserRole) && !hasValidInternalToken(requestInternalToken)) {
            return Result.failure(403, "权限不足，仅教师、管理员或内部服务可查看课程学生列表");
        }

        Map<String, Object> result = enrollmentService.getCourseStudentsWithStatus(courseId, page, size, status);
        return Result.success(result);
    }

    /**
     * 导出教师学生数据为 CSV。
     * 说明：写入 UTF-8 BOM，避免 Excel 打开乱码。
     */
    @GetMapping("/teacher/{teacherId}/students/export")
    public void exportTeacherStudents(
            @PathVariable("teacherId") Long teacherId,
            @RequestParam(name = "courseId", required = false) Long courseId,
            @RequestHeader(value = "X-User-Id", required = false) String currentUserIdHeader,
            @RequestHeader(value = "X-User-Role", required = false) String currentUserRole,
            jakarta.servlet.http.HttpServletResponse response) throws java.io.IOException {
        // 导出学生名单仅允许教师本人或管理员执行
        Long currentUserId = parseUserId(currentUserIdHeader);
        if (!canAccessTeacherData(teacherId, currentUserId, currentUserRole)) {
            response.setStatus(403);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":403,\"message\":\"权限不足，仅教师本人或管理员可导出学生数据\",\"data\":null}");
            return;
        }

        List<Map<String, Object>> students = enrollmentService.getTeacherStudentsForExport(teacherId, courseId);

        // 生成带时间戳的文件名
        String timestamp = java.time.LocalDateTime.now()
                .format(java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String filename = "students_export_" + timestamp + ".csv";

        response.setContentType("text/csv;charset=UTF-8");
        response.setHeader("Content-Disposition", "attachment; filename=\"" + filename + "\"");
        response.setCharacterEncoding("UTF-8");

        java.io.PrintWriter writer = response.getWriter();
        // 添加BOM以支持Excel正确显示中文
        writer.write('\ufeff');

        // CSV头
        writer.println("学生ID,学生姓名,邮箱,课程名称,报名时间,学习进度,学情状态,最后学习时间");

        // CSV数据
        for (Map<String, Object> student : students) {
            String enrolledAt = student.get("enrolledAt") != null ? student.get("enrolledAt").toString() : "";
            String lastStudyTime = student.get("lastStudyTime") != null ? student.get("lastStudyTime").toString() : "";

            writer.println(String.format("%s,%s,%s,%s,%s,%s%%,%s,%s",
                    student.get("studentId"),
                    escapeCsv(String.valueOf(student.get("name"))),
                    escapeCsv(String.valueOf(student.get("email"))),
                    escapeCsv(String.valueOf(student.get("courseName"))),
                    enrolledAt,
                    student.get("progress"),
                    student.get("learningStatus"),
                    lastStudyTime));
        }

        writer.flush();
    }

    /**
     * CSV字段转义。
     */
    private String escapeCsv(String value) {
        if (value == null || "null".equals(value)) {
            return "";
        }
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
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
     * 判断是否具备教师管理权限（教师或管理员）。
     */
    private boolean hasTeacherManageRole(String currentUserRole) {
        return currentUserRole != null
                && ("teacher".equalsIgnoreCase(currentUserRole) || "admin".equalsIgnoreCase(currentUserRole));
    }

    /**
     * 校验内部服务调用令牌。
     */
    private boolean hasValidInternalToken(String requestInternalToken) {
        return requestInternalToken != null && requestInternalToken.equals(internalToken);
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

    /**
     * 教师数据访问控制：管理员可跨账号访问，教师仅可访问本人数据。
     */
    private boolean canAccessTeacherData(Long targetTeacherId, Long currentUserId, String currentUserRole) {
        if (currentUserRole != null && "admin".equalsIgnoreCase(currentUserRole)) {
            return true;
        }
        return currentUserRole != null
                && "teacher".equalsIgnoreCase(currentUserRole)
                && currentUserId != null
                && currentUserId.equals(targetTeacherId);
    }
}
