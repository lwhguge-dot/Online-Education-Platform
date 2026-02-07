package com.eduplatform.progress.client;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

@Component
public class HomeworkServiceClientFallback implements HomeworkServiceClient {
    
    @Override
    public Map<String, Object> unlockHomework(Long studentId, Long chapterId) {
        Map<String, Object> result = new HashMap<>();
        result.put("success", false);
        result.put("message", "Homework service is unavailable");
        return result;
    }
    
    @Override
    public Map<String, Object> getStudentUrgentHomeworks(Long studentId, Integer days) {
        Map<String, Object> result = new HashMap<>();
        result.put("code", 500);
        result.put("message", "Homework service is unavailable");
        result.put("data", new ArrayList<>());
        return result;
    }
    
    @Override
    public Map<String, Object> getStudentPendingHomeworkCount(Long studentId) {
        Map<String, Object> result = new HashMap<>();
        result.put("code", 500);
        result.put("message", "Homework service is unavailable");
        result.put("data", 0);
        return result;
    }
}
