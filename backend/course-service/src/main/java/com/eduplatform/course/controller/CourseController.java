package com.eduplatform.course.controller;

import com.eduplatform.common.result.Result;
import com.eduplatform.common.exception.BusinessException;
import com.eduplatform.common.security.InternalTokenVerifier;
import com.eduplatform.common.security.RequestContext;
import com.eduplatform.course.dto.CourseAuditRequest;
import com.eduplatform.course.dto.CourseBatchStatusRequest;
import com.eduplatform.course.dto.CourseDTO;
import com.eduplatform.course.dto.CourseStatusUpdateRequest;
import com.eduplatform.course.dto.CourseUpdateRequest;
import com.eduplatform.course.dto.DuplicateCourseRequest;
import com.eduplatform.course.entity.Course;
import com.eduplatform.course.service.CourseCascadeDeleteService;
import com.eduplatform.course.service.CourseService;
import com.eduplatform.course.vo.CourseVO;
import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * 课程控制器。
 */
@RestController
@RequestMapping("/api/courses")
@RequiredArgsConstructor
@Slf4j
public class CourseController {

    private final CourseService courseService;
    private final CourseCascadeDeleteService courseCascadeDeleteService;
    private final InternalTokenVerifier internalTokenVerifier;
    private final RequestContext requestContext;

    /**
     * 获取所有课程列表。
     */
    @GetMapping
    public Result<List<CourseVO>> getAllCourses(
            @RequestParam(name = "subject", required = false) String subject,
            @RequestParam(name = "status", required = false) String status,
            @RequestParam(name = "keyword", required = false) String keyword,
            @RequestHeader(value = "X-User-Id", required = false) String currentUserIdHeader,
            @RequestHeader(value = "X-User-Role", required = false) String currentUserRole) {
        List<Course> courses;
        if ("admin".equalsIgnoreCase(currentUserRole)) {
            courses = courseService.getAdminVisibleCourses(subject, status, keyword);
        } else if ("teacher".equalsIgnoreCase(currentUserRole)) {
            Long currentUserId = parseUserId(currentUserIdHeader);
            if (currentUserId == null) {
                return Result.failure(403, "权限不足，无法识别当前教师身份");
            }
            courses = courseService.getTeacherCourses(currentUserId, subject, status);
        } else {
            courses = courseService.getPublishedCourses(subject);
        }
        return Result.success(courseService.convertToVOList(courses));
    }

    @GetMapping("/published")
    public Result<List<CourseVO>> getPublishedCourses(
            @RequestParam(name = "subject", required = false) String subject) {
        List<Course> courses = courseService.getPublishedCourses(subject);
        return Result.success(courseService.convertToVOList(courses));
    }

    @GetMapping("/stats")
    public Result<Map<String, Object>> getStats(
            @RequestHeader(value = "X-Internal-Token", required = false) String internalTokenHeader) {
        if (!requestContext.isTeacherOrAdmin() && !internalTokenVerifier.isValid(internalTokenHeader)) {
            return Result.failure(403, "权限不足，仅教师或管理员可查看课程统计");
        }
        return Result.success(courseService.getCourseStatistics());
    }

    @GetMapping("/stats/by-subject")
    public Result<Map<String, Object>> getStatsBySubject(
            @RequestHeader(value = "X-Internal-Token", required = false) String internalTokenHeader) {
        if (!requestContext.isTeacherOrAdmin() && !internalTokenVerifier.isValid(internalTokenHeader)) {
            return Result.failure(403, "权限不足，仅教师或管理员可查看学科统计");
        }
        return Result.success(courseService.getCourseStatsBySubject());
    }

    @GetMapping("/{id}")
    public Result<CourseVO> getById(@PathVariable("id") Long id) {
        Course course = courseService.getById(id);
        if (course != null) {
            return Result.success(courseService.convertToVO(course));
        }
        return Result.error("课程不存在");
    }

