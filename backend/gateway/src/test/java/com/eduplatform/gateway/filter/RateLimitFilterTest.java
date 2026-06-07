package com.eduplatform.gateway.filter;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.ReactiveValueOperations;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.net.InetSocketAddress;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * RateLimitFilter 客户端标识提取测试。
 */
@DisplayName("RateLimitFilter 单元测试")
class RateLimitFilterTest {

    @Test
    @DisplayName("默认不信任转发头-使用 remoteAddress 作为限流键")
    void shouldUseRemoteAddressWhenForwardedHeadersNotTrusted() {
        RateLimitFilter filter = new RateLimitFilter(100.0, 10, 1000, false);
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/test")
                .header("X-Forwarded-For", "198.51.100.10")
                .header("X-Real-IP", "198.51.100.11")
                .remoteAddress(new InetSocketAddress("10.10.10.10", 12345))
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        String clientKey = filter.resolveClientKey(exchange);

        assertEquals("10.10.10.10", clientKey);
    }

    @Test
    @DisplayName("开启转发头但未配置受信代理-仍使用 remoteAddress 作为限流键")
    void shouldIgnoreForwardedHeadersWhenNoTrustedProxyConfigured() {
        RateLimitFilter filter = new RateLimitFilter(100.0, 10, 1000, true);
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/test")
                .header("X-Forwarded-For", "198.51.100.10, 198.51.100.11")
                .header("X-Real-IP", "198.51.100.12")
                .remoteAddress(new InetSocketAddress("10.10.10.10", 12345))
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        String clientKey = filter.resolveClientKey(exchange);

        assertEquals("10.10.10.10", clientKey);
    }

    @Test
    @DisplayName("显式信任转发头且来源为受信代理-优先使用 X-Forwarded-For 首个 IP")
    void shouldUseForwardedForWhenTrusted() {
        RateLimitFilter filter = new RateLimitFilter(
                null, 100.0, 10, 1000, true, 1, false, Set.of("10.10.10.10"));
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/test")
                .header("X-Forwarded-For", "198.51.100.10, 198.51.100.11")
                .remoteAddress(new InetSocketAddress("10.10.10.10", 12345))
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        String clientKey = filter.resolveClientKey(exchange);

        assertEquals("198.51.100.10", clientKey);
    }

    @Test
    @DisplayName("显式信任转发头且来源为受信代理-缺少 XFF 时回退 X-Real-IP")
    void shouldUseXRealIpWhenForwardedForMissingAndProxyTrusted() {
        RateLimitFilter filter = new RateLimitFilter(
                null, 100.0, 10, 1000, true, 1, false, Set.of("10.10.10.10"));
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/test")
                .header("X-Real-IP", "198.51.100.12")
                .remoteAddress(new InetSocketAddress("10.10.10.10", 12345))
                .build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        String clientKey = filter.resolveClientKey(exchange);

        assertEquals("198.51.100.12", clientKey);
    }

    @Test
    @DisplayName("无可用地址信息-回退 unknown")
    void shouldFallbackToUnknownWhenNoAddressAvailable() {
        RateLimitFilter filter = new RateLimitFilter(100.0, 10, 1000, false);
        MockServerHttpRequest request = MockServerHttpRequest.get("/api/test").build();
        MockServerWebExchange exchange = MockServerWebExchange.from(request);

        String clientKey = filter.resolveClientKey(exchange);

        assertEquals("unknown", clientKey);
    }

    @Test
    @DisplayName("Redis窗口限流-同一窗口超限时返回429")
    void shouldRejectWhenRedisCounterExceedsLimit() {
        ReactiveStringRedisTemplate redisTemplate = mock(ReactiveStringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ReactiveValueOperations<String, String> valueOperations = mock(ReactiveValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.increment(anyString())).thenReturn(Mono.just(1L), Mono.just(2L));
        when(redisTemplate.expire(anyString(), any(Duration.class))).thenReturn(Mono.just(true));

        RateLimitFilter filter = new RateLimitFilter(redisTemplate, 1.0, 10, 1000, false, 1, true);

        MockServerWebExchange firstExchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/test")
                        .remoteAddress(new InetSocketAddress("10.10.10.10", 12345))
                        .build());
        AtomicBoolean firstInvoked = new AtomicBoolean(false);
        filter.filter(firstExchange, ex -> {
            firstInvoked.set(true);
            return Mono.empty();
        }).block();

        assertTrue(firstInvoked.get());
        assertNull(firstExchange.getResponse().getStatusCode());

        MockServerWebExchange secondExchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/api/test")
                        .remoteAddress(new InetSocketAddress("10.10.10.10", 12345))
                        .build());
        AtomicBoolean secondInvoked = new AtomicBoolean(false);
        filter.filter(secondExchange, ex -> {
            secondInvoked.set(true);
            return Mono.empty();
        }).block();

        assertFalse(secondInvoked.get());
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, secondExchange.getResponse().getStatusCode());
    }
}
