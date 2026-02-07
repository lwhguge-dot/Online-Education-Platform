package com.eduplatform.course.dto;

import lombok.Data;

/**
 * 用户简要信息传输对象
 * 用于跨服务获取用户基础信息，避免依赖其他服务的 VO。
 *
 * @author Antigravity
 */
@Data
public class UserBriefDTO {

    /**
     * 用户ID
     */
    private Long id;

    /**
     * 用户名
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
