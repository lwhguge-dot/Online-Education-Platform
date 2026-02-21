package com.eduplatform.homework.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.eduplatform.common.event.EventType;
import com.eduplatform.common.event.RedisStreamConstants;
import com.eduplatform.common.event.RedisStreamPublisher;
import com.eduplatform.homework.dto.HomeworkSubmitDTO;
import com.eduplatform.homework.entity.Homework;
import com.eduplatform.homework.entity.HomeworkAnswer;
import com.eduplatform.homework.entity.HomeworkQuestion;
import com.eduplatform.homework.entity.HomeworkSubmission;
import com.eduplatform.homework.entity.HomeworkUnlock;
import com.eduplatform.homework.mapper.HomeworkAnswerMapper;
import com.eduplatform.homework.mapper.HomeworkMapper;
import com.eduplatform.homework.mapper.HomeworkQuestionMapper;
import com.eduplatform.homework.mapper.HomeworkSubmissionMapper;
import com.eduplatform.homework.mapper.HomeworkUnlockMapper;
import com.eduplatform.homework.vo.HomeworkSubmissionVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 作业提交写模型服务。
 * 说明：承接学生提交、自动判分、提交流转与通知发布流程，降低 HomeworkService 的流程耦合。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class HomeworkSubmissionService {

    private final HomeworkMapper homeworkMapper;
    private final HomeworkQuestionMapper questionMapper;
    private final HomeworkUnlockMapper unlockMapper;
    private final HomeworkSubmissionMapper submissionMapper;
    private final HomeworkAnswerMapper answerMapper;
    private final RedisStreamPublisher redisStreamPublisher;

    /**
     * 执行作业提交流水。
     */
    @Transactional
    public Map<String, Object> submitHomework(HomeworkSubmitDTO dto) {
        Homework homework = homeworkMapper.selectById(dto.getHomeworkId());
        if (homework == null) {
            throw new RuntimeException("作业实体不存在");
        }

        // 准入层防线：确保解锁状态正确
        HomeworkUnlock unlock = unlockMapper.selectOne(
                new LambdaQueryWrapper<HomeworkUnlock>()
                        .eq(HomeworkUnlock::getStudentId, dto.getStudentId())
                        .eq(HomeworkUnlock::getHomeworkId, dto.getHomeworkId()));

        if (unlock == null) {
            unlock = new HomeworkUnlock();
            unlock.setStudentId(dto.getStudentId());
            unlock.setHomeworkId(dto.getHomeworkId());
            unlock.setUnlockStatus(1);
            unlock.setUnlockedAt(LocalDateTime.now());
            unlockMapper.insert(unlock);
        } else if (unlock.getUnlockStatus() != 1) {
            unlock.setUnlockStatus(1);
            unlock.setUnlockedAt(LocalDateTime.now());
            unlockMapper.updateById(unlock);
        }

        // 获取题干全量快照进行比对
        List<HomeworkQuestion> questions = questionMapper.selectList(
                new LambdaQueryWrapper<HomeworkQuestion>()
                        .eq(HomeworkQuestion::getHomeworkId, dto.getHomeworkId()));

        // 初始化/更新提交工单
        HomeworkSubmission submission = submissionMapper.selectOne(
                new LambdaQueryWrapper<HomeworkSubmission>()
                        .eq(HomeworkSubmission::getStudentId, dto.getStudentId())
                        .eq(HomeworkSubmission::getHomeworkId, dto.getHomeworkId()));

        if (submission == null) {
            submission = new HomeworkSubmission();
            submission.setStudentId(dto.getStudentId());
            submission.setHomeworkId(dto.getHomeworkId());
            submission.setSubmitStatus("draft");
            submissionMapper.insert(submission);
        }

        int objectiveScore = 0;
        int objectiveTotal = 0;
        boolean hasSubjective = false;
        List<Map<String, Object>> answerResults = new ArrayList<>();

        for (HomeworkQuestion question : questions) {
            String studentAnswer = dto.getAnswers().stream()
                    .filter(a -> a.getQuestionId().equals(question.getId()))
                    .map(HomeworkSubmitDTO.AnswerDTO::getAnswer)
                    .findFirst()
                    .orElse("");

            // 落地具体的答案条目
            HomeworkAnswer answer = answerMapper.selectOne(
                    new LambdaQueryWrapper<HomeworkAnswer>()
                            .eq(HomeworkAnswer::getSubmissionId, submission.getId())
                            .eq(HomeworkAnswer::getQuestionId, question.getId()));

            if (answer == null) {
                answer = new HomeworkAnswer();
                answer.setSubmissionId(submission.getId());
                answer.setQuestionId(question.getId());
            }
            answer.setStudentAnswer(studentAnswer);

            Map<String, Object> answerResult = new HashMap<>();
            answerResult.put("questionId", question.getId());
            answerResult.put("studentAnswer", studentAnswer);
            answerResult.put("questionType", question.getQuestionType());

            // 客观题判分引擎
            if (!"subjective".equals(question.getQuestionType())) {
                objectiveTotal += question.getScore();
                boolean isCorrect = compareAnswer(question.getCorrectAnswer(), studentAnswer);
                answer.setIsCorrect(isCorrect ? 1 : 0);
                answer.setScore(isCorrect ? question.getScore() : 0);
                answer.setAiFeedback(isCorrect ? "回答正确"
                        : "回答有误。建议重新查看章节解析。正确选项为：" + question.getCorrectAnswer());

                if (isCorrect) {
                    objectiveScore += question.getScore();
                }

                answerResult.put("isCorrect", isCorrect);
                answerResult.put("correctAnswer", question.getCorrectAnswer());
                answerResult.put("analysis", question.getAnswerAnalysis());
                answerResult.put("score", answer.getScore());
            } else {
                // 标记存在需要人工介入的主观题
                hasSubjective = true;
                answerResult.put("isCorrect", null);
                answerResult.put("message", "等待教师人工评阅");
            }

            if (answer.getId() == null) {
                answerMapper.insert(answer);
            } else {
                answerMapper.updateById(answer);
            }

            answerResults.add(answerResult);
        }

        // 状态机流转：若全为客观题则直接进入 graded 状态
        submission.setSubmitStatus(hasSubjective ? "submitted" : "graded");
        submission.setObjectiveScore(objectiveScore);
        submission.setSubmittedAt(LocalDateTime.now());
        if (!hasSubjective) {
            submission.setTotalScore(objectiveScore);
            submission.setGradedAt(LocalDateTime.now());
        }
        submissionMapper.updateById(submission);

        // 若需人工批改则触发通知
        if (hasSubjective) {
            sendSubmissionNotification(submission, homework);
        }

        Map<String, Object> result = new HashMap<>();
        result.put("submission", convertToSubmissionVO(submission));
        result.put("objectiveScore", objectiveScore);
        result.put("objectiveTotal", objectiveTotal);
        result.put("hasSubjective", hasSubjective);
        result.put("answerResults", answerResults);
        return result;
    }

    /**
     * 转换提交实体为视图对象，避免向外暴露可变实体对象。
     */
    private HomeworkSubmissionVO convertToSubmissionVO(HomeworkSubmission submission) {
        if (submission == null) {
            return null;
        }
        HomeworkSubmissionVO vo = new HomeworkSubmissionVO();
        BeanUtils.copyProperties(submission, vo);
        return vo;
    }

    /**
     * 通过 Redis Stream 发布作业提交事件。
     */
    private void sendSubmissionNotification(HomeworkSubmission submission, Homework homework) {
        try {
            Map<String, Object> data = new HashMap<>();
            data.put("homeworkId", homework.getId());
            data.put("homeworkTitle", homework.getTitle());
            data.put("submissionId", submission.getId());
            data.put("studentId", submission.getStudentId());
            data.put("courseId", homework.getCourseId());
            data.put("chapterId", homework.getChapterId());

            redisStreamPublisher.publish(
                    EventType.HOMEWORK_SUBMITTED,
                    RedisStreamConstants.SERVICE_HOMEWORK,
                    data);
        } catch (Exception e) {
            // 事件发布失败不影响主业务流程，仅记录日志
            log.error("发布作业提交事件失败: homeworkId={}, submissionId={}, error={}",
                    homework.getId(), submission.getId(), e.getMessage());
        }
    }

    /**
     * 比对答案。
     */
    private boolean compareAnswer(String correct, String student) {
        if (correct == null || student == null) {
            return false;
        }
        // 忽略大小写和空格比对
        return correct.trim().replaceAll("\\s+", "")
                .equalsIgnoreCase(student.trim().replaceAll("\\s+", ""));
    }
}
