package com.ingenio.backend.codegen.ai.autofix;

import com.ingenio.backend.codegen.ai.tool.ValidationTool;
import com.ingenio.backend.codegen.schema.Entity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * AutoFixOrchestrator - 自动修复编排器
 *
 * <p>核心职责：编排自动修复流程，协调ValidationTool和FixStrategy</p>
 *
 * <p>核心算法：</p>
 * <pre>
 * 1. 验证代码（ValidationTool）
 * 2. 如果通过验证（≥70分）→ 返回成功
 * 3. 如果未通过：
 *    a. 分析问题并选择修复策略（Strategy Pattern）
 *    b. 应用修复策略
 *    c. 检查代码是否有改进
 *    d. 如果未达到最大迭代次数（3次），回到步骤1
 * 4. 如果达到最大迭代次数 → 返回失败
 * </pre>
 *
 * <p>核心机制：</p>
 * <ul>
 *   <li>Circuit Breaker: 最多3次迭代，防止无限循环</li>
 *   <li>Strategy Selection: 按优先级选择最适合的修复策略</li>
 *   <li>Improvement Detection: 检测代码是否有实质性改进</li>
 *   <li>Fix History Tracking: 记录每次迭代的详细信息</li>
 * </ul>
 *
 * <p>使用示例：</p>
 * <pre>{@code
 * AutoFixOrchestrator orchestrator = new AutoFixOrchestrator(
 *     validationTool, fixStrategies
 * );
 *
 * AutoFixResult result = orchestrator.attemptAutoFix(
 *     generatedCode, entity, "createUser"
 * );
 *
 * if (result.isSuccess()) {
 *     System.out.println("修复成功：" + result.getFinalCode());
 * } else {
 *     System.out.println("修复失败：" + result.getFailureReason());
 * }
 * }</pre>
 *
 * @author Ingenio AutoFix Orchestrator
 * @since 2025-11-19 P0 Phase 3: AutoFixOrchestrator核心实现
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AutoFixOrchestrator {

    /**
     * 最大修复迭代次数（Circuit Breaker）
     *
     * <p>为什么是3次？</p>
     * <ul>
     *   <li>第1次：修复Compilation错误（Priority 1）</li>
     *   <li>第2次：修复Structure错误（Priority 2）</li>
     *   <li>第3次：修复Logic错误（Priority 3）</li>
     * </ul>
     */
    private static final int MAX_FIX_ITERATIONS = 3;

    /**
     * 质量评分通过阈值（70分）
     *
     * <p>来自ValidationTool的评分标准：</p>
     * <ul>
     *   <li>语法验证（Syntax）：30分</li>
     *   <li>结构验证（Structure）：30分</li>
     *   <li>逻辑验证（Logic）：40分</li>
     * </ul>
     */
    private static final int QUALITY_PASS_THRESHOLD = 70;

    /**
     * 代码验证工具
     */
    private final ValidationTool validationTool;

    /**
     * 所有可用的修复策略
     *
     * <p>Spring会自动注入所有实现了FixStrategy接口的Bean：</p>
     * <ul>
     *   <li>CompilationErrorFixStrategy (Priority 1)</li>
     *   <li>StructureErrorFixStrategy (Priority 2)</li>
     *   <li>LogicErrorFixStrategy (Priority 3)</li>
     * </ul>
     */
    private final List<FixStrategy> fixStrategies;

    /**
     * 尝试自动修复生成的代码
     *
     * <p>核心算法：3次迭代 + Circuit Breaker</p>
     *
     * <p>迭代流程：</p>
     * <pre>
     * Iteration 1:
     *   - 验证初始代码 (ValidationTool)
     *   - 如果≥70分 → 成功返回
     *   - 否则：选择策略 → 应用修复 → 检查改进
     *
     * Iteration 2:
     *   - 重新验证修复后的代码
     *   - 如果≥70分 → 成功返回
     *   - 否则：继续修复
     *
     * Iteration 3:
     *   - 最后一次尝试
     *   - 如果仍未通过 → 返回失败
     * </pre>
     *
     * @param initialCode 初始生成的代码
     * @param entity      实体定义（用于验证）
     * @param methodName  方法名称（用于验证）
     * @return AutoFixResult 修复结果（包含成功/失败状态、最终代码、修复历史）
     */
    public AutoFixResult attemptAutoFix(String initialCode, Entity entity, String methodName) {
        log.info("[AutoFixOrchestrator] 🔧 开始自动修复流程");
        log.info("[AutoFixOrchestrator] 实体: {}, 方法: {}, 初始代码长度: {}字符",
                entity.getName(), methodName, initialCode.length());

        long startTime = System.currentTimeMillis();
        String currentCode = initialCode;
        int iteration = 0;
        List<AutoFixResult.FixHistoryEntry> fixHistory = new ArrayList<>();

        // Circuit Breaker: 最多尝试3次修复
        while (iteration < MAX_FIX_ITERATIONS) {
            iteration++;
            long iterationStartTime = System.currentTimeMillis();

            log.info("[AutoFixOrchestrator] 📊 第{}次迭代 (最多{}次)", iteration, MAX_FIX_ITERATIONS);

            // Step 1: 验证当前代码
            log.debug("[AutoFixOrchestrator] Step 1: 调用ValidationTool验证代码");
            ValidationResult validationResult;
            try {
                validationResult = validate(currentCode, entity, methodName);
            } catch (Exception e) {
                log.error("[AutoFixOrchestrator] ❌ ValidationTool验证失败: {}", e.getMessage(), e);
                return AutoFixResult.failure(
                        "ValidationTool验证失败: " + e.getMessage(),
                        iteration,
                        0
                );
            }

            int qualityScore = validationResult.getScore();
            List<String> issues = validationResult.getIssues();

            log.info("[AutoFixOrchestrator] 质量评分: {}/100, 问题数量: {}",
                    qualityScore, issues.size());

            // Step 2: 检查是否通过验证（≥70分）
            if (validationResult.isSuccess() && qualityScore >= QUALITY_PASS_THRESHOLD) {
                log.info("[AutoFixOrchestrator] ✅ 代码通过验证！评分: {}/100", qualityScore);

                // 记录最后一次验证成功的历史
                AutoFixResult.FixHistoryEntry finalEntry = AutoFixResult.FixHistoryEntry.builder()
                        .iteration(iteration)
                        .scoreBeforeFix(qualityScore)
                        .scoreAfterFix(qualityScore)
                        .issuesFound(new ArrayList<>())
                        .appliedStrategy("N/A - 验证通过")
                        .fixApplied(false)
                        .durationMs(System.currentTimeMillis() - iterationStartTime)
                        .notes("代码已通过验证，无需修复")
                        .build();
                fixHistory.add(finalEntry);

                long totalDuration = System.currentTimeMillis() - startTime;
                AutoFixResult successResult = AutoFixResult.success(currentCode, iteration, qualityScore);
                successResult.setFixHistory(fixHistory);
                successResult.setTotalDurationMs(totalDuration);

                log.info("[AutoFixOrchestrator] 🎉 自动修复成功！总耗时: {}ms, 迭代次数: {}",
                        totalDuration, iteration);
                return successResult;
            }

            // Step 3: 解析验证问题
            log.debug("[AutoFixOrchestrator] Step 2: 解析验证问题");
            List<ValidationIssue> parsedIssues = parseValidationIssues(issues);
            log.info("[AutoFixOrchestrator] 解析后问题分布 - SYNTAX: {}, STRUCTURE: {}, LOGIC: {}",
                    countIssuesByType(parsedIssues, ValidationIssue.IssueType.SYNTAX),
                    countIssuesByType(parsedIssues, ValidationIssue.IssueType.STRUCTURE),
                    countIssuesByType(parsedIssues, ValidationIssue.IssueType.LOGIC));

            // Step 4: 选择适用的修复策略
            log.debug("[AutoFixOrchestrator] Step 3: 选择适用的修复策略");
            List<FixStrategy> applicableStrategies = selectStrategies(parsedIssues);

            if (applicableStrategies.isEmpty()) {
                log.warn("[AutoFixOrchestrator] ⚠️ 没有适用的修复策略");

                // 记录失败历史
                AutoFixResult.FixHistoryEntry failedEntry = AutoFixResult.FixHistoryEntry.builder()
                        .iteration(iteration)
                        .scoreBeforeFix(qualityScore)
                        .scoreAfterFix(qualityScore)
                        .issuesFound(parsedIssues)
                        .appliedStrategy("N/A - 无适用策略")
                        .fixApplied(false)
                        .durationMs(System.currentTimeMillis() - iterationStartTime)
                        .notes("无法找到适用的修复策略")
                        .build();
                fixHistory.add(failedEntry);

                long totalDuration = System.currentTimeMillis() - startTime;
                AutoFixResult failureResult = AutoFixResult.failure(
                        "No applicable fix strategy",
                        iteration,
                        qualityScore
                );
                failureResult.setFixHistory(fixHistory);
                failureResult.setTotalDurationMs(totalDuration);
                return failureResult;
            }

            log.info("[AutoFixOrchestrator] 找到{}个适用策略: {}",
                    applicableStrategies.size(),
                    applicableStrategies.stream()
                            .map(FixStrategy::getName)
                            .collect(Collectors.joining(", ")));

            // Step 5: 应用修复策略
            log.debug("[AutoFixOrchestrator] Step 4: 应用修复策略");
            String fixedCode = applyStrategies(currentCode, parsedIssues, applicableStrategies);

            // Step 6: 检查代码是否有改进
            if (fixedCode.equals(currentCode)) {
                log.warn("[AutoFixOrchestrator] ⚠️ 修复策略未改变代码");

                // 记录失败历史
                AutoFixResult.FixHistoryEntry unchangedEntry = AutoFixResult.FixHistoryEntry.builder()
                        .iteration(iteration)
                        .scoreBeforeFix(qualityScore)
                        .scoreAfterFix(qualityScore)
                        .issuesFound(parsedIssues)
                        .appliedStrategy(applicableStrategies.get(0).getName())
                        .fixApplied(false)
                        .durationMs(System.currentTimeMillis() - iterationStartTime)
                        .notes("修复策略执行但代码未改变")
                        .build();
                fixHistory.add(unchangedEntry);

                long totalDuration = System.currentTimeMillis() - startTime;
                AutoFixResult failureResult = AutoFixResult.failure(
                        "Fix strategy did not change code",
                        iteration,
                        qualityScore
                );
                failureResult.setFixHistory(fixHistory);
                failureResult.setTotalDurationMs(totalDuration);
                return failureResult;
            }

            log.info("[AutoFixOrchestrator] ✅ 代码已修改，准备下一次验证");

            // 记录本次迭代的修复历史
            AutoFixResult.FixHistoryEntry historyEntry = AutoFixResult.FixHistoryEntry.builder()
                    .iteration(iteration)
                    .scoreBeforeFix(qualityScore)
                    .scoreAfterFix(-1) // 下次迭代会更新
                    .issuesFound(parsedIssues)
                    .appliedStrategy(applicableStrategies.stream()
                            .map(FixStrategy::getName)
                            .collect(Collectors.joining(", ")))
                    .fixApplied(true)
                    .durationMs(System.currentTimeMillis() - iterationStartTime)
                    .notes("修复策略应用成功，代码已改变")
                    .build();
            fixHistory.add(historyEntry);

            // 更新当前代码，进入下一次迭代
            currentCode = fixedCode;
        }

        // 达到最大迭代次数，仍未通过验证
        log.warn("[AutoFixOrchestrator] ❌ 达到最大迭代次数({}次)，仍未通过验证", MAX_FIX_ITERATIONS);

        // 最后一次验证，获取最终评分
        ValidationResult finalValidation;
        try {
            finalValidation = validate(currentCode, entity, methodName);
        } catch (Exception e) {
            log.error("[AutoFixOrchestrator] 最终验证失败: {}", e.getMessage(), e);
            finalValidation = ValidationResult.builder()
                    .success(false)
                    .score(0)
                    .issues(List.of("最终验证失败: " + e.getMessage()))
                    .build();
        }

        long totalDuration = System.currentTimeMillis() - startTime;
        AutoFixResult failureResult = AutoFixResult.failure(
                "Max iterations reached",
                MAX_FIX_ITERATIONS,
                finalValidation.getScore()
        );
        failureResult.setFixHistory(fixHistory);
        failureResult.setTotalDurationMs(totalDuration);

        log.info("[AutoFixOrchestrator] 自动修复失败 - 总耗时: {}ms, 最终评分: {}/100",
                totalDuration, finalValidation.getScore());

        return failureResult;
    }

    /**
     * 调用ValidationTool验证代码质量
     *
     * <p>作用：</p>
     * <ul>
     *   <li>将代码、实体定义、方法名封装为ValidationTool.Request</li>
     *   <li>调用validationTool.apply()执行验证</li>
     *   <li>使用ValidationResult.fromToolResponse()适配响应</li>
     * </ul>
     *
     * <p>为什么需要这个方法？</p>
     * <ul>
     *   <li>封装ValidationTool调用细节，简化AutoFixOrchestrator的主逻辑</li>
     *   <li>统一异常处理</li>
     *   <li>使用适配器模式解耦AutoFixOrchestrator和ValidationTool</li>
     * </ul>
     *
     * @param code       待验证的代码
     * @param entity     实体定义（包含字段、关系等Schema信息）
     * @param methodName 方法名
     * @return ValidationResult 验证结果
     * @throws RuntimeException 如果ValidationTool调用失败
     */
    private ValidationResult validate(String code, Entity entity, String methodName) {
        try {
            log.debug("[AutoFixOrchestrator] 调用ValidationTool验证代码，方法名: {}", methodName);

            // Step 1: 创建ValidationTool.Request
            ValidationTool.Request request = new ValidationTool.Request(code, entity, methodName);

            // Step 2: 调用ValidationTool执行验证
            ValidationTool.Response toolResponse = validationTool.apply(request);

            // Step 3: 适配为ValidationResult（解耦）
            ValidationResult result = ValidationResult.fromToolResponse(toolResponse);

            log.debug("[AutoFixOrchestrator] 验证完成，评分: {}/100, 是否通过: {}",
                    result.getScore(), result.isSuccess());

            return result;
        } catch (Exception e) {
            log.error("[AutoFixOrchestrator] ValidationTool调用失败: {}", e.getMessage(), e);
            throw new RuntimeException("ValidationTool调用失败: " + e.getMessage(), e);
        }
    }

    /**
     * 解析ValidationTool的问题描述为结构化的ValidationIssue对象
     *
     * <p>解析规则：</p>
     * <ul>
     *   <li>"语法错误：XXX" → IssueType.SYNTAX + IssueSeverity.ERROR</li>
     *   <li>"结构警告：XXX" → IssueType.STRUCTURE + IssueSeverity.WARNING</li>
     *   <li>"逻辑建议：XXX" → IssueType.LOGIC + IssueSeverity.INFO</li>
     * </ul>
     *
     * @param issues ValidationTool返回的问题描述列表
     * @return 解析后的ValidationIssue列表
     */
    private List<ValidationIssue> parseValidationIssues(List<String> issues) {
        List<ValidationIssue> parsedIssues = new ArrayList<>();

        for (String issue : issues) {
            try {
                ValidationIssue parsedIssue = ValidationIssue.fromValidationToolIssue(issue);
                parsedIssues.add(parsedIssue);
            } catch (Exception e) {
                log.warn("[AutoFixOrchestrator] 无法解析问题: {}", issue);
                // 创建默认的LOGIC类型问题
                parsedIssues.add(ValidationIssue.builder()
                        .type(ValidationIssue.IssueType.LOGIC)
                        .severity(ValidationIssue.IssueSeverity.WARNING)
                        .message(issue)
                        .originalIssue(issue)
                        .build());
            }
        }

        return parsedIssues;
    }

    /**
     * 选择适用的修复策略
     *
     * <p>选择规则：</p>
     * <ol>
     *   <li>调用每个策略的supports()方法，判断是否适用</li>
     *   <li>按priority()排序（数字越小优先级越高）</li>
     *   <li>返回排序后的适用策略列表</li>
     * </ol>
     *
     * <p>策略优先级：</p>
     * <ul>
     *   <li>Priority 1: CompilationErrorFixStrategy（编译错误）</li>
     *   <li>Priority 2: StructureErrorFixStrategy（结构错误）</li>
     *   <li>Priority 3: LogicErrorFixStrategy（逻辑错误）</li>
     * </ul>
     *
     * @param issues 验证问题列表
     * @return 适用的修复策略列表（已按优先级排序）
     */
    private List<FixStrategy> selectStrategies(List<ValidationIssue> issues) {
        return fixStrategies.stream()
                .filter(strategy -> strategy.supports(issues))
                .sorted(Comparator.comparing(FixStrategy::priority))
                .collect(Collectors.toList());
    }

    /**
     * 应用修复策略
     *
     * <p>应用规则：</p>
     * <ul>
     *   <li>按优先级顺序依次应用每个策略</li>
     *   <li>每个策略的输出作为下一个策略的输入</li>
     *   <li>Pipeline模式：code → strategy1 → strategy2 → strategy3 → fixedCode</li>
     * </ul>
     *
     * <p>为什么是Pipeline？</p>
     * <ul>
     *   <li>先修复编译错误（语法） → 代码可编译</li>
     *   <li>再修复结构错误（类定义） → 代码结构完整</li>
     *   <li>最后修复逻辑错误（业务逻辑） → 代码逻辑正确</li>
     * </ul>
     *
     * @param code       原始代码
     * @param issues     验证问题列表
     * @param strategies 适用的修复策略列表（已排序）
     * @return 修复后的代码
     */
    private String applyStrategies(String code, List<ValidationIssue> issues, List<FixStrategy> strategies) {
        String currentCode = code;

        for (FixStrategy strategy : strategies) {
            log.debug("[AutoFixOrchestrator] 应用策略: {} (Priority: {})",
                    strategy.getName(), strategy.priority());

            try {
                String fixedCode = strategy.apply(currentCode, issues);

                if (!fixedCode.equals(currentCode)) {
                    log.info("[AutoFixOrchestrator] ✅ 策略 {} 成功修改代码",
                            strategy.getName());
                    currentCode = fixedCode;
                } else {
                    log.debug("[AutoFixOrchestrator] 策略 {} 未修改代码",
                            strategy.getName());
                }
            } catch (Exception e) {
                log.error("[AutoFixOrchestrator] ❌ 策略 {} 应用失败: {}",
                        strategy.getName(), e.getMessage(), e);
                // 继续尝试下一个策略
            }
        }

        return currentCode;
    }

    /**
     * 统计指定类型的问题数量
     *
     * @param issues 问题列表
     * @param type   问题类型
     * @return 该类型的问题数量
     */
    private long countIssuesByType(List<ValidationIssue> issues, ValidationIssue.IssueType type) {
        return issues.stream()
                .filter(issue -> issue.getType() == type)
                .count();
    }
}
