package com.eduplatform.common.event;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.connection.stream.StringRecord;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Redis Stream 通用消息发布器
 * 各微服务通过注入此组件发布异步事件到对应的 Stream，
 * 消费端通过 StreamMessageListenerContainer 自动监听并处理。
 *
 * 核心机制：
 * 1. 自动构建 EventMessage 并序列化为 Map 写入 Stream
 * 2. Stream 不存在时由 Redis 自动创建（XADD 的隐式行为）
 * 3. 使用 SLF4J 记录发布日志，便于链路追踪
 *
 * @author Antigravity
 */
@Slf4j
@Component
@ConditionalOnBean(StringRedisTemplate.class)
public class RedisStreamPublisher {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public RedisStreamPublisher(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = new ObjectMapper();
        this.objectMapper.registerModule(new JavaTimeModule());
    }

    /**
     * 发布事件到 Redis Stream
     *
     * @param type       事件类型枚举
     * @param sourceName 来源服务名（如 RedisStreamConstants.SERVICE_HOMEWORK）
     * @param data       业务数据载荷
     * @return Redis Stream 消息 ID（如 "1234567890-0"）；发布失败返回 null
     */
    public String publish(EventType type, String sourceName, Map<String, Object> data) {
        // 1. 构建统一消息体
        EventMessage message = EventMessage.builder()
                .id(UUID.randomUUID().toString())
                .type(type.name())
                .source(sourceName)
                .data(data)
                .timestamp(LocalDateTime.now())
                .build();

        // 2. 序列化为扁平 Map（Redis Stream 要求 Map<String, String>）
        Map<String, String> messageMap = new HashMap<>();
        try {
            messageMap.put("id", message.getId());
            messageMap.put("type", message.getType());
            messageMap.put("source", message.getSource());
            messageMap.put("timestamp", message.getTimestamp().toString());
            messageMap.put("data", objectMapper.writeValueAsString(message.getData()));
        } catch (JsonProcessingException e) {
            log.error("事件序列化失败: type={}, error={}", type, e.getMessage());
            return null;
        }

        // 3. 写入 Redis Stream（Stream 不存在时自动创建）
        String streamKey = type.getStreamKey();
        try {
            StringRecord record = StreamRecords.string(messageMap).withStreamKey(streamKey);
            RecordId recordId = redisTemplate.opsForStream().add(record);

            log.info("事件发布成功: stream={}, recordId={}, type={}, messageId={}",
                    streamKey, recordId, type, message.getId());

            return recordId != null ? recordId.getValue() : null;
        } catch (Exception e) {
            log.error("事件发布失败: stream={}, type={}, error={}", streamKey, type, e.getMessage());
            return null;
        }
    }
}
