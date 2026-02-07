package com.eduplatform.common.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * 统一异步消息体
 * 所有通过 Redis Stream 传输的业务事件均使用此模型封装，
 * 保证消息结构的一致性与可追溯性。
 *
 * @author Antigravity
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EventMessage implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 消息唯一标识（UUID，用于幂等性校验） */
    private String id;

    /** 事件类型（对应 EventType 枚举的 name()） */
    private String type;

    /** 来源服务名（如 homework-service） */
    private String source;

    /** 业务数据载荷（键值对形式，灵活承载各事件的具体数据） */
    private Map<String, Object> data;

    /** 消息发送时间戳 */
    private LocalDateTime timestamp;
}
