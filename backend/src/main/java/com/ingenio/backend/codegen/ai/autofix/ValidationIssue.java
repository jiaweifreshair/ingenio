package com.ingenio.backend.codegen.ai.autofix;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * ValidationIssue - 验证问题数据类
 *
 * <p>封装ValidationTool返回的问题，提供结构化的问题描述和类型分类</p>
 *
 * <p>问题类型三级分类：</p>
 * <ul>
 *   <li>SYNTAX: 语法错误（括号不匹配、分号缺失、关键字拼写错误）</li>
 *   <li>STRUCTURE: 结构错误（缺少类定义、方法定义、包声明、注解）</li>
 *   <li>LOGIC: 逻辑错误（缺少Repository注入、业务逻辑不完整、异常处理缺失）</li>
 * </ul>
 *
 * <p>问题解析规则（基于ValidationTool的问题描述）：</p>
 * <pre>
 * "语法错误：" → SYNTAX
 * "结构错误：" → STRUCTURE
 * "结构警告：" → STRUCTURE
 * "逻辑错误：" → LOGIC
 * "逻辑警告：" → LOGIC
 * "逻辑建议：" → LOGIC
 * </pre>
 *
 * <p>使用示例：</p>
 * <pre>{@code
 * ValidationIssue issue = ValidationIssue.fromValidationToolIssue("语法错误：括号不匹配");
 * // issue.type = IssueType.SYNTAX
 * // issue.message = "括号不匹配"
 * // issue.severity = IssueSeverity.ERROR
 * }</pre>
 *
 * @author Ingenio AutoFix Orchestrator
 * @since 2025-11-19 P0 Phase 2: ValidationIssue数据类
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ValidationIssue {

    /**
     * 问题类型
     */
    private IssueType type;

    /**
     * 严重程度
     */
    private IssueSeverity severity;

    /**
     * 问题描述消息
     */
    private String message;

    /**
     * 原始问题描述（来自ValidationTool）
     */
    private String originalIssue;

    /**
     * 问题位置提示（可选，用于精确定位代码问题）
     */
    private String locationHint;

    /**
     * 问题类型枚举
     *
     * <p>对应ValidationTool的三层验证：</p>
     * <ul>
     *   <li>SYNTAX: Level 1 语法验证（30分）</li>
     *   <li>STRUCTURE: Level 2 结构验证（30分）</li>
     *   <li>LOGIC: Level 3 逻辑验证（40分）</li>
     * </ul>
     */
    public enum IssueType {
        /**
         * 语法错误
         * <p>示例：括号不匹配、分号缺失、关键字拼写错误</p>
         */
        SYNTAX,

        /**
         * 结构错误
         * <p>示例：缺少类定义、缺少方法定义、缺少包声明、缺少@Service注解</p>
         */
        STRUCTURE,

        /**
         * 逻辑错误
         * <p>示例：缺少Repository注入、业务逻辑不完整、异常处理缺失</p>
         */
        LOGIC
    }

    /**
     * 问题严重程度枚举
     *
     * <p>严重性分级：</p>
     * <ul>
     *   <li>ERROR: 阻塞性错误，必须修复（例如：括号不匹配）</li>
     *   <li>WARNING: 影响质量但不阻塞（例如：缺少包声明）</li>
     *   <li>INFO: 优化建议（例如：缺少异常处理）</li>
     * </ul>
     */
    public enum IssueSeverity {
        /**
         * 错误：阻塞性问题，必须修复
         */
        ERROR,

        /**
         * 警告：影响质量但不阻塞
         */
        WARNING,

        /**
         * 信息：优化建议
         */
        INFO
    }

    /**
     * 从ValidationTool的问题描述字符串解析为结构化的ValidationIssue
     *
     * <p>解析规则：</p>
     * <pre>
     * "语法错误：XXX" → IssueType.SYNTAX, IssueSeverity.ERROR
     * "语法警告：XXX" → IssueType.SYNTAX, IssueSeverity.WARNING
     * "结构错误：XXX" → IssueType.STRUCTURE, IssueSeverity.ERROR
     * "结构警告：XXX" → IssueType.STRUCTURE, IssueSeverity.WARNING
     * "逻辑错误：XXX" → IssueType.LOGIC, IssueSeverity.ERROR
     * "逻辑警告：XXX" → IssueType.LOGIC, IssueSeverity.WARNING
     * "逻辑建议：XXX" → IssueType.LOGIC, IssueSeverity.INFO
     * </pre>
     *
     * @param issueDescription ValidationTool返回的问题描述字符串
     * @return 结构化的ValidationIssue对象
     */
    public static ValidationIssue fromValidationToolIssue(String issueDescription) {
        ValidationIssue issue = new ValidationIssue();
        issue.setOriginalIssue(issueDescription);

        // 解析问题类型和严重程度
        if (issueDescription.startsWith("语法错误：")) {
            issue.setType(IssueType.SYNTAX);
            issue.setSeverity(IssueSeverity.ERROR);
            issue.setMessage(issueDescription.substring("语法错误：".length()));
        } else if (issueDescription.startsWith("语法警告：")) {
            issue.setType(IssueType.SYNTAX);
            issue.setSeverity(IssueSeverity.WARNING);
            issue.setMessage(issueDescription.substring("语法警告：".length()));
        } else if (issueDescription.startsWith("结构错误：")) {
            issue.setType(IssueType.STRUCTURE);
            issue.setSeverity(IssueSeverity.ERROR);
            issue.setMessage(issueDescription.substring("结构错误：".length()));
        } else if (issueDescription.startsWith("结构警告：")) {
            issue.setType(IssueType.STRUCTURE);
            issue.setSeverity(IssueSeverity.WARNING);
            issue.setMessage(issueDescription.substring("结构警告：".length()));
        } else if (issueDescription.startsWith("逻辑错误：")) {
            issue.setType(IssueType.LOGIC);
            issue.setSeverity(IssueSeverity.ERROR);
            issue.setMessage(issueDescription.substring("逻辑错误：".length()));
        } else if (issueDescription.startsWith("逻辑警告：")) {
            issue.setType(IssueType.LOGIC);
            issue.setSeverity(IssueSeverity.WARNING);
            issue.setMessage(issueDescription.substring("逻辑警告：".length()));
        } else if (issueDescription.startsWith("逻辑建议：")) {
            issue.setType(IssueType.LOGIC);
            issue.setSeverity(IssueSeverity.INFO);
            issue.setMessage(issueDescription.substring("逻辑建议：".length()));
        } else {
            // 未知格式，默认为LOGIC WARNING
            issue.setType(IssueType.LOGIC);
            issue.setSeverity(IssueSeverity.WARNING);
            issue.setMessage(issueDescription);
        }

        return issue;
    }

    /**
     * 判断是否为ERROR级别的问题（需要立即修复）
     *
     * @return true=ERROR级别，false=WARNING/INFO级别
     */
    public boolean isError() {
        return severity == IssueSeverity.ERROR;
    }

    /**
     * 判断是否为语法错误
     *
     * @return true=语法错误，false=其他类型
     */
    public boolean isSyntaxError() {
        return type == IssueType.SYNTAX;
    }

    /**
     * 判断是否为结构错误
     *
     * @return true=结构错误，false=其他类型
     */
    public boolean isStructureError() {
        return type == IssueType.STRUCTURE;
    }

    /**
     * 判断是否为逻辑错误
     *
     * @return true=逻辑错误，false=其他类型
     */
    public boolean isLogicError() {
        return type == IssueType.LOGIC;
    }

    @Override
    public String toString() {
        return String.format("[%s/%s] %s", type, severity, message);
    }
}
