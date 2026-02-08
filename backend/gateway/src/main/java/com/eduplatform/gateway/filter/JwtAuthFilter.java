package com.eduplatform.gateway.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;

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

    /**
     * 白名单接口：无需登录即可访问。
     */
    private static final Set<String> PUBLIC_PATHS = Set.of(
            "/api/auth/login",
            "/api/auth/register",
            "/api/auth/health",
            "/api/auth/reset-password"
    );

    private final ObjectMapper objectMapper;

    @Value("${jwt.secret}")
    private String jwtSecret;

    @Value("${security.internal-token}")
    private String internalToken;

    public JwtAuthFilter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();

        // 非 API 与 WebSocket 请求先放行，避免影响静态资源和站点路由。
        if (!path.startsWith("/api/") && !path.startsWith("/ws/")) {
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

        // WebSocket 握手请求校验 token 参数，后续由 user-service 二次校验并绑定身份。
        if (path.startsWith("/ws/")) {
            String token = exchange.getRequest().getQueryParams().getFirst("token");
            if (!StringUtils.hasText(token)) {
                return writeError(exchange, HttpStatus.UNAUTHORIZED, 401, "WebSocket 鉴权失败：缺少 token");
            }
            if (!validateToken(token)) {
                return writeError(exchange, HttpStatus.UNAUTHORIZED, 401, "WebSocket 鉴权失败：token 无效");
            }
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

        // 构造可信请求头：先移除同名头，避免被客户端伪造；再注入网关解析结果。
        ServerHttpRequest mutatedRequest = exchange.getRequest().mutate()
                .headers(headers -> {
                    headers.remove(HEADER_USER_ID);
                    headers.remove(HEADER_USER_NAME);
                    headers.remove(HEADER_USER_ROLE);
                    headers.add(HEADER_USER_ID, userId);
                    headers.add(HEADER_USER_NAME, username);
                    headers.add(HEADER_USER_ROLE, role);
                })
                .build();

        return chain.filter(exchange.mutate().request(mutatedRequest).build());
    }

    private boolean isPublicPath(String path) {
        return PUBLIC_PATHS.contains(path);
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

    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * 统一输出错误响应，前端可按 code/message 解析。
     */
    private Mono<Void> writeError(ServerWebExchange exchange, HttpStatus status, int code, String message) {
        exchange.getResponse().setStatusCode(status);
        exchange.getResponse().getHeaders().set("Content-Type", "application/json;charset=UTF-8");

        try {
            byte[] bytes = objectMapper.writeValueAsBytes(Map.of(
                    "code", code,
                    "message", message,
                    "data", (Object) null
            ));
            return exchange.getResponse()
                    .writeWith(Mono.just(exchange.getResponse().bufferFactory().wrap(bytes)));
        } catch (Exception e) {
            log.error("网关写入鉴权错误响应失败", e);
            return exchange.getResponse().setComplete();
        }
    }

    @Override
    public int getOrder() {
        // 先于业务路由执行，晚于最高优先级的极少数系统过滤器。
        return Ordered.HIGHEST_PRECEDENCE + 10;
    }
}

