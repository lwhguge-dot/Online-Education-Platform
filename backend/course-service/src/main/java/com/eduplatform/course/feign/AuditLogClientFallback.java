package com.eduplatform.course.feign;

import com.eduplatform.common.result.Result;
import com.eduplatform.course.dto.AuditLogRequest;
import org.springframework.stereotype.Component;

/**
 * 审计日志Feign客户端降级处理
 */
@Component
public class AuditLogClientFallback implements AuditLogClient {

    @Override
    public Result<String> createAuditLog(AuditLogRequest request) {
        // 审计日志服务不可用时，静默失败
        return Result.success("审计日志服务暂不可用", null);
    }
}
