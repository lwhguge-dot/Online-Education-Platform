package com.eduplatform.homework.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.eduplatform.homework.entity.HomeworkUnlock;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface HomeworkUnlockMapper extends BaseMapper<HomeworkUnlock> {
    
    @Delete("DELETE FROM homework_unlocks WHERE student_id = #{studentId}")
    int deleteByStudentId(@Param("studentId") Long studentId);
    
    @Delete("DELETE FROM homework_unlocks WHERE homework_id = #{homeworkId}")
    int deleteByHomeworkId(@Param("homeworkId") Long homeworkId);
}
