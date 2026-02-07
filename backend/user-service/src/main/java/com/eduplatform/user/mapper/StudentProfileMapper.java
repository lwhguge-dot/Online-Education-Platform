package com.eduplatform.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.eduplatform.user.entity.StudentProfile;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface StudentProfileMapper extends BaseMapper<StudentProfile> {
    
    @Delete("DELETE FROM student_profiles WHERE user_id = #{userId}")
    int deleteByUserId(@Param("userId") Long userId);
}
