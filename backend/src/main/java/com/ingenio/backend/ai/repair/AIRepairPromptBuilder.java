package com.ingenio.backend.ai.repair;

import lombok.Builder;
import lombok.Getter;

import java.util.List;
import java.util.Map;

/**
 * AI修复Prompt构建器
 *
 * 功能：
 * 1. 根据失败类型生成针对性的修复建议Prompt
 * 2. 支持三种修复策略：类型推断、依赖分析、业务逻辑修复
 * 3. 提供上下文信息（错误详情、代码片段、历史修复）
 * 4. 优化Prompt结构以提升AI修复成功率
 *
 * 设计原则：
 * - Prompt Engineering：精准的Prompt设计提升修复准确率
 * - 上下文完整：提供足够的上下文信息（错误堆栈、代码片段）
 * - 结构化输出：要求AI返回结构化的JSON格式修复建议
 * - Few-Shot Learning：提供修复示例提升生成质量
 *
 * @author Ingenio Team
 * @since 2.0.0 Phase 4
 */
@Getter
@Builder
public class AIRepairPromptBuilder {

    /**
     * 失败类型
     */
    private final String failureType;

    /**
     * 错误详情（错误消息、堆栈跟踪）
     */
    private final Map<String, Object> errorDetails;

    /**
     * 相关代码片段
     */
    private final String codeSnippet;

    /**
     * 编程语言
     */
    private final String language;

    /**
     * 历史修复记录（用于避免重复失败的修复）
     */
    private final List<Map<String, Object>> previousRepairs;

    /**
     * 构建修复建议Prompt
     *
     * @return 完整的Prompt字符串
     */
    public String buildPrompt() {
        return switch (failureType) {
            case "compile_error" -> buildCompileErrorPrompt();
            case "test_failure" -> buildTestFailurePrompt();
            case "business_violation" -> buildBusinessViolationPrompt();
            case "type_error" -> buildTypeErrorPrompt();
            case "dependency_missing" -> buildDependencyMissingPrompt();
            default -> buildGenericRepairPrompt();
        };
    }

    /**
     * 构建编译错误修复Prompt（类型推断策略）
     *
     * 重点关注：
     * - TypeScript/Java类型错误
     * - 类型推断和自动补全
     * - 泛型类型匹配
     * - 接口实现缺失
     */
    private String buildCompileErrorPrompt() {
        String errorMessage = getErrorMessage();
        String stackTrace = getStackTrace();

        return String.format("""
            你是一位资深的全栈工程师，专精于修复编译错误。请分析以下编译错误并提供修复建议。

            ## 错误信息
            ```
            %s
            ```

            ## 错误堆栈
            ```
            %s
            ```

            ## 相关代码片段（%s）
            ```%s
            %s
            ```

            %s

            ## 要求输出格式（JSON）

            请返回以下格式的JSON（严格遵循，不要添加Markdown标记）：

            {
              "repairStrategy": "type_inference" | "import_fix" | "syntax_fix" | "interface_implementation",
              "suggestions": [
                {
                  "file": "文件路径",
                  "lineNumber": 错误行号,
                  "errorType": "错误类型（如type_mismatch, missing_import）",
                  "originalCode": "原始代码",
                  "fixedCode": "修复后的代码",
                  "explanation": "修复说明（中文）",
                  "confidence": 0.95,
                  "priority": "high" | "medium" | "low"
                }
              ],
              "affectedFiles": ["文件路径列表"],
              "estimatedImpact": "low" | "medium" | "high",
              "reasoning": "修复思路（中文）"
            }

            ## 修复原则

            1. **类型安全**：优先使用明确的类型声明而非any
            2. **最小改动**：只修改必要的代码，避免影响其他功能
            3. **保持一致**：遵循现有代码风格和命名规范
            4. **向后兼容**：确保修复不破坏现有功能

            请立即开始分析并返回JSON格式的修复建议：
            """,
                errorMessage,
                stackTrace,
                language,
                language,
                codeSnippet,
                buildHistoryContext()
        );
    }

