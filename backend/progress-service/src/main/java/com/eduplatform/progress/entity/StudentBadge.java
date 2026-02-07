package com.eduplatform.progress.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 学生徽章获得记录实体
 */
@Data
@TableName("student_badges")
public class StudentBadge {
    @TableId(type = IdType.AUTO)
    private Long id;
    
    private Long studentId;    // 学生ID
    private Integer badgeId;   // 徽章ID
    
    @TableField("earned_at")
    private LocalDateTime earnedAt;  // 获得时间
}
