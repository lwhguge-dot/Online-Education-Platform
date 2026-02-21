package com.eduplatform.user.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.eduplatform.common.result.Result;
import com.eduplatform.user.dto.CreateAuditLogRequest;
import com.eduplatform.user.entity.AuditLog;
import com.eduplatform.user.service.AuditLogService;
import com.eduplatform.user.vo.AuditLogVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 审计日志控制器
 * 提供系统操作轨迹的查询接口，支持精细化的多维筛选（操作人、时间、业务目标）。
 */
@RestController
@RequestMapping("/api/audit-logs")
@RequiredArgsConstructor
public class AuditLogController {

    private final AuditLogService auditLogService;
    @Value("${security.internal-token}")
    private String internalToken;

    /**
     * 审计轨迹分页检索 (管理员控制台)
     * 支持根据操作类型、操作人 ID、目标领域及时间范围进行全方位追溯。
     *
     * @param page       当前页码
     * @param size       页容量
     * @param type       操作类型标识 (如: DELETE_USER, LOGIN)
     * @param operatorId 操作执行人唯一 ID
     * @param targetType 业务对象类型 (如: USER, COURSE)
     * @param startDate  轨迹起始时间
     * @param endDate    轨迹结束时间
     * @return 标准分页响应，包含审计流明细
     */
    @GetMapping
    public Result<Map<String, Object>> getAuditLogs(
            @RequestParam(name = "page", defaultValue = "1") int page,
            @RequestParam(name = "size", defaultValue = "20") int size,
            @RequestParam(name = "type", required = false) String type,
            @RequestParam(name = "operatorId", required = false) Long operatorId,
            @RequestParam(name = "targetType", required = false) String targetType,
            @RequestParam(name = "startDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startDate,
            @RequestParam(name = "endDate", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endDate,
            @RequestHeader(value = "X-User-Role", required = false) String currentUserRole) {
        if (!isAdminRole(currentUserRole)) {
            return Result.failure(403, "权限不足，仅管理员可查询审计日志");
        }

        IPage<AuditLog> pageResult = auditLogService.findByPage(page, size, type, operatorId, targetType, startDate,
                endDate);

        Map<String, Object> result = new HashMap<>();
        result.put("records", auditLogService.convertToVOList(pageResult.getRecords()));
        result.put("total", pageResult.getTotal());
        result.put("pages", pageResult.getPages());
        result.put("current", pageResult.getCurrent());
        result.put("size", pageResult.getSize());

        return Result.success(result);
    }

    /**
     * 查看单条审计日志的原始上下文
     */
    @GetMapping("/{id}")
    public Result<AuditLogVO> getAuditLogById(
            @PathVariable Long id,
            @RequestHeader(value = "X-User-Role", required = false) String currentUserRole) {
        if (!isAdminRole(currentUserRole)) {
            return Result.failure(403, "权限不足，仅管理员可查看审计日志详情");
        }
        AuditLogVO log = auditLogService.convertToVO(auditLogService.findById(id));
        if (log == null) {
            return Result.error("审计记录已被存档或不存在");
        }
        return Result.success(log);
    }

    /**
     * 业务日志采集埋点 (Feign 内部调用或网关透传)
     * 允许全微服务系统将关键业务操作（尤其是高危操作）记录至 User-service 的统一流水。
     *
     * @param body 包含操作人隐私、目标快照及具体变更详情的原始映射
     */
    @PostMapping
    public Result<String> createAuditLog(
            @Valid @RequestBody CreateAuditLogRequest body,
            @RequestHeader(value = "X-Internal-Token", required = false) String requestInternalToken,
            @RequestHeader(value = "X-User-Id", required = false) String currentUserIdHeader,
            @RequestHeader(value = "X-User-Name", required = false) String currentUserName,
            @RequestHeader(value = "X-User-Role", required = false) String currentUserRole) {
        boolean internalCall = hasValidInternalToken(requestInternalToken);
        if (!internalCall && !isAdminRole(currentUserRole)) {
            return Result.failure(403, "权限不足，仅内部调用或管理员可写入审计日志");
        }

        Long operatorId = body.getOperatorId();
        String operatorName = body.getOperatorName();
        String actionType = body.getActionType();
        String targetType = body.getTargetType();
        Long targetId = body.getTargetId();
        String targetName = body.getTargetName();
        String details = body.getDetails();
        String ipAddress = body.getIpAddress();

        // 非内部调用场景下，操作人必须以网关注入身份为准，防止请求体伪造操作者。
        if (!internalCall) {
            Long currentUserId = parseUserId(currentUserIdHeader);
            if (currentUserId == null) {
                return Result.failure(401, "身份信息缺失，无法写入审计日志");
            }
            operatorId = currentUserId;
            if (currentUserName != null && !currentUserName.isBlank()) {
                operatorName = currentUserName;
            }
        }

        auditLogService.log(operatorId, operatorName, actionType, targetType, targetId, targetName, details,
                ipAddress);
        return Result.success("审计流水已入库", null);
    }

    /**
     * 判断是否管理员角色。
     */
    private boolean isAdminRole(String currentUserRole) {
        return currentUserRole != null && "admin".equalsIgnoreCase(currentUserRole);
    }

    /**
     * 校验内部调用令牌。
     */
    private boolean hasValidInternalToken(String requestInternalToken) {
        return requestInternalToken != null && !requestInternalToken.isBlank() && requestInternalToken.equals(internalToken);
    }

    /**
     * 解析网关注入的用户ID。
     */
    private Long parseUserId(String userIdHeader) {
        if (userIdHeader == null || userIdHeader.isBlank()) {
            return null;
        }
        try {
            return Long.parseLong(userIdHeader);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
