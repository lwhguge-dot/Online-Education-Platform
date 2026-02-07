package com.eduplatform.course.feign;

import com.eduplatform.common.result.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * 作业服务Feign客户端降级处理
 */
@Slf4j
@Component
public class HomeworkServiceClientFallback implements HomeworkServiceClient {

    @Override
    public Result<Void> deleteCourseRelatedData(Long courseId) {
        log.warn("作业服务不可用，无法删除课程相关数据: courseId={}", courseId);
        return Result.error("作业服务不可用");
    }

    @Override
    public Result<Void> deleteUserRelatedData(Long userId) {
        log.warn("作业服务不可用，无法删除用户相关数据: userId={}", userId);
        return Result.error("作业服务不可用");
    }
}
