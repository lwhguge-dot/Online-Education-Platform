package com.eduplatform.homework.controller;

import com.eduplatform.common.result.Result;
import com.eduplatform.homework.dto.TeacherDashboardDTO;
import com.eduplatform.homework.service.TeacherStatsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 教师统计控制器。
 * 设计意图：为教师端仪表盘提供作业统计与教学概览数据。
 */
@Slf4j
@RestController
@RequestMapping("/api/stats/teacher")
@RequiredArgsConstructor
public class TeacherStatsController {

    private final TeacherStatsService teacherStatsService;

    /**
     * 获取教师仪表盘聚合数据。
     * 说明：课程列表由前端透传，减少跨服务调用开销。
     *
     * @param teacherId 教师ID
     * @param courses   教师的课程列表（由前端传入，避免跨服务调用）
     */
    @PostMapping("/dashboard")
    public Result<TeacherDashboardDTO> getTeacherDashboard(
            @RequestParam("teacherId") Long teacherId,
            @RequestBody List<Map<String, Object>> courses) {
        try {
            log.info("获取教师仪表盘数据, teacherId={}, coursesCount={}", teacherId, courses.size());
            TeacherDashboardDTO dashboard = teacherStatsService.getTeacherDashboard(teacherId, courses);
            return Result.success(dashboard);
        } catch (Exception e) {
            log.error("获取教师仪表盘数据失败", e);
            return Result.error("获取仪表盘数据失败: " + e.getMessage());
        }
    }

    /**
     * 获取教师仪表盘聚合数据（GET方式，不需要课程列表）。
     * 返回基础统计数据。
     */
    @GetMapping("/dashboard")
    public Result<TeacherDashboardDTO> getTeacherDashboardSimple(
            @RequestParam("teacherId") Long teacherId) {
        try {
            log.info("获取教师仪表盘基础数据, teacherId={}", teacherId);
            // 返回空课程列表的基础数据
            TeacherDashboardDTO dashboard = teacherStatsService.getTeacherDashboard(teacherId, List.of());
            return Result.success(dashboard);
        } catch (Exception e) {
            log.error("获取教师仪表盘数据失败", e);
            return Result.error("获取仪表盘数据失败: " + e.getMessage());
        }
    }
}
