package com.eduplatform.homework.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.eduplatform.common.result.Result;
import com.eduplatform.homework.dto.HomeworkWithStatsDTO;
import com.eduplatform.homework.dto.PendingSubmissionsDTO;
import com.eduplatform.homework.dto.StudentHomeworkDTO;
import com.eduplatform.homework.dto.SubmissionDetailDTO;
import com.eduplatform.homework.dto.UserBriefDTO;
import com.eduplatform.homework.entity.Homework;
import com.eduplatform.homework.entity.HomeworkAnswer;
import com.eduplatform.homework.entity.HomeworkQuestion;
import com.eduplatform.homework.entity.HomeworkQuestionDiscussion;
import com.eduplatform.homework.entity.HomeworkSubmission;
import com.eduplatform.homework.entity.HomeworkUnlock;
import com.eduplatform.homework.feign.UserServiceClient;
import com.eduplatform.homework.mapper.HomeworkAnswerMapper;
import com.eduplatform.homework.mapper.HomeworkMapper;
import com.eduplatform.homework.mapper.HomeworkQuestionDiscussionMapper;
import com.eduplatform.homework.mapper.HomeworkQuestionMapper;
import com.eduplatform.homework.mapper.HomeworkSubmissionMapper;
import com.eduplatform.homework.mapper.HomeworkUnlockMapper;
import com.eduplatform.homework.vo.HomeworkQuestionDiscussionVO;
import com.eduplatform.homework.vo.HomeworkQuestionVO;
import com.eduplatform.homework.vo.HomeworkStudentQuestionVO;
import com.eduplatform.homework.vo.HomeworkSubmissionVO;
import com.eduplatform.homework.vo.HomeworkVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 作业读模型服务。
 * 说明：集中承接查询、统计与视图装配，降低 HomeworkService 的职责复杂度。
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class HomeworkReadService {

    private final HomeworkMapper homeworkMapper;
    private final HomeworkQuestionMapper questionMapper;
    private final HomeworkUnlockMapper unlockMapper;
    private final HomeworkSubmissionMapper submissionMapper;
    private final HomeworkAnswerMapper answerMapper;
    private final HomeworkQuestionDiscussionMapper discussionMapper;
    private final UserServiceClient userServiceClient;

    /**
     * 将作业实体转换为视图对象。
     */
    private HomeworkVO convertToVO(Homework homework) {
        if (homework == null) {
            return null;
        }
        HomeworkVO vo = new HomeworkVO();
        BeanUtils.copyProperties(homework, vo);
        return vo;
    }

    /**
     * 将题目实体转换为视图对象。
     */
    private HomeworkQuestionVO convertQuestionToVO(HomeworkQuestion question) {
        if (question == null) {
            return null;
        }
        HomeworkQuestionVO vo = new HomeworkQuestionVO();
        BeanUtils.copyProperties(question, vo);
        return vo;
    }

    /**
     * 将提交实体转换为视图对象。
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
     * 获取作业详情（作业信息 + 题目列表）。
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
     * 获取章节作业列表。
     */
    public List<Homework> getHomeworksByChapter(Long chapterId) {
        return homeworkMapper.findByChapterId(chapterId);
    }

    /**
     * 获取章节作业列表与提交统计。
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

            Long totalSubmissions = submissionMapper.selectCount(
                    new LambdaQueryWrapper<HomeworkSubmission>()
                            .eq(HomeworkSubmission::getHomeworkId, hw.getId())
                            .ne(HomeworkSubmission::getSubmitStatus, "draft"));

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
     * 获取学生在章节下的作业视图。
     */
    public List<StudentHomeworkDTO> getStudentHomeworks(Long studentId, Long chapterId) {
        List<Homework> homeworks = getHomeworksByChapter(chapterId);
        List<StudentHomeworkDTO> result = new ArrayList<>();

        for (Homework hw : homeworks) {
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
     * 获取学生提交详情。
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
     * 获取错题报告。
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
     * 获取作业的提交记录（教师批改视图）。
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
     * 获取教师待办事项。
     */
    public List<Map<String, Object>> getTeacherTodos(Long teacherId) {
        List<Map<String, Object>> todos = new ArrayList<>();

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
            todo.put("time", pendingSubmissions.get(0).getSubmittedAt() != null
                    ? pendingSubmissions.get(0).getSubmittedAt().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
                    : "");
            todos.add(todo);
        }

        return todos;
    }

    /**
     * 获取学生紧急作业列表。
     */
    public List<Map<String, Object>> getStudentUrgentHomeworks(Long studentId, Integer days) {
        List<Map<String, Object>> result = new ArrayList<>();

        List<HomeworkUnlock> unlocks = unlockMapper.selectList(
                new LambdaQueryWrapper<HomeworkUnlock>()
                        .eq(HomeworkUnlock::getStudentId, studentId)
                        .eq(HomeworkUnlock::getUnlockStatus, 1));

        if (unlocks.isEmpty()) {
            return result;
        }

        List<Long> unlockedHomeworkIds = unlocks.stream()
                .map(HomeworkUnlock::getHomeworkId)
                .toList();

        List<HomeworkSubmission> submissions = submissionMapper.selectList(
                new LambdaQueryWrapper<HomeworkSubmission>()
                        .eq(HomeworkSubmission::getStudentId, studentId)
                        .in(HomeworkSubmission::getHomeworkId, unlockedHomeworkIds)
                        .ne(HomeworkSubmission::getSubmitStatus, "draft"));

        Set<Long> submittedHomeworkIds = submissions.stream()
                .map(HomeworkSubmission::getHomeworkId)
                .collect(Collectors.toSet());

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime deadline = now.plusDays(days);

        for (Long homeworkId : unlockedHomeworkIds) {
            if (submittedHomeworkIds.contains(homeworkId)) {
                continue;
            }

            Homework homework = homeworkMapper.selectById(homeworkId);
            if (homework == null || homework.getDeadline() == null) {
                continue;
            }

            if (homework.getDeadline().isAfter(now) && homework.getDeadline().isBefore(deadline)) {
                Map<String, Object> item = new HashMap<>();
                item.put("id", homework.getId());
                item.put("title", homework.getTitle());
                item.put("deadline", homework.getDeadline().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")));
                item.put("chapterId", homework.getChapterId());
                item.put("daysLeft", java.time.Duration.between(now, homework.getDeadline()).toDays());
                item.put("courseName", "课程作业");
                result.add(item);
            }
        }

        result.sort((a, b) -> ((String) a.get("deadline")).compareTo((String) b.get("deadline")));
        return result;
    }

    /**
     * 获取学生待完成作业数量。
     */
    public int getStudentPendingHomeworkCount(Long studentId) {
        List<HomeworkUnlock> unlocks = unlockMapper.selectList(
                new LambdaQueryWrapper<HomeworkUnlock>()
                        .eq(HomeworkUnlock::getStudentId, studentId)
                        .eq(HomeworkUnlock::getUnlockStatus, 1));

        if (unlocks.isEmpty()) {
            return 0;
        }

        List<Long> unlockedHomeworkIds = unlocks.stream()
                .map(HomeworkUnlock::getHomeworkId)
                .toList();

        List<HomeworkSubmission> submissions = submissionMapper.selectList(
                new LambdaQueryWrapper<HomeworkSubmission>()
                        .eq(HomeworkSubmission::getStudentId, studentId)
                        .in(HomeworkSubmission::getHomeworkId, unlockedHomeworkIds)
                        .ne(HomeworkSubmission::getSubmitStatus, "draft"));

        Set<Long> submittedHomeworkIds = submissions.stream()
                .map(HomeworkSubmission::getHomeworkId)
                .collect(Collectors.toSet());

        return (int) unlockedHomeworkIds.stream()
                .filter(id -> !submittedHomeworkIds.contains(id))
                .count();
    }

    /**
     * 获取教师最近活动。
     */
    public List<Map<String, Object>> getTeacherActivities(Long teacherId) {
        List<Map<String, Object>> activities = new ArrayList<>();

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
            act.put("time", sub.getSubmittedAt() != null
                    ? sub.getSubmittedAt().format(DateTimeFormatter.ofPattern("HH:mm"))
                    : "");
            activities.add(act);
            if (activities.size() >= 5) {
                break;
            }
        }

        return activities;
    }

    /**
     * 获取待批改提交列表。
     */
    public PendingSubmissionsDTO getPendingSubmissions(Long homeworkId) {
        Homework homework = homeworkMapper.selectById(homeworkId);
        if (homework == null) {
            return null;
        }

        PendingSubmissionsDTO result = new PendingSubmissionsDTO();

        PendingSubmissionsDTO.HomeworkInfo homeworkInfo = new PendingSubmissionsDTO.HomeworkInfo();
        homeworkInfo.setId(homework.getId());
        homeworkInfo.setTitle(homework.getTitle());
        homeworkInfo.setTotalScore(homework.getTotalScore());
        homeworkInfo.setDeadline(homework.getDeadline());
        result.setHomework(homeworkInfo);

        List<HomeworkSubmission> submissions = submissionMapper.selectList(
                new LambdaQueryWrapper<HomeworkSubmission>()
                        .eq(HomeworkSubmission::getHomeworkId, homeworkId)
                        .ne(HomeworkSubmission::getSubmitStatus, "draft")
                        .orderByDesc(HomeworkSubmission::getSubmittedAt));

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
            summary.setHasUngraded(checkHasUngradedSubjective(submission.getId()));

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
     * 获取批改工作台提交详情。
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

        answerDetails.sort(Comparator.comparingInt(a -> a.getSortOrder() != null ? a.getSortOrder() : 0));
        result.setAnswers(answerDetails);
        return result;
    }

    /**
     * 获取作业问答列表。
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
     * 获取学生提问列表。
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
     * 检查提交是否存在未批改主观题。
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
     * 批量构建题目映射，避免循环内查询。
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

        List<HomeworkQuestion> questions = questionMapper.selectBatchIds(questionIds);
        return questions.stream()
                .filter(question -> question != null && question.getId() != null)
                .collect(Collectors.toMap(HomeworkQuestion::getId, question -> question, (left, right) -> left));
    }

    /**
     * 批量查询学生姓名映射。
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
            log.warn("批量查询学生信息失败，降级为占位名: {}", e.getMessage());
            return Collections.emptyMap();
        }
    }

    /**
     * 查询单个学生名称（批量失败时降级兜底）。
     */
    private String getStudentName(Long studentId) {
        try {
            Result<UserBriefDTO> response = userServiceClient.getUserById(studentId);
            if (response != null && response.getData() != null) {
                String username = response.getData().getUsername();
                return username != null ? username : "学生" + studentId;
            }
        } catch (Exception e) {
            log.debug("查询学生姓名失败，使用占位名: studentId={}", studentId);
        }
        return "学生" + studentId;
    }

    /**
     * 获取教师待回复问题数量。
     */
    public int getTeacherPendingQuestionsCount(Long teacherId) {
        return discussionMapper.selectList(
                new LambdaQueryWrapper<HomeworkQuestionDiscussion>()
                        .eq(HomeworkQuestionDiscussion::getStatus, "pending"))
                .size();
    }
}
