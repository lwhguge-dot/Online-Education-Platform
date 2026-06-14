package com.eduplatform.course.service;

import com.eduplatform.common.exception.BusinessException;
import com.eduplatform.course.dto.AuditLogRequest;
import com.eduplatform.course.entity.Course;
import com.eduplatform.course.feign.AuditLogClient;
import com.eduplatform.course.mapper.CourseMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 课程工作流写模型服务。
 * 说明：集中处理课程状态机、审核流、批量状态变更与审计日志，降低 CourseService 的职责复杂度。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CourseWorkflowService {

    private final CourseMapper courseMapper;
    private final AuditLogClient auditLogClient;

    /**
     * 原子化更新课程状态。
     */
    @Transactional
    public void updateStatus(Long id, String status) {
        Course course = courseMapper.selectById(id);
        if (course == null) {
            throw new BusinessException("课程不存在");
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
    @Transactional
    public void updateStatusWithAudit(Long id, String status, Long operatorId, String operatorName, String ipAddress) {
        Course course = courseMapper.selectById(id);
        if (course == null) {
            throw new BusinessException("课程不存在");
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
            // 审计失败不影响主流程，但必须记录以便排查审计丢失
            log.warn("审计日志投递失败: action={}, courseId={}", actionType, id, e);
        }
    }

    /**
     * 教师提交课程审核。
     */
    @Transactional
    public void submitReview(Long id) {
        Course course = courseMapper.selectById(id);
        if (course == null) {
            throw new BusinessException("课程不存在");
        }
        if (!Course.STATUS_DRAFT.equals(course.getStatus()) && !Course.STATUS_REJECTED.equals(course.getStatus())) {
            throw new BusinessException("当前课程状态不允许发起提审");
        }
        course.setStatus(Course.STATUS_REVIEWING);
        course.setSubmitTime(LocalDateTime.now());
        course.setUpdatedAt(LocalDateTime.now());
        courseMapper.updateById(course);
    }

    /**
     * 教师撤回审核申请。
     */
    @Transactional
    public void withdrawReview(Long id) {
        Course course = courseMapper.selectById(id);
        if (course == null) {
            throw new BusinessException("课程不存在");
        }
        if (!Course.STATUS_REVIEWING.equals(course.getStatus())) {
            throw new BusinessException("非审核中状态，无法执行撤回操作");
        }
        course.setStatus(Course.STATUS_DRAFT);
        course.setUpdatedAt(LocalDateTime.now());
        courseMapper.updateById(course);
    }

    /**
     * 管理员审核课程。
     */
    @Transactional
    public void auditCourse(Long id, String action, String remark, Long auditBy, String auditByName, String ipAddress) {
        Course course = courseMapper.selectById(id);
        if (course == null) {
            throw new BusinessException("课程不存在");
        }
        if (!Course.STATUS_REVIEWING.equals(course.getStatus())) {
            throw new BusinessException("课程未处于待审核状态");
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
            throw new BusinessException("由于无效的操作类型，审核请求被拒绝");
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
                log.warn("审核审计日志投递失败: action={}, courseId={}", actionType, id, e);
            }
        }
    }

    /**
     * 内部审核流（系统调用）。
     */
    public void auditCourseInternal(Long id, String action, String remark, Long auditBy) {
        Course course = courseMapper.selectById(id);
        if (course == null) {
            throw new BusinessException("课程记录缺失");
        }
        if (!Course.STATUS_REVIEWING.equals(course.getStatus())) {
            throw new BusinessException("状态不符：无法执行内部审核");
        }

        if ("APPROVE".equals(action)) {
            course.setStatus(Course.STATUS_PUBLISHED);
        } else if ("REJECT".equals(action)) {
            course.setStatus(Course.STATUS_REJECTED);
        } else {
            throw new BusinessException("无效的内部审核动作");
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
    @Transactional
    public void offlineCourse(Long id, Long operatorId, String operatorName, String ipAddress) {
        Course course = courseMapper.selectById(id);
        if (course == null) {
            throw new BusinessException("课程记录不存在");
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
                log.warn("下线审计日志投递失败: courseId={}", id, e);
            }
        }
    }

    /**
     * 批量更新课程状态。
     */
    @Transactional
    public Map<String, Object> batchUpdateStatus(List<Long> courseIds, String status,
            Long operatorId, String operatorName, String ipAddress) {
        int successCount = 0;
        int failCount = 0;
        List<String> failedCourses = new java.util.ArrayList<>();

        List<Course> existingCourses = courseIds.isEmpty() ? java.util.Collections.emptyList()
                : courseMapper.selectBatchIds(courseIds);
        java.util.Map<Long, Course> courseMap = existingCourses.stream()
                .collect(Collectors.toMap(Course::getId, c -> c, (a, b) -> a));

        for (Long courseId : courseIds) {
            try {
                Course course = courseMap.get(courseId);
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
                        log.warn("批量状态审计投递失败: courseId={}", courseId, e);
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
            case "3":
                return Course.STATUS_BANNED;
            case "DRAFT":
            case "REVIEWING":
            case "PUBLISHED":
            case "OFFLINE":
            case "REJECTED":
            case "BANNED":
                return status;
            default:
                throw new BusinessException("不支持的状态值: " + status);
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
            // 允许从审核中 或 下架状态 转为已发布（重新上架）
            return Course.STATUS_REVIEWING.equals(currentStatus) || Course.STATUS_OFFLINE.equals(currentStatus);
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
