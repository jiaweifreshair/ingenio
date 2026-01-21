package com.ingenio.backend.service.g3.template;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * context.md 模板生成器
 *
 * 基于Manus工作流的上下文文件模板
 *
 * 文件用途：
 * - 存储已生成文件清单
 * - 维护import索引（避免import缺失）
 * - 保存类签名摘要（提供给后续Agent）
 *
 * 核心价值：
 * 解决G3引擎"依赖关系错误"问题，通过维护上下文确保：
 * 1. 每个Agent知道已生成了哪些文件
 * 2. 每个Agent知道可以import哪些类
 * 3. 每个Agent知道可以调用哪些方法
 *
 * @author Claude
 * @since 2025-01-08 (G3引擎任务规划增强)
 */
@Component
public class ContextTemplate {

    private static final DateTimeFormatter DATE_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

    /**
     * 生成初始上下文文件
     *
     * @param projectName 项目名称
     * @param basePackage 基础包名
     * @return Markdown格式的上下文文件
     */
    public String generateInitial(String projectName, String basePackage) {
        StringBuilder sb = new StringBuilder();

        sb.append("# 项目上下文: ").append(projectName).append("\n\n");
        sb.append("*最后更新: ").append(DATE_FORMATTER.format(Instant.now())).append("*\n\n");

        // 项目信息
        sb.append("## 项目信息\n\n");
        sb.append("- **基础包名**: ").append(basePackage).append("\n");
        sb.append("- **项目名称**: ").append(projectName).append("\n\n");

        // 已生成文件清单
        sb.append("## 已生成文件\n\n");
        sb.append("| 文件路径 | 类名 | 类型 | 状态 | 生成时间 |\n");
        sb.append("|---------|------|------|------|----------|\n");
        sb.append("| - | - | - | - | - |\n\n");

        // Import索引
        sb.append("## Import索引\n\n");
        sb.append("*Agent生成代码时，可以import以下类：*\n\n");
        sb.append("### Entity类\n");
        sb.append("```java\n");
        sb.append("// 待生成\n");
        sb.append("```\n\n");

        sb.append("### Mapper类\n");
        sb.append("```java\n");
        sb.append("// 待生成\n");
        sb.append("```\n\n");

        sb.append("### Service类\n");
        sb.append("```java\n");
        sb.append("// 待生成\n");
        sb.append("```\n\n");

        // 类签名摘要
        sb.append("## 类签名摘要\n\n");
        sb.append("*提供给后续Agent，了解可调用的方法：*\n\n");
        sb.append("### Entity签名\n");
        sb.append("*待生成*\n\n");

        sb.append("### Service签名\n");
        sb.append("*待生成*\n\n");

        // 依赖关系图
        sb.append("## 依赖关系图\n\n");
        sb.append("```\n");
        sb.append("Entity → Mapper → Service → Controller\n");
        sb.append("```\n");

        return sb.toString();
    }

    /**
     * 添加已生成文件
     *
     * @param currentContent 当前内容
     * @param filePath 文件路径
     * @param className 类名
     * @param type 类型（entity/mapper/service/controller）
     * @param status 状态
     * @return 更新后的内容
     */
    public String addGeneratedFile(String currentContent, String filePath, String className, String type, String status) {
        String timestamp = DATE_FORMATTER.format(Instant.now());
        String newRow = "| " + filePath + " | " + className + " | " + type + " | " + status + " | " + timestamp + " |\n";

        // 找到表格最后一行（在Import索引之前）
        int importPos = currentContent.indexOf("## Import索引");
        if (importPos > 0) {
            // 在表格末尾追加
            String beforeImport = currentContent.substring(0, importPos);
            String afterImport = currentContent.substring(importPos);

            // 移除占位符行
            beforeImport = beforeImport.replace("| - | - | - | - | - |\n", "");

            return beforeImport + newRow + "\n" + afterImport;
        }

        return currentContent;
    }

    /**
     * 更新Import索引
     *
     * @param currentContent 当前内容
     * @param type 类型（entity/mapper/service）
     * @param imports import语句列表
     * @return 更新后的内容
     */
    public String updateImportIndex(String currentContent, String type, List<String> imports) {
        String typeTitle = switch (type.toLowerCase()) {
            case "entity" -> "### Entity类";
            case "mapper" -> "### Mapper类";
            case "service" -> "### Service类";
            default -> "### " + type;
        };

        StringBuilder importBlock = new StringBuilder();
        importBlock.append(typeTitle).append("\n");
        importBlock.append("```java\n");
        for (String imp : imports) {
            importBlock.append("import ").append(imp).append(";\n");
        }
        importBlock.append("```\n");

        // 找到对应类型的import块并替换
        String pattern = typeTitle + "\\n```java\\n[\\s\\S]*?```\\n";
        return currentContent.replaceFirst(pattern, importBlock.toString());
    }

    /**
     * 添加类签名摘要
     *
     * @param currentContent 当前内容
     * @param type 类型（entity/service）
     * @param className 类名
     * @param signature 签名内容
     * @return 更新后的内容
     */
    public String addClassSignature(String currentContent, String type, String className, String signature) {
        String sectionTitle = switch (type.toLowerCase()) {
            case "entity" -> "### Entity签名";
            case "service" -> "### Service签名";
            default -> "### " + type + "签名";
        };

        String newSignature = "\n#### " + className + "\n```java\n" + signature + "\n```\n";

        // 找到对应section并追加
        int sectionPos = currentContent.indexOf(sectionTitle);
        if (sectionPos > 0) {
            // 找到下一个section或文件末尾
            int nextSectionPos = currentContent.indexOf("\n### ", sectionPos + sectionTitle.length());
            int dependencyPos = currentContent.indexOf("## 依赖关系图");

            int insertPos = nextSectionPos > 0 ? nextSectionPos : (dependencyPos > 0 ? dependencyPos : currentContent.length());

            // 移除"*待生成*"占位符
            String before = currentContent.substring(0, insertPos).replace("*待生成*\n", "");
            String after = currentContent.substring(insertPos);

            return before + newSignature + after;
        }

        return currentContent + newSignature;
    }

    /**
     * 更新依赖关系图
     *
     * @param currentContent 当前内容
     * @param dependencyGraph 依赖图（ASCII art格式）
     * @return 更新后的内容
     */
    public String updateDependencyGraph(String currentContent, String dependencyGraph) {
        String pattern = "## 依赖关系图\\n\\n```\\n[\\s\\S]*?```";
        String replacement = "## 依赖关系图\n\n```\n" + dependencyGraph + "\n```";

        return currentContent.replaceFirst(pattern, replacement);
    }

    /**
     * 生成精简上下文（供Agent使用）
     *
     * 只包含必要信息，减少Token消耗
     *
     * @param currentContent 完整上下文
     * @return 精简上下文
     */
    public String generateCompactContext(String currentContent) {
        StringBuilder compact = new StringBuilder();

        compact.append("# 可用类索引\n\n");

        // 提取Import索引部分
        int importStart = currentContent.indexOf("## Import索引");
        int importEnd = currentContent.indexOf("## 类签名摘要");

        if (importStart > 0 && importEnd > importStart) {
            compact.append(currentContent, importStart, importEnd);
        }

        // 提取关键签名
        compact.append("\n## 关键方法\n\n");

        int signatureStart = currentContent.indexOf("## 类签名摘要");
        int signatureEnd = currentContent.indexOf("## 依赖关系图");

        if (signatureStart > 0 && signatureEnd > signatureStart) {
            compact.append(currentContent, signatureStart, signatureEnd);
        }

        return compact.toString();
    }
}
