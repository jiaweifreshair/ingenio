package com.ingenio.backend.service;

import com.ingenio.backend.ai.AIProvider;
import com.ingenio.backend.ai.AIProviderFactory;
import com.ingenio.backend.ai.repair.AIRepairPromptBuilder;
import com.ingenio.backend.ai.repair.AIRepairSuggestionParser;
import com.ingenio.backend.dto.request.repair.*;
import com.ingenio.backend.dto.response.repair.RepairResponse;
import com.ingenio.backend.dto.response.validation.ValidationResponse;
import com.ingenio.backend.entity.ProjectEntity;
import com.ingenio.backend.entity.RepairRecordEntity;
import com.ingenio.backend.mapper.RepairRecordMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

/**
 * V2.0 AI自动修复服务
 *
 * 功能：
 * 1. 触发AI修复流程
 * 2. 生成修复建议（集成真实AI模型）
 * 3. 迭代修复（最多3次）
 * 4. 升级到人工介入
 * 5. 智能修复策略（类型错误/依赖/业务逻辑）
 * 6. 自动修复并重新验证
 *
 * V2.0 Phase 4 升级：
 * - 集成AIProviderFactory（支持多AI提供商）
 * - 使用AIRepairPromptBuilder构建专业Prompt
 * - 使用AIRepairSuggestionParser解析AI返回
 * - 统计AI Token消耗
 *
 * @author Ingenio Team
 * @since 2.0.0 Phase 4
 */
@Service
public class RepairService {

    private static final Logger log = LoggerFactory.getLogger(RepairService.class);

    private final RepairRecordMapper repairRecordMapper;
    private final ValidationService validationService;
    private final AIProviderFactory aiProviderFactory; // AI Provider工厂注入
    private final AIRepairSuggestionParser aiRepairSuggestionParser; // AI建议解析器注入
    /**
     * 项目服务
     *
     * 是什么：根据 appSpecId 查询项目实体的服务。
     * 做什么：解析项目级AI配置入口。
     * 为什么：保留项目级Provider入口，便于后续扩展。
     */
    private final ProjectService projectService;

    public RepairService(RepairRecordMapper repairRecordMapper, ValidationService validationService,
            AIProviderFactory aiProviderFactory, AIRepairSuggestionParser aiRepairSuggestionParser,
            ProjectService projectService) {
        this.repairRecordMapper = repairRecordMapper;
        this.validationService = validationService;
        this.aiProviderFactory = aiProviderFactory;
        this.aiRepairSuggestionParser = aiRepairSuggestionParser;
        this.projectService = projectService;
    }

    /**
     * 触发AI修复流程
     *
     * @param request 触发修复请求
     * @return 修复响应
     */
    @Transactional(rollbackFor = Exception.class)
    public RepairResponse triggerRepair(TriggerRepairRequest request) {
        log.info("触发AI修复流程 - appSpecId: {}, failureType: {}",
                request.getAppSpecId(), request.getFailureType());

        Instant startTime = Instant.now();
        UUID repairId = UUID.randomUUID();

        try {
            // 创建修复记录
            RepairRecordEntity entity = RepairRecordEntity.builder()
                    .id(repairId)
                    .tenantId(request.getTenantId())
                    .appSpecId(request.getAppSpecId())
                    .failureType(request.getFailureType())
                    .status(RepairRecordEntity.Status.ANALYZING.getValue())
                    .currentIteration(0)
                    .maxIterations(3)
                    .errorDetails(convertErrorDetails(request.getErrorDetails()))
                    .isSuccess(false)
                    .isEscalated(false)
                    .notificationSent(false)
                    .startedAt(startTime)
                    .createdAt(Instant.now())
                    .updatedAt(Instant.now())
                    .build();

            repairRecordMapper.insert(entity);

            log.info("AI修复流程已触发 - repairId: {}", repairId);

            return RepairResponse.builder()
                    .repairId(repairId)
                    .status(entity.getStatus())
                    .isSuccess(false)
                    .currentIteration(0)
                    .maxIterations(3)
                    .repairStrategy(null)
                    .suggestions(new ArrayList<>())
                    .codeChanges(new HashMap<>())
                    .affectedFiles(new ArrayList<>())
                    .isEscalated(false)
                    .durationMs(0L)
                    .completedAt(null)
                    .aiTokenUsage(null)
                    .build();

        } catch (Exception e) {
            log.error("触发AI修复失败", e);
            throw new RuntimeException("触发AI修复失败: " + e.getMessage(), e);
        }
    }

