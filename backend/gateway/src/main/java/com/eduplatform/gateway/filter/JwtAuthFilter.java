package com.eduplatform.gateway.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.client.loadbalancer.LoadBalanced;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HashMap;
import java.util.Base64;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 网关 JWT 鉴权过滤器。
 * 设计意图：
 * 1. 在网关统一完成身份校验，避免各微服务重复实现鉴权逻辑。
 * 2. 由网关注入可信用户头，屏蔽前端伪造的同名请求头。
 * 3. 对高危内部接口（如 cascade）执行二次防护，阻止外部直接访问。
 */
@Slf4j
@Component
public class JwtAuthFilter implements GlobalFilter, Ordered {

    private static final String HEADER_AUTHORIZATION = "Authorization";
    private static final String HEADER_INTERNAL_TOKEN = "X-Internal-Token";
    private static final String HEADER_USER_ID = "X-User-Id";
    private static final String HEADER_USER_NAME = "X-User-Name";
    private static final String HEADER_USER_ROLE = "X-User-Role";
    private static final String HEADER_USER_TS = "X-User-Ts";
    private static final String HEADER_USER_SIGNATURE = "X-User-Signature";
    private static final String HEADER_TRACE_ID = "X-Trace-Id";

    /**
     * 白名单接口：无需登录即可访问。
     */
    private static final Set<String> PUBLIC_PATHS = Set.of(
            "/api/auth/login",
            "/api/auth/register",
            "/api/auth/password-reset/request",
            "/api/auth/password-reset/confirm",
            "/api/auth/health",
            "/api/courses/published",
            "/api/courses/stats",
            "/api/courses/stats/by-subject"
    );

    private static final Set<String> PUBLIC_PATH_PATTERNS = Set.of(
            "/api/courses/*"
    );

    /**
     * 管理员专属路径：仅允许 role=admin 的请求通过。
     * 使用 Ant 风格路径模式，支持通配符匹配。
     */
    private static final Set<String> ADMIN_PATHS = Set.of(
            "/api/users/list",
            "/api/users/*/status",
            "/api/users/export",
            "/api/users/stats",
            "/api/users/online-status",
            "/api/courses/reviewing",
            "/api/courses/*/audit",
            "/api/courses/*/offline",
            "/api/courses/batch-status",
            "/api/courses/export",
            "/api/audit-logs/**",
            "/api/announcements",
            "/api/stats/admin/**"
    );

    private final ObjectMapper objectMapper;
    private final WebClient userServiceWebClient;

    @Value("${jwt.secret}")
    private String jwtSecret;

    @Value("${security.internal-token}")
    private String internalToken;

    public JwtAuthFilter(ObjectMapper objectMapper, @LoadBalanced WebClient.Builder webClientBuilder) {
        this.objectMapper = objectMapper;
        this.userServiceWebClient = webClientBuilder.build();
    }

