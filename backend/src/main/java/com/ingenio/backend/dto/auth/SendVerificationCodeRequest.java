package com.ingenio.backend.dto.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 发送验证码请求
 *
 * 用于请求发送邮箱验证码
 *
 * 请求示例：
 * POST /api/v1/auth/verification-code/send
 * {
 *   "email": "user@example.com",
 *   "type": "REGISTER"
 * }
 *
 * @author Ingenio Team
 * @since Phase 5.1
 */
@Data
public class SendVerificationCodeRequest {
    /**
     * 邮箱地址
     * 必填，需要符合邮箱格式
     */
    @NotBlank(message = "邮箱不能为空")
    @Email(message = "邮箱格式不正确")
    private String email;

    /**
     * 验证码类型
     * REGISTER - 注册验证
     * RESET_PASSWORD - 找回密码
     * CHANGE_EMAIL - 修改邮箱
     */
    @NotNull(message = "验证码类型不能为空")
    private VerificationType type;
}
