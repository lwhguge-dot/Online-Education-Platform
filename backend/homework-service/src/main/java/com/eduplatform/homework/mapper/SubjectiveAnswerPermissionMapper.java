package com.eduplatform.homework.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.eduplatform.homework.entity.SubjectiveAnswerPermission;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface SubjectiveAnswerPermissionMapper extends BaseMapper<SubjectiveAnswerPermission> {
    
    @Delete("DELETE FROM subjective_answer_permission WHERE student_id = #{studentId}")
    int deleteByStudentId(@Param("studentId") Long studentId);
    
    @Delete("DELETE FROM subjective_answer_permission WHERE question_id = #{questionId}")
    int deleteByQuestionId(@Param("questionId") Long questionId);
}
