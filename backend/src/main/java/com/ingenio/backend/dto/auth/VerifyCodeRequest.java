package com.ingenio.backend.dto.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * 验证码校验请求
 *
 * 用于验证用户输入的邮箱验证码是否正确
 *
 * 请求示例：
 * POST /api/v1/auth/verification-code/verify
 * {
 *   "email": "user@example.com",
 *   "code": "123456",
 *   "type": "REGISTER"
 * }
 *
 * @author Ingenio Team
 * @since Phase 5.1
 */
@Data
public class VerifyCodeRequest {
    /**
     * 邮箱地址
     * 必填，需要符合邮箱格式
     */
    @NotBlank(message = "邮箱不能为空")
    @Email(message = "邮箱格式不正确")
    private String email;

    /**
     * 验证码
     * 必填，6位数字
     */
    @NotBlank(message = "验证码不能为空")
    @Pattern(regexp = "^\\d{6}$", message = "验证码必须为6位数字")
    private String code;

    /**
     * 验证码类型
     * REGISTER - 注册验证
     * RESET_PASSWORD - 找回密码
     * CHANGE_EMAIL - 修改邮箱
     */
    @NotNull(message = "验证码类型不能为空")
    private VerificationType type;
}
