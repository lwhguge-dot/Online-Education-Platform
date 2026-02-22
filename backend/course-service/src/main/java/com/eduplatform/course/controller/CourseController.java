package com.eduplatform.course.controller;

import com.eduplatform.common.result.Result;
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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * 课程控制器。
 * 设计意图：统一课程管理入口，控制层只接收 DTO 并输出 VO，避免实体直出。
 *
 * @author Antigravity
 */
@RestController
@RequestMapping("/api/courses")
@RequiredArgsConstructor
@Slf4j
public class CourseController {

    @Value("${security.internal-token}")
    private String internalToken;

    private final CourseService courseService;
    private final CourseCascadeDeleteService courseCascadeDeleteService;

    /**
     * 获取所有课程列表。
     * 业务原因：后台需要统一筛选入口，避免多端重复拼接查询条件。
     *
     * @param subject 学科分类（可选）
     * @param status  课程状态（可选）
     * @return 课程视图对象列表
     */
    @GetMapping
    public Result<List<CourseVO>> getAllCourses(
            @RequestParam(name = "subject", required = false) String subject,
            @RequestParam(name = "status", required = false) String status,
            @RequestHeader(value = "X-User-Id", required = false) String currentUserIdHeader,
            @RequestHeader(value = "X-User-Role", required = false) String currentUserRole) {
        // 课程列表按角色收口：草稿仅教师本人可见，管理员默认不看草稿，学生仅看已发布
        List<Course> courses;
        if (isAdminRole(currentUserRole)) {
            courses = courseService.getAdminVisibleCourses(subject, status);
        } else if ("teacher".equalsIgnoreCase(currentUserRole)) {
            Long currentUserId = parseUserId(currentUserIdHeader);
            if (currentUserId == null) {
                return Result.failure(403, "权限不足，无法识别当前教师身份");
            }
            courses = courseService.getTeacherCourses(currentUserId, subject, status);
        } else {
            // 学生及其他角色仅可查询已发布课程
            courses = courseService.getPublishedCourses(subject);
        }
        return Result.success(courseService.convertToVOList(courses));
    }

    /**
     * 获取已发布的课程列表。
     * 业务原因：前台只展示已发布课程，避免在前端二次过滤。
     *
     * @param subject 学科分类（可选）
     * @return 已发布课程列表
     */
    @GetMapping("/published")
    public Result<List<CourseVO>> getPublishedCourses(
            @RequestParam(name = "subject", required = false) String subject) {
        List<Course> courses = courseService.getPublishedCourses(subject);
        return Result.success(courseService.convertToVOList(courses));
    }

    /**
     * 获取课程统计数据。
     * 业务原因：为管理后台统计面板提供统一数据口径。
     *
     * @return 统计数据映射
     */
    @GetMapping("/stats")
    public Result<Map<String, Object>> getStats() {
        return Result.success(courseService.getCourseStatistics());
    }

    /**
     * 获取按学科分组的课程分布统计。
     * 业务原因：为管理员仪表盘的课程分布饼图提供数据支撑。
     *
     * @return 包含学科列表、课程数及学生数的统计数据
     */
    @GetMapping("/stats/by-subject")
    public Result<Map<String, Object>> getStatsBySubject() {
        return Result.success(courseService.getCourseStatsBySubject());
    }

    /**
     * 根据ID获取课程详情。
     *
     * @param id 课程ID
     * @return 课程视图对象
     */
    @GetMapping("/{id}")
    public Result<CourseVO> getById(@PathVariable("id") Long id) {
        Course course = courseService.getById(id);
        if (course != null) {
            return Result.success(courseService.convertToVO(course));
        }
        return Result.error("课程不存在");
    }

