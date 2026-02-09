package com.eduplatform.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.eduplatform.user.entity.TeacherProfile;
import org.apache.ibatis.annotations.*;

@Mapper
public interface TeacherProfileMapper extends BaseMapper<TeacherProfile> {
    
    @Select("SELECT * FROM teacher_profiles WHERE user_id = #{userId}")
    TeacherProfile findByUserId(@Param("userId") Long userId);
    
    @Insert("""
        INSERT INTO teacher_profiles (user_id, title, department, subjects, introduction, 
            teaching_subjects, default_grading_criteria, dashboard_layout, notification_settings)
        VALUES (#{userId}, #{title}, #{department}, #{subjects}, #{introduction},
            #{teachingSubjects}::jsonb, #{defaultGradingCriteria}::jsonb, #{dashboardLayout}::jsonb, #{notificationSettings}::jsonb)
    """)
    @Options(useGeneratedKeys = true, keyProperty = "id")
    int insertProfile(TeacherProfile profile);
    
    @Update("""
        UPDATE teacher_profiles SET
            title = #{title},
            department = #{department},
            subjects = #{subjects},
            introduction = #{introduction},
            teaching_subjects = #{teachingSubjects}::jsonb,
            default_grading_criteria = #{defaultGradingCriteria}::jsonb,
            dashboard_layout = #{dashboardLayout}::jsonb,
            notification_settings = #{notificationSettings}::jsonb
        WHERE user_id = #{userId}
    """)
    int updateByUserId(TeacherProfile profile);
    
    @Delete("DELETE FROM teacher_profiles WHERE user_id = #{userId}")
    int deleteByUserId(@Param("userId") Long userId);
}
