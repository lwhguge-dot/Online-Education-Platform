package com.eduplatform.user.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.eduplatform.common.result.Result;
import com.eduplatform.user.dto.AnnouncementStatsDTO;
import com.eduplatform.user.dto.TeacherAnnouncementDTO;
import com.eduplatform.user.entity.Announcement;
import com.eduplatform.user.entity.AnnouncementRead;
import com.eduplatform.user.entity.User;
import com.eduplatform.user.feign.CourseServiceClient;
import com.eduplatform.user.mapper.AnnouncementMapper;
import com.eduplatform.user.mapper.AnnouncementReadMapper;
import com.eduplatform.user.mapper.UserMapper;
import com.eduplatform.user.vo.AnnouncementVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 公告管理服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AnnouncementService {

    private final AnnouncementMapper announcementMapper;
    private final AnnouncementReadMapper announcementReadMapper;
    private final CourseServiceClient courseServiceClient;
    private final UserMapper userMapper;

    /**
     * 创建公告
     */
    public Announcement create(Announcement announcement) {
        announcement.setCreatedAt(LocalDateTime.now());
        announcement.setUpdatedAt(LocalDateTime.now());
        if (announcement.getStatus() == null) {
            announcement.setStatus("DRAFT");
        }
        if (announcement.getIsPinned() == null) {
            announcement.setIsPinned(false);
        }
        if (announcement.getReadCount() == null) {
            announcement.setReadCount(0);
        }
        // 如果没有设置创建人，默认为管理员(ID=1)
        if (announcement.getCreatedBy() == null) {
            announcement.setCreatedBy(1L);
        }
        announcementMapper.insert(announcement);
        return announcement;
    }

    /**
     * 更新公告
     */
    public Announcement update(Long id, Announcement announcement) {
        Announcement existing = announcementMapper.selectById(id);
        if (existing == null) {
            throw new RuntimeException("公告不存在");
        }

        if (announcement.getTitle() != null) {
            existing.setTitle(announcement.getTitle());
        }
        if (announcement.getContent() != null) {
            existing.setContent(announcement.getContent());
        }
        if (announcement.getTargetAudience() != null) {
            existing.setTargetAudience(announcement.getTargetAudience());
        }
        if (announcement.getCourseId() != null) {
            existing.setCourseId(announcement.getCourseId());
        }
        if (announcement.getStatus() != null) {
            existing.setStatus(announcement.getStatus());
        }
        if (announcement.getIsPinned() != null) {
            existing.setIsPinned(announcement.getIsPinned());
        }
        if (announcement.getPublishTime() != null) {
            existing.setPublishTime(announcement.getPublishTime());
        }
        if (announcement.getExpireTime() != null) {
            existing.setExpireTime(announcement.getExpireTime());
        }

        existing.setUpdatedAt(LocalDateTime.now());
        announcementMapper.updateById(existing);
        return existing;
    }

    /**
     * 删除公告
     */
    public void delete(Long id) {
        announcementMapper.deleteById(id);
    }

    /**
     * 根据ID查询公告
     */
    public Announcement findById(Long id) {
        return announcementMapper.selectById(id);
    }

    /**
     * 分页查询公告
     */
    public IPage<Announcement> findByPage(int page, int size, String status, String targetAudience) {
        Page<Announcement> pageParam = new Page<>(page, size);
        LambdaQueryWrapper<Announcement> wrapper = new LambdaQueryWrapper<>();

        if (status != null && !status.isEmpty()) {
            wrapper.eq(Announcement::getStatus, status);
        }
        if (targetAudience != null && !targetAudience.isEmpty()) {
            wrapper.eq(Announcement::getTargetAudience, targetAudience);
        }

        wrapper.orderByDesc(Announcement::getIsPinned)
                .orderByDesc(Announcement::getCreatedAt);
        return announcementMapper.selectPage(pageParam, wrapper);
    }

    /**
     * 查询所有公告
     */
    public List<Announcement> findAll() {
        return announcementMapper.selectList(
                new LambdaQueryWrapper<Announcement>()
                        .orderByDesc(Announcement::getIsPinned)
                        .orderByDesc(Announcement::getCreatedAt));
    }

    /**
     * 查询已发布且未过期的公告（按受众过滤）
     */
    public List<Announcement> findActiveByAudience(String audience) {
        // 先更新过期公告状态和发布定时公告
        announcementMapper.updateExpiredAnnouncements();
        announcementMapper.publishScheduledAnnouncements();
        return announcementMapper.findActiveByAudience(audience);
    }

    /**
     * 发布公告
     */
    public Announcement publish(Long id) {
        Announcement announcement = announcementMapper.selectById(id);
        if (announcement == null) {
            throw new RuntimeException("公告不存在");
        }

        announcement.setStatus("PUBLISHED");
        if (announcement.getPublishTime() == null) {
            announcement.setPublishTime(LocalDateTime.now());
        }
        announcement.setUpdatedAt(LocalDateTime.now());
        announcementMapper.updateById(announcement);
        return announcement;
    }

    /**
     * 统计公告数量
     */
    public long count() {
        return announcementMapper.selectCount(null);
    }

    // ==================== 教师公告相关方法 ====================

    /**
     * 教师创建公告逻辑
     * 1. 封装 Announcement 实体，设置教师为创建人
     * 2. 处理定时发布逻辑：若发布时间在未来，状态设为 SCHEDULED，否则设为 PUBLISHED
     * 3. 关联特定课程（可选）并持久化
     *
     * @param teacherId 教师用户ID
     * @param dto       教务公告传输对象
     * @return 创建成功的公告实体
     */
    @Transactional
    public Announcement createByTeacher(Long teacherId, TeacherAnnouncementDTO dto) {
        Announcement announcement = new Announcement();
        announcement.setTitle(dto.getTitle());
        announcement.setContent(dto.getContent());
        announcement.setTargetAudience(dto.getTargetAudience() != null ? dto.getTargetAudience() : "STUDENT");
        announcement.setCourseId(dto.getCourseId());
        announcement.setIsPinned(dto.getIsPinned() != null ? dto.getIsPinned() : false);
        announcement.setExpireTime(dto.getExpireTime());
        announcement.setCreatedBy(teacherId);
        announcement.setReadCount(0);
        announcement.setCreatedAt(LocalDateTime.now());
        announcement.setUpdatedAt(LocalDateTime.now());

        // 如果发布时间设定在未来，则标记为“排期中”，由定时任务扫描发布
        if (dto.getPublishTime() != null && dto.getPublishTime().isAfter(LocalDateTime.now())) {
            announcement.setStatus("SCHEDULED");
            announcement.setPublishTime(dto.getPublishTime());
        } else {
            // 立即发布
            announcement.setStatus("PUBLISHED");
            announcement.setPublishTime(LocalDateTime.now());
        }

        announcementMapper.insert(announcement);
        return announcement;
    }

    /**
     * 教师更新公告
     */
    @Transactional
    public Announcement updateByTeacher(Long teacherId, Long announcementId, TeacherAnnouncementDTO dto) {
        Announcement existing = announcementMapper.selectById(announcementId);
        if (existing == null) {
            throw new RuntimeException("公告不存在");
        }
        if (!existing.getCreatedBy().equals(teacherId)) {
            throw new RuntimeException("无权修改此公告");
        }

        if (dto.getTitle() != null) {
            existing.setTitle(dto.getTitle());
        }
        if (dto.getContent() != null) {
            existing.setContent(dto.getContent());
        }
        if (dto.getTargetAudience() != null) {
            existing.setTargetAudience(dto.getTargetAudience());
        }
        if (dto.getCourseId() != null) {
            existing.setCourseId(dto.getCourseId());
        }
        if (dto.getIsPinned() != null) {
            existing.setIsPinned(dto.getIsPinned());
        }
        if (dto.getExpireTime() != null) {
            existing.setExpireTime(dto.getExpireTime());
        }

        // 处理定时发布更新
        if (dto.getPublishTime() != null) {
            if (dto.getPublishTime().isAfter(LocalDateTime.now())) {
                existing.setStatus("SCHEDULED");
                existing.setPublishTime(dto.getPublishTime());
            } else if ("DRAFT".equals(existing.getStatus()) || "SCHEDULED".equals(existing.getStatus())) {
                existing.setStatus("PUBLISHED");
                existing.setPublishTime(LocalDateTime.now());
            }
        }

        existing.setUpdatedAt(LocalDateTime.now());
        announcementMapper.updateById(existing);
        return existing;
    }

    /**
     * 教师删除公告
     */
    @Transactional
    public void deleteByTeacher(Long teacherId, Long announcementId) {
        Announcement existing = announcementMapper.selectById(announcementId);
        if (existing == null) {
            throw new RuntimeException("公告不存在");
        }
        if (!existing.getCreatedBy().equals(teacherId)) {
            throw new RuntimeException("无权删除此公告");
        }

        // 删除阅读记录
        LambdaQueryWrapper<AnnouncementRead> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AnnouncementRead::getAnnouncementId, announcementId);
        announcementReadMapper.delete(wrapper);

        // 删除公告
        announcementMapper.deleteById(announcementId);
    }

    /**
     * 查询教师发布的公告列表。
     * 设计原因：控制层返回 VO，避免直接暴露持久层实体。
     */
    public Map<String, Object> findByTeacher(Long teacherId, Long courseId, String status, int page, int size) {
        int offset = (page - 1) * size;
        List<Announcement> records = announcementMapper.findByTeacher(teacherId, courseId, status, offset, size);
        long total = announcementMapper.countByTeacher(teacherId, courseId, status);

        Map<String, Object> result = new HashMap<>();
        result.put("records", convertToVOList(records));
        result.put("total", total);
        result.put("pages", (total + size - 1) / size);
        result.put("current", page);
        result.put("size", size);
        return result;
    }

    /**
     * 获取公告阅读统计
     * 核心逻辑：根据公告类型（课程级/全局）和目标受众（ALL/TEACHER/STUDENT）计算目标人数，
     * 再基于已阅读人数计算阅读率。
     *
     * @param announcementId 公告ID
     * @return 包含阅读数、目标人数和阅读率的统计DTO
     */
    public AnnouncementStatsDTO getAnnouncementStats(Long announcementId) {
        Announcement announcement = announcementMapper.selectById(announcementId);
        if (announcement == null) {
            throw new RuntimeException("公告不存在");
        }

        AnnouncementStatsDTO stats = new AnnouncementStatsDTO();
        stats.setAnnouncement(convertToVO(announcement));
        stats.setReadCount(announcement.getReadCount() != null ? announcement.getReadCount() : 0);

        // 计算目标受众人数
        int targetCount = calculateTargetCount(announcement);
        stats.setTargetCount(targetCount);

        // 计算阅读率（保留1位小数）
        if (targetCount > 0) {
            double rate = (double) stats.getReadCount() / targetCount * 100;
            BigDecimal roundedRate = BigDecimal.valueOf(rate).setScale(1, RoundingMode.HALF_UP);
            stats.setReadRate(roundedRate.doubleValue());
        } else {
            stats.setReadRate(0.0);
        }

        return stats;
    }

    /**
     * 计算公告的目标受众人数。
     * 分类策略：
     * 1. 课程级公告（courseId != null）：通过 Feign 调用 course-service 获取该课程的选课学生数。
     * 2. 全局公告（courseId == null）：直接查询本地 user 表，按 targetAudience 过滤角色。
     *
     * @param announcement 公告实体
     * @return 目标受众人数
     */
    private int calculateTargetCount(Announcement announcement) {
        if (announcement.getCourseId() != null) {
            // 课程级公告：通过 Feign 调用获取该课程的选课学生数
            try {
                Result<Long> result = courseServiceClient.getCourseStudentCount(announcement.getCourseId());
                if (result != null && result.getData() != null) {
                    return result.getData().intValue();
                }
            } catch (Exception e) {
                log.warn("获取课程选课人数失败，courseId={}，降级返回0: {}", announcement.getCourseId(), e.getMessage());
            }
            return 0;
        }

        // 全局公告：根据目标受众查询本地 user 表
        String targetAudience = announcement.getTargetAudience();
        if (targetAudience == null || "ALL".equals(targetAudience)) {
            // 全体用户（排除已禁用/已删除的）
            Long count = userMapper.selectCount(
                    new LambdaQueryWrapper<User>().eq(User::getStatus, 1));
            return count != null ? count.intValue() : 0;
        }

        // 按角色过滤：TEACHER -> teacher, STUDENT -> student
        String role = targetAudience.toLowerCase();
        Long count = userMapper.selectCount(
                new LambdaQueryWrapper<User>()
                        .eq(User::getRole, role)
                        .eq(User::getStatus, 1));
        return count != null ? count.intValue() : 0;
    }

    /**
     * 公告实体转视图对象
     *
     * @param announcement 公告实体
     * @return 公告视图对象
     */
    public AnnouncementVO convertToVO(Announcement announcement) {
        if (announcement == null) {
            return null;
        }
        AnnouncementVO vo = new AnnouncementVO();
        BeanUtils.copyProperties(announcement, vo);
        return vo;
    }

    /**
     * 公告实体列表转视图对象列表
     *
     * @param announcements 公告实体列表
     * @return 公告视图对象列表
     */
    public List<AnnouncementVO> convertToVOList(List<Announcement> announcements) {
        if (announcements == null || announcements.isEmpty()) {
            return java.util.Collections.emptyList();
        }
        return announcements.stream()
                .map(this::convertToVO)
                .collect(java.util.stream.Collectors.toList());
    }

    /**
     * 记录用户阅读公告行为
     * 实现逻辑：
     * 1. 校验用户是否已阅读过此公告（避免重复计票）
     * 2. 写入 announcement_reads 表记录明细
     * 3. 异步（或原子操作）增加 announcements 表的 read_count
     *
     * @param announcementId 公告ID
     * @param userId         阅读用户ID
     */
    @Transactional
    public void recordRead(Long announcementId, Long userId) {
        // 1. 调用自定义 SQL 检查阅读记录是否存在
        int count = announcementReadMapper.checkUserRead(announcementId, userId);
        if (count > 0) {
            return; // 幂等性处理：已阅读则直接返回
        }

        // 2. 插入阅读流水
        AnnouncementRead read = new AnnouncementRead();
        read.setAnnouncementId(announcementId);
        read.setUserId(userId);
        read.setReadAt(LocalDateTime.now());
        announcementReadMapper.insert(read);

        // 3. 更新公告主表的累计阅读数
        announcementMapper.incrementReadCount(announcementId);
    }

    /**
     * 置顶/取消置顶公告
     */
    @Transactional
    public Announcement togglePin(Long teacherId, Long announcementId) {
        Announcement existing = announcementMapper.selectById(announcementId);
        if (existing == null) {
            throw new RuntimeException("公告不存在");
        }
        if (!existing.getCreatedBy().equals(teacherId)) {
            throw new RuntimeException("无权操作此公告");
        }

        existing.setIsPinned(!existing.getIsPinned());
        existing.setUpdatedAt(LocalDateTime.now());
        announcementMapper.updateById(existing);
        return existing;
    }

    /**
     * 定时任务：发布定时公告
     * 每分钟执行一次
     * 逻辑：查找状态为 SCHEDULED 且发布时间小于等于当前时间的公告，将其状态改为 PUBLISHED。
     */
    @Scheduled(fixedRate = 60000)
    public void publishScheduledAnnouncementsTask() {
        int count = announcementMapper.publishScheduledAnnouncements();
        if (count > 0) {
            // TODO: 可在此处集成通知服务，如 WebSocket 或邮件通知用户
        }
    }

    /**
     * 定时任务：更新过期公告
     * 每分钟执行一次
     */
    @Scheduled(fixedRate = 60000)
    public void updateExpiredAnnouncementsTask() {
        announcementMapper.updateExpiredAnnouncements();
    }
}
