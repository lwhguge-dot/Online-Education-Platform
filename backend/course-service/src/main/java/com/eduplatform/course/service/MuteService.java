package com.eduplatform.course.service;

import com.eduplatform.course.entity.MutedUser;
import com.eduplatform.course.mapper.MutedUserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 社交秩序维护服务 (禁言管理)
 * 负责课程讨论区的环境治理，为教师与管理员提供针对特定用户的言论管控工具。
 *
 * 核心功能：
 * 1. 权限拦截：实时判定用户在当前课程上下文下的发帖/评论特权。
 * 2. 行为溯源：详尽记录每一笔禁言操作的触发时间、违规原因及操作责任人。
 * 3. 禁言生命周期：支持社交权力的封禁（Mute）与恢复（Unmute），维护健康的教学互动环境。
 *
 * @author Antigravity
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MuteService {

    private final MutedUserMapper mutedUserMapper;

    /**
     * 实时验证用户是否处于禁言封锁期
     * 作为交互动作（如发布评论、加入讨论）的前置置信检查。
     *
     * @param userId   目标用户
     * @param courseId 课程上下文
     * @return true 表示该用户当前无权发表言论
     */
    public boolean isMuted(Long userId, Long courseId) {
        try {
            return mutedUserMapper.checkMuted(userId, courseId) > 0;
        } catch (Exception e) {
            log.error("社交合规异常：判定禁言态失败 userId={}, courseId={}", userId, courseId, e);
            // 降级策略：状态未知时暂不拦截，保证用户基本体验 (Fail-open)
            return false;
        }
    }

    /**
     * 检索指定用户的禁言深度明细
     * 用于前端向用户展示“因何被禁言、何时被禁、由谁执行”等反馈信息。
     */
    public Map<String, Object> getMuteInfo(Long userId, Long courseId) {
        try {
            Map<String, Object> info = mutedUserMapper.getMuteInfo(userId, courseId);
            if (info == null) {
                Map<String, Object> result = new HashMap<>();
                result.put("isMuted", false);
                return result;
            }

            Map<String, Object> result = new HashMap<>();
            result.put("isMuted", true);
            result.put("mutedAt", info.get("muted_at"));
            result.put("reason", info.get("reason"));
            result.put("mutedByName", info.get("muted_by_name"));
            return result;
        } catch (Exception e) {
            log.error("社交合规异常：获取禁言明细失败 userId={}, courseId={}", userId, courseId, e);
            Map<String, Object> result = new HashMap<>();
            result.put("isMuted", false);
            return result;
        }
    }

    /**
     * 执行禁言封锁
     * 业务规则：
     * 1. 幂等性：严禁对已处于禁言态的用户重复发起操作。
     * 2. 审计：记录操作责任人（mutedBy）及违规依据（reason）。
     */
    @Transactional
    public void muteUser(Long userId, Long courseId, Long mutedBy, String reason) {
        // 重复操作拦截
        if (isMuted(userId, courseId)) {
            throw new RuntimeException("合规性冲突：该用户已在此课程中被禁言");
        }

        MutedUser mutedUser = new MutedUser();
        mutedUser.setUserId(userId);
        mutedUser.setCourseId(courseId);
        mutedUser.setMutedBy(mutedBy);
        mutedUser.setReason(reason);
        mutedUser.setMutedAt(LocalDateTime.now());
        mutedUser.setStatus(1); // 激活记录

        mutedUserMapper.muteUser(mutedUser);
        log.info("审计：社交环境治理 | 用户 {} 在课程 {} 被禁言, 操作人: {}, 原因: {}",
                userId, courseId, mutedBy, reason);
    }

    /**
     * 释放社交权限 (解除禁言)
     */
    @Transactional
    public void unmuteUser(Long userId, Long courseId) {
        int affected = mutedUserMapper.unmuteUser(userId, courseId);
        if (affected == 0) {
            throw new RuntimeException("合规性冲突：该用户当前未被禁言，无需解除");
        }
        log.info("审计：社交环境治理 | 用户 {} 在课程 {} 已恢复发言权限", userId, courseId);
    }

    /**
     * 导出课程受限用户名录
     * 用于助教或老师管理课程社交环境的仪表盘。
     */
    public List<Map<String, Object>> getMuteRecords(Long courseId) {
        try {
            List<Map<String, Object>> records = mutedUserMapper.getMuteRecords(courseId);
            return records.stream().map(record -> {
                Map<String, Object> dto = new HashMap<>();
                dto.put("id", record.get("id"));
                dto.put("userId", record.get("user_id"));
                dto.put("userName", record.get("user_name"));
                dto.put("courseId", record.get("course_id"));
                dto.put("mutedBy", record.get("muted_by"));
                dto.put("mutedByName", record.get("muted_by_name"));
                dto.put("reason", record.get("reason"));
                dto.put("mutedAt", record.get("muted_at"));
                dto.put("unmutedAt", record.get("unmuted_at"));
                dto.put("status", record.get("status"));
                return dto;
            }).collect(Collectors.toList());
        } catch (Exception e) {
            log.error("数据聚合异常：获取禁言名录失败 courseId={}", courseId, e);
            return List.of();
        }
    }
}
