package com.eduplatform.course.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 内容合规屏蔽词实体类
 * 对应数据库表 `blocked_words`，用于在评论或课程描述中过滤敏感词汇。
 * 支持全局屏蔽（所有课程生效）或针对特定课程的精准屏蔽。
 */
@Data
@TableName("blocked_words")
public class BlockedWord {

    /**
     * 屏蔽词记录 ID
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 待屏蔽的关键字符串 (如：广告用语、违规词)
     */
    private String word;

    /**
     * 过滤作用范围 (global: 全系统拦截, course: 仅在指定课程内拦截)
     */
    private String scope;

    /**
     * 关联课程 ID (当 scope 为 'course' 时必填，用于处理该课程专属的敏感词)
     */
    private Long courseId;

    /**
     * 创建该拦截规则的管理员或教师 ID
     */
    private Long createdBy;

    /**
     * 规则创建时间
     */
    private LocalDateTime createdAt;
}
