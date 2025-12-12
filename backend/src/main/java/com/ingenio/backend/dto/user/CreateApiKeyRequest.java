package com.ingenio.backend.dto.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 创建API密钥请求DTO
 */
@Data
public class CreateApiKeyRequest {

    /**
     * 密钥名称
     */
    @NotBlank(message = "密钥名称不能为空")
    @Size(max = 100, message = "密钥名称不能超过100个字符")
    private String name;

    /**
     * 密钥描述
     */
    @Size(max = 500, message = "密钥描述不能超过500个字符")
    private String description;

    /**
     * 权限范围（如：["read", "write"]）
     */
    private List<String> scopes;

    /**
     * 速率限制（每分钟请求数，NULL表示使用默认值）
     */
    private Integer rateLimit;

    /**
     * 过期天数（NULL表示永久有效）
     */
    private Integer expireDays;
}
