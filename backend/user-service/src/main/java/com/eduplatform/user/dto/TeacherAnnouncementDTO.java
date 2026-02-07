package com.eduplatform.user.dto;

import lombok.Data;
import java.time.LocalDateTime;

/**
 * 教师公告DTO
 */
@Data
public class TeacherAnnouncementDTO {
    /** 公告标题 */
    private String title;
    
    /** 公告内容 */
    private String content;
    
    /** 目标受众：ALL/STUDENT */
    private String targetAudience;
    
    /** 关联课程ID（NULL表示全局公告） */
    private Long courseId;
    
    /** 是否置顶 */
    private Boolean isPinned;
    
    /** 定时发布时间（NULL表示立即发布） */
    private LocalDateTime publishTime;
    
    /** 过期时间 */
    private LocalDateTime expireTime;
}
