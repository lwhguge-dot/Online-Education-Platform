package com.eduplatform.homework.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.eduplatform.homework.entity.Homework;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import java.util.List;

@Mapper
public interface HomeworkMapper extends BaseMapper<Homework> {
    
    @Select("SELECT * FROM homeworks WHERE chapter_id = #{chapterId}")
    List<Homework> findByChapterId(@Param("chapterId") Long chapterId);
    
    @Delete("DELETE FROM homeworks WHERE course_id = #{courseId}")
    int deleteByCourseId(@Param("courseId") Long courseId);
    
    @Delete("DELETE FROM homeworks WHERE chapter_id = #{chapterId}")
    int deleteByChapterId(@Param("chapterId") Long chapterId);
    
    @Select("SELECT id FROM homeworks WHERE course_id = #{courseId}")
    List<Long> findIdsByCourseId(@Param("courseId") Long courseId);
    
    @Select("SELECT id FROM homeworks WHERE chapter_id = #{chapterId}")
    List<Long> findIdsByChapterId(@Param("chapterId") Long chapterId);
}
