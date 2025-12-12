package com.ingenio.backend.dto.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 重置密码请求
 *
 * 用于通过邮箱验证码重置密码
 *
 * 请求示例：
 * POST /api/v1/auth/reset-password
 * {
 *   "email": "user@example.com",
 *   "code": "123456",
 *   "newPassword": "NewPassword123"
 * }
 *
 * 业务流程：
 * 1. 用户请求发送验证码到邮箱（RESET_PASSWORD类型）
 * 2. 用户输入邮箱、验证码和新密码
 * 3. 后端验证验证码是否正确
 * 4. 验证通过后更新用户密码（BCrypt加密）
 * 5. 删除验证码（防止重复使用）
 * 6. 返回成功响应
 *
 * 安全机制：
 * - 验证码5分钟过期
 * - 验证码验证后立即删除
 * - 密码强度验证
 * - BCrypt加密存储
 *
 * @author Ingenio Team
 * @since Phase 6.2
 */
@Data
public class ResetPasswordRequest {
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
     * 新密码
     * 必填，8-50个字符
     * 要求：至少包含大写字母、小写字母和数字
     */
    @NotBlank(message = "新密码不能为空")
    @Size(min = 8, max = 50, message = "密码长度必须在8-50个字符之间")
    @Pattern(
            regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).{8,50}$",
            message = "密码必须包含大写字母、小写字母和数字"
    )
    private String newPassword;
}
