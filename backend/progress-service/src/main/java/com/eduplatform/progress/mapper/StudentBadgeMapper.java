package com.eduplatform.progress.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.eduplatform.progress.entity.StudentBadge;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface StudentBadgeMapper extends BaseMapper<StudentBadge> {
    
    @Delete("DELETE FROM student_badges WHERE student_id = #{studentId}")
    int deleteByStudentId(@Param("studentId") Long studentId);
}
