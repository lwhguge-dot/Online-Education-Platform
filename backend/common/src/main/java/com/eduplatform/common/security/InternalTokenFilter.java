package com.eduplatform.common.security;

import com.eduplatform.common.result.Result;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
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

    private final ObjectMapper objectMapper;

    @Value("${security.internal-token:}")
    private String internalToken;

    public InternalTokenFilter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String uri = request.getRequestURI();
        if (uri != null && (uri.startsWith("/actuator") || uri.startsWith("/api/auth"))) {
            filterChain.doFilter(request, response);
            return;
        }

        String token = request.getHeader(HEADER_INTERNAL_TOKEN);
        if (!StringUtils.hasText(token)) {
            filterChain.doFilter(request, response);
            return;
        }

        if (!StringUtils.hasText(internalToken)) {
            log.error("X-Internal-Token header 但服务未配置 security.internal-token: uri={}", uri);
            writeResult(response, HttpStatus.INTERNAL_SERVER_ERROR, Result.fail("服务内部配置错误"));
            return;
        }

        if (!MessageDigest.isEqual(
                internalToken.getBytes(StandardCharsets.UTF_8),
                token.getBytes(StandardCharsets.UTF_8))) {
            log.warn("Internal token 校验失败: uri={}", uri);
            writeResult(response, HttpStatus.FORBIDDEN, Result.failure(403, "内部鉴权失败"));
            return;
        }

        filterChain.doFilter(request, response);
    }

    private void writeResult(HttpServletResponse response, HttpStatus status, Result<?> result) throws IOException {
        response.setStatus(status.value());
        response.setContentType("application/json;charset=UTF-8");
        objectMapper.writeValue(response.getOutputStream(), result);
    }
}
