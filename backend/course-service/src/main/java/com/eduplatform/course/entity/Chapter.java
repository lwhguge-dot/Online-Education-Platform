package com.eduplatform.course.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 章节实体类
 * 对应数据库表 `chapters`，隶属于特定课程，承载视频资源、学习目标及解锁逻辑。
 * 章节支持通过 sort_order 进行灵活排序，并可设置前置解锁依赖。
 *
 * @author Antigravity
 */
@Data
@TableName("chapters")
public class Chapter {

    /**
     * 章节唯一标识
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 关联的父级课程 ID
     */
    private Long courseId;

    /**
     * 章节名称 (例如：第一章 环境搭建)
     */
    private String title;

    /**
     * 章节学习摘要或重点导读
     */
    private String description;

    /**
     * 展示排序索引 (数值越小排名越靠前，用于决定课程大纲顺序)
     */
    private Integer sortOrder;

    /**
     * 视频资源存储地址 (支持云存储 URL 或点播 ID)
     */
    private String videoUrl;

    /**
     * 视频播放时长 (规格：秒，用于计算视频进度百分比)
     */
    private Integer videoDuration;

    /**
     * 学习门槛：需要前序视频观看率达标 (浮点数：0.00-1.00，例如 0.8 代表需观看 80%)
     */
    private BigDecimal unlockVideoRate;

    /**
     * 学习门槛：需要前序章节测验达标分数 (整型，未达分则不可进入本章)
     */
    private Integer unlockQuizScore;

    /**
     * 章节可用性状态 (1: 激活/公开, 0: 隐藏/编辑中)
     */
    private Integer status;

    /**
     * 映射 created_at，自动填充
     */
    @TableField("created_at")
    private LocalDateTime createdAt;

    /**
     * 映射 updated_at，自动填充
     */
    @TableField("updated_at")
    private LocalDateTime updatedAt;
}
