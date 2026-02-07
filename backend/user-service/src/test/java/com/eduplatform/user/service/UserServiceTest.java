package com.eduplatform.user.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.eduplatform.user.dto.LoginRequest;
import com.eduplatform.user.dto.LoginResponse;
import com.eduplatform.user.dto.RegisterRequest;
import com.eduplatform.user.dto.ResetPasswordRequest;
import com.eduplatform.user.entity.User;
import com.eduplatform.user.mapper.UserMapper;
import com.eduplatform.user.util.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * UserService 单元测试
 *
 * 覆盖场景:
 * 1. 登录: 正常登录、密码错误、用户不存在、账号被禁用、单点登录踢出
 * 2. 注册: 正常注册、邮箱重复、用户名重复、禁止注册管理员
 * 3. 密码重置: 正常重置、邮箱不存在、真实姓名不匹配
 * 4. 用户管理: 删除用户、禁止删除管理员
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("UserService 单元测试")
class UserServiceTest {

    @InjectMocks
    private UserService userService;

    @Mock
    private UserMapper userMapper;

    @Mock
    private JwtUtil jwtUtil;

    @Mock
    private UserSessionService sessionService;

    @Mock
    private AuditLogService auditLogService;

    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = new User();
        testUser.setId(1L);
        testUser.setEmail("test@example.com");
        testUser.setUsername("testuser");
        testUser.setName("测试用户");
        // BCrypt 编码后的 "123456"
        testUser.setPassword("$2a$10$8.UnVuG9HHgffUDAlk8qfOuVGkqRzgVymGe07xd00DMxs.p" +
                ".Y8GVC");
        testUser.setRole("student");
        testUser.setStatus(1);
    }

    // =========================================================================
    // 登录测试
    // =========================================================================
    @Nested
    @DisplayName("登录测试")
    class LoginTests {

        @Test
        @DisplayName("正常登录 - 返回 Token 和用户信息")
        void loginSuccess() {
            // 准备
            LoginRequest request = new LoginRequest();
            request.setEmail("test@example.com");
            request.setPassword("123456");

            when(userMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(testUser);
            when(sessionService.hasOnlineSession(1L)).thenReturn(false);
            when(sessionService.createSession(1L)).thenReturn("jti-123");
            when(jwtUtil.generateToken(eq(1L), eq("test@example.com"),
                    eq("student"), eq("jti-123"))).thenReturn("mock-token");

            // 执行
            LoginResponse response = userService.login(request);

            // 验证
            assertNotNull(response);
            assertEquals("mock-token", response.getToken());
            assertEquals(1L, response.getUser().getId());
            assertEquals("test@example.com", response.getUser().getEmail());
            assertEquals("student", response.getUser().getRole());

            verify(userMapper).updateById(any(User.class));
        }

        @Test
        @DisplayName("登录失败 - 用户不存在")
        void loginFailUserNotFound() {
            LoginRequest request = new LoginRequest();
            request.setEmail("notexist@example.com");
            request.setPassword("123456");

            when(userMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

            RuntimeException ex = assertThrows(RuntimeException.class,
                    () -> userService.login(request));
            assertEquals("邮箱或密码错误", ex.getMessage());
        }

        @Test
        @DisplayName("登录失败 - 密码错误")
        void loginFailWrongPassword() {
            LoginRequest request = new LoginRequest();
            request.setEmail("test@example.com");
            request.setPassword("wrong_password");

            when(userMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(testUser);

            RuntimeException ex = assertThrows(RuntimeException.class,
                    () -> userService.login(request));
            assertEquals("邮箱或密码错误", ex.getMessage());
        }

        @Test
        @DisplayName("登录失败 - 账号被禁用")
        void loginFailAccountDisabled() {
            LoginRequest request = new LoginRequest();
            request.setEmail("test@example.com");
            request.setPassword("123456");

            testUser.setStatus(0);
            when(userMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(testUser);

            RuntimeException ex = assertThrows(RuntimeException.class,
                    () -> userService.login(request));
            assertEquals("账号已被禁用", ex.getMessage());
        }

        @Test
        @DisplayName("登录时踢出旧会话 - 单点登录")
        void loginKickOutOldSession() {
            LoginRequest request = new LoginRequest();
            request.setEmail("test@example.com");
            request.setPassword("123456");

            when(userMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(testUser);
            when(sessionService.hasOnlineSession(1L)).thenReturn(true);
            when(sessionService.createSession(1L)).thenReturn("new-jti");
            when(jwtUtil.generateToken(any(), any(), any(), any())).thenReturn("new-token");

            userService.login(request);

            // 验证旧会话被强制下线
            verify(sessionService).forceOfflineUser(1L);
        }
    }

    // =========================================================================
    // 注册测试
    // =========================================================================
    @Nested
    @DisplayName("注册测试")
    class RegisterTests {

        @Test
        @DisplayName("正常注册 - 创建用户并自动登录")
        void registerSuccess() {
            RegisterRequest request = new RegisterRequest();
            request.setEmail("new@example.com");
            request.setUsername("newuser");
            request.setRealName("新用户");
            request.setPassword("123456");
            request.setRole("student");

            // 邮箱和用户名均不存在
            when(userMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
            when(sessionService.createSession(any())).thenReturn("jti-new");
            when(jwtUtil.generateToken(any(), any(), any(), any())).thenReturn("new-token");

            LoginResponse response = userService.register(request);

            assertNotNull(response);
            assertEquals("new-token", response.getToken());
            verify(userMapper).insert(any(User.class));
        }

        @Test
        @DisplayName("注册失败 - 禁止注册管理员")
        void registerFailAdminRole() {
            RegisterRequest request = new RegisterRequest();
            request.setRole("admin");

            RuntimeException ex = assertThrows(RuntimeException.class,
                    () -> userService.register(request));
            assertEquals("不允许注册管理员账号", ex.getMessage());
        }

        @Test
        @DisplayName("注册失败 - 邮箱已存在")
        void registerFailEmailExists() {
            RegisterRequest request = new RegisterRequest();
            request.setEmail("test@example.com");
            request.setUsername("newuser");
            request.setRole("student");

            // 第一次 selectOne 查邮箱，返回已存在的用户
            when(userMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(testUser);

            RuntimeException ex = assertThrows(RuntimeException.class,
                    () -> userService.register(request));
            assertEquals("该邮箱已被注册", ex.getMessage());
        }

        @Test
        @DisplayName("注册时角色为空 - 默认为 student")
        void registerDefaultRole() {
            RegisterRequest request = new RegisterRequest();
            request.setEmail("new2@example.com");
            request.setUsername("newuser2");
            request.setRealName("新用户2");
            request.setPassword("123456");
            request.setRole(null);

            when(userMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
            when(sessionService.createSession(any())).thenReturn("jti");
            when(jwtUtil.generateToken(any(), any(), any(), any())).thenReturn("token");

            userService.register(request);

            verify(userMapper).insert(argThat(user ->
                    "student".equals(user.getRole())));
        }
    }

    // =========================================================================
    // 密码重置测试
    // =========================================================================
    @Nested
    @DisplayName("密码重置测试")
    class ResetPasswordTests {

        @Test
        @DisplayName("正常重置密码")
        void resetPasswordSuccess() {
            ResetPasswordRequest request = new ResetPasswordRequest();
            request.setEmail("test@example.com");
            request.setRealName("测试用户");
            request.setNewPassword("newpass123");

            when(userMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(testUser);

            assertDoesNotThrow(() -> userService.resetPassword(request));

            verify(userMapper).updateById(any(User.class));
            verify(sessionService).forceOfflineUser(1L);
        }

        @Test
        @DisplayName("重置密码失败 - 邮箱不存在")
        void resetPasswordFailEmailNotFound() {
            ResetPasswordRequest request = new ResetPasswordRequest();
            request.setEmail("notexist@example.com");

            when(userMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

            RuntimeException ex = assertThrows(RuntimeException.class,
                    () -> userService.resetPassword(request));
            assertEquals("该邮箱未注册", ex.getMessage());
        }

        @Test
        @DisplayName("重置密码失败 - 真实姓名不匹配")
        void resetPasswordFailNameMismatch() {
            ResetPasswordRequest request = new ResetPasswordRequest();
            request.setEmail("test@example.com");
            request.setRealName("错误姓名");

            when(userMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(testUser);

            RuntimeException ex = assertThrows(RuntimeException.class,
                    () -> userService.resetPassword(request));
            assertEquals("真实姓名验证失败", ex.getMessage());
        }
    }

    // =========================================================================
    // 用户管理测试
    // =========================================================================
    @Nested
    @DisplayName("用户管理测试")
    class UserManagementTests {

        @Test
        @DisplayName("删除用户 - 正常删除")
        void deleteUserSuccess() {
            when(userMapper.selectById(1L)).thenReturn(testUser);

            assertDoesNotThrow(() -> userService.delete(1L, 99L, "admin", "127.0.0.1"));

            verify(userMapper).deleteById(1L);
            verify(auditLogService).log(eq(99L), eq("admin"), eq("USER_DELETE"),
                    eq("USER"), eq(1L), eq("testuser"), eq("删除用户"), eq("127.0.0.1"));
        }

        @Test
        @DisplayName("删除用户失败 - 不能删除管理员")
        void deleteAdminFail() {
            testUser.setRole("admin");
            when(userMapper.selectById(1L)).thenReturn(testUser);

            RuntimeException ex = assertThrows(RuntimeException.class,
                    () -> userService.delete(1L, 99L, "admin", "127.0.0.1"));
            assertEquals("不能删除管理员账号", ex.getMessage());
        }

        @Test
        @DisplayName("删除用户失败 - 用户不存在")
        void deleteUserNotFound() {
            when(userMapper.selectById(999L)).thenReturn(null);

            RuntimeException ex = assertThrows(RuntimeException.class,
                    () -> userService.delete(999L, 99L, "admin", "127.0.0.1"));
            assertEquals("用户不存在", ex.getMessage());
        }

        @Test
        @DisplayName("Entity 转 VO - 空值安全")
        void convertToVONullSafe() {
            assertNull(userService.convertToVO(null));
        }

        @Test
        @DisplayName("Entity 转 VO - 字段正确映射")
        void convertToVOFieldMapping() {
            var vo = userService.convertToVO(testUser);

            assertNotNull(vo);
            assertEquals(testUser.getId(), vo.getId());
            assertEquals(testUser.getEmail(), vo.getEmail());
            assertEquals(testUser.getUsername(), vo.getUsername());
        }
    }
}
