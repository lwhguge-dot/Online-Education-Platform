package com.eduplatform.common.event;

/**
 * 平台事件类型枚举
 * 定义所有通过 Redis Stream 传输的异步事件类型，
 * 每个枚举值对应一个独立的 Stream Key，实现事件的分类路由。
 *
 * @author Antigravity
 */
public enum EventType {

    /**
     * 学生提交作业事件
     * 生产者：homework-service
     * 消费者：user-service（通知教师批改）
     */
    HOMEWORK_SUBMITTED("homework-submitted", "学生提交作业"),

    /**
     * 公告发布事件
     * 生产者：user-service（定时任务发布 / 即时发布）
     * 消费者：user-service（WebSocket 推送给目标用户）
     */
    ANNOUNCEMENT_PUBLISHED("announcement-published", "公告发布"),

    /**
     * 章节学习完成事件
     * 生产者：progress-service
     * 消费者：homework-service（解锁作业）、progress-service（授予徽章）
     */
    CHAPTER_COMPLETED("chapter-completed", "章节学习完成"),

    /**
     * 学生选课事件
     * 生产者：course-service
     * 消费者：user-service（通知）
     */
    COURSE_ENROLLED("course-enrolled", "学生选课"),

    /**
     * 学生退课事件
     * 生产者：course-service
     * 消费者：user-service（通知）
     */
    COURSE_DROPPED("course-dropped", "学生退课");

    private final String streamSuffix;
    private final String description;

    EventType(String streamSuffix, String description) {
        this.streamSuffix = streamSuffix;
        this.description = description;
    }

    /**
     * 获取 Stream Key 后缀（与 RedisStreamConstants.STREAM_PREFIX 拼接使用）
     */
    public String getStreamSuffix() {
        return streamSuffix;
    }

    public String getDescription() {
        return description;
    }

    /**
     * 获取完整的 Redis Stream Key
     * 格式：stream:edu:{事件类型后缀}
     */
    public String getStreamKey() {
        return RedisStreamConstants.STREAM_PREFIX + streamSuffix;
    }
}
