package com.eduplatform.course.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.eduplatform.course.entity.Chapter;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface ChapterMapper extends BaseMapper<Chapter> {
    
    @Delete("DELETE FROM chapters WHERE course_id = #{courseId}")
    int deleteByCourseId(@Param("courseId") Long courseId);
    
    @Select("SELECT id FROM chapters WHERE course_id = #{courseId}")
    List<Long> findIdsByCourseId(@Param("courseId") Long courseId);
    
    @Select("SELECT COUNT(*) FROM chapters WHERE course_id = #{courseId}")
    int countByCourseId(@Param("courseId") Long courseId);
}
