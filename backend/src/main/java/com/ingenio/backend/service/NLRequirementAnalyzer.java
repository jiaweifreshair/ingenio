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
                final java.util.concurrent.atomic.AtomicBoolean isCompleted = 
                    new java.util.concurrent.atomic.AtomicBoolean(false);
                final java.util.concurrent.atomic.AtomicInteger heartbeatCount = 
                    new java.util.concurrent.atomic.AtomicInteger(0);

                java.util.concurrent.ScheduledExecutorService heartbeat = 
                    java.util.concurrent.Executors.newSingleThreadScheduledExecutor();

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
                .confidence(needsConfirmation ? 0.6 : 0.85)  // 需要确认时降低置信度
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
        if (levelStr == null) return ComplexityLevel.MEDIUM;

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
                            "entities", entities.keySet()
                    ))
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
                            "operations", operations.keySet()
                    ))
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
                            "confidence", techStack.getConfidence()
                    ))
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
                    .progress(100)
                    .result(Map.of(
                            "complexityLevel", complexity.getLevel().name(),
                            "estimatedDays", complexity.getEstimatedDays(),
                            "estimatedLines", complexity.getEstimatedLines(),
                            "confidenceScore", confidenceScore
                    ))
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
}