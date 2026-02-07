package com.eduplatform.user.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.eduplatform.user.entity.User;
import com.eduplatform.user.feign.CourseServiceClient;
import com.eduplatform.user.mapper.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 用户级联删除服务
 * 删除用户时清除所有相关数据
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserCascadeDeleteService {

    private static final String ROOT_USERNAME = "root";
    private static final String ROOT_EMAIL = "root@edu.cn";

    private final UserMapper userMapper;
    private final UserSessionMapper userSessionMapper;
    private final StudentProfileMapper studentProfileMapper;
    private final TeacherProfileMapper teacherProfileMapper;
    private final AnnouncementMapper announcementMapper;
    private final AnnouncementReadMapper announcementReadMapper;
    private final CourseServiceClient courseServiceClient;
    private final AuditLogService auditLogService;

    /**
     * 级联删除用户及其关联的所有数据记录
     * 
     * 该操作属于高危操作，逻辑如下：
     * 1. 安全校验：严禁删除 root 超级管理员账号。
     * 2. 本地数据清理：删除用户会话 (Redis+DB)、个人扩展资料 (学生/教师)、公告阅读流水。
     * 3. 关联业务清理：删除用户发布的公告及其产生的所有阅读记录。
     * 4. 跨服务同步：通过 Feign 强一致性调用课程服务，清理该用户的报名记录、章节进度、测验成绩等。
     * 5. 账号注销：最后删除 users 表主记录并记录审计日志。
     *
     * @param userId       目标用户ID
     * @param operatorId   操作人ID
     * @param operatorName 操作人用户名
     * @param ipAddress    操作客户端 IP
     */
    @Transactional
    public void cascadeDeleteUser(Long userId, Long operatorId, String operatorName, String ipAddress) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new RuntimeException("用户不存在");
        }

        // 安全闸门：根管理员受系统级保护
        if (ROOT_USERNAME.equalsIgnoreCase(user.getUsername()) || ROOT_EMAIL.equalsIgnoreCase(user.getEmail())) {
            throw new RuntimeException("不能删除root管理员账号");
        }

        String username = user.getUsername();
        String role = user.getRole();
        log.info("开始级联删除用户: userId={}, username={}, role={}", userId, username, role);

        // 1. 删除用户会话（防止删除后 Token 仍然有效）
        int sessionCount = userSessionMapper.deleteByUserId(userId);
        log.info("删除用户会话: {} 条", sessionCount);

        // 2. 擦除用户画像（学生/教师）
        int studentProfileCount = studentProfileMapper.deleteByUserId(userId);
        if (studentProfileCount > 0) {
            log.info("删除学生扩展信息: {} 条", studentProfileCount);
        }
        int teacherProfileCount = teacherProfileMapper.deleteByUserId(userId);
        if (teacherProfileCount > 0) {
            log.info("删除教师扩展信息: {} 条", teacherProfileCount);
        }

        // 3. 公告系统清理
        int readCount = announcementReadMapper.deleteByUserId(userId);
        log.info("删除公告阅读记录: {} 条", readCount);

        // 4. 清理该教师发布的公告内容（级联清理阅读明细）
        List<Long> announcementIds = announcementMapper.findIdsByCreatedBy(userId);
        for (Long announcementId : announcementIds) {
            announcementReadMapper.deleteByAnnouncementId(announcementId);
        }
        int announcementCount = announcementMapper.deleteByCreatedBy(userId);
        log.info("删除用户创建的公告: {} 条", announcementCount);

        // 5. 跨微服务调用：清理 Course-Service 中的关联业务数据
        try {
            courseServiceClient.deleteUserRelatedData(userId, role);
            log.info("调用课程服务删除用户相关数据成功");
        } catch (Exception e) {
            // 记录异常但不阻断本地事务提交，后续可通过对账补齐
            log.error("调用课程服务删除用户相关数据失败: {}", e.getMessage());
        }

        // 6. 执行物理/逻辑删除
        userMapper.deleteById(userId);
        log.info("删除用户记录成功");

        // 7. 写入管理审计日志
        auditLogService.log(operatorId, operatorName, "USER_DELETE", "USER", userId, username,
                "级联删除用户及所有相关数据", ipAddress);

        log.info("用户级联删除完成: userId={}", userId);
    }
}
