package com.eduplatform.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.eduplatform.user.entity.AuditLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 审计日志Mapper
 */
@Mapper
public interface AuditLogMapper extends BaseMapper<AuditLog> {
    
    /**
     * 按条件查询审计日志
     */
    @Select("<script>" +
            "SELECT * FROM audit_logs WHERE 1=1 " +
            "<if test='actionType != null'> AND action_type = #{actionType}</if>" +
            "<if test='operatorId != null'> AND operator_id = #{operatorId}</if>" +
            "<if test='targetType != null'> AND target_type = #{targetType}</if>" +
            "<if test='startDate != null'> AND created_at &gt;= #{startDate}</if>" +
            "<if test='endDate != null'> AND created_at &lt;= #{endDate}</if>" +
            " ORDER BY created_at DESC" +
            "</script>")
    List<AuditLog> findByConditions(
            @Param("actionType") String actionType,
            @Param("operatorId") Long operatorId,
            @Param("targetType") String targetType,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate
    );
}
