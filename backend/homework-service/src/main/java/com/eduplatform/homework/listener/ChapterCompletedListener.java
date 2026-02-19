package com.eduplatform.homework.listener;

import com.eduplatform.common.event.EventType;
import com.eduplatform.common.event.RedisStreamConstants;
import com.eduplatform.homework.service.HomeworkService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.stream.StreamListener;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 章节完成事件消费者
 * 消费 CHAPTER_COMPLETED 事件后，自动解锁该章节关联的作业。
 *
 * 消费流程：
 * 1. 从事件中提取 studentId 和 chapterId
 * 2. 调用 HomeworkService.unlockHomeworkByChapter() 执行解锁
 * 3. ACK 确认消息消费成功
 *
 * @author Antigravity
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChapterCompletedListener implements StreamListener<String, MapRecord<String, String, String>> {

    private final HomeworkService homeworkService;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public void onMessage(MapRecord<String, String, String> message) {
        try {
            Map<String, String> body = message.getValue();
            String dataJson = body.get("data");
            String messageId = body.get("id");

            log.info("收到章节完成事件: recordId={}, messageId={}", message.getId(), messageId);

            Map<String, Object> data = objectMapper.readValue(dataJson, new TypeReference<>() {
            });

            Long studentId = toLong(data.get("studentId"));
            Long chapterId = toLong(data.get("chapterId"));

            if (studentId == null || chapterId == null) {
                log.warn("章节完成事件缺少必要参数: studentId={}, chapterId={}", studentId, chapterId);
                ackMessage(message);
                return;
            }

            // 调用 HomeworkService 解锁该章节关联的作业
            homeworkService.unlockHomeworkByChapter(studentId, chapterId);

            // ACK 确认
            ackMessage(message);

            log.info("章节完成事件处理完成（作业解锁）: studentId={}, chapterId={}", studentId, chapterId);
        } catch (Exception e) {
            log.error("处理章节完成事件失败: recordId={}, error={}", message.getId(), e.getMessage(), e);
            // 不 ACK，消息将在 pending list 中等待重试
        }
    }

    private void ackMessage(MapRecord<String, String, String> message) {
        redisTemplate.opsForStream().acknowledge(
                EventType.CHAPTER_COMPLETED.getStreamKey(),
                RedisStreamConstants.GROUP_HOMEWORK_SERVICE,
                message.getId());
    }

    private Long toLong(Object obj) {
        if (obj == null) {
            return null;
        }
        if (obj instanceof Number) {
            return ((Number) obj).longValue();
        }
        return Long.valueOf(obj.toString());
    }
}
