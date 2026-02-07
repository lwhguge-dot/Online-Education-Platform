package com.eduplatform.user.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.eduplatform.user.dto.UserSettingsDTO;
import com.eduplatform.user.entity.StudentProfile;
import com.eduplatform.user.mapper.StudentProfileMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 学生档案服务
 * 维护学生的个性化设置（学习目标、通知偏好）及学业统计数据。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StudentProfileService {

    private final StudentProfileMapper profileMapper;
    private final ObjectMapper objectMapper;

    /**
     * 获取学生的个性化配置
     * 逻辑：
     * 1. 自动确保用户拥有 Profile 记录（不存在则创建）
     * 2. 将数据库中的 JSON 字符串反序列化为 UserSettingsDTO 对象
     *
     * @param userId 用户ID
     * @return 包含通知设置和学习目标的传输对象
     */
    public UserSettingsDTO getUserSettings(Long userId) {
        StudentProfile profile = getOrCreateProfile(userId);

        UserSettingsDTO settings = new UserSettingsDTO();

        // 解析通知推送详情
        if (profile.getNotificationSettings() != null && !profile.getNotificationSettings().isEmpty()) {
            try {
                settings.setNotificationSettings(
                        objectMapper.readValue(profile.getNotificationSettings(),
                                UserSettingsDTO.NotificationSettings.class));
            } catch (JsonProcessingException e) {
                log.error("解析通知设置失败: {}", e.getMessage());
                settings.setNotificationSettings(new UserSettingsDTO.NotificationSettings());
            }
        } else {
            settings.setNotificationSettings(new UserSettingsDTO.NotificationSettings());
        }

        // 解析个性化学习目标
        if (profile.getStudyGoal() != null && !profile.getStudyGoal().isEmpty()) {
            try {
                settings.setStudyGoal(
                        objectMapper.readValue(profile.getStudyGoal(),
                                UserSettingsDTO.StudyGoal.class));
            } catch (JsonProcessingException e) {
                log.error("解析学习目标失败: {}", e.getMessage());
                settings.setStudyGoal(new UserSettingsDTO.StudyGoal());
            }
        } else {
            settings.setStudyGoal(new UserSettingsDTO.StudyGoal());
        }

        return settings;
    }

    /**
     * 更新学生设置
     * 逻辑：将 DTO 对象序列化为 JSON 字符串存储，以支持动态扩展配置项。
     *
     * @param userId   用户ID
     * @param settings 新的设置数据
     * @return 更新后的完整设置
     */
    @Transactional
    public UserSettingsDTO updateUserSettings(Long userId, UserSettingsDTO settings) {
        StudentProfile profile = getOrCreateProfile(userId);

        try {
            // 序列化通知偏好
            if (settings.getNotificationSettings() != null) {
                profile.setNotificationSettings(
                        objectMapper.writeValueAsString(settings.getNotificationSettings()));
            }

            // 序列化学习目标
            if (settings.getStudyGoal() != null) {
                profile.setStudyGoal(
                        objectMapper.writeValueAsString(settings.getStudyGoal()));
            }

            profileMapper.updateById(profile);
            log.info("用户 {} 设置已更新", userId);

        } catch (JsonProcessingException e) {
            log.error("序列化设置失败: {}", e.getMessage());
            throw new RuntimeException("保存设置失败");
        }

        return getUserSettings(userId);
    }

    /**
     * 获取或初始化创建学生档案
     * 确保后续业务调用的健壮性，防止 NPE。
     */
    private StudentProfile getOrCreateProfile(Long userId) {
        StudentProfile profile = profileMapper.selectOne(
                new LambdaQueryWrapper<StudentProfile>()
                        .eq(StudentProfile::getUserId, userId));

        if (profile == null) {
            profile = new StudentProfile();
            profile.setUserId(userId);
            profile.setStudyDays(0);
            profile.setTotalStudyTime(0);
            profileMapper.insert(profile);
            log.info("为用户 {} 创建学生档案", userId);
        }

        return profile;
    }
}
