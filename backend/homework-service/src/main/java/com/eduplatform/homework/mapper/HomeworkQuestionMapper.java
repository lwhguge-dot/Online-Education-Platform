package com.eduplatform.homework.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.eduplatform.homework.entity.HomeworkQuestion;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import java.util.List;

@Mapper
public interface HomeworkQuestionMapper extends BaseMapper<HomeworkQuestion> {
    
    @Select("SELECT COUNT(*) FROM homework_questions WHERE homework_id = #{homeworkId}")
    int countByHomeworkId(@Param("homeworkId") Long homeworkId);
    
    @Select("SELECT * FROM homework_questions WHERE homework_id = #{homeworkId} ORDER BY sort_order ASC")
    List<HomeworkQuestion> findByHomeworkId(@Param("homeworkId") Long homeworkId);
    
    @Delete("DELETE FROM homework_questions WHERE homework_id = #{homeworkId}")
    int deleteByHomeworkId(@Param("homeworkId") Long homeworkId);
    
    @Select("SELECT id FROM homework_questions WHERE homework_id = #{homeworkId}")
    List<Long> findIdsByHomeworkId(@Param("homeworkId") Long homeworkId);
}
