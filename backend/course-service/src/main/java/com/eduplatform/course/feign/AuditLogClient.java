package com.eduplatform.course.feign;

import com.eduplatform.common.result.Result;
import com.eduplatform.course.dto.AuditLogRequest;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * 审计日志Feign客户端
 */
@FeignClient(name = "user-service", fallback = AuditLogClientFallback.class)
public interface AuditLogClient {

    @PostMapping("/api/audit-logs")
    Result<String> createAuditLog(@RequestBody AuditLogRequest request);
}
