package com.eduplatform.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.eduplatform.user.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface UserMapper extends BaseMapper<User> {
    
    @Update("UPDATE users SET status = #{status}, updated_at = NOW() WHERE id = #{id}")
    int updateStatusById(@Param("id") Long id, @Param("status") Integer status);
}
