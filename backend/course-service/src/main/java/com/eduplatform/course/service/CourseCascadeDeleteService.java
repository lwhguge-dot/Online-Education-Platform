package com.eduplatform.course.service;

import com.eduplatform.common.exception.BusinessException;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.eduplatform.course.entity.Chapter;
import com.eduplatform.course.entity.Course;
import com.eduplatform.course.feign.HomeworkServiceClient;
import com.eduplatform.course.feign.ProgressServiceClient;
import com.eduplatform.course.mapper.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.ArrayList;
import java.util.List;

/**
 * 核心级联删除服务 (课程资源终结者)
 * 负责执行高风险、深度关联的数据擦除操作，确保课程或用户注销时，系统各维度冗余数据的原子化清理。
 *
 * <p>事务与 RPC 模型：
 * <ul>
 *   <li>跨服务 Feign 调用（progress/homework）在 <b>本地事务之外</b> 执行，避免占用 DB 连接导致连接池雪崩。</li>
 *   <li>本地多表删除（quiz/comment/like/chapter/enrollment/muted/blocked_word/course）在单事务内原子提交。</li>
 *   <li>物理文件回收通过 {@code afterCommit} 回调在事务提交后执行，保证「DB 未提交则不误删文件」。</li>
 * </ul>
 *
 * @author Antigravity
 */
@Slf4j
@Service
public class CourseCascadeDeleteService {

    private final CourseMapper courseMapper;
    private final ChapterMapper chapterMapper;
    private final ChapterQuizMapper chapterQuizMapper;
    private final EnrollmentMapper enrollmentMapper;
    private final ChapterCommentMapper chapterCommentMapper;
    private final CommentLikeMapper commentLikeMapper;
    private final MutedUserMapper mutedUserMapper;
    private final BlockedWordMapper blockedWordMapper;
    private final HomeworkServiceClient homeworkServiceClient;
    private final ProgressServiceClient progressServiceClient;
    private final FileUploadService fileUploadService;
    // 自注入 Spring 代理，用于调用本类的 @Transactional 方法，
    // 规避同类方法间 this 调用绕过 AOP 代理导致事务失效的问题。
    private final CourseCascadeDeleteService self;

    public CourseCascadeDeleteService(
            CourseMapper courseMapper,
            ChapterMapper chapterMapper,
            ChapterQuizMapper chapterQuizMapper,
            EnrollmentMapper enrollmentMapper,
            ChapterCommentMapper chapterCommentMapper,
            CommentLikeMapper commentLikeMapper,
            MutedUserMapper mutedUserMapper,
            BlockedWordMapper blockedWordMapper,
            HomeworkServiceClient homeworkServiceClient,
            ProgressServiceClient progressServiceClient,
            FileUploadService fileUploadService,
            @Lazy CourseCascadeDeleteService self) {
        this.courseMapper = courseMapper;
        this.chapterMapper = chapterMapper;
        this.chapterQuizMapper = chapterQuizMapper;
        this.enrollmentMapper = enrollmentMapper;
        this.chapterCommentMapper = chapterCommentMapper;
        this.commentLikeMapper = commentLikeMapper;
        this.mutedUserMapper = mutedUserMapper;
        this.blockedWordMapper = blockedWordMapper;
        this.homeworkServiceClient = homeworkServiceClient;
        this.progressServiceClient = progressServiceClient;
        this.fileUploadService = fileUploadService;
        this.self = self;
    }

