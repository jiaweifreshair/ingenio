package com.ingenio.backend.service.blueprint;

import java.util.List;

/**
 * Blueprint 合规性校验结果
 *
 * 用途：
 * - 校验 Architect 输出的 DDL / OpenAPI 是否满足 Blueprint 的强约束
 * - 作为“生成 → 验证 → 修复”闭环中的结构化反馈数据
 */
public record BlueprintComplianceResult(
        boolean passed,
        List<String> violations
) {

    /**
     * 创建“通过”结果
     *
     * 说明：
     * - record 组件包含 passed 字段，会自动生成实例方法 passed()
     * - 因此这里的工厂方法不能命名为 passed()，否则会与 accessor 冲突
     */
    public static BlueprintComplianceResult passedResult() {
        return new BlueprintComplianceResult(true, List.of());
    }

    /**
     * 创建“失败”结果
     */
    public static BlueprintComplianceResult failedResult(List<String> violations) {
        return new BlueprintComplianceResult(false, List.copyOf(violations));
    }
}
