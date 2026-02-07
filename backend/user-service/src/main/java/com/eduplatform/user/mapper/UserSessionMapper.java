package com.eduplatform.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.eduplatform.user.entity.UserSession;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface UserSessionMapper extends BaseMapper<UserSession> {
    
    @Update("UPDATE user_session SET status = 'OFFLINE', logout_time = NOW(), updated_at = NOW() " +
            "WHERE user_id = #{userId} AND status = 'ONLINE'")
    int offlineAllByUserId(@Param("userId") Long userId);
    
    @Update("UPDATE user_session SET last_active_time = NOW(), updated_at = NOW() WHERE jti = #{jti}")
    int updateLastActiveTime(@Param("jti") String jti);
    
    @Update("UPDATE user_session SET status = 'OFFLINE', logout_time = NOW(), updated_at = NOW() WHERE jti = #{jti}")
    int offlineByJti(@Param("jti") String jti);
    
    @Delete("DELETE FROM user_session WHERE user_id = #{userId}")
    int deleteByUserId(@Param("userId") Long userId);
}