    /**
     * 构建测试失败修复Prompt
     *
     * 重点关注：
     * - API路径错误
     * - Mock数据不匹配
     * - 断言错误
     * - 异步测试时序问题
     */
    private String buildTestFailurePrompt() {
        String errorMessage = getErrorMessage();
        String stackTrace = getStackTrace();

        return String.format("""
            你是一位测试专家，专精于修复单元测试和集成测试失败。请分析以下测试失败并提供修复建议。

            ## 测试失败信息
            ```
            %s
            ```

            ## 错误堆栈
            ```
            %s
            ```

            ## 测试代码（%s）
            ```%s
            %s
            ```

            %s

            ## 要求输出格式（JSON）

            请返回以下格式的JSON（严格遵循，不要添加Markdown标记）：

            {
              "repairStrategy": "api_path_fix" | "mock_data_fix" | "assertion_fix" | "timing_fix",
              "suggestions": [
                {
                  "file": "测试文件路径",
                  "testName": "测试名称",
                  "errorType": "错误类型（如assertion_failed, timeout）",
                  "originalCode": "原始测试代码",
                  "fixedCode": "修复后的测试代码",
                  "explanation": "修复说明（中文）",
                  "confidence": 0.90,
                  "priority": "high"
                }
              ],
              "affectedFiles": ["文件路径列表"],
              "estimatedImpact": "low",
              "reasoning": "修复思路（中文）"
            }

            ## 修复原则

            1. **准确断言**：确保断言条件符合实际业务逻辑
            2. **Mock一致**：Mock数据与真实API响应格式一致
            3. **异步处理**：正确处理Promise和async/await
            4. **隔离性**：测试间互不影响，可独立运行

            请立即开始分析并返回JSON格式的修复建议：
            """,
                errorMessage,
                stackTrace,
                language,
                language,
                codeSnippet,
                buildHistoryContext()
        );
    }

    /**
     * 构建业务逻辑违规修复Prompt
     *
     * 重点关注：
     * - 业务规则验证失败
     * - 数据约束冲突
     * - 状态机违规
     * - 权限控制缺失
     */
    private String buildBusinessViolationPrompt() {
        String errorMessage = getErrorMessage();

        return String.format("""
            你是一位业务逻辑专家，专精于修复业务规则验证失败。请分析以下业务逻辑违规并提供修复建议。

            ## 违规信息
            ```
            %s
            ```

            ## 相关代码（%s）
            ```%s
            %s
            ```

            %s

            ## 要求输出格式（JSON）

            请返回以下格式的JSON（严格遵循，不要添加Markdown标记）：

            {
              "repairStrategy": "validation_add" | "constraint_relax" | "state_machine_fix" | "permission_add",
              "suggestions": [
                {
                  "file": "文件路径",
                  "component": "组件名称",
                  "violationType": "违规类型（如required_field_missing, state_invalid）",
                  "originalCode": "原始代码",
                  "fixedCode": "修复后的代码",
                  "businessRule": "相关业务规则说明（中文）",
                  "explanation": "修复说明（中文）",
                  "confidence": 0.85,
                  "priority": "high"
                }
              ],
              "affectedFiles": ["文件路径列表"],
              "estimatedImpact": "medium",
              "reasoning": "修复思路（中文）"
            }

            ## 修复原则

            1. **业务准确**：修复后的代码严格符合业务规则
            2. **用户体验**：合理的错误提示和引导
            3. **数据一致性**：确保数据库约束和业务规则一致
            4. **安全性**：不降低原有的安全控制

            请立即开始分析并返回JSON格式的修复建议：
            """,
                errorMessage,
                language,
                language,
                codeSnippet,
                buildHistoryContext()
        );
    }

    /**
     * 构建类型错误修复Prompt（TypeScript/Java专用）
     */
    private String buildTypeErrorPrompt() {
        return buildCompileErrorPrompt(); // 复用编译错误Prompt
    }

