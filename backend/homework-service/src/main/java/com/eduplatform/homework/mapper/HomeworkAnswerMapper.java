package com.eduplatform.homework.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.eduplatform.homework.entity.HomeworkAnswer;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface HomeworkAnswerMapper extends BaseMapper<HomeworkAnswer> {
    
    @Delete("DELETE FROM homework_answers WHERE submission_id = #{submissionId}")
    int deleteBySubmissionId(@Param("submissionId") Long submissionId);
    
    @Delete("DELETE FROM homework_answers WHERE question_id = #{questionId}")
    int deleteByQuestionId(@Param("questionId") Long questionId);
}
