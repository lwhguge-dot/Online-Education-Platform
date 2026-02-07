package com.eduplatform.user.listener;

import com.eduplatform.common.event.EventType;
import com.eduplatform.common.event.RedisStreamConstants;
import com.eduplatform.user.service.NotificationService;
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
 * 选课/退课事件消费者
 * 消费 COURSE_ENROLLED 和 COURSE_DROPPED 事件，
 * 向学生发送选课确认/退课确认通知。
 *
 * @author Antigravity
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EnrollmentEventListener implements StreamListener<String, MapRecord<String, String, String>> {

    private final NotificationService notificationService;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public void onMessage(MapRecord<String, String, String> message) {
        try {
            Map<String, String> body = message.getValue();
            String dataJson = body.get("data");
            String eventType = body.get("type");
            String messageId = body.get("id");

            log.info("收到选课事件: recordId={}, type={}, messageId={}", message.getId(), eventType, messageId);

            Map<String, Object> data = objectMapper.readValue(dataJson, new TypeReference<>() {});

            Long studentId = toLong(data.get("studentId"));
            Long courseId = toLong(data.get("courseId"));
            String courseName = (String) data.get("courseName");

            if (studentId == null) {
                log.warn("选课事件缺少 studentId，跳过处理");
                ackMessage(message);
                return;
            }

            // 根据事件类型生成不同的通知文案
            if (EventType.COURSE_ENROLLED.name().equals(eventType)) {
                String title = "选课成功";
                String content = String.format("您已成功报名课程「%s」，开始学习之旅吧！",
                        courseName != null ? courseName : "课程#" + courseId);
                notificationService.send(studentId, title, content, "course", courseId);
            } else if (EventType.COURSE_DROPPED.name().equals(eventType)) {
                String title = "退课确认";
                String content = String.format("您已退出课程「%s」的学习。",
                        courseName != null ? courseName : "课程#" + courseId);
                notificationService.send(studentId, title, content, "course", courseId);
            }

            // ACK 确认（选课和退课 Stream 共用此 Listener，需根据 Stream Key ACK）
            ackMessage(message);

            log.info("选课事件处理完成: type={}, studentId={}, courseId={}", eventType, studentId, courseId);
        } catch (Exception e) {
            log.error("处理选课事件失败: recordId={}, error={}", message.getId(), e.getMessage(), e);
        }
    }

    private void ackMessage(MapRecord<String, String, String> message) {
        redisTemplate.opsForStream().acknowledge(
                message.getStream(),
                RedisStreamConstants.GROUP_USER_SERVICE,
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