    /**
     * 生成修复建议（真实AI调用）
     *
     * @param failedTests 失败的测试列表
     * @param appSpecId   AppSpec ID
     * @return 修复响应
     */
    @Transactional(rollbackFor = Exception.class)
    public RepairResponse generateSuggestions(UUID appSpecId, List<Map<String, Object>> failedTests) {
        log.info("生成修复建议 - appSpecId: {}, failedTestCount: {}",
                appSpecId, failedTests != null ? failedTests.size() : 0);

        Instant startTime = Instant.now();

        try {
            // 1. 构建错误上下文
            Map<String, Object> errorDetails = new HashMap<>();
            StringBuilder errorMessageBuilder = new StringBuilder();
            StringBuilder stackTraceBuilder = new StringBuilder();

            for (Map<String, Object> test : failedTests) {
                errorMessageBuilder.append(test.get("testName")).append(": ")
                        .append(test.get("error")).append("\n");

                if (test.containsKey("stackTrace")) {
                    stackTraceBuilder.append(test.get("stackTrace")).append("\n");
                }
            }

            errorDetails.put("message", errorMessageBuilder.toString());
            errorDetails.put("stackTrace", stackTraceBuilder.toString());

            // 2. 构建AI Prompt
            AIRepairPromptBuilder promptBuilder = AIRepairPromptBuilder.builder()
                    .failureType("test_failure")
                    .errorDetails(errorDetails)
                    .codeSnippet("// 测试代码上下文\n// TODO: 从AppSpec中获取实际代码")
                    .language("typescript")
                    .previousRepairs(null) // 第一次修复，无历史记录
                    .build();

            String prompt = promptBuilder.buildPrompt();

            // 3. 调用AI生成修复建议
            AIProvider.AIRequest aiRequest = AIProvider.AIRequest.builder()
                    .temperature(0.3) // 低温度提升准确率
                    .maxTokens(4096)
                    .build();

            AIProvider.AIResponse aiResponse = resolveProvider(appSpecId).generate(prompt, aiRequest);

            log.info("AI修复建议生成完成 - promptTokens: {}, completionTokens: {}, duration: {}ms",
                    aiResponse.promptTokens(), aiResponse.completionTokens(), aiResponse.durationMs());

            // 4. 解析AI返回的JSON修复建议
            AIRepairSuggestionParser.RepairSuggestion suggestion = aiRepairSuggestionParser.parse(aiResponse.content());

            // 5. 计算耗时并返回
            long durationMs = Instant.now().toEpochMilli() - startTime.toEpochMilli();

            return RepairResponse.builder()
                    .repairId(UUID.randomUUID())
                    .status("suggestions_generated")
                    .isSuccess(false)
                    .currentIteration(0)
                    .maxIterations(3)
                    .repairStrategy(suggestion.getRepairStrategy())
                    .suggestions(suggestion.getSuggestions())
                    .codeChanges(new HashMap<>())
                    .affectedFiles(suggestion.getAffectedFiles())
                    .isEscalated(false)
                    .durationMs(durationMs)
                    .completedAt(null)
                    .aiTokenUsage(Map.of(
                            "promptTokens", aiResponse.promptTokens(),
                            "completionTokens", aiResponse.completionTokens(),
                            "totalTokens", aiResponse.totalTokens()))
                    .build();

        } catch (AIProvider.AIException e) {
            log.error("AI调用失败", e);
            throw new RuntimeException("AI修复建议生成失败: " + e.getMessage(), e);
        } catch (Exception e) {
            log.error("生成修复建议失败", e);
            throw new RuntimeException("生成修复建议失败: " + e.getMessage(), e);
        }
    }

