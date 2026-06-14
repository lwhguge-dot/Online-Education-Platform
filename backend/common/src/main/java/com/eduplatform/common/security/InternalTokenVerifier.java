package com.eduplatform.common.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * 内部调用令牌（X-Internal-Token）常量时间比较器。
 *
 * <p>设计意图：
 * <ul>
 *   <li>统一所有 Controller / 内部接口对内部令牌的校验入口，避免各处使用 {@code String.equals}
 *       触发计时旁路攻击。</li>
 *   <li>使用 {@link MessageDigest#isEqual} 保证恒定时间比较，与网关、InternalTokenFilter 保持一致。</li>
 *   <li>封装空值与空串兜底，调用方无需重复判断。</li>
 * </ul>
 *
 * <p>使用方式：Controller 通过构造器注入本 Bean，调用 {@link #isValid(String)} 校验请求头中的令牌。
 */
@Component
public class InternalTokenVerifier {

    private final String internalToken;

    public InternalTokenVerifier(@Value("${security.internal-token:}") String internalToken) {
        this.internalToken = internalToken;
    }

    /**
     * 校验请求方传入的内部令牌是否与服务端配置一致。
     *
     * <p>使用常量时间比较，避免按字节提前返回造成计时差异。
     *
     * @param requestToken 请求头 {@code X-Internal-Token} 的值，可为 null
     * @return true 仅当服务端配置非空、请求非空且二者字节相等
     */
    public boolean isValid(String requestToken) {
        if (!StringUtils.hasText(internalToken) || !StringUtils.hasText(requestToken)) {
            return false;
        }
        return MessageDigest.isEqual(
                requestToken.getBytes(StandardCharsets.UTF_8),
                internalToken.getBytes(StandardCharsets.UTF_8));
    }
}
