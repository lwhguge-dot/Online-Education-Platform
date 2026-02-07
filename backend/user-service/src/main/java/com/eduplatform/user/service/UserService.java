package com.eduplatform.user.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.eduplatform.user.dto.*;
import com.eduplatform.user.entity.User;
import com.eduplatform.user.mapper.UserMapper;
import com.eduplatform.user.util.JwtUtil;
import com.eduplatform.user.vo.UserVO;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 用户服务类
 * 处理用户相关的业务逻辑，包括登录、注册、个人资料管理及管理员操作。
 *
 * @author Antigravity
 */
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserMapper userMapper;
    private final JwtUtil jwtUtil;
    private final UserSessionService sessionService;
    private final AuditLogService auditLogService;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    /**
     * 将用户实体转换为视图对象
     *
     * @param user 用户实体
     * @return 用户视图对象
     */
    public UserVO convertToVO(User user) {
        if (user == null) {
            return null;
        }
        UserVO vo = new UserVO();
        BeanUtils.copyProperties(user, vo);
        return vo;
    }

    /**
     * 将用户实体列表转换为视图对象列表
     *
     * @param users 用户实体列表
     * @return 用户视图对象列表
     */
    public List<UserVO> convertToVOList(List<User> users) {
        return users.stream()
                .map(this::convertToVO)
                .collect(Collectors.toList());
    }

    /**
     * 用户登录逻辑
     * 1. 验证用户是否存在且状态正常
     * 2. 匹配 BCrypt 加密密码
     * 3. 强制踢出该用户的旧会话（单点登录支持）
     * 4. 更新最后登录时间并创建新的 Redis/数据库会话
     * 5. 生成 JWT Token 并返回用户信息
     *
     * @param request 登录请求参数 (email, password)
     * @return 包含 Token 和用户信息的结果
     * @throws RuntimeException 当密码错误、用户不存在或被禁用时抛出
     */
    public LoginResponse login(LoginRequest request) {
        // 1. 根据邮箱查询用户
        User user = userMapper.selectOne(
                new LambdaQueryWrapper<User>().eq(User::getEmail, request.getEmail()));

        if (user == null) {
            throw new RuntimeException("邮箱或密码错误");
        }

        // 2. 检查账号状态（1: 正常）
        if (user.getStatus() != 1) {
            throw new RuntimeException("账号已被禁用");
        }

        // 3. 验证 BCrypt 加密后的密码
        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new RuntimeException("邮箱或密码错误");
        }

        // 4. 处理单点登录：如果已有在线会话，则强制踢出
        if (sessionService.hasOnlineSession(user.getId())) {
            sessionService.forceOfflineUser(user.getId());
        }

        // 5. 更新登录元数据
        user.setLastLoginAt(LocalDateTime.now());
        userMapper.updateById(user);

        // 6. 创建会话追踪并签发 JWT
        String jti = sessionService.createSession(user.getId());
        String token = jwtUtil.generateToken(user.getId(), user.getEmail(), user.getRole(), jti);

        return LoginResponse.builder()
                .token(token)
                .user(LoginResponse.UserInfo.builder()
                        .id(user.getId())
                        .email(user.getEmail())
                        .username(user.getUsername())
                        .name(user.getName())
                        .role(user.getRole())
                        .avatar(user.getAvatar())
                        .build())
                .build();
    }

    /**
     * 用户注册逻辑
     * 1. 验证角色权限（严禁注册管理员）
     * 2. 查重邮箱与用户名
     * 3. 初始实体封装与加密密码
     * 4. 自动登录流程
     *
     * @param request 注册请求参数
     * @return 登录成功后的响应（同登录接口）
     */
    public LoginResponse register(RegisterRequest request) {
        if ("admin".equals(request.getRole())) {
            throw new RuntimeException("不允许注册管理员账号");
        }

        // 1. 检查邮箱唯一性
        User existByEmail = userMapper.selectOne(
                new LambdaQueryWrapper<User>().eq(User::getEmail, request.getEmail()));
        if (existByEmail != null) {
            throw new RuntimeException("该邮箱已被注册");
        }

        // 2. 检查用户名唯一性
        User existByUsername = userMapper.selectOne(
                new LambdaQueryWrapper<User>().eq(User::getUsername, request.getUsername()));
        if (existByUsername != null) {
            throw new RuntimeException("该用户名已被使用");
        }

        // 3. 构建用户实体
        User user = new User();
        user.setEmail(request.getEmail());
        user.setUsername(request.getUsername());
        user.setName(request.getRealName()); // 真实姓名用于安全校验，通常不直接在资料页修改
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setRole(request.getRole() != null ? request.getRole() : "student");
        user.setStatus(1);
        user.setCreatedAt(LocalDateTime.now());
        user.setUpdatedAt(LocalDateTime.now());

        userMapper.insert(user);

        // 4. 注册成功后自动为用户创建会话并生成 Token
        String jti = sessionService.createSession(user.getId());
        String token = jwtUtil.generateToken(user.getId(), user.getEmail(), user.getRole(), jti);

        return LoginResponse.builder()
                .token(token)
                .user(LoginResponse.UserInfo.builder()
                        .id(user.getId())
                        .email(user.getEmail())
                        .username(user.getUsername())
                        .name(user.getName())
                        .role(user.getRole())
                        .avatar(user.getAvatar())
                        .build())
                .build();
    }

    /**
     * 密码重置 - 通过邮箱和真实姓名验证
     */
    public void resetPassword(ResetPasswordRequest request) {
        // 查找用户
        User user = userMapper.selectOne(
                new LambdaQueryWrapper<User>().eq(User::getEmail, request.getEmail()));

        if (user == null) {
            throw new RuntimeException("该邮箱未注册");
        }

        // 验证真实姓名（防止空指针）
        if (user.getName() == null || !user.getName().equals(request.getRealName())) {
            throw new RuntimeException("真实姓名验证失败");
        }

        // 更新密码
        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        user.setUpdatedAt(LocalDateTime.now());
        userMapper.updateById(user);

        // 使所有现有会话失效
        sessionService.forceOfflineUser(user.getId());
    }

    /**
     * 根据用户 ID 获取原始实体
     *
     * @param id 用户ID
     * @return 用户实体 (若不存在返回 null)
     */
    public User getById(Long id) {
        return userMapper.selectById(id);
    }

    /**
     * 分页查询用户列表 (支持角色、状态、关键词筛选)
     *
     * @param page    当前页码
     * @param size    每页记录数
     * @param role    角色过滤 (可选)
     * @param status  状态过滤 (可选)
     * @param keyword 关键词搜索 (匹配邮箱或用户名)
     * @return 分页结果
     */
    public Page<User> getList(int page, int size, String role, Integer status, String keyword) {
        Page<User> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();

        if (role != null && !role.isEmpty()) {
            wrapper.eq(User::getRole, role);
        }
        if (status != null) {
            wrapper.eq(User::getStatus, status);
        }
        if (keyword != null && !keyword.isEmpty()) {
            wrapper.and(w -> w.like(User::getEmail, keyword).or().like(User::getUsername, keyword));
        }
        wrapper.orderByDesc(User::getCreatedAt);

        return userMapper.selectPage(pageParam, wrapper);
    }

    /**
     * 管理员操作：更新用户账号状态
     *
     * @param id           被操作用户ID
     * @param status       新状态 (1:正常, 0:禁用)
     * @param operatorId   执行操作的管理员ID
     * @param operatorName 执行操作的管理员名称
     * @param ipAddress    操作来源 IP
     */
    public void updateStatus(Long id, Integer status, Long operatorId, String operatorName, String ipAddress) {
        User user = userMapper.selectById(id);
        if (user == null) {
            throw new RuntimeException("用户不存在");
        }

        userMapper.updateStatusById(id, status);

        // 记录关键审计日志，用于后续追踪管理行为
        String actionType = status == 1 ? "USER_ENABLE" : "USER_DISABLE";
        String details = status == 1 ? "启用用户账户" : "禁用用户账户";
        auditLogService.log(operatorId, operatorName, actionType, "USER", id, user.getUsername(), details, ipAddress);
    }

    /**
     * 更新用户状态（无审计日志版本，用于内部调用）
     */
    public void updateStatusInternal(Long id, Integer status) {
        userMapper.updateStatusById(id, status);
    }

    public void delete(Long id, Long operatorId, String operatorName, String ipAddress) {
        User user = userMapper.selectById(id);
        if (user == null) {
            throw new RuntimeException("用户不存在");
        }
        if ("admin".equals(user.getRole())) {
            throw new RuntimeException("不能删除管理员账号");
        }

        String username = user.getUsername();
        userMapper.deleteById(id);

        // 记录审计日志
        auditLogService.log(operatorId, operatorName, "USER_DELETE", "USER", id, username, "删除用户", ipAddress);
    }

    /**
     * 删除用户（无审计日志版本，用于内部调用）
     */
    public void deleteInternal(Long id) {
        User user = userMapper.selectById(id);
        if (user != null && "admin".equals(user.getRole())) {
            throw new RuntimeException("不能删除管理员账号");
        }
        userMapper.deleteById(id);
    }

    public long countByRole(String role) {
        return userMapper.selectCount(new LambdaQueryWrapper<User>().eq(User::getRole, role));
    }

    public long countTotal() {
        return userMapper.selectCount(null);
    }

    public List<User> getSimpleList(String role) {
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<>();
        if (role != null && !role.isEmpty() && !"all".equals(role)) {
            wrapper.eq(User::getRole, role);
        }
        wrapper.orderByAsc(User::getId);
        return userMapper.selectList(wrapper);
    }

    public List<User> getRecentLoginUsers(int limit) {
        return userMapper.selectList(
                new LambdaQueryWrapper<User>()
                        .orderByDesc(User::getLastLoginAt)
                        .last("LIMIT " + limit));
    }

    public List<User> getRecentCreatedUsers(int limit) {
        return userMapper.selectList(
                new LambdaQueryWrapper<User>()
                        .orderByDesc(User::getCreatedAt)
                        .last("LIMIT " + limit));
    }

    public void updateById(User user) {
        user.setUpdatedAt(LocalDateTime.now());
        userMapper.updateById(user);
    }

    /**
     * 根据用户 ID 列表批量查询用户基础信息。
     * 业务原因：供 course-service 等外部服务通过 Feign 一次性获取多个用户信息，
     * 避免逐个调用产生 N+1 性能问题。
     *
     * 使用 MyBatis-Plus selectBatchIds 实现单条 SQL 批量查询。
     *
     * @param ids 用户 ID 列表
     * @return 用户基础信息列表
     */
    public List<User> getByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Collections.emptyList();
        }
        return userMapper.selectBatchIds(ids);
    }

    /**
     * 检查用户名是否已被使用
     */
    public boolean isUsernameExists(String username, Long excludeUserId) {
        LambdaQueryWrapper<User> wrapper = new LambdaQueryWrapper<User>()
                .eq(User::getUsername, username);
        if (excludeUserId != null) {
            wrapper.ne(User::getId, excludeUserId);
        }
        return userMapper.selectCount(wrapper) > 0;
    }

    /**
     * 统计今日新增用户数（基于created_at字段）
     */
    public long countNewUsersToday() {
        LocalDateTime todayStart = LocalDateTime.now().toLocalDate().atStartOfDay();
        LocalDateTime todayEnd = todayStart.plusDays(1);
        return userMapper.selectCount(
                new LambdaQueryWrapper<User>()
                        .ge(User::getCreatedAt, todayStart)
                        .lt(User::getCreatedAt, todayEnd));
    }

    /**
     * 统计今日活跃用户数（基于last_login_at字段）
     */
    public long countActiveUsersToday() {
        LocalDateTime todayStart = LocalDateTime.now().toLocalDate().atStartOfDay();
        LocalDateTime todayEnd = todayStart.plusDays(1);
        return userMapper.selectCount(
                new LambdaQueryWrapper<User>()
                        .ge(User::getLastLoginAt, todayStart)
                        .lt(User::getLastLoginAt, todayEnd));
    }

    /**
     * 统计指定日期的新增用户数
     *
     * @param date 目标日期
     * @return 新增用户数
     */
    public long countNewUsersByDate(LocalDateTime date) {
        LocalDateTime dayStart = date.toLocalDate().atStartOfDay();
        LocalDateTime dayEnd = dayStart.plusDays(1);
        return userMapper.selectCount(
                new LambdaQueryWrapper<User>()
                        .ge(User::getCreatedAt, dayStart)
                        .lt(User::getCreatedAt, dayEnd));
    }

    /**
     * 统计指定日期的活跃用户数（最后登录时间在当天的用户）
     *
     * @param date 目标日期
     * @return 活跃用户数
     */
    public long countActiveUsersByDate(LocalDateTime date) {
        LocalDateTime dayStart = date.toLocalDate().atStartOfDay();
        LocalDateTime dayEnd = dayStart.plusDays(1);
        return userMapper.selectCount(
                new LambdaQueryWrapper<User>()
                        .ge(User::getLastLoginAt, dayStart)
                        .lt(User::getLastLoginAt, dayEnd));
    }
}
