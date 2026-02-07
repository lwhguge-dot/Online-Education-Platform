package com.eduplatform.progress.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.eduplatform.progress.entity.Badge;
import com.eduplatform.progress.entity.ChapterProgress;
import com.eduplatform.progress.entity.StudentBadge;
import com.eduplatform.progress.mapper.BadgeMapper;
import com.eduplatform.progress.mapper.ChapterProgressMapper;
import com.eduplatform.progress.mapper.StudentBadgeMapper;
import com.eduplatform.progress.vo.BadgeVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 荣誉激励与成就系统服务
 * 负责通过游戏化机制提升学生参与度，涵盖勋章定义的解析、解锁进度的实时预估、以及自动化的荣誉授予。
 *
 * 核心功能：
 * 1. 进度追踪：动态计算“即将解锁”（Near Unlock）百分比，外显化学生的微小进步。
 * 2. 规则校验：支持“完课密度”、“出勤毅力”、“测验爆发力（满分）”等多维条件的原子化判定。
 * 3. 荣誉持久化：记录勋章授予的关键瞬间，作为学情报告的重要组成部分。
 *
 * @author Antigravity
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BadgeService {

    private final BadgeMapper badgeMapper;
    private final StudentBadgeMapper studentBadgeMapper;
    private final ChapterProgressMapper progressMapper;

    /**
     * 获取学生全量勋章墙 (包含进度预估)
     * 逻辑流：
     * 1. 拉取标准勋章库与学生已获快照。
     * 2. 计算当前学情指标（学时、天数、满分频次）。
     * 3. 对未得勋章执行线性进度模拟，标记进度 >= 80% 的“即将解锁”项。
     *
     * @param studentId 学生标识
     * @return 包含解锁状态与进度详情的 BadgeVO 列表
     */
    public List<BadgeVO> getStudentBadges(Long studentId) {
        // 获取所有勋章本体
        List<Badge> allBadges = badgeMapper.selectList(null);

        // 获取已归档的荣誉记录
        List<StudentBadge> earnedBadges = studentBadgeMapper.selectList(
                new LambdaQueryWrapper<StudentBadge>()
                        .eq(StudentBadge::getStudentId, studentId));

        Map<Integer, StudentBadge> earnedMap = earnedBadges.stream()
                .collect(Collectors.toMap(StudentBadge::getBadgeId, sb -> sb));

        // 提取统计指标
        List<ChapterProgress> allProgress = progressMapper.selectList(
                new LambdaQueryWrapper<ChapterProgress>()
                        .eq(ChapterProgress::getStudentId, studentId));

        int completedChapters = (int) allProgress.stream()
                .filter(p -> p.getIsCompleted() != null && p.getIsCompleted() == 1)
                .count();
        int studyDays = calculateStudyDays(allProgress);
        int perfectScoreCount = (int) allProgress.stream()
                .filter(p -> p.getQuizScore() != null && p.getQuizScore() >= 100)
                .count();

        return allBadges.stream()
                .map(badge -> {
                    BadgeVO vo = new BadgeVO();
                    vo.setId(badge.getId().longValue());
                    vo.setName(badge.getName());
                    vo.setDescription(badge.getDescription());
                    vo.setIconUrl(badge.getIcon());
                    vo.setConditionType(badge.getConditionType());
                    vo.setConditionValue(badge.getConditionValue());

                    StudentBadge earned = earnedMap.get(badge.getId());
                    vo.setEarned(earned != null);
                    if (earned != null) {
                        vo.setEarnedAt(earned.getEarnedAt());
                    }

                    // 对于未解锁勋章，执行进度启发式计算
                    if (earned == null) {
                        int currentValue = 0;
                        switch (badge.getConditionType()) {
                            case "chapter_complete":
                                currentValue = completedChapters;
                                break;
                            case "study_days":
                                currentValue = studyDays;
                                break;
                            case "perfect_score":
                                currentValue = perfectScoreCount;
                                break;
                        }
                        int targetValue = badge.getConditionValue();
                        int progressPercent = targetValue > 0 ? Math.min(100, (currentValue * 100) / targetValue) : 0;
                        vo.setProgress(progressPercent);
                        vo.setCurrentValue(currentValue);
                        vo.setTargetValue(targetValue);
                        // 阈值设定：进度达到 80% 标记为“即将解锁”，激励用户进行最后的冲刺
                        vo.setNearUnlock(progressPercent >= 80 && progressPercent < 100);
                    } else {
                        vo.setProgress(100);
                        vo.setNearUnlock(false);
                    }

                    return vo;
                })
                .collect(Collectors.toList());
    }

    /**
     * 荣誉授予执行器 (Event Triggered)
     * 当章节完成、测验提交等关键事件发生时，异步（或同步）驱动此逻辑进行“荣誉晋升”判定。
     *
     * @return 本次操作新获得的勋章列表，用于前端气泡通知
     */
    @Transactional
    public List<BadgeVO> checkAndAwardBadges(Long studentId) {
        List<BadgeVO> newlyAwarded = new ArrayList<>();

        List<Badge> allBadges = badgeMapper.selectList(null);

        // 幂等保护：排除已获得的 ID
        Set<Integer> earnedBadgeIds = studentBadgeMapper.selectList(
                new LambdaQueryWrapper<StudentBadge>()
                        .eq(StudentBadge::getStudentId, studentId))
                .stream()
                .map(StudentBadge::getBadgeId)
                .collect(Collectors.toSet());

        List<ChapterProgress> allProgress = progressMapper.selectList(
                new LambdaQueryWrapper<ChapterProgress>()
                        .eq(ChapterProgress::getStudentId, studentId));

        int completedChapters = (int) allProgress.stream()
                .filter(p -> p.getIsCompleted() != null && p.getIsCompleted() == 1)
                .count();

        int studyDays = calculateStudyDays(allProgress);

        boolean hasPerfectScore = allProgress.stream()
                .anyMatch(p -> p.getQuizScore() != null && p.getQuizScore() >= 100);

        for (Badge badge : allBadges) {
            if (earnedBadgeIds.contains(badge.getId())) {
                continue;
            }

            boolean shouldAward = false;

            // 规则匹配引擎
            switch (badge.getConditionType()) {
                case "chapter_complete":
                    shouldAward = completedChapters >= badge.getConditionValue();
                    break;
                case "study_days":
                    shouldAward = studyDays >= badge.getConditionValue();
                    break;
                case "perfect_score":
                    shouldAward = hasPerfectScore && badge.getConditionValue() <= 1;
                    break;
                default:
                    log.warn("未知的勋章判定逻辑: {}", badge.getConditionType());
            }

            if (shouldAward) {
                // 事务内原子化授予
                StudentBadge studentBadge = new StudentBadge();
                studentBadge.setStudentId(studentId);
                studentBadge.setBadgeId(badge.getId());
                studentBadge.setEarnedAt(LocalDateTime.now());
                studentBadgeMapper.insert(studentBadge);

                BadgeVO vo = new BadgeVO();
                vo.setId(badge.getId().longValue());
                vo.setName(badge.getName());
                vo.setDescription(badge.getDescription());
                vo.setIconUrl(badge.getIcon());
                vo.setConditionType(badge.getConditionType());
                vo.setConditionValue(badge.getConditionValue());
                vo.setEarned(true);
                vo.setEarnedAt(studentBadge.getEarnedAt());
                vo.setProgress(100);
                vo.setNearUnlock(false);
                newlyAwarded.add(vo);

                log.info("恭喜学生 {} 解锁荣誉: {}", studentId, badge.getName());
            }
        }

        return newlyAwarded;
    }

    /**
     * 计算活跃天数 (基于日期指纹的去重聚合)
     */
    private int calculateStudyDays(List<ChapterProgress> allProgress) {
        return (int) allProgress.stream()
                .filter(p -> p.getLastUpdateTime() != null)
                .map(p -> p.getLastUpdateTime().toLocalDate())
                .distinct()
                .count();
    }
}
