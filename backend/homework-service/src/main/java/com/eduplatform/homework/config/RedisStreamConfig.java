package com.eduplatform.homework.config;

import com.eduplatform.common.event.EventType;
import com.eduplatform.common.event.RedisStreamConstants;
import com.eduplatform.homework.listener.ChapterCompletedListener;
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

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.Executors;

/**
 * Redis Stream 消费者配置
 * homework-service 消费 CHAPTER_COMPLETED 事件，
 * 当学生完成章节学习后自动解锁对应作业。
 *
 * @author Antigravity
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class RedisStreamConfig {

    private final StringRedisTemplate redisTemplate;
    private final ChapterCompletedListener chapterCompletedListener;

    /**
     * 创建并启动 StreamMessageListenerContainer
     */
    @Bean
    public StreamMessageListenerContainer<String, MapRecord<String, String, String>>
            streamMessageListenerContainer(RedisConnectionFactory factory) {

        StreamMessageListenerContainer.StreamMessageListenerContainerOptions<String, MapRecord<String, String, String>> options =
                StreamMessageListenerContainer.StreamMessageListenerContainerOptions.builder()
                        .pollTimeout(Duration.ofSeconds(2))
                        .executor(Executors.newFixedThreadPool(1))
                        .build();

        StreamMessageListenerContainer<String, MapRecord<String, String, String>> container =
                StreamMessageListenerContainer.create(factory, options);

        // 幂等创建 Consumer Group
        createConsumerGroupIfAbsent(EventType.CHAPTER_COMPLETED.getStreamKey());

        String consumerName = RedisStreamConstants.SERVICE_HOMEWORK + ":1";
        String groupName = RedisStreamConstants.GROUP_HOMEWORK_SERVICE;

        // 注册章节完成事件监听器
        container.receive(
                Consumer.from(groupName, consumerName),
                StreamOffset.create(EventType.CHAPTER_COMPLETED.getStreamKey(), ReadOffset.lastConsumed()),
                chapterCompletedListener);

        container.start();
        log.info("homework-service Redis Stream 消费者已启动，监听 CHAPTER_COMPLETED 事件");

        return container;
    }

    /**
     * 幂等创建 Consumer Group
     */
    private void createConsumerGroupIfAbsent(String streamKey) {
        String groupName = RedisStreamConstants.GROUP_HOMEWORK_SERVICE;
        try {
            redisTemplate.opsForStream().createGroup(streamKey, ReadOffset.from("0"), groupName);
            log.info("创建 Consumer Group 成功: stream={}, group={}", streamKey, groupName);
        } catch (Exception e) {
            if (e.getMessage() != null && e.getMessage().contains("BUSYGROUP")) {
                log.debug("Consumer Group 已存在: stream={}, group={}", streamKey, groupName);
            } else {
                try {
                    redisTemplate.opsForStream().add(streamKey, Map.of("_init", "1"));
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
