package com.eduplatform.course.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.eduplatform.course.entity.ChapterQuiz;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ChapterQuizMapper extends BaseMapper<ChapterQuiz> {
    
    @Delete("DELETE FROM chapter_quizzes WHERE chapter_id = #{chapterId}")
    int deleteByChapterId(@Param("chapterId") Long chapterId);
}
