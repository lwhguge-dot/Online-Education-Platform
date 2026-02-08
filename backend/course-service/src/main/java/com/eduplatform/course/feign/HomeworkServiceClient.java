package com.eduplatform.course.feign;

import com.eduplatform.common.result.Result;
import com.eduplatform.course.config.InternalApiFeignConfig;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * 作业服务Feign客户端
 */
@FeignClient(name = "homework-service", fallback = HomeworkServiceClientFallback.class, configuration = InternalApiFeignConfig.class)
public interface HomeworkServiceClient {

    /**
     * 删除课程相关的所有作业数据
     */
    @DeleteMapping("/api/homeworks/cascade/course/{courseId}")
    Result<Void> deleteCourseRelatedData(@PathVariable("courseId") Long courseId);

    /**
     * 删除用户相关的所有作业数据
     */
    @DeleteMapping("/api/homeworks/cascade/user/{userId}")
    Result<Void> deleteUserRelatedData(@PathVariable("userId") Long userId);
}
