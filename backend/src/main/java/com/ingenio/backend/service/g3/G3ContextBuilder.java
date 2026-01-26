package com.ingenio.backend.service.g3;

import com.ingenio.backend.entity.g3.G3ArtifactEntity;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.ingenio.backend.entity.g3.AnalysisContextSummary;
import com.ingenio.backend.entity.g3.G3JobEntity;
import com.ingenio.backend.mapper.g3.G3JobMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fasterxml.jackson.databind.DeserializationFeature;

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
 * v2.2.0 增强（M2）：
 * - 智能压缩：大型项目（>20文件）自动切换为摘要模式
 * - Token控制：按 maxTokens 参数控制上下文大小
 * - 渐进式压缩：小项目完整上下文 → 中项目精简签名 → 大项目统计摘要
 *
 * @author Claude
 * @since 2025-01-14
 */
@Service
@RequiredArgsConstructor
public class G3ContextBuilder {

    private static final Logger log = LoggerFactory.getLogger(G3ContextBuilder.class);

    private final G3PlanningFileService planningFileService;
    private final G3JobMapper jobMapper;

    /**
     * 任务类型常量
     */
    public static final String TASK_TYPE_ENTITY = "entity";
    public static final String TASK_TYPE_MAPPER = "mapper";
    public static final String TASK_TYPE_DTO = "dto";
    public static final String TASK_TYPE_SERVICE = "service";
    public static final String TASK_TYPE_CONTROLLER = "controller";

    /**
     * 压缩策略阈值（M2）
     */
    /** 小型项目阈值：文件数 <= 此值时使用完整上下文 */
    private static final int SMALL_PROJECT_THRESHOLD = 10;
    /** 中型项目阈值：文件数 <= 此值时使用精简签名模式 */
    private static final int MEDIUM_PROJECT_THRESHOLD = 30;
    /** 默认最大 Token 数（约等于字符数的 1/4） */
    private static final int DEFAULT_MAX_TOKENS = 4000;
    /** 每个类签名的最大字符数 */
    private static final int MAX_SIGNATURE_CHARS = 300;

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
        StringBuilder sb = new StringBuilder();

        // 1. 注入分析上下文（M8 Enhanced）
        try {
            G3JobEntity job = jobMapper.selectById(jobId);
            if (job != null && job.getAnalysisContextJson() != null && !job.getAnalysisContextJson().isEmpty()) {
                ObjectMapper mapper = new ObjectMapper();
                mapper.registerModule(new JavaTimeModule());
                mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

                AnalysisContextSummary summary = mapper.convertValue(
                        job.getAnalysisContextJson(),
                        AnalysisContextSummary.class);

                sb.append(summary.formatAsMarkdown());
                sb.append("\n\n");
                log.debug("注入分析上下文摘要 ({})", summary.getCompressionLevel());
            }
        } catch (Exception e) {
            log.warn("构建分析上下文失败: {}", e.getMessage());
        }

        // 2. 基础上下文（复用规划文件）
        // 它包含了 context.md 中的核心信息（文件列表、关键签名）
        sb.append(planningFileService.getCompactContext(jobId));

