package com.ingenio.backend.service.g3;

import com.ingenio.backend.entity.g3.G3ArtifactEntity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * G3上下文构建器
 *
 * 负责为Agent构建执行上下文，包括：
 * 1. 已生成的文件清单
 * 2. 相关类的签名摘要
 * 3. 项目的基础配置信息
 *
 * 核心目标是解决Agent之间的"信息孤岛"问题，确保后续生成的代码
 * 能正确引用前序生成的类。
 *
 * v2.1.0 增强：
 * - 按任务类型过滤上下文，减少无关信息干扰
 * - 支持注入已生成类清单，避免重复生成或引用不存在的类
 *
 * @author Claude
 * @since 2025-01-14
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class G3ContextBuilder {

    private final G3PlanningFileService planningFileService;

    /**
     * 任务类型常量
     */
    public static final String TASK_TYPE_ENTITY = "entity";
    public static final String TASK_TYPE_MAPPER = "mapper";
    public static final String TASK_TYPE_DTO = "dto";
    public static final String TASK_TYPE_SERVICE = "service";
    public static final String TASK_TYPE_CONTROLLER = "controller";

    /**
     * 构建全局上下文
     *
     * 适用于不需要特定任务聚焦的场景，提供项目整体概览
     *
     * @param jobId 任务ID
     * @return 上下文文本 (Markdown格式)
     */
    public String buildGlobalContext(UUID jobId) {
        log.debug("构建全局上下文: jobId={}", jobId);
        // 直接复用规划文件服务提供的精简上下文
        // 它包含了 context.md 中的核心信息（文件列表、关键签名）
        return planningFileService.getCompactContext(jobId);
    }

    /**
     * 为特定任务构建上下文（按任务类型过滤）
     *
     * 不同任务类型需要的上下文不同：
     * - mapper: 只需要 Entity 签名
     * - dto: 需要 Entity 签名 + OpenAPI契约
     * - service: 需要 Entity + Mapper + DTO 签名
     * - controller: 需要 Service + DTO 签名
     *
     * @param jobId 任务ID
     * @param taskType 任务类型 (entity/mapper/dto/service/controller)
     * @param relatedEntity 相关实体名（可选）
     * @return 上下文文本
     */
    public String buildTaskContext(UUID jobId, String taskType, String relatedEntity) {
        log.debug("构建任务上下文: jobId={}, type={}, entity={}", jobId, taskType, relatedEntity);

        String baseContext = planningFileService.getCompactContext(jobId);

        // 按任务类型过滤上下文
        String filteredContext = filterContextByTaskType(baseContext, taskType);

        StringBuilder sb = new StringBuilder();
        sb.append("## 当前任务环境\n");
        sb.append("- 任务类型: ").append(taskType).append("\n");
        if (relatedEntity != null && !relatedEntity.isBlank()) {
            sb.append("- 目标实体: ").append(relatedEntity).append("\n");
        }
        sb.append("\n");

        sb.append(filteredContext);

        return sb.toString();
    }

    /**
     * 构建已生成类清单
     *
     * 用于在生成后续代码时，告知 AI 哪些类已经生成，避免：
     * 1. 重复生成同名类
     * 2. 引用未生成的类
     *
     * @param artifacts 已生成的产物列表
     * @param layerType 层类型 (entity/mapper/dto/service/controller)
     * @return 已生成类清单文本
     */
    public String buildGeneratedClassList(List<G3ArtifactEntity> artifacts, String layerType) {
        if (artifacts == null || artifacts.isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("## 已生成的 ").append(layerType.toUpperCase()).append(" 类\n\n");
        sb.append("以下类已生成，请在后续代码中正确引用（禁止重复定义）：\n\n");

        for (G3ArtifactEntity artifact : artifacts) {
            String fileName = artifact.getFileName();
            if (fileName == null) continue;

            String className = fileName.replace(".java", "");
            String packageName = extractPackageFromContent(artifact.getContent());

            if (packageName != null && !packageName.isBlank()) {
                sb.append("- `").append(packageName).append(".").append(className).append("`\n");
            } else {
                sb.append("- `").append(className).append("`\n");
            }
        }

        sb.append("\n");
        return sb.toString();
    }

    /**
     * 构建类签名摘要
     *
     * 提取类的关键签名信息（类名、公共方法），供后续 Agent 参考
     *
     * @param artifacts 产物列表
     * @param layerType 层类型
     * @return 签名摘要文本
     */
    public String buildClassSignatureSummary(List<G3ArtifactEntity> artifacts, String layerType) {
        if (artifacts == null || artifacts.isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("## ").append(layerType.toUpperCase()).append(" 类签名摘要\n\n");

        for (G3ArtifactEntity artifact : artifacts) {
            String content = artifact.getContent();
            if (content == null || content.isBlank()) continue;

            String className = artifact.getFileName().replace(".java", "");
            sb.append("### ").append(className).append("\n");
            sb.append("```java\n");

            // 提取类声明和公共方法签名
            String signature = extractClassSignature(content);
            sb.append(signature);

            sb.append("\n```\n\n");
        }

        return sb.toString();
    }

    /**
     * 按任务类型过滤上下文
     *
     * @param fullContext 完整上下文
     * @param taskType 任务类型
     * @return 过滤后的上下文
     */
    private String filterContextByTaskType(String fullContext, String taskType) {
        if (fullContext == null || fullContext.isBlank()) {
            return "";
        }

        // 目前采用保守策略：返回完整上下文
        // 当 context.md 变大时，可以在这里实现更激进的过滤
        // 例如：生成 Controller 时只保留 Service 和 DTO 部分

        return switch (taskType) {
            case TASK_TYPE_MAPPER -> filterForMapper(fullContext);
            case TASK_TYPE_SERVICE -> filterForService(fullContext);
            case TASK_TYPE_CONTROLLER -> filterForController(fullContext);
            default -> fullContext;  // entity/dto 等使用完整上下文
        };
    }

    /**
     * 为 Mapper 生成过滤上下文（只需要 Entity）
     */
    private String filterForMapper(String context) {
        // Mapper 只需要知道 Entity 的结构
        // 保留：Entity 相关部分
        // 移除：Service、Controller 部分（如果有）
        return filterSections(context, List.of("entity", "import"));
    }

    /**
     * 为 Service 生成过滤上下文（需要 Entity + Mapper + DTO）
     */
    private String filterForService(String context) {
        // Service 需要知道 Entity、Mapper、DTO
        return filterSections(context, List.of("entity", "mapper", "dto", "import"));
    }

    /**
     * 为 Controller 生成过滤上下文（需要 Service + DTO）
     */
    private String filterForController(String context) {
        // Controller 主要需要 Service 接口和 DTO
        return filterSections(context, List.of("service", "dto", "import"));
    }

    /**
     * 按 section 关键词过滤上下文
     *
     * @param context 原始上下文
     * @param keepKeywords 保留的关键词列表
     * @return 过滤后的上下文
     */
    private String filterSections(String context, List<String> keepKeywords) {
        // 简单实现：如果上下文不太大（<8000字符），直接返回完整内容
        if (context.length() < 8000) {
            return context;
        }

        // 上下文较大时，按 section 过滤
        StringBuilder filtered = new StringBuilder();
        String[] sections = context.split("(?=## )");

        for (String section : sections) {
            String lowerSection = section.toLowerCase();
            boolean keep = keepKeywords.stream().anyMatch(lowerSection::contains);
            if (keep) {
                filtered.append(section);
            }
        }

        return filtered.length() > 0 ? filtered.toString() : context;
    }

    /**
     * 从代码内容中提取包名
     */
    private String extractPackageFromContent(String content) {
        if (content == null) return null;

        Pattern packagePattern = Pattern.compile("^package\\s+([\\w.]+);", Pattern.MULTILINE);
        Matcher matcher = packagePattern.matcher(content);

        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    /**
     * 提取类签名（类声明 + 公共方法）
     */
    private String extractClassSignature(String content) {
        if (content == null) return "";

        StringBuilder signature = new StringBuilder();
        String[] lines = content.split("\n");
        boolean inClass = false;
        int braceCount = 0;

        for (String line : lines) {
            String trimmed = line.trim();

            // 跳过 import 和 package
            if (trimmed.startsWith("import ") || trimmed.startsWith("package ")) {
                continue;
            }

            // 捕获类/接口声明
            if (trimmed.matches("^(public\\s+)?(class|interface|enum)\\s+.*")) {
                signature.append(trimmed).append("\n");
                inClass = true;
                braceCount += countChar(trimmed, '{') - countChar(trimmed, '}');
                continue;
            }

            // 在类内部，捕获公共方法签名
            if (inClass && trimmed.startsWith("public ") && !trimmed.contains("class ")) {
                // 只保留方法签名，不要方法体
                if (trimmed.contains("{")) {
                    signature.append("    ").append(trimmed.split("\\{")[0].trim()).append(";\n");
                } else if (trimmed.endsWith(";")) {
                    signature.append("    ").append(trimmed).append("\n");
                }
            }

            braceCount += countChar(trimmed, '{') - countChar(trimmed, '}');
            if (inClass && braceCount <= 0) {
                break;
            }
        }

        // 限制签名长度
        String result = signature.toString();
        if (result.length() > 500) {
            result = result.substring(0, 500) + "\n    // ... (更多方法省略)\n";
        }

        return result;
    }

    /**
     * 统计字符出现次数
     */
    private int countChar(String str, char c) {
        int count = 0;
        for (char ch : str.toCharArray()) {
            if (ch == c) count++;
        }
        return count;
    }
}
