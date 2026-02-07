package com.eduplatform.course.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.eduplatform.course.entity.Enrollment;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface EnrollmentMapper extends BaseMapper<Enrollment> {
    
    @Delete("DELETE FROM enrollments WHERE student_id = #{studentId}")
    int deleteByStudentId(@Param("studentId") Long studentId);
    
    @Delete("DELETE FROM enrollments WHERE course_id = #{courseId}")
    int deleteByCourseId(@Param("courseId") Long courseId);
    
    @Select("SELECT course_id FROM enrollments WHERE student_id = #{studentId}")
    List<Long> findCourseIdsByStudentId(@Param("studentId") Long studentId);
}
