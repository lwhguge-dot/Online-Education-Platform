# Gateway Security Hardening Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use compose:subagent (recommended) or compose:execute to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fix 17 security issues identified in the gateway security audit, prioritized by severity.

**Architecture:** Add centralized InternalTokenFilter in common module, harden JwtAuthFilter and RateLimitFilter, restrict Actuator/CORS exposure.

**Tech Stack:** Java 21, Spring Boot 3.2, Spring WebFlux, Caffeine, Redis

---

### Task 1: Common module - InternalTokenFilter

**Covers:** R17 (集中式内部令牌验证), R19 (常量时间比较)

**Files:**
- Create: `backend/common/src/main/java/com/eduplatform/common/security/InternalTokenFilter.java`
- Create: `backend/common/src/test/java/com/eduplatform/common/security/InternalTokenFilterTest.java`

- [ ] **Step 1: Write the failing test**

```java
package com.eduplatform.common.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@DisplayName("InternalTokenFilter 单元测试")
class InternalTokenFilterTest {

    @Test
    @DisplayName("无内部令牌且无用户头-放行")
    void shouldPassWhenNoInternalTokenAndNoUserHeaders() throws Exception {
        InternalTokenFilter filter = new InternalTokenFilter();
        ReflectionTestUtils.setField(filter, "internalToken", "test-token");

        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("X-Internal-Token")).thenReturn(null);
        when(request.getHeader("X-User-Id")).thenReturn(null);
        when(request.getRequestURI()).thenReturn("/api/test");
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");

        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);

        filter.doFilterInternal(request, response, chain);

        verify(chain).doFilter(request, response);
    }

    @Test
    @DisplayName("有效内部令牌-放行")
    void shouldPassWhenValidInternalToken() throws Exception {
        InternalTokenFilter filter = new InternalTokenFilter();
        ReflectionTestUtils.setField(filter, "internalToken", "test-token");

        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("X-Internal-Token")).thenReturn("test-token");
        when(request.getRequestURI()).thenReturn("/api/test");
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");

        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);

        filter.doFilterInternal(request, response, chain);

        verify(chain).doFilter(request, response);
    }

    @Test
    @DisplayName("无效内部令牌-拒绝")
    void shouldRejectWhenInvalidInternalToken() throws Exception {
        InternalTokenFilter filter = new InternalTokenFilter();
        ReflectionTestUtils.setField(filter, "internalToken", "test-token");

        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("X-Internal-Token")).thenReturn("wrong-token");
        when(request.getRequestURI()).thenReturn("/api/test");
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");

        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);

        filter.doFilterInternal(request, response, chain);

        verify(response).setStatus(403);
        verify(chain, never()).doFilter(request, response);
    }

    @Test
    @DisplayName("actuator路径-放行")
    void shouldPassForActuatorPaths() throws Exception {
        InternalTokenFilter filter = new InternalTokenFilter();
        ReflectionTestUtils.setField(filter, "internalToken", "test-token");

        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn("/actuator/health");
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");

        HttpServletResponse response = mock(HttpServletResponse.class);
        FilterChain chain = mock(FilterChain.class);

        filter.doFilterInternal(request, response, chain);

        verify(chain).doFilter(request, response);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn test -pl backend/common -Dtest=InternalTokenFilterTest -s settings.xml`
Expected: FAIL - class not found

- [ ] **Step 3: Write minimal implementation**

