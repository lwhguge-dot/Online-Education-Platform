package com.eduplatform.progress.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.Map;

@FeignClient(name = "homework-service", fallback = HomeworkServiceClientFallback.class)
public interface HomeworkServiceClient {
    
    @PostMapping("/api/homeworks/unlock")
    Map<String, Object> unlockHomework(
            @RequestParam("studentId") Long studentId,
            @RequestParam("chapterId") Long chapterId
    );
    
    @GetMapping("/api/homeworks/student/{studentId}/urgent")
    Map<String, Object> getStudentUrgentHomeworks(
            @PathVariable("studentId") Long studentId,
            @RequestParam(value = "days", defaultValue = "2") Integer days
    );
    
    @GetMapping("/api/homeworks/student/{studentId}/pending-count")
    Map<String, Object> getStudentPendingHomeworkCount(
            @PathVariable("studentId") Long studentId
    );
}
