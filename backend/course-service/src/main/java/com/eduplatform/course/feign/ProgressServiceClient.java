package com.eduplatform.course.feign;

import com.eduplatform.common.result.Result;
import com.eduplatform.course.config.InternalApiFeignConfig;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * 进度服务Feign客户端
 */
@FeignClient(name = "progress-service", fallback = ProgressServiceClientFallback.class, configuration = InternalApiFeignConfig.class)
public interface ProgressServiceClient {

    /**
     * 删除课程相关的所有进度数据
     */
    @DeleteMapping("/api/progress/cascade/course/{courseId}")
    Result<Void> deleteCourseRelatedData(@PathVariable("courseId") Long courseId);

    /**
     * 删除用户相关的所有进度数据
     */
    @DeleteMapping("/api/progress/cascade/user/{userId}")
    Result<Void> deleteUserRelatedData(@PathVariable("userId") Long userId);
}