```java
package com.eduplatform.common.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 3)
public class InternalTokenFilter extends OncePerRequestFilter {

    private static final String HEADER_INTERNAL_TOKEN = "X-Internal-Token";

    @Value("${security.internal-token:}")
    private String internalToken;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String uri = request.getRequestURI();

        if (uri != null && (uri.startsWith("/actuator") || uri.startsWith("/api/auth"))) {
            filterChain.doFilter(request, response);
            return;
        }

        String requestToken = request.getHeader(HEADER_INTERNAL_TOKEN);
        if (!StringUtils.hasText(requestToken)) {
            filterChain.doFilter(request, response);
            return;
        }

        if (!StringUtils.hasText(internalToken)) {
            log.error("服务未配置 security.internal-token");
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":500,\"message\":\"服务内部配置错误\",\"data\":null}");
            return;
        }

        if (!MessageDigest.isEqual(
                internalToken.getBytes(StandardCharsets.UTF_8),
                requestToken.getBytes(StandardCharsets.UTF_8))) {
            log.warn("内部令牌校验失败: uri={}, remoteAddr={}", uri, request.getRemoteAddr());
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":403,\"message\":\"内部令牌无效\",\"data\":null}");
            return;
        }

        filterChain.doFilter(request, response);
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn test -pl backend/common -Dtest=InternalTokenFilterTest -s settings.xml`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add backend/common/src/main/java/com/eduplatform/common/security/InternalTokenFilter.java backend/common/src/test/java/com/eduplatform/common/security/InternalTokenFilterTest.java
git commit -m "feat(security): add centralized InternalTokenFilter for service-to-service auth"
```

---

### Task 2: JwtAuthFilter hardening

**Covers:** R2 (AntPathMatcher性能), R3 (PUBLIC_PATH_PATTERNS精度), R5 (validateSession错误处理), R6 (死代码清理), R19 (内部令牌常量时间比较)

**Files:**
- Modify: `backend/gateway/src/main/java/com/eduplatform/gateway/filter/JwtAuthFilter.java`
- Modify: `backend/gateway/src/test/java/com/eduplatform/gateway/filter/JwtAuthFilterTest.java`

- [ ] **Step 1: Write failing tests for new behavior**

```java
// Add to JwtAuthFilterTest.java

