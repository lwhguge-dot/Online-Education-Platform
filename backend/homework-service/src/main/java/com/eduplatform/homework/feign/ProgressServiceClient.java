package com.eduplatform.homework.feign;

import com.eduplatform.homework.config.InternalApiFeignConfig;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Map;

/**
 * 进度服务Feign客户端
 */
@FeignClient(name = "progress-service", fallback = ProgressServiceClientFallback.class, configuration = InternalApiFeignConfig.class)
public interface ProgressServiceClient {

    @GetMapping("/api/progress/course/{courseId}/completion-rate")
    Map<String, Object> getCourseCompletionRate(@PathVariable("courseId") Long courseId);

    @GetMapping("/api/progress/teacher/{teacherId}/weekly-stats")
    Map<String, Object> getTeacherWeeklyStats(@PathVariable("teacherId") Long teacherId);
}
