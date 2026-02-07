package com.eduplatform.common.event;

/**
 * Redis Stream 常量定义
 * 集中管理所有 Stream Key 前缀、Consumer Group 名称等配置，
 * 避免硬编码分散在各服务中。
 *
 * @author Antigravity
 */
public final class RedisStreamConstants {

    private RedisStreamConstants() {
        // 工具类禁止实例化
    }

    /** Stream Key 前缀：所有教育平台事件统一使用此前缀 */
    public static final String STREAM_PREFIX = "stream:edu:";

    /** Consumer Group 前缀：各服务通过 "group:{服务名}" 标识消费者组 */
    public static final String GROUP_PREFIX = "group:";

    // ==================== 服务名常量 ====================

    public static final String SERVICE_USER = "user-service";
    public static final String SERVICE_COURSE = "course-service";
    public static final String SERVICE_HOMEWORK = "homework-service";
    public static final String SERVICE_PROGRESS = "progress-service";

    // ==================== Consumer Group 名称 ====================

    public static final String GROUP_USER_SERVICE = GROUP_PREFIX + SERVICE_USER;
    public static final String GROUP_COURSE_SERVICE = GROUP_PREFIX + SERVICE_COURSE;
    public static final String GROUP_HOMEWORK_SERVICE = GROUP_PREFIX + SERVICE_HOMEWORK;
    public static final String GROUP_PROGRESS_SERVICE = GROUP_PREFIX + SERVICE_PROGRESS;
}