    @PostConstruct
    public void validateSecret() {
        if (jwtSecret == null || jwtSecret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("JWT_SECRET must be at least 32 bytes for HS256");
        }
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();

        // 非 API 请求先放行，避免影响静态资源和站点路由。
        // WebSocket 请求直接放行，由 user-service 的 WebSocketHandler 自行处理 AUTH 握手。
        if (!path.startsWith("/api/")) {
            return chain.filter(exchange);
        }

        // 对高危内部接口执行专门拦截，要求携带内部令牌。
        if (isInternalCascadePath(path) && !hasValidInternalToken(exchange)) {
            return writeError(exchange, HttpStatus.FORBIDDEN, 403, "禁止外部访问内部级联接口");
        }

        // 白名单接口直接放行。
        if (isPublicPath(path)) {
            return chain.filter(exchange);
        }

        String authHeader = exchange.getRequest().getHeaders().getFirst(HEADER_AUTHORIZATION);
        if (!StringUtils.hasText(authHeader) || !authHeader.startsWith("Bearer ")) {
            return writeError(exchange, HttpStatus.UNAUTHORIZED, 401, "身份认证失败：缺少 Bearer Token");
        }

        String token = authHeader.substring(7);
        Claims claims;
        try {
            claims = parseClaims(token);
        } catch (Exception e) {
            log.warn("网关鉴权失败，token 解析异常: path={}, error={}", path, e.getMessage());
            return writeError(exchange, HttpStatus.UNAUTHORIZED, 401, "身份认证失败：token 无效或已过期");
        }

        Object userIdObj = claims.get("id");
        String username = String.valueOf(claims.get("username"));
        String role = String.valueOf(claims.get("role"));

        if (userIdObj == null || "null".equals(username) || "null".equals(role)) {
            return writeError(exchange, HttpStatus.UNAUTHORIZED, 401, "身份认证失败：token 载荷不完整");
        }

        String userId = String.valueOf(userIdObj);

        // 管理员路径角色校验：非管理员禁止访问管理专属接口
        if (isAdminPath(path) && !"admin".equalsIgnoreCase(role)) {
            return writeError(exchange, HttpStatus.FORBIDDEN, 403, "权限不足：仅管理员可访问此接口");
        }

        // 构造可信请求头：先移除同名头，避免被客户端伪造；再注入网关解析结果。
        if (!StringUtils.hasText(internalToken)) {
            log.error("网关未配置 security.internal-token，无法生成可信身份签名头");
            return writeError(exchange, HttpStatus.INTERNAL_SERVER_ERROR, 500, "网关内部配置错误");
        }

        long ts = Instant.now().getEpochSecond();
        String signature = signUserHeaders(internalToken, userId, username, role, ts);
        // 在网关鉴权链闭环会话有效性：签名正确但已失效的 jti 不允许继续访问。
        return validateSession(userId, username, role, token, ts, signature)
                .flatMap(sessionValid -> {
                    if (!sessionValid) {
                        return writeError(exchange, HttpStatus.UNAUTHORIZED, 401, "身份认证失败：会话已失效，请重新登录");
                    }

                    ServerHttpRequest mutatedRequest = exchange.getRequest().mutate()
                            .headers(headers -> {
                                headers.remove(HEADER_INTERNAL_TOKEN);
                                headers.remove(HEADER_USER_ID);
                                headers.remove(HEADER_USER_NAME);
                                headers.remove(HEADER_USER_ROLE);
                                headers.remove(HEADER_USER_TS);
                                headers.remove(HEADER_USER_SIGNATURE);
                                headers.add(HEADER_USER_ID, userId);
                                headers.add(HEADER_USER_NAME, username);
                                headers.add(HEADER_USER_ROLE, role);
                                headers.add(HEADER_USER_TS, String.valueOf(ts));
                                headers.add(HEADER_USER_SIGNATURE, signature);
                            })
                            .build();

                    return chain.filter(exchange.mutate().request(mutatedRequest).build());
                });
    }

