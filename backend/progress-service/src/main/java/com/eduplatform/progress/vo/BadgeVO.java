package com.eduplatform.progress.vo;

import lombok.Data;
import java.time.LocalDateTime;

/**
 * 徽章视图对象
 * 用于向前端展示用户的徽章获得情况，包含徽章基础信息及解锁状态。
 *
 * @author Antigravity
 */
@Data
public class BadgeVO {

    /**
     * 徽章ID
     */
    private Long id;

    /**
     * 徽章名称
     */
    private String name;

    /**
     * 徽章描述
     */
    private String description;

    /**
     * 徽章图标 URL
     */
    private String iconUrl;

    /**
     * 徽章分类 (ACHIEVEMENT, MILESTONE, etc.)
     */
    private String category;

    /**
     * 稀有度
     */
    private String rarity;

    /**
     * 是否已获得
     */
    private Boolean earned;

    /**
     * 获得时间
     */
    private LocalDateTime earnedAt;

    /**
     * 获取条件类型
     */
    private String conditionType;

    /**
     * 条件目标值
     */
    private Integer conditionValue;

    /**
     * 进度百分比 (0-100)
     */
    private Integer progress;

    /**
     * 当前进度值
     */
    private Integer currentValue;

    /**
     * 目标进度值
     */
    private Integer targetValue;

    /**
     * 是否即将解锁
     */
    private Boolean nearUnlock;
}
