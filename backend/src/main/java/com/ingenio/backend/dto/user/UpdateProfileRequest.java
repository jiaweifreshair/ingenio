package com.ingenio.backend.dto.user;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 更新用户信息请求DTO
 */
@Data
public class UpdateProfileRequest {

    /**
     * 用户名（登录凭证之一）
     * 规则：3-20字符，仅允许字母、数字、下划线
     */
    @Size(min = 3, max = 20, message = "用户名长度必须在3-20个字符之间")
    @Pattern(regexp = "^[a-zA-Z0-9_]+$", message = "用户名只能包含字母、数字和下划线")
    private String username;

    /**
     * 用户显示名称
     */
    @Size(max = 100, message = "显示名称不能超过100个字符")
    private String displayName;

    /**
     * 邮箱地址（修改邮箱需验证）
     */
    @Email(message = "邮箱格式不正确")
    private String email;

    /**
     * 手机号码
     */
    @Size(max = 20, message = "手机号码不能超过20个字符")
    private String phone;

    /**
     * 个人简介
     */
    @Size(max = 500, message = "个人简介不能超过500个字符")
    private String bio;

    /**
     * 头像URL
     */
    @Size(max = 500, message = "头像URL不能超过500个字符")
    private String avatarUrl;
}
