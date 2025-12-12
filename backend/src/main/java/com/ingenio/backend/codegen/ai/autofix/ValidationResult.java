package com.ingenio.backend.codegen.ai.autofix;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * ValidationResult - 验证结果适配器
 *
 * <p>作用：适配ValidationTool.Response到AutoFixOrchestrator</p>
 *
 * <p>为什么需要这个类？</p>
 * <ul>
 *   <li>解耦：AutoFixOrchestrator不直接依赖ValidationTool.Response</li>
 *   <li>简化：提供简洁的验证结果接口</li>
 *   <li>扩展性：未来可以添加更多字段而不影响ValidationTool</li>
 * </ul>
 *
 * <p>使用场景：</p>
 * <pre>{@code
 * // 从ValidationTool.Response创建
 * ValidationTool.Response toolResponse = validationTool.apply(request);
 * ValidationResult result = ValidationResult.fromToolResponse(toolResponse);
 *
 * // 在AutoFixOrchestrator中使用
 * if (result.isSuccess()) {
 *     // 验证通过，无需修复
 * } else {
 *     // 验证失败，需要修复
 *     List<String> issues = result.getIssues();
 * }
 * }</pre>
 *
 * @author Ingenio AutoFix Orchestrator
 * @since 2025-11-19 P0 Phase 3: ValidationResult适配器
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ValidationResult {

    /**
     * 是否验证通过
     *
     * <p>通过标准：质量评分≥70分</p>
     */
    private boolean success;

    /**
     * 质量评分（0-100分）
     *
     * <p>评分构成：</p>
     * <ul>
     *   <li>语法验证：30分</li>
     *   <li>结构验证：30分</li>
     *   <li>逻辑验证：40分</li>
     * </ul>
     */
    private int score;

    /**
     * 响应消息
     */
    private String message;

    /**
     * 问题列表
     *
     * <p>包含所有发现的问题：</p>
     * <ul>
     *   <li>语法错误（如：括号不匹配）</li>
     *   <li>结构警告（如：缺少类定义）</li>
     *   <li>逻辑建议（如：缺少Repository引用）</li>
     * </ul>
     */
    private List<String> issues;

    /**
     * 从ValidationTool.Response创建ValidationResult
     *
     * <p>适配器模式：将ValidationTool的Response转换为AutoFixOrchestrator需要的ValidationResult</p>
     *
     * @param toolResponse ValidationTool的响应
     * @return ValidationResult对象
     */
    public static ValidationResult fromToolResponse(com.ingenio.backend.codegen.ai.tool.ValidationTool.Response toolResponse) {
        return ValidationResult.builder()
                .success(toolResponse.isSuccess())
                .score(toolResponse.getQualityScore())
                .message(toolResponse.getMessage())
                .issues(toolResponse.getIssues())
                .build();
    }

    /**
     * 获取评分（兼容方法）
     *
     * @return 质量评分（0-100分）
     */
    public int getScore() {
        return score;
    }
}
