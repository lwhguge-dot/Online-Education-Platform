package com.eduplatform.homework.feign;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;
import java.util.Map;

/**
 * 课程服务Feign客户端
 */
@FeignClient(name = "course-service", fallback = CourseServiceClientFallback.class)
public interface CourseServiceClient {

    @GetMapping("/api/courses/teacher/{teacherId}")
    Map<String, Object> getTeacherCourses(@PathVariable("teacherId") Long teacherId);

    @GetMapping("/api/courses/{id}")
    Map<String, Object> getCourseById(@PathVariable("id") Long id);

    @GetMapping("/api/enrollments/course/{courseId}")
    Map<String, Object> getCourseEnrollments(@PathVariable("courseId") Long courseId);

    @GetMapping("/api/enrollments/course/{courseId}/today")
    Map<String, Object> getTodayEnrollments(@PathVariable("courseId") Long courseId);

    @GetMapping("/api/chapters/course/{courseId}")
    Map<String, Object> getCourseChapters(@PathVariable("courseId") Long courseId);
}
