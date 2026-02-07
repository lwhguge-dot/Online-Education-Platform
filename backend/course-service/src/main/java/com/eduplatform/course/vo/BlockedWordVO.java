package com.eduplatform.course.vo;

import lombok.Data;
import java.time.LocalDateTime;

/**
 * 屏蔽词视图对象
 * 对外返回合规词库数据，隔离持久层实体。
 */
@Data
public class BlockedWordVO {

    /**
     * 屏蔽词记录ID
     */
    private Long id;

    /**
     * 屏蔽词内容
     */
    private String word;

    /**
     * 作用域 (global/course)
     */
    private String scope;

    /**
     * 课程ID
     */
    private Long courseId;

    /**
     * 创建者ID
     */
    private Long createdBy;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;
}
