package com.eduplatform.common.exception;

import com.eduplatform.common.result.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 全局异常处理器
 * 拦截所有 Controller 层抛出的异常，返回标准 Result 格式
 */
@Slf4j
@RestControllerAdvice(basePackages = "com.eduplatform")
public class GlobalExceptionHandler {

    /**
     * 处理 NoResourceFoundException - 排除 Actuator 端点
     */
    @ExceptionHandler(NoResourceFoundException.class)
    public void handleNoResourceFoundException(NoResourceFoundException e, HttpServletRequest request) throws NoResourceFoundException {
        String uri = request.getRequestURI();
        // 如果是 Actuator 端点，重新抛出异常让 Spring 处理
        if (uri.startsWith("/actuator")) {
            throw e;
        }
        log.error("请求地址 '{}', 资源未找到.", uri, e);
        // 对于非 Actuator 路径，也重新抛出让 Spring 默认处理
        throw e;
    }

    /**
     * 处理所有未捕获的通用异常
     */
    @ExceptionHandler(Exception.class)
    public Result<String> handleException(Exception e, HttpServletRequest request) {
        String uri = request.getRequestURI();
        // 排除 Actuator 端点
        if (uri.startsWith("/actuator")) {
            return null;
        }
        log.error("请求地址 '{}', 发生系统异常.", uri, e);
        return Result.fail("系统繁忙，请稍后重试: " + e.getMessage());
    }

    /**
     * 处理运行时异常 (通常是业务逻辑错误)
     */
    @ExceptionHandler(RuntimeException.class)
    public Result<String> handleRuntimeException(RuntimeException e, HttpServletRequest request) {
        String uri = request.getRequestURI();
        // 排除 Actuator 端点
        if (uri.startsWith("/actuator")) {
            return null;
        }
        log.error("请求地址 '{}', 发生业务异常.", uri, e);
        return Result.fail(e.getMessage());
    }
}
