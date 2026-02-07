package com.eduplatform.progress.service;

import com.eduplatform.progress.mapper.ChapterProgressMapper;
import com.eduplatform.progress.mapper.StudentBadgeMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 进度数据级联删除服务 (Data Hygiene Service)
 * 负责在核心业务实体（如课程、用户）发生物理删除时，同步清理其在进度模块中的关联数据。
 *
 * 设计定位：
 * 1. 数据一致性：作为外部服务删除逻辑的回调或级联点，规避数据库孤儿记录。
 * 2. 事务性清理：确保整个清理过程满足 ACID 特性，防止部分删除导致的逻辑异常。
 *
 * @author Antigravity
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ProgressCascadeDeleteService {

    private final ChapterProgressMapper chapterProgressMapper;
    private final StudentBadgeMapper studentBadgeMapper;

    /**
     * 深度清理课程关联的学习语料
     * 场景：课程被整体注销、课程合并等需要物理抹除进度的操作。
     *
     * @param courseId 目标课程 ID
     */
    @Transactional
    public void deleteCourseRelatedData(Long courseId) {
        log.info("开始执行课程数据物理清理: courseId={}", courseId);

        // 核心动作：擦除所有在该课程下产生的章节进度快照
        int progressCount = chapterProgressMapper.deleteByCourseId(courseId);
        log.info("清理完成，共移除章节进度快照: {} 条", progressCount);

        log.info("课程级联清理流水已结束: courseId={}", courseId);
    }

    /**
     * 深度清理用户关联的所有学习资产
     * 场景：用户销号、隐私数据合规性抹除等。
     *
     * @param userId 目标学生/用户 ID
     */
    @Transactional
    public void deleteUserRelatedData(Long userId) {
        log.info("开始执行用户资产全量清理: userId={}", userId);

        // 1. 擦除该用户在全平台的所有章节进度记录
        int progressCount = chapterProgressMapper.deleteByStudentId(userId);
        log.info("已移除进度轨迹: {} 条", progressCount);

        // 2. 剥夺该用户已获得的所有勋章荣誉（物理删除）
        int badgeCount = studentBadgeMapper.deleteByStudentId(userId);
        log.info("已移除勋章记录: {} 条", badgeCount);

        log.info("用户级联清理流水已结束: userId={}", userId);
    }
}
