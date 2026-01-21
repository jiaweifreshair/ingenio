package com.ingenio.backend.config;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * AI 模型覆写配置（按“职责”拆分）
 *
 * 设计目标：
 * - 支持在同一个 Spring AI ChatModel 基础上，对不同 Agent/阶段按需指定模型；
 * - 便于线上/本地快速做模型对照测试（A/B），无需改业务代码；
 * - 默认不覆写：为空字符串时保持 Spring AI 的全局默认模型配置不变。
 *
 * 配置项（支持环境变量注入）：
 * - ingenio.ai.models.intent：意图识别模型（IntentClassifier）
 * - ingenio.ai.models.execute：编程/生成模型（ExecuteAgent 等“生成阶段”）
 * - ingenio.ai.models.validate：测试/验证模型（ValidateAgent 等“验证阶段”）
 */
@Getter
@Component
public class AiModelOverrides {

    private final String intent;
    private final String execute;
    private final String validate;

    public AiModelOverrides(
            @Value("${ingenio.ai.models.intent:}") String intent,
            @Value("${ingenio.ai.models.execute:}") String execute,
            @Value("${ingenio.ai.models.validate:}") String validate
    ) {
        this.intent = normalize(intent);
        this.execute = normalize(execute);
        this.validate = normalize(validate);
    }

    /**
     * 规范化配置值：空/空白统一视为“不覆写”（返回 null）
     */
    private static String normalize(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}

