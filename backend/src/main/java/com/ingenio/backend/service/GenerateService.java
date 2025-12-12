package com.ingenio.backend.service;

import java.time.Instant;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ingenio.backend.agent.ExecuteAgentFactory;
import com.ingenio.backend.agent.IExecuteAgent;
import com.ingenio.backend.agent.IValidateAgent;
import com.ingenio.backend.agent.PlanAgent;
import com.ingenio.backend.agent.ValidateAgentFactory;
import com.ingenio.backend.agent.dto.PlanResult;
import com.ingenio.backend.agent.dto.ValidateResult;
import com.ingenio.backend.common.exception.BusinessException;
import com.ingenio.backend.common.exception.ErrorCode;
import com.ingenio.backend.dto.request.GenerateFullRequest;
import com.ingenio.backend.dto.response.GenerateFullResponse;
import com.ingenio.backend.entity.AppSpecEntity;
import com.ingenio.backend.mapper.AppSpecMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 代码生成服务
 * 负责编排PlanAgent、ExecuteAgent、ValidateAgent的执行流程
 * 实现完整的需求→AppSpec→验证流程
 *
 * @author Ingenio Team
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GenerateService {

    private final PlanAgent planAgent;
    private final ExecuteAgentFactory executeAgentFactory;
    private final ValidateAgentFactory validateAgentFactory;
    private final AppSpecMapper appSpecMapper;
    private final ObjectMapper objectMapper;

    // 代码生成相关服务
    private final AICodeGenerator aiCodeGenerator;
    private final KotlinMultiplatformGenerator kmpGenerator;
    private final ComposeUIGenerator composeUIGenerator;
    private final CodePackagingService packagingService;

    /**
     * 完整生成流程：Plan → Execute → Validate
     *
     * @param request 生成请求
     * @return 生成结果
     * @throws BusinessException 当生成失败时抛出
     */
    @Transactional(rollbackFor = Exception.class)
    public GenerateFullResponse generateFull(GenerateFullRequest request) {
        log.info("开始完整生成流程 - userRequirement: {}", request.getUserRequirement());

        long startTime = System.currentTimeMillis();
        GenerateFullResponse.GenerateFullResponseBuilder responseBuilder = GenerateFullResponse.builder();

        try {
            // Step 1: PlanAgent规划
            log.info("Step 1: PlanAgent开始规划");
            responseBuilder.status("planning");
            PlanResult planResult = planAgent.plan(request.getUserRequirement());
            responseBuilder.planResult(planResult);
            log.info("Step 1: PlanAgent规划完成 - modules: {}, complexityScore: {}",
                    planResult.getModules().size(), planResult.getComplexityScore());

            // Step 2: ExecuteAgent生成AppSpec
            log.info("Step 2: ExecuteAgent开始生成AppSpec");
            responseBuilder.status("executing");
            // V2.0: 通过Factory动态选择V1/V2实现
            IExecuteAgent executeAgent = executeAgentFactory.getExecuteAgent();
            Map<String, Object> appSpecJson = executeAgent.execute(planResult);
            log.info("Step 2: ExecuteAgent生成完成 - version: {}, appSpec keys: {}",
                    executeAgent.getVersion(), appSpecJson.keySet());

            // 保存AppSpec到数据库（V2.0: 传递planResult保存意图识别结果）
            AppSpecEntity appSpec = saveAppSpec(request.getUserRequirement(), appSpecJson, planResult);
            responseBuilder.appSpecId(appSpec.getId());

            // Step 3: ValidateAgent验证
            log.info("Step 3: ValidateAgent开始验证");
            responseBuilder.status("validating");
            // V2.0: 通过Factory动态选择V1/V2实现
            IValidateAgent validateAgent = validateAgentFactory.getValidateAgent();
            Map<String, Object> validateResultMap = validateAgent.validate(appSpecJson);
            // Convert Map back to ValidateResult for response builder
            ValidateResult validateResult = objectMapper.convertValue(validateResultMap, ValidateResult.class);
            responseBuilder.validateResult(validateResult);
            responseBuilder.isValid(validateResult.getIsValid());
            responseBuilder.qualityScore(validateResult.getQualityScore());
            log.info("Step 3: ValidateAgent验证完成 - version: {}, isValid: {}, qualityScore: {}",
                    validateAgent.getVersion(), validateResult.getIsValid(), validateResult.getQualityScore());

            // 检查验证结果
            if (!request.getSkipValidation() && !validateResult.getIsValid()) {
                log.warn("验证未通过 - qualityScore: {}, threshold: {}",
                        validateResult.getQualityScore(), request.getQualityThreshold());
                responseBuilder.status("failed");
                responseBuilder.errorMessage("AppSpec质量不达标，请优化需求后重试");
            } else if (validateResult.getQualityScore() < request.getQualityThreshold()) {
                log.warn("质量评分低于阈值 - qualityScore: {}, threshold: {}",
                        validateResult.getQualityScore(), request.getQualityThreshold());
                responseBuilder.status("failed");
                responseBuilder.errorMessage(
                        String.format("质量评分(%d)低于阈值(%d)，请优化需求后重试",
                                validateResult.getQualityScore(), request.getQualityThreshold()));
            } else {
                // Step 4: 生成代码（如果需要）
                if (Boolean.TRUE.equals(request.getGeneratePreview())) {
                    log.info("Step 4: 开始生成代码");
                    responseBuilder.status("generating");

                    try {
                        // 生成所有代码文件
                        Map<String, String> generatedFiles = generateAllCodeFiles(
                                planResult,
                                appSpecJson,
                                request
                        );

                        // 打包并上传
                        String projectName = extractAppName(appSpecJson);
                        String downloadUrl = packagingService.packageAndUpload(
                                generatedFiles,
                                projectName
                        );

                        // 设置响应
                        responseBuilder.codeDownloadUrl(downloadUrl);
                        responseBuilder.generatedFileList(new java.util.ArrayList<>(generatedFiles.keySet()));
                        responseBuilder.codeSummary(buildCodeSummary(generatedFiles, projectName));

                        log.info("Step 4: 代码生成完成 - totalFiles: {}, url: {}",
                                generatedFiles.size(), downloadUrl);

                    } catch (Exception e) {
                        log.error("Step 4: 代码生成失败", e);
                        responseBuilder.status("failed");
                        responseBuilder.errorMessage("代码生成失败: " + e.getMessage());
                        // 不抛出异常，允许返回失败响应
                        return responseBuilder.build();
                    }
                }

                responseBuilder.status("completed");
                log.info("完整生成流程完成 - appSpecId: {}", appSpec.getId());
            }

            // 计算耗时
            long duration = System.currentTimeMillis() - startTime;
            responseBuilder.durationMs(duration);
            responseBuilder.generatedAt(Instant.now());

            // TODO: 统计Token使用量（需要集成ChatModel的使用统计）
            responseBuilder.tokenUsage(GenerateFullResponse.TokenUsage.builder()
                    .planTokens(0)
                    .executeTokens(0)
                    .validateTokens(0)
                    .totalTokens(0)
                    .estimatedCost(0.0)
                    .build());

            return responseBuilder.build();

        } catch (BusinessException e) {
            log.error("生成流程失败 - BusinessException: {}", e.getMessage(), e);
            throw e;
        } catch (Exception e) {
            log.error("生成流程失败 - Exception: {}", e.getMessage(), e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "生成失败: " + e.getMessage());
        }
    }

    /**
     * 保存AppSpec到数据库
     *
     * @param userRequirement 用户需求
     * @param appSpecJson AppSpec JSON
     * @param planResult Plan阶段结果（V2.0新增，包含IntentClassificationResult）
     * @return 保存的AppSpec实体
     */
    private AppSpecEntity saveAppSpec(String userRequirement, Map<String, Object> appSpecJson, PlanResult planResult) {
        try {
            // 开发环境使用默认租户ID和用户ID
            // TODO: 生产环境需要从当前登录用户获取
            UUID tenantId = UUID.fromString("00000000-0000-0000-0000-000000000001");
            UUID userId = UUID.fromString("00000000-0000-0000-0000-000000000001");

            // V2.0 新增：提取意图识别结果
            com.ingenio.backend.agent.dto.IntentClassificationResult intentResult =
                planResult.getIntentClassificationResult();

            // 构建AppSpec实体（包含V2.0字段）
            AppSpecEntity.AppSpecEntityBuilder builder = AppSpecEntity.builder()
                    .id(UUID.randomUUID())
                    .tenantId(tenantId)
                    .createdByUserId(userId)
                    .specContent(appSpecJson)
                    .version(1)
                    .status(AppSpecEntity.Status.DRAFT.getValue())
                    .createdAt(Instant.now())
                    .updatedAt(Instant.now());

            // V2.0 新增：设置意图识别相关字段
            if (intentResult != null) {
                // 意图类型（枚举名称）
                if (intentResult.getIntent() != null) {
                    builder.intentType(intentResult.getIntent().name());
                }

                // 置信度评分（0.00-1.00）
                if (intentResult.getConfidence() != null) {
                    builder.confidenceScore(java.math.BigDecimal.valueOf(intentResult.getConfidence()));
                }

                // 匹配的模板列表（TODO: 暂时留空，等待IndustryTemplate系统实现）
                // 未来可基于intentResult.getExtractedKeywords()查询IndustryTemplateRepository
                builder.matchedTemplates(new java.util.ArrayList<>());

                // 设计确认标志（初始为false）
                builder.designConfirmed(false);

                log.info("V2.0意图识别结果已设置 - intent={}, confidence={:.2f}%, referenceUrls={}",
                    intentResult.getIntent(),
                    intentResult.getConfidence() * 100,
                    intentResult.getReferenceUrls() != null ? intentResult.getReferenceUrls().size() : 0);
            } else {
                log.warn("PlanResult未包含IntentClassificationResult，V2.0字段保持默认值");
                builder.designConfirmed(false);
                builder.matchedTemplates(new java.util.ArrayList<>());
            }

            AppSpecEntity appSpec = builder.build();

            appSpecMapper.insert(appSpec);
            log.info("AppSpec保存成功 - id: {}, intentType: {}, confidence: {}",
                appSpec.getId(),
                appSpec.getIntentType(),
                appSpec.getConfidenceScore());

            return appSpec;
        } catch (Exception e) {
            log.error("保存AppSpec失败", e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "保存AppSpec失败: " + e.getMessage());
        }
    }

    /**
     * 生成所有代码文件
     *
     * @param planResult 规划结果
     * @param appSpecJson AppSpec JSON
     * @param request 生成请求
     * @return 生成的文件Map（key=文件路径, value=文件内容）
     */
    private Map<String, String> generateAllCodeFiles(
            PlanResult planResult,
            Map<String, Object> appSpecJson,
            GenerateFullRequest request
    ) {
        Map<String, String> allFiles = new HashMap<>();

        // 提取实体列表
        List<Map<String, Object>> entities = extractEntities(appSpecJson);
        String packageName = extractPackageName(appSpecJson, request);
        String appName = extractAppName(appSpecJson);

        log.info("开始生成代码: entities={}, packageName={}, appName={}",
                entities.size(), packageName, appName);

        // Step 4.1: 生成Kotlin Multiplatform代码
        log.info("生成Kotlin Multiplatform代码...");
        for (Map<String, Object> entity : entities) {
            // 数据模型
            String dataModel = kmpGenerator.generateDataModel(entity);
            String tableName = (String) entity.get("tableName");
            String className = toCamelCase(tableName);
            allFiles.put(
                    packageNameToPath(packageName) + "/data/model/" + className + ".kt",
                    dataModel
            );

            // Repository
            String repository = kmpGenerator.generateRepository(entity);
            allFiles.put(
                    packageNameToPath(packageName) + "/data/repository/" + className + "Repository.kt",
                    repository
            );

            // ViewModel
            String viewModel = kmpGenerator.generateViewModel(entity);
            allFiles.put(
                    packageNameToPath(packageName) + "/presentation/viewmodel/" + className + "ViewModel.kt",
                    viewModel
            );
        }

        // Step 4.2: 生成UI代码
        log.info("生成Compose UI代码...");
        for (Map<String, Object> entity : entities) {
            String tableName = (String) entity.get("tableName");
            String className = toCamelCase(tableName);

            // 列表界面
            String listScreen = composeUIGenerator.generateListScreen(entity);
            allFiles.put(
                    packageNameToPath(packageName) + "/presentation/screen/" + className + "ListScreen.kt",
                    listScreen
            );

            // 表单界面
            String formScreen = composeUIGenerator.generateFormScreen(entity);
            allFiles.put(
                    packageNameToPath(packageName) + "/presentation/screen/" + className + "FormScreen.kt",
                    formScreen
            );
        }

        // 导航配置
        String navigation = composeUIGenerator.generateNavigation(entities);
        allFiles.put(
                packageNameToPath(packageName) + "/presentation/navigation/AppNavigation.kt",
                navigation
        );

        // build.gradle.kts配置
        String buildConfig = kmpGenerator.generateBuildConfig(entities);
        allFiles.put("build.gradle.kts", buildConfig);

        // Step 4.3: 如果需要AI能力，生成AI代码
        if (planResult.getAiCapability() != null &&
                Boolean.TRUE.equals(planResult.getAiCapability().getNeedsAI())) {

            log.info("检测到AI能力需求，开始生成AI集成代码...");

            try {
                Map<String, String> aiFiles = aiCodeGenerator.generateAICode(
                        planResult.getAiCapability(),
                        packageName,
                        appName
                );

                allFiles.putAll(aiFiles);
                log.info("AI代码生成完成: 共{}个文件", aiFiles.size());

            } catch (Exception e) {
                log.error("AI代码生成失败，跳过AI集成", e);
                // 不中断流程，继续生成其他代码
            }
        } else {
            log.info("未检测到AI能力需求，跳过AI代码生成");
        }

        log.info("所有代码文件生成完成: totalFiles={}", allFiles.size());
        return allFiles;
    }

    /**
     * 从AppSpec提取实体列表
     *
     * @param appSpecJson AppSpec JSON
     * @return 实体列表
     */
    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> extractEntities(Map<String, Object> appSpecJson) {
        List<Map<String, Object>> entities = new java.util.ArrayList<>();

        // 从dataModels中提取实体
        if (appSpecJson.containsKey("dataModels")) {
            Object dataModelsObj = appSpecJson.get("dataModels");
            if (dataModelsObj instanceof List) {
                List<Map<String, Object>> dataModels = (List<Map<String, Object>>) dataModelsObj;
                entities.addAll(dataModels);
            }
        }

        log.debug("提取实体: count={}", entities.size());
        return entities;
    }

    /**
     * 从AppSpec提取包名
     *
     * @param appSpecJson AppSpec JSON
     * @param request 请求参数（可能包含用户指定的包名）
     * @return 包名
     */
    private String extractPackageName(Map<String, Object> appSpecJson, GenerateFullRequest request) {
        // 优先使用请求中的包名
        if (request.getPackageName() != null && !request.getPackageName().isEmpty()) {
            return request.getPackageName();
        }

        // 从AppSpec中提取
        if (appSpecJson.containsKey("packageName")) {
            return (String) appSpecJson.get("packageName");
        }

        // 使用应用名称生成默认包名
        String appName = extractAppName(appSpecJson);
        String cleanName = appName.toLowerCase()
                .replaceAll("[^a-z0-9]", "")
                .replaceAll("^[0-9]+", "");

        return "com.ingenio.generated." + cleanName;
    }

    /**
     * 从AppSpec提取应用名称
     *
     * @param appSpecJson AppSpec JSON
     * @return 应用名称
     */
    private String extractAppName(Map<String, Object> appSpecJson) {
        if (appSpecJson.containsKey("appName")) {
            return (String) appSpecJson.get("appName");
        }
        return "GeneratedApp";
    }

    /**
     * 构建代码生成摘要
     *
     * @param generatedFiles 生成的文件
     * @param projectName 项目名称
     * @return 代码生成摘要
     */
    private GenerateFullResponse.CodeGenerationSummary buildCodeSummary(
            Map<String, String> generatedFiles,
            String projectName
    ) {
        int totalFiles = generatedFiles.size();

        // 按文件类型统计
        int dataModelFiles = (int) generatedFiles.keySet().stream()
                .filter(path -> path.contains("/data/model/"))
                .count();

        int repositoryFiles = (int) generatedFiles.keySet().stream()
                .filter(path -> path.contains("/data/repository/"))
                .count();

        int viewModelFiles = (int) generatedFiles.keySet().stream()
                .filter(path -> path.contains("/presentation/viewmodel/"))
                .count();

        int uiScreenFiles = (int) generatedFiles.keySet().stream()
                .filter(path -> path.contains("/presentation/screen/"))
                .count();

        int aiIntegrationFiles = (int) generatedFiles.keySet().stream()
                .filter(path -> path.contains("/ai/") || path.contains("AIService") || path.contains("AIConfig"))
                .count();

        int configFiles = (int) generatedFiles.keySet().stream()
                .filter(path -> path.endsWith(".gradle") || path.endsWith(".properties") || path.endsWith(".env"))
                .count();

        int documentFiles = (int) generatedFiles.keySet().stream()
                .filter(path -> path.endsWith(".md") || path.endsWith(".txt"))
                .count();

        // 计算总大小
        long totalSize = generatedFiles.values().stream()
                .mapToLong(content -> content.getBytes(java.nio.charset.StandardCharsets.UTF_8).length)
                .sum();

        return GenerateFullResponse.CodeGenerationSummary.builder()
                .totalFiles(totalFiles)
                .databaseSchemaFiles(0) // TODO: 集成DatabaseSchemaGenerator后更新
                .dataModelFiles(dataModelFiles)
                .repositoryFiles(repositoryFiles)
                .viewModelFiles(viewModelFiles)
                .uiScreenFiles(uiScreenFiles)
                .aiIntegrationFiles(aiIntegrationFiles)
                .configFiles(configFiles)
                .documentFiles(documentFiles)
                .totalSize(totalSize)
                .zipFileName(projectName + "-" + UUID.randomUUID().toString().substring(0, 8) + ".zip")
                .build();
    }

    /**
     * 将包名转换为文件路径
     * 例如：com.example.myapp -> com/example/myapp
     */
    private String packageNameToPath(String packageName) {
        return packageName.replace(".", "/");
    }

    /**
     * 将下划线命名转换为驼峰命名
     * 例如：user_profile -> UserProfile
     */
    private String toCamelCase(String snakeCase) {
        String[] parts = snakeCase.split("_");
        StringBuilder camelCase = new StringBuilder();

        for (String part : parts) {
            if (!part.isEmpty()) {
                camelCase.append(Character.toUpperCase(part.charAt(0)));
                camelCase.append(part.substring(1).toLowerCase());
            }
        }

        return camelCase.toString();
    }
}
