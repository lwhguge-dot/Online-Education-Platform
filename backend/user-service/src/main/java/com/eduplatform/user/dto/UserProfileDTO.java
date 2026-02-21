package com.eduplatform.user.dto;

import jakarta.validation.constraints.Size;
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
    @Size(max = 100, message = "name长度不能超过100")
    private String name;

    /**
     * 用户名
     */
    @Size(max = 50, message = "username长度不能超过50")
    private String username;

    /**
     * 手机号
     */
    @Size(max = 20, message = "phone长度不能超过20")
    private String phone;

    /**
     * 头像URL
     */
    @Size(max = 500, message = "avatar长度不能超过500")
    private String avatar;

    /**
     * 出生日期
     */
    private LocalDate birthday;

    /**
     * 性别
     */
    @Size(max = 20, message = "gender长度不能超过20")
    private String gender;
}