    @PostMapping
    public Result<String> createCourse(
            @Valid @RequestBody CourseDTO courseDTO,
            @RequestHeader(value = "X-User-Id", required = false) String currentUserIdHeader,
            @RequestHeader(value = "X-User-Role", required = false) String currentUserRole) {
        if (!requestContext.isTeacherOrAdmin()) {
            return Result.failure(403, "权限不足，仅教师或管理员可创建课程");
        }
        Long currentUserId = parseUserId(currentUserIdHeader);
        if (!"admin".equalsIgnoreCase(currentUserRole)) {
            if (currentUserId == null) {
                return Result.failure(403, "权限不足，无法识别当前教师身份");
            }
            courseDTO.setTeacherId(currentUserId);
        }

        try {
            courseService.createCourse(courseDTO);
            return Result.success("课程创建成功", null);
        } catch (Exception e) {
            log.error("创建课程失败: teacherId={}", courseDTO != null ? courseDTO.getTeacherId() : null, e);
            return Result.error("创建失败，请稍后重试");
        }
    }

    @PutMapping("/{id}")
    public Result<String> updateCourse(
            @PathVariable("id") Long id,
            @Valid @RequestBody CourseUpdateRequest updateRequest,
            @RequestHeader(value = "X-User-Id", required = false) String currentUserIdHeader,
            @RequestHeader(value = "X-User-Role", required = false) String currentUserRole) {
        if (!requestContext.isTeacherOrAdmin()) {
            return Result.failure(403, "权限不足，仅教师或管理员可更新课程");
        }
        CourseDTO courseDTO = toCourseDTO(updateRequest);
        Long currentUserId = parseUserId(currentUserIdHeader);
        if (!"admin".equalsIgnoreCase(currentUserRole)) {
            if (!canManageCourse(id, currentUserId, currentUserRole)) {
                return Result.failure(403, "权限不足，仅课程所属教师可更新该课程");
            }
            courseDTO.setTeacherId(currentUserId);
        }

        try {
            courseService.updateCourse(id, courseDTO);
            return Result.success("课程更新成功", null);
        } catch (Exception e) {
            log.error("更新课程失败: courseId={}", id, e);
            return Result.error("更新失败，请稍后重试");
        }
    }

    @PutMapping("/{id}/status")
    public Result<String> updateStatus(
            @PathVariable("id") Long id,
            @Valid @RequestBody CourseStatusUpdateRequest body,
            @RequestHeader(value = "X-User-Id", required = false) String operatorIdStr,
            @RequestHeader(value = "X-User-Name", required = false) String operatorName,
            @RequestHeader(value = "X-Real-IP", required = false) String ipAddress) {
        if (!requestContext.isAdmin()) {
            return Result.failure(403, "权限不足，仅管理员可更新课程状态");
        }
        try {
            String status = body.getStatus();
            Long operatorId = operatorIdStr != null ? Long.parseLong(operatorIdStr) : null;
            if (operatorId != null && operatorName != null) {
                courseService.updateStatusWithAudit(id, status, operatorId, operatorName, ipAddress);
            } else {
                courseService.updateStatus(id, status);
            }
            return Result.success("状态更新成功", null);
        } catch (Exception e) {
            log.error("更新课程状态失败: courseId={}", id, e);
            return Result.error("状态更新失败，请稍后重试");
        }
    }

    @PostMapping("/{id}/submit-review")
    public Result<String> submitReview(
            @PathVariable("id") Long id,
            @RequestHeader(value = "X-User-Id", required = false) String currentUserIdHeader,
            @RequestHeader(value = "X-User-Role", required = false) String currentUserRole) {
        if (!requestContext.isTeacherOrAdmin()) {
            return Result.failure(403, "权限不足，仅教师或管理员可操作");
        }
        Long currentUserId = parseUserId(currentUserIdHeader);
        if (!"admin".equalsIgnoreCase(currentUserRole) && !canManageCourse(id, currentUserId, currentUserRole)) {
            return Result.failure(403, "权限不足，仅课程所属教师可提交审核");
        }

        try {
            courseService.submitReview(id);
            return Result.success("已提交审核", null);
        } catch (Exception e) {
            log.error("提交课程审核失败: courseId={}", id, e);
            return Result.error("提交审核失败，请稍后重试");
        }
    }

