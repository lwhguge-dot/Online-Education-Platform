package com.eduplatform.homework.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.eduplatform.homework.entity.TeachingEvent;
import org.apache.ibatis.annotations.*;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface TeachingEventMapper extends BaseMapper<TeachingEvent> {
    
    @Select("""
        SELECT te.*
        FROM teaching_events te
        WHERE te.teacher_id = #{teacherId}
        AND te.start_time >= #{startDate}
        AND te.start_time < #{endDate}
        AND (te.status IS NULL OR te.status != 'cancelled')
        ORDER BY te.start_time ASC
    """)
    List<TeachingEvent> findByTeacherAndDateRange(
        @Param("teacherId") Long teacherId,
        @Param("startDate") LocalDateTime startDate,
        @Param("endDate") LocalDateTime endDate
    );
    
    @Select("""
        SELECT * FROM teaching_events
        WHERE teacher_id = #{teacherId}
        AND course_id = #{courseId}
        AND status != 'cancelled'
        ORDER BY start_time ASC
    """)
    List<TeachingEvent> findByCourse(@Param("teacherId") Long teacherId, @Param("courseId") Long courseId);
    
    @Delete("DELETE FROM teaching_events WHERE teacher_id = #{teacherId}")
    int deleteByTeacherId(@Param("teacherId") Long teacherId);
    
    @Delete("DELETE FROM teaching_events WHERE course_id = #{courseId}")
    int deleteByCourseId(@Param("courseId") Long courseId);
    
    @Delete("DELETE FROM teaching_events WHERE homework_id = #{homeworkId}")
    int deleteByHomeworkId(@Param("homeworkId") Long homeworkId);
}
