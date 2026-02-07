package com.eduplatform.user.vo;

import lombok.Data;

/**
 * 用户简要信息视图对象
 * 用于跨服务传输用户基础信息，避免暴露完整用户实体。
 * 典型场景：course-service 批量获取学生名单时使用。
 *
 * @author Antigravity
 */
@Data
public class UserBriefVO {

    /**
     * 用户ID
     */
    private Long id;

    /**
     * 用户名/昵称
     */
    private String username;

    /**
     * 真实姓名
     */
    private String name;

    /**
     * 电子邮箱
     */
    private String email;
}
