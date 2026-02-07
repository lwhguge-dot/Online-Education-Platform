package com.eduplatform.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.eduplatform.user.entity.Announcement;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 系统公告Mapper
 */
@Mapper
public interface AnnouncementMapper extends BaseMapper<Announcement> {
    
    /**
     * 查询已发布且未过期的公告（按受众过滤）
     */
    @Select("<script>" +
            "SELECT * FROM announcements " +
            "WHERE status = 'PUBLISHED' " +
            "AND (publish_time IS NULL OR publish_time &lt;= NOW()) " +
            "AND (expire_time IS NULL OR expire_time &gt; NOW()) " +
            "<if test='audience != null and audience != \"ALL\"'>" +
            "AND (target_audience = 'ALL' OR target_audience = #{audience})" +
            "</if>" +
            " ORDER BY is_pinned DESC, publish_time DESC" +
            "</script>")
    List<Announcement> findActiveByAudience(@Param("audience") String audience);
    
    /**
     * 更新过期公告状态
     */
    @Update("UPDATE announcements SET status = 'EXPIRED', updated_at = NOW() " +
            "WHERE status = 'PUBLISHED' AND expire_time IS NOT NULL AND expire_time < NOW()")
    int updateExpiredAnnouncements();
    
    /**
     * 发布定时公告（将到达发布时间的SCHEDULED公告改为PUBLISHED）
     */
    @Update("UPDATE announcements SET status = 'PUBLISHED', updated_at = NOW() " +
            "WHERE status = 'SCHEDULED' AND publish_time IS NOT NULL AND publish_time <= NOW()")
    int publishScheduledAnnouncements();
    
    /**
     * 查询教师发布的公告
     */
    @Select("<script>" +
            "SELECT a.*, u.username as creator_name " +
            "FROM announcements a " +
            "LEFT JOIN users u ON a.created_by = u.id " +
            "WHERE a.created_by = #{teacherId} " +
            "<if test='courseId != null'>" +
            "AND a.course_id = #{courseId} " +
            "</if>" +
            "<if test='status != null and status != \"\"'>" +
            "AND a.status = #{status} " +
            "</if>" +
            "ORDER BY a.created_at DESC " +
            "LIMIT #{offset}, #{size}" +
            "</script>")
    List<Announcement> findByTeacher(@Param("teacherId") Long teacherId, 
                                      @Param("courseId") Long courseId,
                                      @Param("status") String status,
                                      @Param("offset") int offset, 
                                      @Param("size") int size);
    
    /**
     * 统计教师发布的公告数量
     */
    @Select("<script>" +
            "SELECT COUNT(*) FROM announcements " +
            "WHERE created_by = #{teacherId} " +
            "<if test='courseId != null'>" +
            "AND course_id = #{courseId} " +
            "</if>" +
            "<if test='status != null and status != \"\"'>" +
            "AND status = #{status} " +
            "</if>" +
            "</script>")
    long countByTeacher(@Param("teacherId") Long teacherId, 
                        @Param("courseId") Long courseId,
                        @Param("status") String status);
    
    /**
     * 查询课程相关的公告（学生视角）
     */
    @Select("<script>" +
            "SELECT a.* FROM announcements a " +
            "WHERE a.status = 'PUBLISHED' " +
            "AND (a.publish_time IS NULL OR a.publish_time &lt;= NOW()) " +
            "AND (a.expire_time IS NULL OR a.expire_time &gt; NOW()) " +
            "AND (a.course_id IS NULL OR a.course_id IN " +
            "  (SELECT e.course_id FROM enrollments e WHERE e.student_id = #{studentId})) " +
            "AND (a.target_audience = 'ALL' OR a.target_audience = 'STUDENT') " +
            "ORDER BY a.is_pinned DESC, a.publish_time DESC " +
            "LIMIT #{limit}" +
            "</script>")
    List<Announcement> findForStudent(@Param("studentId") Long studentId, @Param("limit") int limit);
    
    /**
     * 增加阅读次数
     */
    @Update("UPDATE announcements SET read_count = read_count + 1 WHERE id = #{id}")
    int incrementReadCount(@Param("id") Long id);
    
    /**
     * 删除用户创建的所有公告
     */
    @Delete("DELETE FROM announcements WHERE created_by = #{userId}")
    int deleteByCreatedBy(@Param("userId") Long userId);
    
    /**
     * 删除课程相关的所有公告
     */
    @Delete("DELETE FROM announcements WHERE course_id = #{courseId}")
    int deleteByCourseId(@Param("courseId") Long courseId);
    
    /**
     * 查询用户创建的公告ID列表
     */
    @Select("SELECT id FROM announcements WHERE created_by = #{userId}")
    List<Long> findIdsByCreatedBy(@Param("userId") Long userId);
    
    /**
     * 查询课程相关的公告ID列表
     */
    @Select("SELECT id FROM announcements WHERE course_id = #{courseId}")
    List<Long> findIdsByCourseId(@Param("courseId") Long courseId);
}
