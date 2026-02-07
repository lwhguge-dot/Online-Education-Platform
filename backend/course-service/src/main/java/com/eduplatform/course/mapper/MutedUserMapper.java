package com.eduplatform.course.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.eduplatform.course.entity.MutedUser;
import org.apache.ibatis.annotations.*;

import java.util.List;
import java.util.Map;

/**
 * 禁言用户Mapper接口
 */
@Mapper
public interface MutedUserMapper extends BaseMapper<MutedUser> {
    
    /**
     * 检查用户是否被禁言
     */
    @Select("SELECT COUNT(*) FROM muted_users WHERE user_id = #{userId} AND course_id = #{courseId} AND status = 1")
    int checkMuted(@Param("userId") Long userId, @Param("courseId") Long courseId);
    
    /**
     * 获取用户禁言信息
     */
    @Select("SELECT m.*, u.real_name as muted_by_name FROM muted_users m " +
            "LEFT JOIN users u ON m.muted_by = u.id " +
            "WHERE m.user_id = #{userId} AND m.course_id = #{courseId} AND m.status = 1")
    Map<String, Object> getMuteInfo(@Param("userId") Long userId, @Param("courseId") Long courseId);
    
    /**
     * 禁言用户
     */
    @Insert("INSERT INTO muted_users (user_id, course_id, muted_by, reason, muted_at, status) " +
            "VALUES (#{userId}, #{courseId}, #{mutedBy}, #{reason}, NOW(), 1)")
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int muteUser(MutedUser mutedUser);
    
    /**
     * 解除禁言
     */
    @Update("UPDATE muted_users SET status = 0, unmuted_at = NOW() " +
            "WHERE user_id = #{userId} AND course_id = #{courseId} AND status = 1")
    int unmuteUser(@Param("userId") Long userId, @Param("courseId") Long courseId);
    
    /**
     * 获取课程禁言记录列表
     */
    @Select("SELECT m.*, u1.real_name as user_name, u2.real_name as muted_by_name " +
            "FROM muted_users m " +
            "LEFT JOIN users u1 ON m.user_id = u1.id " +
            "LEFT JOIN users u2 ON m.muted_by = u2.id " +
            "WHERE m.course_id = #{courseId} " +
            "ORDER BY m.muted_at DESC")
    List<Map<String, Object>> getMuteRecords(@Param("courseId") Long courseId);
    
    /**
     * 删除用户的所有禁言记录
     */
    @Delete("DELETE FROM muted_users WHERE user_id = #{userId}")
    int deleteByUserId(@Param("userId") Long userId);
    
    /**
     * 删除课程的所有禁言记录
     */
    @Delete("DELETE FROM muted_users WHERE course_id = #{courseId}")
    int deleteByCourseId(@Param("courseId") Long courseId);
}
