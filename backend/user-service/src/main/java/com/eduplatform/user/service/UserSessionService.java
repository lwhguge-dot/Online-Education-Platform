package com.eduplatform.user.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.eduplatform.user.entity.UserSession;
import com.eduplatform.user.mapper.UserSessionMapper;
import com.eduplatform.user.vo.UserSessionVO;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.redis.core.StringRedisTemplate;
import java.util.concurrent.TimeUnit;

/**
 * 用户会话管理服务
 * 结合 Redis 与 MySQL 实现高性能的会话追踪与单点登录安全策略。
 */
@lombok.extern.slf4j.Slf4j
@Service
@RequiredArgsConstructor
public class UserSessionService {

    private final UserSessionMapper sessionMapper;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    private static final String SESSION_KEY_PREFIX = "session:";

    @Value("${session.timeout-seconds:120}")
    private int sessionTimeoutSeconds;

    /**
     * 校验用户当前是否存在有效的在线会话
     *
     * @param userId 用户标识
     * @return true 若用户处于在线状态
     */
    public boolean hasOnlineSession(Long userId) {
        // 先触发一次清理过期记录，保证计数准确
        cleanupExpiredSessions(userId);

        Long count = sessionMapper.selectCount(
                new LambdaQueryWrapper<UserSession>()
                        .eq(UserSession::getUserId, userId)
                        .eq(UserSession::getStatus, UserSession.STATUS_ONLINE));
        return count != null && count > 0;
    }

    /**
     * 创建并持久化新会话
     * 1. 生成全局唯一的 jti 作为 JWT 的唯一标识。
     * 2. 写入 MySQL 用于审计与会话历史追踪。
     * 3. 写入 Redis 并设置 TTL，用于高频拦截器校验。
     *
     * @param userId 用户标识
     * @return 生成的 jti 字符串
     */
    @Transactional
    public String createSession(Long userId) {
        String jti = UUID.randomUUID().toString().replace("-", "");

        UserSession session = new UserSession();
        session.setUserId(userId);
        session.setJti(jti);
        session.setStatus(UserSession.STATUS_ONLINE);
        session.setLoginTime(LocalDateTime.now());
        session.setLastActiveTime(LocalDateTime.now());
        session.setCreatedAt(LocalDateTime.now());
        session.setUpdatedAt(LocalDateTime.now());

        sessionMapper.insert(session);

        // 同步缓存至 Redis，过期时间与 Token 有效期同步
        cacheSession(session);

        return jti;
    }

    /**
     * 高频校验：检查会话标识 jti 是否仍然有效
     * 优先通过 Redis 进行 O(1) 级别的判定，减少 DB 负载。
     *
     * @param jti JWT 标识
     * @return true 若 Redis 中存在该会话且未过期
     */
    public boolean isSessionValid(String jti) {
        String key = SESSION_KEY_PREFIX + jti;
        try {
            return Boolean.TRUE.equals(redisTemplate.hasKey(key));
        } catch (Exception e) {
            // Redis 宕机时的降级策略可在此扩展，暂选安全失败
            return false;
        }
    }

    /**
     * 心跳维持与续期
     * 更新 Redis 的 TTL 并同步更新 DB 最后活跃时间。
     */
    public boolean heartbeat(String jti) {
        String key = SESSION_KEY_PREFIX + jti;
        try {
            Boolean expire = redisTemplate.expire(key, sessionTimeoutSeconds, TimeUnit.SECONDS);
            if (Boolean.TRUE.equals(expire)) {
                // 异步更新数据库活跃时间 (生产环境建议使用消息队列或异步线程)
                sessionMapper.updateLastActiveTime(jti);
                return true;
            }
        } catch (Exception e) {
            log.error("Redis session heartbeat failed for JTI: {}", jti, e);
        }
        return false;
    }

    /**
     * 用户注销逻辑
     * 清理 Redis 缓存并同步将 DB 状态置为下线。
     */
    public void logout(String jti) {
        redisTemplate.delete(SESSION_KEY_PREFIX + jti);
        sessionMapper.offlineByJti(jti);
    }

    /**
     * 强制踢出该用户的所有在线会话 (单点登录 SSO 核心逻辑)
     * 批量清理该用户在多端产生的所有 Redis 会话 Key。
     */
    public void forceOfflineUser(Long userId) {
        // 1. 获取该用户名下所有处于 ONLINE 状态的会话
        java.util.List<UserSession> sessions = sessionMapper.selectList(
                new LambdaQueryWrapper<UserSession>()
                        .eq(UserSession::getUserId, userId)
                        .eq(UserSession::getStatus, UserSession.STATUS_ONLINE));

        // 2. 批量从缓存中剔除，实现实时封禁/踢出
        if (sessions != null && !sessions.isEmpty()) {
            java.util.List<String> keys = sessions.stream()
                    .map(s -> SESSION_KEY_PREFIX + s.getJti())
                    .collect(java.util.stream.Collectors.toList());
            redisTemplate.delete(keys);
        }

        // 3. MySQL 状态统一流转为 OFFLINE
        sessionMapper.offlineAllByUserId(userId);
    }