    private boolean isPublicPath(String path) {
        if (PUBLIC_PATHS.contains(path)) {
            return true;
        }
        AntPathMatcher matcher = new AntPathMatcher();
        for (String pattern : PUBLIC_PATH_PATTERNS) {
            if (matcher.match(pattern, path)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 管理员专属路径判定：使用 AntPathMatcher 进行通配符匹配。
     */
    private boolean isAdminPath(String path) {
        AntPathMatcher matcher = new AntPathMatcher();
        for (String pattern : ADMIN_PATHS) {
            if (matcher.match(pattern, path)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 内部高危接口路径判定：仅允许服务间调用。
     */
    private boolean isInternalCascadePath(String path) {
        return path.startsWith("/api/courses/cascade/")
                || path.startsWith("/api/homeworks/cascade/")
                || path.startsWith("/api/progress/cascade/");
    }

    /**
     * 校验内部调用令牌。
     */
    private boolean hasValidInternalToken(ServerWebExchange exchange) {
        if (!StringUtils.hasText(internalToken)) {
            log.error("网关未配置 security.internal-token，内部接口保护不可用");
            return false;
        }
        String requestToken = exchange.getRequest().getHeaders().getFirst(HEADER_INTERNAL_TOKEN);
        return StringUtils.hasText(requestToken) && internalToken.equals(requestToken);
    }

    private boolean validateToken(String token) {
        try {
            parseClaims(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /**
     * 校验会话是否仍有效（jti 在服务端白名单中）。
     * 说明：调用 user-service 的 validate-token 接口，并沿用网关签名头，避免身份头被伪造。
     */
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
                });
    }

    /**
     * 解析 user-service 返回的 Result，判定会话校验是否通过。
     */
    private boolean isValidateTokenResponseSuccess(Map<String, Object> payload) {
        if (payload == null) {
            return false;
        }
        Object code = payload.get("code");
        Object data = payload.get("data");
        boolean codeOk = "200".equals(String.valueOf(code));
        boolean dataOk = Boolean.TRUE.equals(data) || "true".equalsIgnoreCase(String.valueOf(data));
        return codeOk && dataOk;
    }

    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
    }

    private String signUserHeaders(String secret, String userId, String username, String role, long ts) {
        String payload = userId + "|" + username + "|" + role + "|" + ts;
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] digest = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (Exception e) {
            log.error("网关生成身份签名失败", e);
            throw new IllegalStateException("身份签名生成失败，服务内部配置异常", e);
        }
    }

    /**
     * 统一输出错误响应，前端可按 code/message 解析。
     */
    private Mono<Void> writeError(ServerWebExchange exchange, HttpStatus status, int code, String message) {
        String traceId = resolveTraceId(exchange);
        exchange.getResponse().setStatusCode(status);
        exchange.getResponse().getHeaders().set("Content-Type", "application/json;charset=UTF-8");
        // 将 traceId 透传到响应头，便于前端与日志联动排障。
        exchange.getResponse().getHeaders().set(HEADER_TRACE_ID, traceId);

        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("code", code);
            payload.put("message", message);
            payload.put("data", null);
            payload.put("traceId", traceId);
            byte[] bytes = objectMapper.writeValueAsBytes(payload);
            return exchange.getResponse()
                    .writeWith(Mono.just(exchange.getResponse().bufferFactory().wrap(bytes)));
        } catch (Exception e) {
            log.error("网关写入鉴权错误响应失败", e);
            return exchange.getResponse().setComplete();
        }
    }

    /**
     * 提取或生成 traceId，保证网关错误响应具备可追踪性。
     */
    private String resolveTraceId(ServerWebExchange exchange) {
        String directTraceId = firstNonBlank(
                exchange.getRequest().getHeaders().getFirst(HEADER_TRACE_ID),
                exchange.getRequest().getHeaders().getFirst("X-B3-TraceId"),
                exchange.getRequest().getHeaders().getFirst("X-Request-Id"));
        if (StringUtils.hasText(directTraceId)) {
            return directTraceId;
        }

        String traceIdFromTraceParent = extractTraceIdFromTraceParent(
                exchange.getRequest().getHeaders().getFirst("traceparent"));
        if (StringUtils.hasText(traceIdFromTraceParent)) {
            return traceIdFromTraceParent;
        }
        return UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * 从 W3C traceparent 报文中提取 traceId。
     */
    private String extractTraceIdFromTraceParent(String traceParent) {
        if (!StringUtils.hasText(traceParent)) {
            return null;
        }
        String[] parts = traceParent.trim().split("-");
        if (parts.length < 4) {
            return null;
        }
        String traceId = parts[1];
        if (traceId.length() != 32) {
            return null;
        }
        return traceId;
    }

    /**
     * 返回首个非空白字符串。
     */
    private String firstNonBlank(String... candidates) {
        if (candidates == null) {
            return null;
        }
        for (String candidate : candidates) {
            if (StringUtils.hasText(candidate)) {
                return candidate.trim();
            }
        }
        return null;
    }

    @Override
    public int getOrder() {
        // 先于业务路由执行，晚于最高优先级的极少数系统过滤器。
        return Ordered.HIGHEST_PRECEDENCE + 10;
    }
}
