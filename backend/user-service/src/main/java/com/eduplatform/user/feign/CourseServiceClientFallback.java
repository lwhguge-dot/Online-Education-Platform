package com.eduplatform.user.feign;

import com.eduplatform.common.result.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * 课程服务Feign客户端降级处理
 * 当 course-service 不可用时，返回安全的默认值以保证 user-service 核心功能正常运行。
 */
@Slf4j
@Component
public class CourseServiceClientFallback implements CourseServiceClient {

    @Override
    public Result<Map<String, Object>> getCourseStats() {
        // 服务不可用时返回默认值
        Map<String, Object> defaultStats = new HashMap<>();
        defaultStats.put("total", 0L);
        defaultStats.put("draft", 0L);
        defaultStats.put("reviewing", 0L);
        defaultStats.put("published", 0L);
        defaultStats.put("offline", 0L);
        return Result.success(defaultStats);
    }

    @Override
    public Result<Void> deleteUserRelatedData(Long userId, String role) {
        log.warn("课程服务不可用，无法删除用户相关数据: userId={}, role={}", userId, role);
        return Result.error("课程服务不可用");
    }

    /**
     * 获取课程选课学生人数的降级处理。
     * 降级策略：返回 0，公告统计页面会显示"目标人数=0"，但不影响主流程。
     */
    @Override
    public Result<Long> getCourseStudentCount(Long courseId) {
        log.warn("课程服务不可用，无法获取课程选课人数: courseId={}", courseId);
        return Result.success(0L);
    }
}
