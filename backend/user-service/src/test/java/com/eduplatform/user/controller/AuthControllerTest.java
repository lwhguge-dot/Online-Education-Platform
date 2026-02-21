package com.eduplatform.user.controller;

import com.eduplatform.common.result.Result;
import com.eduplatform.user.dto.PasswordResetConfirmRequest;
import com.eduplatform.user.dto.PasswordResetIssueRequest;
import com.eduplatform.user.dto.PasswordResetTokenResponse;
import com.eduplatform.user.dto.ResetPasswordRequest;
import com.eduplatform.user.service.PasswordResetService;
import com.eduplatform.user.service.UserService;
import com.eduplatform.user.service.UserSessionService;
import com.eduplatform.user.util.JwtUtil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AuthController 权限回归测试。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AuthController 单元测试")
class AuthControllerTest {

    @InjectMocks
    private AuthController authController;

    @Mock
    private UserService userService;

    @Mock
    private UserSessionService userSessionService;

    @Mock
    private PasswordResetService passwordResetService;

    @Mock
    private JwtUtil jwtUtil;

    @Test
    @DisplayName("兼容重置密码-非管理员禁止访问")
    void resetPasswordShouldRejectNonAdmin() {
        ResetPasswordRequest request = new ResetPasswordRequest();
        request.setEmail("user@example.com");
        request.setRealName("测试用户");
        request.setNewPassword("pass1234");

        Result<Boolean> result = authController.resetPassword(request, "student");

        assertNotNull(result);
        assertEquals(403, result.getCode());
        assertEquals("权限不足，仅管理员可重置密码", result.getMessage());
        verify(userService, never()).resetPassword(any());
        verify(passwordResetService, never()).resetByIdentity(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("重置密码-管理员可直接执行")
    void resetPasswordShouldAllowAdmin() {
        ResetPasswordRequest request = new ResetPasswordRequest();
        request.setEmail("user@example.com");
        request.setRealName("测试用户");
        request.setNewPassword("pass1234");

        Result<Boolean> result = authController.resetPassword(request, "admin");

        assertNotNull(result);
        assertEquals(200, result.getCode());
        assertEquals("管理员已重置您的登录密码", result.getMessage());
        verify(userService).resetPassword(request);
        verify(passwordResetService, never()).resetByIdentity(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("申请重置令牌-返回令牌")
    void issuePasswordResetTokenShouldReturnToken() {
        PasswordResetIssueRequest request = new PasswordResetIssueRequest();
        request.setEmail("user@example.com");
        request.setRealName("测试用户");
        when(passwordResetService.issueResetToken(anyString(), anyString(), anyString()))
                .thenReturn(new PasswordResetService.PasswordResetIssueResult(
                        PasswordResetService.PasswordResetStatus.ACCEPTED,
                        "token-123"));

        Result<PasswordResetTokenResponse> result = authController.issuePasswordResetToken(request, null);

        assertNotNull(result);
        assertEquals(200, result.getCode());
        assertNotNull(result.getData());
        assertEquals("token-123", result.getData().getResetToken());
    }

    @Test
    @DisplayName("申请重置令牌-限流返回429")
    void issuePasswordResetTokenShouldReturn429WhenRateLimited() {
        PasswordResetIssueRequest request = new PasswordResetIssueRequest();
        request.setEmail("user@example.com");
        request.setRealName("测试用户");
        when(passwordResetService.issueResetToken(anyString(), anyString(), anyString()))
                .thenReturn(new PasswordResetService.PasswordResetIssueResult(
                        PasswordResetService.PasswordResetStatus.RATE_LIMITED,
                        null));

        Result<PasswordResetTokenResponse> result = authController.issuePasswordResetToken(request, null);

        assertNotNull(result);
        assertEquals(429, result.getCode());
        assertEquals("请求过于频繁，请稍后再试", result.getMessage());
    }

    @Test
    @DisplayName("确认重置密码-成功受理")
    void confirmPasswordResetShouldReturnAccepted() {
        PasswordResetConfirmRequest request = new PasswordResetConfirmRequest();
        request.setResetToken("token-123");
        request.setNewPassword("newpass123");
        when(passwordResetService.confirmResetByToken(anyString(), anyString(), anyString()))
                .thenReturn(PasswordResetService.PasswordResetStatus.ACCEPTED);

        Result<Boolean> result = authController.confirmPasswordReset(request, null);

        assertNotNull(result);
        assertEquals(200, result.getCode());
        assertEquals("如令牌有效，密码重置已受理", result.getMessage());
    }

    @Test
    @DisplayName("确认重置密码-限流返回429")
    void confirmPasswordResetShouldReturn429WhenRateLimited() {
        PasswordResetConfirmRequest request = new PasswordResetConfirmRequest();
        request.setResetToken("token-123");
        request.setNewPassword("newpass123");
        when(passwordResetService.confirmResetByToken(anyString(), anyString(), anyString()))
                .thenReturn(PasswordResetService.PasswordResetStatus.RATE_LIMITED);

        Result<Boolean> result = authController.confirmPasswordReset(request, null);

        assertNotNull(result);
        assertEquals(429, result.getCode());
        assertEquals("请求过于频繁，请稍后再试", result.getMessage());
    }

    @Test
    @DisplayName("会话校验-非管理员禁止校验他人会话")
    void validateTokenShouldRejectNonAdminCrossUserCheck() {
        Result<Boolean> result = authController.validateToken(
                100L,
                "101",
                "student",
                "Bearer token-value");

        assertNotNull(result);
        assertEquals(403, result.getCode());
        assertEquals("权限不足，仅本人或管理员可校验会话", result.getMessage());
        verify(jwtUtil, never()).getJtiFromToken(anyString());
        verify(userSessionService, never()).isSessionValid(anyString());
    }

    @Test
    @DisplayName("会话校验-本人但会话失效应返回错误")
    void validateTokenShouldReturnErrorWhenSessionInvalid() {
        when(jwtUtil.getJtiFromToken("token-value")).thenReturn("jti-1");
        when(userSessionService.isSessionValid("jti-1")).thenReturn(false);

        Result<Boolean> result = authController.validateToken(
                100L,
                "100",
                "student",
                "Bearer token-value");

        assertNotNull(result);
        assertEquals(500, result.getCode());
        assertEquals("您的账号已在其他端登录或会话已失效，请重新登录", result.getMessage());
        verify(jwtUtil, times(1)).getJtiFromToken("token-value");
        verify(userSessionService, times(1)).isSessionValid("jti-1");
    }

    @Test
    @DisplayName("会话校验-管理员可校验任意用户会话")
    void validateTokenShouldAllowAdminWhenSessionValid() {
        when(jwtUtil.getJtiFromToken("token-value")).thenReturn("jti-2");
        when(userSessionService.isSessionValid("jti-2")).thenReturn(true);

        Result<Boolean> result = authController.validateToken(
                100L,
                "1",
                "admin",
                "Bearer token-value");

        assertNotNull(result);
        assertEquals(200, result.getCode());
        assertEquals("会话有效", result.getMessage());
        assertEquals(true, result.getData());
        verify(jwtUtil, times(1)).getJtiFromToken("token-value");
        verify(userSessionService, times(1)).isSessionValid("jti-2");
    }
}
