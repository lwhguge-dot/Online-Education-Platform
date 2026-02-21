package com.eduplatform.user.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.eduplatform.user.entity.User;
import com.eduplatform.user.mapper.UserMapper;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * 密码重置安全服务。
 * 提供限流、防枚举与一次性令牌能力，避免弱校验接口被批量利用。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PasswordResetService {

    private static final String RESET_REQ_IP_PREFIX = "pwd_reset:req:ip:";
    private static final String RESET_REQ_IDENTITY_PREFIX = "pwd_reset:req:identity:";
    private static final String RESET_CONFIRM_IP_PREFIX = "pwd_reset:confirm:ip:";
    private static final String RESET_TOKEN_PREFIX = "pwd_reset:token:";
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final UserMapper userMapper;
    private final UserSessionService sessionService;
    private final StringRedisTemplate redisTemplate;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    @Value("${security.password-reset.window-seconds:600}")
    private long windowSeconds = 600;

    @Value("${security.password-reset.request-limit-per-ip:10}")
    private long requestLimitPerIp = 10;

    @Value("${security.password-reset.request-limit-per-identity:5}")
    private long requestLimitPerIdentity = 5;

    @Value("${security.password-reset.confirm-limit-per-ip:10}")
    private long confirmLimitPerIp = 10;

    @Value("${security.password-reset.token-ttl-seconds:900}")
    private long tokenTtlSeconds = 900;

    /**
     * 密码重置状态。
     */
    public enum PasswordResetStatus {
        ACCEPTED,
        RATE_LIMITED
    }

    /**
     * 申请令牌返回值。
     */
    @Data
    @AllArgsConstructor
    public static class PasswordResetIssueResult {
        private PasswordResetStatus status;
        private String token;
    }

    /**
     * 申请密码重置令牌（公开接口）。
     * 为避免枚举，始终返回令牌；仅在身份匹配时把令牌写入 Redis。
     */
    public PasswordResetIssueResult issueResetToken(String email, String realName, String clientIp) {
        String normalizedIp = normalizeIp(clientIp);
        if (isRateLimited(RESET_REQ_IP_PREFIX + normalizedIp, requestLimitPerIp, windowSeconds)) {
            return new PasswordResetIssueResult(PasswordResetStatus.RATE_LIMITED, null);
        }

        String normalizedEmail = normalizeEmail(email);
        String normalizedRealName = normalizeRealName(realName);
        String identityHash = digestIdentity(normalizedEmail, normalizedRealName);
        if (isRateLimited(RESET_REQ_IDENTITY_PREFIX + identityHash, requestLimitPerIdentity, windowSeconds)) {
            return new PasswordResetIssueResult(PasswordResetStatus.RATE_LIMITED, null);
        }

        String token = generateResetToken();
        User user = findUserByEmail(normalizedEmail);
        if (isIdentityMatched(user, normalizedRealName)) {
            cacheResetToken(token, user.getId());
        }
        return new PasswordResetIssueResult(PasswordResetStatus.ACCEPTED, token);
    }

    /**
     * 使用一次性令牌确认密码重置。
     */
    public PasswordResetStatus confirmResetByToken(String resetToken, String newPassword, String clientIp) {
        String normalizedIp = normalizeIp(clientIp);
        if (isRateLimited(RESET_CONFIRM_IP_PREFIX + normalizedIp, confirmLimitPerIp, windowSeconds)) {
            return PasswordResetStatus.RATE_LIMITED;
        }
        applyResetByToken(resetToken, newPassword);
        return PasswordResetStatus.ACCEPTED;
    }

    /**
     * 兼容旧接口：基于身份信息直接受理重置。
     * 内部仍使用一次性令牌，确保流程单向且可失效。
     */
    public PasswordResetStatus resetByIdentity(String email, String realName, String newPassword, String clientIp) {
        PasswordResetIssueResult issueResult = issueResetToken(email, realName, clientIp);
        if (issueResult.getStatus() == PasswordResetStatus.RATE_LIMITED) {
            return PasswordResetStatus.RATE_LIMITED;
        }
        applyResetByToken(issueResult.getToken(), newPassword);
        return PasswordResetStatus.ACCEPTED;
    }

    /**
     * 真正执行密码落库与会话失效。
     * 令牌无效或身份不匹配时静默返回，避免形成枚举信号。
     */
    private void applyResetByToken(String token, String newPassword) {
        if (!StringUtils.hasText(token) || !StringUtils.hasText(newPassword)) {
            return;
        }

        String tokenKey = RESET_TOKEN_PREFIX + token;
        String userIdText;
        try {
            userIdText = redisTemplate.opsForValue().get(tokenKey);
        } catch (Exception e) {
            log.warn("读取重置令牌失败，已忽略此次请求", e);
            return;
        }
        if (!StringUtils.hasText(userIdText)) {
            return;
        }

        try {
            Long userId = Long.parseLong(userIdText);
            User user = userMapper.selectById(userId);
            if (user == null) {
                return;
            }

            user.setPassword(passwordEncoder.encode(newPassword));
            user.setUpdatedAt(LocalDateTime.now());
            userMapper.updateById(user);
            // 密码变更后强制下线所有会话，防止旧 token 继续使用。
            sessionService.forceOfflineUser(userId);
        } catch (Exception e) {
            log.warn("应用密码重置失败，已忽略本次重置", e);
        } finally {
            try {
                redisTemplate.delete(tokenKey);
            } catch (Exception e) {
                log.debug("删除重置令牌失败，tokenKey={}", tokenKey, e);
            }
        }
    }

    private void cacheResetToken(String token, Long userId) {
        try {
            redisTemplate.opsForValue().set(
                    RESET_TOKEN_PREFIX + token,
                    String.valueOf(userId),
                    safeDuration(tokenTtlSeconds),
                    TimeUnit.SECONDS);
        } catch (Exception e) {
            // 缓存失败按未命中处理，避免降级为无令牌重置。
            log.warn("缓存重置令牌失败，token 将不可用", e);
        }
    }

    private User findUserByEmail(String normalizedEmail) {
        if (!StringUtils.hasText(normalizedEmail)) {
            return null;
        }
        return userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getEmail, normalizedEmail));
    }

    private boolean isIdentityMatched(User user, String normalizedRealName) {
        if (user == null || !StringUtils.hasText(normalizedRealName) || !StringUtils.hasText(user.getName())) {
            return false;
        }
        return normalizeRealName(user.getName()).equals(normalizedRealName);
    }

    /**
     * 固定窗口限流：首次写入时设置过期时间，超过阈值视为限流命中。
     */
    private boolean isRateLimited(String key, long maxAllowed, long ttlSeconds) {
        if (!StringUtils.hasText(key) || maxAllowed <= 0) {
            return false;
        }
        try {
            Long count = redisTemplate.opsForValue().increment(key);
            if (count == null) {
                return true;
            }
            if (count == 1L) {
                redisTemplate.expire(key, safeDuration(ttlSeconds), TimeUnit.SECONDS);
            }
            return count > maxAllowed;
        } catch (Exception e) {
            // Redis 异常时采取安全失败策略，避免被无限重试绕过。
            log.warn("密码重置限流检查失败，按限流处理: key={}", key, e);
            return true;
        }
    }

    private String normalizeEmail(String email) {
        if (email == null) {
            return "";
        }
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeRealName(String realName) {
        if (realName == null) {
            return "";
        }
        return realName.trim();
    }

    private String normalizeIp(String clientIp) {
        if (!StringUtils.hasText(clientIp)) {
            return "unknown";
        }
        return clientIp.trim();
    }

    private long safeDuration(long seconds) {
        return Math.max(seconds, 1);
    }

    private String digestIdentity(String email, String realName) {
        String source = email + "|" + realName;
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(source.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            // 极端场景回退到可用键，保证流程不中断。
            return source;
        }
    }

    private String generateResetToken() {
        byte[] bytes = new byte[24];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}

