package com.eduplatform.course.service;

import com.eduplatform.course.dto.CommentDTO;
import com.eduplatform.course.entity.ChapterComment;
import com.eduplatform.course.entity.CommentLike;
import com.eduplatform.course.mapper.ChapterCommentMapper;
import com.eduplatform.course.mapper.CommentLikeMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 章节社交化评论服务
 * 负责构建课程的互动生态，涵盖双层嵌套评论系统、多维排序算法、以及基于权限的评论生命周期管理。
 *
 * 核心机制：
 * 1. 双层树形结构：支持主评论与二级回复的 1:N 嵌套，提供清晰的对话脉络。
 * 2. 动态排序：集成“热度排序”（基于点赞权重）与“时间排序”，满足不同场景下的消费需求。
 * 3. 社交化交互：管理点赞足迹与评论置顶权限，增强社区活跃度。
 * 4. 软删除策略：执行合规性删除，确保主评论消失时，其下属回复序列同步处于不可见状态。
 *
 * @author Antigravity
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChapterCommentService {

    private final ChapterCommentMapper commentMapper;
    private final CommentLikeMapper likeMapper;

    /**
     * 分页检索章节下的主评论列表
     * 逻辑包含：
     * 1. 执行排序算法路由（热度 vs 时间）。
     * 2. 注入当前用户的社交足记（是否已点赞）。
     * 3. 实现预加载逻辑：为每条主评论关联展示前 3 条精选回复。
     *
     * @param chapterId 目标章节 ID
     * @param userId    当前访问用户 ID（用于计算点赞态）
     * @param sort      排序模式 (hot/time)
     * @param page      页码
     * @param size      页数
     * @return 包含聚合评论列表及分页元数据的 Map 结构
     */
    public Map<String, Object> getComments(Long chapterId, Long userId, String sort, int page, int size) {
        int offset = (page - 1) * size;
        List<Map<String, Object>> rawComments;

        // 执行排序路由
        if ("hot".equals(sort)) {
            rawComments = commentMapper.findByChapterOrderByHot(chapterId, offset, size);
        } else {
            rawComments = commentMapper.findByChapterOrderByTime(chapterId, offset, size);
        }

        List<CommentDTO> comments = rawComments.stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());

        // 社交态注水：标记用户点赞过的项
        if (userId != null && !comments.isEmpty()) {
            List<Long> commentIds = comments.stream().map(CommentDTO::getId).collect(Collectors.toList());
            List<Long> likedIds = likeMapper.findLikedCommentIds(userId, commentIds);
            Set<Long> likedSet = new HashSet<>(likedIds);
            comments.forEach(c -> c.setIsLiked(likedSet.contains(c.getId())));
        }

        // 二级评论预加载：为每条主评论抓取浅层回复列表 (Top 3)
        for (CommentDTO comment : comments) {
            List<Map<String, Object>> rawReplies = commentMapper.findReplies(comment.getId());
            List<CommentDTO> replies = rawReplies.stream()
                    .limit(3)
                    .map(this::mapToDTO)
                    .collect(Collectors.toList());
            comment.setReplies(replies);
        }

        int total = commentMapper.countByChapter(chapterId);

        Map<String, Object> result = new HashMap<>();
        result.put("comments", comments);
        result.put("total", total);
        result.put("page", page);
        result.put("size", size);
        result.put("totalPages", (total + size - 1) / size);

        return result;
    }

    /**
     * 发布互动内容 (评论/回复)
     * 操作流程：插入新评论记录 -> 若为回复则触发父评论计数器自增。
     * 
     * @param chapterId 章节上下文
     * @param courseId  课程上下文
     * @param userId    发布者 ID
     * @param content   文本内容 (假设已通过敏感词过滤)
     * @param parentId  父评论 ID (若为一级评论则为空)
     * @return 构造出的 CommentDTO。注意：此流程内部不包含异步审核触发。
     */
    @Transactional
    public CommentDTO createComment(Long chapterId, Long courseId, Long userId, String content, Long parentId) {
        ChapterComment comment = new ChapterComment();
        comment.setChapterId(chapterId);
        comment.setCourseId(courseId);
        comment.setUserId(userId);
        comment.setContent(content);
        comment.setParentId(parentId);
        comment.setLikeCount(0);
        comment.setReplyCount(0);
        comment.setIsPinned(0);
        comment.setStatus(1);
        comment.setCreatedAt(LocalDateTime.now());

        commentMapper.insert(comment);

        // 如果是二级互动，维护父节点的影子计数
        if (parentId != null) {
            commentMapper.updateReplyCount(parentId, 1);
        }

        CommentDTO dto = new CommentDTO();
        dto.setId(comment.getId());
        dto.setChapterId(chapterId);
        dto.setCourseId(courseId);
        dto.setUserId(userId);
        dto.setContent(content);
        dto.setParentId(parentId);
        dto.setLikeCount(0);
        dto.setReplyCount(0);
        dto.setIsPinned(false);
        dto.setIsLiked(false);
        dto.setCreatedAt(comment.getCreatedAt());

        return dto;
    }

    /**
     * 移除评论及其相关依赖
     * 规则说明：
     * 1. 权限隔离：仅限作者本人或管理员执行。
     * 2. 级联逻辑：主评论删除导致其下属回复序列同步软删除。
     * 
     * @param commentId 目标 ID
     * @param userId    操作人 ID
     * @param isAdmin   管理特权标识
     */
    @Transactional
    public void deleteComment(Long commentId, Long userId, boolean isAdmin) {
        ChapterComment comment = commentMapper.selectById(commentId);
        if (comment == null || comment.getStatus() == 0) {
            throw new RuntimeException("操作失败：评论已过期或不存在");
        }

        // 安全审计：权限校验
        if (!isAdmin && !comment.getUserId().equals(userId)) {
            throw new RuntimeException("违规操作：您无权删除他人发表的评论");
        }

        // 执行合规性软删除
        commentMapper.softDelete(commentId);

        // 处理从属关系
        if (comment.getParentId() == null) {
            // 一级评论消失导致回复群集集体隐藏
            commentMapper.softDeleteReplies(commentId);
        } else {
            // 二级评论删除需扣减父节点计数
            commentMapper.updateReplyCount(comment.getParentId(), -1);
        }
    }

    /**
     * 切换评论置顶状态 (CMS 管理权限)
     */
    @Transactional
    public void togglePin(Long commentId) {
        commentMapper.togglePin(commentId);
    }

    /**
     * 社交点赞原子操作
     * 实现：检查是否存在记录 -> 存在则取消(Delete + Decrement) -> 不存在则添加(Insert + Increment)。
     * 
     * @return 返回最终状态：true 表示当前处于点赞态
     */
    @Transactional
    public boolean toggleLike(Long commentId, Long userId) {
        int liked = likeMapper.checkLiked(commentId, userId);

        if (liked > 0) {
            // 撤销动作
            likeMapper.deleteLike(commentId, userId);
            commentMapper.updateLikeCount(commentId, -1);
            return false;
        } else {
            // 注入动作
            CommentLike like = new CommentLike();
            like.setCommentId(commentId);
            like.setUserId(userId);
            like.setCreatedAt(LocalDateTime.now());
            likeMapper.insert(like);
            commentMapper.updateLikeCount(commentId, 1);
            return true;
        }
    }

    /**
     * 全量展开指定评论的下属回复流
     * 常用于前端“查看更多回复”的点击行为。
     */
    public List<CommentDTO> getReplies(Long parentId, Long userId) {
        List<Map<String, Object>> rawReplies = commentMapper.findReplies(parentId);
        List<CommentDTO> replies = rawReplies.stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());

        // 补全社交行为标识
        if (userId != null && !replies.isEmpty()) {
            List<Long> commentIds = replies.stream().map(CommentDTO::getId).collect(Collectors.toList());
            List<Long> likedIds = likeMapper.findLikedCommentIds(userId, commentIds);
            Set<Long> likedSet = new HashSet<>(likedIds);
            replies.forEach(c -> c.setIsLiked(likedSet.contains(c.getId())));
        }

        return replies;
    }

    /**
     * SQL 结果聚合 Map 映射 DTO 的归约逻辑
     */
    private CommentDTO mapToDTO(Map<String, Object> data) {
        CommentDTO dto = new CommentDTO();
        dto.setId(getLongValue(data, "id"));
        dto.setChapterId(getLongValue(data, "chapter_id"));
        dto.setCourseId(getLongValue(data, "course_id"));
        dto.setUserId(getLongValue(data, "user_id"));
        dto.setUserName((String) data.get("user_name"));
        dto.setUserAvatar((String) data.get("user_avatar"));
        dto.setParentId(getLongValue(data, "parent_id"));
        dto.setContent((String) data.get("content"));
        dto.setLikeCount(getIntValue(data, "like_count"));
        dto.setReplyCount(getIntValue(data, "reply_count"));
        dto.setIsPinned(getIntValue(data, "is_pinned") == 1);
        dto.setCreatedAt((LocalDateTime) data.get("created_at"));
        dto.setIsLiked(false);
        return dto;
    }

    /**
     * 类型安全转换工具：处理 MyBatis 转 Map 时不同驱动对数值类型的投影差异
     */
    private Long getLongValue(Map<String, Object> data, String key) {
        Object value = data.get(key);
        if (value == null)
            return null;
        if (value instanceof Long)
            return (Long) value;
        if (value instanceof Integer)
            return ((Integer) value).longValue();
        return null;
    }

    /**
     * 整型宽容校验工具
     */
    private Integer getIntValue(Map<String, Object> data, String key) {
        Object value = data.get(key);
        if (value == null)
            return 0;
        if (value instanceof Integer)
            return (Integer) value;
        if (value instanceof Long)
            return ((Long) value).intValue();
        return 0;
    }
}