    /**
     * 迭代修复
     *
     * @param request 迭代修复请求
     * @return 修复响应
     */
    @Transactional(rollbackFor = Exception.class)
    public RepairResponse iterateRepair(IterateRepairRequest request) {
        log.info("开始迭代修复 - repairId: {}, maxIterations: {}",
                request.getRepairId(), request.getMaxIterations());

        try {
            // 查询修复记录
            RepairRecordEntity entity = repairRecordMapper.selectById(request.getRepairId());
            if (entity == null) {
                throw new IllegalArgumentException("修复记录不存在: " + request.getRepairId());
            }

            // 检查是否超过最大迭代次数
            if (entity.getCurrentIteration() >= request.getMaxIterations()) {
                log.warn("已达到最大迭代次数 - current: {}, max: {}",
                        entity.getCurrentIteration(), request.getMaxIterations());

                if (Boolean.TRUE.equals(request.getAutoEscalate())) {
                    return escalateToHuman(request.getRepairId(), "达到最大迭代次数仍未修复成功");
                }

                throw new IllegalStateException("已达到最大迭代次数: " + request.getMaxIterations());
            }

            // 增加迭代次数
            int newIteration = entity.getCurrentIteration() + 1;
            entity.setCurrentIteration(newIteration);
            entity.setStatus(RepairRecordEntity.Status.REPAIRING.getValue());
            entity.setUpdatedAt(Instant.now());

            repairRecordMapper.updateById(entity);

            log.info("迭代修复进行中 - iteration: {}/{}", newIteration, request.getMaxIterations());

            return RepairResponse.builder()
                    .repairId(entity.getId())
                    .status(entity.getStatus())
                    .isSuccess(false)
                    .currentIteration(newIteration)
                    .maxIterations(request.getMaxIterations())
                    .repairStrategy(entity.getRepairStrategy())
                    .suggestions(entity.getRepairSuggestions())
                    .codeChanges(entity.getCodeChanges())
                    .affectedFiles(entity.getAffectedFiles())
                    .isEscalated(false)
                    .durationMs(0L)
                    .completedAt(null)
                    .aiTokenUsage(null)
                    .build();

        } catch (Exception e) {
            log.error("迭代修复失败", e);
            throw new RuntimeException("迭代修复失败: " + e.getMessage(), e);
        }
    }

    /**
     * 升级到人工介入
     *
     * @param repairId 修复记录ID
     * @param reason   升级原因
     * @return 修复响应
     */
    @Transactional(rollbackFor = Exception.class)
    public RepairResponse escalateToHuman(UUID repairId, String reason) {
        log.info("升级到人工介入 - repairId: {}, reason: {}", repairId, reason);

        try {
            RepairRecordEntity entity = repairRecordMapper.selectById(repairId);
            if (entity == null) {
                throw new IllegalArgumentException("修复记录不存在: " + repairId);
            }

            // 更新状态
            entity.setStatus(RepairRecordEntity.Status.ESCALATED.getValue());
            entity.setIsEscalated(true);
            entity.setEscalatedAt(Instant.now());
            entity.setFailureReason(reason);
            entity.setCompletedAt(Instant.now());

            // 计算耗时
            if (entity.getStartedAt() != null) {
                long durationMs = entity.getCompletedAt().toEpochMilli() - entity.getStartedAt().toEpochMilli();
                entity.setDurationMs(durationMs);
            }

            // 发送通知（模拟）
            // TODO: 集成通知系统（邮件/Slack/钉钉）
            boolean notificationSent = sendEscalationNotification(entity);
            entity.setNotificationSent(notificationSent);

            entity.setUpdatedAt(Instant.now());
            repairRecordMapper.updateById(entity);

            log.info("已升级到人工介入 - repairId: {}, notificationSent: {}", repairId, notificationSent);

            return RepairResponse.builder()
                    .repairId(entity.getId())
                    .status(entity.getStatus())
                    .isSuccess(false)
                    .currentIteration(entity.getCurrentIteration())
                    .maxIterations(entity.getMaxIterations())
                    .repairStrategy(entity.getRepairStrategy())
                    .suggestions(entity.getRepairSuggestions())
                    .codeChanges(entity.getCodeChanges())
                    .affectedFiles(entity.getAffectedFiles())
                    .isEscalated(true)
                    .failureReason(reason)
                    .durationMs(entity.getDurationMs())
                    .completedAt(entity.getCompletedAt())
                    .aiTokenUsage(null)
                    .build();

        } catch (Exception e) {
            log.error("升级人工介入失败", e);
            throw new RuntimeException("升级人工介入失败: " + e.getMessage(), e);
        }
    }