    /**
     * 课程全链路级联销毁。
     *
     * <p>执行顺序（关键）：
     * <ol>
     *   <li>资源快照：检索章节视频与封面 URL。</li>
     *   <li>跨服务同步：在事务外调用 progress/homework 清理远端数据；任一失败抛异常终止流程。</li>
     *   <li>本地事务：删除 quiz/comment/like/chapter/enrollment/muted/blocked_word/course。</li>
     *   <li>物理文件回收：通过 afterCommit 回调在事务提交后执行。</li>
     * </ol>
     *
     * @param courseId 目标课程 ID
     * @throws BusinessException 当课程不存在或远端服务不可用时抛出
     */
    public void cascadeDeleteCourse(Long courseId) {
        Course course = courseMapper.selectById(courseId);
        if (course == null) {
            throw new BusinessException("操作失败：目标课程不存在或已被销毁");
        }

        log.info("级联审计：启动课程清理流 | courseId={}, title={}", courseId, course.getTitle());

        // 1. 资源快照提取：在事务外读取，避免事务持有时间过长
        List<Chapter> chapters = chapterMapper.selectList(
                new LambdaQueryWrapper<Chapter>().eq(Chapter::getCourseId, courseId));
        List<Long> chapterIds = chapters.stream().map(Chapter::getId).toList();

        List<String> filesToDelete = new ArrayList<>();
        for (Chapter chapter : chapters) {
            if (chapter.getVideoUrl() != null && !chapter.getVideoUrl().isEmpty()) {
                filesToDelete.add(chapter.getVideoUrl());
            }
        }
        if (course.getCoverImage() != null && !course.getCoverImage().isEmpty()) {
            filesToDelete.add(course.getCoverImage());
        }

        // 2. 跨域联动：在事务外同步清理远端数据，失败抛异常终止流程。
        //    不在事务内执行 RPC，避免 DB 连接被远程调用长时间占用导致连接池耗尽。
        try {
            progressServiceClient.deleteCourseRelatedData(courseId);
            log.info("RPC 调用：同步清理 Progress-service 成功");
        } catch (Exception e) {
            log.error("RPC 异常：Progress 数据同步失败，终止删除 courseId={}", courseId, e);
            throw new BusinessException("级联删除失败：学习进度服务不可用，请稍后重试");
        }

        try {
            homeworkServiceClient.deleteCourseRelatedData(courseId);
            log.info("RPC 调用：同步清理 Homework-service 成功");
        } catch (Exception e) {
            log.error("RPC 异常：Homework 数据同步失败，终止删除 courseId={}", courseId, e);
            throw new BusinessException("级联删除失败：作业服务不可用，请稍后重试");
        }

        // 3. 本地事务：原子删除所有本地关联表
        // 通过 self 代理调用，确保 @Transactional 生效。
        self.doLocalCascadeDelete(courseId, chapterIds);

        // 4. 物理文件回收：注册 afterCommit 回调，事务提交后再删除文件，
        //    避免「DB 回滚但文件已删」的不一致。
        if (!filesToDelete.isEmpty() && TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    for (String filePath : filesToDelete) {
                        try {
                            fileUploadService.deleteFile(filePath);
                        } catch (Exception e) {
                            log.error("IO 审计失败：物理文件清理中断 {}", filePath, e);
                        }
                    }
                }
            });
        } else if (!filesToDelete.isEmpty()) {
            // 兜底：无事务上下文时直接删除（理论上不会走到，doLocalCascadeDelete 已开事务）。
            for (String filePath : filesToDelete) {
                try {
                    fileUploadService.deleteFile(filePath);
                } catch (Exception e) {
                    log.error("IO 审计失败：物理文件清理中断 {}", filePath, e);
                }
            }
        }

        log.info("级联审计：课程清理成功结束 courseId={}", courseId);
    }

    /**
     * 本地多表原子删除（独立事务）。
     * 不包含跨服务调用，保证事务尽可能短小。
     */
    @Transactional
    public void doLocalCascadeDelete(Long courseId, List<Long> chapterIds) {
        // 测验数据清理
        for (Long chapterId : chapterIds) {
            chapterQuizMapper.deleteByChapterId(chapterId);
        }

        // 评论点赞关系清理
        List<Long> commentIds = chapterCommentMapper.findIdsByCourseId(courseId);
        for (Long commentId : commentIds) {
            commentLikeMapper.deleteByCommentId(commentId);
        }

        // 业务数据批量擦除
        chapterCommentMapper.deleteByCourseId(courseId);
        chapterMapper.deleteByCourseId(courseId);
        enrollmentMapper.deleteByCourseId(courseId);
        mutedUserMapper.deleteByCourseId(courseId);
        blockedWordMapper.deleteByCourseId(courseId);

        // 主记录最后删除
        courseMapper.deleteById(courseId);
        log.info("持久层审计：课程主表记录已移除");
    }

    /**
     * 用户注销级联清理 (基于角色的数据脱敏)
     *
     * @param userId 操作人/注销人 ID
     * @param role   角色标识符 (teacher/student)
     */
    public void deleteUserRelatedData(Long userId, String role) {
        log.info("级联审计：启动用户关联清理 | userId={}, role={}", userId, role);

        if ("teacher".equals(role)) {
            // 教师注销：深度触发其名下所有课程的销毁逻辑
            List<Long> courseIds = courseMapper.findIdsByTeacherId(userId);
            for (Long courseId : courseIds) {
                try {
                    cascadeDeleteCourse(courseId);
                } catch (Exception e) {
                    // 单个课程失败不阻断其它课程清理，但记录告警以便对账
                    log.error("级联审计失败：教师课程销毁中断 courseId={}", courseId, e);
                }
            }
        }

        if ("student".equals(role)) {
            // 学生注销：解除所有选课契约
            enrollmentMapper.deleteByStudentId(userId);
        }

        // 社会关系清理：点赞、评论、禁言足迹
        commentLikeMapper.deleteByUserId(userId);
        chapterCommentMapper.deleteByUserId(userId);
        mutedUserMapper.deleteByUserId(userId);

        // 跨服务联动：在事务外同步远端，失败抛异常让调用方感知并重试
        try {
            progressServiceClient.deleteUserRelatedData(userId);
        } catch (Exception e) {
            log.error("RPC 异常：Progress 用户数据同步失败 userId={}", userId, e);
            throw new BusinessException("级联删除失败：学习进度服务不可用，请稍后重试");
        }

        try {
            homeworkServiceClient.deleteUserRelatedData(userId);
        } catch (Exception e) {
            log.error("RPC 异常：Homework 用户数据同步失败 userId={}", userId, e);
            throw new BusinessException("级联删除失败：作业服务不可用，请稍后重试");
        }

        log.info("级联审计：用户关联清理成功结束 userId={}", userId);
    }
}
