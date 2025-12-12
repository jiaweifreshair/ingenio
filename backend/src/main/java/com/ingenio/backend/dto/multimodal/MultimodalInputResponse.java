package com.ingenio.backend.dto.multimodal;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;

/**
 * 多模态输入响应DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MultimodalInputResponse {

    /**
     * 输入记录ID
     */
    private String inputId;

    /**
     * 输入类型（text/voice/image）
     */
    private String type;

    /**
     * 原始内容（文本内容或文件URL）
     */
    private String content;

    /**
     * 转录/分析结果
     */
    private String transcript;

    /**
     * 分析结果详情
     */
    private Map<String, Object> analysisResult;

    /**
     * 处理状态（pending/processing/completed/failed）
     */
    private String status;

    /**
     * 错误信息
     */
    private String errorMessage;

    /**
     * 创建时间
     */
    private Instant createdAt;

    /**
     * 更新时间
     */
    private Instant updatedAt;
}