    /**
     * 创建新课程。
     * 业务原因：创建动作需要统一写入审核状态与初始统计字段。
     *
     * @param courseDTO 课程数据
     * @return 成功消息
     */
    @PostMapping
    public Result<String> createCourse(
            @Valid @RequestBody CourseDTO courseDTO,
            @RequestHeader(value = "X-User-Id", required = false) String currentUserIdHeader,
            @RequestHeader(value = "X-User-Role", required = false) String currentUserRole) {
        // 创建课程仅允许教师或管理员，且教师只能以本人身份创建
        if (!hasTeacherManageRole(currentUserRole)) {
            return Result.failure(403, "权限不足，仅教师或管理员可创建课程");
        }

        Long currentUserId = parseUserId(currentUserIdHeader);
        if (!isAdminRole(currentUserRole)) {
            if (currentUserId == null) {
                return Result.failure(403, "权限不足，无法识别当前教师身份");
            }
            // 非管理员强制绑定当前教师ID，避免伪造 teacherId
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

    /**
     * 更新课程信息。
     * 业务原因：课程信息涉及多处展示，统一在服务层完成校验与更新。
     *
     * @param id        课程ID
     * @param updateRequest 课程更新数据
     * @return 成功消息
     */
    @PutMapping("/{id}")
    public Result<String> updateCourse(
            @PathVariable("id") Long id,
            @Valid @RequestBody CourseUpdateRequest updateRequest,
            @RequestHeader(value = "X-User-Id", required = false) String currentUserIdHeader,
            @RequestHeader(value = "X-User-Role", required = false) String currentUserRole) {
        // 更新课程仅允许教师或管理员，教师仅可操作本人课程
        if (!hasTeacherManageRole(currentUserRole)) {
            return Result.failure(403, "权限不足，仅教师或管理员可更新课程");
        }

        CourseDTO courseDTO = toCourseDTO(updateRequest);
        Long currentUserId = parseUserId(currentUserIdHeader);
        if (!isAdminRole(currentUserRole)) {
            if (!canManageCourse(id, currentUserId, currentUserRole)) {
                return Result.failure(403, "权限不足，仅课程所属教师可更新该课程");
            }
            // 非管理员强制使用当前教师身份，避免伪造 teacherId
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

    /**
     * 更新课程状态。
     * 说明：支持管理员操作审计与普通更新两种路径。
     */
    @PutMapping("/{id}/status")
    public Result<String> updateStatus(
            @PathVariable("id") Long id,
            @Valid @RequestBody CourseStatusUpdateRequest body,
            @RequestHeader(value = "X-User-Id", required = false) String operatorIdStr,
            @RequestHeader(value = "X-User-Role", required = false) String currentUserRole,
            @RequestHeader(value = "X-User-Name", required = false) String operatorName,
            @RequestHeader(value = "X-Real-IP", required = false) String ipAddress) {
        // 通用状态更新属于管理操作，仅管理员可执行
        if (!isAdminRole(currentUserRole)) {
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

    /**
     * 提交课程审核。
     * 业务原因：教师发布前需完成审核流程。
     */
    @PostMapping("/{id}/submit-review")
    public Result<String> submitReview(
            @PathVariable("id") Long id,
            @RequestHeader(value = "X-User-Id", required = false) String currentUserIdHeader,
            @RequestHeader(value = "X-User-Role", required = false) String currentUserRole) {
        // 提交审核仅允许教师或管理员，教师仅可提交本人课程
        if (!hasTeacherManageRole(currentUserRole)) {
            return Result.failure(403, "权限不足，仅教师或管理员可提交课程审核");
        }

        Long currentUserId = parseUserId(currentUserIdHeader);
        if (!isAdminRole(currentUserRole) && !canManageCourse(id, currentUserId, currentUserRole)) {
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

    /**
     * 撤回课程审核。
     * 说明：避免错误提交导致课程误审。
     */
    @PostMapping("/{id}/withdraw-review")
    public Result<String> withdrawReview(
            @PathVariable("id") Long id,
            @RequestHeader(value = "X-User-Id", required = false) String currentUserIdHeader,
            @RequestHeader(value = "X-User-Role", required = false) String currentUserRole) {
        // 撤回审核仅允许教师或管理员，教师仅可撤回本人课程
        if (!hasTeacherManageRole(currentUserRole)) {
            return Result.failure(403, "权限不足，仅教师或管理员可撤回课程审核");
        }

        Long currentUserId = parseUserId(currentUserIdHeader);
        if (!isAdminRole(currentUserRole) && !canManageCourse(id, currentUserId, currentUserRole)) {
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

    /**
     * 管理员审核课程。
     * 说明：支持审核通过/驳回及备注原因。
     */
    @PostMapping("/{id}/audit")
    public Result<String> auditCourse(
            @PathVariable("id") Long id,
            @Valid @RequestBody CourseAuditRequest body,
            @RequestHeader(value = "X-User-Id", required = false) String operatorIdStr,
            @RequestHeader(value = "X-User-Role", required = false) String currentUserRole,
            @RequestHeader(value = "X-User-Name", required = false) String operatorName,
            @RequestHeader(value = "X-Real-IP", required = false) String ipAddress,
            @RequestHeader(value = "X-Internal-Token", required = false) String requestInternalToken) {
        // 审核操作仅允许管理员或内部服务
        if (!isAdminRole(currentUserRole) && !hasValidInternalToken(requestInternalToken)) {
            return Result.failure(403, "权限不足，仅管理员或内部服务可审核课程");
        }

        try {
            String action = body.getAction();
            String remark = body.getRemark() != null ? body.getRemark() : "";
            Long auditByFromBody = body.getAuditBy();

            // 优先使用网关注入身份，避免信任请求体中的 auditBy
            Long operatorId = parseUserId(operatorIdStr);
            Long auditBy = operatorId != null ? operatorId : auditByFromBody;
            String opName = operatorName != null ? operatorName : "admin";

            if (operatorId != null) {
                courseService.auditCourse(id, action, remark, operatorId, opName, ipAddress);
            } else {
                courseService.auditCourseInternal(id, action, remark, auditBy);
            }
            return Result.success("审核完成", null);
        } catch (Exception e) {
            log.error("审核课程失败: courseId={}", id, e);
            return Result.error("审核失败，请稍后重试");
        }
    }

    /**
     * 强制下线课程。
     * 说明：用于违规内容处理或临时下架。
     */
    @PostMapping("/{id}/offline")
    public Result<String> offlineCourse(
            @PathVariable("id") Long id,
            @RequestHeader(value = "X-User-Id", required = false) String operatorIdStr,
            @RequestHeader(value = "X-User-Role", required = false) String currentUserRole,
            @RequestHeader(value = "X-User-Name", required = false) String operatorName,
            @RequestHeader(value = "X-Real-IP", required = false) String ipAddress) {
        // 强制下线属于高风险治理动作，仅管理员可执行
        if (!isAdminRole(currentUserRole)) {
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

    /**
     * 获取指定教师的课程列表。
     *
     * @param teacherId 教师ID
     * @return 课程列表
     */
    @GetMapping("/teacher/{teacherId}")
    public Result<List<CourseVO>> getTeacherCourses(
            @PathVariable("teacherId") Long teacherId,
            @RequestParam(name = "subject", required = false) String subject,
            @RequestParam(name = "status", required = false) String status,
            @RequestHeader(value = "X-User-Id", required = false) String currentUserIdHeader,
            @RequestHeader(value = "X-User-Role", required = false) String currentUserRole) {
        // 教师课程列表仅允许教师本人或管理员访问
        Long currentUserId = parseUserId(currentUserIdHeader);
        if (!canAccessTeacherData(teacherId, currentUserId, currentUserRole)) {
            return Result.failure(403, "权限不足，仅教师本人或管理员可查看教师课程列表");
        }

        List<Course> courses = courseService.getTeacherCourses(teacherId, subject, status);
        return Result.success(courseService.convertToVOList(courses));
    }

    /**
     * 获取审核中的课程列表。
     *
     * @return 审核中课程列表
     */
    @GetMapping("/reviewing")
    public Result<List<CourseVO>> getReviewingCourses(
            @RequestHeader(value = "X-User-Role", required = false) String currentUserRole) {
        // 审核中列表为管理视图，仅管理员可查看
        if (!isAdminRole(currentUserRole)) {
            return Result.failure(403, "权限不足，仅管理员可查看审核中课程");
        }

        List<Course> courses = courseService.getReviewingCourses();
        return Result.success(courseService.convertToVOList(courses));
    }

    /**
     * 删除课程（级联）。
     * 业务原因：课程关联章节、资源等多表数据，需要统一清理。
     */
    @DeleteMapping("/{id}")
    public Result<Void> deleteCourse(
            @PathVariable("id") Long id,
            @RequestHeader(value = "X-User-Id", required = false) String currentUserIdHeader,
            @RequestHeader(value = "X-User-Role", required = false) String currentUserRole) {
        // 删除课程仅允许管理员或课程所属教师
        if (!hasTeacherManageRole(currentUserRole)) {
            return Result.failure(403, "权限不足，仅教师或管理员可删除课程");
        }

        Long currentUserId = parseUserId(currentUserIdHeader);
        if (!isAdminRole(currentUserRole) && !canManageCourse(id, currentUserId, currentUserRole)) {
            return Result.failure(403, "权限不足，仅课程所属教师可删除该课程");
        }

        try {
            // 使用级联删除服务，删除课程及其所有相关数据
            courseCascadeDeleteService.cascadeDeleteCourse(id);
            return Result.success("课程及相关数据已删除", null);
        } catch (Exception e) {
            log.error("删除课程失败: courseId={}", id, e);
            return Result.error("删除失败，请稍后重试");
        }
    }

    /**
     * 删除用户相关的课程数据（供用户服务调用）。
     */
    @DeleteMapping("/cascade/user/{userId}")
    public Result<Void> deleteUserRelatedData(
            @PathVariable("userId") Long userId,
            @RequestParam("role") String role,
            @RequestHeader(value = "X-Internal-Token", required = false) String requestInternalToken) {
        try {
            // 内部高危接口：仅允许服务间令牌调用
            if (!hasValidInternalToken(requestInternalToken)) {
                return Result.failure(403, "禁止外部访问内部级联接口");
            }
            courseCascadeDeleteService.deleteUserRelatedData(userId, role);
            return Result.success("用户相关课程数据已删除", null);
        } catch (Exception e) {
            // 安全要求：避免直接记录请求参数，减少日志注入风险。
            log.error("删除用户相关课程数据失败", e);
            return Result.error("删除失败，请稍后重试");
        }
    }

    /**
     * 导出课程数据为 CSV。
     * 说明：写入 UTF-8 BOM，避免 Excel 打开乱码。
     */
    @GetMapping("/export")
    public void exportCourses(
            @RequestParam(name = "format", defaultValue = "csv") String format,
            @RequestParam(name = "status", required = false) String status,
            @RequestHeader(value = "X-User-Role", required = false) String currentUserRole,
            HttpServletResponse response) throws IOException {
        // 导出课程数据仅允许管理员执行
        if (!isAdminRole(currentUserRole)) {
            response.setStatus(403);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":403,\"message\":\"权限不足，仅管理员可导出课程数据\",\"data\":null}");
            return;
        }

        List<Course> courses = courseService.getAdminVisibleCourses(null, status);

        // 生成带时间戳的文件名
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String filename = "courses_export_" + timestamp + ".csv";

        response.setContentType("text/csv;charset=UTF-8");
        response.setHeader("Content-Disposition", "attachment; filename=\"" + filename + "\"");
        response.setCharacterEncoding("UTF-8");

        PrintWriter writer = response.getWriter();
        // 添加BOM以支持Excel正确显示中文
        writer.write('\ufeff');

        // CSV头
        writer.println("ID,课程名称,学科,教师ID,状态,学生数,评分,创建时间");

        // CSV数据
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

    /**
     * CSV字段转义。
     */
    private String escapeCsv(String value) {
        if (value == null) {
            return "";
        }
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    /**
     * 批量更新课程状态。
     */
    @PostMapping("/batch-status")
    public Result<Map<String, Object>> batchUpdateStatus(
            @Valid @RequestBody CourseBatchStatusRequest body,
            @RequestHeader(value = "X-User-Id", required = false) String operatorIdStr,
            @RequestHeader(value = "X-User-Role", required = false) String currentUserRole,
            @RequestHeader(value = "X-User-Name", required = false) String operatorName,
            @RequestHeader(value = "X-Real-IP", required = false) String ipAddress) {
        // 批量状态变更仅允许管理员执行
        if (!isAdminRole(currentUserRole)) {
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

    /**
     * 复制课程。
     *
     * @param id   源课程ID
     * @param body 包含新标题和教师ID的请求体
     * @return 新课程视图对象
     */
    @PostMapping("/{id}/duplicate")
    public Result<CourseVO> duplicateCourse(
            @PathVariable("id") Long id,
            @Valid @RequestBody(required = false) DuplicateCourseRequest body,
            @RequestHeader(value = "X-User-Id", required = false) String currentUserIdHeader,
            @RequestHeader(value = "X-User-Role", required = false) String currentUserRole) {
        // 复制课程仅允许教师或管理员，教师仅可复制本人课程
        if (!hasTeacherManageRole(currentUserRole)) {
            return Result.failure(403, "权限不足，仅教师或管理员可复制课程");
        }

        Long currentUserId = parseUserId(currentUserIdHeader);
        if (!isAdminRole(currentUserRole) && !canManageCourse(id, currentUserId, currentUserRole)) {
            return Result.failure(403, "权限不足，仅课程所属教师可复制该课程");
        }

        try {
            String newTitle = body != null ? body.getTitle() : null;
            Long teacherId = null;
            if (isAdminRole(currentUserRole)) {
                teacherId = body != null ? body.getTeacherId() : null;
            } else {
                // 非管理员强制复制到当前教师名下，避免伪造 teacherId
                teacherId = currentUserId;
            }

            Course newCourse = courseService.duplicateCourse(id, newTitle, teacherId);
            return Result.success("课程复制成功", courseService.convertToVO(newCourse));
        } catch (Exception e) {
            log.error("复制课程失败: courseId={}", id, e);
            return Result.error("复制失败，请稍后重试");
        }
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
     * 课程管理权限校验：管理员可管理全部，教师仅可管理本人课程。
     */
    private boolean canManageCourse(Long courseId, Long currentUserId, String currentUserRole) {
        if (isAdminRole(currentUserRole)) {
            return true;
        }
        if (currentUserId == null || !"teacher".equalsIgnoreCase(currentUserRole)) {
            return false;
        }

        Course course = courseService.getById(courseId);
        return course != null && course.getTeacherId() != null && course.getTeacherId().equals(currentUserId);
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

    /**
     * 将更新请求转换为服务层沿用的 CourseDTO。
     */
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
