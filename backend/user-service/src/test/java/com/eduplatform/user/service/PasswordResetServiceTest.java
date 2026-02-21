package com.eduplatform.user.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.eduplatform.user.entity.User;
import com.eduplatform.user.mapper.UserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * PasswordResetService 安全逻辑测试。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PasswordResetService 单元测试")
class PasswordResetServiceTest {

    @InjectMocks
    private PasswordResetService passwordResetService;

    @Mock
    private UserMapper userMapper;

    @Mock
    private UserSessionService sessionService;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    @DisplayName("申请令牌-身份匹配时写入一次性令牌")
    void issueResetTokenShouldCacheTokenWhenIdentityMatches() {
        User user = new User();
        user.setId(1L);
        user.setEmail("user@example.com");
        user.setName("测试用户");
        when(userMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(user);
        when(valueOperations.increment(startsWith("pwd_reset:req:ip:"))).thenReturn(1L);
        when(valueOperations.increment(startsWith("pwd_reset:req:identity:"))).thenReturn(1L);

        PasswordResetService.PasswordResetIssueResult result = passwordResetService.issueResetToken(
                "user@example.com",
                "测试用户",
                "127.0.0.1");

        assertEquals(PasswordResetService.PasswordResetStatus.ACCEPTED, result.getStatus());
        assertNotNull(result.getToken());
        verify(valueOperations).set(startsWith("pwd_reset:token:"), eq("1"), anyLong(), eq(TimeUnit.SECONDS));
    }

    @Test
    @DisplayName("申请令牌-命中IP限流返回429状态")
    void issueResetTokenShouldReturnRateLimitedWhenIpExceeded() {
        when(valueOperations.increment(startsWith("pwd_reset:req:ip:"))).thenReturn(11L);

        PasswordResetService.PasswordResetIssueResult result = passwordResetService.issueResetToken(
                "user@example.com",
                "测试用户",
                "127.0.0.1");

        assertEquals(PasswordResetService.PasswordResetStatus.RATE_LIMITED, result.getStatus());
        verify(userMapper, never()).selectOne(any(LambdaQueryWrapper.class));
    }

    @Test
    @DisplayName("确认重置-有效令牌会更新密码并强制下线")
    void confirmResetByTokenShouldUpdatePasswordAndForceOffline() {
        User user = new User();
        user.setId(1L);
        user.setPassword(new BCryptPasswordEncoder().encode("oldpass123"));
        when(valueOperations.increment(startsWith("pwd_reset:confirm:ip:"))).thenReturn(1L);
        when(valueOperations.get("pwd_reset:token:token-123")).thenReturn("1");
        when(userMapper.selectById(1L)).thenReturn(user);

        PasswordResetService.PasswordResetStatus status = passwordResetService.confirmResetByToken(
                "token-123",
                "newpass123",
                "127.0.0.1");

        assertEquals(PasswordResetService.PasswordResetStatus.ACCEPTED, status);
        verify(userMapper).updateById(argThat(updated ->
                new BCryptPasswordEncoder().matches("newpass123", updated.getPassword())));
        verify(sessionService).forceOfflineUser(1L);
        verify(redisTemplate).delete("pwd_reset:token:token-123");
    }

    @Test
    @DisplayName("兼容重置-身份不匹配时静默受理但不更新密码")
    void resetByIdentityShouldNotUpdatePasswordWhenIdentityMismatched() {
        User user = new User();
        user.setId(1L);
        user.setEmail("user@example.com");
        user.setName("错误姓名");
        when(userMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(user);
        when(valueOperations.increment(startsWith("pwd_reset:req:ip:"))).thenReturn(1L);
        when(valueOperations.increment(startsWith("pwd_reset:req:identity:"))).thenReturn(1L);

        PasswordResetService.PasswordResetStatus status = passwordResetService.resetByIdentity(
                "user@example.com",
                "测试用户",
                "newpass123",
                "127.0.0.1");

        assertEquals(PasswordResetService.PasswordResetStatus.ACCEPTED, status);
        verify(userMapper, never()).updateById(any(User.class));
        verify(sessionService, never()).forceOfflineUser(anyLong());
    }
}

