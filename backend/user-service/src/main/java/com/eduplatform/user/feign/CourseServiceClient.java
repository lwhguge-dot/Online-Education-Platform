package com.eduplatform.user.feign;

import com.eduplatform.common.result.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Map;

/**
 * 课程服务Feign客户端
 * 用于 user-service 跨服务调用 course-service 的远程接口。
 */
@FeignClient(name = "course-service", fallback = CourseServiceClientFallback.class)
public interface CourseServiceClient {

    /**
     * 获取课程统计数据
     */
    @GetMapping("/api/courses/stats")
    Result<Map<String, Object>> getCourseStats();

    /**
     * 删除用户相关的所有数据（选课、评论、点赞等）
     */
    @DeleteMapping("/api/courses/cascade/user/{userId}")
    Result<Void> deleteUserRelatedData(@PathVariable("userId") Long userId, @RequestParam("role") String role);

    /**
     * 获取课程选课学生人数。
     * 业务原因：用于计算课程级公告的目标受众人数。
     *
     * @param courseId 课程ID
     * @return 该课程的活跃选课学生数
     */
    @GetMapping("/api/enrollments/course/{courseId}/count")
    Result<Long> getCourseStudentCount(@PathVariable("courseId") Long courseId);

    /**
     * 获取课程详情（含教师ID）
     * 业务原因：异步消费作业提交事件时，需要通过课程ID获取教师信息以发送通知。
     *
     * @param courseId 课程ID
     * @return 课程详情 Map
     */
    @GetMapping("/api/courses/{courseId}")
    Result<Map<String, Object>> getCourseById(@PathVariable("courseId") Long courseId);
}