@Test
@DisplayName("/api/courses/published 应该公开访问")
void publishedCoursesPathShouldBePublic() {
    JwtAuthFilter filter = createFilterWithValidateTokenResponse("{\"code\":200,\"data\":true}");
    MockServerHttpRequest request = MockServerHttpRequest.get("/api/courses/published").build();
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
@DisplayName("/api/courses/123 应该公开访问（单层通配符）")
void singleLevelCoursesPathShouldBePublic() {
    JwtAuthFilter filter = createFilterWithValidateTokenResponse("{\"code\":200,\"data\":true}");
    MockServerHttpRequest request = MockServerHttpRequest.get("/api/courses/123").build();
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
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn test -pl backend/gateway -Dtest=JwtAuthFilterTest -s settings.xml`
Expected: FAIL (tests pass with current code but we need to verify the pattern works)

- [ ] **Step 3: Implement JwtAuthFilter changes**

```java
// In JwtAuthFilter.java, make these changes:

// 1. Add static AntPathMatcher as class field (replaces per-request instantiation)
private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();

// 2. Remove unused validateToken method (lines 267-274)

// 3. Change isPublicPath to use static matcher
private boolean isPublicPath(String path) {
    if (PUBLIC_PATHS.contains(path)) {
        return true;
    }
    for (String pattern : PUBLIC_PATH_PATTERNS) {
        if (PATH_MATCHER.match(pattern, path)) {
            return true;
        }
    }
    return false;
}

// 4. Change isAdminPath to use static matcher
private boolean isAdminPath(String path) {
    for (String pattern : adminPaths) {
        if (PATH_MATCHER.match(pattern, path)) {
            return true;
        }
    }
    return false;
}

// 5. Fix internalToken comparison to use constant-time comparison
private boolean hasValidInternalToken(ServerWebExchange exchange) {
    if (!StringUtils.hasText(internalToken)) {
        log.error("网关未配置 security.internal-token，内部接口保护不可用");
        return false;
    }
    String requestToken = exchange.getRequest().getHeaders().getFirst(HEADER_INTERNAL_TOKEN);
    if (!StringUtils.hasText(requestToken)) {
        return false;
    }
    return MessageDigest.isEqual(
            internalToken.getBytes(StandardCharsets.UTF_8),
            requestToken.getBytes(StandardCharsets.UTF_8));
}

// 6. Add import for MessageDigest if not present
import java.security.MessageDigest;

// 7. Improve validateSession error handling
private Mono<Boolean> validateSession(
        String userId,
        String username,
        String role,
        String token,
        long ts,
        String signature) {
    return userServiceWebClient.get()
            .uri("http://user-service/api/auth/validate-token/{userId}", userId)
            .header(HEADER_AUTHORIZATION, "Bearer " + token)
            .header(HEADER_USER_ID, userId)
            .header(HEADER_USER_NAME, username)
            .header(HEADER_USER_ROLE, role)
            .header(HEADER_USER_TS, String.valueOf(ts))
            .header(HEADER_USER_SIGNATURE, signature)
            .retrieve()
            .bodyToMono(Map.class)
            .map(this::isValidateTokenResponseSuccess)
            .onErrorResume(e -> {
                log.warn("网关会话校验失败: userId={}, error={}", userId, e.getMessage());
                return Mono.just(false);
            })
            .timeout(Duration.ofSeconds(3))
            .onErrorResume(e -> {
                log.warn("网关会话校验超时: userId={}", userId);
                return Mono.just(false);
            });
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn test -pl backend/gateway -Dtest=JwtAuthFilterTest -s settings.xml`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add backend/gateway/src/main/java/com/eduplatform/gateway/filter/JwtAuthFilter.java backend/gateway/src/test/java/com/eduplatform/gateway/filter/JwtAuthFilterTest.java
git commit -m "fix(security): harden JwtAuthFilter with singleton matcher, constant-time comparison, timeout"
```

---

### Task 3: RateLimitFilter user-level limiting

**Covers:** R4 (登录限流), R8 (用户级限流), R9 (unknown桶处理)

**Files:**
- Modify: `backend/gateway/src/main/java/com/eduplatform/gateway/filter/RateLimitFilter.java`
- Modify: `backend/gateway/src/test/java/com/eduplatform/gateway/filter/RateLimitFilterTest.java`

- [ ] **Step 1: Write failing tests**

```java
// Add to RateLimitFilterTest.java

@Test
@DisplayName("认证请求应使用userId作为限流键")
void shouldUseUserIdForAuthenticatedRequests() {
    RateLimitFilter filter = new RateLimitFilter(100.0, 10, 1000, false);
    MockServerHttpRequest request = MockServerHttpRequest.get("/api/test")
            .header("X-User-Id", "user-123")
            .remoteAddress(new InetSocketAddress("10.10.10.10", 12345))
            .build();
    MockServerWebExchange exchange = MockServerWebExchange.from(request);

    String clientKey = filter.resolveClientKey(exchange);

    assertEquals("user-123", clientKey);
}

@Test
@DisplayName("unknown地址应使用受限的限流值")
void shouldUseRestrictedLimitForUnknownAddress() {
    RateLimitFilter filter = new RateLimitFilter(100.0, 10, 1000, false);
    MockServerHttpRequest request = MockServerHttpRequest.get("/api/test").build();
    MockServerWebExchange exchange = MockServerWebExchange.from(request);

    String clientKey = filter.resolveClientKey(exchange);

    assertEquals("unknown", clientKey);
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn test -pl backend/gateway -Dtest=RateLimitFilterTest -s settings.xml`
Expected: FAIL

- [ ] **Step 3: Implement changes**

```java
// In RateLimitFilter.java:

// 1. Add user-level rate limiting in resolveClientKey
String resolveClientKey(ServerWebExchange exchange) {
    // Authenticated requests use userId for rate limiting
    String userId = exchange.getRequest().getHeaders().getFirst("X-User-Id");
    if (StringUtils.hasText(userId)) {
        return "user:" + userId;
    }

    String remoteIp = resolveRemoteAddress(exchange);
    if (shouldUseForwardedHeaders(remoteIp)) {
        String xForwardedFor = exchange.getRequest().getHeaders().getFirst("X-Forwarded-For");
        if (StringUtils.hasText(xForwardedFor)) {
            String forwardedIp = xForwardedFor.split(",")[0].trim();
            if (StringUtils.hasText(forwardedIp)) {
                return forwardedIp;
            }
        }
        String xRealIp = exchange.getRequest().getHeaders().getFirst("X-Real-IP");
        if (StringUtils.hasText(xRealIp)) {
            return xRealIp.trim();
        }
    }
    if (StringUtils.hasText(remoteIp)) {
        return remoteIp;
    }
    return "unknown";
}

// 2. Add restricted limit for unknown addresses in allowByLocal
private boolean allowByLocal(String clientIp) {
    double effectivePermits = "unknown".equals(clientIp) ? Math.min(permitsPerSecond, 10) : permitsPerSecond;
    RateLimiter limiter = limiterCache.get(clientIp, key -> RateLimiter.create(effectivePermits));
    return limiter != null && limiter.tryAcquire();
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn test -pl backend/gateway -Dtest=RateLimitFilterTest -s settings.xml`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add backend/gateway/src/main/java/com/eduplatform/gateway/filter/RateLimitFilter.java backend/gateway/src/test/java/com/eduplatform/gateway/filter/RateLimitFilterTest.java
git commit -m "feat(security): add user-level rate limiting and restrict unknown IP bucket"
```

---

### Task 4: application.yml security hardening

**Covers:** R12 (Actuator暴露), R13 (CORS localhost), R16 (X-Internal-Token头)

**Files:**
- Modify: `backend/gateway/src/main/resources/application.yml`

- [ ] **Step 1: Make changes to application.yml**

```yaml
# In application.yml, update:

# 1. Restrict Actuator endpoints (remove gateway endpoint)
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics
  endpoint:
    health:
      show-details: when_authorized
  info:
    env:
      enabled: true

# 2. Remove X-Internal-Token from CORS allowedHeaders
spring:
  cloud:
    gateway:
      globalcors:
        cors-configurations:
          '[/**]':
            allowedOrigins: ${CORS_ALLOWED_ORIGINS:http://localhost:3000,http://localhost:5173}
            allowedMethods:
              - GET
              - POST
              - PUT
              - DELETE
              - OPTIONS
            allowedHeaders:
              - "Content-Type"
              - "Authorization"
              - "X-Requested-With"
              - "X-Trace-Id"
            allowCredentials: true
```

- [ ] **Step 2: Verify changes don't break compilation**

Run: `mvn compile -pl backend/gateway -s settings.xml`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add backend/gateway/src/main/resources/application.yml
git commit -m "fix(security): restrict Actuator exposure and tighten CORS configuration"
```

---

### Task 5: WebClient timeout configuration

**Covers:** R7 (WebClient超时)

**Files:**
- Modify: `backend/gateway/src/main/java/com/eduplatform/gateway/config/WebClientConfig.java`

- [ ] **Step 1: Add read timeout**

```java
// In WebClientConfig.java, update the HttpClient configuration:

HttpClient httpClient = HttpClient.create()
        .responseTimeout(Duration.ofSeconds(3))
        .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 2000);
```

Note: The connect timeout is already set to 2000ms and response timeout to 3s. This is acceptable.

- [ ] **Step 2: Verify changes don't break compilation**

Run: `mvn compile -pl backend/gateway -s settings.xml`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add backend/gateway/src/main/java/com/eduplatform/gateway/config/WebClientConfig.java
git commit -m "chore(security): verify WebClient timeout configuration is adequate"
```

---

### Task 6: Full test verification

**Covers:** All tasks

- [ ] **Step 1: Run all gateway tests**

Run: `mvn test -pl backend/gateway -s settings.xml`
Expected: All tests pass

- [ ] **Step 2: Run common module tests**

Run: `mvn test -pl backend/common -s settings.xml`
Expected: All tests pass

- [ ] **Step 3: Full backend compile check**

Run: `mvn clean compile -s settings.xml`
Expected: BUILD SUCCESS
