package com.eduplatform.homework.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.eduplatform.homework.dto.GradeSubmissionDTO;
import com.eduplatform.homework.dto.NotificationRequest;
import com.eduplatform.homework.entity.Homework;
import com.eduplatform.homework.entity.HomeworkAnswer;
import com.eduplatform.homework.entity.HomeworkQuestion;
import com.eduplatform.homework.entity.HomeworkSubmission;
import com.eduplatform.homework.feign.UserServiceClient;
import com.eduplatform.homework.mapper.HomeworkAnswerMapper;
import com.eduplatform.homework.mapper.HomeworkMapper;
import com.eduplatform.homework.mapper.HomeworkQuestionMapper;
import com.eduplatform.homework.mapper.HomeworkSubmissionMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 作业批改写模型服务。
 * 说明：集中承接批改流程与状态流转，降低 HomeworkService 的职责复杂度。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class HomeworkGradingService {

    private final HomeworkMapper homeworkMapper;
    private final HomeworkSubmissionMapper submissionMapper;
    private final HomeworkAnswerMapper answerMapper;
    private final HomeworkQuestionMapper questionMapper;
    private final UserServiceClient userServiceClient;

    /**
     * 教师批改单个主观题。
     */
    @Transactional
    public void gradeSubjective(Long submissionId, Long questionId, Integer score, String feedback) {
        HomeworkAnswer answer = answerMapper.selectOne(
                new LambdaQueryWrapper<HomeworkAnswer>()
                        .eq(HomeworkAnswer::getSubmissionId, submissionId)
                        .eq(HomeworkAnswer::getQuestionId, questionId));

        if (answer != null) {
            answer.setScore(score);
            answer.setTeacherFeedback(feedback);
            answerMapper.updateById(answer);

            // 检查是否所有主观题都已批改，更新提交状态
            updateSubmissionStatus(submissionId);
        }
    }

    /**
     * 批量批改提交。
     */
    @Transactional
    public void gradeSubmission(Long submissionId, GradeSubmissionDTO dto) {
        HomeworkSubmission submission = submissionMapper.selectById(submissionId);
        if (submission == null) {
            throw new RuntimeException("提交记录不存在");
        }

        // 批改每个题目
        if (dto.getGrades() != null) {
            for (GradeSubmissionDTO.QuestionGrade grade : dto.getGrades()) {
                HomeworkAnswer answer = answerMapper.selectOne(
                        new LambdaQueryWrapper<HomeworkAnswer>()
                                .eq(HomeworkAnswer::getSubmissionId, submissionId)
                                .eq(HomeworkAnswer::getQuestionId, grade.getQuestionId()));

                if (answer != null) {
                    answer.setScore(grade.getScore());
                    answer.setTeacherFeedback(grade.getFeedback());
                    answerMapper.updateById(answer);
                }
            }
        }

        // 更新整体评语
        if (dto.getOverallFeedback() != null) {
            submission.setFeedback(dto.getOverallFeedback());
        }

        // 更新批改人
        if (dto.getGradedBy() != null) {
            submission.setGradedBy(dto.getGradedBy());
        }

        // 重新计算总分并更新状态
        updateSubmissionScoreAndStatus(submissionId);

        // 检查是否批改完成，如果完成则发送通知给学生
        HomeworkSubmission updatedSubmission = submissionMapper.selectById(submissionId);
        if ("graded".equals(updatedSubmission.getSubmitStatus())) {
            sendGradingNotification(updatedSubmission);
        }
    }

    /**
     * 更新提交状态（主观题批改路径）。
     */
    private void updateSubmissionStatus(Long submissionId) {
        HomeworkSubmission submission = submissionMapper.selectById(submissionId);
        if (submission == null) {
            return;
        }

        List<HomeworkAnswer> answers = answerMapper.selectList(
                new LambdaQueryWrapper<HomeworkAnswer>()
                        .eq(HomeworkAnswer::getSubmissionId, submissionId));

        boolean allGraded = answers.stream().allMatch(a -> a.getScore() != null);

        if (allGraded) {
            int totalScore = answers.stream().mapToInt(a -> a.getScore() != null ? a.getScore() : 0).sum();
            int subjectiveScore = answers.stream()
                    .filter(a -> {
                        HomeworkQuestion q = questionMapper.selectById(a.getQuestionId());
                        return q != null && "subjective".equals(q.getQuestionType());
                    })
                    .mapToInt(a -> a.getScore() != null ? a.getScore() : 0)
                    .sum();

            submission.setSubjectiveScore(subjectiveScore);
            submission.setTotalScore(totalScore);
            submission.setSubmitStatus("graded");
            submission.setGradedAt(LocalDateTime.now());
            submissionMapper.updateById(submission);
        }
    }

    /**
     * 更新提交分数与状态（批量批改路径）。
     */
    private void updateSubmissionScoreAndStatus(Long submissionId) {
        HomeworkSubmission submission = submissionMapper.selectById(submissionId);
        if (submission == null) {
            return;
        }

        List<HomeworkAnswer> answers = answerMapper.selectList(
                new LambdaQueryWrapper<HomeworkAnswer>()
                        .eq(HomeworkAnswer::getSubmissionId, submissionId));
        Map<Long, HomeworkQuestion> questionMap = buildQuestionMapByAnswers(answers);

        // 检查是否所有题目都已批改
        boolean allGraded = true;
        int totalScore = 0;
        int objectiveScore = 0;
        int subjectiveScore = 0;

        for (HomeworkAnswer answer : answers) {
            HomeworkQuestion question = questionMap.get(answer.getQuestionId());
            if (question == null) {
                continue;
            }

            if ("subjective".equals(question.getQuestionType())) {
                if (answer.getScore() == null) {
                    allGraded = false;
                } else {
                    subjectiveScore += answer.getScore();
                    totalScore += answer.getScore();
                }
            } else {
                if (answer.getScore() != null) {
                    objectiveScore += answer.getScore();
                    totalScore += answer.getScore();
                }
            }
        }

        submission.setObjectiveScore(objectiveScore);
        submission.setSubjectiveScore(subjectiveScore);
        submission.setTotalScore(totalScore);

        if (allGraded) {
            submission.setSubmitStatus("graded");
            submission.setGradedAt(LocalDateTime.now());
        }

        submissionMapper.updateById(submission);
    }

    /**
     * 批量加载题目信息并构建映射，避免循环内按 ID 单条查询引发 N+1。
     */
    private Map<Long, HomeworkQuestion> buildQuestionMapByAnswers(List<HomeworkAnswer> answers) {
        if (answers == null || answers.isEmpty()) {
            return Collections.emptyMap();
        }

        List<Long> questionIds = answers.stream()
                .map(HomeworkAnswer::getQuestionId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (questionIds.isEmpty()) {
            return Collections.emptyMap();
        }

        // 批量查询题目，避免在循环中反复调用 selectById
        List<HomeworkQuestion> questions = questionMapper.selectBatchIds(questionIds);
        return questions.stream()
                .filter(question -> question != null && question.getId() != null)
                .collect(Collectors.toMap(HomeworkQuestion::getId, question -> question, (left, right) -> left));
    }

    /**
     * 发送批改完成通知给学生。
     */
    private void sendGradingNotification(HomeworkSubmission submission) {
        try {
            Homework homework = homeworkMapper.selectById(submission.getHomeworkId());
            String homeworkTitle = homework != null ? homework.getTitle() : "作业";

            NotificationRequest notificationRequest = new NotificationRequest();
            notificationRequest.setUserId(submission.getStudentId());
            notificationRequest.setTitle("作业批改完成");
            notificationRequest.setContent(String.format(
                    "您的作业「%s」已批改完成，得分：%d分。请查看详细反馈。",
                    homeworkTitle,
                    submission.getTotalScore() != null ? submission.getTotalScore() : 0));
            notificationRequest.setType("HOMEWORK_GRADED");

            userServiceClient.sendNotification(notificationRequest);
        } catch (Exception e) {
            // 通知发送失败不影响批改流程
            // 记录日志但不抛出异常
            log.error("发送批改通知失败: {}", e.getMessage());
        }
    }
}
