package com.eduplatform.user;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import static org.junit.jupiter.api.Assertions.*;

/**
 * BCrypt 密码加密工具测试
 *
 * 验证密码加密和校验的核心安全功能
 */
@DisplayName("密码加密测试")
class PasswordTest {

    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    @Test
    @DisplayName("加密后的密码应与原文匹配")
    void encodedPasswordShouldMatch() {
        String password = "123456";
        String hash = encoder.encode(password);

        assertNotNull(hash);
        assertTrue(encoder.matches(password, hash));
    }

    @Test
    @DisplayName("不同密码不应匹配")
    void differentPasswordShouldNotMatch() {
        String hash = encoder.encode("123456");

        assertFalse(encoder.matches("wrong_password", hash));
    }

    @Test
    @DisplayName("每次加密生成不同的哈希值 (盐值随机)")
    void encodeShouldProduceDifferentHashes() {
        String password = "123456";
        String hash1 = encoder.encode(password);
        String hash2 = encoder.encode(password);

        assertNotEquals(hash1, hash2);
        // 但都应该能匹配原密码
        assertTrue(encoder.matches(password, hash1));
        assertTrue(encoder.matches(password, hash2));
    }

    @Test
    @DisplayName("空密码不应匹配任何哈希")
    void emptyPasswordShouldNotMatch() {
        String hash = encoder.encode("123456");

        assertFalse(encoder.matches("", hash));
    }
}
