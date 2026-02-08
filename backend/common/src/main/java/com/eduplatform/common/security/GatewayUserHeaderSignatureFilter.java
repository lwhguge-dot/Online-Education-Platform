package com.eduplatform.common.security;

import com.eduplatform.common.result.Result;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.Base64;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 5)
public class GatewayUserHeaderSignatureFilter extends OncePerRequestFilter {

    private static final String HEADER_USER_ID = "X-User-Id";
    private static final String HEADER_USER_NAME = "X-User-Name";
    private static final String HEADER_USER_ROLE = "X-User-Role";
    private static final String HEADER_USER_TS = "X-User-Ts";
    private static final String HEADER_USER_SIGNATURE = "X-User-Signature";

    private static final long MAX_SKEW_SECONDS = 600;

    private final ObjectMapper objectMapper;

    @Value("${security.internal-token:}")
    private String internalToken;

    public GatewayUserHeaderSignatureFilter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String uri = request.getRequestURI();
        if (uri != null && uri.startsWith("/actuator")) {
            filterChain.doFilter(request, response);
            return;
        }

        String userId = request.getHeader(HEADER_USER_ID);
        String username = request.getHeader(HEADER_USER_NAME);
        String role = request.getHeader(HEADER_USER_ROLE);

        boolean hasUserHeaders = StringUtils.hasText(userId) || StringUtils.hasText(username) || StringUtils.hasText(role);
        if (!hasUserHeaders) {
            filterChain.doFilter(request, response);
            return;
        }

        if (!StringUtils.hasText(internalToken)) {
            writeResult(response, HttpStatus.INTERNAL_SERVER_ERROR, Result.fail("服务内部配置错误"));
            return;
        }

        String tsHeader = request.getHeader(HEADER_USER_TS);
        String sigHeader = request.getHeader(HEADER_USER_SIGNATURE);
        if (!StringUtils.hasText(tsHeader) || !StringUtils.hasText(sigHeader)) {
            writeResult(response, HttpStatus.UNAUTHORIZED, Result.failure(401, "身份信息校验失败"));
            return;
        }

        long ts;
        try {
            ts = Long.parseLong(tsHeader);
        } catch (NumberFormatException e) {
            writeResult(response, HttpStatus.UNAUTHORIZED, Result.failure(401, "身份信息校验失败"));
            return;
        }

        long now = Instant.now().getEpochSecond();
        if (Math.abs(now - ts) > MAX_SKEW_SECONDS) {
            writeResult(response, HttpStatus.UNAUTHORIZED, Result.failure(401, "身份信息校验失败"));
            return;
        }

        if (!StringUtils.hasText(userId) || !StringUtils.hasText(username) || !StringUtils.hasText(role)) {
            writeResult(response, HttpStatus.UNAUTHORIZED, Result.failure(401, "身份信息校验失败"));
            return;
        }

        String expected = sign(internalToken, userId, username, role, ts);
        if (!MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), sigHeader.getBytes(StandardCharsets.UTF_8))) {
            writeResult(response, HttpStatus.UNAUTHORIZED, Result.failure(401, "身份信息校验失败"));
            return;
        }

        filterChain.doFilter(request, response);
    }

    private String sign(String secret, String userId, String username, String role, long ts) {
        String payload = userId + "|" + username + "|" + role + "|" + ts;
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] digest = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (Exception e) {
            return "";
        }
    }

    private void writeResult(HttpServletResponse response, HttpStatus status, Result<?> result) throws IOException {
        response.setStatus(status.value());
        response.setContentType("application/json;charset=UTF-8");
        objectMapper.writeValue(response.getOutputStream(), result);
    }
}
