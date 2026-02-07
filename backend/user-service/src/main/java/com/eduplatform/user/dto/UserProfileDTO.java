package com.eduplatform.user.dto;

import lombok.Data;
import java.time.LocalDate;

/**
 * 用户个人资料数据传输对象
 * 用于更新用户基本信息。
 *
 * @author Antigravity
 */
@Data
public class UserProfileDTO {

    /**
     * 姓名（真实姓名/昵称）
     */
    private String name;

    /**
     * 用户名
     */
    private String username;

    /**
     * 手机号
     */
    private String phone;

    /**
     * 头像URL
     */
    private String avatar;

    /**
     * 出生日期
     */
    private LocalDate birthday;

    /**
     * 性别
     */
    private String gender;
}