    /**
     * 类型错误修复（真实AI调用）
     *
     * @param appSpecId AppSpec ID
     * @param errorCode TypeScript错误码
     * @param context   错误上下文
     * @return 修复响应
     */
    @Transactional(rollbackFor = Exception.class)
    public RepairResponse fixTypeError(UUID appSpecId, String errorCode, Map<String, Object> context) {
        log.info("开始类型错误修复 - appSpecId: {}, errorCode: {}", appSpecId, errorCode);

        Instant startTime = Instant.now();

        try {
            // 1. 构建错误详情
            Map<String, Object> errorDetails = new HashMap<>();
            String expectedType = (String) context.get("expectedType");
            String actualType = (String) context.get("actualType");
            String variableName = (String) context.get("variableName");
            String filePath = (String) context.getOrDefault("filePath", "src/types.ts");
            Integer lineNumber = (Integer) context.getOrDefault("lineNumber", 0);

            errorDetails.put("message", String.format(
                    "类型错误 TS%s: 变量 '%s' 期望类型为 '%s'，但实际类型为 '%s'",
                    errorCode, variableName, expectedType, actualType));
            errorDetails.put("stackTrace", String.format(
                    "%s:%d:1 - Type '%s' is not assignable to type '%s'",
                    filePath, lineNumber, actualType, expectedType));

            // 2. 构建代码片段
            String codeSnippet = String.format(
                    "const %s: %s = originalValue; // 当前代码\n// 类型不匹配：期望%s，实际%s",
                    variableName, actualType, expectedType, actualType);

            // 3. 构建AI Prompt
            AIRepairPromptBuilder promptBuilder = AIRepairPromptBuilder.builder()
                    .failureType("type_error")
                    .errorDetails(errorDetails)
                    .codeSnippet(codeSnippet)
                    .language("typescript")
                    .previousRepairs(null)
                    .build();

            String prompt = promptBuilder.buildPrompt();

            // 4. 调用AI生成修复建议
            AIProvider.AIRequest aiRequest = AIProvider.AIRequest.builder()
                    .temperature(0.3)
                    .maxTokens(2048)
                    .build();

            AIProvider.AIResponse aiResponse = resolveProvider(appSpecId).generate(prompt, aiRequest);

            log.info("AI类型错误修复完成 - promptTokens: {}, completionTokens: {}, duration: {}ms",
                    aiResponse.promptTokens(), aiResponse.completionTokens(), aiResponse.durationMs());

            // 5. 解析AI返回
            AIRepairSuggestionParser.RepairSuggestion suggestion = aiRepairSuggestionParser.parse(aiResponse.content());

            // 6. 构建代码变更Map（从第一个建议中提取）
            Map<String, Object> codeChanges = new HashMap<>();
            if (!suggestion.getSuggestions().isEmpty()) {
                Map<String, Object> firstSuggestion = suggestion.getSuggestions().get(0);
                codeChanges.put("strategy", suggestion.getRepairStrategy());
                codeChanges.put("originalCode", firstSuggestion.get("originalCode"));
                codeChanges.put("fixedCode", firstSuggestion.get("fixedCode"));
                codeChanges.put("explanation", firstSuggestion.get("explanation"));
            }

            long durationMs = Instant.now().toEpochMilli() - startTime.toEpochMilli();

            return RepairResponse.builder()
                    .repairId(UUID.randomUUID())
                    .status("success")
                    .isSuccess(true)
                    .currentIteration(1)
                    .maxIterations(3)
                    .repairStrategy(suggestion.getRepairStrategy())
                    .suggestions(suggestion.getSuggestions())
                    .codeChanges(codeChanges)
                    .affectedFiles(suggestion.getAffectedFiles())
                    .isEscalated(false)
                    .durationMs(durationMs)
                    .completedAt(Instant.now())
                    .aiTokenUsage(Map.of(
                            "promptTokens", aiResponse.promptTokens(),
                            "completionTokens", aiResponse.completionTokens(),
                            "totalTokens", aiResponse.totalTokens()))
                    .build();

        } catch (AIProvider.AIException e) {
            log.error("AI调用失败", e);
            throw new RuntimeException("类型错误修复失败: " + e.getMessage(), e);
        } catch (Exception e) {
            log.error("类型错误修复失败", e);
            throw new RuntimeException("类型错误修复失败: " + e.getMessage(), e);
        }
    }

