package com.eduplatform.common.result;

import org.springframework.core.MethodParameter;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpResponse;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

/**
 * 统一 HTTP 状态码映射 Advice。
 *
 * <p>背景：Controller 内直接 {@code return Result.failure(403, ...)} 时，
 * Spring MVC 默认返回 HTTP 200，前端无法靠 HTTP 状态码做拦截器
 * （如 401 自动跳登录、429 触发限流提示）。
 *
 * <p>本 Advice 在响应序列化前，根据 {@link Result#getCode()} 把 HTTP 状态码改写为
 * 与业务码对应的值，保证「HTTP 状态码 ↔ 业务码」语义一致。
 *
 * <p>排除场景（不修改 HTTP 状态码）：
 * <ul>
 *   <li>返回类型已是 {@code ResponseEntity}（业务自行控制了 HTTP 状态码）。</li>
 *   <li>非 JSON 响应（如文件下载、CSV 导出、流式响应）。</li>
 *   <li>HTTP 200 之外的状态（如重定向 3xx、服务端错误 5xx），避免覆盖既有语义。</li>
 *   <li>无 body 的响应（如 204 No Content）。</li>
 * </ul>
 *
 * <p>映射规则与 {@code GlobalExceptionHandler#resolveHttpStatus} 保持一致：
 * <ul>
 *   <li>200 → HTTP 200</li>
 *   <li>401 → HTTP 401</li>
 *   <li>403 → HTTP 403</li>
 *   <li>404 → HTTP 404</li>
 *   <li>409 → HTTP 409</li>
 *   <li>429 → HTTP 429</li>
 *   <li>5xx → HTTP 500</li>
 *   <li>其他 4xx → HTTP 400</li>
 * </ul>
 *
 * <p>注意：本组件仅适用于基于 Servlet（Spring MVC）的业务服务，
 * 不应用于 Gateway（WebFlux）。
 */
@RestControllerAdvice(basePackages = "com.eduplatform")
public class HttpStatusCodeAdvice implements ResponseBodyAdvice<Object> {

    @Override
    public boolean supports(MethodParameter returnType,
            Class<? extends HttpMessageConverter<?>> converterType) {
        // 跳过 ResponseEntity 返回类型：业务已自行控制 HTTP 状态码
        return !org.springframework.http.ResponseEntity.class.isAssignableFrom(returnType.getParameterType());
    }

    @Override
    public Object beforeBodyWrite(Object body,
            MethodParameter returnType,
            MediaType selectedContentType,
            Class<? extends HttpMessageConverter<?>> selectedConverterType,
            ServerHttpRequest request,
            ServerHttpResponse response) {
        // 仅处理 JSON 响应且 body 是 Result 类型
        if (body == null) {
            return null;
        }
        if (!MediaType.APPLICATION_JSON.includes(selectedContentType)
                && !selectedContentType.isCompatibleWith(MediaType.APPLICATION_JSON)) {
            return body;
        }
        if (!(body instanceof Result<?> result)) {
            return body;
        }

        int code = result.getCode() == null ? HttpStatus.OK.value() : result.getCode();
        HttpStatus targetStatus = resolveHttpStatus(code);

        // 仅在当前状态为 200 时改写，避免覆盖 ResponseEntity 或其他链路设置的语义化状态码
        if (response instanceof ServletServerHttpResponse servletResponse) {
            int currentStatus = servletResponse.getServletResponse().getStatus();
            if (currentStatus == HttpStatus.OK.value()) {
                servletResponse.setStatusCode(targetStatus);
            }
        }
        return result;
    }

    private HttpStatus resolveHttpStatus(int code) {
        // 注意：switch case 必须是编译期常量，HttpStatus.OK.value() 不满足要求，使用字面量 200。
        return switch (code) {
            case 200 -> HttpStatus.OK;
            case 401 -> HttpStatus.UNAUTHORIZED;
            case 403 -> HttpStatus.FORBIDDEN;
            case 404 -> HttpStatus.NOT_FOUND;
            case 409 -> HttpStatus.CONFLICT;
            case 429 -> HttpStatus.TOO_MANY_REQUESTS;
            default -> code >= 500 ? HttpStatus.INTERNAL_SERVER_ERROR : HttpStatus.BAD_REQUEST;
        };
    }
}
