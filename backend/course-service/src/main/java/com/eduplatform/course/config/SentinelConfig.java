package com.eduplatform.course.config;

import com.alibaba.csp.sentinel.adapter.spring.webmvc.callback.BlockExceptionHandler;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.alibaba.csp.sentinel.slots.block.authority.AuthorityException;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeException;
import com.alibaba.csp.sentinel.slots.block.flow.FlowException;
import com.alibaba.csp.sentinel.slots.block.flow.param.ParamFlowException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Sentinel 配置类
 *
 * 功能说明:
 * 1. 自定义限流/熔断降级的响应格式
 * 2. 统一异常处理,返回标准 JSON 格式
 * 3. 区分不同类型的 Sentinel 异常
 *
 * @author Claude
 * @since 2026-02-07
 */
@Configuration
public class SentinelConfig {

    /**
     * 自定义 Sentinel 异常处理器
     *
     * 根据不同的异常类型返回不同的错误信息:
     * - FlowException: 流控规则触发 (QPS 超限)
     * - DegradeException: 熔断降级触发 (服务异常率过高)
     * - ParamFlowException: 热点参数限流触发
     * - AuthorityException: 授权规则不通过
     * - 其他: 系统保护规则触发
     *
     * @return BlockExceptionHandler 自定义的阻塞异常处理器
     */
    @Bean
    public BlockExceptionHandler blockExceptionHandler() {
        return new BlockExceptionHandler() {
            @Override
            public void handle(HttpServletRequest request, HttpServletResponse response, BlockException e) throws IOException {
                Map<String, Object> result = new HashMap<>();

                // 根据异常类型设置不同的错误信息
                if (e instanceof FlowException) {
                    result.put("code", 429);
                    result.put("msg", "请求过于频繁,请稍后再试");
                    result.put("data", null);
                } else if (e instanceof DegradeException) {
                    result.put("code", 503);
                    result.put("msg", "服务暂时不可用,请稍后再试");
                    result.put("data", null);
                } else if (e instanceof ParamFlowException) {
                    result.put("code", 429);
                    result.put("msg", "热点参数限流,请稍后再试");
                    result.put("data", null);
                } else if (e instanceof AuthorityException) {
                    result.put("code", 403);
                    result.put("msg", "访问权限不足");
                    result.put("data", null);
                } else {
                    result.put("code", 500);
                    result.put("msg", "系统繁忙,请稍后再试");
                    result.put("data", null);
                }

                // 设置响应头
                response.setStatus(429);
                response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                response.setCharacterEncoding("UTF-8");

                // 返回 JSON 响应
                ObjectMapper objectMapper = new ObjectMapper();
                response.getWriter().write(objectMapper.writeValueAsString(result));
            }
        };
    }
}
