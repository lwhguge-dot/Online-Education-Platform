package com.eduplatform.course.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.eduplatform.course.entity.Chapter;
import com.eduplatform.course.entity.Course;
import com.eduplatform.course.feign.HomeworkServiceClient;
import com.eduplatform.course.feign.ProgressServiceClient;
import com.eduplatform.course.mapper.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

/**
 * 核心级联删除服务 (课程资源终结者)
 * 负责执行高风险、深度关联的数据擦除操作，确保课程或用户注销时，系统各维度冗余数据的原子化清理。
 * 
 * 核心逻辑：
 * 1. 垂直级联：从课程本体向下渗透，清理章节、测验、评论、点赞、禁言、选课及词库。
 * 2. 水平对齐：通过 Feign 强一致或最终一致性地触发 Progress（进度）与 Homework（作业）服务的相关数据同步。
 * 3. 物理回收：作为数据库事务的后置环节，彻底销毁磁盘上的流媒体视频与图片资源。
 *
 * @author Antigravity
 */
@Slf4j
@Service
@RequiredArgsConstructor
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

    /**
     * 课程全链路级联销毁 (核心业务算法)
     * 遵循 12 步标准化清理流程，确保分布式环境下无数据孤岛。
     * 
     * 算法阶段：
     * 1. 环境普查：检索全量章节轨迹，提取物理文件指纹（视频 URL）。
     * 2. 内部降维：按照依赖顺序依次清理 测验 -> 评论态 -> 章节 -> 选课关系。
     * 3. 外部同步：强行触发外部微服务的数据下线信号。
     * 4. 物理擦除：在 DB 事务安全提交后，回收磁盘空间。
     * 
     * @param courseId 目标课程 ID
     * @throws RuntimeException 当课程不存在或核心持久层操作异常时抛出
     */
    @Transactional
    public void cascadeDeleteCourse(Long courseId) {
        Course course = courseMapper.selectById(courseId);
        if (course == null) {
            throw new RuntimeException("操作失败：目标课程不存在或已被销毁");
        }

        log.info("级联审计：启动课程清理流 | courseId={}, title={}", courseId, course.getTitle());

        // 1. 资源快照提取：备份物理路径以防事务回滚导致文件误删 (NIO 策略)
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

        // 2. 交互数据清理：测验、评论点赞关系
        for (Long chapterId : chapterIds) {
            chapterQuizMapper.deleteByChapterId(chapterId);
        }

        // 处理评论社交足迹
        List<Long> commentIds = chapterCommentMapper.findIdsByCourseId(courseId);
        for (Long commentId : commentIds) {
            commentLikeMapper.deleteByCommentId(commentId);
        }

        // 3. 业务数据批量擦除
        chapterCommentMapper.deleteByCourseId(courseId); // 评论内容
        chapterMapper.deleteByCourseId(courseId); // 教学章节
        enrollmentMapper.deleteByCourseId(courseId); // 学生选课契约
        mutedUserMapper.deleteByCourseId(courseId); // 社交管控记录
        blockedWordMapper.deleteByCourseId(courseId); // 课程私有词库

        // 4. 跨域联动：调用分布式微服务清理孤岛数据
        try {
            progressServiceClient.deleteCourseRelatedData(courseId);
            log.info("RPC 调用：同步清理 Progress-service 成功");
        } catch (Exception e) {
            log.warn("RPC 异常：Progress 数据同步失败（可能导致统计偏差）, error={}", e.getMessage());
        }

        try {
            homeworkServiceClient.deleteCourseRelatedData(courseId);
            log.info("RPC 调用：同步清理 Homework-service 成功");
        } catch (Exception e) {
            log.warn("RPC 异常：Homework 数据同步失败, error={}", e.getMessage());
        }

        // 5. 实体卸载
        courseMapper.deleteById(courseId);
        log.info("持久层审计：课程主表记录已移除");

        // 6. 物理资源回收 (在事务同步器完成后，或利用 Aspect 执行)
        for (String filePath : filesToDelete) {
            try {
                fileUploadService.deleteFile(filePath);
            } catch (Exception e) {
                log.error("IO 审计失败：物理文件清理中断 {}", filePath, e);
            }
        }

        log.info("级联审计：课程清理成功结束 courseId={}", courseId);
    }

    /**
     * 用户注销级联清理 (基于角色的数据脱敏)
     * 根据注销用户在课程体系内的角色（学生/教师），执行不同维度的存量数据清理。
     *
     * @param userId 操作人/注销人 ID
     * @param role   角色标识符 (teacher/student)
     */
    @Transactional
    public void deleteUserRelatedData(Long userId, String role) {
        log.info("级联审计：启动用户关联清理 | userId={}, role={}", userId, role);

        if ("teacher".equals(role)) {
            // 教师注销：深度触发其名下所有课程的销毁逻辑
            List<Long> courseIds = courseMapper.findIdsByTeacherId(userId);
            for (Long courseId : courseIds) {
                try {
                    cascadeDeleteCourse(courseId);
                } catch (Exception e) {
                    log.error("级联审计失败：教师课程销毁中断 courseId={}", courseId, e);
                }
            }
        }

        if ("student".equals(role)) {
            // 学生注销：解除所有选课契约，保障招生热度统计准确性
            enrollmentMapper.deleteByStudentId(userId);
        }

        // 社会关系清理：点赞、评论、禁言足迹
        commentLikeMapper.deleteByUserId(userId);
        chapterCommentMapper.deleteByUserId(userId);
        mutedUserMapper.deleteByUserId(userId);

        // 跨服务联动：触发进度与作业足迹清理
        try {
            progressServiceClient.deleteUserRelatedData(userId);
        } catch (Exception e) {
            log.warn("RPC 异常：Progress 用户数据同步失败 userId={}", userId);
        }

        try {
            homeworkServiceClient.deleteUserRelatedData(userId);
        } catch (Exception e) {
            log.warn("RPC 异常：Homework 用户数据同步失败 userId={}", userId);
        }

        log.info("级联审计：用户关联清理成功结束 userId={}", userId);
    }
}
