package com.ingenio.backend.codegen.ai.autofix;

import java.util.List;

/**
 * FixStrategy - 代码修复策略接口
 *
 * <p>定义修复策略的统一接口，使用策略模式（Strategy Pattern）实现不同类型的代码修复</p>
 *
 * <p>修复策略三级优先级：</p>
 * <ul>
 *   <li>Priority 1: CompilationErrorFixStrategy（编译错误修复）</li>
 *   <li>Priority 2: StructureErrorFixStrategy（结构错误修复）</li>
 *   <li>Priority 3: LogicErrorFixStrategy（逻辑错误修复）</li>
 * </ul>
 *
 * <p>修复策略执行流程：</p>
 * <pre>
 * 1. supports(issues) - 判断策略是否适用于当前问题列表
 * 2. priority() - 返回策略优先级（数字越小优先级越高）
 * 3. apply(code, issues) - 应用修复策略，返回修复后的代码
 * </pre>
 *
 * <p>使用示例：</p>
 * <pre>{@code
 * // 选择适用的修复策略
 * List<FixStrategy> applicableStrategies = allStrategies.stream()
 *     .filter(strategy -> strategy.supports(issues))
 *     .sorted(Comparator.comparing(FixStrategy::priority))
 *     .collect(Collectors.toList());
 *
 * // 应用第一个适用的策略
 * if (!applicableStrategies.isEmpty()) {
 *     FixStrategy strategy = applicableStrategies.get(0);
 *     String fixedCode = strategy.apply(code, issues);
 * }
 * }</pre>
 *
 * @author Ingenio AutoFix Orchestrator
 * @since 2025-11-19 P0 Phase 2: FixStrategy接口定义
 */
public interface FixStrategy {

    /**
     * 判断策略是否适用于当前问题列表
     *
     * <p>判断规则：</p>
     * <ul>
     *   <li>CompilationErrorFixStrategy: 适用于SYNTAX类型的ERROR问题</li>
     *   <li>StructureErrorFixStrategy: 适用于STRUCTURE类型的ERROR问题</li>
     *   <li>LogicErrorFixStrategy: 适用于LOGIC类型的ERROR或WARNING问题</li>
     * </ul>
     *
     * @param issues 验证问题列表
     * @return true=策略适用，false=策略不适用
     */
    boolean supports(List<ValidationIssue> issues);

    /**
     * 应用修复策略，返回修复后的代码
     *
     * <p>修复原则：</p>
     * <ul>
     *   <li>精确修复：只修复identified issues，不改变其他代码</li>
     *   <li>保守修复：优先使用通用模板和标准实践</li>
     *   <li>幂等性：对已修复的问题不重复修复</li>
     * </ul>
     *
     * <p>修复失败处理：</p>
     * <ul>
     *   <li>如果无法修复，返回原始代码（不抛出异常）</li>
     *   <li>记录修复失败的原因到日志</li>
     * </ul>
     *
     * @param code   原始代码
     * @param issues 验证问题列表
     * @return 修复后的代码（如果无法修复则返回原始代码）
     */
    String apply(String code, List<ValidationIssue> issues);

    /**
     * 返回策略优先级
     *
     * <p>优先级定义（数字越小优先级越高）：</p>
     * <ul>
     *   <li>1: CompilationErrorFixStrategy - 编译错误（阻塞性最高）</li>
     *   <li>2: StructureErrorFixStrategy - 结构错误（影响代码完整性）</li>
     *   <li>3: LogicErrorFixStrategy - 逻辑错误（影响业务逻辑）</li>
     * </ul>
     *
     * <p>优先级排序原则：</p>
     * <ul>
     *   <li>先修复编译错误（否则代码无法编译）</li>
     *   <li>再修复结构错误（否则代码结构不完整）</li>
     *   <li>最后修复逻辑错误（优化业务逻辑）</li>
     * </ul>
     *
     * @return 策略优先级（1-3）
     */
    int priority();

    /**
     * 返回策略名称（用于日志和调试）
     *
     * @return 策略名称
     */
    default String getName() {
        return this.getClass().getSimpleName();
    }

    /**
     * 返回策略描述（用于日志和调试）
     *
     * @return 策略描述
     */
    default String getDescription() {
        return "Generic fix strategy";
    }
}
