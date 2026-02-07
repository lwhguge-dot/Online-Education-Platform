package com.eduplatform.progress.controller;

import com.eduplatform.common.result.Result;
import com.eduplatform.progress.dto.StudentStatsDTO;
import com.eduplatform.progress.service.StudentStatsService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

/**
 * 学生学习统计控制器
 */
@RestController
@RequestMapping("/api/stats")
@RequiredArgsConstructor
public class StatsController {

    private final StudentStatsService studentStatsService;

    /**
     * 获取学生仪表盘统计数据
     * 包含：总学习时长、今日学习、连续天数、本周学习时长、测验成绩等
     */
    @GetMapping("/student/{studentId}/dashboard")
    public Result<StudentStatsDTO> getStudentDashboard(@PathVariable("studentId") Long studentId) {
        StudentStatsDTO stats = studentStatsService.getStudentDashboardStats(studentId);
        return Result.success(stats);
    }
}