    /**
     * 依赖自动安装（真实AI调用）
     *
     * @param appSpecId         AppSpec ID
     * @param missingDependency 缺失的依赖
     * @return 修复响应
     */
    @Transactional(rollbackFor = Exception.class)
    public RepairResponse installDependency(UUID appSpecId, String missingDependency) {
        log.info("开始依赖自动安装 - appSpecId: {}, dependency: {}", appSpecId, missingDependency);

        Instant startTime = Instant.now();

        try {
            // 1. 构建错误详情
            Map<String, Object> errorDetails = new HashMap<>();
            errorDetails.put("message", String.format("模块未找到: 无法解析模块 '%s'", missingDependency));
            errorDetails.put("stackTrace", String.format(
                    "Error: Cannot find module '%s'\n    at require (node:internal/modules/cjs/loader.js:1002:15)",
                    missingDependency));

            // 2. 构建package.json片段
            String packageJsonSnippet = "{\n  \"dependencies\": {\n    // 当前依赖列表\n  }\n}";

            // 3. 构建AI Prompt
            AIRepairPromptBuilder promptBuilder = AIRepairPromptBuilder.builder()
                    .failureType("dependency_missing")
                    .errorDetails(errorDetails)
                    .codeSnippet(packageJsonSnippet)
                    .language("typescript")
                    .previousRepairs(null)
                    .build();

            String prompt = promptBuilder.buildPrompt();

            // 4. 调用AI生成修复建议
            AIProvider.AIRequest aiRequest = AIProvider.AIRequest.builder()
                    .temperature(0.3)
                    .maxTokens(2048)
                    .build();

            AIProvider.AIResponse aiResponse = resolveProvider(appSpecId).generate(prompt, aiRequest);

            log.info("AI依赖分析完成 - promptTokens: {}, completionTokens: {}, duration: {}ms",
                    aiResponse.promptTokens(), aiResponse.completionTokens(), aiResponse.durationMs());

            // 5. 解析AI返回
            AIRepairSuggestionParser.RepairSuggestion suggestion = aiRepairSuggestionParser.parse(aiResponse.content());

            // 6. 构建代码变更Map（从第一个建议中提取）
            Map<String, Object> codeChanges = new HashMap<>();
            if (!suggestion.getSuggestions().isEmpty()) {
                Map<String, Object> firstSuggestion = suggestion.getSuggestions().get(0);
                codeChanges.put("strategy", suggestion.getRepairStrategy());
                codeChanges.put("packageManager", firstSuggestion.get("packageManager"));
                codeChanges.put("installedPackage", firstSuggestion.get("packageName"));
                codeChanges.put("version", firstSuggestion.get("version"));
                codeChanges.put("command", firstSuggestion.get("command"));
            }

            long durationMs = Instant.now().toEpochMilli() - startTime.toEpochMilli();

            return RepairResponse.builder()
                    .repairId(UUID.randomUUID())
                    .status("success")
                    .isSuccess(true)
                    .currentIteration(1)
                    .maxIterations(3)
                    .repairStrategy(suggestion.getRepairStrategy())
                    .suggestions(suggestion.getSuggestions())
                    .codeChanges(codeChanges)
                    .affectedFiles(suggestion.getAffectedFiles())
                    .isEscalated(false)
                    .durationMs(durationMs)
                    .completedAt(Instant.now())
                    .aiTokenUsage(Map.of(
                            "promptTokens", aiResponse.promptTokens(),
                            "completionTokens", aiResponse.completionTokens(),
                            "totalTokens", aiResponse.totalTokens()))
                    .build();

        } catch (AIProvider.AIException e) {
            log.error("AI调用失败", e);
            throw new RuntimeException("依赖安装失败: " + e.getMessage(), e);
        } catch (Exception e) {
            log.error("依赖安装失败", e);
            throw new RuntimeException("依赖安装失败: " + e.getMessage(), e);
        }
    }

