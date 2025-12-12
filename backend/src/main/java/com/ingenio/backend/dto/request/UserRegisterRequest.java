package com.ingenio.backend.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 用户注册请求DTO
 * 用于接收用户注册时提交的表单数据
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserRegisterRequest {

    /**
     * 用户名
     * 必填，长度3-50字符
     */
    @NotBlank(message = "用户名不能为空")
    @Size(min = 3, max = 50, message = "用户名长度必须在3-50字符之间")
    private String username;

    /**
     * 邮箱
     * 必填，必须是有效的邮箱格式
     */
    @NotBlank(message = "邮箱不能为空")
    @Email(message = "邮箱格式不正确")
    private String email;

    /**
     * 密码
     * 必填，长度6-128字符
     */
    @NotBlank(message = "密码不能为空")
    @Size(min = 6, max = 128, message = "密码长度必须在6-128字符之间")
    private String password;

    /**
     * 租户ID
     * 可选，用于多租户场景
     */
    private String tenantId;

    /**
     * 显示名称
     * 可选，用于页面展示
     */
    @Size(max = 100, message = "显示名称长度不能超过100字符")
    private String displayName;
}