    /**
     * 内部辅助：序列化并缓存会话对象
     */
    private void cacheSession(UserSession session) {
        try {
            String key = SESSION_KEY_PREFIX + session.getJti();
            String value = objectMapper.writeValueAsString(session);
            redisTemplate.opsForValue().set(key, value, sessionTimeoutSeconds, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new RuntimeException("Redis 会话缓存同步失败", e);
        }
    }

    /**
     * 内部辅助：自动清理数据库中因异常中断未下线的过期记录
     */
    private void cleanupExpiredSessions(Long userId) {
        if (sessionTimeoutSeconds <= 0)
            return;

        LambdaQueryWrapper<UserSession> wrapper = new LambdaQueryWrapper<UserSession>()
                .eq(UserSession::getUserId, userId)
                .eq(UserSession::getStatus, UserSession.STATUS_ONLINE)
                .lt(UserSession::getLastActiveTime, LocalDateTime.now().minusSeconds(sessionTimeoutSeconds));

        UserSession update = new UserSession();
        update.setStatus(UserSession.STATUS_OFFLINE);
        update.setLogoutTime(LocalDateTime.now());
        sessionMapper.update(update, wrapper);
    }

    /**
     * 清理所有用户的过期在线会话
     * 将超过超时时间未活跃的会话标记为离线
     */
    private void cleanupAllExpiredSessions() {
        if (sessionTimeoutSeconds <= 0)
            return;

        LambdaQueryWrapper<UserSession> wrapper = new LambdaQueryWrapper<UserSession>()
                .eq(UserSession::getStatus, UserSession.STATUS_ONLINE)
                .lt(UserSession::getLastActiveTime, LocalDateTime.now().minusSeconds(sessionTimeoutSeconds));

        UserSession update = new UserSession();
        update.setStatus(UserSession.STATUS_OFFLINE);
        update.setLogoutTime(LocalDateTime.now());
        sessionMapper.update(update, wrapper);
    }

    /**
     * 查询会话详情
     */
    public UserSession getSessionByJti(String jti) {
        return sessionMapper.selectOne(
                new LambdaQueryWrapper<UserSession>().eq(UserSession::getJti, jti));
    }

    /**
     * 全系统实时在线人数统计
     * 只统计在会话超时时间内有活动的用户（真正在线的用户）
     */
    public long countOnlineUsers() {
        // 先清理过期的在线会话
        cleanupAllExpiredSessions();

        // 只统计状态为 ONLINE 且最后活跃时间在超时窗口内的会话
        // 按用户去重，避免同一用户多个会话被重复计算
        if (sessionTimeoutSeconds <= 0) {
            // 如果超时时间未配置，只按状态统计
            return sessionMapper.selectCount(
                    new LambdaQueryWrapper<UserSession>()
                            .eq(UserSession::getStatus, UserSession.STATUS_ONLINE));
        }

        LocalDateTime activeThreshold = LocalDateTime.now().minusSeconds(sessionTimeoutSeconds);
        java.util.List<UserSession> activeSessions = sessionMapper.selectList(
                new LambdaQueryWrapper<UserSession>()
                        .eq(UserSession::getStatus, UserSession.STATUS_ONLINE)
                        .ge(UserSession::getLastActiveTime, activeThreshold)
                        .select(UserSession::getUserId));

        // 按用户ID去重后返回数量
        return activeSessions.stream()
                .map(UserSession::getUserId)
                .distinct()
                .count();
    }

    /**
     * 获取用户登录历史流水
     */
    public java.util.List<UserSession> getUserSessions(Long userId) {
        return sessionMapper.selectList(
                new LambdaQueryWrapper<UserSession>()
                        .eq(UserSession::getUserId, userId)
                        .orderByDesc(UserSession::getLoginTime));
    }

    /**
     * 获取当前系统所有活跃用户的 ID 列表
     * 只返回在会话超时时间内有活动的用户
     */
    public java.util.List<Long> getAllOnlineUserIds() {
        // 先清理过期会话
        cleanupAllExpiredSessions();

        LambdaQueryWrapper<UserSession> wrapper = new LambdaQueryWrapper<UserSession>()
                .eq(UserSession::getStatus, UserSession.STATUS_ONLINE)
                .select(UserSession::getUserId);

        // 如果配置了超时时间，增加活跃时间过滤条件
        if (sessionTimeoutSeconds > 0) {
            LocalDateTime activeThreshold = LocalDateTime.now().minusSeconds(sessionTimeoutSeconds);
            wrapper.ge(UserSession::getLastActiveTime, activeThreshold);
        }

        java.util.List<UserSession> onlineSessions = sessionMapper.selectList(wrapper);
        return onlineSessions.stream()
                .map(UserSession::getUserId)
                .distinct()
                .collect(java.util.stream.Collectors.toList());
    }

    /**
     * 将会话实体转换为视图对象
     * 用于控制层输出，隔离持久层字段。
     *
     * @param session 会话实体
     * @return 会话视图对象
     */
    public UserSessionVO convertToVO(UserSession session) {
        if (session == null) {
            return null;
        }
        UserSessionVO vo = new UserSessionVO();
        BeanUtils.copyProperties(session, vo);
        return vo;
    }

    /**
     * 批量转换会话实体列表为视图对象列表
     *
     * @param sessions 会话实体列表
     * @return 会话视图对象列表
     */
    public java.util.List<UserSessionVO> convertToVOList(java.util.List<UserSession> sessions) {
        if (sessions == null || sessions.isEmpty()) {
            return java.util.Collections.emptyList();
        }
        return sessions.stream()
                .map(this::convertToVO)
                .collect(java.util.stream.Collectors.toList());
    }
}
