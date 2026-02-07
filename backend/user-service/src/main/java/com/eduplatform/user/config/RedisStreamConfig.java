package com.eduplatform.user.config;

import com.eduplatform.common.event.EventType;
import com.eduplatform.common.event.RedisStreamConstants;
import com.eduplatform.user.listener.AnnouncementEventListener;
import com.eduplatform.user.listener.EnrollmentEventListener;
import com.eduplatform.user.listener.HomeworkEventListener;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.stream.StreamMessageListenerContainer;
import org.springframework.data.redis.stream.Subscription;

import java.time.Duration;
import java.util.concurrent.Executors;

/**
 * Redis Stream 消费者配置
 * user-service 作为核心消费者，监听多种业务事件并分发给对应的 Listener 处理：
 * - HOMEWORK_SUBMITTED → 通知教师批改
 * - COURSE_ENROLLED / COURSE_DROPPED → 选课/退课通知
 * - ANNOUNCEMENT_PUBLISHED → WebSocket 公告推送
 *
 * @author Antigravity
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class RedisStreamConfig {

    private final StringRedisTemplate redisTemplate;
    private final HomeworkEventListener homeworkEventListener;
    private final EnrollmentEventListener enrollmentEventListener;
    private final AnnouncementEventListener announcementEventListener;

    /**
     * 创建并启动 StreamMessageListenerContainer
     * 统一管理所有 Stream 的消费者订阅生命周期。
     */
    @Bean
    public StreamMessageListenerContainer<String, MapRecord<String, String, String>>
            streamMessageListenerContainer(RedisConnectionFactory factory) {

        // 消费线程池配置
        StreamMessageListenerContainer.StreamMessageListenerContainerOptions<String, MapRecord<String, String, String>> options =
                StreamMessageListenerContainer.StreamMessageListenerContainerOptions.builder()
                        .pollTimeout(Duration.ofSeconds(2))
                        .executor(Executors.newFixedThreadPool(3))
                        .build();

        StreamMessageListenerContainer<String, MapRecord<String, String, String>> container =
                StreamMessageListenerContainer.create(factory, options);

        // 幂等创建 Consumer Group（若 Stream 或 Group 不存在则初始化）
        createConsumerGroupIfAbsent(EventType.HOMEWORK_SUBMITTED.getStreamKey());
        createConsumerGroupIfAbsent(EventType.COURSE_ENROLLED.getStreamKey());
        createConsumerGroupIfAbsent(EventType.COURSE_DROPPED.getStreamKey());
        createConsumerGroupIfAbsent(EventType.ANNOUNCEMENT_PUBLISHED.getStreamKey());

        String consumerName = RedisStreamConstants.SERVICE_USER + ":1";
        String groupName = RedisStreamConstants.GROUP_USER_SERVICE;

        // 注册各事件监听器
        container.receive(
                Consumer.from(groupName, consumerName),
                StreamOffset.create(EventType.HOMEWORK_SUBMITTED.getStreamKey(), ReadOffset.lastConsumed()),
                homeworkEventListener);

        container.receive(
                Consumer.from(groupName, consumerName),
                StreamOffset.create(EventType.COURSE_ENROLLED.getStreamKey(), ReadOffset.lastConsumed()),
                enrollmentEventListener);

        container.receive(
                Consumer.from(groupName, consumerName),
                StreamOffset.create(EventType.COURSE_DROPPED.getStreamKey(), ReadOffset.lastConsumed()),
                enrollmentEventListener);

        container.receive(
                Consumer.from(groupName, consumerName),
                StreamOffset.create(EventType.ANNOUNCEMENT_PUBLISHED.getStreamKey(), ReadOffset.lastConsumed()),
                announcementEventListener);

        container.start();
        log.info("user-service Redis Stream 消费者已启动，监听 4 个事件流");

        return container;
    }

    /**
     * 幂等创建 Consumer Group
     * 如果 Stream 不存在，先通过 XADD 创建空 Stream，再执行 XGROUP CREATE。
     */
    private void createConsumerGroupIfAbsent(String streamKey) {
        String groupName = RedisStreamConstants.GROUP_USER_SERVICE;
        try {
            redisTemplate.opsForStream().createGroup(streamKey, ReadOffset.from("0"), groupName);
            log.info("创建 Consumer Group 成功: stream={}, group={}", streamKey, groupName);
        } catch (Exception e) {
            // BUSYGROUP 说明 Group 已存在，属于正常情况
            if (e.getMessage() != null && e.getMessage().contains("BUSYGROUP")) {
                log.debug("Consumer Group 已存在: stream={}, group={}", streamKey, groupName);
            } else {
                // Stream 可能不存在，先创建再重试
                try {
                    redisTemplate.opsForStream().add(streamKey, java.util.Map.of("_init", "1"));
                    redisTemplate.opsForStream().createGroup(streamKey, ReadOffset.from("0"), groupName);
                    log.info("初始化 Stream 并创建 Consumer Group: stream={}, group={}", streamKey, groupName);
                } catch (Exception ex) {
                    if (ex.getMessage() != null && ex.getMessage().contains("BUSYGROUP")) {
                        log.debug("Consumer Group 已存在（重试后）: stream={}, group={}", streamKey, groupName);
                    } else {
                        log.warn("创建 Consumer Group 失败: stream={}, error={}", streamKey, ex.getMessage());
                    }
                }
            }
        }
    }
}