    @PostMapping("/{id}/withdraw-review")
    public Result<String> withdrawReview(
            @PathVariable("id") Long id,
            @RequestHeader(value = "X-User-Id", required = false) String currentUserIdHeader,
            @RequestHeader(value = "X-User-Role", required = false) String currentUserRole) {
        if (!requestContext.isTeacherOrAdmin()) {
            return Result.failure(403, "权限不足，仅教师或管理员可操作");
        }
        Long currentUserId = parseUserId(currentUserIdHeader);
        if (!"admin".equalsIgnoreCase(currentUserRole) && !canManageCourse(id, currentUserId, currentUserRole)) {
            return Result.failure(403, "权限不足，仅课程所属教师可撤回审核");
        }

        try {
            courseService.withdrawReview(id);
            return Result.success("已撤回审核", null);
        } catch (Exception e) {
            log.error("撤回课程审核失败: courseId={}", id, e);
            return Result.error("撤回审核失败，请稍后重试");
        }
    }

    @PostMapping("/{id}/audit")
    public Result<String> auditCourse(
            @PathVariable("id") Long id,
            @Valid @RequestBody CourseAuditRequest body,
            @RequestHeader(value = "X-User-Id", required = false) String operatorIdStr,
            @RequestHeader(value = "X-User-Name", required = false) String operatorName,
            @RequestHeader(value = "X-Real-IP", required = false) String ipAddress) {
        if (!requestContext.isAdmin()) {
            return Result.failure(403, "权限不足，仅管理员可审核课程");
        }
        // 门禁语义：本接口面向管理员 UI 审核。
        // 注意：JwtAuthFilter 会剥离外部 X-Internal-Token 头，普通管理员无法注入该头，
        //       因此无需在此额外校验内部令牌；服务内部自动化审核走 auditCourseInternal。
        try {
            String action = body.getAction();
            String remark = body.getRemark() != null ? body.getRemark() : "";
            Long auditByFromBody = body.getAuditBy();

            Long operatorId = parseUserId(operatorIdStr);
            Long auditBy = operatorId != null ? operatorId : auditByFromBody;
            String opName = operatorName != null ? operatorName : "admin";

            if (operatorId != null) {
                courseService.auditCourse(id, action, remark, operatorId, opName, ipAddress);
            } else {
                courseService.auditCourseInternal(id, action, remark, auditBy);
            }
            return Result.success("审核完成", null);
        } catch (BusinessException e) {
            log.warn("审核课程业务异常: courseId={}, message={}", id, e.getMessage());
            return Result.failure(400, e.getMessage());
        } catch (Exception e) {
            log.error("审核课程失败: courseId={}", id, e);
            return Result.error("审核失败，请稍后重试");
        }
    }

    @PostMapping("/{id}/offline")
    public Result<String> offlineCourse(
            @PathVariable("id") Long id,
            @RequestHeader(value = "X-User-Id", required = false) String operatorIdStr,
            @RequestHeader(value = "X-User-Name", required = false) String operatorName,
            @RequestHeader(value = "X-Real-IP", required = false) String ipAddress) {
        if (!requestContext.isAdmin()) {
            return Result.failure(403, "权限不足，仅管理员可下线课程");
        }
        try {
            Long operatorId = parseUserId(operatorIdStr);
            courseService.offlineCourse(id, operatorId, operatorName, ipAddress);
            return Result.success("课程已下线", null);
        } catch (Exception e) {
            log.error("强制下线课程失败: courseId={}", id, e);
            return Result.error("下线失败，请稍后重试");
        }
    }

