package com.eduplatform.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 教师公告DTO
 */
@Data
public class TeacherAnnouncementDTO {
    /** 公告标题 */
    @NotBlank(message = "title不能为空")
    @Size(max = 200, message = "title长度不能超过200")
    private String title;
    
    /** 公告内容 */
    @NotBlank(message = "content不能为空")
    @Size(max = 10000, message = "content长度不能超过10000")
    private String content;
    
    /** 目标受众：ALL/STUDENT */
    @Pattern(regexp = "^(ALL|STUDENT)$", message = "targetAudience仅支持ALL/STUDENT")
    private String targetAudience;
    
    /** 关联课程ID（NULL表示全局公告） */
    @Positive(message = "courseId必须为正数")
    private Long courseId;
    
    /** 是否置顶 */
    private Boolean isPinned;
    
    /** 定时发布时间（NULL表示立即发布） */
    private LocalDateTime publishTime;
    
    /** 过期时间 */
    private LocalDateTime expireTime;
}
