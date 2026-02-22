package com.eduplatform.homework.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.eduplatform.homework.dto.*;
import com.eduplatform.homework.entity.*;
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

    private final HomeworkUnlockMapper unlockMapper;
    private final HomeworkReadService homeworkReadService;
    private final HomeworkDiscussionService homeworkDiscussionService;
    private final HomeworkGradingService homeworkGradingService;
    private final HomeworkAuthoringService homeworkAuthoringService;
    private final HomeworkSubmissionService homeworkSubmissionService;

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
        return homeworkAuthoringService.createHomework(dto);
    }

    /**
     * 检索作业结构化详情
     *
     * @param homeworkId 作业唯一标识
     * @return 包含 Homework 元数据与 Question 列表的复合 Map
     */
    public Map<String, Object> getHomeworkDetail(Long homeworkId) {
        return homeworkReadService.getHomeworkDetail(homeworkId);
    }

    /**
     * 获取指定章节下的所有作业
     *
     * @param chapterId 章节标识
     * @return 作业列表
     */
    public List<Homework> getHomeworksByChapter(Long chapterId) {
        return homeworkReadService.getHomeworksByChapter(chapterId);
    }

    /**
     * 获取章节作业列表及其提交度快照 (Teacher Dashboard Data)
     * 用于教师端课程管理页面，展示每个作业的批改进度。
     *
     * @param chapterId 章节标识
     * @return 包含提交数与批改数的 DTO 列表
     */
    public List<HomeworkWithStatsDTO> getHomeworksByChapterWithStats(Long chapterId) {
        return homeworkReadService.getHomeworksByChapterWithStats(chapterId);
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
        return homeworkReadService.getStudentHomeworks(studentId, chapterId);
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
        return homeworkSubmissionService.submitHomework(dto);
    }

    /**
     * 获取详细的提交答题报告 (Report Details)
     *
     * @param homeworkId 作业标识
     * @param studentId  学生标识
     * @return 包含题目背板、学生原图、得分及批语的详情包
     */
    public Map<String, Object> getSubmissionDetail(Long homeworkId, Long studentId) {
        return homeworkReadService.getSubmissionDetail(homeworkId, studentId);
    }

    /**
     * 获取错题报告
     */
    public Map<String, Object> getErrorReport(Long studentId, Long homeworkId) {
        return homeworkReadService.getErrorReport(studentId, homeworkId);
    }

    /**
     * 教师批改主观题
     */
    @Transactional
    public void gradeSubjective(Long submissionId, Long questionId, Integer score, String feedback) {
        homeworkGradingService.gradeSubjective(submissionId, questionId, score, feedback);
    }

    /**
     * 获取作业的所有提交记录（教师端用于批改）
     */
    public List<Map<String, Object>> getSubmissionsByHomework(Long homeworkId) {
        return homeworkReadService.getSubmissionsByHomework(homeworkId);
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
     * 获取教师待办事项（真实数据）
     */
    public List<Map<String, Object>> getTeacherTodos(Long teacherId) {
        return homeworkReadService.getTeacherTodos(teacherId);
    }

    /**
     * 获取学生紧急作业（截止日期在指定天数内的未完成作业）
     */
    public List<Map<String, Object>> getStudentUrgentHomeworks(Long studentId, Integer days) {
        return homeworkReadService.getStudentUrgentHomeworks(studentId, days);
    }

    /**
     * 获取学生待完成作业数量
     */
    public int getStudentPendingHomeworkCount(Long studentId) {
        return homeworkReadService.getStudentPendingHomeworkCount(studentId);
    }

    /**
     * 获取教师最近活动（真实数据）
     */
    public List<Map<String, Object>> getTeacherActivities(Long teacherId) {
        return homeworkReadService.getTeacherActivities(teacherId);
    }

    // ==================== 批改工作台相关方法 ====================

    /**
     * 获取作业的待批改提交列表
     */
    public PendingSubmissionsDTO getPendingSubmissions(Long homeworkId) {
        return homeworkReadService.getPendingSubmissions(homeworkId);
    }

    /**
     * 获取提交详情（用于批改工作台）
     */
    public SubmissionDetailDTO getSubmissionDetailForGrading(Long submissionId) {
        return homeworkReadService.getSubmissionDetailForGrading(submissionId);
    }

    /**
     * 批量批改提交
     */
    @Transactional
    public void gradeSubmission(Long submissionId, GradeSubmissionDTO dto) {
        homeworkGradingService.gradeSubmission(submissionId, dto);
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
        return homeworkAuthoringService.duplicateHomework(homeworkId, targetChapterId, newTitle);
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
        return homeworkAuthoringService.importQuestions(homeworkId, questions);
    }

    // ==================== 作业问答相关方法 ====================

    /**
     * 学生提问
     */
    @Transactional
    public void askQuestion(Long homeworkId, Long studentId, Long questionId, String content) {
        homeworkDiscussionService.askQuestion(homeworkId, studentId, questionId, content);
    }

    /**
     * 教师回复问题
     */
    @Transactional
    public void replyQuestion(Long discussionId, Long teacherId, String reply) {
        homeworkDiscussionService.replyQuestion(discussionId, teacherId, reply);
    }

    /**
     * 获取作业的所有问答
     */
    public List<HomeworkQuestionDiscussionVO> getHomeworkQuestions(Long homeworkId) {
        return homeworkReadService.getHomeworkQuestions(homeworkId);
    }

    /**
     * 获取学生的所有提问
     */
    public List<HomeworkStudentQuestionVO> getStudentQuestions(Long studentId) {
        return homeworkReadService.getStudentQuestions(studentId);
    }

    /**
     * 获取教师待回复的问题数量
     */
    public int getTeacherPendingQuestionsCount(Long teacherId) {
        return homeworkReadService.getTeacherPendingQuestionsCount(teacherId);
    }
}
