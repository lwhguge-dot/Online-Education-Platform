package com.eduplatform.homework.feign;

import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

/**
 * 进度服务Feign客户端降级处理
 */
@Component
public class ProgressServiceClientFallback implements ProgressServiceClient {
    
    @Override
    public Map<String, Object> getCourseCompletionRate(Long courseId) {
        Map<String, Object> result = new HashMap<>();
        result.put("code", 500);
        result.put("message", "进度服务暂不可用");
        result.put("data", 0.0);
        return result;
    }
    
    @Override
    public Map<String, Object> getTeacherWeeklyStats(Long teacherId) {
        Map<String, Object> result = new HashMap<>();
        result.put("code", 500);
        result.put("message", "进度服务暂不可用");
        result.put("data", null);
        return result;
    }
}
