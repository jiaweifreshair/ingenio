package com.ingenio.backend.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * OpenAiBaseUrlEnvironmentPostProcessor 单元测试
 *
 * 目标：
 * - 防止 spring.ai.openai.base-url 误配为带 /v1 的形式，导致最终请求变成 /v1/v1/...（404）
 */
@DisplayName("OpenAiBaseUrlEnvironmentPostProcessor - base-url 规范化")
class OpenAiBaseUrlEnvironmentPostProcessorTest {

    @Test
    @DisplayName("当 base-url 以 /v1 结尾时应自动裁剪")
    void shouldStripTrailingV1() {
        MockEnvironment environment = new MockEnvironment();
        environment.setProperty("spring.ai.openai.base-url", "https://api.qnaigc.com/v1");

        new OpenAiBaseUrlEnvironmentPostProcessor()
                .postProcessEnvironment(environment, new SpringApplication(OpenAiBaseUrlEnvironmentPostProcessorTest.class));

        assertThat(environment.getProperty("spring.ai.openai.base-url")).isEqualTo("https://api.qnaigc.com");
    }

    @Test
    @DisplayName("当 base-url 以 /v1/ 结尾时应裁剪并移除多余斜杠")
    void shouldStripTrailingV1AndSlashes() {
        MockEnvironment environment = new MockEnvironment();
        environment.setProperty("spring.ai.openai.base-url", "  https://api.qnaigc.com/v1/  ");

        new OpenAiBaseUrlEnvironmentPostProcessor()
                .postProcessEnvironment(environment, new SpringApplication(OpenAiBaseUrlEnvironmentPostProcessorTest.class));

        assertThat(environment.getProperty("spring.ai.openai.base-url")).isEqualTo("https://api.qnaigc.com");
    }

    @Test
    @DisplayName("当 base-url 已规范化时不应改变")
    void shouldKeepBaseUrlWhenAlreadyNormalized() {
        MockEnvironment environment = new MockEnvironment();
        environment.setProperty("spring.ai.openai.base-url", "https://api.qnaigc.com");

        new OpenAiBaseUrlEnvironmentPostProcessor()
                .postProcessEnvironment(environment, new SpringApplication(OpenAiBaseUrlEnvironmentPostProcessorTest.class));

        assertThat(environment.getProperty("spring.ai.openai.base-url")).isEqualTo("https://api.qnaigc.com");
    }

    @Test
    @DisplayName("当 base-url 误写为 /v1/v1 时应反复裁剪直到规范化")
    void shouldStripRepeatedV1Suffix() {
        MockEnvironment environment = new MockEnvironment();
        environment.setProperty("spring.ai.openai.base-url", "https://api.qnaigc.com/v1/v1");

        new OpenAiBaseUrlEnvironmentPostProcessor()
                .postProcessEnvironment(environment, new SpringApplication(OpenAiBaseUrlEnvironmentPostProcessorTest.class));

        assertThat(environment.getProperty("spring.ai.openai.base-url")).isEqualTo("https://api.qnaigc.com");
    }
}

