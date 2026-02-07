package com.eduplatform.course.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.eduplatform.course.entity.CommentLike;
import org.apache.ibatis.annotations.*;

import java.util.List;

/**
 * 评论点赞Mapper接口
 */
@Mapper
public interface CommentLikeMapper extends BaseMapper<CommentLike> {
    
    /**
     * 检查用户是否已点赞
     */
    @Select("SELECT COUNT(*) FROM comment_likes WHERE comment_id = #{commentId} AND user_id = #{userId}")
    int checkLiked(@Param("commentId") Long commentId, @Param("userId") Long userId);
    
    /**
     * 删除点赞记录
     */
    @Delete("DELETE FROM comment_likes WHERE comment_id = #{commentId} AND user_id = #{userId}")
    int deleteLike(@Param("commentId") Long commentId, @Param("userId") Long userId);
    
    /**
     * 获取用户在指定评论列表中已点赞的评论ID
     */
    @Select("""
        <script>
        SELECT comment_id FROM comment_likes 
        WHERE user_id = #{userId} 
        AND comment_id IN 
        <foreach collection='commentIds' item='id' open='(' separator=',' close=')'>
            #{id}
        </foreach>
        </script>
    """)
    List<Long> findLikedCommentIds(@Param("userId") Long userId, @Param("commentIds") List<Long> commentIds);
    
    /**
     * 删除用户的所有点赞记录
     */
    @Delete("DELETE FROM comment_likes WHERE user_id = #{userId}")
    int deleteByUserId(@Param("userId") Long userId);
    
    /**
     * 删除评论的所有点赞记录
     */
    @Delete("DELETE FROM comment_likes WHERE comment_id = #{commentId}")
    int deleteByCommentId(@Param("commentId") Long commentId);
}
