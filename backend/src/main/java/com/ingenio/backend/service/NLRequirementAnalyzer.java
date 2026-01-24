package com.ingenio.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ingenio.backend.ai.AIProvider;
import com.ingenio.backend.ai.AIProviderFactory;
import com.ingenio.backend.dto.ComplexityAssessment;
import com.ingenio.backend.dto.ComplexityAssessment.ComplexityLevel;
import com.ingenio.backend.dto.TechStackRecommendation;
import com.ingenio.backend.dto.response.AnalysisProgressMessage;
import com.ingenio.backend.entity.GenerationTaskEntity;
import com.ingenio.backend.entity.StructuredRequirementEntity;
import com.ingenio.backend.mapper.StructuredRequirementMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * 自然语言需求分析器
 *
 * 使用 Google Gemini API (OpenAI 兼容接口) 理解用户的自然语言需求，提取结构化信息：
 * - 实体（entities）：数据模型，如Blog、Comment、Tag
 * - 关系（relationships）：实体间关系，如Blog 1:N Comment
 * - 操作（operations）：业务操作，如创建博客、发布评论
 * - 约束（constraints）：字段约束，如标题最大长度200
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class NLRequirementAnalyzer {

    /**
     * AI提供商工厂
     * 使用七牛云或阿里云DashScope等配置的AI服务
     */
    private final AIProviderFactory aiProviderFactory;

    private final StructuredRequirementMapper requirementMapper;
    private final ObjectMapper objectMapper;

    // 重试配置
    private static final int MAX_RETRIES = 2;
    private static final long RETRY_DELAY_MS = 5000;

    /**
     * 分析自然语言需求，提取结构化信息
     */
    public StructuredRequirementEntity analyze(String requirement, GenerationTaskEntity task) {
        log.info("开始需求分析: taskId={}, requirementLength={}",
                task.getId(), requirement.length());

        try {
            // 1. 调用AI API进行语义理解
            String analysisJson = callAIForAnalysisWithRetry(requirement, progress -> {
                // 空回调
            });

            // 2. 解析分析结果
            Map<String, Object> analysisResult = parseAnalysisResult(analysisJson);

            // 3. 创建结构化需求实体
            StructuredRequirementEntity entity = new StructuredRequirementEntity();
            entity.setId(UUID.randomUUID());
            entity.setTenantId(task.getTenantId());
            entity.setUserId(task.getUserId());
            entity.setTaskId(task.getId());
            entity.setRawRequirement(requirement);
            entity.setEntities(extractMap(analysisResult, "entities"));
            entity.setRelationships(extractMap(analysisResult, "relationships"));
            entity.setOperations(extractMap(analysisResult, "operations"));
            entity.setConstraints(extractMap(analysisResult, "constraints"));
            entity.setAiModel(getAiModelName());
            entity.setConfidenceScore(extractConfidenceScore(analysisResult));
            entity.setCreatedAt(Instant.now());
            entity.setUpdatedAt(Instant.now());

            // 4. 保存到数据库
            requirementMapper.insert(entity);

            log.info("需求分析完成: taskId={}, requirementId={}, entitiesCount={}, confidenceScore={}",
                    task.getId(), entity.getId(),
                    extractMap(analysisResult, "entities").size(),
                    entity.getConfidenceScore());

            return entity;

        } catch (Exception e) {
            log.error("需求分析失败: taskId={}", task.getId(), e);
            throw new RuntimeException("需求分析失败: " + e.getMessage(), e);
        }
    }

    /**
     * 检查AI服务是否可用
     */
    public boolean isConfigured() {
        try {
            AIProvider provider = aiProviderFactory.getProvider();
            return provider != null && provider.isAvailable();
        } catch (Exception e) {
            log.warn("AI服务不可用: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 带重试和心跳进度的AI API调用
     */
    private String callAIForAnalysisWithRetry(
            String requirement,
            Consumer<AnalysisProgressMessage> progressCallback) throws Exception {

        Exception lastException = null;

        for (int attempt = 1; attempt <= MAX_RETRIES + 1; attempt++) {
            try {
                log.info("调用AI API进行需求分析 (尝试 {}/{})", attempt, MAX_RETRIES + 1);

                // 启动心跳线程
                final java.util.concurrent.atomic.AtomicBoolean isCompleted = new java.util.concurrent.atomic.AtomicBoolean(
                        false);
                final java.util.concurrent.atomic.AtomicInteger heartbeatCount = new java.util.concurrent.atomic.AtomicInteger(
                        0);

                java.util.concurrent.ScheduledExecutorService heartbeat = java.util.concurrent.Executors
                        .newSingleThreadScheduledExecutor();

                heartbeat.scheduleAtFixedRate(() -> {
                    if (!isCompleted.get()) {
                        int count = heartbeatCount.incrementAndGet();
                        int elapsed = count * 3;
                        int progress = Math.min(1 + count, 19);

                        progressCallback.accept(AnalysisProgressMessage.builder()
                                .step(1)
                                .stepName("需求解析")
                                .status(AnalysisProgressMessage.StepStatus.RUNNING)
                                .description(String.format("AI正在深度分析中...已用时%d秒（预计60-90秒）", elapsed))
                                .progress(progress)
                                .result(Map.of("heartbeat", count, "elapsed", elapsed))
                                .timestamp(Instant.now())
                                .build());
                    }
                }, 3, 3, TimeUnit.SECONDS);

                try {
                    // 调用AI API（使用配置的AI提供商）
                    String result = callAIForAnalysis(requirement);

                    isCompleted.set(true);
                    heartbeat.shutdown();
                    return result;

                } catch (Exception e) {
                    isCompleted.set(true);
                    heartbeat.shutdown();
                    throw e;
                }

            } catch (Exception e) {
                lastException = e;
                String errorMsg = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
                boolean isTimeout = errorMsg.contains("timeout") || errorMsg.contains("SocketTimeout");

                if (isTimeout && attempt <= MAX_RETRIES) {
                    log.warn("AI API超时，等待重试...");
                    Thread.sleep(RETRY_DELAY_MS);
                } else {
                    break;
                }
            }
        }
        throw lastException;
    }

    /**
     * 调用配置的AI提供商进行需求分析
     *
     * 使用AIProviderFactory自动选择可用的AI提供商（七牛云/阿里云等）
     */
    private String callAIForAnalysis(String requirement) throws Exception {
        log.info("使用配置的AI提供商进行需求分析");

        String systemPrompt = buildAnalysisPrompt();
        String userPrompt = "请分析以下需求描述：\n\n" + requirement;

        // 组合成完整的提示词
        String fullPrompt = systemPrompt + "\n\n" + userPrompt;

        try {
            // 获取可用的AI提供商
            AIProvider provider = aiProviderFactory.getProvider();
            log.info("使用AI提供商: {}", provider.getProviderDisplayName());

            // 调用AI生成（使用默认参数）
            AIProvider.AIResponse response = provider.generate(fullPrompt);

            // AIResponse是record类型，使用content()方法获取内容
            String content = response.content();
            if (content == null || content.isBlank()) {
                throw new RuntimeException("AI返回内容为空");
            }

            log.debug("AI分析结果: {}", content);
            return content;

        } catch (AIProvider.AIException e) {
            log.error("AI提供商调用失败", e);
            throw new RuntimeException("AI服务调用失败: " + e.getMessage(), e);
        }
    }

    /**
     * 获取当前使用的AI模型名称
     */
    private String getAiModelName() {
        try {
            AIProvider provider = aiProviderFactory.getProvider();
            return provider.getProviderName();
        } catch (Exception e) {
            return "unknown";
        }
    }

    /**
     * 构建AI分析的系统提示词
     *
     * 要求AI返回完整的结构化分析结果，包括：
     * - entities: 数据实体
     * - relationships: 实体关系
     * - operations: 业务操作
     * - constraints: 约束条件
     * - techStack: 技术栈推荐
     * - complexity: 复杂度评估
     * - needsConfirmation: 是否需要与用户确认技术选型
     */
    private String buildAnalysisPrompt() {
        return """
                You are a professional software architect for Ingenio (秒构AI) platform.
                Analyze the user requirement comprehensively.

                ===== CRITICAL: Tech Stack Selection Rules =====

                【技术栈选择核心原则】

                1. 需要原生功能调用的多端应用 → 使用 "Kuikly" 框架
                   Kuikly适用场景（需要原生能力）：
                   - 相机、GPS定位、传感器、蓝牙等硬件调用
                   - 推送通知、后台任务、本地存储大量数据
                   - 高性能渲染（游戏、动画、图形处理）
                   - 需要App Store/Play Store上架的原生应用
                   - 离线优先应用、需要深度系统集成的应用

                2. 普通多端应用（无原生功能需求）→ 使用 "H5 + WebView" (套壳方案)
                   H5+WebView适用场景：
                   - 内容展示类应用（新闻、博客、文档）
                   - 简单表单、列表、数据管理应用
                   - 电商展示、信息查询类应用
                   - 不需要复杂原生交互的应用
                   - 快速迭代、频繁更新的应用

                3. 纯Web应用 → 使用 "React + Supabase"
                   Web-only适用场景：
                   - 仅在浏览器运行的应用
                   - SaaS管理后台
                   - 数据可视化Dashboard

                4. 复杂企业级应用（>8实体）→ 使用 "React + Spring Boot + PostgreSQL"

                【关键词识别】
                需要Kuikly的关键词：相机、摄像头、GPS、定位、蓝牙、NFC、指纹、Face ID、
                                   推送通知、后台下载、离线、本地数据库、传感器、陀螺仪、
                                   App Store、Play Store、原生、高性能、游戏

                可用H5+WebView的关键词：展示、浏览、查询、表单、列表、内容、文章、
                                       商品展示、信息展示、简单交互

                【不确定时】
                如果无法明确判断是否需要原生功能，设置 "needsConfirmation": true，
                并在reason中说明需要与用户确认的点。
                ==============================================

                Return a JSON object with the following structure:
                {
                  "entities": {
                    "EntityName": {
                      "fields": ["field1", "field2"],
                      "description": "Entity description"
                    }
                  },
                  "relationships": {
                    "RelationName": {
                      "from": "Entity1",
                      "to": "Entity2",
                      "type": "one-to-many|many-to-many|one-to-one"
                    }
                  },
                  "operations": {
                    "OperationName": {
                      "type": "CRUD|business",
                      "description": "What this operation does"
                    }
                  },
                  "constraints": {
                    "ConstraintName": {
                      "type": "validation|business|security",
                      "description": "Constraint description"
                    }
                  },
                  "techStack": {
                    "platform": "Kuikly|H5+WebView|Web|React Native",
                    "frontend": "Kuikly|React|Vue|H5",
                    "backend": "Supabase|Spring Boot|Node.js|Firebase",
                    "database": "SQLite|PostgreSQL|MySQL|MongoDB",
                    "needsNativeFeatures": true/false,
                    "nativeFeatures": ["camera", "gps", "bluetooth"],
                    "needsConfirmation": true/false,
                    "reason": "Why this tech stack is recommended, and what needs user confirmation if any"
                  },
                  "complexity": {
                    "level": "SIMPLE|MEDIUM|COMPLEX",
                    "estimatedDays": 5,
                    "estimatedLines": 1000,
                    "riskFactors": ["risk1", "risk2"],
                    "reason": "Why this complexity level"
                  },
                  "confidence": 0.85
                }

                Analyze based on:
                - Number and complexity of entities (≤3 = SIMPLE, 4-8 = MEDIUM, >8 = COMPLEX)
                - Whether native device features are needed (camera, GPS, sensors, etc.)
                - Business logic requirements
                - User interaction patterns
                - Data relationships
                - Security requirements

                Return ONLY valid JSON, no markdown, no explanation.
                """;
    }

    private Map<String, Object> parseAnalysisResult(String analysisJson) {
        try {
            String jsonContent = analysisJson;
            if (analysisJson.contains("```json")) {
                int start = analysisJson.indexOf("```json") + 7;
                int end = analysisJson.lastIndexOf("```");
                if (end > start) {
                    jsonContent = analysisJson.substring(start, end).trim();
                }
            } else if (analysisJson.contains("```")) {
                int start = analysisJson.indexOf("```") + 3;
                int end = analysisJson.lastIndexOf("```");
                if (end > start) {
                    jsonContent = analysisJson.substring(start, end).trim();
                }
            }
            return objectMapper.readValue(jsonContent, Map.class);
        } catch (Exception e) {
            log.error("解析分析结果失败", e);
            return new HashMap<>();
        }
    }

    private Map<String, Object> extractMap(Map<String, Object> source, String key) {
        Object value = source.get(key);
        if (value instanceof Map) {
            return (Map<String, Object>) value;
        } else if (value instanceof List) {
            Map<String, Object> map = new HashMap<>();
            map.put("items", value);
            return map;
        }
        return new HashMap<>();
    }

    private BigDecimal extractConfidenceScore(Map<String, Object> analysisResult) {
        Object confidence = analysisResult.get("confidence");
        if (confidence instanceof Number) {
            return new BigDecimal(confidence.toString());
        }
        return BigDecimal.ZERO;
    }

    /**
     * 从AI分析结果中提取技术栈推荐
     *
     * 技术栈选择规则：
     * 1. 需要原生功能调用（相机、GPS、蓝牙等）→ Kuikly
     * 2. 普通多端应用（无原生需求）→ H5+WebView（套壳方案）
     * 3. 纯Web应用 → React + Supabase
     * 4. 复杂企业应用 → React + Spring Boot + PostgreSQL
     *
     * @param analysisResult AI返回的完整分析结果
     * @return 技术栈推荐
     */
    public TechStackRecommendation recommendTechStack(Map<String, Object> analysisResult) {
        Map<String, Object> techStack = extractMap(analysisResult, "techStack");

        if (techStack.isEmpty()) {
            log.warn("AI未返回techStack，使用默认推荐（H5+WebView）");
            return getDefaultTechStack();
        }

        String platform = getStringValue(techStack, "platform", "H5+WebView");
        String frontend = getStringValue(techStack, "frontend", "H5");
        String backend = getStringValue(techStack, "backend", "Supabase");
        String database = getStringValue(techStack, "database", "PostgreSQL");
        String reason = getStringValue(techStack, "reason", "基于需求分析的智能推荐");

        // 提取原生功能相关信息
        boolean needsNativeFeatures = getBooleanValue(techStack, "needsNativeFeatures", false);
        boolean needsConfirmation = getBooleanValue(techStack, "needsConfirmation", false);
        Object nativeFeatures = techStack.get("nativeFeatures");

        log.info("AI推荐技术栈: platform={}, frontend={}, backend={}, database={}, needsNative={}, needsConfirm={}",
                platform, frontend, backend, database, needsNativeFeatures, needsConfirmation);

        if (nativeFeatures != null) {
            log.info("  需要的原生功能: {}", nativeFeatures);
        }

        if (needsConfirmation) {
            log.info("  ⚠️ 需要与用户确认技术选型: {}", reason);
        }

        return TechStackRecommendation.builder()
                .platform(platform)
                .uiFramework(frontend)
                .backend(backend)
                .database(database)
                .confidence(needsConfirmation ? 0.6 : 0.85) // 需要确认时降低置信度
                .reason(reason)
                .build();
    }

    /**
     * 兼容旧接口（保留）
     */
    public TechStackRecommendation recommendTechStack(StructuredRequirementEntity requirement) {
        return getDefaultTechStack();
    }

    /**
     * 默认技术栈推荐
     *
     * 默认使用 H5+WebView 方案（套壳），适用于：
     * - 大部分简单应用（不需要原生功能）
     * - 内容展示、表单、列表类应用
     * - 快速迭代、频繁更新的应用
     *
     * 只有明确需要原生功能（相机、GPS、蓝牙等）才推荐 Kuikly
     */
    private TechStackRecommendation getDefaultTechStack() {
        return TechStackRecommendation.builder()
                .platform("H5+WebView")
                .uiFramework("H5")
                .backend("Supabase")
                .database("PostgreSQL")
                .confidence(0.8)
                .reason("简单应用推荐使用 H5+WebView 方案（套壳），快速开发、易于更新。如需原生功能（相机、GPS等）请告知，将推荐 Kuikly 框架")
                .build();
    }

    /**
     * 从AI分析结果中提取复杂度评估
     *
     * @param analysisResult AI返回的完整分析结果
     * @return 复杂度评估
     */
    public ComplexityAssessment assessComplexity(Map<String, Object> analysisResult) {
        Map<String, Object> complexity = extractMap(analysisResult, "complexity");

        if (complexity.isEmpty()) {
            log.warn("AI未返回complexity，使用默认评估");
            return getDefaultComplexity(analysisResult);
        }

        String levelStr = getStringValue(complexity, "level", "MEDIUM");
        ComplexityLevel level = parseComplexityLevel(levelStr);

        int estimatedDays = getIntValue(complexity, "estimatedDays", 5);
        int estimatedLines = getIntValue(complexity, "estimatedLines", 1000);
        String reason = getStringValue(complexity, "reason", "基于需求分析的智能评估");

        log.info("AI评估复杂度: level={}, days={}, lines={}",
                level, estimatedDays, estimatedLines);

        return ComplexityAssessment.builder()
                .level(level)
                .estimatedDays(estimatedDays)
                .estimatedLines(estimatedLines)
                .confidence(0.85)
                .description(reason)
                .build();
    }

    /**
     * 兼容旧接口（保留）
     */
    public ComplexityAssessment assessComplexity(StructuredRequirementEntity requirement) {
        return ComplexityAssessment.builder()
                .level(ComplexityLevel.MEDIUM)
                .estimatedDays(5)
                .estimatedLines(1000)
                .confidence(0.7)
                .description("默认评估")
                .build();
    }

    /**
     * 基于实体数量计算默认复杂度
     */
    private ComplexityAssessment getDefaultComplexity(Map<String, Object> analysisResult) {
        Map<String, Object> entities = extractMap(analysisResult, "entities");
        Map<String, Object> operations = extractMap(analysisResult, "operations");

        int entityCount = entities.size();
        int operationCount = operations.size();

        ComplexityLevel level;
        int days;
        int lines;

        // 枚举值: SIMPLE, MEDIUM, COMPLEX
        if (entityCount <= 3 && operationCount <= 5) {
            level = ComplexityLevel.SIMPLE;
            days = 2;
            lines = 500;
        } else if (entityCount <= 10 && operationCount <= 15) {
            level = ComplexityLevel.MEDIUM;
            days = 5;
            lines = 1500;
        } else {
            level = ComplexityLevel.COMPLEX;
            days = 10;
            lines = 3000;
        }

        return ComplexityAssessment.builder()
                .level(level)
                .estimatedDays(days)
                .estimatedLines(lines)
                .confidence(0.7)
                .description(String.format("基于 %d 个实体和 %d 个操作的自动评估", entityCount, operationCount))
                .build();
    }

    /**
     * 解析复杂度级别字符串
     * 支持多种格式: SIMPLE/LOW, MEDIUM, COMPLEX/HIGH
     */
    private ComplexityLevel parseComplexityLevel(String levelStr) {
        if (levelStr == null)
            return ComplexityLevel.MEDIUM;

        String upper = levelStr.toUpperCase().trim();

        // 映射AI可能返回的各种格式
        return switch (upper) {
            case "SIMPLE", "LOW", "EASY" -> ComplexityLevel.SIMPLE;
            case "MEDIUM", "MODERATE", "NORMAL" -> ComplexityLevel.MEDIUM;
            case "COMPLEX", "HIGH", "HARD", "VERY_HIGH" -> ComplexityLevel.COMPLEX;
            default -> ComplexityLevel.MEDIUM;
        };
    }

    private String getStringValue(Map<String, Object> map, String key, String defaultValue) {
        Object value = map.get(key);
        return value != null ? value.toString() : defaultValue;
    }

    private int getIntValue(Map<String, Object> map, String key, int defaultValue) {
        Object value = map.get(key);
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        return defaultValue;
    }

    private boolean getBooleanValue(Map<String, Object> map, String key, boolean defaultValue) {
        Object value = map.get(key);
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        if (value instanceof String) {
            return Boolean.parseBoolean((String) value);
        }
        return defaultValue;
    }

    /**
     * 带进度回调的流式需求分析
     *
     * 5个步骤：
     * 1. 需求语义解析 (0-20%)：调用AI理解自然语言需求
     * 2. 实体关系建模 (20-40%)：提取数据实体和关系
     * 3. 功能意图识别 (40-60%)：分析功能模块和业务逻辑
     * 4. 技术架构选型 (60-80%)：推荐技术栈和设计模式
     * 5. 复杂度与风险评估 (80-100%)：评估开发成本和风险
     */
    public StructuredRequirementEntity analyzeWithProgress(
            String requirement,
            Consumer<AnalysisProgressMessage> progressCallback) {

        log.info("开始流式需求分析: requirementLength={}", requirement.length());

        try {
            // ============ 步骤1：需求语义解析 ============
            progressCallback.accept(AnalysisProgressMessage.builder()
                    .step(1)
                    .stepName("需求语义解析")
                    .status(AnalysisProgressMessage.StepStatus.RUNNING)
                    .description("正在调用AI模型理解您的需求...")
                    .detail("AI正在深度分析您的自然语言描述，提取核心意图和关键信息")
                    .progress(5)
                    .timestamp(Instant.now())
                    .build());

            // 调用AI进行分析（这是最耗时的步骤）
            String analysisJson = callAIForAnalysisWithRetry(requirement, progressCallback);

            // 步骤1完成
            progressCallback.accept(AnalysisProgressMessage.builder()
                    .step(1)
                    .stepName("需求语义解析")
                    .status(AnalysisProgressMessage.StepStatus.COMPLETED)
                    .description("AI已成功理解您的需求")
                    .detail("需求解析完成，正在提取结构化信息...")
                    .progress(20)
                    .result(Map.of("rawLength", requirement.length(), "aiModel", getAiModelName()))
                    .timestamp(Instant.now())
                    .build());

            // 解析AI返回的JSON
            Map<String, Object> analysisResult = parseAnalysisResult(analysisJson);

            // ============ 步骤2：实体关系建模 ============
            progressCallback.accept(AnalysisProgressMessage.builder()
                    .step(2)
                    .stepName("实体关系建模")
                    .status(AnalysisProgressMessage.StepStatus.RUNNING)
                    .description("正在识别核心数据实体与关联...")
                    .detail("从需求中提取数据模型：表结构、字段、主外键关系")
                    .progress(25)
                    .timestamp(Instant.now())
                    .build());

            Map<String, Object> entities = extractMap(analysisResult, "entities");
            Map<String, Object> relationships = extractMap(analysisResult, "relationships");

            // 模拟处理时间
            Thread.sleep(500);

            progressCallback.accept(AnalysisProgressMessage.builder()
                    .step(2)
                    .stepName("实体关系建模")
                    .status(AnalysisProgressMessage.StepStatus.COMPLETED)
                    .description("数据模型构建完成")
                    .detail(String.format("识别到 %d 个实体，%d 个关系",
                            entities.size(), relationships.size()))
                    .progress(40)
                    .result(Map.of(
                            "entitiesCount", entities.size(),
                            "relationshipsCount", relationships.size(),
                            "entities", entities.keySet()))
                    .timestamp(Instant.now())
                    .build());

            // ============ 步骤3：功能意图识别 ============
            progressCallback.accept(AnalysisProgressMessage.builder()
                    .step(3)
                    .stepName("功能意图识别")
                    .status(AnalysisProgressMessage.StepStatus.RUNNING)
                    .description("正在分析所需的功能模块与业务逻辑...")
                    .detail("提取CRUD操作、业务流程、用户交互场景")
                    .progress(45)
                    .timestamp(Instant.now())
                    .build());

            Map<String, Object> operations = extractMap(analysisResult, "operations");
            Map<String, Object> constraints = extractMap(analysisResult, "constraints");

            Thread.sleep(500);

            progressCallback.accept(AnalysisProgressMessage.builder()
                    .step(3)
                    .stepName("功能意图识别")
                    .status(AnalysisProgressMessage.StepStatus.COMPLETED)
                    .description("功能模块分析完成")
                    .detail(String.format("识别到 %d 个操作，%d 个约束条件",
                            operations.size(), constraints.size()))
                    .progress(60)
                    .result(Map.of(
                            "operationsCount", operations.size(),
                            "constraintsCount", constraints.size(),
                            "operations", operations.keySet()))
                    .timestamp(Instant.now())
                    .build());

            // ============ 步骤4：技术架构选型 ============
            progressCallback.accept(AnalysisProgressMessage.builder()
                    .step(4)
                    .stepName("技术架构选型")
                    .status(AnalysisProgressMessage.StepStatus.RUNNING)
                    .description("正在从AI分析结果中提取技术栈推荐...")
                    .detail("根据需求复杂度和功能特性智能匹配技术方案")
                    .progress(65)
                    .timestamp(Instant.now())
                    .build());

            // 从AI分析结果中提取技术栈推荐（真实AI分析结果）
            TechStackRecommendation techStack = recommendTechStack(analysisResult);

            Thread.sleep(300);

            progressCallback.accept(AnalysisProgressMessage.builder()
                    .step(4)
                    .stepName("技术架构选型")
                    .status(AnalysisProgressMessage.StepStatus.COMPLETED)
                    .description("技术方案已确定")
                    .detail(String.format("推荐: %s + %s + %s",
                            techStack.getPlatform(), techStack.getUiFramework(), techStack.getBackend()))
                    .progress(80)
                    .result(Map.of(
                            "platform", techStack.getPlatform(),
                            "uiFramework", techStack.getUiFramework(),
                            "backend", techStack.getBackend(),
                            "database", techStack.getDatabase(),
                            "confidence", techStack.getConfidence()))
                    .timestamp(Instant.now())
                    .build());

            // ============ 步骤5：复杂度与风险评估 ============
            progressCallback.accept(AnalysisProgressMessage.builder()
                    .step(5)
                    .stepName("复杂度与风险评估")
                    .status(AnalysisProgressMessage.StepStatus.RUNNING)
                    .description("正在从AI分析结果中提取复杂度评估...")
                    .detail("基于实体数量、操作复杂度、业务逻辑评估项目规模")
                    .progress(85)
                    .timestamp(Instant.now())
                    .build());

            // 从AI分析结果中提取复杂度评估（真实AI分析结果）
            ComplexityAssessment complexity = assessComplexity(analysisResult);
            BigDecimal confidenceScore = extractConfidenceScore(analysisResult);

            Thread.sleep(300);

            progressCallback.accept(AnalysisProgressMessage.builder()
                    .step(5)
                    .stepName("复杂度与风险评估")
                    .status(AnalysisProgressMessage.StepStatus.COMPLETED)
                    .description("评估完成")
                    .detail(String.format("复杂度: %s，预计 %d 天，约 %d 行代码",
                            complexity.getLevel(), complexity.getEstimatedDays(), complexity.getEstimatedLines()))
                    .progress(90)
                    .result(Map.of(
                            "complexityLevel", complexity.getLevel().name(),
                            "estimatedDays", complexity.getEstimatedDays(),
                            "estimatedLines", complexity.getEstimatedLines(),
                            "confidenceScore", confidenceScore))
                    .timestamp(Instant.now())
                    .build());

            // ============ 步骤6：Ultrathink 深度规划 ============
            progressCallback.accept(AnalysisProgressMessage.builder()
                    .step(6)
                    .stepName("Ultrathink 深度规划")
                    .status(AnalysisProgressMessage.StepStatus.RUNNING)
                    .description("正在生成技术实施蓝图...")
                    .detail("构建系统架构、数据流图与实施路径")
                    .progress(95)
                    .timestamp(Instant.now())
                    .build());

            String technicalBlueprint = generateTechnicalBlueprint(analysisResult, techStack, complexity);

            // 模拟打字机效果的延迟，让前端展示更自然
            Thread.sleep(800);

            progressCallback.accept(AnalysisProgressMessage.builder()
                    .step(6)
                    .stepName("Ultrathink 深度规划")
                    .status(AnalysisProgressMessage.StepStatus.COMPLETED)
                    .description("技术蓝图构建完成")
                    .detail(technicalBlueprint)
                    .progress(100)
                    .result(Map.of(
                            "blueprint", technicalBlueprint,
                            "sections", 4))
                    .timestamp(Instant.now())
                    .build());

            // ============ 构建最终实体 ============
            StructuredRequirementEntity entity = new StructuredRequirementEntity();
            entity.setId(UUID.randomUUID());
            entity.setRawRequirement(requirement);
            entity.setEntities(entities);
            entity.setRelationships(relationships);
            entity.setOperations(operations);
            entity.setConstraints(constraints);
            entity.setAiModel(getAiModelName());
            entity.setConfidenceScore(confidenceScore);
            entity.setCreatedAt(Instant.now());
            entity.setUpdatedAt(Instant.now());

            log.info("流式需求分析完成: entitiesCount={}, operationsCount={}, confidence={}",
                    entities.size(), operations.size(), confidenceScore);

            return entity;

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Analysis interrupted", e);
        } catch (Exception e) {
            log.error("流式需求分析失败", e);
            throw new RuntimeException("Analysis failed: " + e.getMessage(), e);
        }
    }

    /**
     * 生成技术蓝图 Markdown（完整版）
     * 
     * 包含 Step1~5 的完整上下文：
     * 1. 系统架构 - 技术栈选型（Step 4）
     * 2. UI 设计风格 - 界面设计要求（Step 1 + 自动生成）
     * 3. 产品功能规划 - 核心功能与用户故事（Step 1 + Step 3）
     * 4. 目标用户画像（Step 1）
     * 5. 数据领域模型 - 实体定义（Step 2）
     * 6. 实体关系图（Step 2）
     * 7. API 与逻辑层 - 功能操作（Step 3）
     * 8. 业务约束条件（Step 3）
     * 9. 复杂度与风险评估（Step 5）
     * 10. 执行策略（前端/服务端/数据库规范）
     */
    private String generateTechnicalBlueprint(Map<String, Object> analysisResult, TechStackRecommendation techStack,
            ComplexityAssessment complexity) {
        StringBuilder sb = new StringBuilder();
        sb.append("# 🚀 技术实施蓝图\n\n");

        // ============ 1. 系统架构（Step 4 技术选型）============
        sb.append("## 1. 系统架构\n");
        sb.append("**平台**: ").append(techStack.getPlatform()).append("\n");
        sb.append("**前端**: ").append(techStack.getUiFramework()).append("\n");
        sb.append("**后端**: ").append(techStack.getBackend()).append("\n");
        sb.append("**数据库**: ").append(techStack.getDatabase()).append("\n");
        if (techStack.getReason() != null && !techStack.getReason().isEmpty()) {
            sb.append("**选型理由**: ").append(techStack.getReason()).append("\n");
        }
        sb.append("\n");

        // ============ 2. UI 设计风格（新增）============
        sb.append("## 2. UI 设计风格\n");
        Map<String, Object> uiStyle = extractMap(analysisResult, "uiStyle");
        if (!uiStyle.isEmpty()) {
            String theme = getStringValue(uiStyle, "theme", "");
            String colorScheme = getStringValue(uiStyle, "colorScheme", "");
            String layout = getStringValue(uiStyle, "layout", "");
            if (!theme.isEmpty())
                sb.append("**主题风格**: ").append(theme).append("\n");
            if (!colorScheme.isEmpty())
                sb.append("**配色方案**: ").append(colorScheme).append("\n");
            if (!layout.isEmpty())
                sb.append("**布局模式**: ").append(layout).append("\n");
        } else {
            // 根据平台自动推荐 UI 风格
            sb.append("**设计原则**:\n");
            sb.append("- 采用现代简约设计风格，注重用户体验\n");
            sb.append("- 使用清晰的视觉层次和一致的组件规范\n");
            sb.append("- 支持响应式布局，适配不同屏幕尺寸\n");
            if ("Web".equalsIgnoreCase(techStack.getPlatform())
                    || techStack.getUiFramework().toLowerCase().contains("react")) {
                sb.append("- 遵循 Material Design 或 Ant Design 设计规范\n");
                sb.append("- 支持深色模式切换\n");
            }
        }
        sb.append("\n");
        sb.append("**交互设计**:\n");
        sb.append("- 提供清晰的操作反馈和加载状态提示\n");
        sb.append("- 实现表单验证与友好的错误提示\n");
        sb.append("- 关键操作需二次确认，防止误操作\n");
        sb.append("\n");

        // ============ 3. 产品功能规划（新增）============
        sb.append("## 3. 产品功能规划\n");
        Map<String, Object> features = extractMap(analysisResult, "features");
        Map<String, Object> operations = extractMap(analysisResult, "operations");

        if (!features.isEmpty()) {
            sb.append("### 核心功能模块\n");
            for (Map.Entry<String, Object> entry : features.entrySet()) {
                String featureName = entry.getKey();
                String featureDesc = getDescription(entry.getValue());
                sb.append("- **").append(featureName).append("**: ").append(featureDesc).append("\n");
            }
            sb.append("\n");
        } else if (!operations.isEmpty()) {
            // 从 operations 推断功能模块
            sb.append("### 核心功能模块\n");
            // 按功能类型分组
            List<String> crudOps = new ArrayList<>();
            List<String> businessOps = new ArrayList<>();
            List<String> queryOps = new ArrayList<>();

            for (String opName : operations.keySet()) {
                String lowerName = opName.toLowerCase();
                if (lowerName.contains("create") || lowerName.contains("add") || lowerName.contains("新增") ||
                        lowerName.contains("update") || lowerName.contains("edit") || lowerName.contains("修改") ||
                        lowerName.contains("delete") || lowerName.contains("remove") || lowerName.contains("删除")) {
                    crudOps.add(opName);
                } else if (lowerName.contains("list") || lowerName.contains("get") || lowerName.contains("query") ||
                        lowerName.contains("search") || lowerName.contains("查询") || lowerName.contains("列表")) {
                    queryOps.add(opName);
                } else {
                    businessOps.add(opName);
                }
            }

            if (!crudOps.isEmpty()) {
                sb.append("**数据管理**: ");
                sb.append(String.join("、", crudOps)).append("\n");
            }
            if (!queryOps.isEmpty()) {
                sb.append("**数据查询**: ");
                sb.append(String.join("、", queryOps)).append("\n");
            }
            if (!businessOps.isEmpty()) {
                sb.append("**业务功能**: ");
                sb.append(String.join("、", businessOps)).append("\n");
            }
            sb.append("\n");
        }

        // 用户故事
        Object userStories = analysisResult.get("userStories");
        if (userStories instanceof List) {
            @SuppressWarnings("unchecked")
            List<Object> storyList = (List<Object>) userStories;
            if (!storyList.isEmpty()) {
                sb.append("### 用户故事\n");
                for (Object story : storyList) {
                    sb.append("- ").append(story.toString()).append("\n");
                }
                sb.append("\n");
            }
        }

        // 页面规划
        sb.append("### 页面规划\n");
        Map<String, Object> pages = extractMap(analysisResult, "pages");
        if (!pages.isEmpty()) {
            for (Map.Entry<String, Object> entry : pages.entrySet()) {
                String pageName = entry.getKey();
                String pageDesc = getDescription(entry.getValue());
                sb.append("- **").append(pageName).append("**: ").append(pageDesc).append("\n");
            }
        } else {
            // 根据实体自动推断页面
            Map<String, Object> entities = extractMap(analysisResult, "entities");
            if (!entities.isEmpty()) {
                for (String entityName : entities.keySet()) {
                    sb.append("- **").append(entityName).append("列表页**: 展示").append(entityName)
                            .append("数据列表，支持分页、搜索、筛选\n");
                    sb.append("- **").append(entityName).append("详情页**: 查看").append(entityName).append("详细信息\n");
                }
            } else {
                sb.append("- 系统将根据需求自动规划页面结构\n");
            }
        }
        sb.append("\n");

        // ============ 4. 目标用户画像（Step 1）============
        Object targetUser = analysisResult.get("targetUser");
        Object userProfile = analysisResult.get("userProfile");
        if (targetUser != null || userProfile != null) {
            sb.append("## 4. 目标用户画像\n");
            if (targetUser != null) {
                sb.append(getDescription(targetUser)).append("\n");
            }
            if (userProfile != null) {
                sb.append(getDescription(userProfile)).append("\n");
            }
            sb.append("\n");
        }

        // ============ 5. 数据领域模型（Step 2）============
        sb.append("## 5. 数据领域模型\n");
        Map<String, Object> entities = extractMap(analysisResult, "entities");
        if (!entities.isEmpty()) {
            for (Map.Entry<String, Object> entry : entities.entrySet()) {
                String entityName = entry.getKey();
                Object entityValue = entry.getValue();
                sb.append("**").append(entityName).append("**: ");
                String desc = getDescription(entityValue);
                sb.append(desc).append("\n");

                // 尝试提取字段信息
                if (entityValue instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> entityMap = (Map<String, Object>) entityValue;
                    Object fields = entityMap.get("fields");
                    if (fields instanceof List) {
                        @SuppressWarnings("unchecked")
                        List<Object> fieldList = (List<Object>) fields;
                        if (!fieldList.isEmpty()) {
                            sb.append("  - 字段: ");
                            List<String> fieldNames = new ArrayList<>();
                            for (Object field : fieldList) {
                                if (field instanceof Map) {
                                    @SuppressWarnings("unchecked")
                                    Map<String, Object> fieldMap = (Map<String, Object>) field;
                                    String fieldName = getStringValue(fieldMap, "name", "");
                                    String fieldType = getStringValue(fieldMap, "type", "");
                                    if (!fieldName.isEmpty()) {
                                        fieldNames.add(fieldName + (fieldType.isEmpty() ? "" : "(" + fieldType + ")"));
                                    }
                                }
                            }
                            sb.append(String.join(", ", fieldNames)).append("\n");
                        }
                    }
                }
            }
        } else {
            sb.append("未检测到复杂实体，系统将根据需求自动推断简单数据结构。\n");
        }
        sb.append("\n");

        // ============ 6. 实体关系（Step 2）============
        Map<String, Object> relationships = extractMap(analysisResult, "relationships");
        if (!relationships.isEmpty()) {
            sb.append("## 6. 实体关系\n");
            for (Map.Entry<String, Object> entry : relationships.entrySet()) {
                String relName = entry.getKey();
                String relDesc = getDescription(entry.getValue());
                sb.append("- **").append(relName).append("**: ").append(relDesc).append("\n");
            }
            sb.append("\n");
        }

        // ============ 7. API 与逻辑层（Step 3）============
        sb.append("## 7. API 与逻辑层\n");
        if (!operations.isEmpty()) {
            for (Map.Entry<String, Object> entry : operations.entrySet()) {
                String opName = entry.getKey();
                String opDesc = getDescription(entry.getValue());
                sb.append("- `").append(opName).append("`: ").append(opDesc).append("\n");
            }
        } else {
            sb.append("系统将根据实体自动生成标准 CRUD 接口。\n");
        }
        sb.append("\n");

        // ============ 8. 业务约束条件（Step 3）============
        Map<String, Object> constraints = extractMap(analysisResult, "constraints");
        if (!constraints.isEmpty()) {
            sb.append("## 8. 业务约束条件\n");
            for (Map.Entry<String, Object> entry : constraints.entrySet()) {
                String constName = entry.getKey();
                String constDesc = getDescription(entry.getValue());
                sb.append("- **").append(constName).append("**: ").append(constDesc).append("\n");
            }
            sb.append("\n");
        }

        // ============ 9. 复杂度与风险评估（Step 5）============
        sb.append("## 9. 复杂度与风险评估\n");
        sb.append("**复杂度级别**: ").append(complexity.getLevel().getDisplayName()).append("\n");
        sb.append("**预计开发周期**: ").append(complexity.getEstimatedDays()).append(" 天\n");
        sb.append("**预计代码规模**: 约 ").append(complexity.getEstimatedLines()).append(" 行\n");
        if (complexity.getDescription() != null && !complexity.getDescription().isEmpty()) {
            sb.append("**评估说明**: ").append(complexity.getDescription()).append("\n");
        }
        sb.append("\n");

        // ============ 10. 执行策略 ============
        sb.append("## 10. 执行策略\n");
        sb.append("### 前端生成规范\n");
        sb.append("- 使用 ").append(techStack.getUiFramework()).append(" 构建响应式用户界面\n");
        sb.append("- 遵循组件化设计原则，确保代码可维护性\n");
        sb.append("- 实现友好的用户交互与错误提示\n");
        sb.append("- 页面布局采用模块化设计，便于后续扩展\n");
        sb.append("\n");

        sb.append("### 服务端生成规范\n");
        if ("Supabase".equalsIgnoreCase(techStack.getBackend())) {
            sb.append("- 使用 Supabase 提供的 RESTful API 和实时订阅功能\n");
            sb.append("- 前端直连数据库，无需额外服务端代码\n");
            sb.append("- 利用 Row Level Security (RLS) 实现数据安全\n");
        } else {
            sb.append("- 使用 ").append(techStack.getBackend()).append(" 构建企业级服务端\n");
            sb.append("- 实现标准的分层架构：Controller → Service → Repository\n");
            sb.append("- 遵循 RESTful API 设计规范\n");
            sb.append("- 实现统一的异常处理和响应格式\n");
        }
        sb.append("\n");

        sb.append("### 数据库设计规范\n");
        sb.append("- 使用 ").append(techStack.getDatabase()).append(" 作为主数据库\n");
        sb.append("- 根据实体模型自动生成表结构和索引\n");
        sb.append("- 确保数据完整性约束和外键关系\n");
        sb.append("- 关键字段添加索引优化查询性能\n");
        sb.append("\n");

        // ============ 新用户引导流程（如有检测到）============
        Object onboardingFlow = analysisResult.get("onboardingFlow");
        if (onboardingFlow != null) {
            sb.append("## 新用户引导流程\n");
            sb.append(getDescription(onboardingFlow)).append("\n\n");
        }

        // ============ 11. AI 能力规划（M3 新增）============
        Object aiCapabilitiesObj = analysisResult.get("aiCapabilities");
        if (aiCapabilitiesObj instanceof List) {
            @SuppressWarnings("unchecked")
            List<String> aiCapabilities = (List<String>) aiCapabilitiesObj;
            if (!aiCapabilities.isEmpty()) {
                sb.append("## 11. AI 能力规划\n\n");
                sb.append("本应用需要集成以下 AI 能力：\n\n");
                sb.append("| 能力类型 | 用途 | 建议 API |\n");
                sb.append("|----------|------|----------|\n");
                for (String cap : aiCapabilities) {
                    String displayName = getAICapabilityDisplayName(cap);
                    String useCase = getAICapabilityUseCase(cap);
                    String apiEndpoint = "/api/v1/ai/" + cap.toLowerCase().replace("_", "-");
                    sb.append("| ").append(cap).append(" | ").append(displayName)
                            .append(" - ").append(useCase).append(" | `").append(apiEndpoint).append("` |\n");
                }
                sb.append("\n");
                sb.append("**集成说明**:\n");
                sb.append("- 后端 Service 层需注入 `AIProvider` 接口调用 AI 能力\n");
                sb.append("- 前端可通过生成的 API Client 调用 AI 相关接口\n");
                sb.append("\n");
            }
        }

        return sb.toString();
    }

    private String getDescription(Object obj) {
        if (obj instanceof Map) {
            return getStringValue((Map<String, Object>) obj, "description", "Standard Entity");
        }
        return "Standard Entity";
    }

    /**
     * M3: 基于需求文本检测 AI 能力（用于 G3 任务启动时的补救/增强）
     */
    public List<String> detectAiCapabilities(String requirement) {
        if (requirement == null || requirement.isBlank()) {
            return new ArrayList<>();
        }

        List<String> capabilities = new ArrayList<>();
        String searchText = requirement.toLowerCase();

        // AI 能力关键词映射 (保持与 extractAICapabilities 一致)
        Map<String, List<String>> keywordMap = Map.ofEntries(
                Map.entry("CHATBOT", List.of("聊天", "客服", "对话", "chat", "conversation", "客户服务")),
                Map.entry("QA_SYSTEM", List.of("问答", "faq", "qa", "知识问答", "智能问答")),
                Map.entry("RAG", List.of("知识库", "文档检索", "rag", "retrieval", "知识检索")),
                Map.entry("SUMMARIZATION", List.of("摘要", "总结", "summarize", "summary", "归纳")),
                Map.entry("IMAGE_RECOGNITION", List.of("图片识别", "图像识别", "image recognition", "图片分析")),
                Map.entry("SPEECH_TO_TEXT", List.of("语音识别", "语音转文字", "stt", "speech")),
                Map.entry("TEXT_TO_SPEECH", List.of("语音合成", "tts", "文字转语音")),
                Map.entry("CONTENT_GENERATION", List.of("内容生成", "文案", "自动写作", "content generation")),
                Map.entry("SENTIMENT_ANALYSIS", List.of("情感分析", "sentiment", "舆情", "评论分析")),
                Map.entry("TRANSLATION", List.of("翻译", "translate", "多语言")),
                Map.entry("CODE_COMPLETION", List.of("代码补全", "代码生成", "code completion")),
                Map.entry("RECOMMENDATION", List.of("推荐", "recommend", "个性化推荐", "智能推荐")),
                Map.entry("VIDEO_ANALYSIS", List.of("视频分析", "视频识别", "video analysis")),
                Map.entry("KNOWLEDGE_GRAPH", List.of("知识图谱", "knowledge graph", "实体关系")),
                Map.entry("OCR_DOCUMENT", List.of("ocr", "文档识别", "发票识别", "证件识别")),
                Map.entry("REALTIME_STREAM", List.of("实时分析", "流分析", "realtime")),
                Map.entry("HYPER_PERSONALIZATION", List.of("超个性化", "用户画像", "精准推荐")),
                Map.entry("PREDICTIVE_ANALYTICS", List.of("预测分析", "predictive", "趋势预测", "预估")),
                Map.entry("MULTIMODAL_GENERATION", List.of("文生图", "图生文", "multimodal", "多模态生成")),
                Map.entry("ANOMALY_DETECTION", List.of("异常检测", "anomaly", "欺诈检测", "风控")));

        for (Map.Entry<String, List<String>> entry : keywordMap.entrySet()) {
            for (String keyword : entry.getValue()) {
                if (searchText.contains(keyword.toLowerCase())) {
                    if (!capabilities.contains(entry.getKey())) {
                        capabilities.add(entry.getKey());
                    }
                    break; // Found one keyword for this capability, move to next capability
                }
            }
        }
        return capabilities;
    }

    /**
     * M3: 从分析结果和操作列表中提取 AI 能力需求
     * 通过关键词匹配识别用户需求中涉及的 AI 能力类型
     */
    private List<String> extractAICapabilities(Map<String, Object> analysisResult, Map<String, Object> operations) {
        List<String> capabilities = new ArrayList<>();

        // 将分析结果和操作转为可搜索的文本
        String searchText = (analysisResult.toString() + operations.toString()).toLowerCase();

        // AI 能力关键词映射
        Map<String, List<String>> keywordMap = Map.ofEntries(
                Map.entry("CHATBOT", List.of("聊天", "客服", "对话", "chat", "conversation", "客户服务")),
                Map.entry("QA_SYSTEM", List.of("问答", "faq", "qa", "知识问答", "智能问答")),
                Map.entry("RAG", List.of("知识库", "文档检索", "rag", "retrieval", "知识检索")),
                Map.entry("SUMMARIZATION", List.of("摘要", "总结", "summarize", "summary", "归纳")),
                Map.entry("IMAGE_RECOGNITION", List.of("图片识别", "图像识别", "image recognition", "图片分析")),
                Map.entry("SPEECH_TO_TEXT", List.of("语音识别", "语音转文字", "stt", "speech")),
                Map.entry("TEXT_TO_SPEECH", List.of("语音合成", "tts", "文字转语音")),
                Map.entry("CONTENT_GENERATION", List.of("内容生成", "文案", "自动写作", "content generation")),
                Map.entry("SENTIMENT_ANALYSIS", List.of("情感分析", "sentiment", "舆情", "评论分析")),
                Map.entry("TRANSLATION", List.of("翻译", "translate", "多语言")),
                Map.entry("CODE_COMPLETION", List.of("代码补全", "代码生成", "code completion")),
                Map.entry("RECOMMENDATION", List.of("推荐", "recommend", "个性化推荐", "智能推荐")),
                Map.entry("VIDEO_ANALYSIS", List.of("视频分析", "视频识别", "video analysis")),
                Map.entry("KNOWLEDGE_GRAPH", List.of("知识图谱", "knowledge graph", "实体关系")),
                Map.entry("OCR_DOCUMENT", List.of("ocr", "文档识别", "发票识别", "证件识别")),
                Map.entry("REALTIME_STREAM", List.of("实时分析", "流分析", "realtime")),
                Map.entry("HYPER_PERSONALIZATION", List.of("超个性化", "用户画像", "精准推荐")),
                Map.entry("PREDICTIVE_ANALYTICS", List.of("预测分析", "predictive", "趋势预测", "预估")),
                Map.entry("MULTIMODAL_GENERATION", List.of("文生图", "图生文", "multimodal", "多模态生成")),
                Map.entry("ANOMALY_DETECTION", List.of("异常检测", "anomaly", "欺诈检测", "风控")));

        for (Map.Entry<String, List<String>> entry : keywordMap.entrySet()) {
            for (String keyword : entry.getValue()) {
                if (searchText.contains(keyword.toLowerCase())) {
                    if (!capabilities.contains(entry.getKey())) {
                        capabilities.add(entry.getKey());
                    }
                    break;
                }
            }
        }

        return capabilities;
    }

    /**
     * 获取 AI 能力的中文显示名称
     */
    private String getAICapabilityDisplayName(String capabilityType) {
        return switch (capabilityType) {
            case "CHATBOT" -> "聊天机器人";
            case "QA_SYSTEM" -> "问答系统";
            case "RAG" -> "知识库检索";
            case "SUMMARIZATION" -> "文本摘要";
            case "IMAGE_RECOGNITION" -> "图片识别";
            case "SPEECH_TO_TEXT" -> "语音识别";
            case "TEXT_TO_SPEECH" -> "语音合成";
            case "CONTENT_GENERATION" -> "内容生成";
            case "SENTIMENT_ANALYSIS" -> "情感分析";
            case "TRANSLATION" -> "智能翻译";
            case "CODE_COMPLETION" -> "代码补全";
            case "RECOMMENDATION" -> "智能推荐";
            case "VIDEO_ANALYSIS" -> "视频分析";
            case "KNOWLEDGE_GRAPH" -> "知识图谱";
            case "OCR_DOCUMENT" -> "智能文档识别";
            case "REALTIME_STREAM" -> "实时流分析";
            case "HYPER_PERSONALIZATION" -> "超个性化引擎";
            case "PREDICTIVE_ANALYTICS" -> "预测分析";
            case "MULTIMODAL_GENERATION" -> "多模态生成";
            case "ANOMALY_DETECTION" -> "异常检测";
            default -> capabilityType;
        };
    }

    /**
     * 获取 AI 能力的典型使用场景
     */
    private String getAICapabilityUseCase(String capabilityType) {
        return switch (capabilityType) {
            case "CHATBOT" -> "智能客服对话";
            case "QA_SYSTEM" -> "基于知识库的问答";
            case "RAG" -> "检索增强生成";
            case "SUMMARIZATION" -> "长文本自动摘要";
            case "IMAGE_RECOGNITION" -> "图片内容识别";
            case "SPEECH_TO_TEXT" -> "语音转文字";
            case "TEXT_TO_SPEECH" -> "文字转语音";
            case "CONTENT_GENERATION" -> "文案/文章自动生成";
            case "SENTIMENT_ANALYSIS" -> "评论/舆情分析";
            case "TRANSLATION" -> "多语言翻译";
            case "CODE_COMPLETION" -> "代码智能补全";
            case "RECOMMENDATION" -> "个性化内容/商品推荐";
            case "VIDEO_ANALYSIS" -> "视频内容理解";
            case "KNOWLEDGE_GRAPH" -> "实体关系提取";
            case "OCR_DOCUMENT" -> "票据/证件识别";
            case "REALTIME_STREAM" -> "音视频流实时处理";
            case "HYPER_PERSONALIZATION" -> "精准用户画像";
            case "PREDICTIVE_ANALYTICS" -> "业务数据趋势预测";
            case "MULTIMODAL_GENERATION" -> "跨模态内容生成";
            case "ANOMALY_DETECTION" -> "欺诈/异常行为检测";
            default -> "通用 AI 调用";
        };
    }

    /**
     * 执行单个步骤的分析（用于交互式分析）
     *
     * @param requirement      需求描述
     * @param step             步骤编号 (1-6)
     * @param stepResults      之前步骤的结果
     * @param stepFeedback     之前步骤的反馈
     * @param currentFeedback  当前步骤的反馈
     * @param progressCallback 进度回调
     * @return 步骤执行结果
     */
    public Object analyzeSingleStep(
            String requirement,
            int step,
            Map<Integer, Object> stepResults,
            Map<Integer, String> stepFeedback,
            String currentFeedback,
            Consumer<AnalysisProgressMessage> progressCallback) {

        log.info("执行单步分析: step={}, hasFeedback={}, previousSteps={}",
                step, currentFeedback != null, stepResults != null ? stepResults.size() : 0);

        // 构建累积上下文
        String context = buildCumulativeContext(requirement, step, stepResults, stepFeedback, currentFeedback);

        try {
            switch (step) {
                case 1 -> {
                    // 步骤1：需求语义解析
                    return executeStep1(requirement, context, progressCallback);
                }
                case 2 -> {
                    // 步骤2：实体关系建模
                    return executeStep2(requirement, context, progressCallback);
                }
                case 3 -> {
                    // 步骤3：功能意图识别
                    return executeStep3(requirement, context, progressCallback);
                }
                case 4 -> {
                    // 步骤4：技术架构选型
                    return executeStep4(requirement, context, progressCallback);
                }
                case 5 -> {
                    // 步骤5：复杂度与风险评估
                    return executeStep5(requirement, context, progressCallback);
                }
                case 6 -> {
                    // 步骤6：Ultrathink 深度规划
                    return executeStep6(requirement, context, progressCallback);
                }
                default -> throw new IllegalArgumentException("无效的步骤编号: " + step);
            }
        } catch (Exception e) {
            log.error("单步分析失败: step={}", step, e);
            throw new RuntimeException("步骤 " + step + " 执行失败: " + e.getMessage(), e);
        }
    }

    /**
     * 构建累积上下文
     * 将之前步骤的结果和反馈整合到提示词中
     */
    private String buildCumulativeContext(
            String requirement,
            int step,
            Map<Integer, Object> stepResults,
            Map<Integer, String> stepFeedback,
            String currentFeedback) {

        StringBuilder context = new StringBuilder();

        // 原始需求
        context.append("# 原始需求\n").append(requirement).append("\n\n");

        // 添加之前步骤的结果和反馈
        if (stepResults != null && !stepResults.isEmpty()) {
            for (int i = 1; i < step; i++) {
                if (stepResults.containsKey(i)) {
                    context.append("## Step ").append(i).append(" 结果\n");
                    try {
                        context.append(objectMapper.writeValueAsString(stepResults.get(i))).append("\n\n");
                    } catch (Exception e) {
                        context.append(stepResults.get(i).toString()).append("\n\n");
                    }
                }

                if (stepFeedback != null && stepFeedback.containsKey(i)) {
                    context.append("## Step ").append(i).append(" 用户反馈\n");
                    context.append(stepFeedback.get(i)).append("\n\n");
                }
            }
        }

        // 当前步骤反馈
        if (currentFeedback != null && !currentFeedback.isEmpty()) {
            context.append("## 当前步骤用户反馈\n");
            context.append(currentFeedback).append("\n\n");
        }

        // 当前步骤任务描述
        context.append("## 当前任务 (Step ").append(step).append(")\n");
        context.append(getStepDescription(step)).append("\n");

        log.debug("构建的累积上下文长度: {} 字符", context.length());
        return context.toString();
    }

    /**
     * 获取步骤描述
     */
    private String getStepDescription(int step) {
        return switch (step) {
            case 1 -> "需求语义解析: 理解用户需求,提取核心意图和关键信息";
            case 2 -> "实体关系建模: 基于Step 1的结果,识别数据实体和关系";
            case 3 -> "功能意图识别: 基于前面步骤的结果,分析功能模块和业务逻辑";
            case 4 -> "技术架构选型: 基于前面步骤的结果,推荐技术栈和架构方案";
            case 5 -> "复杂度与风险评估: 基于前面步骤的结果,评估项目规模和风险";
            case 6 -> "Ultrathink 深度规划: 基于前面所有步骤的结果,生成完整的技术实施蓝图";
            default -> "未知步骤";
        };
    }

    private Object executeStep1(String requirement, String context, Consumer<AnalysisProgressMessage> progressCallback)
            throws Exception {
        progressCallback.accept(AnalysisProgressMessage.builder()
                .step(1)
                .stepName("需求语义解析")
                .status(AnalysisProgressMessage.StepStatus.RUNNING)
                .description("正在调用AI模型理解您的需求...")
                .detail("AI正在深度分析您的自然语言描述，提取核心意图和关键信息")
                .progress(5)
                .timestamp(Instant.now())
                .build());

        // 使用累积上下文调用AI
        String analysisJson = callAIForAnalysisWithRetry(context, progressCallback);
        Map<String, Object> analysisResult = parseAnalysisResult(analysisJson);

        progressCallback.accept(AnalysisProgressMessage.builder()
                .step(1)
                .stepName("需求语义解析")
                .status(AnalysisProgressMessage.StepStatus.COMPLETED)
                .description("AI已成功理解您的需求")
                .detail("需求解析完成，正在提取结构化信息...")
                .progress(100)
                .result(analysisResult)
                .timestamp(Instant.now())
                .build());

        return analysisResult;
    }

    private Object executeStep2(String requirement, String context, Consumer<AnalysisProgressMessage> progressCallback)
            throws Exception {
        progressCallback.accept(AnalysisProgressMessage.builder()
                .step(2)
                .stepName("实体关系建模")
                .status(AnalysisProgressMessage.StepStatus.RUNNING)
                .description("正在识别核心数据实体与关联...")
                .detail("基于Step 1的结果,从需求中提取数据模型")
                .progress(25)
                .timestamp(Instant.now())
                .build());

        // 使用累积上下文调用AI
        String analysisJson = callAIForAnalysisWithRetry(context, progressCallback);
        Map<String, Object> analysisResult = parseAnalysisResult(analysisJson);
        Map<String, Object> entities = extractMap(analysisResult, "entities");
        Map<String, Object> relationships = extractMap(analysisResult, "relationships");

        Thread.sleep(500);

        Map<String, Object> result = Map.of(
                "entities", entities,
                "relationships", relationships,
                "entitiesCount", entities.size(),
                "relationshipsCount", relationships.size());

        progressCallback.accept(AnalysisProgressMessage.builder()
                .step(2)
                .stepName("实体关系建模")
                .status(AnalysisProgressMessage.StepStatus.COMPLETED)
                .description("数据模型构建完成")
                .detail(String.format("识别到 %d 个实体，%d 个关系", entities.size(), relationships.size()))
                .progress(100)
                .result(result)
                .timestamp(Instant.now())
                .build());

        return result;
    }

    private Object executeStep3(String requirement, String context, Consumer<AnalysisProgressMessage> progressCallback)
            throws Exception {
        progressCallback.accept(AnalysisProgressMessage.builder()
                .step(3)
                .stepName("功能意图识别")
                .status(AnalysisProgressMessage.StepStatus.RUNNING)
                .description("正在分析所需的功能模块与业务逻辑...")
                .detail("基于前面步骤的结果,提取CRUD操作、业务流程")
                .progress(45)
                .timestamp(Instant.now())
                .build());

        String analysisJson = callAIForAnalysisWithRetry(context, progressCallback);
        Map<String, Object> analysisResult = parseAnalysisResult(analysisJson);
        Map<String, Object> operations = extractMap(analysisResult, "operations");
        Map<String, Object> constraints = extractMap(analysisResult, "constraints");

        Thread.sleep(500);

        // M3: AI 能力识别 - 分析需求是否包含 AI 相关功能
        List<String> aiCapabilities = extractAICapabilities(analysisResult, operations);

        Map<String, Object> result = new java.util.HashMap<>(Map.of(
                "operations", operations,
                "constraints", constraints,
                "operationsCount", operations.size(),
                "constraintsCount", constraints.size()));
        // 如果识别到 AI 能力，添加到结果中
        if (!aiCapabilities.isEmpty()) {
            result.put("aiCapabilities", aiCapabilities);
        }

        progressCallback.accept(AnalysisProgressMessage.builder()
                .step(3)
                .stepName("功能意图识别")
                .status(AnalysisProgressMessage.StepStatus.COMPLETED)
                .description("功能模块分析完成")
                .detail(String.format("识别到 %d 个操作，%d 个约束条件", operations.size(), constraints.size()))
                .progress(100)
                .result(result)
                .timestamp(Instant.now())
                .build());

        return result;
    }

    private Object executeStep4(String requirement, String context, Consumer<AnalysisProgressMessage> progressCallback)
            throws Exception {
        progressCallback.accept(AnalysisProgressMessage.builder()
                .step(4)
                .stepName("技术架构选型")
                .status(AnalysisProgressMessage.StepStatus.RUNNING)
                .description("正在从AI分析结果中提取技术栈推荐...")
                .detail("基于前面步骤的结果,智能匹配技术方案")
                .progress(65)
                .timestamp(Instant.now())
                .build());

        String analysisJson = callAIForAnalysisWithRetry(context, progressCallback);
        Map<String, Object> analysisResult = parseAnalysisResult(analysisJson);
        TechStackRecommendation techStack = recommendTechStack(analysisResult);

        Thread.sleep(300);

        Map<String, Object> result = Map.of(
                "platform", techStack.getPlatform(),
                "uiFramework", techStack.getUiFramework(),
                "backend", techStack.getBackend(),
                "database", techStack.getDatabase(),
                "confidence", techStack.getConfidence(),
                "reason", techStack.getReason());

        progressCallback.accept(AnalysisProgressMessage.builder()
                .step(4)
                .stepName("技术架构选型")
                .status(AnalysisProgressMessage.StepStatus.COMPLETED)
                .description("技术方案已确定")
                .detail(String.format("推荐: %s + %s + %s", techStack.getPlatform(), techStack.getUiFramework(),
                        techStack.getBackend()))
                .progress(100)
                .result(result)
                .timestamp(Instant.now())
                .build());

        return result;
    }

    private Object executeStep5(String requirement, String context, Consumer<AnalysisProgressMessage> progressCallback)
            throws Exception {
        progressCallback.accept(AnalysisProgressMessage.builder()
                .step(5)
                .stepName("复杂度与风险评估")
                .status(AnalysisProgressMessage.StepStatus.RUNNING)
                .description("正在从AI分析结果中提取复杂度评估...")
                .detail("基于前面步骤的结果,评估项目规模和风险")
                .progress(85)
                .timestamp(Instant.now())
                .build());

        String analysisJson = callAIForAnalysisWithRetry(context, progressCallback);
        Map<String, Object> analysisResult = parseAnalysisResult(analysisJson);
        ComplexityAssessment complexity = assessComplexity(analysisResult);
        BigDecimal confidenceScore = extractConfidenceScore(analysisResult);

        Thread.sleep(300);

        Map<String, Object> result = Map.of(
                "complexityLevel", complexity.getLevel().name(),
                "estimatedDays", complexity.getEstimatedDays(),
                "estimatedLines", complexity.getEstimatedLines(),
                "confidenceScore", confidenceScore,
                "description", complexity.getDescription());

        progressCallback.accept(AnalysisProgressMessage.builder()
                .step(5)
                .stepName("复杂度与风险评估")
                .status(AnalysisProgressMessage.StepStatus.COMPLETED)
                .description("评估完成")
                .detail(String.format("复杂度: %s，预计 %d 天，约 %d 行代码",
                        complexity.getLevel(), complexity.getEstimatedDays(), complexity.getEstimatedLines()))
                .progress(100)
                .result(result)
                .timestamp(Instant.now())
                .build());

        return result;
    }

    private Object executeStep6(String requirement, String context, Consumer<AnalysisProgressMessage> progressCallback)
            throws Exception {
        progressCallback.accept(AnalysisProgressMessage.builder()
                .step(6)
                .stepName("Ultrathink 深度规划")
                .status(AnalysisProgressMessage.StepStatus.RUNNING)
                .description("正在生成技术实施蓝图...")
                .detail("基于前面所有步骤的结果,构建系统架构与实施路径")
                .progress(95)
                .timestamp(Instant.now())
                .build());

        String analysisJson = callAIForAnalysisWithRetry(context, progressCallback);
        Map<String, Object> analysisResult = parseAnalysisResult(analysisJson);
        TechStackRecommendation techStack = recommendTechStack(analysisResult);
        ComplexityAssessment complexity = assessComplexity(analysisResult);
        String technicalBlueprint = generateTechnicalBlueprint(analysisResult, techStack, complexity);

        Thread.sleep(800);

        Map<String, Object> result = Map.of(
                "blueprint", technicalBlueprint,
                "sections", 10);

        progressCallback.accept(AnalysisProgressMessage.builder()
                .step(6)
                .stepName("Ultrathink 深度规划")
                .status(AnalysisProgressMessage.StepStatus.COMPLETED)
                .description("技术蓝图构建完成")
                .detail(technicalBlueprint)
                .progress(100)
                .result(result)
                .timestamp(Instant.now())
                .build());

        return result;
    }
}
