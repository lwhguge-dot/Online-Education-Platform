package com.eduplatform.course.controller;

import com.eduplatform.common.result.Result;
import com.eduplatform.course.dto.CourseDTO;
import com.eduplatform.course.entity.Course;
import com.eduplatform.course.service.CourseCascadeDeleteService;
import com.eduplatform.course.service.CourseService;
import com.eduplatform.course.vo.CourseVO;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
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
public class CourseController {

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
            @RequestParam(name = "status", required = false) String status) {
        List<Course> courses = courseService.getAllCourses(subject, status);
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
    public Result<String> createCourse(@RequestBody CourseDTO courseDTO) {
        try {
            courseService.createCourse(courseDTO);
            return Result.success("课程创建成功", null);
        } catch (Exception e) {
            return Result.error("创建失败: " + e.getMessage());
        }
    }

    /**
     * 更新课程信息。
     * 业务原因：课程信息涉及多处展示，统一在服务层完成校验与更新。
     *
     * @param id        课程ID
     * @param courseDTO 课程数据
     * @return 成功消息
     */
    @PutMapping("/{id}")
    public Result<String> updateCourse(@PathVariable("id") Long id, @RequestBody CourseDTO courseDTO) {
        try {
            courseService.updateCourse(id, courseDTO);
            return Result.success("课程更新成功", null);
        } catch (Exception e) {
            return Result.error("更新失败: " + e.getMessage());
        }
    }

    /**
     * 更新课程状态。
     * 说明：支持管理员操作审计与普通更新两种路径。
     */
    @PutMapping("/{id}/status")
    public Result<String> updateStatus(
            @PathVariable("id") Long id,
            @RequestBody Map<String, Object> body,
            @RequestHeader(value = "X-User-Id", required = false) String operatorIdStr,
            @RequestHeader(value = "X-User-Name", required = false) String operatorName,
            @RequestHeader(value = "X-Real-IP", required = false) String ipAddress) {
        try {
            String status = body.get("status").toString();
            Long operatorId = operatorIdStr != null ? Long.parseLong(operatorIdStr) : null;
            if (operatorId != null && operatorName != null) {
                courseService.updateStatusWithAudit(id, status, operatorId, operatorName, ipAddress);
            } else {
                courseService.updateStatus(id, status);
            }
            return Result.success("状态更新成功", null);
        } catch (Exception e) {
            return Result.error("更新失败: " + e.getMessage());
        }
    }

    /**
     * 提交课程审核。
     * 业务原因：教师发布前需完成审核流程。
     */
    @PostMapping("/{id}/submit-review")
    public Result<String> submitReview(@PathVariable("id") Long id) {
        try {
            courseService.submitReview(id);
            return Result.success("已提交审核", null);
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 撤回课程审核。
     * 说明：避免错误提交导致课程误审。
     */
    @PostMapping("/{id}/withdraw-review")
    public Result<String> withdrawReview(@PathVariable("id") Long id) {
        try {
            courseService.withdrawReview(id);
            return Result.success("已撤回审核", null);
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 管理员审核课程。
     * 说明：支持审核通过/驳回及备注原因。
     */
    @PostMapping("/{id}/audit")
    public Result<String> auditCourse(
            @PathVariable("id") Long id,
            @RequestBody Map<String, Object> body,
            @RequestHeader(value = "X-User-Id", required = false) String operatorIdStr,
            @RequestHeader(value = "X-User-Name", required = false) String operatorName,
            @RequestHeader(value = "X-Real-IP", required = false) String ipAddress) {
        try {
            String action = body.get("action").toString();
            String remark = body.get("remark") != null ? body.get("remark").toString() : "";
            Long auditBy = body.get("auditBy") != null ? Long.parseLong(body.get("auditBy").toString()) : null;

            // 优先使用请求头中的操作人信息
            Long operatorId = operatorIdStr != null ? Long.parseLong(operatorIdStr) : auditBy;
            String opName = operatorName != null ? operatorName : "admin";

            if (operatorId != null) {
                courseService.auditCourse(id, action, remark, operatorId, opName, ipAddress);
            } else {
                courseService.auditCourseInternal(id, action, remark, auditBy);
            }
            return Result.success("审核完成", null);
        } catch (Exception e) {
            return Result.error(e.getMessage());
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
            @RequestHeader(value = "X-User-Name", required = false) String operatorName,
            @RequestHeader(value = "X-Real-IP", required = false) String ipAddress) {
        try {
            Long operatorId = operatorIdStr != null ? Long.parseLong(operatorIdStr) : null;
            courseService.offlineCourse(id, operatorId, operatorName, ipAddress);
            return Result.success("课程已下线", null);
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 获取指定教师的课程列表。
     *
     * @param teacherId 教师ID
     * @return 课程列表
     */
    @GetMapping("/teacher/{teacherId}")
    public Result<List<CourseVO>> getTeacherCourses(@PathVariable("teacherId") Long teacherId) {
        List<Course> courses = courseService.getTeacherCourses(teacherId);
        return Result.success(courseService.convertToVOList(courses));
    }

    /**
     * 获取审核中的课程列表。
     *
     * @return 审核中课程列表
     */
    @GetMapping("/reviewing")
    public Result<List<CourseVO>> getReviewingCourses() {
        List<Course> courses = courseService.getReviewingCourses();
        return Result.success(courseService.convertToVOList(courses));
    }

    /**
     * 删除课程（级联）。
     * 业务原因：课程关联章节、资源等多表数据，需要统一清理。
     */
    @DeleteMapping("/{id}")
    public Result<Void> deleteCourse(@PathVariable("id") Long id) {
        try {
            // 使用级联删除服务，删除课程及其所有相关数据
            courseCascadeDeleteService.cascadeDeleteCourse(id);
            return Result.success("课程及相关数据已删除", null);
        } catch (Exception e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 删除用户相关的课程数据（供用户服务调用）。
     */
    @DeleteMapping("/cascade/user/{userId}")
    public Result<Void> deleteUserRelatedData(
            @PathVariable("userId") Long userId,
            @RequestParam("role") String role) {
        try {
            courseCascadeDeleteService.deleteUserRelatedData(userId, role);
            return Result.success("用户相关课程数据已删除", null);
        } catch (Exception e) {
            return Result.error(e.getMessage());
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
            HttpServletResponse response) throws IOException {

        List<Course> courses = courseService.getAllCourses(null, status);

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
            @RequestBody Map<String, Object> body,
            @RequestHeader(value = "X-User-Id", required = false) String operatorIdStr,
            @RequestHeader(value = "X-User-Name", required = false) String operatorName,
            @RequestHeader(value = "X-Real-IP", required = false) String ipAddress) {
        try {
            @SuppressWarnings("unchecked")
            List<Number> courseIdNumbers = (List<Number>) body.get("courseIds");
            List<Long> courseIds = courseIdNumbers.stream()
                    .map(Number::longValue)
                    .toList();
            String status = (String) body.get("status");

            if (courseIds == null || courseIds.isEmpty()) {
                return Result.error("课程ID列表不能为空");
            }
            if (status == null || status.isEmpty()) {
                return Result.error("目标状态不能为空");
            }

            Long operatorId = operatorIdStr != null ? Long.parseLong(operatorIdStr) : null;

            Map<String, Object> result = courseService.batchUpdateStatus(
                    courseIds, status, operatorId, operatorName, ipAddress);

            return Result.success("批量更新完成", result);
        } catch (Exception e) {
            return Result.error("批量更新失败: " + e.getMessage());
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
            @RequestBody(required = false) Map<String, Object> body) {
        try {
            String newTitle = body != null ? (String) body.get("title") : null;
            Long teacherId = body != null && body.get("teacherId") != null
                    ? Long.parseLong(body.get("teacherId").toString())
                    : null;

            Course newCourse = courseService.duplicateCourse(id, newTitle, teacherId);
            return Result.success("课程复制成功", courseService.convertToVO(newCourse));
        } catch (Exception e) {
            return Result.error("复制失败: " + e.getMessage());
        }
    }
}
