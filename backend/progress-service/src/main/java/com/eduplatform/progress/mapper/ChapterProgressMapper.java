package com.eduplatform.progress.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.eduplatform.progress.entity.ChapterProgress;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ChapterProgressMapper extends BaseMapper<ChapterProgress> {
    
    @Delete("DELETE FROM chapter_progress WHERE student_id = #{studentId}")
    int deleteByStudentId(@Param("studentId") Long studentId);
    
    @Delete("DELETE FROM chapter_progress WHERE course_id = #{courseId}")
    int deleteByCourseId(@Param("courseId") Long courseId);
    
    @Delete("DELETE FROM chapter_progress WHERE chapter_id = #{chapterId}")
    int deleteByChapterId(@Param("chapterId") Long chapterId);
}
