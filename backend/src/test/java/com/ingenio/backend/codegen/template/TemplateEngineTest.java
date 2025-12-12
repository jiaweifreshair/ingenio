package com.ingenio.backend.codegen.template;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

/**
 * TemplateEngine单元测试
 *
 * <p>测试Freemarker模板引擎的核心功能</p>
 *
 * @author Justin
 * @since 2025-11-17 V2.0 Phase 3.1: 模板引擎基础设施测试
 */
class TemplateEngineTest {

    private TemplateEngine templateEngine;

    @BeforeEach
    void setUp() {
        templateEngine = new TemplateEngine();
    }

    /**
     * 测试：成功渲染简单模板
     *
     * <p>验证模板引擎能够正确加载和渲染TestTemplate.ftl</p>
     */
    @Test
    void shouldRenderSimpleTemplateSuccessfully() {
        // Given: 准备测试数据
        Map<String, Object> dataModel = Map.of(
                "packageName", "com.example.entity",
                "className", "User",
                "author", "Justin",
                "date", "2025-11-17",
                "fields", List.of(
                        Map.of("name", "id", "type", "UUID", "description", "用户ID"),
                        Map.of("name", "email", "type", "String", "description", "邮箱")
                )
        );

        // When: 渲染模板
        String renderedCode = templateEngine.render("TestTemplate.ftl", dataModel);

        // Then: 验证结果
        assertThat(renderedCode).isNotNull();
        assertThat(renderedCode).contains("package com.example.entity;");
        assertThat(renderedCode).contains("public class User {");
        assertThat(renderedCode).contains("private UUID id;");
        assertThat(renderedCode).contains("private String email;");
        assertThat(renderedCode).contains("用户ID");
        assertThat(renderedCode).contains("邮箱");
        assertThat(renderedCode).contains("@author Justin");
    }

    /**
     * 测试：模板不存在时抛出异常
     */
    @Test
    void shouldThrowExceptionWhenTemplateNotFound() {
        // Given: 不存在的模板名称
        String templateName = "NonExistentTemplate.ftl";
        Map<String, Object> dataModel = Map.of();

        // When & Then: 验证抛出异常
        assertThatThrownBy(() -> templateEngine.render(templateName, dataModel))
                .isInstanceOf(TemplateEngine.TemplateRenderException.class)
                .hasMessageContaining("模板加载失败");
    }

    /**
     * 测试：模板存在性检查
     */
    @Test
    void shouldCheckTemplateExistence() {
        // When & Then: 验证模板存在性检查
        assertThat(templateEngine.templateExists("TestTemplate.ftl")).isTrue();
        assertThat(templateEngine.templateExists("NonExistent.ftl")).isFalse();
    }

    /**
     * 测试：空数据模型渲染
     */
    @Test
    void shouldHandleEmptyDataModel() {
        // Given: 空数据模型（会导致模板渲染错误）
        Map<String, Object> emptyDataModel = Map.of();

        // When & Then: 验证抛出异常（因为模板需要必需变量）
        assertThatThrownBy(() -> templateEngine.render("TestTemplate.ftl", emptyDataModel))
                .isInstanceOf(TemplateEngine.TemplateRenderException.class)
                .hasMessageContaining("模板渲染失败");
    }
}
