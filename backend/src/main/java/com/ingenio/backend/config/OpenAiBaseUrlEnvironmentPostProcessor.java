package com.ingenio.backend.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.util.StringUtils;

import java.util.Map;

/**
 * Spring AI OpenAI base-url 规范化处理器
 *
 * <p>问题背景：</p>
 * <ul>
 *   <li>Spring AI 的 {@code spring.ai.openai.base-url} 约定为“域名根地址”，内部会自动拼接 {@code /v1/...}</li>
 *   <li>若把 base-url 误配置为 {@code https://api.qnaigc.com/v1}，最终请求会变成 {@code /v1/v1/chat/completions}（404）</li>
 * </ul>
 *
 * <p>本处理器做什么：</p>
 * <ul>
 *   <li>在应用启动早期阶段，将 {@code spring.ai.openai.base-url} 的尾部 {@code /v1} 自动裁剪掉</li>
 *   <li>同时移除末尾多余的 {@code /}，降低因环境变量误配导致的线上故障概率</li>
 * </ul>
 *
 * <p>为什么这么做：</p>
 * <ul>
 *   <li>配置层面对误配做兜底，避免把修复成本转嫁给前端（SSE）或业务逻辑层</li>
 *   <li>兼容历史部署脚本/环境变量仍使用 {@code .../v1} 的情况</li>
 * </ul>
 */
public class OpenAiBaseUrlEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    private static final String OPENAI_BASE_URL_KEY = "spring.ai.openai.base-url";
    private static final String PROPERTY_SOURCE_NAME = "ingenioOpenAiBaseUrlNormalization";

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        String rawValue = environment.getProperty(OPENAI_BASE_URL_KEY);
        if (!StringUtils.hasText(rawValue)) {
            return;
        }

        String normalized = normalizeBaseUrl(rawValue);
        if (rawValue.equals(normalized)) {
            return;
        }

        environment.getPropertySources().addFirst(
                new MapPropertySource(PROPERTY_SOURCE_NAME, Map.of(OPENAI_BASE_URL_KEY, normalized))
        );
    }

    /**
     * 规范化 base-url：
     * - 裁剪末尾空白与多余的 '/'
     * - 反复移除尾部的 '/v1'（大小写不敏感），避免出现 '/v1/v1/...'
     */
    static String normalizeBaseUrl(String baseUrl) {
        if (!StringUtils.hasText(baseUrl)) {
            return baseUrl;
        }

        String normalized = stripTrailingSlash(baseUrl.trim());
        while (endsWithV1(normalized)) {
            normalized = stripTrailingSlash(normalized.substring(0, normalized.length() - 3));
        }

        return normalized;
    }

    private static boolean endsWithV1(String value) {
        if (value == null || value.length() < 3) {
            return false;
        }
        return value.toLowerCase().endsWith("/v1");
    }

    private static String stripTrailingSlash(String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }

        int end = value.length();
        while (end > 0 && value.charAt(end - 1) == '/') {
            end--;
        }

        return value.substring(0, end);
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}