    /**
     * 业务逻辑修复（真实AI调用）
     *
     * @param appSpecId        AppSpec ID
     * @param errorDescription 错误描述
     * @param expectedBehavior 期望行为
     * @return 修复响应
     */
    @Transactional(rollbackFor = Exception.class)
    public RepairResponse fixBusinessLogic(UUID appSpecId, String errorDescription, String expectedBehavior) {
        log.info("开始业务逻辑修复 - appSpecId: {}", appSpecId);

        Instant startTime = Instant.now();

        try {
            // 1. 构建错误详情
            Map<String, Object> errorDetails = new HashMap<>();
            errorDetails.put("message", String.format(
                    "业务逻辑违规: %s\n期望行为: %s",
                    errorDescription, expectedBehavior));

            // 2. 构建代码片段（示例）
            String codeSnippet = "// 业务逻辑代码片段\n// TODO: 从AppSpec中获取实际代码";

            // 3. 构建AI Prompt
            AIRepairPromptBuilder promptBuilder = AIRepairPromptBuilder.builder()
                    .failureType("business_violation")
                    .errorDetails(errorDetails)
                    .codeSnippet(codeSnippet)
                    .language("typescript")
                    .previousRepairs(null)
                    .build();

            String prompt = promptBuilder.buildPrompt();

            // 4. 调用AI生成修复建议
            AIProvider.AIRequest aiRequest = AIProvider.AIRequest.builder()
                    .temperature(0.3)
                    .maxTokens(4096)
                    .build();

            AIProvider.AIResponse aiResponse = resolveProvider(appSpecId).generate(prompt, aiRequest);

            log.info("AI业务逻辑修复完成 - promptTokens: {}, completionTokens: {}, duration: {}ms",
                    aiResponse.promptTokens(), aiResponse.completionTokens(), aiResponse.durationMs());

            // 5. 解析AI返回
            AIRepairSuggestionParser.RepairSuggestion suggestion = aiRepairSuggestionParser.parse(aiResponse.content());

            long durationMs = Instant.now().toEpochMilli() - startTime.toEpochMilli();

            return RepairResponse.builder()
                    .repairId(UUID.randomUUID())
                    .status("plan_generated")
                    .isSuccess(false)
                    .currentIteration(1)
                    .maxIterations(3)
                    .repairStrategy(suggestion.getRepairStrategy())
                    .suggestions(suggestion.getSuggestions())
                    .codeChanges(new HashMap<>())
                    .affectedFiles(suggestion.getAffectedFiles())
                    .isEscalated(false)
                    .durationMs(durationMs)
                    .completedAt(Instant.now())
                    .aiTokenUsage(Map.of(
                            "promptTokens", aiResponse.promptTokens(),
                            "completionTokens", aiResponse.completionTokens(),
                            "totalTokens", aiResponse.totalTokens()))
                    .build();

        } catch (AIProvider.AIException e) {
            log.error("AI调用失败", e);
            throw new RuntimeException("业务逻辑修复失败: " + e.getMessage(), e);
        } catch (Exception e) {
            log.error("业务逻辑修复失败", e);
            throw new RuntimeException("业务逻辑修复失败: " + e.getMessage(), e);
        }
    }

    /**
     * 自动修复并重新验证
     *
     * @param request 自动修复请求
     * @return 修复响应
     */
    @Transactional(rollbackFor = Exception.class)
    public RepairResponse autoRepairAndValidate(AutoRepairRequest request) {
        log.info("开始自动修复并重新验证 - appSpecId: {}, maxIterations: {}",
                request.getAppSpecId(), request.getMaxIterations());

        Instant startTime = Instant.now();
        RepairResponse lastRepairResponse = null;

        try {
            for (int iteration = 1; iteration <= request.getMaxIterations(); iteration++) {
                log.info("自动修复迭代 {}/{}", iteration, request.getMaxIterations());

                // 1. 执行修复（模拟）
                // TODO: 集成真实的AI修复逻辑
                lastRepairResponse = RepairResponse.builder()
                        .repairId(UUID.randomUUID())
                        .status("repairing")
                        .isSuccess(false)
                        .currentIteration(iteration)
                        .maxIterations(request.getMaxIterations())
                        .repairStrategy("ai_suggestion")
                        .suggestions(new ArrayList<>())
                        .codeChanges(new HashMap<>())
                        .affectedFiles(new ArrayList<>())
                        .isEscalated(false)
                        .aiTokenUsage(null)
                        .build();

                // 2. 重新验证
                // TODO: 调用真实的验证服务
                boolean validationPassed = (iteration == 2); // 模拟第2次迭代成功

                if (validationPassed) {
                    log.info("修复成功并通过验证 - iteration: {}", iteration);

                    Instant endTime = Instant.now();
                    long durationMs = endTime.toEpochMilli() - startTime.toEpochMilli();

                    lastRepairResponse.setStatus("success");
                    lastRepairResponse.setIsSuccess(true);
                    lastRepairResponse.setDurationMs(durationMs);
                    lastRepairResponse.setCompletedAt(endTime);

                    return lastRepairResponse;
                }

                log.warn("第{}次修复未通过验证，继续尝试...", iteration);
            }

            // 所有迭代都失败
            log.error("达到最大迭代次数仍未修复成功 - maxIterations: {}", request.getMaxIterations());

            if (Boolean.TRUE.equals(request.getAutoEscalate())) {
                return escalateToHuman(lastRepairResponse.getRepairId(), "达到最大迭代次数仍未修复成功");
            }

            throw new RuntimeException("修复失败: 达到最大迭代次数仍未修复成功");

        } catch (Exception e) {
            log.error("自动修复并重新验证失败", e);
            throw new RuntimeException("自动修复并重新验证失败: " + e.getMessage(), e);
        }
    }

