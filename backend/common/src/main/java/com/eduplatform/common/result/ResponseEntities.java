package com.eduplatform.common.result;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

/**
 * Result 与 ResponseEntity 的桥接工具。
 *
 * <p>背景：直接 {@code return Result.failure(403, ...)} 时 Spring MVC 默认返回 HTTP 200，
 * 前端无法靠 HTTP 状态码做拦截器（如 401 自动跳登录、429 触发限流提示）。
 *
 * <p>本工具把 {@link Result#getCode()} 映射到正确的 HTTP 状态码，供新接口逐步采用。
 * 旧接口可保持现状，避免一次性改造带来的前端兼容风险。
 *
 * <p>使用示例：
 * <pre>{@code
 * @PostMapping("/foo")
 * public ResponseEntity<Result<Void>> foo(...) {
 *     if (!hasPermission) {
 *         return ResponseEntities.failure(403, "权限不足");
 *     }
 *     return ResponseEntities.success("操作成功", null);
 * }
 * }</pre>
 */
public final class ResponseEntities {

    private ResponseEntities() {
    }

    /**
     * 用业务码构建 ResponseEntity，code 映射到对应 HTTP 状态码。
     */
    public static <T> ResponseEntity<Result<T>> of(int code, String message, T data) {
        return ResponseEntity.status(resolveHttpStatus(code)).body(Result.failure(code, message, data));
    }

    /**
     * 构建成功 ResponseEntity（HTTP 200）。
     */
    public static <T> ResponseEntity<Result<T>> success(T data) {
        return ResponseEntity.ok(Result.success(data));
    }

    /**
     * 构建成功 ResponseEntity（HTTP 200）。
     */
    public static <T> ResponseEntity<Result<T>> success(String message, T data) {
        return ResponseEntity.ok(Result.success(message, data));
    }

    /**
     * 构建失败 ResponseEntity，HTTP 状态码根据 code 映射。
     */
    public static <T> ResponseEntity<Result<T>> failure(int code, String message) {
        return ResponseEntity.status(resolveHttpStatus(code)).body(Result.failure(code, message));
    }

    /**
     * 业务码到 HTTP 状态码的映射规则，与 {@code GlobalExceptionHandler#resolveHttpStatus} 保持一致。
     */
    private static HttpStatus resolveHttpStatus(int code) {
        return switch (code) {
            case 401 -> HttpStatus.UNAUTHORIZED;
            case 403 -> HttpStatus.FORBIDDEN;
            case 404 -> HttpStatus.NOT_FOUND;
            case 409 -> HttpStatus.CONFLICT;
            case 429 -> HttpStatus.TOO_MANY_REQUESTS;
            default -> code >= 500 ? HttpStatus.INTERNAL_SERVER_ERROR : HttpStatus.BAD_REQUEST;
        };
    }
}
