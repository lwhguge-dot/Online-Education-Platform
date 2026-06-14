package com.eduplatform.user.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.eduplatform.common.exception.BusinessException;
import com.eduplatform.common.result.Result;
import com.eduplatform.common.security.InternalTokenVerifier;
import com.eduplatform.common.security.RequestContext;
import com.eduplatform.user.dto.UserProfileDTO;
import com.eduplatform.user.dto.UserStatusUpdateRequest;
import com.eduplatform.user.dto.UserSettingsDTO;
import com.eduplatform.user.entity.User;
import com.eduplatform.user.service.StudentProfileService;
import com.eduplatform.user.service.TeacherProfileService;
import com.eduplatform.user.service.UserCascadeDeleteService;
import com.eduplatform.user.service.UserService;
import com.eduplatform.user.service.UserSessionService;
import com.eduplatform.user.vo.UserBriefVO;
import com.eduplatform.user.vo.UserVO;
import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.PrintWriter;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 用户控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final StudentProfileService studentProfileService;
    private final TeacherProfileService teacherProfileService;
    private final UserSessionService sessionService;
    private final UserCascadeDeleteService userCascadeDeleteService;
    private final InternalTokenVerifier internalTokenVerifier;
    private final RequestContext requestContext;

    @GetMapping("/list")
    public Result<Map<String, Object>> getList(
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "limit", defaultValue = "10") int limit,
            @RequestParam(name = "role", required = false) String role,
            @RequestParam(name = "status", required = false) Integer status,
            @RequestParam(name = "keyword", required = false) String keyword) {

        if (!requestContext.isAdmin()) {
            return Result.failure(403, "权限不足，仅管理员可查看用户列表");
        }

        Page<User> pageResult = userService.getList(page, limit, role, status, keyword);
        List<UserVO> userVOs = userService.convertToVOList(pageResult.getRecords());

        Map<String, Object> data = new HashMap<>();
        data.put("list", userVOs);

        Map<String, Object> pagination = new HashMap<>();
        pagination.put("total", pageResult.getTotal());
        pagination.put("page", page);
        pagination.put("limit", limit);
        pagination.put("pages", (int) Math.ceil((double) pageResult.getTotal() / limit));
        data.put("pagination", pagination);

        return Result.success(data);
    }

    @GetMapping("/stats")
    public Result<Map<String, Object>> getStats() {
        if (!requestContext.isAdmin()) {
            return Result.failure(403, "权限不足，仅管理员可查看用户统计");
        }
        Map<String, Object> stats = new HashMap<>();
        stats.put("total", userService.countTotal());
        stats.put("students", userService.countByRole("student"));
        stats.put("teachers", userService.countByRole("teacher"));
        stats.put("admins", userService.countByRole("admin"));

        java.util.List<User> recentUsers = userService.getRecentLoginUsers(20);
        stats.put("recentUsers", userService.convertToVOList(recentUsers));

        java.util.List<User> allUsers = userService.getSimpleList(null);
        stats.put("allUsers", userService.convertToVOList(allUsers));

        return Result.success(stats);
    }

    @GetMapping("/{id}")
    public Result<UserVO> getById(@PathVariable("id") Long id) {
        // 权限校验：仅本人或管理员可查看用户资料。
        // 注：原 @PreAuthorize 在虚拟线程下因 SecurityContext 跨线程丢失而失效，
        // 改为 Controller 内部使用 RequestContext 显式校验。
        if (!requestContext.canAccessSelfOrAdmin(id)) {
            return Result.failure(403, "权限不足，仅本人或管理员可查看用户资料");
        }
        User user = userService.getById(id);
        if (user != null) {
            return Result.success(userService.convertToVO(user));
        }
        throw new BusinessException(404, "用户不存在");
    }

    /**
     * 批量获取用户信息（仅限内部服务调用）
     * 业务原因：供 course-service 等外部服务通过 Feign 获取用户信息
     */
    @PostMapping("/batch")
    public Result<List<UserBriefVO>> getUsersByIds(
            @Valid @RequestBody List<Long> ids,
            @RequestHeader(value = "X-Internal-Token", required = false) String internalToken) {
        if (!hasValidInternalToken(internalToken)) {
            return Result.failure(403, "禁止外部访问批量查询接口");
        }
        if (ids == null || ids.isEmpty()) {
            return Result.success(Collections.emptyList());
        }
        if (ids.size() > 1000) {
            return Result.failure(400, "ids数量不能超过1000");
        }
        if (ids.stream().anyMatch(id -> id == null || id <= 0)) {
            return Result.failure(400, "ids中存在非法用户ID");
        }

        List<User> users = userService.getByIds(ids);
        List<UserBriefVO> voList = users.stream().map(user -> {
            UserBriefVO vo = new UserBriefVO();
            vo.setId(user.getId());
            vo.setUsername(user.getUsername());
            vo.setName(user.getName());
            vo.setEmail(user.getEmail());
            return vo;
        }).collect(Collectors.toList());

        return Result.success(voList);
    }

    @PutMapping("/{id}/status")
    public Result<String> updateStatus(
            @PathVariable("id") Long id,
            @Valid @RequestBody UserStatusUpdateRequest body,
            @RequestHeader(value = "X-User-Id", required = false) String operatorIdStr,
            @RequestHeader(value = "X-User-Name", required = false) String operatorName,
            @RequestHeader(value = "X-Real-IP", required = false) String ipAddress) {

        if (!requestContext.isAdmin()) {
            return Result.failure(403, "权限不足，仅管理员可更新用户状态");
        }

        Integer status = body.getStatus();

        Long operatorId = parseUserId(operatorIdStr);
        if (operatorId == null || operatorName == null) {
            return Result.failure(401, "身份认证失败");
        }

        userService.updateStatus(id, status, operatorId, operatorName,
                ipAddress != null ? ipAddress : "unknown");
        return Result.success("状态更新成功", null);
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(
            @PathVariable("id") Long id,
            @RequestHeader(value = "X-User-Id", required = false) String operatorIdStr,
            @RequestHeader(value = "X-User-Name", required = false) String operatorName,
            @RequestHeader(value = "X-Real-IP", required = false) String ipAddress) {

        if (!requestContext.isAdmin()) {
            return Result.failure(403, "权限不足，仅管理员可删除用户");
        }

        Long operatorId = parseUserId(operatorIdStr);
        if (operatorId == null || operatorName == null) {
            return Result.failure(401, "身份认证失败");
        }
        userCascadeDeleteService.cascadeDeleteUser(id, operatorId, operatorName,
                ipAddress != null ? ipAddress : "unknown");
        return Result.success("用户及相关数据已级联注销", null);
    }

    @PutMapping("/{id}/profile")
    public Result<UserVO> updateProfile(
            @PathVariable("id") Long id,
            @Valid @RequestBody UserProfileDTO profileDTO) {
        if (!requestContext.canAccessSelfOrAdmin(id)) {
            return Result.failure(403, "权限不足，仅本人或管理员可修改用户资料");
        }

        User user = userService.getById(id);
        if (user == null) {
            throw new BusinessException(404, "用户不存在");
        }

        if (profileDTO.getName() != null && !profileDTO.getName().isEmpty()) {
            user.setName(profileDTO.getName());
        }
        if (profileDTO.getUsername() != null && !profileDTO.getUsername().isEmpty()) {
            String newUsername = profileDTO.getUsername();
            if (!newUsername.equals(user.getUsername())) {
                if (userService.isUsernameExists(newUsername, id)) {
                    return Result.error("该用户名已被其他用户使用");
                }
            }
            user.setUsername(newUsername);
        }

        if (profileDTO.getPhone() != null) {
            user.setPhone(profileDTO.getPhone());
        }
        if (profileDTO.getAvatar() != null) {
            user.setAvatar(profileDTO.getAvatar());
        }
        if (profileDTO.getBirthday() != null) {
            user.setBirthday(profileDTO.getBirthday());
        }
        if (profileDTO.getGender() != null) {
            user.setGender(profileDTO.getGender());
        }

        userService.updateById(user);
        return Result.success("个人资料已更新", userService.convertToVO(user));
    }

    @GetMapping("/{id}/settings")
    public Result<UserSettingsDTO> getUserSettings(@PathVariable("id") Long id) {
        if (!requestContext.canAccessSelfOrAdmin(id)) {
            return Result.failure(403, "权限不足，仅本人或管理员可查看用户设置");
        }
        UserSettingsDTO settings = studentProfileService.getUserSettings(id);
        return Result.success(settings);
    }

    @PutMapping("/{id}/settings")
    public Result<UserSettingsDTO> updateUserSettings(
            @PathVariable("id") Long id,
            @Valid @RequestBody UserSettingsDTO settings) {
        if (!requestContext.canAccessSelfOrAdmin(id)) {
            return Result.failure(403, "权限不足，仅本人或管理员可修改用户设置");
        }
        UserSettingsDTO updated = studentProfileService.updateUserSettings(id, settings);
        return Result.success("个性化设置已保存", updated);
    }

    @PostMapping("/{id}/settings")
    public Result<UserSettingsDTO> updateUserSettingsByPost(
            @PathVariable("id") Long id,
            @Valid @RequestBody UserSettingsDTO settings) {
        return updateUserSettings(id, settings);
    }

    @PostMapping("/{id}/avatar")
    public Result<Map<String, String>> uploadAvatar(
            @PathVariable("id") Long id,
            @RequestParam("file") MultipartFile file) throws IOException {
        if (!requestContext.canAccessSelfOrAdmin(id)) {
            return Result.failure(403, "权限不足，仅本人或管理员可上传头像");
        }
        String avatarUrl = teacherProfileService.uploadAvatar(id, file);
        return Result.success("头像上传成功", Map.of("avatarUrl", avatarUrl));
    }

    @GetMapping("/{id}/sessions")
    public Result<List<com.eduplatform.user.vo.UserSessionVO>> getUserSessions(@PathVariable("id") Long id) {
        if (!requestContext.canAccessSelfOrAdmin(id)) {
            return Result.failure(403, "权限不足，仅本人或管理员可查看会话");
        }
        java.util.List<com.eduplatform.user.entity.UserSession> sessions = sessionService.getUserSessions(id);
        return Result.success(sessionService.convertToVOList(sessions));
    }

    @GetMapping("/online-status")
    public Result<List<Map<String, Object>>> getAllOnlineUserIds() {
        if (!requestContext.isAdmin()) {
            return Result.failure(403, "权限不足，仅管理员可查看在线用户");
        }
        List<Long> onlineUserIds = sessionService.getAllOnlineUserIds();
        if (onlineUserIds.isEmpty()) {
            return Result.success(Collections.emptyList());
        }
        List<User> onlineUsers = userService.getByIds(onlineUserIds);
        List<Map<String, Object>> result = onlineUsers.stream().map(user -> {
            Map<String, Object> map = new HashMap<>();
            map.put("id", user.getId());
            map.put("username", user.getUsername());
            map.put("name", user.getName());
            map.put("email", user.getEmail());
            map.put("role", user.getRole());
            return map;
        }).collect(Collectors.toList());
        return Result.success(result);
    }

    @GetMapping("/export")
    public void exportUsers(
            @RequestParam(name = "format", defaultValue = "csv") String format,
            @RequestParam(name = "role", required = false) String role,
            HttpServletResponse response) throws IOException {
        if (!requestContext.isAdmin()) {
            response.setStatus(403);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":403,\"message\":\"权限不足，仅管理员可导出用户数据\",\"data\":null}");
            return;
        }

        List<User> users = userService.getSimpleList(role);

        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String filename = "user_data_export_" + timestamp + ".csv";

        response.setContentType("text/csv;charset=UTF-8");
        response.setHeader("Content-Disposition", "attachment; filename=\"" + filename + "\"");
        response.setCharacterEncoding("UTF-8");

        PrintWriter writer = response.getWriter();
        writer.write('\ufeff');

        writer.println("ID,用户名,真实姓名,角色,当前状态,注册日期,最近登录");

        for (User user : users) {
            String statusText = user.getStatus() == 1 ? "正常" : "限制访问";
            String createdAt = user.getCreatedAt() != null
                    ? user.getCreatedAt().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                    : "未知";
            String lastLoginAt = user.getLastLoginAt() != null
                    ? user.getLastLoginAt().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))
                    : "从未登录";

            writer.println(String.format("%d,%s,%s,%s,%s,%s,%s",
                    user.getId(),
                    escapeCsv(user.getUsername()),
                    escapeCsv(user.getName()),
                    user.getRole(),
                    statusText,
                    createdAt,
                    lastLoginAt));
        }

        writer.flush();
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

    private Long parseUserId(String userIdHeader) {
        return requestContext.parseUserId(userIdHeader);
    }

    private boolean hasValidInternalToken(String requestInternalToken) {
        return internalTokenVerifier.isValid(requestInternalToken);
    }
}
