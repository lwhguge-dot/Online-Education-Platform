package com.eduplatform.user.controller;

import com.eduplatform.common.result.Result;
import com.eduplatform.user.dto.UserProfileDTO;
import com.eduplatform.user.dto.UserSettingsDTO;
import com.eduplatform.user.entity.User;
import com.eduplatform.user.service.StudentProfileService;
import com.eduplatform.user.service.UserCascadeDeleteService;
import com.eduplatform.user.service.UserService;
import com.eduplatform.user.service.UserSessionService;
import com.eduplatform.user.vo.UserBriefVO;
import com.eduplatform.user.vo.UserVO;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

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
 * 提供用户管理、个人资料维护、个性化设置、在线会话追踪及数据导出等核心 RESTful API。
 *
 * @author Antigravity
 */
@Slf4j
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final StudentProfileService studentProfileService;
    private final UserSessionService sessionService;
    private final UserCascadeDeleteService userCascadeDeleteService;

    /**
     * 获取用户列表 (管理员控制台使用)
     * 支持分页、角色筛选、状态过滤及关键词联想搜索。
     *
     * @param page    当前页码 (从1开始)
     * @param limit   每页显示的记录条数
     * @param role    角色过滤 (如: ADMIN, TEACHER, STUDENT)
     * @param status  用户状态 (1: 正常, 0: 禁用)
     * @param keyword 搜索关键词 (匹配用户名、姓名或邮箱)
     * @return 包含用户视图列表及分页元数据的包装对象
     */
    @GetMapping("/list")
    public Result<Map<String, Object>> getList(
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "limit", defaultValue = "10") int limit,
            @RequestParam(name = "role", required = false) String role,
            @RequestParam(name = "status", required = false) Integer status,
            @RequestParam(name = "keyword", required = false) String keyword) {

        java.util.List<User> users = userService.getSimpleList(role);
        List<UserVO> userVOs = userService.convertToVOList(users);

        // 服务端手动分页逻辑 (后续可迁移至 MyBatis-Plus 分页插件)
        int total = userVOs.size();
        int start = (page - 1) * limit;
        int end = Math.min(start + limit, total);
        java.util.List<UserVO> pageUsers = start < total ? userVOs.subList(start, end)
                : java.util.Collections.emptyList();

        Map<String, Object> data = new HashMap<>();
        data.put("list", pageUsers);

        Map<String, Object> pagination = new HashMap<>();
        pagination.put("total", total);
        pagination.put("page", page);
        pagination.put("limit", limit);
        pagination.put("pages", (int) Math.ceil((double) total / limit));
        data.put("pagination", pagination);

        return Result.success(data);
    }

    /**
     * 获取全系统用户分布统计
     * 提供用户总数、各角色画像人数分布及系统最近活跃用户的简要信息。
     *
     * @return 统计数据映射集
     */
    @GetMapping("/stats")
    public Result<Map<String, Object>> getStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("total", userService.countTotal());
        stats.put("students", userService.countByRole("student"));
        stats.put("teachers", userService.countByRole("teacher"));
        stats.put("admins", userService.countByRole("admin"));

        // 获取近期登录的活跃用户
        java.util.List<User> recentUsers = userService.getRecentLoginUsers(20);
        stats.put("recentUsers", userService.convertToVOList(recentUsers));

        java.util.List<User> allUsers = userService.getSimpleList(null);
        stats.put("allUsers", userService.convertToVOList(allUsers));

        return Result.success(stats);
    }

    /**
     * 获取指定用户的详细脱敏资料
     * 逻辑：根据 ID 查询用户实体并根据请求人的权限进行数据隔离展示。
     *
     * @param id 用户唯一标识 ID
     * @return 用户视图对象 (UserVO)
     */
    @GetMapping("/{id}")
    public Result<UserVO> getById(@PathVariable("id") Long id) {
        User user = userService.getById(id);
        if (user != null) {
            return Result.success(userService.convertToVO(user));
        }
        return Result.error("用户不存在");
    }

    /**
     * 批量获取用户简要信息。
     * 业务原因：供外部服务（如 course-service）通过 Feign 一次性获取多个用户的基本信息，
     * 避免逐个调用产生 N+1 性能问题。
     *
     * 使用场景：教师导出学生列表时批量获取真实姓名和邮箱。
     *
     * @param ids 用户 ID 列表
     * @return 用户简要信息列表
     */
    @PostMapping("/batch")
    public Result<List<UserBriefVO>> getUsersByIds(@RequestBody List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Result.success(Collections.emptyList());
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

    /**
     * 修改用户账号状态 (管理员操作)
     * 支持启用、禁用。禁用操作将触发审计日志并记录执行上下文。
     *
     * @param id            用户ID
     * @param body          请求体，包含 key 为 "status" 的目标值
     * @param operatorIdStr 网关层透传的操作人 ID
     * @param operatorName  网关层透传的操作人姓名
     * @param ipAddress     请求来源的真实 IP
     * @return 操作结果响应
     */
    @PutMapping("/{id}/status")
    public Result<String> updateStatus(
            @PathVariable("id") Long id,
            @RequestBody Map<String, Object> body,
            @RequestHeader(value = "X-User-Id", required = false) String operatorIdStr,
            @RequestHeader(value = "X-User-Role", required = false) String operatorRole,
            @RequestHeader(value = "X-User-Name", required = false) String operatorName,
            @RequestHeader(value = "X-Real-IP", required = false) String ipAddress) {

        // 仅管理员允许修改账号状态，避免普通用户越权禁用他人
        if (!isAdminRole(operatorRole)) {
            return Result.failure(403, "权限不足，仅管理员可修改用户状态");
        }

        Integer status = body.get("status") instanceof Integer
                ? (Integer) body.get("status")
                : Integer.parseInt(body.get("status").toString());

        Long operatorId = parseUserId(operatorIdStr);
        if (operatorId == null || operatorName == null) {
            return Result.failure(401, "身份认证失败");
        }

        // 统一走审计链路，禁止公开接口进入无审计更新分支
        userService.updateStatus(id, status, operatorId, operatorName,
                ipAddress != null ? ipAddress : "unknown");
        return Result.success("状态更新成功", null);
    }

    /**
     * 级联注销用户账号 (高危)
     * 调用级联删除服务，此操作会物理清理该用户在 User、Course 等所有微服务中的冗余业务数据。
     *
     * @param id            待删除的用户 ID
     * @param operatorIdStr 操作执行人 ID
     * @param operatorName  操作人姓名
     * @param ipAddress     客户端 IP 来源
     */
    @DeleteMapping("/{id}")
    public Result<Void> delete(
            @PathVariable("id") Long id,
            @RequestHeader(value = "X-User-Id", required = false) String operatorIdStr,
            @RequestHeader(value = "X-User-Role", required = false) String operatorRole,
            @RequestHeader(value = "X-User-Name", required = false) String operatorName,
            @RequestHeader(value = "X-Real-IP", required = false) String ipAddress) {

        // 高危删除操作仅允许管理员执行
        if (!isAdminRole(operatorRole)) {
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

    /**
     * 更新用户个人资料 (资料编辑)
     * 允许更新用户名、手机号、头像、生日、性别等个人属性。
     *
     * @param id         用户 ID
     * @param profileDTO 包含变更信息的资料传输对象
     * @return 更新后的完整 UserVO
     */
    @PutMapping("/{id}/profile")
    public Result<UserVO> updateProfile(
            @PathVariable("id") Long id,
            @RequestHeader(value = "X-User-Id", required = false) String currentUserIdStr,
            @RequestHeader(value = "X-User-Role", required = false) String currentUserRole,
            @RequestBody UserProfileDTO profileDTO) {
        // 仅本人或管理员可修改资料
        Long currentUserId = parseUserId(currentUserIdStr);
        if (!hasSelfOrAdminAccess(id, currentUserId, currentUserRole)) {
            return Result.failure(403, "权限不足，仅本人或管理员可修改资料");
        }

        User user = userService.getById(id);
        if (user == null) {
            return Result.error("用户不存在");
        }

        // 更新允许修改的基础字段
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

    /**
     * 获取用户个性化业务设置
     * 包括系统的通知偏好设置、周期性学习目标等扩展配置。
     */
    @GetMapping("/{id}/settings")
    public Result<UserSettingsDTO> getUserSettings(
            @PathVariable("id") Long id,
            @RequestHeader(value = "X-User-Id", required = false) String currentUserIdStr,
            @RequestHeader(value = "X-User-Role", required = false) String currentUserRole) {
        Long currentUserId = parseUserId(currentUserIdStr);
        if (!hasSelfOrAdminAccess(id, currentUserId, currentUserRole)) {
            return Result.failure(403, "权限不足，仅本人或管理员可查看设置");
        }
        UserSettingsDTO settings = studentProfileService.getUserSettings(id);
        return Result.success(settings);
    }

    /**
     * 覆盖更新用户个性化业务设置
     */
    @PutMapping("/{id}/settings")
    public Result<UserSettingsDTO> updateUserSettings(
            @PathVariable("id") Long id,
            @RequestHeader(value = "X-User-Id", required = false) String currentUserIdStr,
            @RequestHeader(value = "X-User-Role", required = false) String currentUserRole,
            @RequestBody UserSettingsDTO settings) {
        Long currentUserId = parseUserId(currentUserIdStr);
        if (!hasSelfOrAdminAccess(id, currentUserId, currentUserRole)) {
            return Result.failure(403, "权限不足，仅本人或管理员可修改设置");
        }
        UserSettingsDTO updated = studentProfileService.updateUserSettings(id, settings);
        return Result.success("个性化设置已保存", updated);
    }

    /**
     * 兼容性接口：部分客户端不便发起 PUT 时使用 POST 更新设置。
     */
    @PostMapping("/{id}/settings")
    public Result<UserSettingsDTO> updateUserSettingsByPost(
            @PathVariable("id") Long id,
            @RequestHeader(value = "X-User-Id", required = false) String currentUserIdStr,
            @RequestHeader(value = "X-User-Role", required = false) String currentUserRole,
            @RequestBody UserSettingsDTO settings) {
        return updateUserSettings(id, currentUserIdStr, currentUserRole, settings);
    }

    /**
     * 查询指定用户的登录会话历史
     * 展示近期的登录地点、登录时间及其当前的在线状态（基于 jti）。
     */
    @GetMapping("/{id}/sessions")
    public Result<List<com.eduplatform.user.vo.UserSessionVO>> getUserSessions(
            @PathVariable("id") Long id,
            @RequestHeader(value = "X-User-Id", required = false) String currentUserIdStr,
            @RequestHeader(value = "X-User-Role", required = false) String currentUserRole) {
        Long currentUserId = parseUserId(currentUserIdStr);
        if (!hasSelfOrAdminAccess(id, currentUserId, currentUserRole)) {
            return Result.failure(403, "权限不足，仅本人或管理员可查看会话");
        }
        java.util.List<com.eduplatform.user.entity.UserSession> sessions = sessionService.getUserSessions(id);
        return Result.success(sessionService.convertToVOList(sessions));
    }

    /**
     * 获取全系统当前的在线用户 ID 列表 (管理员/仪表盘使用)
     */
    @GetMapping("/online-status")
    public Result<List<Long>> getAllOnlineUserIds(
            @RequestHeader(value = "X-User-Role", required = false) String currentUserRole) {
        if (!isAdminRole(currentUserRole)) {
            return Result.failure(403, "权限不足，仅管理员可查看在线状态");
        }
        List<Long> onlineUserIds = sessionService.getAllOnlineUserIds();
        return Result.success(onlineUserIds);
    }

    /**
     * 导出全量用户资料报表
     * 逻辑：生成符合标准的 CSV 文本流，支持角色过滤。包含 BOM 头以解决中文乱码。
     *
     * @param format   导出格式 (目前仅支持 csv)
     * @param role     角色过滤
     * @param response HTTP 响应流
     */
    @GetMapping("/export")
    public void exportUsers(
            @RequestParam(name = "format", defaultValue = "csv") String format,
            @RequestParam(name = "role", required = false) String role,
            @RequestHeader(value = "X-User-Role", required = false) String currentUserRole,
            HttpServletResponse response) throws IOException {

        // 导出全量用户数据属于管理员能力
        if (!isAdminRole(currentUserRole)) {
            response.setStatus(403);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":403,\"message\":\"权限不足，仅管理员可导出用户数据\",\"data\":null}");
            return;
        }

        List<User> users = userService.getSimpleList(role);

        // 构造具有业务特征的动态文件名
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String filename = "user_data_export_" + timestamp + ".csv";

        response.setContentType("text/csv;charset=UTF-8");
        response.setHeader("Content-Disposition", "attachment; filename=\"" + filename + "\"");
        response.setCharacterEncoding("UTF-8");

        PrintWriter writer = response.getWriter();
        // 显式写入 UTF-8 BOM，确保 Excel 打开不乱码
        writer.write('\ufeff');

        // CSV 表头定义
        writer.println("ID,用户名,真实姓名,角色,当前状态,注册日期,最近登录");

        // 写入数据行
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

    /**
     * 内部辅助：执行 CSV 字符转义，防止数据截断或脚本注入。
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
     * 解析网关注入用户ID。
     */
    private Long parseUserId(String userIdHeader) {
        if (userIdHeader == null || userIdHeader.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(userIdHeader);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * 判断是否管理员角色。
     */
    private boolean isAdminRole(String role) {
        return role != null && "admin".equalsIgnoreCase(role);
    }

    /**
     * 判断是否具备“本人或管理员”访问权限。
     */
    private boolean hasSelfOrAdminAccess(Long targetUserId, Long currentUserId, String currentUserRole) {
        if (isAdminRole(currentUserRole)) {
            return true;
        }
        return currentUserId != null && currentUserId.equals(targetUserId);
    }
}
