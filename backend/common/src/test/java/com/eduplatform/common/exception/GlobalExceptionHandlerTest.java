package com.eduplatform.common.exception;

import com.eduplatform.common.result.Result;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * GlobalExceptionHandler traceId 行为回归测试。
 */
@DisplayName("GlobalExceptionHandler 单元测试")
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    @DisplayName("系统异常-可从traceparent提取traceId")
    void shouldExtractTraceIdFromTraceParent() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/demo");
        request.addHeader("traceparent", "00-4bf92f3577b34da6a3ce929d0e0e4736-00f067aa0ba902b7-01");

        Result<String> result = handler.handleException(new Exception("boom"), request);

        assertEquals(500, result.getCode());
        assertEquals("系统繁忙，请稍后重试", result.getMessage());
        assertEquals("4bf92f3577b34da6a3ce929d0e0e4736", result.getTraceId());
    }

    @Test
    @DisplayName("业务异常-优先使用X-Trace-Id")
    void shouldPreferExplicitTraceIdHeader() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/demo");
        request.addHeader("X-Trace-Id", "trace-id-from-header");

        Result<String> result = handler.handleRuntimeException(new RuntimeException("business"), request);

        assertEquals(500, result.getCode());
        assertEquals("请求处理失败，请稍后重试", result.getMessage());
        assertEquals("trace-id-from-header", result.getTraceId());
    }

    @Test
    @DisplayName("无追踪头时-自动生成traceId")
    void shouldGenerateTraceIdWhenNoHeaderProvided() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/demo");

        Result<String> result = handler.handleRuntimeException(new RuntimeException("business"), request);

        assertNotNull(result.getTraceId());
        assertTrue(result.getTraceId().matches("[0-9a-fA-F]{32}"));
    }
}
