package com.eduplatform.course.feign;

import com.eduplatform.common.result.Result;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 审计日志Feign客户端降级处理
 */
@Component
public class AuditLogClientFallback implements AuditLogClient {

    @Override
    public Result<String> createAuditLog(Map<String, Object> body) {
        // 审计日志服务不可用时，静默失败
        return Result.success("审计日志服务暂不可用", null);
    }
}
