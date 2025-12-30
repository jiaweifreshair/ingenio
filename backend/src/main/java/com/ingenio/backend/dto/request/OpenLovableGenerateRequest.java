package com.ingenio.backend.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * OpenLovable代码生成请求DTO
 * 用于V2.0架构Plan阶段调用OpenLovable-CN生成前端原型
 *
 * @author Ingenio Team
 * @version 2.0.0
 * @since 2025-11-14
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OpenLovableGenerateRequest {

    /**
     * 用户需求描述（自然语言）
     * 必填，描述要生成的应用功能
     */
    private String userRequirement;

    /**
     * 参考网站URL列表（可选）
     * 当intent为CLONE或HYBRID时，提供要爬取的网站URL
     */
    private List<String> referenceUrls;

    /**
     * 定制化需求（可选）
     * 当intent为HYBRID时，描述在参考网站基础上的定制化修改
     */
    private String customizationRequirement;

    /**
     * AI模型选择（可选，默认使用 Gemini 3 Pro）
     *
     * 说明：
     * - 当前 OpenLovable-CN 的部署更适配 gemini- 前缀模型（GCA 直连/稳定输出），避免出现“生成成功但代码为空”的假阳性。
     * - 仍支持其它模型（open-lovable-cn 会自动 fallback），但建议在未显式指定时优先 Gemini 3 Pro。
     *
     * 支持的模型示例：
     * - gemini-3-pro-preview（默认）
     * - deepseek-r1
     * - deepseek-v3
     * - deepseek-v3.1
     * - qwen3-max
     * - kimi-k2
     */
    @Builder.Default
    private String aiModel = "gemini-3-pro-preview";

    /**
     * 是否需要爬取网站（可选，默认根据referenceUrls自动判断）
     * true: 强制爬取referenceUrls
     * false: 不爬取，纯生成
     * null: 自动判断（有referenceUrls则爬取）
     */
    private Boolean needsCrawling;

    /**
     * 生成超时时间（秒，默认30秒）
     * OpenLovable生成时间通常在5-10秒
     */
    @Builder.Default
    private Integer timeoutSeconds = 30;

    /**
     * 是否流式返回（默认false）
     * true: 使用SSE流式返回生成过程
     * false: 等待完成后返回最终结果
     */
    @Builder.Default
    private Boolean streaming = false;

    /**
     * 沙箱ID（可选）
     * 如果已有沙箱，可复用已有沙箱
     * 如果为null，会自动创建新沙箱
     */
    private String sandboxId;

    /**
     * 验证请求参数
     *
     * @return true 如果参数有效
     */
    public boolean isValid() {
        if (userRequirement == null || userRequirement.trim().isEmpty()) {
            return false;
        }

        if (userRequirement.length() < 10) {
            return false; // 需求描述过短
        }

        return true;
    }

    /**
     * 判断是否需要爬取（根据needsCrawling和referenceUrls综合判断）
     *
     * @return true 如果需要爬取
     */
    public boolean shouldCrawl() {
        if (needsCrawling != null) {
            return needsCrawling;
        }

        return referenceUrls != null && !referenceUrls.isEmpty();
    }

    /**
     * 获取第一个参考URL（用于爬取）
     *
     * @return 第一个URL，如果没有则返回null
     */
    public String getPrimaryReferenceUrl() {
        if (referenceUrls == null || referenceUrls.isEmpty()) {
            return null;
        }
        return referenceUrls.get(0);
    }

    /**
     * 构建发送给Open-Lovable的提示词
     *
     * @return 优化后的提示词
     */
    public String buildPrompt() {
        StringBuilder prompt = new StringBuilder();

        // 基础需求
        prompt.append(userRequirement);

        // 如果有参考网站
        if (shouldCrawl()) {
            prompt.append("\n\n参考网站：");
            for (String url : referenceUrls) {
                prompt.append("\n- ").append(url);
            }
        }

        // 如果有定制化需求
        if (customizationRequirement != null && !customizationRequirement.trim().isEmpty()) {
            prompt.append("\n\n定制化要求：\n");
            prompt.append(customizationRequirement);
        }

        return prompt.toString();
    }
}
