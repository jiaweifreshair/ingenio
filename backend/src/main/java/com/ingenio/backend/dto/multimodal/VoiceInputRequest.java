package com.ingenio.backend.dto.multimodal;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * 语音输入请求DTO
 */
@Data
public class VoiceInputRequest {

    /**
     * 音频文件URL（MinIO存储）
     */
    @NotBlank(message = "音频文件URL不能为空")
    private String audioUrl;

    /**
     * 音频格式（mp3/wav/m4a等）
     */
    @NotBlank(message = "音频格式不能为空")
    @Pattern(regexp = "^(mp3|wav|m4a|ogg|webm)$", message = "不支持的音频格式")
    private String audioFormat;

    /**
     * 语言代码（zh-CN/en-US等）
     */
    private String language = "zh-CN";

    /**
     * 用户ID
     */
    private String userId;

    /**
     * 租户ID
     */
    private String tenantId;
}
