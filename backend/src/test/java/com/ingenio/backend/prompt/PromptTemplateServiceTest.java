package com.ingenio.backend.prompt;

import com.ingenio.backend.config.PromptProperties;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * PromptTemplateService 单元测试
 *
 * <p>目标：</p>
 * <ul>
 *   <li>确保默认 classpath 提示词文件存在且可读取；</li>
 *   <li>避免重构/清理时误删 prompts/* 资源导致运行期才暴雷。</li>
 * </ul>
 */
class PromptTemplateServiceTest {

    @Test
    void shouldLoadDefaultPromptTemplatesFromClasspath() {
        PromptProperties properties = new PromptProperties();
        PromptTemplateService service = new PromptTemplateService(properties, new DefaultResourceLoader());

        assertThat(service.architectContractTemplate()).contains("OpenAPI").contains("%s");
        assertThat(service.architectSchemaTemplate()).contains("PostgreSQL").contains("%s");

        assertThat(service.coderStandardsTemplate()).contains("SOLID");
        assertThat(service.coderEntityTemplate()).contains("实体类").contains("%s");
        assertThat(service.coderMapperTemplate()).contains("Mapper").contains("%s");
        assertThat(service.coderDtoTemplate()).contains("DTO").contains("%s");
        assertThat(service.coderServiceTemplate()).contains("Service").contains("%s");
        assertThat(service.coderControllerTemplate()).contains("Controller").contains("%s");

        assertThat(service.coachAnalysisTemplate()).contains("错误分析").contains("%s");
        assertThat(service.coachFixTemplate()).contains("修复").contains("%s");
        assertThat(service.coachFixPomXmlTemplate()).contains("pom.xml").contains("%s");
    }
}

