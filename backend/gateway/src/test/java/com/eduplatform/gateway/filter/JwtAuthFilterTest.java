package com.eduplatform.gateway.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * JwtAuthFilter 白名单与鉴权行为回归测试。
 */
@DisplayName("JwtAuthFilter 单元测试")
class JwtAuthFilterTest {

    private static final String JWT_SECRET = "0123456789abcdef0123456789abcdef";
    private static final String INTERNAL_TOKEN = "internal-token-for-test";

    @Test
    @DisplayName("旧重置密码接口不再白名单-未携带Token应返回401")
    void legacyResetPasswordPathShouldRequireToken() {
        JwtAuthFilter filter = new JwtAuthFilter(new ObjectMapper(), WebClient.builder());
        ReflectionTestUtils.setField(filter, "jwtSecret", JWT_SECRET);
        ReflectionTestUtils.setField(filter, "internalToken", INTERNAL_TOKEN);
        MockServerHttpRequest request = MockServerHttpRequest.post("/api/auth/reset-password").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        AtomicBoolean chainInvoked = new AtomicBoolean(false);
        GatewayFilterChain chain = ex -> {
            chainInvoked.set(true);
            return Mono.empty();
        };

        filter.filter(exchange, chain).block();

        assertFalse(chainInvoked.get());
        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
        String body = exchange.getResponse().getBodyAsString().block();
        assertTrue(body != null && body.contains("\"traceId\""));
    }

    @Test
    @DisplayName("新重置令牌申请接口保持白名单-允许匿名访问")
    void newPasswordResetRequestPathShouldBePublic() {
        JwtAuthFilter filter = new JwtAuthFilter(new ObjectMapper(), WebClient.builder());
        ReflectionTestUtils.setField(filter, "jwtSecret", JWT_SECRET);
        ReflectionTestUtils.setField(filter, "internalToken", INTERNAL_TOKEN);
        MockServerHttpRequest request = MockServerHttpRequest.post("/api/auth/password-reset/request").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        AtomicBoolean chainInvoked = new AtomicBoolean(false);
        GatewayFilterChain chain = ex -> {
            chainInvoked.set(true);
            return Mono.empty();
        };

        filter.filter(exchange, chain).block();

        assertTrue(chainInvoked.get());
        assertNull(exchange.getResponse().getStatusCode());
    }

    @Test
    @DisplayName("受保护接口-会话校验失败时应返回401")
    void protectedPathShouldRejectWhenSessionInvalid() {
        JwtAuthFilter filter = createFilterWithValidateTokenResponse("{\"code\":200,\"data\":false}");
        String token = buildToken(100L, "alice", "student");

        MockServerHttpRequest request = MockServerHttpRequest.get("/api/users/profile")
                .header("Authorization", "Bearer " + token)
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        AtomicBoolean chainInvoked = new AtomicBoolean(false);
        GatewayFilterChain chain = ex -> {
            chainInvoked.set(true);
            return Mono.empty();
        };

        filter.filter(exchange, chain).block();

        assertFalse(chainInvoked.get());
        assertEquals(HttpStatus.UNAUTHORIZED, exchange.getResponse().getStatusCode());
        String body = exchange.getResponse().getBodyAsString().block();
        assertTrue(body != null && body.contains("会话已失效"));
    }

    @Test
    @DisplayName("受保护接口-会话校验通过时应注入可信用户头")
    void protectedPathShouldInjectTrustedHeadersWhenSessionValid() {
        JwtAuthFilter filter = createFilterWithValidateTokenResponse("{\"code\":200,\"data\":true}");
        String token = buildToken(101L, "bob", "teacher");

        MockServerHttpRequest request = MockServerHttpRequest.get("/api/users/profile")
                .header("Authorization", "Bearer " + token)
                // 模拟客户端伪造同名头，网关应覆盖为可信值
                .header("X-User-Id", "999")
                .header("X-User-Name", "evil")
                .header("X-User-Role", "admin")
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        AtomicBoolean chainInvoked = new AtomicBoolean(false);
        AtomicReference<ServerHttpRequest> forwardedRequest = new AtomicReference<>();
        GatewayFilterChain chain = ex -> {
            chainInvoked.set(true);
            forwardedRequest.set(ex.getRequest());
            return Mono.empty();
        };

        filter.filter(exchange, chain).block();

        assertTrue(chainInvoked.get());
        assertNull(exchange.getResponse().getStatusCode());

        ServerHttpRequest requestAfterFilter = forwardedRequest.get();
        assertNotNull(requestAfterFilter);
        assertEquals("101", requestAfterFilter.getHeaders().getFirst("X-User-Id"));
        assertEquals("bob", requestAfterFilter.getHeaders().getFirst("X-User-Name"));
        assertEquals("teacher", requestAfterFilter.getHeaders().getFirst("X-User-Role"));
        assertTrue(StringUtils.hasText(requestAfterFilter.getHeaders().getFirst("X-User-Signature")));
    }

    /**
     * 构造带固定会话校验响应的过滤器，避免单测依赖真实 user-service。
     */
    private JwtAuthFilter createFilterWithValidateTokenResponse(String responseBody) {
        ExchangeFunction exchangeFunction = clientRequest -> Mono.just(
                ClientResponse.create(HttpStatus.OK)
                        .header("Content-Type", "application/json")
                        .body(responseBody)
                        .build());

        WebClient.Builder webClientBuilder = WebClient.builder().exchangeFunction(exchangeFunction);
        JwtAuthFilter filter = new JwtAuthFilter(new ObjectMapper(), webClientBuilder);
        ReflectionTestUtils.setField(filter, "jwtSecret", JWT_SECRET);
        ReflectionTestUtils.setField(filter, "internalToken", INTERNAL_TOKEN);
        return filter;
    }

    /**
     * 生成用于网关鉴权测试的 JWT。
     */
    private String buildToken(Long userId, String username, String role) {
        SecretKey key = Keys.hmacShaKeyFor(JWT_SECRET.getBytes(StandardCharsets.UTF_8));
        return Jwts.builder()
                .claim("id", userId)
                .claim("username", username)
                .claim("role", role)
                .signWith(key)
                .compact();
    }
}
