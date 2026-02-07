package com.eduplatform.user.listener;

import com.eduplatform.common.event.EventType;
import com.eduplatform.common.event.RedisStreamConstants;
import com.eduplatform.common.result.Result;
import com.eduplatform.user.feign.CourseServiceClient;
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
 * 作业提交事件消费者
 * 消费 HOMEWORK_SUBMITTED 事件后，通知对应课程的教师进行批改。
 *
 * 消费流程：
 * 1. 从事件中提取 courseId
 * 2. 通过 Feign 调用 course-service 获取课程教师ID
 * 3. 通过 NotificationService 持久化通知并 WebSocket 推送
 * 4. ACK 确认消息消费成功
 *
 * @author Antigravity
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class HomeworkEventListener implements StreamListener<String, MapRecord<String, String, String>> {

    private final NotificationService notificationService;
    private final CourseServiceClient courseServiceClient;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    @Override
    public void onMessage(MapRecord<String, String, String> message) {
        try {
            Map<String, String> body = message.getValue();
            String dataJson = body.get("data");
            String messageId = body.get("id");

            log.info("收到作业提交事件: recordId={}, messageId={}", message.getId(), messageId);

            Map<String, Object> data = objectMapper.readValue(dataJson, new TypeReference<>() {});

            Long courseId = toLong(data.get("courseId"));
            Long homeworkId = toLong(data.get("homeworkId"));
            Long studentId = toLong(data.get("studentId"));
            String homeworkTitle = (String) data.get("homeworkTitle");

            // 通过 Feign 获取课程教师ID
            Long teacherId = getTeacherIdByCourse(courseId);

            if (teacherId != null) {
                String title = "学生提交了作业";
                String content = String.format("学生(ID:%d)提交了作业「%s」，请及时批改。", studentId, homeworkTitle);
                notificationService.send(teacherId, title, content, "homework", homeworkId);
            }

            // ACK 确认
            redisTemplate.opsForStream().acknowledge(
                    EventType.HOMEWORK_SUBMITTED.getStreamKey(),
                    RedisStreamConstants.GROUP_USER_SERVICE,
                    message.getId());

            log.info("作业提交事件处理完成: homeworkId={}, teacherId={}", homeworkId, teacherId);
        } catch (Exception e) {
            log.error("处理作业提交事件失败: recordId={}, error={}", message.getId(), e.getMessage(), e);
            // 不 ACK，消息将在 pending list 中等待重试
        }
    }

    /**
     * 通过课程服务获取教师ID
     */
    private Long getTeacherIdByCourse(Long courseId) {
        if (courseId == null) {
            return null;
        }
        try {
            Result<Map<String, Object>> result = courseServiceClient.getCourseById(courseId);
            if (result != null && result.getData() != null) {
                Object teacherIdObj = result.getData().get("teacherId");
                return teacherIdObj != null ? toLong(teacherIdObj) : null;
            }
        } catch (Exception e) {
            log.warn("获取课程教师信息失败: courseId={}, error={}", courseId, e.getMessage());
        }
        return null;
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
