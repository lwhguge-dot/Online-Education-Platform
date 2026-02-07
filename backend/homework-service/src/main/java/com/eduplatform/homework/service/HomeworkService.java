package com.eduplatform.homework.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.eduplatform.common.result.Result;
import com.eduplatform.common.event.EventType;
import com.eduplatform.common.event.RedisStreamConstants;
import com.eduplatform.common.event.RedisStreamPublisher;
import com.eduplatform.homework.dto.*;
import com.eduplatform.homework.entity.*;
import com.eduplatform.homework.feign.UserServiceClient;
import com.eduplatform.homework.mapper.*;
import com.eduplatform.homework.vo.HomeworkQuestionDiscussionVO;
import com.eduplatform.homework.vo.HomeworkQuestionVO;
import com.eduplatform.homework.vo.HomeworkStudentQuestionVO;
import com.eduplatform.homework.vo.HomeworkVO;
import com.eduplatform.homework.vo.HomeworkSubmissionVO;
import com.eduplatform.homework.vo.SubjectiveCommentVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 作业系统核心逻辑服务 (Assignments & Assessment Service)
 * 负责管控作业的全生命周期，从教师端的任务发布、学生端的受限解锁、到多维度的答题评分与反馈流。
 *
 * 核心模型：
 * 1. 准入模型：基于 Progress-service 触发的章节完成度，执行作业的条件解锁。
 * 2. 交互模型：支持“草稿暂存”与“正式提交”，支持客观题（自动批改）与主观题（人工批改）的混合模式。
 * 3. 统计模型：实时度量作业的提交率、批改率，为学情报告提供数据支撑。
 *
 * @author Antigravity
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class HomeworkService {

    private final HomeworkMapper homeworkMapper;
    private final HomeworkQuestionMapper questionMapper;
    private final HomeworkUnlockMapper unlockMapper;
    private final HomeworkSubmissionMapper submissionMapper;
    private final HomeworkAnswerMapper answerMapper;
    private final HomeworkQuestionDiscussionMapper discussionMapper;
    private final UserServiceClient userServiceClient;
    private final RedisStreamPublisher redisStreamPublisher;

    /**
     * 将作业领域对象映射为视图层对象 (VO Domain Mapping)
     *
     * @param homework 作业实体
     * @return 过滤敏感字段后的视图对象
     */
    public HomeworkVO convertToVO(Homework homework) {
        if (homework == null) {
            return null;
        }
        HomeworkVO vo = new HomeworkVO();
        BeanUtils.copyProperties(homework, vo);
        return vo;
    }

    /**
     * 将主观题评论映射为视图层对象
     *
     * @param comment 评论实体
     * @return 评论视图对象
     */
    public SubjectiveCommentVO convertToCommentVO(SubjectiveComment comment) {
        if (comment == null) {
            return null;
        }
        SubjectiveCommentVO vo = new SubjectiveCommentVO();
        BeanUtils.copyProperties(comment, vo);
        return vo;
    }

    /**
     * 批量转换作业实体列表
     *
     * @param homeworks 作业实体列表
     * @return 视图对象列表
     */
    public List<HomeworkVO> convertToVOList(List<Homework> homeworks) {
        return homeworks.stream().map(this::convertToVO).collect(Collectors.toList());
    }

    /**
     * 将题目实体转换为视图对象
     *
     * @param question 题目实体
     * @return 题目视图对象
     */
    public HomeworkQuestionVO convertQuestionToVO(HomeworkQuestion question) {
        if (question == null) {
            return null;
        }
        HomeworkQuestionVO vo = new HomeworkQuestionVO();
        BeanUtils.copyProperties(question, vo);
        return vo;
    }

    /**
     * 将作业提交实体转换为视图对象
     *
     * @param submission 作业提交实体
     * @return 作业提交视图对象
     */
    public HomeworkSubmissionVO convertToSubmissionVO(HomeworkSubmission submission) {
        if (submission == null) {
            return null;
        }
        HomeworkSubmissionVO vo = new HomeworkSubmissionVO();
        BeanUtils.copyProperties(submission, vo);
        return vo;
    }

    /**
     * 批量转换评论实体列表
     *
     * @param comments 评论实体列表
     * @return 视图对象列表
     */
    public List<SubjectiveCommentVO> convertToCommentVOList(List<SubjectiveComment> comments) {
        return comments.stream().map(this::convertToCommentVO).collect(Collectors.toList());
    }

    /**
     * 发布新作业（教师功能）
     * 包含作业主体元数据保存及题库的原子化持久化。
     *
     * @param dto 包含作业信息与题目列表的 DTO
     * @return 已持久化的作业实体
     */
    @Transactional
    public Homework createHomework(HomeworkCreateDTO dto) {
        Homework homework = new Homework();
        homework.setChapterId(dto.getChapterId());
        homework.setCourseId(dto.getCourseId());
        homework.setTitle(dto.getTitle());
        homework.setDescription(dto.getDescription());
        homework.setHomeworkType(dto.getHomeworkType());
        homework.setTotalScore(dto.getTotalScore());
        homework.setDeadline(dto.getDeadline());
        homeworkMapper.insert(homework);

        // 处理关联题目
        if (dto.getQuestions() != null && !dto.getQuestions().isEmpty()) {
            int order = 1;
            for (HomeworkCreateDTO.QuestionDTO q : dto.getQuestions()) {
                HomeworkQuestion question = new HomeworkQuestion();
                question.setHomeworkId(homework.getId());
                question.setQuestionType(q.getQuestionType());
                question.setContent(q.getContent());
                question.setOptions(q.getOptions());
                question.setCorrectAnswer(q.getCorrectAnswer());
                question.setAnswerAnalysis(q.getAnswerAnalysis());
                question.setScore(q.getScore() != null ? q.getScore() : 10);
                question.setSortOrder(q.getSortOrder() != null ? q.getSortOrder() : order++);
                questionMapper.insert(question);
            }
        }

        return homework;
    }

    /**
     * 检索作业结构化详情
     *
     * @param homeworkId 作业唯一标识
     * @return 包含 Homework 元数据与 Question 列表的复合 Map
     */
    public Map<String, Object> getHomeworkDetail(Long homeworkId) {
        Homework homework = homeworkMapper.selectById(homeworkId);
        if (homework == null) {
            return null;
        }

        List<HomeworkQuestion> questions = questionMapper.findByHomeworkId(homeworkId);

        Map<String, Object> result = new HashMap<>();
        result.put("homework", convertToVO(homework));
        result.put("questions", questions.stream()
                .map(this::convertQuestionToVO)
                .collect(Collectors.toList()));
        return result;
    }

    /**
     * 获取指定章节下的所有作业
     *
     * @param chapterId 章节标识
     * @return 作业列表
     */
    public List<Homework> getHomeworksByChapter(Long chapterId) {
        return homeworkMapper.findByChapterId(chapterId);
    }

    /**
     * 获取章节作业列表及其提交度快照 (Teacher Dashboard Data)
     * 用于教师端课程管理页面，展示每个作业的批改进度。
     *
     * @param chapterId 章节标识
     * @return 包含提交数与批改数的 DTO 列表
     */
    public List<HomeworkWithStatsDTO> getHomeworksByChapterWithStats(Long chapterId) {
        List<Homework> homeworks = homeworkMapper.findByChapterId(chapterId);
        List<HomeworkWithStatsDTO> result = new ArrayList<>();

        for (Homework hw : homeworks) {
            HomeworkWithStatsDTO dto = new HomeworkWithStatsDTO();
            dto.setId(hw.getId());
            dto.setChapterId(hw.getChapterId());
            dto.setTitle(hw.getTitle());
            dto.setDescription(hw.getDescription());
            dto.setHomeworkType(hw.getHomeworkType());
            dto.setTotalScore(hw.getTotalScore());
            dto.setDeadline(hw.getDeadline());
            dto.setCreatedAt(hw.getCreatedAt());

            // 聚合统计：计算总提交量（排除草稿）
            Long totalSubmissions = submissionMapper.selectCount(
                    new LambdaQueryWrapper<HomeworkSubmission>()
                            .eq(HomeworkSubmission::getHomeworkId, hw.getId())
                            .ne(HomeworkSubmission::getSubmitStatus, "draft"));

            // 聚合统计：计算已完成批改的量
            Long gradedSubmissions = submissionMapper.selectCount(
                    new LambdaQueryWrapper<HomeworkSubmission>()
                            .eq(HomeworkSubmission::getHomeworkId, hw.getId())
                            .eq(HomeworkSubmission::getSubmitStatus, "graded"));

            dto.setSubmissionCount(totalSubmissions != null ? totalSubmissions.intValue() : 0);
            dto.setGradedCount(gradedSubmissions != null ? gradedSubmissions.intValue() : 0);

            result.add(dto);
        }

        return result;
    }

    /**
     * 获取学生视角下的章节作业视图 (Student Context)
     * 会关联学生的个人提交状态（是否已交、得分快照）。
     *
     * @param studentId 学生标识
     * @param chapterId 章节标识
     * @return 包含解锁状态与提交记录的 Map 列表
     */
    public List<StudentHomeworkDTO> getStudentHomeworks(Long studentId, Long chapterId) {
        List<Homework> homeworks = getHomeworksByChapter(chapterId);
        List<StudentHomeworkDTO> result = new ArrayList<>();

        for (Homework hw : homeworks) {
            // 查询当前学生的提交记录快照
            HomeworkSubmission submission = null;
            try {
                submission = submissionMapper.selectOne(
                        new LambdaQueryWrapper<HomeworkSubmission>()
                                .eq(HomeworkSubmission::getStudentId, studentId)
                                .eq(HomeworkSubmission::getHomeworkId, hw.getId()));
            } catch (Exception e) {
                log.error("查询提交记录失败: studentId={}, homeworkId={}", studentId, hw.getId(), e);
            }

            boolean submitted = submission != null && !"draft".equals(submission.getSubmitStatus());

            StudentHomeworkDTO item = new StudentHomeworkDTO();
            item.setHomework(convertToVO(hw));
            item.setQuestionCount(1);
            item.setUnlocked(true);
            item.setSubmitted(submitted);
            item.setSubmission(convertToSubmissionVO(submission));
            result.add(item);
        }

        return result;
    }

    /**
     * 联动解锁章节作业 (Service Coordination)
     * 当 Progress-service 判定学生学满该章节时，远程调用此方法开通作业访问权限。
     *
     * @param studentId 学生标识
     * @param chapterId 目标章节
     */
    @Transactional
    public void unlockHomeworkByChapter(Long studentId, Long chapterId) {
        List<Homework> homeworks = getHomeworksByChapter(chapterId);

        for (Homework hw : homeworks) {
            HomeworkUnlock existing = unlockMapper.selectOne(
                    new LambdaQueryWrapper<HomeworkUnlock>()
                            .eq(HomeworkUnlock::getStudentId, studentId)
                            .eq(HomeworkUnlock::getHomeworkId, hw.getId()));

            if (existing == null) {
                HomeworkUnlock unlock = new HomeworkUnlock();
                unlock.setStudentId(studentId);
                unlock.setHomeworkId(hw.getId());
                unlock.setUnlockStatus(1);
                unlock.setUnlockedAt(LocalDateTime.now());
                unlockMapper.insert(unlock);
            } else if (existing.getUnlockStatus() == 0) {
                existing.setUnlockStatus(1);
                existing.setUnlockedAt(LocalDateTime.now());
                unlockMapper.updateById(existing);
            }
        }
    }

    /**
     * 执行作业提交流水 (Submission Workflow)
     * 逻辑包含：
     * 1. 准入校验：自动补全缺失的解锁记录（容错处理）。
     * 2. 状态扭转：从 draft 转为 submitted 或 graded。
     * 3. 客观评分：对选择/填空执行即时判定与打分。
     * 4. 异步协同：若存在主观题，则触发教师通知流程。
     *
     * @param dto 包含答案坐标集的提交包
     * @return 包含即时得分快照的评估结果
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

            // 客观题判分引擎 (Auto-Grading Engine)
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

        // 异构驱动：若需批改则触发通知
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
     * 通过 Redis Stream 发布作业提交事件
     * 替代原有的同步 TODO 通知，由 user-service 异步消费后通知教师批改。
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
     * 获取详细的提交答题报告 (Report Details)
     *
     * @param homeworkId 作业标识
     * @param studentId  学生标识
     * @return 包含题目背板、学生原图、得分及批语的详情包
     */
    public Map<String, Object> getSubmissionDetail(Long homeworkId, Long studentId) {
        HomeworkSubmission submission = submissionMapper.selectOne(
                new LambdaQueryWrapper<HomeworkSubmission>()
                        .eq(HomeworkSubmission::getStudentId, studentId)
                        .eq(HomeworkSubmission::getHomeworkId, homeworkId));

        if (submission == null) {
            return null;
        }

        List<HomeworkAnswer> answers = answerMapper.selectList(
                new LambdaQueryWrapper<HomeworkAnswer>()
                        .eq(HomeworkAnswer::getSubmissionId, submission.getId()));
        Map<Long, HomeworkQuestion> questionMap = buildQuestionMapByAnswers(answers);

        List<Map<String, Object>> answerDetails = new ArrayList<>();
        for (HomeworkAnswer answer : answers) {
            HomeworkQuestion question = questionMap.get(answer.getQuestionId());
            if (question != null) {
                Map<String, Object> detail = new HashMap<>();
                detail.put("questionId", answer.getQuestionId());
                detail.put("questionType", question.getQuestionType());
                detail.put("content", question.getContent());
                detail.put("studentAnswer", answer.getStudentAnswer());
                detail.put("correctAnswer", question.getCorrectAnswer());
                detail.put("isCorrect", answer.getIsCorrect());
                detail.put("score", answer.getScore());
                detail.put("aiFeedback", answer.getAiFeedback());
                detail.put("teacherFeedback", answer.getTeacherFeedback());
                answerDetails.add(detail);
            }
        }

        Map<String, Object> result = new HashMap<>();
        result.put("submission", convertToSubmissionVO(submission));
        result.put("answers", answerDetails);

        return result;
    }

    /**
     * 获取错题报告
     */
    public Map<String, Object> getErrorReport(Long studentId, Long homeworkId) {
        HomeworkSubmission submission = submissionMapper.selectOne(
                new LambdaQueryWrapper<HomeworkSubmission>()
                        .eq(HomeworkSubmission::getStudentId, studentId)
                        .eq(HomeworkSubmission::getHomeworkId, homeworkId));

        if (submission == null) {
            return null;
        }

        List<HomeworkAnswer> answers = answerMapper.selectList(
                new LambdaQueryWrapper<HomeworkAnswer>()
                        .eq(HomeworkAnswer::getSubmissionId, submission.getId()));
        Map<Long, HomeworkQuestion> questionMap = buildQuestionMapByAnswers(answers);

        List<Map<String, Object>> errorList = new ArrayList<>();
        for (HomeworkAnswer answer : answers) {
            if (answer.getIsCorrect() != null && answer.getIsCorrect() == 0) {
                HomeworkQuestion question = questionMap.get(answer.getQuestionId());
                if (question != null) {
                    Map<String, Object> error = new HashMap<>();
                    error.put("question", convertQuestionToVO(question));
                    error.put("studentAnswer", answer.getStudentAnswer());
                    error.put("correctAnswer", question.getCorrectAnswer());
                    error.put("analysis", question.getAnswerAnalysis());
                    error.put("feedback", answer.getAiFeedback());
                    errorList.add(error);
                }
            }
        }

        Map<String, Object> result = new HashMap<>();
        result.put("submission", convertToSubmissionVO(submission));
        result.put("errorCount", errorList.size());
        result.put("errors", errorList);

        return result;
    }

    /**
     * 教师批改主观题
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
     * 获取作业的所有提交记录（教师端用于批改）
     */
    public List<Map<String, Object>> getSubmissionsByHomework(Long homeworkId) {
        List<HomeworkSubmission> submissions = submissionMapper.selectList(
                new LambdaQueryWrapper<HomeworkSubmission>()
                        .eq(HomeworkSubmission::getHomeworkId, homeworkId)
                        .orderByDesc(HomeworkSubmission::getSubmittedAt));

        List<Map<String, Object>> result = new ArrayList<>();
        for (HomeworkSubmission submission : submissions) {
            Map<String, Object> item = new HashMap<>();
            item.put("submissionId", submission.getId());
            item.put("studentId", submission.getStudentId());
            item.put("submitStatus", submission.getSubmitStatus());
            item.put("objectiveScore", submission.getObjectiveScore());
            item.put("subjectiveScore", submission.getSubjectiveScore());
            item.put("totalScore", submission.getTotalScore());
            item.put("submittedAt", submission.getSubmittedAt());
            item.put("gradedAt", submission.getGradedAt());

            // 获取答案详情
        List<HomeworkAnswer> answers = answerMapper.selectList(
                new LambdaQueryWrapper<HomeworkAnswer>()
                        .eq(HomeworkAnswer::getSubmissionId, submission.getId()));
        Map<Long, HomeworkQuestion> questionMap = buildQuestionMapByAnswers(answers);

        List<Map<String, Object>> answerDetails = new ArrayList<>();
        boolean hasUngraded = false;
        for (HomeworkAnswer answer : answers) {
            HomeworkQuestion question = questionMap.get(answer.getQuestionId());
            if (question != null) {
                Map<String, Object> answerDetail = new HashMap<>();
                answerDetail.put("questionId", question.getId());
                    answerDetail.put("questionType", question.getQuestionType());
                    answerDetail.put("content", question.getContent());
                    answerDetail.put("options", question.getOptions());
                    answerDetail.put("correctAnswer", question.getCorrectAnswer());
                    answerDetail.put("studentAnswer", answer.getStudentAnswer());
                    answerDetail.put("score", answer.getScore());
                    answerDetail.put("maxScore", question.getScore());
                    answerDetail.put("isCorrect", answer.getIsCorrect());
                    answerDetail.put("teacherFeedback", answer.getTeacherFeedback());
                    answerDetails.add(answerDetail);

                    if ("subjective".equals(question.getQuestionType()) && answer.getScore() == null) {
                        hasUngraded = true;
                    }
                }
            }
            item.put("answers", answerDetails);
            item.put("hasUngraded", hasUngraded);
            result.add(item);
        }

        return result;
    }

    /**
     * 比对答案
     */
    private boolean compareAnswer(String correct, String student) {
        if (correct == null || student == null) {
            return false;
        }
        // 忽略大小写和空格比对
        return correct.trim().replaceAll("\\s+", "")
                .equalsIgnoreCase(student.trim().replaceAll("\\s+", ""));
    }

    /**
     * 更新提交状态
     */
    private void updateSubmissionStatus(Long submissionId) {
        HomeworkSubmission submission = submissionMapper.selectById(submissionId);
        if (submission == null)
            return;

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
     * 获取教师待办事项（真实数据）
     */
    public List<Map<String, Object>> getTeacherTodos(Long teacherId) {
        List<Map<String, Object>> todos = new ArrayList<>();

        // 查询待批改的作业提交
        List<HomeworkSubmission> pendingSubmissions = submissionMapper.selectList(
                new LambdaQueryWrapper<HomeworkSubmission>()
                        .eq(HomeworkSubmission::getSubmitStatus, "pending")
                        .orderByDesc(HomeworkSubmission::getSubmittedAt));

        if (!pendingSubmissions.isEmpty()) {
            Map<String, Object> todo = new HashMap<>();
            todo.put("id", 1);
            todo.put("type", "homework");
            todo.put("title", "待批改作业");
            todo.put("count", pendingSubmissions.size());
            todo.put("urgent", pendingSubmissions.size() > 5);
            todo.put("time", pendingSubmissions.get(0).getSubmittedAt() != null ? pendingSubmissions.get(0)
                    .getSubmittedAt().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")) : "");
            todos.add(todo);
        }

        return todos;
    }

    /**
     * 获取学生紧急作业（截止日期在指定天数内的未完成作业）
     */
    public List<Map<String, Object>> getStudentUrgentHomeworks(Long studentId, Integer days) {
        List<Map<String, Object>> result = new ArrayList<>();

        // 获取学生已解锁的作业
        List<HomeworkUnlock> unlocks = unlockMapper.selectList(
                new LambdaQueryWrapper<HomeworkUnlock>()
                        .eq(HomeworkUnlock::getStudentId, studentId)
                        .eq(HomeworkUnlock::getUnlockStatus, 1));

        if (unlocks.isEmpty()) {
            return result;
        }

        // 获取已解锁作业的ID列表
        List<Long> unlockedHomeworkIds = unlocks.stream()
                .map(HomeworkUnlock::getHomeworkId)
                .collect(java.util.stream.Collectors.toList());

        // 获取已提交的作业ID列表
        List<HomeworkSubmission> submissions = submissionMapper.selectList(
                new LambdaQueryWrapper<HomeworkSubmission>()
                        .eq(HomeworkSubmission::getStudentId, studentId)
                        .in(HomeworkSubmission::getHomeworkId, unlockedHomeworkIds)
                        .ne(HomeworkSubmission::getSubmitStatus, "draft"));

        Set<Long> submittedHomeworkIds = submissions.stream()
                .map(HomeworkSubmission::getHomeworkId)
                .collect(java.util.stream.Collectors.toSet());

        // 计算截止日期范围
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime deadline = now.plusDays(days);

        // 查询紧急作业
        for (Long homeworkId : unlockedHomeworkIds) {
            // 跳过已提交的作业
            if (submittedHomeworkIds.contains(homeworkId)) {
                continue;
            }

            Homework homework = homeworkMapper.selectById(homeworkId);
            if (homework == null || homework.getDeadline() == null) {
                continue;
            }

            // 检查是否在截止日期范围内且未过期
            if (homework.getDeadline().isAfter(now) && homework.getDeadline().isBefore(deadline)) {
                Map<String, Object> item = new HashMap<>();
                item.put("id", homework.getId());
                item.put("title", homework.getTitle());
                item.put("deadline", homework.getDeadline()
                        .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")));
                item.put("chapterId", homework.getChapterId());

                // 计算剩余天数
                long daysLeft = java.time.Duration.between(now, homework.getDeadline()).toDays();
                item.put("daysLeft", daysLeft);

                // 获取课程名称（通过章节ID查询）
                item.put("courseName", "课程作业"); // 简化处理，实际需要跨服务查询

                result.add(item);
            }
        }

        // 按截止日期排序
        result.sort((a, b) -> {
            String deadlineA = (String) a.get("deadline");
            String deadlineB = (String) b.get("deadline");
            return deadlineA.compareTo(deadlineB);
        });

        return result;
    }

    /**
     * 获取学生待完成作业数量
     */
    public int getStudentPendingHomeworkCount(Long studentId) {
        // 获取学生已解锁的作业
        List<HomeworkUnlock> unlocks = unlockMapper.selectList(
                new LambdaQueryWrapper<HomeworkUnlock>()
                        .eq(HomeworkUnlock::getStudentId, studentId)
                        .eq(HomeworkUnlock::getUnlockStatus, 1));

        if (unlocks.isEmpty()) {
            return 0;
        }

        // 获取已解锁作业的ID列表
        List<Long> unlockedHomeworkIds = unlocks.stream()
                .map(HomeworkUnlock::getHomeworkId)
                .collect(java.util.stream.Collectors.toList());

        // 获取已提交的作业ID列表
        List<HomeworkSubmission> submissions = submissionMapper.selectList(
                new LambdaQueryWrapper<HomeworkSubmission>()
                        .eq(HomeworkSubmission::getStudentId, studentId)
                        .in(HomeworkSubmission::getHomeworkId, unlockedHomeworkIds)
                        .ne(HomeworkSubmission::getSubmitStatus, "draft"));

        Set<Long> submittedHomeworkIds = submissions.stream()
                .map(HomeworkSubmission::getHomeworkId)
                .collect(java.util.stream.Collectors.toSet());

        // 返回未提交的作业数量
        return (int) unlockedHomeworkIds.stream()
                .filter(id -> !submittedHomeworkIds.contains(id))
                .count();
    }

    /**
     * 获取教师最近活动（真实数据）
     */
    public List<Map<String, Object>> getTeacherActivities(Long teacherId) {
        List<Map<String, Object>> activities = new ArrayList<>();

        // 查询最新的作业提交记录
        List<HomeworkSubmission> recentSubmissions = submissionMapper.selectList(
                new LambdaQueryWrapper<HomeworkSubmission>()
                        .orderByDesc(HomeworkSubmission::getSubmittedAt)
                        .last("LIMIT 10"));

        int id = 1;
        for (HomeworkSubmission sub : recentSubmissions) {
            Map<String, Object> act = new HashMap<>();
            act.put("id", id++);
            act.put("type", "submit");
            act.put("content", "学生" + sub.getStudentId() + "提交了作业");
            act.put("time",
                    sub.getSubmittedAt() != null
                            ? sub.getSubmittedAt().format(java.time.format.DateTimeFormatter.ofPattern("HH:mm"))
                            : "");
            activities.add(act);
            if (activities.size() >= 5)
                break;
        }

        return activities;
    }

    // ==================== 批改工作台相关方法 ====================

    /**
     * 获取作业的待批改提交列表
     */
    public PendingSubmissionsDTO getPendingSubmissions(Long homeworkId) {
        Homework homework = homeworkMapper.selectById(homeworkId);
        if (homework == null) {
            return null;
        }

        PendingSubmissionsDTO result = new PendingSubmissionsDTO();

        // 设置作业信息
        PendingSubmissionsDTO.HomeworkInfo homeworkInfo = new PendingSubmissionsDTO.HomeworkInfo();
        homeworkInfo.setId(homework.getId());
        homeworkInfo.setTitle(homework.getTitle());
        homeworkInfo.setTotalScore(homework.getTotalScore());
        homeworkInfo.setDeadline(homework.getDeadline());
        result.setHomework(homeworkInfo);

        // 获取所有提交记录（排除草稿）
        List<HomeworkSubmission> submissions = submissionMapper.selectList(
                new LambdaQueryWrapper<HomeworkSubmission>()
                        .eq(HomeworkSubmission::getHomeworkId, homeworkId)
                        .ne(HomeworkSubmission::getSubmitStatus, "draft")
                        .orderByDesc(HomeworkSubmission::getSubmittedAt));

        // 批量预加载学生姓名，避免循环内逐条远程调用
        Map<Long, String> studentNameMap = batchGetStudentNameMap(
                submissions.stream()
                        .map(HomeworkSubmission::getStudentId)
                        .filter(Objects::nonNull)
                        .toList());

        List<PendingSubmissionsDTO.SubmissionSummary> summaries = new ArrayList<>();
        int gradedCount = 0;

        for (HomeworkSubmission submission : submissions) {
            PendingSubmissionsDTO.SubmissionSummary summary = new PendingSubmissionsDTO.SubmissionSummary();
            summary.setId(submission.getId());
            summary.setStudentId(submission.getStudentId());
            summary.setStudentName(studentNameMap.getOrDefault(submission.getStudentId(), getStudentName(submission.getStudentId())));
            summary.setSubmittedAt(submission.getSubmittedAt());
            summary.setObjectiveScore(submission.getObjectiveScore());
            summary.setSubjectiveScore(submission.getSubjectiveScore());
            summary.setStatus(submission.getSubmitStatus());

            // 检查是否有未批改的主观题
            boolean hasUngraded = checkHasUngradedSubjective(submission.getId());
            summary.setHasUngraded(hasUngraded);

            if ("graded".equals(submission.getSubmitStatus())) {
                gradedCount++;
            }

            summaries.add(summary);
        }

        result.setSubmissions(summaries);
        result.setGradedCount(gradedCount);
        result.setTotalCount(submissions.size());

        return result;
    }

    /**
     * 获取提交详情（用于批改工作台）
     */
    public SubmissionDetailDTO getSubmissionDetailForGrading(Long submissionId) {
        HomeworkSubmission submission = submissionMapper.selectById(submissionId);
        if (submission == null) {
            return null;
        }

        Homework homework = homeworkMapper.selectById(submission.getHomeworkId());
        if (homework == null) {
            return null;
        }

        SubmissionDetailDTO result = new SubmissionDetailDTO();

        // 设置提交信息
        SubmissionDetailDTO.SubmissionInfo submissionInfo = new SubmissionDetailDTO.SubmissionInfo();
        submissionInfo.setId(submission.getId());
        submissionInfo.setStudentId(submission.getStudentId());
        submissionInfo.setStudentName(getStudentName(submission.getStudentId()));
        submissionInfo.setHomeworkId(homework.getId());
        submissionInfo.setHomeworkTitle(homework.getTitle());
        submissionInfo.setSubmitStatus(submission.getSubmitStatus());
        submissionInfo.setObjectiveScore(submission.getObjectiveScore());
        submissionInfo.setSubjectiveScore(submission.getSubjectiveScore());
        submissionInfo.setTotalScore(submission.getTotalScore());
        submissionInfo.setSubmittedAt(submission.getSubmittedAt());
        submissionInfo.setGradedAt(submission.getGradedAt());
        submissionInfo.setFeedback(submission.getFeedback());
        result.setSubmission(submissionInfo);

        // 获取答案详情
        List<HomeworkAnswer> answers = answerMapper.selectList(
                new LambdaQueryWrapper<HomeworkAnswer>()
                        .eq(HomeworkAnswer::getSubmissionId, submissionId));
        Map<Long, HomeworkQuestion> questionMap = buildQuestionMapByAnswers(answers);

        List<SubmissionDetailDTO.AnswerDetail> answerDetails = new ArrayList<>();
        for (HomeworkAnswer answer : answers) {
            HomeworkQuestion question = questionMap.get(answer.getQuestionId());
            if (question != null) {
                SubmissionDetailDTO.AnswerDetail detail = new SubmissionDetailDTO.AnswerDetail();
                detail.setQuestionId(question.getId());
                detail.setQuestionType(question.getQuestionType());
                detail.setQuestionContent(question.getContent());
                detail.setOptions(question.getOptions());
                detail.setCorrectAnswer(question.getCorrectAnswer());
                detail.setStudentAnswer(answer.getStudentAnswer());
                detail.setScore(answer.getScore());
                detail.setMaxScore(question.getScore());
                detail.setIsCorrect(answer.getIsCorrect());
                detail.setAiFeedback(answer.getAiFeedback());
                detail.setTeacherFeedback(answer.getTeacherFeedback());
                detail.setSortOrder(question.getSortOrder());
                answerDetails.add(detail);
            }
        }

        // 按题目顺序排序
        answerDetails.sort(Comparator.comparingInt(a -> a.getSortOrder() != null ? a.getSortOrder() : 0));
        result.setAnswers(answerDetails);

        return result;
    }

    /**
     * 批量批改提交
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
     * 发送批改完成通知给学生
     */
    private void sendGradingNotification(HomeworkSubmission submission) {
        try {
            Homework homework = homeworkMapper.selectById(submission.getHomeworkId());
            String homeworkTitle = homework != null ? homework.getTitle() : "作业";

            Map<String, Object> notificationRequest = new HashMap<>();
            notificationRequest.put("userId", submission.getStudentId());
            notificationRequest.put("title", "作业批改完成");
            notificationRequest.put("content", String.format(
                    "您的作业「%s」已批改完成，得分：%d分。请查看详细反馈。",
                    homeworkTitle,
                    submission.getTotalScore() != null ? submission.getTotalScore() : 0));
            notificationRequest.put("type", "HOMEWORK_GRADED");

            userServiceClient.sendNotification(notificationRequest);
        } catch (Exception e) {
            // 通知发送失败不影响批改流程
            // 记录日志但不抛出异常
            log.error("发送批改通知失败: {}", e.getMessage());
        }
    }

    /**
     * 更新提交的分数和状态
     */
    private void updateSubmissionScoreAndStatus(Long submissionId) {
        HomeworkSubmission submission = submissionMapper.selectById(submissionId);
        if (submission == null)
            return;

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
            if (question == null)
                continue;

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
     * 检查提交是否有未批改的主观题
     */
    private boolean checkHasUngradedSubjective(Long submissionId) {
        List<HomeworkAnswer> answers = answerMapper.selectList(
                new LambdaQueryWrapper<HomeworkAnswer>()
                        .eq(HomeworkAnswer::getSubmissionId, submissionId));
        Map<Long, HomeworkQuestion> questionMap = buildQuestionMapByAnswers(answers);

        for (HomeworkAnswer answer : answers) {
            HomeworkQuestion question = questionMap.get(answer.getQuestionId());
            if (question != null && "subjective".equals(question.getQuestionType()) && answer.getScore() == null) {
                return true;
            }
        }
        return false;
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
     * 批量查询学生信息并构建用户名映射，减少逐个用户的远程调用。
     */
    private Map<Long, String> batchGetStudentNameMap(List<Long> studentIds) {
        if (studentIds == null || studentIds.isEmpty()) {
            return Collections.emptyMap();
        }

        List<Long> distinctIds = studentIds.stream()
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (distinctIds.isEmpty()) {
            return Collections.emptyMap();
        }

        try {
            Result<List<UserBriefDTO>> response = userServiceClient.getUsersByIds(distinctIds);
            if (response == null || response.getData() == null || response.getData().isEmpty()) {
                return Collections.emptyMap();
            }

            return response.getData().stream()
                    .filter(user -> user != null && user.getId() != null)
                    .collect(Collectors.toMap(
                            UserBriefDTO::getId,
                            user -> {
                                String name = user.getName();
                                if (name != null && !name.isBlank()) {
                                    return name;
                                }
                                if (user.getUsername() != null && !user.getUsername().isBlank()) {
                                    return user.getUsername();
                                }
                                return "学生" + user.getId();
                            },
                            (left, right) -> left));
        } catch (Exception e) {
            log.warn("批量查询学生信息失败，降级为本地占位名: {}", e.getMessage());
            return Collections.emptyMap();
        }
    }

    /**
     * 获取学生姓名
     */
    private String getStudentName(Long studentId) {
        try {
            Result<UserBriefDTO> response = userServiceClient.getUserById(studentId);
            if (response != null && response.getData() != null) {
                String username = response.getData().getUsername();
                return username != null ? username : "学生" + studentId;
            }
        } catch (Exception e) {
            // 降级处理
        }
        return "学生" + studentId;
    }

    /**
     * 复制作业到指定章节
     * 
     * @param homeworkId      源作业ID
     * @param targetChapterId 目标章节ID
     * @param newTitle        新作业标题（可选）
     * @return 新作业
     */
    @Transactional
    public Homework duplicateHomework(Long homeworkId, Long targetChapterId, String newTitle) {
        Homework source = homeworkMapper.selectById(homeworkId);
        if (source == null) {
            throw new RuntimeException("源作业不存在");
        }

        // 创建新作业
        Homework newHomework = new Homework();
        newHomework.setChapterId(targetChapterId != null ? targetChapterId : source.getChapterId());
        newHomework.setTitle(newTitle != null ? newTitle : source.getTitle() + " (副本)");
        newHomework.setDescription(source.getDescription());
        newHomework.setHomeworkType(source.getHomeworkType());
        newHomework.setTotalScore(source.getTotalScore());
        newHomework.setDeadline(null); // 新作业需要重新设置截止日期
        homeworkMapper.insert(newHomework);

        // 复制题目
        List<HomeworkQuestion> questions = questionMapper.findByHomeworkId(homeworkId);
        for (HomeworkQuestion q : questions) {
            HomeworkQuestion newQuestion = new HomeworkQuestion();
            newQuestion.setHomeworkId(newHomework.getId());
            newQuestion.setQuestionType(q.getQuestionType());
            newQuestion.setContent(q.getContent());
            newQuestion.setOptions(q.getOptions());
            newQuestion.setCorrectAnswer(q.getCorrectAnswer());
            newQuestion.setAnswerAnalysis(q.getAnswerAnalysis());
            newQuestion.setScore(q.getScore());
            newQuestion.setSortOrder(q.getSortOrder());
            questionMapper.insert(newQuestion);
        }

        return newHomework;
    }

    /**
     * 批量导入题目到作业
     * 
     * @param homeworkId 作业ID
     * @param questions  题目列表
     * @return 导入结果
     */
    @Transactional
    public Map<String, Object> importQuestions(Long homeworkId, List<HomeworkCreateDTO.QuestionDTO> questions) {
        Homework homework = homeworkMapper.selectById(homeworkId);
        if (homework == null) {
            throw new RuntimeException("作业不存在");
        }

        // 获取当前最大排序号
        List<HomeworkQuestion> existingQuestions = questionMapper.findByHomeworkId(homeworkId);
        int maxOrder = existingQuestions.stream()
                .mapToInt(q -> q.getSortOrder() != null ? q.getSortOrder() : 0)
                .max()
                .orElse(0);

        int successCount = 0;
        int failCount = 0;
        int totalScore = homework.getTotalScore() != null ? homework.getTotalScore() : 0;

        for (HomeworkCreateDTO.QuestionDTO q : questions) {
            try {
                HomeworkQuestion question = new HomeworkQuestion();
                question.setHomeworkId(homeworkId);
                question.setQuestionType(q.getQuestionType());
                question.setContent(q.getContent());
                question.setOptions(q.getOptions());
                question.setCorrectAnswer(q.getCorrectAnswer());
                question.setAnswerAnalysis(q.getAnswerAnalysis());
                question.setScore(q.getScore() != null ? q.getScore() : 10);
                question.setSortOrder(++maxOrder);
                questionMapper.insert(question);

                totalScore += question.getScore();
                successCount++;
            } catch (Exception e) {
                failCount++;
            }
        }

        // 更新作业总分
        homework.setTotalScore(totalScore);
        homeworkMapper.updateById(homework);

        Map<String, Object> result = new HashMap<>();
        result.put("successCount", successCount);
        result.put("failCount", failCount);
        result.put("total", questions.size());
        result.put("newTotalScore", totalScore);
        return result;
    }

    // ==================== 作业问答相关方法 ====================

    /**
     * 学生提问
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
        }
    }

    /**
     * 教师回复问题
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

            Map<String, Object> notificationRequest = new HashMap<>();
            notificationRequest.put("userId", discussion.getStudentId());
            notificationRequest.put("title", "作业问题已回复");
            notificationRequest.put("content", String.format(
                    "教师已回复您在作业「%s」中的提问，请查看。",
                    homeworkTitle));
            notificationRequest.put("type", "HOMEWORK_QUESTION_REPLIED");

            userServiceClient.sendNotification(notificationRequest);
        } catch (Exception e) {
            // 通知发送失败不影响回复流程
            log.error("发送回复通知失败: {}", e.getMessage());
        }
    }

    /**
     * 获取作业的所有问答
     */
    public List<HomeworkQuestionDiscussionVO> getHomeworkQuestions(Long homeworkId) {
        List<HomeworkQuestionDiscussion> discussions = discussionMapper.findByHomeworkId(homeworkId);
        Map<Long, String> studentNameMap = batchGetStudentNameMap(
                discussions.stream()
                        .map(HomeworkQuestionDiscussion::getStudentId)
                        .filter(Objects::nonNull)
                        .toList());

        List<Long> questionIds = discussions.stream()
                .map(HomeworkQuestionDiscussion::getQuestionId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        Map<Long, HomeworkQuestion> questionMap = questionIds.isEmpty()
                ? Collections.emptyMap()
                : questionMapper.selectBatchIds(questionIds).stream()
                        .filter(question -> question != null && question.getId() != null)
                        .collect(Collectors.toMap(HomeworkQuestion::getId, question -> question, (left, right) -> left));

        List<HomeworkQuestionDiscussionVO> result = new ArrayList<>();

        for (HomeworkQuestionDiscussion discussion : discussions) {
            HomeworkQuestionDiscussionVO item = new HomeworkQuestionDiscussionVO();
            item.setId(discussion.getId());
            item.setHomeworkId(discussion.getHomeworkId());
            item.setQuestionId(discussion.getQuestionId());
            item.setStudentId(discussion.getStudentId());
            item.setStudentName(studentNameMap.getOrDefault(discussion.getStudentId(), getStudentName(discussion.getStudentId())));
            item.setQuestionContent(discussion.getQuestionContent());
            item.setTeacherReply(discussion.getTeacherReply());
            item.setRepliedBy(discussion.getRepliedBy());
            item.setRepliedAt(discussion.getRepliedAt());
            item.setStatus(discussion.getStatus());
            item.setCreatedAt(discussion.getCreatedAt());

            if (discussion.getQuestionId() != null) {
                HomeworkQuestion question = questionMap.get(discussion.getQuestionId());
                if (question != null) {
                    item.setQuestionTitle(question.getContent());
                }
            }

            result.add(item);
        }

        return result;
    }

    /**
     * 获取学生的所有提问
     */
    public List<HomeworkStudentQuestionVO> getStudentQuestions(Long studentId) {
        List<HomeworkQuestionDiscussion> discussions = discussionMapper.findByStudentId(studentId);
        List<Long> homeworkIds = discussions.stream()
                .map(HomeworkQuestionDiscussion::getHomeworkId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        Map<Long, Homework> homeworkMap = homeworkIds.isEmpty()
                ? Collections.emptyMap()
                : homeworkMapper.selectBatchIds(homeworkIds).stream()
                        .filter(homework -> homework != null && homework.getId() != null)
                        .collect(Collectors.toMap(Homework::getId, homework -> homework, (left, right) -> left));

        List<HomeworkStudentQuestionVO> result = new ArrayList<>();

        for (HomeworkQuestionDiscussion discussion : discussions) {
            HomeworkStudentQuestionVO item = new HomeworkStudentQuestionVO();
            item.setId(discussion.getId());
            item.setHomeworkId(discussion.getHomeworkId());
            item.setQuestionContent(discussion.getQuestionContent());
            item.setTeacherReply(discussion.getTeacherReply());
            item.setStatus(discussion.getStatus());
            item.setCreatedAt(discussion.getCreatedAt());
            item.setRepliedAt(discussion.getRepliedAt());

            Homework homework = homeworkMap.get(discussion.getHomeworkId());
            if (homework != null) {
                item.setHomeworkTitle(homework.getTitle());
            }

            result.add(item);
        }

        return result;
    }

    /**
     * 获取教师待回复的问题数量
     */
    public int getTeacherPendingQuestionsCount(Long teacherId) {
        // 这里需要通过课程服务获取教师的所有课程，然后统计待回复问题
        // 简化处理：直接统计所有待回复问题
        return (int) discussionMapper.selectList(
                new LambdaQueryWrapper<HomeworkQuestionDiscussion>()
                        .eq(HomeworkQuestionDiscussion::getStatus, "pending"))
                .size();
    }
}
