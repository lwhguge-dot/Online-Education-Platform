package com.eduplatform.gateway.config;

import com.alibaba.csp.sentinel.adapter.gateway.sc.callback.BlockRequestHandler;
import com.alibaba.csp.sentinel.adapter.gateway.sc.callback.GatewayCallbackManager;
import com.alibaba.csp.sentinel.slots.block.authority.AuthorityException;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeException;
import com.alibaba.csp.sentinel.slots.block.flow.FlowException;
import com.alibaba.csp.sentinel.slots.block.flow.param.ParamFlowException;
import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.server.ServerResponse;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class SentinelConfig {

    @PostConstruct
    public void init() {
        GatewayCallbackManager.setBlockHandler(customBlockRequestHandler());
    }

    private BlockRequestHandler customBlockRequestHandler() {
        return (exchange, throwable) -> {
            HttpStatus status;
            int code;
            String msg;

            if (throwable instanceof AuthorityException) {
                status = HttpStatus.FORBIDDEN;
                code = 403;
                msg = "访问权限不足";
            } else if (throwable instanceof DegradeException) {
                status = HttpStatus.SERVICE_UNAVAILABLE;
                code = 503;
                msg = "服务暂时不可用，请稍后再试";
            } else if (throwable instanceof ParamFlowException || throwable instanceof FlowException) {
                status = HttpStatus.TOO_MANY_REQUESTS;
                code = 429;
                msg = "请求过于频繁，请稍后再试";
            } else {
                status = HttpStatus.INTERNAL_SERVER_ERROR;
                code = 500;
                msg = "系统繁忙，请稍后再试";
            }

            Map<String, Object> result = new HashMap<>();
            result.put("code", code);
            result.put("msg", msg);
            result.put("data", null);

            return ServerResponse
                    .status(status)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(BodyInserters.fromValue(result));
        };
    }
}
