package com.eduplatform.user.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.eduplatform.user.entity.AuditLog;
import com.eduplatform.user.mapper.AuditLogMapper;
import com.eduplatform.user.vo.AuditLogVO;
import java.util.Collections;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 审计日志服务
 * 处理系统安全审计相关的日志记录与检索逻辑。
 */
@Service
@RequiredArgsConstructor
public class AuditLogService {

    private final AuditLogMapper auditLogMapper;

    /**
     * 记录一条审计日志
     * 用于追踪管理员或关键用户的操作行为，确保操作可追溯。
     *
     * @param operatorId   操作人ID
     * @param operatorName 操作人用户名
     * @param actionType   行为类型 (如: USER_ENABLE, COURSE_REJECT)
     * @param targetType   操作对象类型 (如: USER, COURSE)
     * @param targetId     操作对象唯一标识 ID
     * @param targetName   操作对象名称（如用户名或课程标题）
     * @param details      操作详细描述或前后值对比
     * @param ipAddress    操作人的客户端 IP
     */
    public void log(Long operatorId, String operatorName, String actionType,
            String targetType, Long targetId, String targetName,
            String details, String ipAddress) {
        AuditLog log = new AuditLog();
        log.setOperatorId(operatorId);
        log.setOperatorName(operatorName);
        log.setActionType(actionType);
        log.setTargetType(targetType);
        log.setTargetId(targetId);
        log.setTargetName(targetName);
        log.setDetails(details);
        log.setIpAddress(ipAddress);
        log.setCreatedAt(LocalDateTime.now());
        auditLogMapper.insert(log);
    }

    /**
     * 分页查询审计日志 (支持多维度筛选)
     *
     * @param page       当前页码
     * @param size       每页大小
     * @param actionType 行为类型过滤 (可选)
     * @param operatorId 操作人 ID 过滤 (可选)
     * @param targetType 对象类型过滤 (可选)
     * @param startDate  查询开始时间 (可选)
     * @param endDate    查询结束时间 (可选)
     * @return 分页结果记录
     */
    public IPage<AuditLog> findByPage(int page, int size, String actionType,
            Long operatorId, String targetType,
            LocalDateTime startDate, LocalDateTime endDate) {
        Page<AuditLog> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<AuditLog> wrapper = new LambdaQueryWrapper<>();

        if (actionType != null && !actionType.isEmpty()) {
            wrapper.eq(AuditLog::getActionType, actionType);
        }
        if (operatorId != null) {
            wrapper.eq(AuditLog::getOperatorId, operatorId);
        }
        if (targetType != null && !targetType.isEmpty()) {
            wrapper.eq(AuditLog::getTargetType, targetType);
        }
        if (startDate != null) {
            wrapper.ge(AuditLog::getCreatedAt, startDate);
        }
        if (endDate != null) {
            wrapper.le(AuditLog::getCreatedAt, endDate);
        }

        wrapper.orderByDesc(AuditLog::getCreatedAt);
        return auditLogMapper.selectPage(pageParam, wrapper);
    }

    /**
     * 根据行为和时间范围进行高级查询
     * 主要用于导出或大数据量分析场景。
     */
    public List<AuditLog> findByConditions(String actionType, Long operatorId,
            String targetType, LocalDateTime startDate,
            LocalDateTime endDate) {
        return auditLogMapper.findByConditions(actionType, operatorId, targetType, startDate, endDate);
    }

    /**
     * 获取审计详情记录
     */
    public AuditLog findById(Long id) {
        return auditLogMapper.selectById(id);
    }

    /**
     * 统计系统总审计记录数
     */
    public long count() {
        return auditLogMapper.selectCount(null);
    }

    /**
     * 审计日志实体转视图对象。
     * 设计原因：避免控制层直接暴露持久层结构。
     */
    public AuditLogVO convertToVO(AuditLog log) {
        if (log == null) {
            return null;
        }
        AuditLogVO vo = new AuditLogVO();
        BeanUtils.copyProperties(log, vo);
        return vo;
    }

    /**
     * 审计日志实体列表转视图对象列表。
     */
    public java.util.List<AuditLogVO> convertToVOList(java.util.List<AuditLog> logs) {
        if (logs == null || logs.isEmpty()) {
            return Collections.emptyList();
        }
        return logs.stream().map(this::convertToVO).collect(Collectors.toList());
    }
}
