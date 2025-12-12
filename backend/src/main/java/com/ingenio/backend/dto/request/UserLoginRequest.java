package com.ingenio.backend.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import jakarta.validation.constraints.NotBlank;

/**
 * 用户登录请求DTO
 * 用于接收用户登录时提交的表单数据
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserLoginRequest {

    /**
     * 用户名或邮箱
     * 必填
     */
    @NotBlank(message = "用户名不能为空")
    private String username;

    /**
     * 密码
     * 必填
     */
    @NotBlank(message = "密码不能为空")
    private String password;

    /**
     * 租户ID
     * 可选，用于多租户场景
     */
    private String tenantId;

    /**
     * 记住我
     * 可选，用于延长会话时间
     */
    private Boolean rememberMe;
}
