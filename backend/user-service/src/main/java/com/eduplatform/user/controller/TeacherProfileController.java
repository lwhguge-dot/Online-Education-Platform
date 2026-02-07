package com.eduplatform.user.controller;

import com.eduplatform.common.result.Result;
import com.eduplatform.user.dto.TeacherProfileDTO;
import com.eduplatform.user.service.TeacherProfileService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

/**
 * 教师画像控制器。
 * 设计意图：提供教师端档案维护入口，隔离基础用户信息与教师职业信息的写入职责。
 */
@RestController
@RequestMapping("/api/teachers")
@RequiredArgsConstructor
public class TeacherProfileController {

    private final TeacherProfileService profileService;

    /**
     * 获取教师详细职业资料。
     * 逻辑说明：聚合用户基础信息与教师画像扩展字段，避免前端多次请求。
     *
     * @param userId 教师用户 ID
     * @return 封装好的 TeacherProfileDTO 资料数据
     */
    @GetMapping("/{userId}/profile")
    public Result<TeacherProfileDTO> getProfile(@PathVariable("userId") Long userId) {
        TeacherProfileDTO profile = profileService.getProfile(userId);
        return Result.success(profile);
    }

    /**
     * 更新教师职业资料。
     * 业务原因：教师画像与基础账号分表存储，此处统一写入扩展字段。
     *
     * @param userId 教师用户 ID
     * @param dto    包含待更新字段的传输对象
     */
    @PutMapping("/{userId}/profile")
    public Result<Void> updateProfile(
            @PathVariable("userId") Long userId,
            @RequestBody TeacherProfileDTO dto) {
        profileService.updateProfile(userId, dto);
        return Result.success("教师职业资料更新成功", null);
    }

    /**
     * 上传或更换教师头像。
     * 处理多部分文件上传，生成访问 URL 并同步关联至用户信息。
     * 说明：头像上传已统一到对象存储，控制层仅负责参数接收与异常透传。
     *
     * @param userId 教师用户 ID
     * @param file   Multipart 图片文件
     * @return 包含新头像 URL 的响应数据
     */
    @PostMapping("/{userId}/avatar")
    public Result<Map<String, String>> uploadAvatar(
            @PathVariable("userId") Long userId,
            @RequestParam("file") MultipartFile file) throws IOException {
        String avatarUrl = profileService.uploadAvatar(userId, file);
        return Result.success("教师头像上传成功", Map.of("avatarUrl", avatarUrl));
    }

    /**
     * 更新通知接收设置。
     * 业务原因：教师可自定义消息策略，减少无关通知打扰。
     */
    @PutMapping("/{userId}/notification-settings")
    public Result<Void> updateNotificationSettings(
            @PathVariable("userId") Long userId,
            @RequestBody TeacherProfileDTO.NotificationSettings settings) {
        profileService.updateNotificationSettings(userId, settings);
        return Result.success("教师通知推送设置已同步更新", null);
    }

    /**
     * 定义个性化评分标准。
     * 业务原因：教师评分策略差异较大，需持久化默认模板提升批改效率。
     */
    @PutMapping("/{userId}/grading-criteria")
    public Result<Void> updateGradingCriteria(
            @PathVariable("userId") Long userId,
            @RequestBody TeacherProfileDTO.GradingCriteria criteria) {
        profileService.updateGradingCriteria(userId, criteria);
        return Result.success("作业评分标准已持久化", null);
    }

    /**
     * 更新教师工作台布局。
     * 业务原因：教师后台工作流差异大，允许个性化布局以提升效率。
     */
    @PutMapping("/{userId}/dashboard-layout")
    public Result<Void> updateDashboardLayout(
            @PathVariable("userId") Long userId,
            @RequestBody TeacherProfileDTO.DashboardLayout layout) {
        profileService.updateDashboardLayout(userId, layout);
        return Result.success("工作台个性化布局已保存", null);
    }
}
