package com.eduplatform.common.security;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * 请求上下文工具：统一解析网关注入的身份头与权限判断。
 *
 * <p>设计意图：消除各 Controller 中重复的 {@code parseUserId} / {@code isAdmin} /
 * {@code isTeacherOrAdmin} / {@code canAccessStudentData} / {@code canAccessTeacherData}
 * 等私有方法，统一身份解析口径。
 *
 * <p>数据源：网关在 {@code JwtAuthFilter} 中注入并签名的 {@code X-User-Id} / {@code X-User-Name}
 * / {@code X-User-Role}，业务侧由 {@code GatewayUserHeaderSignatureFilter} 二次校验签名，
 * 因此本类读取到的身份头可视为可信。
 */
@Component
public class RequestContext {

    private static final String HEADER_USER_ID = "X-User-Id";
    private static final String HEADER_USER_NAME = "X-User-Name";
    private static final String HEADER_USER_ROLE = "X-User-Role";
    private static final String HEADER_REAL_IP = "X-Real-IP";
    private static final String HEADER_FORWARDED_FOR = "X-Forwarded-For";

    /**
     * 获取当前请求对象；非 HTTP 上下文（如异步线程）返回 null。
     */
    public HttpServletRequest currentRequest() {
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        return attrs == null ? null : attrs.getRequest();
    }

    /**
     * 解析网关注入的用户 ID；缺失或非法返回 null。
     */
    public Long currentUserId() {
        return parseUserId(currentRequestHeader(HEADER_USER_ID));
    }

    /**
     * 解析网关注入的用户名；缺失返回 null。
     */
    public String currentUserName() {
        return currentRequestHeader(HEADER_USER_NAME);
    }

    /**
     * 解析网关注入的角色；缺失返回 null。
     */
    public String currentRole() {
        return currentRequestHeader(HEADER_USER_ROLE);
    }

    /**
     * 解析来源 IP，优先网关注入的 X-Real-IP，其次 X-Forwarded-For 首段，最后回退到 remoteAddr。
     */
    public String currentIp() {
        HttpServletRequest request = currentRequest();
        if (request == null) {
            return "unknown";
        }
        String xRealIp = request.getHeader(HEADER_REAL_IP);
        if (StringUtils.hasText(xRealIp)) {
            return xRealIp.trim();
        }
        String xff = request.getHeader(HEADER_FORWARDED_FOR);
        if (StringUtils.hasText(xff)) {
            String first = xff.split(",")[0].trim();
            if (StringUtils.hasText(first)) {
                return first;
            }
        }
        String remoteAddr = request.getRemoteAddr();
        return StringUtils.hasText(remoteAddr) ? remoteAddr : "unknown";
    }

    /**
     * 当前角色是否为管理员（大小写不敏感）。
     */
    public boolean isAdmin() {
        return hasRole("admin");
    }

    /**
     * 当前角色是否为教师（大小写不敏感）。
     */
    public boolean isTeacher() {
        return hasRole("teacher");
    }

    /**
     * 当前角色是否为学生（大小写不敏感）。
     */
    public boolean isStudent() {
        return hasRole("student");
    }

    /**
     * 当前角色是否为教师或管理员。
     */
    public boolean isTeacherOrAdmin() {
        return isTeacher() || isAdmin();
    }

    /**
     * 学生维度访问控制：学生仅可访问本人，教师和管理员可用于教学管理查询。
     */
    public boolean canAccessStudentData(Long targetStudentId) {
        if (isTeacherOrAdmin()) {
            return true;
        }
        Long current = currentUserId();
        return current != null && current.equals(targetStudentId);
    }

    /**
     * 教师维度访问控制：管理员可跨账号访问，教师仅可访问本人数据。
     */
    public boolean canAccessTeacherData(Long targetTeacherId) {
        if (isAdmin()) {
            return true;
        }
        Long current = currentUserId();
        return isTeacher() && current != null && current.equals(targetTeacherId);
    }

    /**
     * 本人或管理员访问控制：管理员可跨账号访问，否则必须与目标用户 ID 一致。
     */
    public boolean canAccessSelfOrAdmin(Long targetUserId) {
        if (isAdmin()) {
            return true;
        }
        Long current = currentUserId();
        return current != null && current.equals(targetUserId);
    }

    /**
     * 工具方法：解析字符串形式的用户 ID，非法值返回 null。
     */
    public Long parseUserId(String userIdHeader) {
        if (userIdHeader == null || userIdHeader.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(userIdHeader);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private boolean hasRole(String expected) {
        String role = currentRole();
        return role != null && expected.equalsIgnoreCase(role);
    }

    private String currentRequestHeader(String name) {
        HttpServletRequest request = currentRequest();
        if (request == null) {
            return null;
        }
        String value = request.getHeader(name);
        return StringUtils.hasText(value) ? value : null;
    }
}
