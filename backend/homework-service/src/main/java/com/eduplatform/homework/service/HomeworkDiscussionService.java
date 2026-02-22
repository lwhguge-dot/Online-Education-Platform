package com.eduplatform.homework.service;

import com.eduplatform.homework.dto.NotificationRequest;
import com.eduplatform.homework.entity.Homework;
import com.eduplatform.homework.entity.HomeworkQuestionDiscussion;
import com.eduplatform.homework.feign.UserServiceClient;
import com.eduplatform.homework.mapper.HomeworkMapper;
import com.eduplatform.homework.mapper.HomeworkQuestionDiscussionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 作业问答写模型服务。
 * 说明：集中承接提问与回复写操作，降低 HomeworkService 的职责复杂度。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class HomeworkDiscussionService {

    private final HomeworkMapper homeworkMapper;
    private final HomeworkQuestionDiscussionMapper discussionMapper;
    private final UserServiceClient userServiceClient;

    /**
     * 学生提问。
     */
    @Transactional
    public void askQuestion(Long homeworkId, Long studentId, Long questionId, String content) {
        Homework homework = homeworkMapper.selectById(homeworkId);
        if (homework == null) {
            throw new RuntimeException("作业不存在");
        }

        HomeworkQuestionDiscussion discussion = new HomeworkQuestionDiscussion();
        discussion.setHomeworkId(homeworkId);
        discussion.setQuestionId(questionId);
        discussion.setStudentId(studentId);
        discussion.setQuestionContent(content);
        discussion.setStatus("pending");
        discussionMapper.insert(discussion);

        // 发送通知给教师（通过课程ID找到教师）
        try {
            // 这里需要通过课程服务获取教师ID，简化处理暂时跳过
            // 实际应该调用课程服务获取课程的教师ID，然后发送通知
        } catch (Exception e) {
            // 通知发送失败不影响提问流程
            log.warn("发送提问通知失败: homeworkId={}, studentId={}, error={}", homeworkId, studentId, e.getMessage());
        }
    }

    /**
     * 教师回复问题。
     */
    @Transactional
    public void replyQuestion(Long discussionId, Long teacherId, String reply) {
        HomeworkQuestionDiscussion discussion = discussionMapper.selectById(discussionId);
        if (discussion == null) {
            throw new RuntimeException("问题不存在");
        }

        discussion.setTeacherReply(reply);
        discussion.setRepliedBy(teacherId);
        discussion.setRepliedAt(LocalDateTime.now());
        discussion.setStatus("answered");
        discussionMapper.updateById(discussion);

        // 发送通知给学生
        try {
            Homework homework = homeworkMapper.selectById(discussion.getHomeworkId());
            String homeworkTitle = homework != null ? homework.getTitle() : "作业";

            NotificationRequest notificationRequest = new NotificationRequest();
            notificationRequest.setUserId(discussion.getStudentId());
            notificationRequest.setTitle("作业问题已回复");
            notificationRequest.setContent(String.format(
                    "教师已回复您在作业「%s」中的提问，请查看。",
                    homeworkTitle));
            notificationRequest.setType("HOMEWORK_QUESTION_REPLIED");

            userServiceClient.sendNotification(notificationRequest);
        } catch (Exception e) {
            // 通知发送失败不影响回复流程
            log.error("发送回复通知失败: {}", e.getMessage());
        }
    }
}
