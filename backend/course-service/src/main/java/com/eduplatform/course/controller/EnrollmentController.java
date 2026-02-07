package com.eduplatform.course.controller;

import com.eduplatform.common.result.Result;
import com.eduplatform.course.entity.Enrollment;
import com.eduplatform.course.service.EnrollmentService;
import com.eduplatform.course.vo.EnrollmentVO;
import lombok.RequiredArgsConstructor;
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

    private final EnrollmentService enrollmentService;

    /**
     * 学生报名课程。
     * 业务原因：报名会初始化学习进度与统计数据。
     */
    @PostMapping("/enroll")
    public Result<EnrollmentVO> enroll(
            @RequestParam("studentId") Long studentId,
            @RequestParam("courseId") Long courseId) {
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
            @RequestParam("courseId") Long courseId) {
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
            @RequestParam("courseId") Long courseId) {
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
    public Result<List<EnrollmentVO>> getStudentEnrollments(@PathVariable("studentId") Long studentId) {
        List<Enrollment> enrollments = enrollmentService.getStudentEnrollments(studentId);
        return Result.success(enrollmentService.convertToVOList(enrollments));
    }

    /**
     * 获取课程的报名学生列表。
     */
    @GetMapping("/course/{courseId}")
    public Result<List<EnrollmentVO>> getCourseEnrollments(@PathVariable("courseId") Long courseId) {
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
            @RequestParam("progress") Integer progress) {
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
            @RequestParam(required = false) Long courseId) {
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
            @PathVariable("studentId") Long studentId) {
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
            @RequestParam("courseId") Long courseId) {
        Map<String, Object> result = enrollmentService.checkNewChapters(studentId, courseId);
        return Result.success(result);
    }

    /**
     * 获取课程选课学生人数。
     * 业务原因：供 user-service 通过 Feign 调用，计算公告目标受众人数。
     */
    @GetMapping("/course/{courseId}/count")
    public Result<Long> getCourseStudentCount(@PathVariable("courseId") Long courseId) {
        long count = enrollmentService.countByCourse(courseId);
        return Result.success(count);
    }

    /**
     * 获取课程今日新增学生数。
     */
    @GetMapping("/course/{courseId}/today")
    public Result<Integer> getTodayEnrollments(@PathVariable("courseId") Long courseId) {
        int count = enrollmentService.countTodayEnrollments(courseId);
        return Result.success(count);
    }

    /**
     * 获取教师所有课程的今日新增学生总数。
     */
    @GetMapping("/teacher/{teacherId}/today")
    public Result<Integer> getTeacherTodayEnrollments(@PathVariable("teacherId") Long teacherId) {
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
            @RequestParam(name = "size", defaultValue = "20") int size) {
        Map<String, Object> result = enrollmentService.getTeacherStudents(teacherId, page, size);
        return Result.success(result);
    }

    /**
     * 获取教师所有课程的学生概览（按课程分组）。
     */
    @GetMapping("/teacher/{teacherId}/students/overview")
    public Result<Map<String, Object>> getTeacherStudentsOverview(@PathVariable("teacherId") Long teacherId) {
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
            @RequestParam(name = "status", defaultValue = "all") String status) {
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
            jakarta.servlet.http.HttpServletResponse response) throws java.io.IOException {

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
}
