package com.ingenio.backend.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;

/**
 * OpenLovable代码生成响应DTO
 * 封装OpenLovable-CN生成的前端原型信息
 *
 * @author Ingenio Team
 * @version 2.0.0
 * @since 2025-11-14
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OpenLovableGenerateResponse {

    /**
     * 生成是否成功
     */
    private Boolean success;

    /**
     * 沙箱ID
     * 例如：iigs8vh5hr5v9lj9junvx (E2B)
     */
    private String sandboxId;

    /**
     * 沙箱提供商
     * 例如：e2b, vercel
     */
    private String provider;

    /**
     * 原型预览URL
     * 用户可通过此URL查看生成的前端原型
     * 例如：https://5173-iigs8vh5hr5v9lj9junvx.e2b.app
     */
    private String previewUrl;

    /**
     * 生成的前端原型代码（可选）
     * 当需要保存到AppSpec.frontend_prototype时使用
     * 包含所有生成的文件内容
     */
    private Map<String, String> generatedFiles;

    /**
     * 生成耗时（秒）
     */
    private Long durationSeconds;

    /**
     * 生成完成时间
     */
    private Instant completedAt;

    /**
     * 错误信息（当success=false时）
     */
    private String errorMessage;

    /**
     * 生成过程日志（可选，用于调试）
     */
    private String generationLog;

    /**
     * 是否经过了网站爬取
     */
    private Boolean crawled;

    /**
     * 爬取的网站URL（如果crawled=true）
     */
    private String crawledUrl;

    /**
     * AI模型使用情况
     */
    private AIModelUsage modelUsage;

    /**
     * AI模型使用情况
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AIModelUsage {
        /**
         * 使用的AI模型名称
         */
        private String modelName;

        /**
         * 输入token数量
         */
        private Integer inputTokens;

        /**
         * 输出token数量
         */
        private Integer outputTokens;

        /**
         * 总token数量
         */
        private Integer totalTokens;

        /**
         * 预估成本（美元）
         */
        private Double estimatedCost;
    }

    /**
     * 判断生成是否成功
     *
     * @return true 如果成功
     */
    public boolean isSuccessful() {
        return success != null && success && previewUrl != null && !previewUrl.isEmpty();
    }

    /**
     * 获取格式化的成功消息
     *
     * @return 格式化消息
     */
    public String getFormattedMessage() {
        if (isSuccessful()) {
            StringBuilder msg = new StringBuilder();
            msg.append("前端原型生成成功");

            if (crawled != null && crawled) {
                msg.append("（已爬取参考网站：").append(crawledUrl).append("）");
            }

            if (durationSeconds != null) {
                msg.append("，耗时 ").append(durationSeconds).append(" 秒");
            }

            return msg.toString();
        } else {
            return "生成失败：" + (errorMessage != null ? errorMessage : "未知错误");
        }
    }

    /**
     * 创建失败响应
     *
     * @param errorMessage 错误信息
     * @return 失败响应对象
     */
    public static OpenLovableGenerateResponse failure(String errorMessage) {
        return OpenLovableGenerateResponse.builder()
                .success(false)
                .errorMessage(errorMessage)
                .completedAt(Instant.now())
                .build();
    }

    /**
     * 创建成功响应
     *
     * @param sandboxId 沙箱ID
     * @param previewUrl 预览URL
     * @param provider 提供商
     * @return 成功响应对象
     */
    public static OpenLovableGenerateResponse success(String sandboxId, String previewUrl, String provider) {
        return OpenLovableGenerateResponse.builder()
                .success(true)
                .sandboxId(sandboxId)
                .previewUrl(previewUrl)
                .provider(provider)
                .completedAt(Instant.now())
                .build();
    }
}
