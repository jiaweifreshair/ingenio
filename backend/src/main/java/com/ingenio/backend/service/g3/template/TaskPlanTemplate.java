package com.ingenio.backend.service.g3.template;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * task_plan.md 模板生成器
 *
 * 基于Manus工作流的任务计划文件模板
 *
 * 文件用途：
 * - 追踪代码生成的各个阶段
 * - 记录关键决策和错误
 * - 每次Agent决策前需要重新读取（保持目标在注意力窗口）
 *
 * @author Claude
 * @since 2025-01-08 (G3引擎任务规划增强)
 */
@Component
public class TaskPlanTemplate {

    private static final DateTimeFormatter DATE_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

    /**
     * 生成初始任务计划
     *
     * @param projectName 项目名称
     * @param requirement 用户需求
     * @param capabilities 选择的能力列表
     * @return Markdown格式的任务计划
     */
    public String generateInitial(String projectName, String requirement, List<String> capabilities) {
        StringBuilder sb = new StringBuilder();

        sb.append("# 任务计划: ").append(projectName).append("\n\n");

        // 元信息
        sb.append("## 元信息\n");
        sb.append("- **创建时间**: ").append(DATE_FORMATTER.format(Instant.now())).append("\n");
        sb.append("- **项目名称**: ").append(projectName).append("\n");
        sb.append("- **集成能力**: ").append(capabilities != null ? String.join(", ", capabilities) : "无").append("\n\n");

        // 目标
        sb.append("## 目标\n");
        sb.append(requirement != null ? requirement : "待定义").append("\n\n");

        // 阶段
        sb.append("## 阶段\n");
        sb.append("- [ ] 阶段1: 架构设计 (Architect)\n");
        sb.append("- [ ] 阶段2: Entity生成\n");
        sb.append("- [ ] 阶段3: Mapper生成\n");
        sb.append("- [ ] 阶段4: Service生成\n");
        sb.append("- [ ] 阶段5: Controller生成\n");
        if (capabilities != null && !capabilities.isEmpty()) {
            sb.append("- [ ] 阶段6: 能力集成代码生成\n");
        }
        sb.append("- [ ] 阶段7: 编译验证\n");
        sb.append("- [ ] 阶段8: 修复与优化 (Coach)\n\n");

        // 当前状态
        sb.append("## 当前状态\n");
        sb.append("**当前阶段**: 阶段1 - 架构设计\n");
        sb.append("**进度**: 0%\n");
        sb.append("**状态**: 进行中\n\n");

        // 关键问题
        sb.append("## 关键问题\n");
        sb.append("1. 待确定：核心实体有哪些？\n");
        sb.append("2. 待确定：实体间的关系如何？\n");
        sb.append("3. 待确定：需要哪些API接口？\n\n");

        // 决策记录
        sb.append("## 决策记录\n");
        sb.append("| 时间 | 决策 | 原因 |\n");
        sb.append("|------|------|------|\n");
        sb.append("| ").append(DATE_FORMATTER.format(Instant.now())).append(" | 创建任务计划 | 初始化 |\n\n");

        // 错误记录
        sb.append("## 错误记录\n");
        sb.append("| 时间 | 错误 | 解决方案 |\n");
        sb.append("|------|------|----------|\n");
        sb.append("| - | - | - |\n\n");

        // 生成文件清单
        sb.append("## 待生成文件清单\n");
        sb.append("*将在架构设计阶段确定*\n");

        return sb.toString();
    }

    /**
     * 更新阶段状态
     *
     * @param currentContent 当前内容
     * @param phase 阶段编号
     * @param completed 是否完成
     * @return 更新后的内容
     */
    public String updatePhaseStatus(String currentContent, int phase, boolean completed) {
        String phasePattern = "- \\[ \\] 阶段" + phase + ":";
        String replacement = completed
                ? "- [x] 阶段" + phase + ":"
                : "- [ ] 阶段" + phase + ":";

        return currentContent.replaceFirst(phasePattern, replacement);
    }

    /**
     * 追加决策记录
     *
     * @param currentContent 当前内容
     * @param decision 决策内容
     * @param reason 决策原因
     * @return 更新后的内容
     */
    public String appendDecision(String currentContent, String decision, String reason) {
        String timestamp = DATE_FORMATTER.format(Instant.now());
        String newRow = "| " + timestamp + " | " + decision + " | " + reason + " |\n";

        // 在决策记录表格末尾追加
        int insertPos = currentContent.indexOf("## 错误记录");
        if (insertPos > 0) {
            return currentContent.substring(0, insertPos) + newRow + "\n" + currentContent.substring(insertPos);
        }

        return currentContent + newRow;
    }

    /**
     * 追加错误记录
     *
     * @param currentContent 当前内容
     * @param error 错误描述
     * @param solution 解决方案
     * @return 更新后的内容
     */
    public String appendError(String currentContent, String error, String solution) {
        String timestamp = DATE_FORMATTER.format(Instant.now());
        String newRow = "| " + timestamp + " | " + error + " | " + solution + " |\n";

        // 在错误记录表格末尾追加
        int insertPos = currentContent.indexOf("## 待生成文件清单");
        if (insertPos > 0) {
            return currentContent.substring(0, insertPos) + newRow + "\n" + currentContent.substring(insertPos);
        }

        return currentContent + newRow;
    }

    /**
     * 更新当前状态
     *
     * @param currentContent 当前内容
     * @param phase 当前阶段
     * @param progress 进度百分比
     * @param status 状态描述
     * @return 更新后的内容
     */
    public String updateStatus(String currentContent, String phase, int progress, String status) {
        String statusSection = "## 当前状态\n" +
                "**当前阶段**: " + phase + "\n" +
                "**进度**: " + progress + "%\n" +
                "**状态**: " + status + "\n";

        // 替换当前状态部分
        return currentContent.replaceFirst(
                "## 当前状态\\n[\\s\\S]*?(?=\\n## )",
                statusSection
        );
    }

    /**
     * 更新待生成文件清单
     *
     * @param currentContent 当前内容
     * @param files 文件列表
     * @return 更新后的内容
     */
    public String updateFileList(String currentContent, List<Map<String, String>> files) {
        StringBuilder fileList = new StringBuilder();
        fileList.append("## 待生成文件清单\n\n");
        fileList.append("| 文件路径 | 类型 | 状态 |\n");
        fileList.append("|---------|------|------|\n");

        for (Map<String, String> file : files) {
            fileList.append("| ").append(file.get("path")).append(" | ");
            fileList.append(file.get("type")).append(" | ");
            fileList.append(file.getOrDefault("status", "待生成")).append(" |\n");
        }

        // 替换文件清单部分
        int startPos = currentContent.indexOf("## 待生成文件清单");
        if (startPos > 0) {
            return currentContent.substring(0, startPos) + fileList;
        }

        return currentContent + "\n" + fileList;
    }
}
