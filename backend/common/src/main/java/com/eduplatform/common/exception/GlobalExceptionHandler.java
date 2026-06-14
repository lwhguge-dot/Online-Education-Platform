package com.eduplatform.common.exception;

import com.eduplatform.common.result.Result;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.util.StringUtils;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 全局异常处理器。
 * 统一返回 Result 结构，并对外屏蔽内部异常细节。
 */
@Slf4j
@RestControllerAdvice(basePackages = "com.eduplatform")
public class GlobalExceptionHandler {

    /**
     * 处理 NoResourceFoundException - 排除 Actuator 端点。
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public void handleNoResourceFoundException(NoResourceFoundException e, HttpServletRequest request)
            throws NoResourceFoundException {
        String uri = request.getRequestURI();
        // 如果是 Actuator 端点，重新抛出异常让 Spring 处理
        if (uri.startsWith("/actuator")) {
            throw e;
        }
        // 安全要求：日志中不直接记录请求原始输入，避免日志注入和敏感信息泄露。
        // 对 uri 做净化，仅保留白名单字符，截断长度，避免日志注入。
        String safeUri = sanitizeForLog(uri);
        log.warn("请求资源未找到: uri={}", safeUri);
        // 对于非 Actuator 路径，也重新抛出让 Spring 默认处理
        throw e;
    }

    /**
     * 净化用户可控字符串，防止日志注入（换行/控制字符伪造）。
     */
    private static String sanitizeForLog(String value) {
        if (value == null) {
            return "null";
        }
        // 去除换行/回车/制表符等控制字符，并截断长度
        String cleaned = value.replaceAll("[\\r\\n\\t\\p{Cntrl}]", "_");
        return cleaned.length() > 200 ? cleaned.substring(0, 200) + "..." : cleaned;
    }

    /**
     * 处理 @RequestBody 参数校验失败。
     * 返回所有字段的错误信息（而非仅第一条）。
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Result<String>> handleMethodArgumentNotValidException(
            MethodArgumentNotValidException e, HttpServletRequest request) {
        String traceId = resolveTraceId(request);
        String errorMessage = e.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .collect(Collectors.joining("; "));
        if (errorMessage.isBlank()) {
            errorMessage = "请求参数校验失败";
        }
        log.warn("参数校验失败: {}", errorMessage);
        return buildResponseEntity(HttpStatus.BAD_REQUEST, 400, errorMessage, traceId);
    }

    /**
     * 处理表单参数绑定异常。
     * 返回所有字段的错误信息（而非仅第一条）。
     */
    @ExceptionHandler(BindException.class)
    public ResponseEntity<Result<String>> handleBindException(BindException e, HttpServletRequest request) {
        String traceId = resolveTraceId(request);
        String errorMessage = e.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .collect(Collectors.joining("; "));
        if (errorMessage.isBlank()) {
            errorMessage = "请求参数校验失败";
        }
        log.warn("参数绑定失败: {}", errorMessage);
        return buildResponseEntity(HttpStatus.BAD_REQUEST, 400, errorMessage, traceId);
    }

    /**
     * 处理 Spring 6.1+ HandlerMethod 级别的校验异常（如 @Valid List 参数）。
     */
    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<Result<String>> handleHandlerMethodValidationException(
            HandlerMethodValidationException e, HttpServletRequest request) {
        String traceId = resolveTraceId(request);
        String errorMessage = e.getAllErrors().stream()
                .map(err -> err.getDefaultMessage())
                .filter(msg -> msg != null && !msg.isBlank())
                .collect(Collectors.joining("; "));
        if (errorMessage.isBlank()) {
            errorMessage = "请求参数校验失败";
        }
        log.warn("HandlerMethod 校验失败: {}", errorMessage);
        return buildResponseEntity(HttpStatus.BAD_REQUEST, 400, errorMessage, traceId);
    }

