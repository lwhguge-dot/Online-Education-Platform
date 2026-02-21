package com.eduplatform.course.service;

import com.eduplatform.course.dto.AuditLogRequest;
import com.eduplatform.course.entity.Course;
import com.eduplatform.course.feign.AuditLogClient;
import com.eduplatform.course.mapper.CourseMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 课程工作流写模型服务。
 * 说明：集中处理课程状态机、审核流、批量状态变更与审计日志，降低 CourseService 的职责复杂度。
 */
@Service
@RequiredArgsConstructor
public class CourseWorkflowService {

    private final CourseMapper courseMapper;
    private final AuditLogClient auditLogClient;

    /**
     * 原子化更新课程状态。
     */
    public void updateStatus(Long id, String status) {
        Course course = courseMapper.selectById(id);
        if (course == null) {
            throw new RuntimeException("课程不存在");
        }
        // 执行状态归一化，兼容旧状态码输入
        String normalizedStatus = normalizeStatus(status);
        course.setStatus(normalizedStatus);
        course.setUpdatedAt(LocalDateTime.now());
        courseMapper.updateById(course);
    }

    /**
     * 带审计轨迹的状态更新。
     */
    public void updateStatusWithAudit(Long id, String status, Long operatorId, String operatorName, String ipAddress) {
        Course course = courseMapper.selectById(id);
        if (course == null) {
            throw new RuntimeException("课程不存在");
        }
        String normalizedStatus = normalizeStatus(status);
        course.setStatus(normalizedStatus);
        course.setUpdatedAt(LocalDateTime.now());
        courseMapper.updateById(course);

        String actionType = Course.STATUS_OFFLINE.equals(normalizedStatus) ? "COURSE_OFFLINE" : "COURSE_ONLINE";
        String details = Course.STATUS_OFFLINE.equals(normalizedStatus) ? "下架课程" : "上架课程";

        AuditLogRequest auditLog = buildAuditLogRequest(
                operatorId,
                operatorName,
                actionType,
                "COURSE",
                id,
                course.getTitle(),
                details,
                ipAddress);

        try {
            auditLogClient.createAuditLog(auditLog);
        } catch (Exception e) {
            // 审计失败不影响主流程
        }
    }

    /**
     * 教师提交课程审核。
     */
    public void submitReview(Long id) {
        Course course = courseMapper.selectById(id);
        if (course == null) {
            throw new RuntimeException("课程不存在");
        }
        if (!Course.STATUS_DRAFT.equals(course.getStatus()) && !Course.STATUS_REJECTED.equals(course.getStatus())) {
            throw new RuntimeException("当前课程状态不允许发起提审");
        }
        course.setStatus(Course.STATUS_REVIEWING);
        course.setSubmitTime(LocalDateTime.now());
        course.setUpdatedAt(LocalDateTime.now());
        courseMapper.updateById(course);
    }

    /**
     * 教师撤回审核申请。
     */
    public void withdrawReview(Long id) {
        Course course = courseMapper.selectById(id);
        if (course == null) {
            throw new RuntimeException("课程不存在");
        }
        if (!Course.STATUS_REVIEWING.equals(course.getStatus())) {
            throw new RuntimeException("非审核中状态，无法执行撤回操作");
        }
        course.setStatus(Course.STATUS_DRAFT);
        course.setUpdatedAt(LocalDateTime.now());
        courseMapper.updateById(course);
    }

    /**
     * 管理员审核课程。
     */
    public void auditCourse(Long id, String action, String remark, Long auditBy, String auditByName, String ipAddress) {
        Course course = courseMapper.selectById(id);
        if (course == null) {
            throw new RuntimeException("课程不存在");
        }
        if (!Course.STATUS_REVIEWING.equals(course.getStatus())) {
            throw new RuntimeException("课程未处于待审核状态");
        }

        String actionType;
        String details;
        if ("APPROVE".equals(action)) {
            course.setStatus(Course.STATUS_PUBLISHED);
            actionType = "COURSE_APPROVE";
            details = "审核通过课程" + (remark != null && !remark.isEmpty() ? "，备注：" + remark : "");
        } else if ("REJECT".equals(action)) {
            course.setStatus(Course.STATUS_REJECTED);
            actionType = "COURSE_REJECT";
            details = "驳回课程" + (remark != null && !remark.isEmpty() ? "，原因：" + remark : "");
        } else {
            throw new RuntimeException("由于无效的操作类型，审核请求被拒绝");
        }

        course.setAuditBy(auditBy);
        course.setAuditTime(LocalDateTime.now());
        course.setAuditRemark(remark);
        course.setUpdatedAt(LocalDateTime.now());
        courseMapper.updateById(course);

        if (auditBy != null && auditByName != null) {
            try {
                AuditLogRequest logData = buildAuditLogRequest(
                        auditBy,
                        auditByName,
                        actionType,
                        "COURSE",
                        id,
                        course.getTitle(),
                        details,
                        ipAddress);
                auditLogClient.createAuditLog(logData);
            } catch (Exception e) {
                // 审计失败不影响审核结果
            }
        }
    }

