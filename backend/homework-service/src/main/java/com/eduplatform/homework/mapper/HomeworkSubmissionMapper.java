package com.eduplatform.homework.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.eduplatform.homework.entity.HomeworkSubmission;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface HomeworkSubmissionMapper extends BaseMapper<HomeworkSubmission> {
    
    @Delete("DELETE FROM homework_submissions WHERE student_id = #{studentId}")
    int deleteByStudentId(@Param("studentId") Long studentId);
    
    @Delete("DELETE FROM homework_submissions WHERE homework_id = #{homeworkId}")
    int deleteByHomeworkId(@Param("homeworkId") Long homeworkId);
    
    @Select("SELECT id FROM homework_submissions WHERE student_id = #{studentId}")
    List<Long> findIdsByStudentId(@Param("studentId") Long studentId);
    
    @Select("SELECT id FROM homework_submissions WHERE homework_id = #{homeworkId}")
    List<Long> findIdsByHomeworkId(@Param("homeworkId") Long homeworkId);
}
