package com.eduplatform.homework.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.eduplatform.homework.entity.SubjectiveAnswerPermission;
import com.eduplatform.homework.entity.SubjectiveComment;
import com.eduplatform.homework.mapper.SubjectiveAnswerPermissionMapper;
import com.eduplatform.homework.mapper.SubjectiveCommentMapper;
import com.eduplatform.homework.vo.SubjectiveAnswerPermissionVO;
import com.eduplatform.homework.vo.SubjectiveCommentVO;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class CommentService {

    private final SubjectiveCommentMapper commentMapper;
    private final SubjectiveAnswerPermissionMapper permissionMapper;

    /**
     * 发布学生答案（解锁评论区）
     */
    @Transactional
    public SubjectiveAnswerPermission publishAnswer(Long studentId, Long questionId, String answerContent) {
        SubjectiveAnswerPermission permission = permissionMapper.selectOne(
                new LambdaQueryWrapper<SubjectiveAnswerPermission>()
                        .eq(SubjectiveAnswerPermission::getStudentId, studentId)
                        .eq(SubjectiveAnswerPermission::getQuestionId, questionId));

        if (permission == null) {
            permission = new SubjectiveAnswerPermission();
            permission.setStudentId(studentId);
            permission.setQuestionId(questionId);
            permission.setAnswerContent(answerContent);
            permission.setAnswerStatus(1);
            permission.setCommentVisible(1);
            permission.setAnsweredAt(LocalDateTime.now());
            permissionMapper.insert(permission);
        } else {
            permission.setAnswerContent(answerContent);
            permission.setAnswerStatus(1);
            permission.setCommentVisible(1);
            permission.setAnsweredAt(LocalDateTime.now());
            permissionMapper.updateById(permission);
        }

        return permission;
    }

    /**
     * 检查评论可见性权限
     */
    public boolean canViewComments(Long studentId, Long questionId) {
        SubjectiveAnswerPermission permission = permissionMapper.selectOne(
                new LambdaQueryWrapper<SubjectiveAnswerPermission>()
                        .eq(SubjectiveAnswerPermission::getStudentId, studentId)
                        .eq(SubjectiveAnswerPermission::getQuestionId, questionId));
        return permission != null && permission.getCommentVisible() == 1;
    }

    /**
     * 获取评论列表（根据权限过滤）
     */
    public Map<String, Object> getComments(Long questionId, Long studentId, boolean isTeacher) {
        boolean canViewAll = isTeacher || canViewComments(studentId, questionId);

        LambdaQueryWrapper<SubjectiveComment> query = new LambdaQueryWrapper<SubjectiveComment>()
                .eq(SubjectiveComment::getQuestionId, questionId)
                .eq(SubjectiveComment::getStatus, 1)
                .orderByDesc(SubjectiveComment::getIsTop)
                .orderByAsc(SubjectiveComment::getCreatedAt);

        List<SubjectiveComment> allComments = commentMapper.selectList(query);

        List<SubjectiveComment> visibleComments;
        if (canViewAll) {
            visibleComments = allComments;
        } else {
            // 未发布答案，只能看教师置顶的题目
            visibleComments = allComments.stream()
                    .filter(c -> c.getIsTop() == 1)
                    .collect(Collectors.toList());
        }

        List<SubjectiveCommentVO> commentViews = visibleComments.stream()
                .map(this::convertToCommentVO)
                .collect(Collectors.toList());

        Map<String, Object> result = new HashMap<>();
        result.put("comments", commentViews);
        result.put("totalCount", allComments.size());
        result.put("canViewAll", canViewAll);
        result.put("hasPublishedAnswer", canViewComments(studentId, questionId));

        return result;
    }

    /**
     * 发表评论
     */
    @Transactional
    public SubjectiveComment postComment(Long questionId, Long userId, String content, Long parentId,
            boolean isTeacher) {
        // 非教师需要先发布答案才能评论
        if (!isTeacher && !canViewComments(userId, questionId)) {
            throw new RuntimeException("请先发布您的答案后再参与讨论");
        }

        SubjectiveComment comment = new SubjectiveComment();
        comment.setQuestionId(questionId);
        comment.setUserId(userId);
        comment.setContent(content);
        comment.setParentId(parentId);
        comment.setIsAnswer(0);
        comment.setIsTop(0);
        comment.setLikeCount(0);
        comment.setStatus(1);
        commentMapper.insert(comment);

        return comment;
    }

    /**
     * 教师置顶评论/题目
     */
    @Transactional
    public void toggleTop(Long commentId) {
        SubjectiveComment comment = commentMapper.selectById(commentId);
        if (comment != null) {
            comment.setIsTop(comment.getIsTop() == 1 ? 0 : 1);
            commentMapper.updateById(comment);
        }
    }

    /**
     * 教师发布题目（置顶显示）
     */
    @Transactional
    public SubjectiveComment postQuestion(Long questionId, Long teacherId, String questionContent) {
        SubjectiveComment comment = new SubjectiveComment();
        comment.setQuestionId(questionId);
        comment.setUserId(teacherId);
        comment.setContent(questionContent);
        comment.setIsAnswer(0);
        comment.setIsTop(1);
        comment.setLikeCount(0);
        comment.setStatus(1);
        commentMapper.insert(comment);
        return comment;
    }

    /**
     * 删除评论
     */
    @Transactional
    public void deleteComment(Long commentId) {
        SubjectiveComment comment = commentMapper.selectById(commentId);
        if (comment != null) {
            comment.setStatus(0);
            commentMapper.updateById(comment);
        }
    }

    /**
     * 获取学生答案权限状态
     */
    public SubjectiveAnswerPermission getPermission(Long studentId, Long questionId) {
        return permissionMapper.selectOne(
                new LambdaQueryWrapper<SubjectiveAnswerPermission>()
                        .eq(SubjectiveAnswerPermission::getStudentId, studentId)
                        .eq(SubjectiveAnswerPermission::getQuestionId, questionId));
    }

    /**
     * 将评论实体转换为视图对象
     * 用于对外输出，避免直接暴露持久层数据。
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
     * 将答题权限实体转换为视图对象
     *
     * @param permission 权限实体
     * @return 权限视图对象
     */
    public SubjectiveAnswerPermissionVO convertToPermissionVO(SubjectiveAnswerPermission permission) {
        if (permission == null) {
            return null;
        }
        SubjectiveAnswerPermissionVO vo = new SubjectiveAnswerPermissionVO();
        BeanUtils.copyProperties(permission, vo);
        return vo;
    }

    /**
     * 获取学生的问题列表（包含学生已发布答案的问题和学生提出的问题）
     */
    public List<Map<String, Object>> getStudentQuestions(Long studentId) {
        List<Map<String, Object>> result = new java.util.ArrayList<>();
        
        // 1. 获取学生发布过答案的问题
        List<SubjectiveAnswerPermission> permissions = permissionMapper.selectList(
                new LambdaQueryWrapper<SubjectiveAnswerPermission>()
                        .eq(SubjectiveAnswerPermission::getStudentId, studentId)
                        .eq(SubjectiveAnswerPermission::getAnswerStatus, 1)
                        .orderByDesc(SubjectiveAnswerPermission::getAnsweredAt));

        for (SubjectiveAnswerPermission permission : permissions) {
            Map<String, Object> item = new HashMap<>();
            item.put("id", permission.getId());
            item.put("questionId", permission.getQuestionId());
            item.put("content", permission.getAnswerContent());
            item.put("title", "我的答案");
            item.put("time", permission.getAnsweredAt() != null ? 
                    permission.getAnsweredAt().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")) : "");
            item.put("canViewComments", permission.getCommentVisible() == 1);
            item.put("type", "answer");

            // 获取该问题的评论数量
            Long commentCount = commentMapper.selectCount(
                    new LambdaQueryWrapper<SubjectiveComment>()
                            .eq(SubjectiveComment::getQuestionId, permission.getQuestionId())
                            .eq(SubjectiveComment::getStatus, 1));
            item.put("commentCount", commentCount);
            result.add(item);
        }
        
        // 2. 获取学生在评论区提出的问题（非置顶的、由学生发起的评论）
        List<SubjectiveComment> studentComments = commentMapper.selectList(
                new LambdaQueryWrapper<SubjectiveComment>()
                        .eq(SubjectiveComment::getUserId, studentId)
                        .eq(SubjectiveComment::getStatus, 1)
                        .isNull(SubjectiveComment::getParentId) // 只获取顶级评论（问题）
                        .orderByDesc(SubjectiveComment::getCreatedAt));
        
        for (SubjectiveComment comment : studentComments) {
            Map<String, Object> item = new HashMap<>();
            item.put("id", comment.getId());
            item.put("questionId", comment.getQuestionId());
            item.put("content", comment.getContent());
            item.put("title", comment.getContent().length() > 30 ? 
                    comment.getContent().substring(0, 30) + "..." : comment.getContent());
            item.put("time", comment.getCreatedAt() != null ? 
                    comment.getCreatedAt().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")) : "");
            item.put("type", "question");
            
            // 获取回复数量（子评论）
            Long replyCount = commentMapper.selectCount(
                    new LambdaQueryWrapper<SubjectiveComment>()
                            .eq(SubjectiveComment::getParentId, comment.getId())
                            .eq(SubjectiveComment::getStatus, 1));
            item.put("commentCount", replyCount);
            
            // 检查是否有教师回复
            Long teacherReplyCount = commentMapper.selectCount(
                    new LambdaQueryWrapper<SubjectiveComment>()
                            .eq(SubjectiveComment::getParentId, comment.getId())
                            .eq(SubjectiveComment::getIsAnswer, 1)
                            .eq(SubjectiveComment::getStatus, 1));
            item.put("hasReply", teacherReplyCount > 0);
            
            result.add(item);
        }
        
        // 按时间排序
        result.sort((a, b) -> {
            String timeA = (String) a.get("time");
            String timeB = (String) b.get("time");
            if (timeA == null || timeA.isEmpty()) return 1;
            if (timeB == null || timeB.isEmpty()) return -1;
            return timeB.compareTo(timeA);
        });

        return result;
    }

    /**
     * 获取教师的问题列表（教师发布的问题和评论）
     */
    public List<Map<String, Object>> getTeacherQuestions(Long teacherId) {
        // 获取教师发布的置顶问题
        List<SubjectiveComment> questions = commentMapper.selectList(
                new LambdaQueryWrapper<SubjectiveComment>()
                        .eq(SubjectiveComment::getUserId, teacherId)
                        .eq(SubjectiveComment::getIsTop, 1)
                        .eq(SubjectiveComment::getStatus, 1)
                        .orderByDesc(SubjectiveComment::getCreatedAt));

        List<Map<String, Object>> result = new java.util.ArrayList<>();
        for (SubjectiveComment question : questions) {
            Map<String, Object> item = new HashMap<>();
            item.put("questionId", question.getQuestionId());
            item.put("content", question.getContent());
            item.put("createdAt", question.getCreatedAt());

            // 获取该问题的评论数量（不含置顶问题本身）
            Long replyCount = commentMapper.selectCount(
                    new LambdaQueryWrapper<SubjectiveComment>()
                            .eq(SubjectiveComment::getQuestionId, question.getQuestionId())
                            .eq(SubjectiveComment::getStatus, 1)
                            .eq(SubjectiveComment::getIsTop, 0));
            item.put("replyCount", replyCount);

            // 获取回答过的学生数量
            Long answerCount = permissionMapper.selectCount(
                    new LambdaQueryWrapper<SubjectiveAnswerPermission>()
                            .eq(SubjectiveAnswerPermission::getQuestionId, question.getQuestionId())
                            .eq(SubjectiveAnswerPermission::getAnswerStatus, 1));
            item.put("answerCount", answerCount);
            result.add(item);
        }

        return result;
    }
}