    /**
     * 内部审核流（系统调用）。
     */
    public void auditCourseInternal(Long id, String action, String remark, Long auditBy) {
        Course course = courseMapper.selectById(id);
        if (course == null) {
            throw new RuntimeException("课程记录缺失");
        }
        if (!Course.STATUS_REVIEWING.equals(course.getStatus())) {
            throw new RuntimeException("状态不符：无法执行内部审核");
        }

        if ("APPROVE".equals(action)) {
            course.setStatus(Course.STATUS_PUBLISHED);
        } else if ("REJECT".equals(action)) {
            course.setStatus(Course.STATUS_REJECTED);
        } else {
            throw new RuntimeException("无效的内部审核动作");
        }

        course.setAuditBy(auditBy);
        course.setAuditTime(LocalDateTime.now());
        course.setAuditRemark(remark);
        course.setUpdatedAt(LocalDateTime.now());
        courseMapper.updateById(course);
    }

    /**
     * 强制下线课程。
     */
    public void offlineCourse(Long id, Long operatorId, String operatorName, String ipAddress) {
        Course course = courseMapper.selectById(id);
        if (course == null) {
            throw new RuntimeException("课程记录不存在");
        }

        course.setStatus(Course.STATUS_OFFLINE);
        course.setUpdatedAt(LocalDateTime.now());
        courseMapper.updateById(course);

        if (operatorId != null && operatorName != null) {
            try {
                AuditLogRequest logData = buildAuditLogRequest(
                        operatorId,
                        operatorName,
                        "COURSE_OFFLINE",
                        "COURSE",
                        id,
                        course.getTitle(),
                        "执行强制下线操作",
                        ipAddress);
                auditLogClient.createAuditLog(logData);
            } catch (Exception e) {
                // 审计失败不影响下线主流程
            }
        }
    }

    /**
     * 批量更新课程状态。
     */
    public Map<String, Object> batchUpdateStatus(List<Long> courseIds, String status,
            Long operatorId, String operatorName, String ipAddress) {
        int successCount = 0;
        int failCount = 0;
        List<String> failedCourses = new java.util.ArrayList<>();

        for (Long courseId : courseIds) {
            try {
                Course course = courseMapper.selectById(courseId);
                if (course == null) {
                    failCount++;
                    failedCourses.add("课程 ID " + courseId + " 不存在");
                    continue;
                }

                if (!isValidStatusTransition(course.getStatus(), status)) {
                    failCount++;
                    failedCourses.add(course.getTitle() + ": 无法从 " + course.getStatus() + " 转换到 " + status);
                    continue;
                }

                course.setStatus(status);
                course.setUpdatedAt(LocalDateTime.now());
                courseMapper.updateById(course);

                if (operatorId != null && operatorName != null) {
                    try {
                        AuditLogRequest logData = buildAuditLogRequest(
                                operatorId,
                                operatorName,
                                "COURSE_BATCH_STATUS",
                                "COURSE",
                                courseId,
                                course.getTitle(),
                                "批量更新状态为: " + status,
                                ipAddress);
                        auditLogClient.createAuditLog(logData);
                    } catch (Exception e) {
                        // 审计失败不影响业务成功数
                    }
                }

                successCount++;
            } catch (Exception e) {
                failCount++;
                failedCourses.add("课程 ID " + courseId + ": 执行失败");
            }
        }

        Map<String, Object> result = new HashMap<>();
        result.put("successCount", successCount);
        result.put("failCount", failCount);
        result.put("total", courseIds.size());
        result.put("failedCourses", failedCourses);
        return result;
    }

    /**
     * 状态归一化转换器。
     */
    private String normalizeStatus(String status) {
        if (status == null) {
            return Course.STATUS_DRAFT;
        }
        switch (status) {
            case "0":
                return Course.STATUS_REVIEWING;
            case "1":
                return Course.STATUS_PUBLISHED;
            case "2":
                return Course.STATUS_OFFLINE;
            case "DRAFT":
            case "REVIEWING":
            case "PUBLISHED":
            case "OFFLINE":
            case "REJECTED":
            case "BANNED":
                return status;
            default:
                return Course.STATUS_DRAFT;
        }
    }

    /**
     * 构建审计日志请求体。
     */
    private AuditLogRequest buildAuditLogRequest(Long operatorId, String operatorName, String actionType,
            String targetType, Long targetId, String targetName, String details, String ipAddress) {
        AuditLogRequest request = new AuditLogRequest();
        request.setOperatorId(operatorId);
        request.setOperatorName(operatorName);
        request.setActionType(actionType);
        request.setTargetType(targetType);
        request.setTargetId(targetId);
        request.setTargetName(targetName);
        request.setDetails(details);
        request.setIpAddress(ipAddress != null ? ipAddress : "unknown");
        return request;
    }

    /**
     * 验证状态转换路径是否合法。
     */
    private boolean isValidStatusTransition(String currentStatus, String targetStatus) {
        if (Course.STATUS_PUBLISHED.equals(targetStatus)) {
            return Course.STATUS_REVIEWING.equals(currentStatus);
        }
        if (Course.STATUS_OFFLINE.equals(targetStatus)) {
            return Course.STATUS_PUBLISHED.equals(currentStatus);
        }
        if (Course.STATUS_DRAFT.equals(targetStatus)) {
            return Course.STATUS_REVIEWING.equals(currentStatus)
                    || Course.STATUS_REJECTED.equals(currentStatus);
        }
        return false;
    }
}
