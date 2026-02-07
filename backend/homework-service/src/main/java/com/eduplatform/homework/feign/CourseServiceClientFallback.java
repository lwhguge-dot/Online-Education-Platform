package com.eduplatform.homework.feign;

import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.Collections;

/**
 * 课程服务Feign客户端降级处理
 */
@Component
public class CourseServiceClientFallback implements CourseServiceClient {
    
    @Override
    public Map<String, Object> getTeacherCourses(Long teacherId) {
        Map<String, Object> result = new HashMap<>();
        result.put("code", 500);
        result.put("message", "课程服务暂不可用");
        result.put("data", Collections.emptyList());
        return result;
    }
    
    @Override
    public Map<String, Object> getCourseById(Long id) {
        Map<String, Object> result = new HashMap<>();
        result.put("code", 500);
        result.put("message", "课程服务暂不可用");
        result.put("data", null);
        return result;
    }
    
    @Override
    public Map<String, Object> getCourseEnrollments(Long courseId) {
        Map<String, Object> result = new HashMap<>();
        result.put("code", 500);
        result.put("message", "课程服务暂不可用");
        result.put("data", Collections.emptyList());
        return result;
    }
    
    @Override
    public Map<String, Object> getTodayEnrollments(Long courseId) {
        Map<String, Object> result = new HashMap<>();
        result.put("code", 500);
        result.put("message", "课程服务暂不可用");
        result.put("data", 0);
        return result;
    }
    
    @Override
    public Map<String, Object> getCourseChapters(Long courseId) {
        Map<String, Object> result = new HashMap<>();
        result.put("code", 500);
        result.put("message", "课程服务暂不可用");
        result.put("data", Collections.emptyList());
        return result;
    }
}
