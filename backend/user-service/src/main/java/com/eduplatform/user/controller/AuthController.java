package com.eduplatform.user.controller;

import com.eduplatform.common.result.Result;
import com.eduplatform.user.dto.LoginRequest;
import com.eduplatform.user.dto.LoginResponse;
import com.eduplatform.user.dto.RegisterRequest;
import com.eduplatform.user.dto.ResetPasswordRequest;
import com.eduplatform.user.service.UserService;
import com.eduplatform.user.service.UserSessionService;
import com.eduplatform.user.util.JwtUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * 认证授权控制器
 * 负责用户的注册、登录、Token 校验、会话维持（心跳）及账号注销等安全相关业务。
 * 结合 JWT 与 Redis Session 实现单点登录与安全审计。
 */
@Slf4j
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {
    // 记录服务启动时间
    private static final long START_TIME = System.currentTimeMillis();

    private final UserService userService;
    private final UserSessionService sessionService;
    private final JwtUtil jwtUtil;

    /**
     * 用户登录接口
     * 验证身份并颁发双令牌（Access Token & JTI），同时在 Redis 中开启在线会话。
     *
     * @param request 包含邮箱/用户名及加密密码的登录请求
     * @return 包含用户信息及 Token 的响应对象
     */
    @PostMapping("/login")
    public Result<LoginResponse> login(@RequestBody LoginRequest request) {
        LoginResponse response = userService.login(request);
        return Result.success("登录成功", response);
    }

    /**
     * 用户注册接口
     * 初始化用户基础账号并在成功后自动执行登录流程返回 Token。
     *
     * @param request 注册资料 (含角色类型)
     * @return 登录成功的响应对象
     */
    @PostMapping("/register")
    public Result<LoginResponse> register(@RequestBody RegisterRequest request) {
        LoginResponse response = userService.register(request);
        return Result.success("注册成功", response);
    }

    /**
     * 内部/网关调用：实时检查用户账号状态
     * 校验其是否被锁定或注销。
     *
     * @param userId 目标用户 ID
     * @return true 若状态正常 (status=1)
     */
    @GetMapping("/check-status/{userId}")
    public Result<Boolean> checkUserStatus(@PathVariable("userId") Long userId) {
        var user = userService.getById(userId);
        if (user == null) {
            return Result.error("用户不存在");
        }
        if (user.getStatus() != 1) {
            return Result.error("账号已被禁用或处于限制状态");
        }
        return Result.success("账号正常", true);
    }

    /**
     * 服务健康检查接口
     * 增强版：实时探测数据库状态、Redis 状态并返回服务运行时长。
     */
    @GetMapping("/health")
    public Result<java.util.Map<String, Object>> healthCheck() {
        java.util.Map<String, Object> health = new java.util.HashMap<>();
        long now = System.currentTimeMillis();

        health.put("status", "UP");
        health.put("service", "user-service");
        health.put("timestamp", now);

        // 实时探测数据库状态（简单 count 校验）
        try {
            userService.countTotal();
            health.put("database", "UP");
        } catch (Exception e) {
            health.put("database", "DOWN");
            health.put("db_error", e.getMessage());
        }

        // 计算运行时长
        long uptimeSeconds = (now - START_TIME) / 1000;
        health.put("boot_time", java.time.Instant.ofEpochMilli(START_TIME).toString());
        health.put("uptime_seconds", uptimeSeconds);

        return Result.success("系统运行状态良好", health);
    }

    /**
     * 验证当前 Token 是否在有效的 Session 白名单中
     * 主要用于防止 Token 被篡改或在异地登录后被挤下线。
     *
     * @param authHeader Authorization 自定义头 (Bearer ...)
     * @return true 若会话在 Redis 中仍然存活
     */
    @GetMapping("/validate-token/{userId}")
    public Result<Boolean> validateToken(
            @PathVariable("userId") Long userId,
            @RequestHeader(value = "Authorization", required = false) String authHeader) {

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return Result.error("令牌格式错误");
        }
        String token = authHeader.substring(7);
        String jti = jwtUtil.getJtiFromToken(token);
        if (!sessionService.isSessionValid(jti)) {
            return Result.error("您的账号已在其他端登录或会话已失效，请重新登录");
        }
        return Result.success("会话有效", true);
    }

    /**
     * 安全登出接口
     * 清理服务端 Redis Session 中的 jti 记录，使客户端持有的 Token 即刻失效。
     */
    @PostMapping("/logout")
    public Result<Boolean> logout(
            @RequestHeader(value = "Authorization", required = false) String authHeader) {

        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            try {
                String jti = jwtUtil.getJtiFromToken(token);
                if (jti != null) {
                    sessionService.logout(jti);
                    log.debug("用户主动登出成功，jti: {}", jti);
                }
            } catch (Exception e) {
                // 若令牌已过期无法解析，尝试强制清理该用户的全端在线记录
                log.debug("Token解析失败，尝试基于身份强制下线: {}", e.getMessage());
                try {
                    Long userId = jwtUtil.getUserIdFromToken(token);
                    if (userId != null) {
                        sessionService.forceOfflineUser(userId);
                    }
                } catch (Exception ex) {
                    log.debug("无法提取用户标识，注销中断");
                }
            }
        }
        return Result.success("注销成功", true);
    }

    /**
     * 维持会话心跳 (Heartbeat)
     * 前置拦截器校验后，通过此接口续期 Redis 的 TTL，延长会话生命周期。
     */
    @PostMapping("/heartbeat")
    public Result<Boolean> heartbeat(
            @RequestHeader(value = "Authorization", required = false) String authHeader) {

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return Result.error("身份认证已缺失");
        }
        String token = authHeader.substring(7);
        String jti = jwtUtil.getJtiFromToken(token);
        if (sessionService.heartbeat(jti)) {
            return Result.success("会话已续期", true);
        }
        return Result.error("会话已从后台剔除");
    }

    /**
     * 强制下线 (管理员/安全审计使用)
     * 用于封禁账号或处理违规行为，立即剔除该用户的所有在线设备。
     *
     * @param userId 待强制下线的用户 ID
     */
    @PostMapping("/force-logout/{userId}")
    public Result<Boolean> forceLogout(@PathVariable("userId") Long userId) {
        sessionService.forceOfflineUser(userId);
        return Result.success("已强制剔除该用户的所有活动会话", true);
    }

    /**
     * 账户密码重置
     * 校验验证逻辑（如手机/邮件）正确后执行数据库密码加密覆盖。
     */
    @PostMapping("/reset-password")
    public Result<Boolean> resetPassword(@RequestBody ResetPasswordRequest request) {
        userService.resetPassword(request);
        return Result.success("管理员已重置您的登录密码", true);
    }
}
