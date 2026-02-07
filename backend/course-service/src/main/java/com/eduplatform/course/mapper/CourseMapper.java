package com.eduplatform.course.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.eduplatform.course.entity.Course;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface CourseMapper extends BaseMapper<Course> {
    
    @Update("UPDATE courses SET status = #{newStatus}, updated_at = NOW() WHERE id = #{courseId}")
    int updateStatusById(@Param("courseId") Long courseId, @Param("newStatus") Integer newStatus);
    
    /**
     * 删除教师的所有课程
     */
    @Delete("DELETE FROM courses WHERE teacher_id = #{teacherId}")
    int deleteByTeacherId(@Param("teacherId") Long teacherId);
    
    /**
     * 获取教师的所有课程ID
     */
    @Select("SELECT id FROM courses WHERE teacher_id = #{teacherId}")
    List<Long> findIdsByTeacherId(@Param("teacherId") Long teacherId);
}
