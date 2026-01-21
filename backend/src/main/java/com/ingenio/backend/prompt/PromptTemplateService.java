package com.ingenio.backend.prompt;

import com.ingenio.backend.config.PromptProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 提示词模板加载与缓存服务。
 *
 * <p>为什么需要该组件：</p>
 * <ul>
 *   <li>Agent 内嵌超长提示词会导致类文件臃肿、Diff 噪声大、难以做版本化管理；</li>
 *   <li>提示词属于“运行资产”，更适合放在 resources 或外部文件中进行迭代；</li>
 *   <li>统一加载与缓存，避免每次调用都重复 IO。</li>
 * </ul>
 *
 * <p>支持的路径协议：</p>
 * <ul>
 *   <li>classpath:prompts/...（默认）</li>
 *   <li>file:/abs/path/...（部署环境可外置）</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class PromptTemplateService {

    private final PromptProperties promptProperties;
    private final ResourceLoader resourceLoader;

    /**
     * 提示词内容缓存（key=资源路径）。
     */
    private final Map<String, String> cache = new ConcurrentHashMap<>();

    public String architectContractTemplate() {
        return load(promptProperties.getArchitect().getContract());
    }

    public String architectSchemaTemplate() {
        return load(promptProperties.getArchitect().getSchema());
    }

    public String coderStandardsTemplate() {
        return load(promptProperties.getCoder().getStandards());
    }

    public String coderEntityTemplate() {
        return load(promptProperties.getCoder().getEntity());
    }

    public String coderMapperTemplate() {
        return load(promptProperties.getCoder().getMapper());
    }

    public String coderDtoTemplate() {
        return load(promptProperties.getCoder().getDto());
    }

    public String coderServiceTemplate() {
        return load(promptProperties.getCoder().getService());
    }

    public String coderControllerTemplate() {
        return load(promptProperties.getCoder().getController());
    }

    public String coachAnalysisTemplate() {
        return load(promptProperties.getCoach().getAnalysis());
    }

    /**
     * 读取修复计划提示词模板。
     *
     * 是什么：Coach 修复计划阶段的提示词模板读取入口。
     * 做什么：从配置路径加载计划模板内容。
     * 为什么：让修复计划可配置、可迭代，避免硬编码在代码中。
     */
    public String coachPlanTemplate() {
        return load(promptProperties.getCoach().getPlan());
    }

    public String coachFixTemplate() {
        return load(promptProperties.getCoach().getFix());
    }

    public String coachFixPomXmlTemplate() {
        return load(promptProperties.getCoach().getFixPomXml());
    }

    private String load(String location) {
        if (location == null || location.isBlank()) {
            throw new IllegalStateException("提示词路径不能为空");
        }

        return cache.computeIfAbsent(location, this::readResourceAsString);
    }

    private String readResourceAsString(String location) {
        Resource resource = resourceLoader.getResource(location);
        if (!resource.exists()) {
            throw new IllegalStateException("提示词文件不存在: " + location);
        }

        try (var in = resource.getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("读取提示词文件失败: " + location + "，原因: " + e.getMessage(), e);
        }
    }
}
