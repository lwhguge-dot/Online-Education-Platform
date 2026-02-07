package com.eduplatform.progress.controller;

import com.eduplatform.common.result.Result;
import com.eduplatform.progress.service.BadgeService;
import com.eduplatform.progress.vo.BadgeVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 徽章控制器。
 * 设计意图：提供学生荣誉与激励系统入口，统一返回徽章视图对象。
 */
@RestController
@RequestMapping("/api/progress/badges")
@RequiredArgsConstructor
public class BadgeController {

    private final BadgeService badgeService;

    /**
     * 获取学生的所有徽章（包含已解锁和未解锁）。
     */
    @GetMapping("/student/{studentId}")
    public Result<List<BadgeVO>> getStudentBadges(@PathVariable("studentId") Long studentId) {
        List<BadgeVO> badges = badgeService.getStudentBadges(studentId);
        return Result.success(badges);
    }

    /**
     * 检查并授予符合条件的徽章。
     */
    @PostMapping("/student/{studentId}/check")
    public Result<List<BadgeVO>> checkAndAwardBadges(@PathVariable("studentId") Long studentId) {
        List<BadgeVO> newlyAwarded = badgeService.checkAndAwardBadges(studentId);
        if (newlyAwarded.isEmpty()) {
            return Result.success("暂无新徽章", newlyAwarded);
        }
        return Result.success("恭喜获得新徽章！", newlyAwarded);
    }
}
