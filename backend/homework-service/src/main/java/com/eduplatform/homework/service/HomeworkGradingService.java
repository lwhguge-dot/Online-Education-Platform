package com.eduplatform.homework.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.eduplatform.common.exception.BusinessException;
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
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 作业批改写模型服务。
 * 说明：集中承接批改流程与状态流转，降低 HomeworkService 的职责复杂度。
 *
 * <p>并发与事务模型：
 * <ul>
 *   <li>锁在最外层 {@link #gradeSubjective}/{@link #gradeSubmission} 获取，确保同一 submissionId 串行批改。</li>
 *   <li>DB 写入通过自注入 {@code self} 调用 {@link #doGradeSubjective}/{@link #doGradeSubmission}
 *       触发 Spring AOP 代理，使 {@code @Transactional} 真正生效，并保证「事务提交后才释放锁」。</li>
 * </ul>
 */
@Service
@Slf4j
public class HomeworkGradingService {

    private final HomeworkMapper homeworkMapper;
    private final HomeworkSubmissionMapper submissionMapper;
    private final HomeworkAnswerMapper answerMapper;
    private final HomeworkQuestionMapper questionMapper;
    private final UserServiceClient userServiceClient;
    private final RedissonClient redissonClient;
    // 自注入 Spring 代理，用于在锁内调用本类的 @Transactional 方法，
    // 规避同类方法间 this 调用绕过 AOP 代理导致事务失效的问题。
    private final HomeworkGradingService self;

    private static final String SUBMISSION_LOCK_PREFIX = "homework:submission:lock:";

    public HomeworkGradingService(
            HomeworkMapper homeworkMapper,
            HomeworkSubmissionMapper submissionMapper,
            HomeworkAnswerMapper answerMapper,
            HomeworkQuestionMapper questionMapper,
            UserServiceClient userServiceClient,
            RedissonClient redissonClient,
            @Lazy HomeworkGradingService self) {
        this.homeworkMapper = homeworkMapper;
        this.submissionMapper = submissionMapper;
        this.answerMapper = answerMapper;
        this.questionMapper = questionMapper;
        this.userServiceClient = userServiceClient;
        this.redissonClient = redissonClient;
        this.self = self;
    }

    /**
     * 教师批改单个主观题。
     * 锁在事务外获取，事务提交后才释放锁，避免并发批改脏读。
     */
    public void gradeSubjective(Long submissionId, Long questionId, Integer score, String feedback) {
        RLock lock = redissonClient.getLock(SUBMISSION_LOCK_PREFIX + submissionId);
        try {
            if (!lock.tryLock(5, 30, TimeUnit.SECONDS)) {
                throw new BusinessException("操作过于频繁，请稍后重试");
            }
            // 通过 self 代理调用，确保 doGradeSubjective 的 @Transactional 在新事务中生效。
            self.doGradeSubjective(submissionId, questionId, score, feedback);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException("操作被中断");
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    /**
     * 实际的批改写入逻辑（独立事务）。
     * 必须通过 self 代理调用以触发事务，禁止直接 this 调用。
     */
    @Transactional
    public void doGradeSubjective(Long submissionId, Long questionId, Integer score, String feedback) {
        HomeworkAnswer answer = answerMapper.selectOne(
                new LambdaQueryWrapper<HomeworkAnswer>()
                        .eq(HomeworkAnswer::getSubmissionId, submissionId)
                        .eq(HomeworkAnswer::getQuestionId, questionId));

        if (answer != null) {
            answer.setScore(score);
            answer.setTeacherFeedback(feedback);
            answerMapper.updateById(answer);

            updateSubmissionStatus(submissionId);
        }
    }

    /**
     * 批量批改提交。
     * 锁在事务外获取，事务提交后才释放锁，避免并发批改脏读。
     */
    public void gradeSubmission(Long submissionId, GradeSubmissionDTO dto) {
        RLock lock = redissonClient.getLock(SUBMISSION_LOCK_PREFIX + submissionId);
        try {
            if (!lock.tryLock(5, 30, TimeUnit.SECONDS)) {
                throw new BusinessException("操作过于频繁，请稍后重试");
            }
            // 通过 self 代理调用，确保 doGradeSubmission 的 @Transactional 在新事务中生效。
            self.doGradeSubmission(submissionId, dto);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BusinessException("操作被中断");
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    /**
     * 实际的批量批改写入逻辑（独立事务）。
     * 必须通过 self 代理调用以触发事务，禁止直接 this 调用。
     */
    @Transactional
    public void doGradeSubmission(Long submissionId, GradeSubmissionDTO dto) {
        HomeworkSubmission submission = submissionMapper.selectById(submissionId);
        if (submission == null) {
            throw new BusinessException("提交记录不存在");
        }

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

        updateSubmissionScoreAndStatus(submissionId, dto.getOverallFeedback(), dto.getGradedBy());

        HomeworkSubmission updatedSubmission = submissionMapper.selectById(submissionId);
        if (updatedSubmission != null && "graded".equals(updatedSubmission.getSubmitStatus())) {
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
            Map<Long, HomeworkQuestion> qMap = buildQuestionMapByAnswers(answers);
            int subjectiveScore = answers.stream()
                    .filter(a -> {
                        HomeworkQuestion q = qMap.get(a.getQuestionId());
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
    private void updateSubmissionScoreAndStatus(Long submissionId, String feedback, Long gradedBy) {
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

        // 设置反馈和批改人
        if (feedback != null) {
            submission.setFeedback(feedback);
        }
        if (gradedBy != null) {
            submission.setGradedBy(gradedBy);
        }

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
            log.error("发送批改通知失败", e);
        }
    }
}