    /**
     * 处理路径参数和查询参数的约束校验异常。
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Result<String>> handleConstraintViolationException(
            ConstraintViolationException e, HttpServletRequest request) {
        String traceId = resolveTraceId(request);
        String errorMessage = e.getConstraintViolations().stream()
                .map(ConstraintViolation::getMessage)
                .collect(Collectors.joining("; "));
        if (errorMessage.isBlank()) {
            errorMessage = "请求参数校验失败";
        }
        log.warn("约束校验失败: {}", errorMessage);
        return buildResponseEntity(HttpStatus.BAD_REQUEST, 400, errorMessage, traceId);
    }

    /**
     * 处理请求体格式错误（例如 JSON 类型不匹配）。
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Result<String>> handleHttpMessageNotReadableException(
            HttpMessageNotReadableException e, HttpServletRequest request) {
        String traceId = resolveTraceId(request);
        log.warn("请求体解析失败", e);
        return buildResponseEntity(HttpStatus.BAD_REQUEST, 400,
                "请求体格式错误，请检查字段类型与结构", traceId);
    }

    /**
     * 处理业务异常：直接透传业务语义给前端。
     */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<Result<String>> handleBusinessException(
            BusinessException e, HttpServletRequest request) {
        String traceId = resolveTraceId(request);
        log.warn("业务异常: code={}, message={}", e.getCode(), e.getMessage());
        HttpStatus httpStatus = resolveHttpStatus(e.getCode());
        return buildResponseEntity(httpStatus, e.getCode(), e.getMessage(), traceId);
    }

    /**
     * 处理所有未捕获的异常（统一兜底策略）。
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Result<String>> handleException(Exception e, HttpServletRequest request) {
        String traceId = resolveTraceId(request);
        if (e instanceof RuntimeException) {
            log.error("未预期的运行时异常", e);
        } else {
            log.error("发生系统异常", e);
        }
        return buildResponseEntity(HttpStatus.INTERNAL_SERVER_ERROR, 500,
                "系统繁忙，请稍后重试", traceId);
    }

    /**
     * 构建包含 HTTP 状态码和 traceId 的统一响应。
     */
    private <T> ResponseEntity<Result<T>> buildResponseEntity(
            HttpStatus httpStatus, Integer code, String message, String traceId) {
        Result<T> result = Result.failure(code, message);
        result.setTraceId(traceId);
        return new ResponseEntity<>(result, httpStatus);
    }

    /**
     * 将业务错误码映射为 HTTP 状态码。
     * 匹配逻辑：取业务码首位数字，400→400, 403→403, 404→404, 409→409, 429→429, 5xx→500。
     */
    private HttpStatus resolveHttpStatus(Integer code) {
        if (code == null) {
            return HttpStatus.BAD_REQUEST;
        }
        if (code == 403) return HttpStatus.FORBIDDEN;
        if (code == 404) return HttpStatus.NOT_FOUND;
        if (code == 409) return HttpStatus.CONFLICT;
        if (code == 429) return HttpStatus.TOO_MANY_REQUESTS;
        if (code >= 500) return HttpStatus.INTERNAL_SERVER_ERROR;
        return HttpStatus.BAD_REQUEST;
    }

    /**
     * 解析链路追踪ID。
     * 优先级：显式请求头 -> MDC -> 随机生成。
     */
    private String resolveTraceId(HttpServletRequest request) {
        if (request != null) {
            String directHeaderTraceId = firstNonBlank(
                    request.getHeader("X-Trace-Id"),
                    request.getHeader("X-B3-TraceId"),
                    request.getHeader("X-Request-Id"));
            if (StringUtils.hasText(directHeaderTraceId)) {
                return directHeaderTraceId;
            }

            String traceParent = request.getHeader("traceparent");
            String traceIdFromTraceParent = extractTraceIdFromTraceParent(traceParent);
            if (StringUtils.hasText(traceIdFromTraceParent)) {
                return traceIdFromTraceParent;
            }
        }

        String traceIdFromMdc = firstNonBlank(
                MDC.get("traceId"),
                MDC.get("trace_id"),
                MDC.get("X-B3-TraceId"));
        if (StringUtils.hasText(traceIdFromMdc)) {
            return traceIdFromMdc;
        }
        return UUID.randomUUID().toString().replace("-", "");
    }

    /**
     * 从 W3C traceparent 中提取 traceId。
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
}
