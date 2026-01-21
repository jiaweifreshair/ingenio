package com.ingenio.backend.service.g3.template;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * notes.md 模板生成器
 *
 * 基于Manus工作流的笔记文件模板
 *
 * 文件用途：
 * - 存储架构设计结果
 * - 记录API定义
 * - 保存研究和发现
 *
 * @author Claude
 * @since 2025-01-08 (G3引擎任务规划增强)
 */
@Component
public class NotesTemplate {

    private static final DateTimeFormatter DATE_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

    /**
     * 生成初始笔记文件
     *
     * @param projectName 项目名称
     * @return Markdown格式的笔记文件
     */
    public String generateInitial(String projectName) {
        StringBuilder sb = new StringBuilder();

        sb.append("# 笔记: ").append(projectName).append("\n\n");
        sb.append("*创建时间: ").append(DATE_FORMATTER.format(Instant.now())).append("*\n\n");

        // 架构设计
        sb.append("## 架构设计\n\n");
        sb.append("### 技术栈\n");
        sb.append("- **后端框架**: Spring Boot 3.4\n");
        sb.append("- **ORM**: MyBatis-Plus\n");
        sb.append("- **数据库**: PostgreSQL 15\n");
        sb.append("- **缓存**: Redis 7\n\n");

        // 实体设计
        sb.append("## 实体设计\n\n");
        sb.append("*待架构师Agent分析后填充*\n\n");

        // API设计
        sb.append("## API设计\n\n");
        sb.append("*待架构师Agent分析后填充*\n\n");

        // 能力集成
        sb.append("## 能力集成笔记\n\n");
        sb.append("*待能力集成阶段填充*\n\n");

        // 问题与解决方案
        sb.append("## 问题与解决方案\n\n");
        sb.append("| 问题 | 解决方案 | 参考 |\n");
        sb.append("|------|----------|------|\n");
        sb.append("| - | - | - |\n");

        return sb.toString();
    }

    /**
     * 添加实体设计
     *
     * @param currentContent 当前内容
     * @param entities 实体列表
     * @return 更新后的内容
     */
    public String addEntityDesign(String currentContent, List<Map<String, Object>> entities) {
        StringBuilder entitySection = new StringBuilder();
        entitySection.append("## 实体设计\n\n");

        for (Map<String, Object> entity : entities) {
            String name = (String) entity.get("name");
            String description = (String) entity.get("description");
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> attributes = (List<Map<String, Object>>) entity.get("attributes");

            entitySection.append("### ").append(name).append("\n");
            entitySection.append(description != null ? description : "").append("\n\n");

            if (attributes != null && !attributes.isEmpty()) {
                entitySection.append("| 字段 | 类型 | 必填 | 说明 |\n");
                entitySection.append("|------|------|------|------|\n");

                for (Map<String, Object> attr : attributes) {
                    entitySection.append("| ").append(attr.get("name")).append(" | ");
                    entitySection.append(attr.get("type")).append(" | ");
                    entitySection.append(Boolean.TRUE.equals(attr.get("required")) ? "是" : "否").append(" | ");
                    entitySection.append(attr.getOrDefault("description", "-")).append(" |\n");
                }
                entitySection.append("\n");
            }
        }

        // 替换实体设计部分
        return currentContent.replaceFirst(
                "## 实体设计\\n\\n[\\s\\S]*?(?=\\n## API设计)",
                entitySection.toString()
        );
    }

    /**
     * 添加API设计
     *
     * @param currentContent 当前内容
     * @param apis API列表
     * @return 更新后的内容
     */
    public String addApiDesign(String currentContent, List<Map<String, Object>> apis) {
        StringBuilder apiSection = new StringBuilder();
        apiSection.append("## API设计\n\n");

        apiSection.append("| 方法 | 路径 | 描述 | 请求体 | 响应体 |\n");
        apiSection.append("|------|------|------|--------|--------|\n");

        for (Map<String, Object> api : apis) {
            apiSection.append("| ").append(api.get("method")).append(" | ");
            apiSection.append(api.get("path")).append(" | ");
            apiSection.append(api.get("description")).append(" | ");
            apiSection.append(api.getOrDefault("requestBody", "-")).append(" | ");
            apiSection.append(api.getOrDefault("responseBody", "-")).append(" |\n");
        }
        apiSection.append("\n");

        // 替换API设计部分
        return currentContent.replaceFirst(
                "## API设计\\n\\n[\\s\\S]*?(?=\\n## 能力集成笔记)",
                apiSection.toString()
        );
    }

    /**
     * 添加能力集成笔记
     *
     * @param currentContent 当前内容
     * @param capabilityCode 能力代码
     * @param notes 笔记内容
     * @return 更新后的内容
     */
    public String addCapabilityNotes(String currentContent, String capabilityCode, String notes) {
        String timestamp = DATE_FORMATTER.format(Instant.now());

        String newSection = "\n### " + capabilityCode + " (" + timestamp + ")\n\n" + notes + "\n";

        // 在能力集成笔记部分追加
        int insertPos = currentContent.indexOf("## 问题与解决方案");
        if (insertPos > 0) {
            return currentContent.substring(0, insertPos) + newSection + "\n" + currentContent.substring(insertPos);
        }

        return currentContent + newSection;
    }

    /**
     * 添加问题与解决方案
     *
     * @param currentContent 当前内容
     * @param problem 问题
     * @param solution 解决方案
     * @param reference 参考链接
     * @return 更新后的内容
     */
    public String addProblemSolution(String currentContent, String problem, String solution, String reference) {
        String newRow = "| " + problem + " | " + solution + " | " + (reference != null ? reference : "-") + " |\n";

        // 在表格末尾追加
        int lastRowPos = currentContent.lastIndexOf("|\n");
        if (lastRowPos > 0) {
            return currentContent.substring(0, lastRowPos + 2) + newRow;
        }

        return currentContent + newRow;
    }
}
