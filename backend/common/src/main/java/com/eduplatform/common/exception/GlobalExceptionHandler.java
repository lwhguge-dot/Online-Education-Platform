package com.eduplatform.common.exception;

import com.eduplatform.common.result.Result;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.util.StringUtils;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
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
        log.error("请求资源未找到", e);
        // 对于非 Actuator 路径，也重新抛出让 Spring 默认处理
        throw e;
    }

    /**
     * 处理 @RequestBody 参数校验失败。
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<String> handleMethodArgumentNotValidException(MethodArgumentNotValidException e,
            HttpServletRequest request) {
        String traceId = resolveTraceId(request);
        String errorMessage = e.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(FieldError::getDefaultMessage)
                .orElse("请求参数校验失败");
        log.warn("参数校验失败", e);
        return failureWithTraceId(400, errorMessage, traceId);
    }

    /**
     * 处理表单参数绑定异常。
     */
    @ExceptionHandler(BindException.class)
    public Result<String> handleBindException(BindException e, HttpServletRequest request) {
        String traceId = resolveTraceId(request);
        String errorMessage = e.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(FieldError::getDefaultMessage)
                .orElse("请求参数校验失败");
        log.warn("参数绑定失败", e);
        return failureWithTraceId(400, errorMessage, traceId);
    }

    /**
     * 处理路径参数和查询参数的约束校验异常。
     */
    @ExceptionHandler(ConstraintViolationException.class)
    public Result<String> handleConstraintViolationException(ConstraintViolationException e,
            HttpServletRequest request) {
        String traceId = resolveTraceId(request);
        String errorMessage = e.getConstraintViolations().stream()
                .map(ConstraintViolation::getMessage)
                .collect(Collectors.joining("; "));
        if (errorMessage.isBlank()) {
            errorMessage = "请求参数校验失败";
        }
        log.warn("约束校验失败", e);
        return failureWithTraceId(400, errorMessage, traceId);
    }

    /**
     * 处理请求体格式错误（例如 JSON 类型不匹配）。
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public Result<String> handleHttpMessageNotReadableException(HttpMessageNotReadableException e,
            HttpServletRequest request) {
        String traceId = resolveTraceId(request);
        log.warn("请求体解析失败", e);
        return failureWithTraceId(400, "请求体格式错误，请检查字段类型与结构", traceId);
    }

    /**
     * 处理运行时异常（通常是业务执行异常）。
     */
    @ExceptionHandler(RuntimeException.class)
    public Result<String> handleRuntimeException(RuntimeException e, HttpServletRequest request) {
        String traceId = resolveTraceId(request);
        log.error("发生业务异常", e);
        return failureWithTraceId(500, "请求处理失败，请稍后重试", traceId);
    }

    /**
     * 处理所有未捕获的通用异常。
     */
    @ExceptionHandler(Exception.class)
    public Result<String> handleException(Exception e, HttpServletRequest request) {
        String traceId = resolveTraceId(request);
        log.error("发生系统异常", e);
        return failureWithTraceId(500, "系统繁忙，请稍后重试", traceId);
    }

    /**
     * 构建携带 traceId 的统一失败响应。
     */
    private <T> Result<T> failureWithTraceId(Integer code, String message, String traceId) {
        Result<T> result = Result.failure(code, message);
        result.setTraceId(traceId);
        return result;
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
