package com.eduplatform.user.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.eduplatform.common.exception.BusinessException;
import com.eduplatform.user.entity.User;
import com.eduplatform.user.feign.CourseServiceClient;
import com.eduplatform.user.mapper.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 用户级联删除服务
 * 删除用户时清除所有相关数据
 *
 * <p>事务与 RPC 模型：
 * <ul>
 *   <li>跨服务 Feign 调用（course-service）在 <b>本地事务之外</b> 执行，且失败抛 {@link BusinessException}
 *       终止流程，与 course-service 的 {@code CourseCascadeDeleteService} 保持策略一致，
 *       避免本地数据已清理但远端残留的分布式不一致。</li>
 *   <li>本地多表删除在单事务内原子提交。</li>
 * </ul>
 */
@Slf4j
@Service
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
    // 自注入 Spring 代理，用于调用本类的 @Transactional 方法，
    // 规避同类方法间 this 调用绕过 AOP 代理导致事务失效的问题。
    private final UserCascadeDeleteService self;

    public UserCascadeDeleteService(
            UserMapper userMapper,
            UserSessionMapper userSessionMapper,
            StudentProfileMapper studentProfileMapper,
            TeacherProfileMapper teacherProfileMapper,
            AnnouncementMapper announcementMapper,
            AnnouncementReadMapper announcementReadMapper,
            CourseServiceClient courseServiceClient,
            AuditLogService auditLogService,
            @Lazy UserCascadeDeleteService self) {
        this.userMapper = userMapper;
        this.userSessionMapper = userSessionMapper;
        this.studentProfileMapper = studentProfileMapper;
        this.teacherProfileMapper = teacherProfileMapper;
        this.announcementMapper = announcementMapper;
        this.announcementReadMapper = announcementReadMapper;
        this.courseServiceClient = courseServiceClient;
        this.auditLogService = auditLogService;
        this.self = self;
    }

    /**
     * 级联删除用户及其关联的所有数据记录。
     *
     * <p>执行顺序（关键）：
     * <ol>
     *   <li>安全校验：严禁删除 root 超级管理员。</li>
     *   <li>跨服务同步：在事务外调用 course-service 清理远端数据；失败抛异常终止。</li>
     *   <li>本地事务：删除 session/profile/announcement 等本地表。</li>
     *   <li>审计日志写入。</li>
     * </ol>
     *
     * @param userId       目标用户ID
     * @param operatorId   操作人ID
     * @param operatorName 操作人用户名
     * @param ipAddress    操作客户端 IP
     */
    public void cascadeDeleteUser(Long userId, Long operatorId, String operatorName, String ipAddress) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(404, "用户不存在");
        }

        // 安全闸门：根管理员受系统级保护
        if (ROOT_USERNAME.equalsIgnoreCase(user.getUsername()) || ROOT_EMAIL.equalsIgnoreCase(user.getEmail())) {
            throw new BusinessException(403, "不能删除root管理员账号");
        }

        String username = user.getUsername();
        String role = user.getRole();
        log.info("开始级联删除用户: userId={}, username={}, role={}", userId, username, role);

        // 1. 跨服务调用：在事务外清理 Course-Service 中的关联业务数据
        //    失败抛异常终止流程，保持两端策略一致，避免分布式不一致。
        try {
            courseServiceClient.deleteUserRelatedData(userId, role);
            log.info("调用课程服务删除用户相关数据成功");
        } catch (Exception e) {
            log.error("调用课程服务删除用户相关数据失败: userId={}", userId, e);
            throw new BusinessException("级联删除失败：课程服务不可用，请稍后重试");
        }

        // 2. 本地事务：原子清理所有本地关联表
        // 通过 self 代理调用，确保 @Transactional 生效。
        self.doLocalCascadeDelete(userId);

        // 3. 写入管理审计日志（无事务，独立写入）
        auditLogService.log(operatorId, operatorName, "USER_DELETE", "USER", userId, username,
                "级联删除用户及所有相关数据", ipAddress);

        log.info("用户级联删除完成: userId={}", userId);
    }

    /**
     * 本地多表原子删除（独立事务）。
     * 必须通过 self 代理调用以触发事务，禁止直接 this 调用。
     */
    @Transactional
    public void doLocalCascadeDelete(Long userId) {
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

        // 5. 执行物理/逻辑删除
        userMapper.deleteById(userId);
        log.info("删除用户记录成功");
    }
}