    @GetMapping("/teacher/{teacherId}")
    public Result<List<CourseVO>> getTeacherCourses(
            @PathVariable("teacherId") Long teacherId,
            @RequestParam(name = "subject", required = false) String subject,
            @RequestParam(name = "status", required = false) String status) {
        if (!requestContext.canAccessTeacherData(teacherId)) {
            return Result.failure(403, "权限不足，仅教师本人或管理员可查看教师课程");
        }
        List<Course> courses = courseService.getTeacherCourses(teacherId, subject, status);
        return Result.success(courseService.convertToVOList(courses));
    }

    @GetMapping("/reviewing")
    public Result<List<CourseVO>> getReviewingCourses() {
        if (!requestContext.isAdmin()) {
            return Result.failure(403, "权限不足，仅管理员可查看待审核课程");
        }
        List<Course> courses = courseService.getReviewingCourses();
        return Result.success(courseService.convertToVOList(courses));
    }

    @DeleteMapping("/{id}")
    public Result<Void> deleteCourse(
            @PathVariable("id") Long id,
            @RequestHeader(value = "X-User-Id", required = false) String currentUserIdHeader,
            @RequestHeader(value = "X-User-Role", required = false) String currentUserRole) {
        if (!requestContext.isTeacherOrAdmin()) {
            return Result.failure(403, "权限不足，仅教师或管理员可操作");
        }
        Long currentUserId = parseUserId(currentUserIdHeader);
        if (!"admin".equalsIgnoreCase(currentUserRole) && !canManageCourse(id, currentUserId, currentUserRole)) {
            return Result.failure(403, "权限不足，仅课程所属教师可删除该课程");
        }

        try {
            courseCascadeDeleteService.cascadeDeleteCourse(id);
            return Result.success("课程及相关数据已删除", null);
        } catch (Exception e) {
            log.error("删除课程失败: courseId={}", id, e);
            return Result.error("删除失败，请稍后重试");
        }
    }

    @DeleteMapping("/cascade/user/{userId}")
    public Result<Void> deleteUserRelatedData(
            @PathVariable("userId") Long userId,
            @RequestParam("role") String role,
            @RequestHeader(value = "X-Internal-Token", required = false) String requestInternalToken) {
        try {
            if (!hasValidInternalToken(requestInternalToken)) {
                return Result.failure(403, "禁止外部访问内部级联接口");
            }
            courseCascadeDeleteService.deleteUserRelatedData(userId, role);
            return Result.success("用户相关课程数据已删除", null);
        } catch (Exception e) {
            log.error("删除用户相关课程数据失败", e);
            return Result.error("删除失败，请稍后重试");
        }
    }

    @GetMapping("/export")
    public void exportCourses(
            @RequestParam(name = "format", defaultValue = "csv") String format,
            @RequestParam(name = "status", required = false) String status,
            HttpServletResponse response) throws IOException {
        if (!requestContext.isAdmin()) {
            response.setStatus(403);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":403,\"message\":\"权限不足，仅管理员可导出课程数据\",\"data\":null}");
            return;
        }
        List<Course> courses = courseService.getAdminVisibleCourses(null, status, null);

        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String filename = "courses_export_" + timestamp + ".csv";

        response.setContentType("text/csv;charset=UTF-8");
        response.setHeader("Content-Disposition", "attachment; filename=\"" + filename + "\"");
        response.setCharacterEncoding("UTF-8");

        PrintWriter writer = response.getWriter();
        writer.write('\ufeff');

        writer.println("ID,课程名称,学科,教师ID,状态,学生数,评分,创建时间");

        for (Course course : courses) {
            String createdAt = course.getCreatedAt() != null
                    ? course.getCreatedAt().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                    : "";

            writer.println(String.format("%d,%s,%s,%d,%s,%d,%.1f,%s",
                    course.getId(),
                    escapeCsv(course.getTitle()),
                    escapeCsv(course.getSubject()),
                    course.getTeacherId() != null ? course.getTeacherId() : 0,
                    course.getStatus(),
                    course.getStudentCount() != null ? course.getStudentCount() : 0,
                    course.getRating() != null ? course.getRating() : 0.0,
                    createdAt));
        }

