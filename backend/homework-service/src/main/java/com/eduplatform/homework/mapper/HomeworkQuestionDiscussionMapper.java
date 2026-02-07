package com.eduplatform.homework.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.eduplatform.homework.entity.HomeworkQuestionDiscussion;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface HomeworkQuestionDiscussionMapper extends BaseMapper<HomeworkQuestionDiscussion> {
    
    @Select("SELECT * FROM homework_questions_discussion WHERE homework_id = #{homeworkId} ORDER BY created_at DESC")
    List<HomeworkQuestionDiscussion> findByHomeworkId(@Param("homeworkId") Long homeworkId);
    
    @Select("SELECT * FROM homework_questions_discussion WHERE student_id = #{studentId} ORDER BY created_at DESC")
    List<HomeworkQuestionDiscussion> findByStudentId(@Param("studentId") Long studentId);
    
    @Select("SELECT COUNT(*) FROM homework_questions_discussion WHERE homework_id = #{homeworkId} AND status = 'pending'")
    int countPendingByHomeworkId(@Param("homeworkId") Long homeworkId);
}