    /**
     * 构建依赖缺失修复Prompt
     *
     * 重点关注：
     * - npm/pnpm包缺失
     * - Maven/Gradle依赖缺失
     * - 版本冲突
     * - 配置缺失
     */
    private String buildDependencyMissingPrompt() {
        String errorMessage = getErrorMessage();

        return String.format("""
            你是一位依赖管理专家，专精于解决依赖缺失和版本冲突问题。请分析以下依赖错误并提供修复建议。

            ## 依赖错误
            ```
            %s
            ```

            ## 项目配置文件片段（%s）
            ```%s
            %s
            ```

            %s

            ## 要求输出格式（JSON）

            请返回以下格式的JSON（严格遵循，不要添加Markdown标记）：

            {
              "repairStrategy": "dependency_install" | "version_upgrade" | "version_downgrade" | "config_add",
              "suggestions": [
                {
                  "packageManager": "npm" | "pnpm" | "maven" | "gradle",
                  "action": "install" | "upgrade" | "downgrade",
                  "packageName": "依赖包名称",
                  "version": "建议版本号",
                  "command": "安装命令（如pnpm add package@version）",
                  "reasoning": "为什么需要这个依赖（中文）",
                  "confidence": 0.95,
                  "priority": "high"
                }
              ],
              "affectedFiles": ["package.json", "pom.xml"],
              "estimatedImpact": "low",
              "reasoning": "依赖分析思路（中文）"
            }

            ## 修复原则

            1. **版本兼容**：选择与现有依赖兼容的版本
            2. **最小化安装**：只安装必需的依赖
            3. **锁版本**：优先使用精确版本号
            4. **安全性**：检查已知漏洞

            请立即开始分析并返回JSON格式的修复建议：
            """,
                errorMessage,
                language.equals("typescript") ? "package.json" : "pom.xml",
                language,
                codeSnippet,
                buildHistoryContext()
        );
    }

    /**
     * 构建通用修复Prompt（兜底策略）
     */
    private String buildGenericRepairPrompt() {
        String errorMessage = getErrorMessage();

        return String.format("""
            你是一位全栈工程师，请分析以下错误并提供修复建议。

            ## 错误信息
            ```
            %s
            ```

            ## 相关代码（%s）
            ```%s
            %s
            ```

            ## 要求输出格式（JSON）

            请返回以下格式的JSON（严格遵循，不要添加Markdown标记）：

            {
              "repairStrategy": "generic_fix",
              "suggestions": [
                {
                  "file": "文件路径",
                  "errorType": "错误类型",
                  "originalCode": "原始代码",
                  "fixedCode": "修复后的代码",
                  "explanation": "修复说明（中文）",
                  "confidence": 0.80,
                  "priority": "medium"
                }
              ],
              "affectedFiles": ["文件路径列表"],
              "estimatedImpact": "medium",
              "reasoning": "修复思路（中文）"
            }

            请立即开始分析并返回JSON格式的修复建议：
            """,
                errorMessage,
                language,
                language,
                codeSnippet
        );
    }

    /**
     * 构建历史修复上下文（避免重复失败）
     */
    private String buildHistoryContext() {
        if (previousRepairs == null || previousRepairs.isEmpty()) {
            return "";
        }

        StringBuilder historyContext = new StringBuilder();
        historyContext.append("## 历史修复记录（请避免以下失败的修复方案）\n\n");

        for (int i = 0; i < Math.min(previousRepairs.size(), 3); i++) {
            Map<String, Object> repair = previousRepairs.get(i);
            historyContext.append(String.format("""
                **尝试 %d**：
                - 策略：%s
                - 结果：失败
                - 原因：%s

                """,
                    i + 1,
                    repair.get("strategy"),
                    repair.get("failureReason")
            ));
        }

        return historyContext.toString();
    }

    /**
     * 提取错误消息
     */
    private String getErrorMessage() {
        if (errorDetails == null) {
            return "无错误消息";
        }
        return (String) errorDetails.getOrDefault("message", "无错误消息");
    }

    /**
     * 提取堆栈跟踪
     */
    private String getStackTrace() {
        if (errorDetails == null) {
            return "无堆栈信息";
        }
        return (String) errorDetails.getOrDefault("stackTrace", "无堆栈信息");
    }
}