        writer.flush();
    }

    @PostMapping("/batch-status")
    public Result<Map<String, Object>> batchUpdateStatus(
            @Valid @RequestBody CourseBatchStatusRequest body,
            @RequestHeader(value = "X-User-Id", required = false) String operatorIdStr,
            @RequestHeader(value = "X-User-Name", required = false) String operatorName,
            @RequestHeader(value = "X-Real-IP", required = false) String ipAddress) {
        if (!requestContext.isAdmin()) {
            return Result.failure(403, "权限不足，仅管理员可批量更新课程状态");
        }
        try {
            List<Long> courseIds = body.getCourseIds();
            String status = body.getStatus();
            Long operatorId = operatorIdStr != null ? Long.parseLong(operatorIdStr) : null;

            Map<String, Object> result = courseService.batchUpdateStatus(
                    courseIds, status, operatorId, operatorName, ipAddress);

            return Result.success("批量更新完成", result);
        } catch (Exception e) {
            log.error("批量更新课程状态失败: courseCount={}", body.getCourseIds() != null ? body.getCourseIds().size() : 0, e);
            return Result.error("批量更新失败，请稍后重试");
        }
    }

    @PostMapping("/{id}/duplicate")
    public Result<CourseVO> duplicateCourse(
            @PathVariable("id") Long id,
            @Valid @RequestBody(required = false) DuplicateCourseRequest body,
            @RequestHeader(value = "X-User-Id", required = false) String currentUserIdHeader,
            @RequestHeader(value = "X-User-Role", required = false) String currentUserRole) {
        if (!requestContext.isTeacherOrAdmin()) {
            return Result.failure(403, "权限不足，仅教师或管理员可操作");
        }
        Long currentUserId = parseUserId(currentUserIdHeader);
        if (!"admin".equalsIgnoreCase(currentUserRole) && !canManageCourse(id, currentUserId, currentUserRole)) {
            return Result.failure(403, "权限不足，仅课程所属教师可复制该课程");
        }

        try {
            String newTitle = body != null ? body.getTitle() : null;
            Long teacherId = null;
            if ("admin".equalsIgnoreCase(currentUserRole)) {
                teacherId = body != null ? body.getTeacherId() : null;
            } else {
                teacherId = currentUserId;
            }

            Course newCourse = courseService.duplicateCourse(id, newTitle, teacherId);
            return Result.success("课程复制成功", courseService.convertToVO(newCourse));
        } catch (Exception e) {
            log.error("复制课程失败: courseId={}", id, e);
            return Result.error("复制失败，请稍后重试");
        }
    }

    private String escapeCsv(String value) {
        if (value == null) {
            return "";
        }
        // 防止 CSV 注入：以 =, +, -, @, \t, \r 开头的单元格添加前缀
        if (value.matches("^[=+\\-@\\t\\r].*")) {
            value = "'" + value;
        }
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    private Long parseUserId(String currentUserIdHeader) {
        return requestContext.parseUserId(currentUserIdHeader);
    }

    private boolean hasValidInternalToken(String requestInternalToken) {
        return internalTokenVerifier.isValid(requestInternalToken);
    }

    private boolean canManageCourse(Long courseId, Long currentUserId, String currentUserRole) {
        if (requestContext.isAdmin()) {
            return true;
        }
        if (!requestContext.isTeacher()) {
            return false;
        }
        Course course = courseService.getById(courseId);
        Long teacherId = course == null ? null : course.getTeacherId();
        return teacherId != null && teacherId.equals(requestContext.currentUserId());
    }

    private CourseDTO toCourseDTO(CourseUpdateRequest updateRequest) {
        CourseDTO courseDTO = new CourseDTO();
        if (updateRequest == null) {
            return courseDTO;
        }
        courseDTO.setTitle(updateRequest.getTitle());
        courseDTO.setDescription(updateRequest.getDescription());
        courseDTO.setSubject(updateRequest.getSubject());
        courseDTO.setCoverImage(updateRequest.getCoverImage());
        courseDTO.setTeacherId(updateRequest.getTeacherId());
        return courseDTO;
    }
}