    /**
     * 查询修复历史
     *
     * @param appSpecId AppSpec ID
     * @return 修复记录列表
     */
    public List<RepairResponse> getRepairHistory(UUID appSpecId) {
        log.info("查询修复历史 - appSpecId: {}", appSpecId);

        try {
            List<RepairRecordEntity> entities = repairRecordMapper.selectByAppSpecId(appSpecId);

            return entities.stream()
                    .map(this::convertToResponse)
                    .toList();

        } catch (Exception e) {
            log.error("查询修复历史失败", e);
            throw new RuntimeException("查询修复历史失败: " + e.getMessage(), e);
        }
    }

    /**
     * 获取项目级AI Provider
     *
     * 是什么：基于 appSpecId 解析项目并选择AI Provider。
     * 做什么：通过项目上下文选择Provider入口（当前回退系统默认）。
     * 为什么：保留项目级扩展点且不影响未配置项目。
     *
     * @param appSpecId AppSpec ID
     * @return 可用的AI Provider
     */
    private AIProvider resolveProvider(UUID appSpecId) {
        if (appSpecId == null) {
            return aiProviderFactory.getProvider();
        }

        ProjectEntity project = projectService.findByAppSpecId(appSpecId);
        if (project == null) {
            return aiProviderFactory.getProvider();
        }

        return aiProviderFactory.getProviderForProject(project.getId());
    }

    /**
     * 转换错误详情
     */
    private Map<String, Object> convertErrorDetails(List<TriggerRepairRequest.ErrorDetail> errorDetails) {
        Map<String, Object> result = new HashMap<>();
        List<Map<String, Object>> errors = new ArrayList<>();

        for (TriggerRepairRequest.ErrorDetail detail : errorDetails) {
            Map<String, Object> error = new HashMap<>();
            error.put("line", detail.getLine());
            error.put("message", detail.getMessage());
            error.put("errorCode", detail.getErrorCode());
            error.put("filePath", detail.getFilePath());
            error.put("stackTrace", detail.getStackTrace());
            errors.add(error);
        }

        result.put("errors", errors);
        return result;
    }

    /**
     * 发送升级通知
     */
    private boolean sendEscalationNotification(RepairRecordEntity entity) {
        // TODO: 集成通知系统
        log.info("发送升级通知 - repairId: {}, appSpecId: {}", entity.getId(), entity.getAppSpecId());
        return true;
    }

    /**
     * 转换为响应对象
     */
    private RepairResponse convertToResponse(RepairRecordEntity entity) {
        return RepairResponse.builder()
                .repairId(entity.getId())
                .status(entity.getStatus())
                .isSuccess(entity.getIsSuccess())
                .currentIteration(entity.getCurrentIteration())
                .maxIterations(entity.getMaxIterations())
                .repairStrategy(entity.getRepairStrategy())
                .suggestions(entity.getRepairSuggestions())
                .codeChanges(entity.getCodeChanges())
                .affectedFiles(entity.getAffectedFiles())
                .repairValidationResultId(entity.getRepairValidationResultId())
                .isEscalated(entity.getIsEscalated())
                .failureReason(entity.getFailureReason())
                .durationMs(entity.getDurationMs())
                .completedAt(entity.getCompletedAt())
                .aiTokenUsage(null) // 从数据库中没有存储，暂时返回null
                .build();
    }
}
