package com.eduplatform.user.dto;

import com.eduplatform.user.vo.AnnouncementVO;
import lombok.Data;

/**
 * 公告统计DTO
 */
@Data
public class AnnouncementStatsDTO {
    /** 公告信息 */
    private AnnouncementVO announcement;
    
    /** 阅读人数 */
    private Integer readCount;
    
    /** 目标人数（课程学生数或全部学生数） */
    private Integer targetCount;
    
    /** 阅读率 */
    private Double readRate;
}
