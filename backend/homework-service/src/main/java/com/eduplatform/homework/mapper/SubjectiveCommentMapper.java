package com.eduplatform.homework.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.eduplatform.homework.entity.SubjectiveComment;
import org.apache.ibatis.annotations.*;

import java.util.List;
import java.util.Map;

@Mapper
public interface SubjectiveCommentMapper extends BaseMapper<SubjectiveComment> {
    
    /**
     * 获取教师课程的所有讨论（按课程/章节分组）
     */
    @Select("""
        SELECT sc.*, u.username as user_name, u.avatar as user_avatar,
               c.title as course_title, ch.title as chapter_title,
               (SELECT COUNT(*) FROM subjective_comments r WHERE r.parent_id = sc.id AND r.status = 1) as reply_count
        FROM subjective_comments sc
        LEFT JOIN users u ON sc.user_id = u.id
        LEFT JOIN courses c ON sc.course_id = c.id
        LEFT JOIN chapters ch ON sc.chapter_id = ch.id
        WHERE sc.course_id IN (SELECT id FROM courses WHERE teacher_id = #{teacherId})
        AND sc.parent_id IS NULL
        AND sc.status = 1
        ORDER BY sc.is_top DESC, sc.created_at DESC
    """)
    @Results({
        @Result(property = "id", column = "id"),
        @Result(property = "questionId", column = "question_id"),
        @Result(property = "userId", column = "user_id"),
        @Result(property = "userName", column = "user_name"),
        @Result(property = "userAvatar", column = "user_avatar"),
        @Result(property = "parentId", column = "parent_id"),
        @Result(property = "content", column = "content"),
        @Result(property = "isAnswer", column = "is_answer"),
        @Result(property = "isTop", column = "is_top"),
        @Result(property = "likeCount", column = "like_count"),
        @Result(property = "answerStatus", column = "answer_status"),
        @Result(property = "answeredAt", column = "answered_at"),
        @Result(property = "answeredBy", column = "answered_by"),
        @Result(property = "courseId", column = "course_id"),
        @Result(property = "courseTitle", column = "course_title"),
        @Result(property = "chapterId", column = "chapter_id"),
        @Result(property = "chapterTitle", column = "chapter_title"),
        @Result(property = "createdAt", column = "created_at"),
        @Result(property = "replyCount", column = "reply_count")
    })
    List<Map<String, Object>> findByTeacher(@Param("teacherId") Long teacherId);
    
    /**
     * 按课程获取讨论
     */
    @Select("""
        SELECT sc.*, u.username as user_name, u.avatar as user_avatar,
               ch.title as chapter_title,
               (SELECT COUNT(*) FROM subjective_comments r WHERE r.parent_id = sc.id AND r.status = 1) as reply_count
        FROM subjective_comments sc
        LEFT JOIN users u ON sc.user_id = u.id
        LEFT JOIN chapters ch ON sc.chapter_id = ch.id
        WHERE sc.course_id = #{courseId}
        AND sc.parent_id IS NULL
        AND sc.status = 1
        ORDER BY sc.is_top DESC, sc.created_at DESC
    """)
    List<Map<String, Object>> findByCourse(@Param("courseId") Long courseId);
    
    /**
     * 获取超过48小时未回复的讨论数量
     */
    @Select("""
        SELECT COUNT(*) FROM subjective_comments sc
        WHERE sc.course_id IN (SELECT id FROM courses WHERE teacher_id = #{teacherId})
        AND sc.parent_id IS NULL
        AND sc.status = 1
        AND sc.answer_status = 'pending'
        AND sc.created_at < DATE_SUB(NOW(), INTERVAL 48 HOUR)
    """)
    Integer countOverdue(@Param("teacherId") Long teacherId);
    
    /**
     * 获取讨论统计
     */
    @Select("""
        SELECT 
            COUNT(*) as totalQuestions,
            SUM(CASE WHEN answer_status = 'pending' THEN 1 ELSE 0 END) as pendingCount,
            SUM(CASE WHEN answer_status = 'answered' THEN 1 ELSE 0 END) as answeredCount,
            SUM(CASE WHEN answer_status = 'follow_up' THEN 1 ELSE 0 END) as followUpCount,
            SUM(CASE WHEN answer_status = 'pending' AND created_at < DATE_SUB(NOW(), INTERVAL 48 HOUR) THEN 1 ELSE 0 END) as overdueCount
        FROM subjective_comments sc
        WHERE sc.course_id IN (SELECT id FROM courses WHERE teacher_id = #{teacherId})
        AND sc.parent_id IS NULL
        AND sc.status = 1
    """)
    Map<String, Object> getStats(@Param("teacherId") Long teacherId);
    
    /**
     * 更新回答状态
     */
    @Update("""
        UPDATE subjective_comments 
        SET answer_status = #{status}, 
            answered_at = CASE WHEN #{status} = 'answered' THEN NOW() ELSE answered_at END,
            answered_by = CASE WHEN #{status} = 'answered' THEN #{answeredBy} ELSE answered_by END
        WHERE id = #{id}
    """)
    int updateAnswerStatus(@Param("id") Long id, @Param("status") String status, @Param("answeredBy") Long answeredBy);
    
    /**
     * 切换置顶状态
     */
    @Update("UPDATE subjective_comments SET is_top = CASE WHEN is_top = 1 THEN 0 ELSE 1 END WHERE id = #{id}")
    int toggleTop(@Param("id") Long id);
    
    /**
     * 获取回复列表
     */
    @Select("""
        SELECT sc.*, u.username as user_name, u.avatar as user_avatar
        FROM subjective_comments sc
        LEFT JOIN users u ON sc.user_id = u.id
        WHERE sc.parent_id = #{parentId}
        AND sc.status = 1
        ORDER BY sc.created_at ASC
    """)
    List<Map<String, Object>> findReplies(@Param("parentId") Long parentId);
    
    /**
     * 删除用户的所有评论
     */
    @Delete("DELETE FROM subjective_comments WHERE user_id = #{userId}")
    int deleteByUserId(@Param("userId") Long userId);
    
    /**
     * 删除题目的所有评论
     */
    @Delete("DELETE FROM subjective_comments WHERE question_id = #{questionId}")
    int deleteByQuestionId(@Param("questionId") Long questionId);
    
    /**
     * 删除课程的所有评论
     */
    @Delete("DELETE FROM subjective_comments WHERE course_id = #{courseId}")
    int deleteByCourseId(@Param("courseId") Long courseId);
}
