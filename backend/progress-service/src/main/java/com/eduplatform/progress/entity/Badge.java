package com.eduplatform.progress.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 徽章定义实体
 */
@Data
@TableName("badges")
public class Badge {
    @TableId(type = IdType.AUTO)
    private Integer id;
    
    private String name;           // 徽章名称
    private String description;    // 徽章描述
    private String icon;           // 图标
    private String conditionType;  // 获取条件类型: chapter_complete, study_days, perfect_score
    private Integer conditionValue; // 条件值
    
    @TableField("created_at")
    private LocalDateTime createdAt;
}