        return sb.toString();
    }

    /**
     * 构建智能压缩上下文（M2 新增）
     *
     * 根据项目规模自动选择压缩策略：
     * - 小型项目（<=10文件）：完整上下文
     * - 中型项目（<=30文件）：精简签名模式
     * - 大型项目（>30文件）：统计摘要模式
     *
     * @param jobId     任务ID
     * @param artifacts 已生成的产物列表
     * @param maxTokens 最大Token数（0表示使用默认值）
     * @return 压缩后的上下文文本
     */
    public String buildCompactContext(UUID jobId, List<G3ArtifactEntity> artifacts, int maxTokens) {
        int effectiveMaxTokens = maxTokens > 0 ? maxTokens : DEFAULT_MAX_TOKENS;
        int maxChars = effectiveMaxTokens * 4; // 粗略估算：1 token ≈ 4 字符

        int fileCount = artifacts != null ? artifacts.size() : 0;
        log.debug("构建压缩上下文: jobId={}, fileCount={}, maxTokens={}", jobId, fileCount, effectiveMaxTokens);

        // 根据项目规模选择压缩策略
        if (fileCount <= SMALL_PROJECT_THRESHOLD) {
            // 小型项目：完整上下文
            return buildFullContext(jobId, artifacts, maxChars);
        } else if (fileCount <= MEDIUM_PROJECT_THRESHOLD) {
            // 中型项目：精简签名模式
            return buildSignatureContext(jobId, artifacts, maxChars);
        } else {
            // 大型项目：统计摘要模式
            return buildSummaryContext(jobId, artifacts, maxChars);
        }
    }

    /**
     * 构建完整上下文（小型项目）
     *
     * 包含所有类的完整签名信息
     */
    private String buildFullContext(UUID jobId, List<G3ArtifactEntity> artifacts, int maxChars) {
        StringBuilder sb = new StringBuilder();
        sb.append("## 项目上下文（完整模式）\n\n");

        // 添加基础上下文
        String baseContext = planningFileService.getCompactContext(jobId);
        sb.append(baseContext);

        // 添加所有类的签名
        if (artifacts != null && !artifacts.isEmpty()) {
            sb.append("\n## 已生成类签名\n\n");
            for (G3ArtifactEntity artifact : artifacts) {
                if (artifact.getFileName() == null || !artifact.getFileName().endsWith(".java")) {
                    continue;
                }
                String signature = extractClassSignature(artifact.getContent());
                if (!signature.isBlank()) {
                    sb.append("### ").append(artifact.getFileName().replace(".java", "")).append("\n");
                    sb.append("```java\n").append(signature).append("\n```\n\n");
                }

                // 检查长度限制
                if (sb.length() > maxChars) {
                    sb.append("\n... (已达到长度限制，后续内容省略)\n");
                    break;
                }
            }
        }

        return truncateToLimit(sb.toString(), maxChars);
    }

    /**
     * 构建精简签名上下文（中型项目）
     *
     * 只保留类名和主要方法签名，省略方法参数细节
     */
    private String buildSignatureContext(UUID jobId, List<G3ArtifactEntity> artifacts, int maxChars) {
        StringBuilder sb = new StringBuilder();
        sb.append("## 项目上下文（精简模式）\n\n");

        // 按类型分组统计
        Map<String, List<G3ArtifactEntity>> byType = groupArtifactsByType(artifacts);

        sb.append("### 文件统计\n");
        for (Map.Entry<String, List<G3ArtifactEntity>> entry : byType.entrySet()) {
            sb.append("- ").append(entry.getKey()).append(": ").append(entry.getValue().size()).append(" 个\n");
        }
        sb.append("\n");

        // 每种类型只展示前5个类的精简签名
        for (Map.Entry<String, List<G3ArtifactEntity>> entry : byType.entrySet()) {
            String type = entry.getKey();
            List<G3ArtifactEntity> typeArtifacts = entry.getValue();

            sb.append("### ").append(type.toUpperCase()).append(" 类\n");

            int count = 0;
            for (G3ArtifactEntity artifact : typeArtifacts) {
                if (count >= 5) {
                    sb.append("- ... (还有 ").append(typeArtifacts.size() - 5).append(" 个)\n");
                    break;
                }

                String className = artifact.getFileName().replace(".java", "");
                String packageName = extractPackageFromContent(artifact.getContent());
                String fqn = packageName != null ? packageName + "." + className : className;

                // 提取主要方法（最多3个）
                List<String> methods = extractMainMethods(artifact.getContent(), 3);
                sb.append("- `").append(fqn).append("`");
                if (!methods.isEmpty()) {
                    sb.append(": ").append(String.join(", ", methods));
                }
                sb.append("\n");

                count++;
            }
            sb.append("\n");

            if (sb.length() > maxChars) {
                break;
            }
        }

        return truncateToLimit(sb.toString(), maxChars);
    }

    /**
     * 构建统计摘要上下文（大型项目）
     *
     * 只保留类型统计和关键类列表，大幅减少 Token 消耗
     */
    private String buildSummaryContext(UUID jobId, List<G3ArtifactEntity> artifacts, int maxChars) {
        StringBuilder sb = new StringBuilder();
        sb.append("## 项目上下文（摘要模式）\n\n");
        sb.append("> 注意：项目规模较大（").append(artifacts.size()).append(" 个文件），");
        sb.append("已启用摘要模式以减少 Token 消耗。\n\n");

        // 按类型分组统计
        Map<String, List<G3ArtifactEntity>> byType = groupArtifactsByType(artifacts);

        sb.append("### 文件统计\n");
        sb.append("| 类型 | 数量 | 示例类 |\n");
        sb.append("|------|------|--------|\n");

        for (Map.Entry<String, List<G3ArtifactEntity>> entry : byType.entrySet()) {
            String type = entry.getKey();
            List<G3ArtifactEntity> typeArtifacts = entry.getValue();
            int count = typeArtifacts.size();

            // 取前3个作为示例
            String examples = typeArtifacts.stream()
                    .limit(3)
                    .map(a -> a.getFileName().replace(".java", ""))
                    .collect(Collectors.joining(", "));

            if (count > 3) {
                examples += " ...";
            }

            sb.append("| ").append(type).append(" | ").append(count).append(" | ").append(examples).append(" |\n");
        }
        sb.append("\n");

        // 添加关键类的全限定名索引（便于 import）
        sb.append("### 常用类索引（供 import 参考）\n");
        sb.append("```\n");

        int indexCount = 0;
        for (G3ArtifactEntity artifact : artifacts) {
            if (indexCount >= 20) {
                sb.append("... (还有 ").append(artifacts.size() - 20).append(" 个类)\n");
                break;
            }

            String className = artifact.getFileName().replace(".java", "");
            String packageName = extractPackageFromContent(artifact.getContent());
            if (packageName != null) {
                sb.append(packageName).append(".").append(className).append("\n");
                indexCount++;
            }
        }
        sb.append("```\n");

        return truncateToLimit(sb.toString(), maxChars);
    }

    /**
     * 按类型分组产物
     */
    private Map<String, List<G3ArtifactEntity>> groupArtifactsByType(List<G3ArtifactEntity> artifacts) {
        if (artifacts == null || artifacts.isEmpty()) {
            return java.util.Collections.emptyMap();
        }

        return artifacts.stream()
                .filter(a -> a.getFileName() != null && a.getFileName().endsWith(".java"))
                .collect(Collectors.groupingBy(this::inferArtifactType));
    }

    /**
     * 推断产物类型
     */
    private String inferArtifactType(G3ArtifactEntity artifact) {
        String path = artifact.getFilePath();
        String name = artifact.getFileName();

        if (path == null)
            path = "";
        if (name == null)
            name = "";

        String lower = (path + "/" + name).toLowerCase();

        if (lower.contains("/entity/") || name.endsWith("Entity.java"))
            return "Entity";
        if (lower.contains("/mapper/") || name.endsWith("Mapper.java"))
            return "Mapper";
        if (lower.contains("/dto/") || name.endsWith("DTO.java") || name.endsWith("Dto.java"))
            return "DTO";
        if (lower.contains("/service/") || name.endsWith("Service.java"))
            return "Service";
        if (lower.contains("/controller/") || name.endsWith("Controller.java"))
            return "Controller";
        if (lower.contains("/config/"))
            return "Config";
        if (lower.contains("/util/") || lower.contains("/utils/"))
            return "Util";

        return "Other";
    }

    /**
     * 提取主要方法签名（精简版）
     *
     * @param content    代码内容
     * @param maxMethods 最多提取的方法数
     * @return 方法签名列表（如 "create()", "update()", "delete()"）
     */
    private List<String> extractMainMethods(String content, int maxMethods) {
        List<String> methods = new java.util.ArrayList<>();
        if (content == null)
            return methods;

        // 匹配 public 方法名
        Pattern methodPattern = Pattern.compile("public\\s+\\S+\\s+(\\w+)\\s*\\(");
        Matcher matcher = methodPattern.matcher(content);

        while (matcher.find() && methods.size() < maxMethods) {
            String methodName = matcher.group(1);
            // 排除构造函数和常见 getter/setter
            if (!methodName.startsWith("get") && !methodName.startsWith("set") && !methodName.startsWith("is")) {
                methods.add(methodName + "()");
            }
        }

        return methods;
    }

    /**
     * 截断到指定长度限制
     */
    private String truncateToLimit(String content, int maxChars) {
        if (content == null)
            return "";
        if (content.length() <= maxChars)
            return content;

        return content.substring(0, maxChars) + "\n\n... (已截断，原长度: " + content.length() + " 字符)\n";
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
     * @param jobId         任务ID
     * @param taskType      任务类型 (entity/mapper/dto/service/controller)
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
            if (fileName == null)
                continue;

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
            if (content == null || content.isBlank())
                continue;

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
     * @param taskType    任务类型
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
            default -> fullContext; // entity/dto 等使用完整上下文
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
     * @param context      原始上下文
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
        if (content == null)
            return null;

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
        if (content == null)
            return "";

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
            if (ch == c)
                count++;
        }
        return count;
    }
}
