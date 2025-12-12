package com.ingenio.backend.dto.multimodal;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 文本输入请求DTO
 */
@Data
public class TextInputRequest {

    /**
     * 输入文本内容
     */
    @NotBlank(message = "输入文本不能为空")
    @Size(min = 10, max = 10000, message = "输入文本长度必须在10-10000之间")
    private String text;

    /**
     * 用户ID（可选，未登录也可使用）
     */
    private String userId;

    /**
     * 租户ID
     */
    private String tenantId;
}
