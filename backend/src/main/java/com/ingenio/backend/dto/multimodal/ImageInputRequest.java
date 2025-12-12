package com.ingenio.backend.dto.multimodal;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 图像输入请求DTO
 */
@Data
public class ImageInputRequest {

    /**
     * 图片文件URL（MinIO存储）
     */
    @NotBlank(message = "图片文件URL不能为空")
    private String imageUrl;

    /**
     * 分析类型（ocr=文字识别, ui=UI识别, both=两者都做）
     */
    private String analysisType = "both";

    /**
     * 用户ID
     */
    private String userId;

    /**
     * 租户ID
     */
    private String tenantId;
}
