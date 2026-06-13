package com.eduplatform.common.security;

import com.eduplatform.common.result.Result;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@DisplayName("InternalTokenFilter 单元测试")
class InternalTokenFilterTest {

    private InternalTokenFilter filter;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final String VALID_TOKEN = "test-internal-token-abc123";
    private static final String HEADER_INTERNAL_TOKEN = "X-Internal-Token";

    @BeforeEach
    void setUp() {
        filter = new InternalTokenFilter(objectMapper);
        ReflectionTestUtils.setField(filter, "internalToken", VALID_TOKEN);
    }

    @Test
    @DisplayName("无 InternalToken 且无 User Headers → 放行")
    void shouldPassWhenNoInternalTokenAndNoUserHeaders() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/user-service/internal/health");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilterInternal(request, response, chain);

        verify(chain).doFilter(request, response);
    }

    @Test
    @DisplayName("有效 InternalToken → 放行")
    void shouldPassWhenValidInternalToken() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/user-service/internal/data");
        request.addHeader(HEADER_INTERNAL_TOKEN, VALID_TOKEN);
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilterInternal(request, response, chain);

        verify(chain).doFilter(request, response);
    }

    @Test
    @DisplayName("无效 InternalToken → 403 拒绝")
    void shouldRejectWhenInvalidInternalToken() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/user-service/internal/data");
        request.addHeader(HEADER_INTERNAL_TOKEN, "wrong-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilterInternal(request, response, chain);

        verify(chain, never()).doFilter(request, response);
        assertEquals(HttpStatus.FORBIDDEN.value(), response.getStatus());
        assertTrue(response.getContentType().startsWith(MediaType.APPLICATION_JSON_VALUE));

        Result<?> result = objectMapper.readValue(response.getContentAsString(), Result.class);
        assertEquals(403, result.getCode());
        assertFalse(result.getMessage().isEmpty());
    }

    @Test
    @DisplayName("Actuator 路径 → 放行")
    void shouldPassForActuatorPaths() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/actuator/health");
        request.addHeader(HEADER_INTERNAL_TOKEN, "wrong-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilterInternal(request, response, chain);

        verify(chain).doFilter(request, response);
    }

    @Test
    @DisplayName("/api/auth 路径 → 放行")
    void shouldPassForAuthPaths() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/auth/login");
        request.addHeader(HEADER_INTERNAL_TOKEN, "wrong-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilterInternal(request, response, chain);

        verify(chain).doFilter(request, response);
    }

    @Test
    @DisplayName("服务未配置 internalToken → 500 错误")
    void shouldReturn500WhenInternalTokenNotConfigured() throws Exception {
        ReflectionTestUtils.setField(filter, "internalToken", "");

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setRequestURI("/api/user-service/internal/data");
        request.addHeader(HEADER_INTERNAL_TOKEN, VALID_TOKEN);
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilterInternal(request, response, chain);

        verify(chain, never()).doFilter(request, response);
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR.value(), response.getStatus());
        assertTrue(response.getContentType().startsWith(MediaType.APPLICATION_JSON_VALUE));

        Result<?> result = objectMapper.readValue(response.getContentAsString(), Result.class);
        assertEquals(500, result.getCode());
    }
}
