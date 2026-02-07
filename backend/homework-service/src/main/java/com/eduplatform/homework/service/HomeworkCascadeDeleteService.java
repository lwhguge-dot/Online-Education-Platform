package com.eduplatform.homework.service;

import com.eduplatform.homework.mapper.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 作业级联删除服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HomeworkCascadeDeleteService {

    private final HomeworkMapper homeworkMapper;
    private final HomeworkQuestionMapper homeworkQuestionMapper;
    private final HomeworkSubmissionMapper homeworkSubmissionMapper;
    private final HomeworkAnswerMapper homeworkAnswerMapper;
    private final HomeworkUnlockMapper homeworkUnlockMapper;
    private final SubjectiveAnswerPermissionMapper subjectiveAnswerPermissionMapper;
    private final SubjectiveCommentMapper subjectiveCommentMapper;
    private final TeachingEventMapper teachingEventMapper;

    /**
     * 删除课程相关的所有作业数据
     */
    @Transactional
    public void deleteCourseRelatedData(Long courseId) {
        log.info("开始删除课程相关作业数据: courseId={}", courseId);

        // 1. 获取课程的所有作业ID
        List<Long> homeworkIds = homeworkMapper.findIdsByCourseId(courseId);
        log.info("课程包含 {} 个作业", homeworkIds.size());

        // 2. 删除每个作业的相关数据
        for (Long homeworkId : homeworkIds) {
            deleteHomeworkRelatedData(homeworkId);
        }

        // 3. 删除作业本身
        int homeworkCount = homeworkMapper.deleteByCourseId(courseId);
        log.info("删除作业: {} 条", homeworkCount);

        // 4. 删除课程相关的主观题评论
        int commentCount = subjectiveCommentMapper.deleteByCourseId(courseId);
        log.info("删除主观题评论: {} 条", commentCount);

        // 5. 删除课程相关的教学事件
        int eventCount = teachingEventMapper.deleteByCourseId(courseId);
        log.info("删除教学事件: {} 条", eventCount);

        log.info("课程相关作业数据删除完成: courseId={}", courseId);
    }

    /**
     * 删除用户相关的所有作业数据
     */
    @Transactional
    public void deleteUserRelatedData(Long userId) {
        log.info("开始删除用户相关作业数据: userId={}", userId);

        // 1. 删除用户的作业答案
        List<Long> submissionIds = homeworkSubmissionMapper.findIdsByStudentId(userId);
        for (Long submissionId : submissionIds) {
            homeworkAnswerMapper.deleteBySubmissionId(submissionId);
        }
        log.info("删除用户作业答案");

        // 2. 删除用户的作业提交记录
        int submissionCount = homeworkSubmissionMapper.deleteByStudentId(userId);
        log.info("删除作业提交记录: {} 条", submissionCount);

        // 3. 删除用户的作业解锁记录
        int unlockCount = homeworkUnlockMapper.deleteByStudentId(userId);
        log.info("删除作业解锁记录: {} 条", unlockCount);

        // 4. 删除用户的主观题权限记录
        int permissionCount = subjectiveAnswerPermissionMapper.deleteByStudentId(userId);
        log.info("删除主观题权限记录: {} 条", permissionCount);

        // 5. 删除用户的主观题评论
        int commentCount = subjectiveCommentMapper.deleteByUserId(userId);
        log.info("删除主观题评论: {} 条", commentCount);

        // 6. 删除教师的教学事件
        int eventCount = teachingEventMapper.deleteByTeacherId(userId);
        log.info("删除教学事件: {} 条", eventCount);

        log.info("用户相关作业数据删除完成: userId={}", userId);
    }

    /**
     * 删除单个作业的相关数据
     */
    private void deleteHomeworkRelatedData(Long homeworkId) {
        // 获取作业的所有题目ID
        List<Long> questionIds = homeworkQuestionMapper.findIdsByHomeworkId(homeworkId);

        // 删除每个题目的相关数据
        for (Long questionId : questionIds) {
            // 删除主观题权限记录
            subjectiveAnswerPermissionMapper.deleteByQuestionId(questionId);
            // 删除主观题评论
            subjectiveCommentMapper.deleteByQuestionId(questionId);
            // 删除答案记录
            homeworkAnswerMapper.deleteByQuestionId(questionId);
        }

        // 删除作业题目
        homeworkQuestionMapper.deleteByHomeworkId(homeworkId);

        // 获取作业的所有提交ID
        List<Long> submissionIds = homeworkSubmissionMapper.findIdsByHomeworkId(homeworkId);
        for (Long submissionId : submissionIds) {
            homeworkAnswerMapper.deleteBySubmissionId(submissionId);
        }

        // 删除作业提交记录
        homeworkSubmissionMapper.deleteByHomeworkId(homeworkId);

        // 删除作业解锁记录
        homeworkUnlockMapper.deleteByHomeworkId(homeworkId);

        // 删除相关教学事件
        teachingEventMapper.deleteByHomeworkId(homeworkId);
    }
}
