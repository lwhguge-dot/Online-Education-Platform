package com.eduplatform.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.eduplatform.user.entity.AnnouncementRead;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * 公告阅读记录Mapper
 */
@Mapper
public interface AnnouncementReadMapper extends BaseMapper<AnnouncementRead> {
    
    /**
     * 获取公告的阅读统计
     */
    @Select("<script>" +
            "SELECT ar.announcement_id, COUNT(*) as read_count " +
            "FROM announcement_reads ar " +
            "WHERE ar.announcement_id IN " +
            "<foreach item='id' collection='announcementIds' open='(' separator=',' close=')'>" +
            "#{id}" +
            "</foreach> " +
            "GROUP BY ar.announcement_id" +
            "</script>")
    List<Map<String, Object>> getReadStatsByAnnouncementIds(@Param("announcementIds") List<Long> announcementIds);
    
    /**
     * 检查用户是否已阅读公告
     */
    @Select("SELECT COUNT(*) FROM announcement_reads WHERE announcement_id = #{announcementId} AND user_id = #{userId}")
    int checkUserRead(@Param("announcementId") Long announcementId, @Param("userId") Long userId);
    
    /**
     * 删除用户的所有阅读记录
     */
    @Delete("DELETE FROM announcement_reads WHERE user_id = #{userId}")
    int deleteByUserId(@Param("userId") Long userId);
    
    /**
     * 删除公告的所有阅读记录
     */
    @Delete("DELETE FROM announcement_reads WHERE announcement_id = #{announcementId}")
    int deleteByAnnouncementId(@Param("announcementId") Long announcementId);
}
