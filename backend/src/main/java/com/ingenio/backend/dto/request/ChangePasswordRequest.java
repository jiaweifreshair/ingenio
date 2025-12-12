package com.ingenio.backend.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 修改密码请求DTO
 * 用于用户修改密码时提交的表单数据
 *
 * @author Ingenio Team
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChangePasswordRequest {

    /**
     * 旧密码
     * 必填，用于验证用户身份
     */
    @NotBlank(message = "旧密码不能为空")
    private String oldPassword;

    /**
     * 新密码
     * 必填，长度6-128字符
     */
    @NotBlank(message = "新密码不能为空")
    @Size(min = 6, max = 128, message = "新密码长度必须在6-128字符之间")
    private String newPassword;

    /**
     * 确认新密码
     * 必填，必须与新密码一致
     */
    @NotBlank(message = "确认密码不能为空")
    private String confirmPassword;
}
