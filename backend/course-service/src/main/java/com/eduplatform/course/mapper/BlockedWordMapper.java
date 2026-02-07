package com.eduplatform.course.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.eduplatform.course.entity.BlockedWord;
import org.apache.ibatis.annotations.*;

import java.util.List;

/**
 * 屏蔽词Mapper接口
 */
@Mapper
public interface BlockedWordMapper extends BaseMapper<BlockedWord> {
    
    /**
     * 获取全局屏蔽词列表
     */
    @Select("SELECT * FROM blocked_words WHERE scope = 'global' ORDER BY created_at DESC")
    List<BlockedWord> findGlobalWords();
    
    /**
     * 获取课程屏蔽词列表
     */
    @Select("SELECT * FROM blocked_words WHERE scope = 'course' AND course_id = #{courseId} ORDER BY created_at DESC")
    List<BlockedWord> findCourseWords(@Param("courseId") Long courseId);
    
    /**
     * 获取适用于某课程的所有屏蔽词（全局+课程）
     */
    @Select("SELECT * FROM blocked_words WHERE scope = 'global' OR (scope = 'course' AND course_id = #{courseId})")
    List<BlockedWord> findApplicableWords(@Param("courseId") Long courseId);
    
    /**
     * 添加屏蔽词
     */
    @Insert("INSERT INTO blocked_words (word, scope, course_id, created_by, created_at) " +
            "VALUES (#{word}, #{scope}, #{courseId}, #{createdBy}, NOW())")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertWord(BlockedWord blockedWord);
    
    /**
     * 删除屏蔽词
     */
    @Delete("DELETE FROM blocked_words WHERE id = #{id}")
    int deleteWord(@Param("id") Long id);
    
    /**
     * 检查屏蔽词是否存在
     */
    @Select("SELECT COUNT(*) FROM blocked_words WHERE word = #{word} AND scope = #{scope} " +
            "AND (course_id = #{courseId} OR (#{courseId} IS NULL AND course_id IS NULL))")
    int checkExists(@Param("word") String word, @Param("scope") String scope, @Param("courseId") Long courseId);
    
    /**
     * 删除课程的所有屏蔽词
     */
    @Delete("DELETE FROM blocked_words WHERE course_id = #{courseId}")
    int deleteByCourseId(@Param("courseId") Long courseId);
}
