package com.eduplatform.gateway.config;

import com.alibaba.csp.sentinel.adapter.gateway.sc.callback.BlockRequestHandler;
import com.alibaba.csp.sentinel.adapter.gateway.sc.callback.GatewayCallbackManager;
import com.alibaba.csp.sentinel.slots.block.authority.AuthorityException;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeException;
import com.alibaba.csp.sentinel.slots.block.flow.FlowException;
import com.alibaba.csp.sentinel.slots.block.flow.param.ParamFlowException;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.server.ServerResponse;

import jakarta.annotation.PostConstruct;
import java.util.HashMap;
import java.util.Map;

/**
 * Sentinel Gateway 配置类
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
     * 初始化 Sentinel Gateway 回调处理器
     *
     * 在 Spring 容器启动后执行,注册自定义的限流/降级响应处理器
     */
    @PostConstruct
    public void init() {
        // 注册自定义的限流/降级响应处理器
        GatewayCallbackManager.setBlockHandler(customBlockRequestHandler());
    }

    /**
     * 自定义限流/降级响应处理器
     *
     * 根据不同的异常类型返回不同的错误信息:
     * - FlowException: 流控规则触发 (QPS 超限)
     * - DegradeException: 熔断降级触发 (服务异常率过高)
     * - ParamFlowException: 热点参数限流触发
     * - AuthorityException: 授权规则不通过
     * - 其他: 系统保护规则触发
     *
     * @return BlockRequestHandler 自定义的阻塞请求处理器
     */
    private BlockRequestHandler customBlockRequestHandler() {
        return (exchange, throwable) -> {
            Map<String, Object> result = new HashMap<>();

            // 根据异常类型设置不同的错误信息
            if (throwable instanceof FlowException) {
                result.put("code", 429);
                result.put("msg", "请求过于频繁,请稍后再试");
                result.put("data", null);
            } else if (throwable instanceof DegradeException) {
                result.put("code", 503);
                result.put("msg", "服务暂时不可用,请稍后再试");
                result.put("data", null);
            } else if (throwable instanceof ParamFlowException) {
                result.put("code", 429);
                result.put("msg", "热点参数限流,请稍后再试");
                result.put("data", null);
            } else if (throwable instanceof AuthorityException) {
                result.put("code", 403);
                result.put("msg", "访问权限不足");
                result.put("data", null);
            } else {
                result.put("code", 500);
                result.put("msg", "系统繁忙,请稍后再试");
                result.put("data", null);
            }

            // 返回统一格式的 JSON 响应
            return ServerResponse
                    .status(HttpStatus.TOO_MANY_REQUESTS)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(BodyInserters.fromValue(result));
        };
    }
}
