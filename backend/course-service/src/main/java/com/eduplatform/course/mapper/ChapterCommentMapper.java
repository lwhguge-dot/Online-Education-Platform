package com.eduplatform.course.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.eduplatform.course.entity.ChapterComment;
import org.apache.ibatis.annotations.*;

import java.util.List;
import java.util.Map;

/**
 * 章节评论Mapper接口
 */
@Mapper
public interface ChapterCommentMapper extends BaseMapper<ChapterComment> {
    
    /**
     * 获取章节评论列表（按时间排序）
     */
    @Select("""
        SELECT c.*, u.username as user_name, u.avatar as user_avatar
        FROM chapter_comments c
        LEFT JOIN users u ON c.user_id = u.id
        WHERE c.chapter_id = #{chapterId}
        AND c.parent_id IS NULL
        AND c.status = 1
        ORDER BY c.is_pinned DESC, c.created_at DESC
        LIMIT #{offset}, #{limit}
    """)
    List<Map<String, Object>> findByChapterOrderByTime(
        @Param("chapterId") Long chapterId,
        @Param("offset") int offset,
        @Param("limit") int limit
    );
    
    /**
     * 获取章节评论列表（按热度排序）
     */
    @Select("""
        SELECT c.*, u.username as user_name, u.avatar as user_avatar
        FROM chapter_comments c
        LEFT JOIN users u ON c.user_id = u.id
        WHERE c.chapter_id = #{chapterId}
        AND c.parent_id IS NULL
        AND c.status = 1
        ORDER BY c.is_pinned DESC, c.like_count DESC, c.reply_count DESC, c.created_at DESC
        LIMIT #{offset}, #{limit}
    """)
    List<Map<String, Object>> findByChapterOrderByHot(
        @Param("chapterId") Long chapterId,
        @Param("offset") int offset,
        @Param("limit") int limit
    );
    
    /**
     * 获取评论的回复列表
     */
    @Select("""
        SELECT c.*, u.username as user_name, u.avatar as user_avatar
        FROM chapter_comments c
        LEFT JOIN users u ON c.user_id = u.id
        WHERE c.parent_id = #{parentId}
        AND c.status = 1
        ORDER BY c.created_at ASC
    """)
    List<Map<String, Object>> findReplies(@Param("parentId") Long parentId);
    
    /**
     * 获取章节评论总数
     */
    @Select("""
        SELECT COUNT(*) FROM chapter_comments
        WHERE chapter_id = #{chapterId}
        AND parent_id IS NULL
        AND status = 1
    """)
    int countByChapter(@Param("chapterId") Long chapterId);
    
    /**
     * 更新点赞数
     */
    @Update("UPDATE chapter_comments SET like_count = like_count + #{delta} WHERE id = #{id}")
    int updateLikeCount(@Param("id") Long id, @Param("delta") int delta);
    
    /**
     * 更新回复数
     */
    @Update("UPDATE chapter_comments SET reply_count = reply_count + #{delta} WHERE id = #{id}")
    int updateReplyCount(@Param("id") Long id, @Param("delta") int delta);
    
    /**
     * 切换置顶状态
     */
    @Update("UPDATE chapter_comments SET is_pinned = CASE WHEN is_pinned = 1 THEN 0 ELSE 1 END WHERE id = #{id}")
    int togglePin(@Param("id") Long id);
    
    /**
     * 软删除评论
     */
    @Update("UPDATE chapter_comments SET status = 0 WHERE id = #{id}")
    int softDelete(@Param("id") Long id);
    
    /**
     * 软删除评论的所有回复
     */
    @Update("UPDATE chapter_comments SET status = 0 WHERE parent_id = #{parentId}")
    int softDeleteReplies(@Param("parentId") Long parentId);
    
    /**
     * 删除用户的所有评论
     */
    @Delete("DELETE FROM chapter_comments WHERE user_id = #{userId}")
    int deleteByUserId(@Param("userId") Long userId);
    
    /**
     * 删除课程的所有评论
     */
    @Delete("DELETE FROM chapter_comments WHERE course_id = #{courseId}")
    int deleteByCourseId(@Param("courseId") Long courseId);
    
    /**
     * 删除章节的所有评论
     */
    @Delete("DELETE FROM chapter_comments WHERE chapter_id = #{chapterId}")
    int deleteByChapterId(@Param("chapterId") Long chapterId);
    
    /**
     * 获取课程的所有评论ID
     */
    @Select("SELECT id FROM chapter_comments WHERE course_id = #{courseId}")
    List<Long> findIdsByCourseId(@Param("courseId") Long courseId);
    
    /**
     * 获取用户的所有评论ID
     */
    @Select("SELECT id FROM chapter_comments WHERE user_id = #{userId}")
    List<Long> findIdsByUserId(@Param("userId") Long userId);

    /**
     * 获取学生发表的顶级提问（用于学生中心“我的提问”）。
     * 说明：仅查询 parent_id 为空的主评论，并补齐课程/章节标题。
     */
    @Select("""
        SELECT
          c.id,
          c.course_id,
          c.chapter_id,
          c.content,
          c.reply_count,
          c.created_at,
          co.title AS course_title,
          ch.title AS chapter_title
        FROM chapter_comments c
        LEFT JOIN courses co ON co.id = c.course_id
        LEFT JOIN chapters ch ON ch.id = c.chapter_id
        WHERE c.user_id = #{userId}
          AND c.status = 1
          AND c.parent_id IS NULL
        ORDER BY c.created_at DESC
        LIMIT #{limit}
    """)
    List<Map<String, Object>> findStudentTopLevelQuestions(@Param("userId") Long userId, @Param("limit") int limit);
}
