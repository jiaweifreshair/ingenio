package com.ingenio.backend.service;

import com.ingenio.backend.agent.ExecuteAgentFactory;
import com.ingenio.backend.agent.IExecuteAgent;
import com.ingenio.backend.agent.IntentClassifier;
import com.ingenio.backend.agent.dto.IntentClassificationResult;
import com.ingenio.backend.agent.dto.PlanResult;
import com.ingenio.backend.agent.dto.RequirementIntent;
import com.ingenio.backend.enums.DesignStyle;
import com.ingenio.backend.common.exception.BusinessException;
import com.ingenio.backend.common.exception.ErrorCode;
import com.ingenio.backend.dto.request.Generate7StylesRequest;
import com.ingenio.backend.dto.request.OpenLovableGenerateRequest;
import com.ingenio.backend.dto.response.Generate7StylesResponse;
import com.ingenio.backend.dto.response.OpenLovableGenerateResponse;
import com.ingenio.backend.dto.response.StylePreviewResponse;
import com.ingenio.backend.entity.AppSpecEntity;
import com.ingenio.backend.mapper.AppSpecMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

/**
 * PlanRoutingService - Plan阶段智能路由服务
 *
 * V2.0核心组件，负责：
 * 1. 根据用户需求执行意图识别（IntentClassifier）
 * 2. 匹配行业模板（IndustryTemplateMatchingService）
 * 3. 根据意图类型路由到不同的生成分支：
 *    - CLONE_EXISTING_WEBSITE → 直接OpenLovable爬取
 *    - DESIGN_FROM_SCRATCH → SuperDesign 7风格 → 用户选择 → OpenLovable原型
 *    - HYBRID_CLONE_AND_CUSTOMIZE → OpenLovable爬取 → AI定制修改
 * 4. 生成前端原型并部署到E2B Sandbox
 * 5. 保存路由结果到AppSpec实体
 *
 * 设计原则：
 * - 职责单一：专注于Plan阶段的路由决策
 * - 可扩展：支持添加新的意图类型和处理分支
 * - 可追溯：保存完整的路由决策日志
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PlanRoutingService {

    private final IntentClassifier intentClassifier;
    private final IndustryTemplateMatchingService templateMatchingService;
    private final SuperDesignService superDesignService;
    private final OpenLovableService openLovableService;
    private final AppSpecMapper appSpecMapper;
    private final ExecuteAgentFactory executeAgentFactory;

    /**
     * 执行完整的Plan阶段路由流程（兼容旧接口）
     */
    @Transactional
    public PlanRoutingResult route(String userRequirement, UUID tenantId, UUID userId) {
        return route(userRequirement, tenantId, userId, null, null);
    }

    /**
     * 执行完整的Plan阶段路由流程
     *
     * V2.0增强：支持用户预选复杂度和技术栈提示
     *
     * @param userRequirement 用户需求（自然语言）
     * @param tenantId 租户ID
     * @param userId 用户ID
     * @param complexityHint 用户预选的复杂度分类（可选）
     * @param techStackHint 用户预选的技术栈提示（可选）
     * @return 路由结果（包含AppSpec ID和下一步操作指引）
     * @throws BusinessException 当路由失败时抛出
     */
    @Transactional
    public PlanRoutingResult route(String userRequirement, UUID tenantId, UUID userId,
                                    String complexityHint, String techStackHint) {
        log.info("PlanRoutingService开始路由 - userRequirement: {}, tenantId: {}, userId: {}, complexityHint: {}, techStackHint: {}",
                userRequirement, tenantId, userId, complexityHint, techStackHint);

        // V2.0增强：如果用户提供了复杂度提示，增强需求描述
        String enhancedRequirement = enhanceRequirementWithHint(userRequirement, complexityHint, techStackHint);

        long routeStartTime = System.currentTimeMillis();

        try {
            // V2.0性能优化：并行执行意图识别和沙箱预创建
            // 意图识别约2-3秒，沙箱创建约30秒，并行执行可节省大量时间
            log.info("启动并行任务: 意图识别 + 沙箱预创建");

            // 异步启动沙箱预创建（不阻塞主流程）
            CompletableFuture<Map<String, Object>> sandboxFuture = CompletableFuture.supplyAsync(() -> {
                long sandboxStartTime = System.currentTimeMillis();
                log.info("开始异步沙箱预创建...");
                try {
                    Map<String, Object> sandboxInfo = openLovableService.prewarmSandbox();
                    long sandboxDuration = System.currentTimeMillis() - sandboxStartTime;
                    log.info("沙箱预创建完成: sandboxId={}, 耗时={}ms",
                            sandboxInfo.get("sandboxId"), sandboxDuration);
                    return sandboxInfo;
                } catch (Exception e) {
                    log.warn("沙箱预创建失败（将在需要时重新创建）: {}", e.getMessage());
                    return null;
                }
            });

            // 同步执行意图识别（主流程）- 使用增强后的需求描述
            long intentStartTime = System.currentTimeMillis();
            IntentClassificationResult intentResult = performIntentClassification(enhancedRequirement);
            long intentDuration = System.currentTimeMillis() - intentStartTime;
            log.info("意图识别完成 - intent: {}, confidence: {}, 耗时={}ms",
                    intentResult.getIntent(), intentResult.getConfidence(), intentDuration);

            // Step 2: 创建AppSpec实体
            AppSpecEntity appSpec = createAppSpec(tenantId, userId, intentResult, userRequirement);
            log.info("AppSpec创建完成 - appSpecId: {}", appSpec.getId());

            // Step 3: 匹配行业模板
            List<IndustryTemplateMatchingService.TemplateMatchResult> matchedTemplates = matchTemplates(
                    intentResult.getExtractedKeywords(),
                    intentResult.getReferenceUrls()
            );
            updateAppSpecWithTemplates(appSpec, matchedTemplates);
            log.info("模板匹配完成 - matchedCount: {}", matchedTemplates.size());

            // Step 4: 根据意图类型执行路由（传入预创建的沙箱）
            PlanRoutingResult.PlanRoutingResultBuilder resultBuilder = PlanRoutingResult.builder()
                    .appSpecId(appSpec.getId())
                    .intent(intentResult.getIntent())
                    .confidence(intentResult.getConfidence())
                    .matchedTemplateResults(matchedTemplates);

            switch (intentResult.getIntent()) {
                case CLONE_EXISTING_WEBSITE:
                    routeToCloneBranch(appSpec, intentResult, resultBuilder, sandboxFuture);
                    break;
                case DESIGN_FROM_SCRATCH:
                    routeToDesignBranch(appSpec, userRequirement, resultBuilder);
                    // DESIGN分支不需要立即使用沙箱，取消预创建任务
                    sandboxFuture.cancel(true);
                    break;
                case HYBRID_CLONE_AND_CUSTOMIZE:
                    routeToHybridBranch(appSpec, intentResult, resultBuilder, sandboxFuture);
                    break;
                default:
                    sandboxFuture.cancel(true);
                    throw new BusinessException(ErrorCode.SYSTEM_ERROR, "未知的意图类型: " + intentResult.getIntent());
            }

            // Step 5: 保存AppSpec更新
            appSpecMapper.updateById(appSpec);

            PlanRoutingResult result = resultBuilder.build();
            long totalDuration = System.currentTimeMillis() - routeStartTime;
            log.info("PlanRoutingService路由完成 - appSpecId: {}, nextAction: {}, 总耗时={}ms",
                    result.getAppSpecId(), result.getNextAction(), totalDuration);

            return result;

        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("PlanRoutingService路由失败: {}", e.getMessage(), e);
            throw new BusinessException(ErrorCode.AGENT_PLAN_FAILED, "路由失败: " + e.getMessage());
        }
    }

    /**
     * 执行意图识别
     */
    private IntentClassificationResult performIntentClassification(String userRequirement) {
        IntentClassificationResult result = intentClassifier.classifyIntent(userRequirement);

        if (!result.isSuccessful()) {
            log.error("意图识别失败 - 置信度过低: {}", result.getConfidence());
            throw new BusinessException(ErrorCode.AGENT_PLAN_FAILED, "意图识别失败，置信度过低");
        }

        return result;
    }

    /**
     * 创建初始AppSpec实体
     */
    private AppSpecEntity createAppSpec(UUID tenantId, UUID userId,
                                         IntentClassificationResult intentResult,
                                         String userRequirement) {
        // 将意图识别结果转换为Map存储
        Map<String, Object> intentResultMap = new HashMap<>();
        intentResultMap.put("intent", intentResult.getIntent().getCode());
        intentResultMap.put("confidence", intentResult.getConfidence());
        intentResultMap.put("reasoning", intentResult.getReasoning());
        intentResultMap.put("referenceUrls", intentResult.getReferenceUrls());
        intentResultMap.put("extractedKeywords", intentResult.getExtractedKeywords());
        intentResultMap.put("customizationRequirement", intentResult.getCustomizationRequirement());
        intentResultMap.put("warnings", intentResult.getWarnings());

        // 创建基础specContent（用户需求）
        Map<String, Object> specContent = new HashMap<>();
        specContent.put("userRequirement", userRequirement);
        specContent.put("createdAt", Instant.now().toString());

        AppSpecEntity appSpec = AppSpecEntity.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .createdByUserId(userId)
                .specContent(specContent)
                .version(1)
                .status(AppSpecEntity.Status.DRAFT.getValue())
                .intentType(intentResult.getIntent().name())
                .confidenceScore(BigDecimal.valueOf(intentResult.getConfidence()))
                .intentClassificationResult(intentResultMap)
                .designConfirmed(false)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        appSpecMapper.insert(appSpec);
        return appSpec;
    }

    /**
     * 匹配行业模板
     */
    private List<IndustryTemplateMatchingService.TemplateMatchResult> matchTemplates(
            List<String> keywords, List<String> referenceUrls) {
        String referenceUrl = (referenceUrls != null && !referenceUrls.isEmpty())
                ? referenceUrls.get(0)
                : null;

        return templateMatchingService.matchTemplates(keywords, referenceUrl, 3);
    }

    /**
     * 更新AppSpec的模板匹配结果
     */
    private void updateAppSpecWithTemplates(AppSpecEntity appSpec,
                                             List<IndustryTemplateMatchingService.TemplateMatchResult> matchedTemplates) {
        List<String> templateIds = matchedTemplates.stream()
                .map(t -> t.getTemplate().getId().toString())
                .collect(Collectors.toList());
        appSpec.setMatchedTemplates(templateIds);
    }

    /**
     * 路由到克隆分支：直接爬取目标网站
     *
     * V2.0优化：支持使用预创建的沙箱，节省30秒等待时间
     *
     * @param sandboxFuture 预创建的沙箱Future（可能还在创建中）
     */
    private void routeToCloneBranch(AppSpecEntity appSpec,
                                     IntentClassificationResult intentResult,
                                     PlanRoutingResult.PlanRoutingResultBuilder resultBuilder,
                                     CompletableFuture<Map<String, Object>> sandboxFuture) {
        log.info("路由到克隆分支 - appSpecId: {}", appSpec.getId());

        List<String> referenceUrls = intentResult.getReferenceUrls();
        if (referenceUrls == null || referenceUrls.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "克隆模式需要提供参考网站URL");
        }

        // 构建OpenLovable请求
        String targetUrl = referenceUrls.get(0);
        OpenLovableGenerateRequest request = OpenLovableGenerateRequest.builder()
                .userRequirement("Clone website: " + targetUrl)
                .referenceUrls(Collections.singletonList(targetUrl))
                .needsCrawling(true)
                .streaming(false)
                .timeoutSeconds(60)
                .build();

        // V2.0优化：尝试使用预创建的沙箱
        OpenLovableGenerateResponse response;
        Map<String, Object> prewarmedSandbox = waitForPrewarmedSandbox(sandboxFuture);

        if (prewarmedSandbox != null) {
            log.info("使用预创建的沙箱进行克隆分支");
            response = openLovableService.generatePrototypeWithPrewarmedSandbox(request, prewarmedSandbox);
        } else {
            log.info("预创建沙箱不可用，走正常流程");
            response = openLovableService.generatePrototype(request);
        }

        // 转换响应为Map存储
        Map<String, Object> prototypeResult = new HashMap<>();
        prototypeResult.put("previewUrl", response.getPreviewUrl());
        prototypeResult.put("sandboxId", response.getSandboxId());
        prototypeResult.put("provider", response.getProvider());
        prototypeResult.put("generatedAt", response.getCompletedAt() != null ? response.getCompletedAt().toString() : Instant.now().toString());

        // 更新AppSpec
        appSpec.setFrontendPrototype(prototypeResult);
        appSpec.setFrontendPrototypeUrl(response.getPreviewUrl());
        appSpec.setPrototypeGeneratedAt(Instant.now());

        resultBuilder
                .branch(RoutingBranch.CLONE)
                .prototypeGenerated(true)
                .prototypeUrl(response.getPreviewUrl())
                .nextAction("请预览原型并确认设计")
                .requiresUserConfirmation(true);
    }

    /**
     * 路由到设计分支：SuperDesign 7风格方案
     *
     * V2.0优化（极速版）：不再并行生成7种风格，而是智能识别最佳风格并直接生成原型。
     * 这避免了长时间等待和超时问题，提供更流畅的用户体验。
     */
    private void routeToDesignBranch(AppSpecEntity appSpec,
                                      String userRequirement,
                                      PlanRoutingResult.PlanRoutingResultBuilder resultBuilder) {
        log.info("路由到设计分支（单风格极速版） - appSpecId: {}", appSpec.getId());

        // Step 1: 智能识别最佳风格
        DesignStyle bestStyle = identifyBestStyle(userRequirement);
        log.info("智能推荐风格: {} ({})", bestStyle.getDisplayName(), bestStyle.getCode());

        // Step 2: 提取应用信息（用于构建Prompt）
        String appName = extractAppNameFromRequirement(userRequirement);
        List<String> features = extractFeaturesFromRequirement(userRequirement);

        // Step 3: 仅构建Prompt（不立即生成）
        // 使用SuperDesign的高质量Prompt构建逻辑
        String designPrompt = superDesignService.buildDesignPromptForOpenLovable(
                bestStyle, appName, userRequirement, features);

        // 更新specContent中的prompt，供后续Execute使用
        Map<String, Object> specContent = appSpec.getSpecContent();
        if (specContent == null) {
            specContent = new HashMap<>();
        }
        specContent.put("designPrompt", designPrompt); // 保存Prompt
        appSpec.setSpecContent(specContent);

        log.info("已智能选择风格: {}, 准备让前端触发SSE流式生成", bestStyle.getDisplayName());

        // Step 4: 构建单风格变体（用于兼容前端数据结构）
        Map<String, Object> styleVariant = new HashMap<>();
        styleVariant.put("styleId", bestStyle.getCode());
        styleVariant.put("styleName", bestStyle.getDisplayName());
        styleVariant.put("previewHtml", null); 
        styleVariant.put("aiGenerated", true);
        styleVariant.put("designSpec", null); 

        List<Map<String, Object>> styleVariants = Collections.singletonList(styleVariant);

        // Step 5: 更新AppSpec
        // 注意：此时prototypeUrl为空，prototypeGenerated为false
        appSpec.setSelectedStyle(bestStyle.getCode()); // 自动选中
        appSpec.setUpdatedAt(Instant.now());

        // 保存元数据
        Map<String, Object> metadata = appSpec.getMetadata() != null
                ? new HashMap<>(appSpec.getMetadata())
                : new HashMap<>();
        metadata.put("styleVariants", styleVariants);
        metadata.put("designBranchStartedAt", Instant.now().toString());
        metadata.put("autoSelectedStyle", bestStyle.getCode());
        appSpec.setMetadata(metadata);

        // Step 6: 返回结果（prototypeGenerated=false 触发前端SSE生成）
        resultBuilder
                .branch(RoutingBranch.DESIGN)
                .styleVariants(styleVariants)
                .prototypeGenerated(false) // 关键：标记为未生成
                .prototypeUrl(null)        // 关键：URL为空
                .selectedStyleId(bestStyle.getCode()) // 标记为已选择
                .nextAction("正在启动流式生成...")
                .requiresUserConfirmation(true);
    }

    /**
     * 根据用户需求智能识别最佳设计风格
     */
    private DesignStyle identifyBestStyle(String requirement) {
        if (requirement == null) {
            return DesignStyle.MODERN_MINIMAL;
        }
        String req = requirement.toLowerCase();

        // 规则匹配
        if (containsAny(req, "科技", "ai", "未来", "赛博", "数据", "智能", "tech", "cyber", "future")) {
            return DesignStyle.FUTURE_TECH;
        }
        if (containsAny(req, "时尚", "年轻", "社交", "娱乐", "电商", "潮流", "fashion", "social", "trend")) {
            return DesignStyle.VIBRANT_FASHION;
        }
        if (containsAny(req, "企业", "管理", "后台", "办公", "金融", "专业", "严肃", "admin", "business", "pro")) {
            return DesignStyle.CLASSIC_PROFESSIONAL;
        }
        if (containsAny(req, "游戏", "教育", "儿童", "趣味", "互动", "game", "play", "fun", "kid")) {
            return DesignStyle.GAMIFIED;
        }
        if (containsAny(req, "展示", "创意", "艺术", "画廊", "3d", "立体", "art", "gallery", "creative")) {
            return DesignStyle.IMMERSIVE_3D;
        }
        if (containsAny(req, "健康", "环保", "自然", "生活", "有机", "禅意", "nature", "eco", "life", "green")) {
            return DesignStyle.NATURAL_FLOW;
        }

        // 默认风格
        return DesignStyle.MODERN_MINIMAL;
    }

    /**
     * 从用户需求中提取应用名称
     * 简单实现：取第一句话的主语或使用默认名称
     */
    private String extractAppNameFromRequirement(String requirement) {
        if (requirement == null || requirement.isEmpty()) {
            return "我的应用";
        }

        // 尝试提取"创建XXX"、"开发XXX"、"构建XXX"等模式
        String[] patterns = {"创建", "开发", "构建", "设计", "实现"};
        for (String pattern : patterns) {
            int index = requirement.indexOf(pattern);
            if (index != -1) {
                String afterPattern = requirement.substring(index + pattern.length()).trim();
                String[] words = afterPattern.split("[，。、,\\s]");
                if (words.length > 0 && !words[0].isEmpty() && words[0].length() < 20) {
                    return words[0];
                }
            }
        }

        // 默认名称
        return "我的应用";
    }

    /**
     * 从用户需求中提取功能列表
     * 简单实现：提取包含"管理"、"查询"、"展示"等动词的短语
     */
    private List<String> extractFeaturesFromRequirement(String requirement) {
        if (requirement == null || requirement.isEmpty()) {
            return Collections.singletonList("数据管理");
        }

        List<String> features = new ArrayList<>();

        // 提取常见功能关键词
        String[] keywords = {"管理", "查询", "展示", "编辑", "删除", "创建", "浏览",
                "搜索", "筛选", "统计", "分析", "导出", "导入", "预订", "支付"};

        for (String keyword : keywords) {
            if (requirement.contains(keyword) && features.size() < 6) {
                // 提取包含关键词的短语
                int index = requirement.indexOf(keyword);
                int start = Math.max(0, index - 3);
                int end = Math.min(requirement.length(), index + keyword.length() + 3);
                String phrase = requirement.substring(start, end).trim();

                // 清理短语
                phrase = phrase.replaceAll("[，。、,\\s]+$", "")
                        .replaceAll("^[，。、,\\s]+", "");

                if (phrase.length() >= 2 && phrase.length() <= 10) {
                    features.add(phrase);
                }
            }
        }

        // 如果提取不到功能，使用默认功能
        if (features.isEmpty()) {
            features.add("数据管理");
            features.add("信息展示");
            features.add("用户交互");
        }

        // 限制最多6个功能
        return features.size() > 6 ? features.subList(0, 6) : features;
    }

    private boolean containsAny(String text, String... keywords) {
        for (String keyword : keywords) {
            if (text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }


    /**
     * 路由到混合分支：爬取+定制化修改
     * 注意：当前版本简化为先爬取，后续通过用户确认流程进行定制化
     *
     * V2.0优化：支持使用预创建的沙箱，节省30秒等待时间
     *
     * @param sandboxFuture 预创建的沙箱Future（可能还在创建中）
     */
    private void routeToHybridBranch(AppSpecEntity appSpec,
                                      IntentClassificationResult intentResult,
                                      PlanRoutingResult.PlanRoutingResultBuilder resultBuilder,
                                      CompletableFuture<Map<String, Object>> sandboxFuture) {
        log.info("路由到混合分支 - appSpecId: {}", appSpec.getId());

        List<String> referenceUrls = intentResult.getReferenceUrls();
        if (referenceUrls == null || referenceUrls.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "混合模式需要提供参考网站URL");
        }

        // Step 1: 爬取参考网站
        String targetUrl = referenceUrls.get(0);
        String customizationReq = intentResult.getCustomizationRequirement();

        // 构建请求，将定制化需求包含在提示词中
        OpenLovableGenerateRequest request = OpenLovableGenerateRequest.builder()
                .userRequirement("Clone and customize: " + targetUrl + " with modifications: " + customizationReq)
                .referenceUrls(Collections.singletonList(targetUrl))
                .needsCrawling(true)
                .streaming(false)
                .timeoutSeconds(120) // 混合模式需要更长时间
                .build();

        // V2.0优化：尝试使用预创建的沙箱
        OpenLovableGenerateResponse response;
        Map<String, Object> prewarmedSandbox = waitForPrewarmedSandbox(sandboxFuture);

        if (prewarmedSandbox != null) {
            log.info("使用预创建的沙箱进行混合分支");
            response = openLovableService.generatePrototypeWithPrewarmedSandbox(request, prewarmedSandbox);
        } else {
            log.info("预创建沙箱不可用，走正常流程");
            response = openLovableService.generatePrototype(request);
        }

        // 转换响应为Map存储
        Map<String, Object> customizedPrototype = new HashMap<>();
        customizedPrototype.put("previewUrl", response.getPreviewUrl());
        customizedPrototype.put("sandboxId", response.getSandboxId());
        customizedPrototype.put("provider", response.getProvider());
        customizedPrototype.put("generatedAt", response.getCompletedAt() != null ? response.getCompletedAt().toString() : Instant.now().toString());

        // 更新AppSpec
        appSpec.setFrontendPrototype(customizedPrototype);
        appSpec.setFrontendPrototypeUrl(response.getPreviewUrl());
        appSpec.setPrototypeGeneratedAt(Instant.now());

        // 保存定制化信息到metadata
        Map<String, Object> metadata = appSpec.getMetadata() != null
                ? new HashMap<>(appSpec.getMetadata())
                : new HashMap<>();
        metadata.put("customizationRequirement", customizationReq);
        metadata.put("hybridBranchStartedAt", Instant.now().toString());
        appSpec.setMetadata(metadata);

        resultBuilder
                .branch(RoutingBranch.HYBRID)
                .prototypeGenerated(true)
                .prototypeUrl(response.getPreviewUrl())
                .nextAction("请预览定制化原型并确认设计")
                .requiresUserConfirmation(true);
    }

    /**
     * 用户选择设计风格后生成原型
     *
     * @param appSpecId AppSpec ID
     * @param selectedStyleId 用户选择的风格ID（A-G）
     * @return 原型生成结果
     */
    @Transactional
    public PlanRoutingResult selectStyleAndGeneratePrototype(UUID appSpecId, String selectedStyleId) {
        log.info("用户选择设计风格 - appSpecId: {}, selectedStyleId: {}", appSpecId, selectedStyleId);

        AppSpecEntity appSpec = appSpecMapper.selectById(appSpecId);
        if (appSpec == null) {
            throw new BusinessException(ErrorCode.APPSPEC_NOT_FOUND);
        }

        // 验证是否为设计分支
        if (!RequirementIntent.DESIGN_FROM_SCRATCH.name().equals(appSpec.getIntentType())) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "仅设计分支支持风格选择");
        }

        // 获取风格变体（防御性检查）
        Map<String, Object> metadata = appSpec.getMetadata();
        if (metadata == null) {
            log.error("AppSpec metadata为null - appSpecId: {}", appSpecId);
            throw new BusinessException(ErrorCode.PARAM_ERROR, "风格方案尚未生成（metadata为空）");
        }

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> styleVariants = (List<Map<String, Object>>) metadata.get("styleVariants");

        if (styleVariants == null || styleVariants.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "风格方案尚未生成");
        }

        // 查找选中的风格
        Optional<Map<String, Object>> selectedVariant = styleVariants.stream()
                .filter(v -> selectedStyleId.equals(v.get("styleId")))
                .findFirst();

        if (selectedVariant.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "无效的风格ID: " + selectedStyleId);
        }

        // 直接使用选中的风格变体（已包含完整的previewHtml）
        Map<String, Object> selectedStyle = selectedVariant.get();
        String styleName = (String) selectedStyle.get("styleName");
        String previewHtml = (String) selectedStyle.get("previewHtml");

        log.info("使用已生成的风格变体 - styleId: {}, styleName: {}, HTML长度: {}",
                selectedStyleId, styleName, previewHtml != null ? previewHtml.length() : 0);

        // 提取designSpec（AI生成时提供）
        @SuppressWarnings("unchecked")
        Map<String, Object> designSpec = (Map<String, Object>) selectedStyle.get("designSpec");

        if (designSpec != null) {
            log.info("成功提取designSpec - styleId: {}, designSpec包含: colorTheme={}, typography={}, layout={}, components={}",
                    selectedStyleId,
                    designSpec.containsKey("colorTheme"),
                    designSpec.containsKey("typography"),
                    designSpec.containsKey("layout"),
                    designSpec.containsKey("components"));
        } else {
            log.warn("designSpec为空 - styleId: {}, aiGenerated: {}",
                    selectedStyleId, selectedStyle.get("aiGenerated"));
        }

        // ⭐ 新逻辑：调用 OpenLovable 生成真实的交互式原型（E2B Sandbox）
        log.info("开始调用 OpenLovable 生成交互式原型 - styleId: {}", selectedStyleId);

        // Step 1: 从 specContent 提取用户需求
        Map<String, Object> specContent = appSpec.getSpecContent();
        String userRequirement = (specContent != null && specContent.get("userRequirement") != null)
                ? specContent.get("userRequirement").toString()
                : "用户需求信息不完整";

        // Step 2: 构建基于 designSpec 的详细 prompt
        String designPrompt = buildDesignPromptFromSpec(userRequirement, designSpec);
        log.info("用户需求: {}, 生成的设计prompt长度: {}", userRequirement, designPrompt.length());

        // Step 2: 调用 OpenLovable 服务生成原型
        OpenLovableGenerateRequest openLovableRequest = OpenLovableGenerateRequest.builder()
                .userRequirement(designPrompt)
                .referenceUrls(Collections.emptyList())
                .needsCrawling(false)
                .streaming(false)
                .aiModel("deepseek-v3")
                .timeoutSeconds(90) // 设计分支需要更长时间
                .build();

        long prototypeStartTime = System.currentTimeMillis();
        OpenLovableGenerateResponse openLovableResponse;

        try {
            openLovableResponse = openLovableService.generatePrototype(openLovableRequest);
            long prototypeDuration = System.currentTimeMillis() - prototypeStartTime;

            log.info("OpenLovable原型生成成功 - sandboxId: {}, previewUrl: {}, 耗时: {}秒",
                    openLovableResponse.getSandboxId(),
                    openLovableResponse.getPreviewUrl(),
                    prototypeDuration / 1000);

        } catch (Exception e) {
            log.error("OpenLovable原型生成失败 - styleId: {}", selectedStyleId, e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR,
                    "原型生成失败: " + e.getMessage() + "。请确认OpenLovable服务已启动（默认端口3001）");
        }

        // Step 3: 保存原型信息到 AppSpec
        Map<String, Object> prototypeResult = new HashMap<>();
        prototypeResult.put("sandboxId", openLovableResponse.getSandboxId());
        prototypeResult.put("previewUrl", openLovableResponse.getPreviewUrl());
        prototypeResult.put("provider", openLovableResponse.getProvider());
        prototypeResult.put("selectedStyleId", selectedStyleId);
        prototypeResult.put("selectedStyleName", styleName);
        prototypeResult.put("generatedAt", Instant.now().toString());
        prototypeResult.put("aiGenerated", true); // OpenLovable 生成的原型
        prototypeResult.put("generationTime", openLovableResponse.getDurationSeconds());
        prototypeResult.put("designSpec", designSpec); // 保留设计规范

        // 更新AppSpec
        appSpec.setSelectedStyle(selectedStyleId);
        appSpec.setFrontendPrototype(prototypeResult);
        appSpec.setFrontendPrototypeUrl(openLovableResponse.getPreviewUrl()); // ⭐ 设置真实的 E2B URL
        appSpec.setPrototypeGeneratedAt(Instant.now());
        appSpec.setUpdatedAt(Instant.now());

        // 保存designSpec到metadata（供ExecuteAgent使用）
        Map<String, Object> updatedMetadata = appSpec.getMetadata() != null
                ? new HashMap<>(appSpec.getMetadata())
                : new HashMap<>();
        updatedMetadata.put("selectedDesignSpec", designSpec);
        updatedMetadata.put("selectedStyleId", selectedStyleId);
        updatedMetadata.put("sandboxId", openLovableResponse.getSandboxId());
        updatedMetadata.put("designSpecSavedAt", Instant.now().toString());
        appSpec.setMetadata(updatedMetadata);

        log.info("原型信息已保存 - appSpecId: {}, sandboxId: {}, previewUrl: {}",
                appSpecId, openLovableResponse.getSandboxId(), openLovableResponse.getPreviewUrl());

        appSpecMapper.updateById(appSpec);

        log.info("风格选择并生成原型完成 - appSpecId: {}, selectedStyleId: {}, E2B URL: {}",
                appSpecId, selectedStyleId, openLovableResponse.getPreviewUrl());

        return PlanRoutingResult.builder()
                .appSpecId(appSpecId)
                .intent(RequirementIntent.DESIGN_FROM_SCRATCH)
                .branch(RoutingBranch.DESIGN)
                .prototypeGenerated(true)
                .prototypeUrl(openLovableResponse.getPreviewUrl()) // ⭐ 返回真实的 E2B Sandbox URL
                .selectedStyleId(selectedStyleId)
                .nextAction("请预览交互式原型并确认设计")
                .requiresUserConfirmation(true)
                .build();
    }

    /**
     * 用户确认设计
     *
     * @param appSpecId AppSpec ID
     */
    @Transactional
    public void confirmDesign(UUID appSpecId) {
        log.info("用户确认设计 - appSpecId: {}", appSpecId);

        AppSpecEntity appSpec = appSpecMapper.selectById(appSpecId);
        if (appSpec == null) {
            throw new BusinessException(ErrorCode.APPSPEC_NOT_FOUND);
        }

        if (appSpec.getFrontendPrototype() == null || appSpec.getFrontendPrototype().isEmpty()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "原型尚未生成，无法确认设计");
        }

        appSpec.setDesignConfirmed(true);
        appSpec.setDesignConfirmedAt(Instant.now());
        appSpec.setUpdatedAt(Instant.now());

        appSpecMapper.updateById(appSpec);

        log.info("设计确认完成 - appSpecId: {}, confirmedAt: {}",
                appSpecId, appSpec.getDesignConfirmedAt());
    }

    /**
     * 从 designSpec 构建 OpenLovable 的详细设计 prompt
     *
     * 将 SuperDesign 生成的结构化设计规范转换为自然语言描述，
     * 让 OpenLovable 能够理解并生成符合设计约束的 React 代码。
     *
     * @param userRequirement 用户原始需求
     * @param designSpec      设计规范（包含colorTheme、typography、layout、components）
     * @return 详细的设计 prompt
     */
    public String buildDesignPromptFromSpec(String userRequirement, Map<String, Object> designSpec) {
        log.info("开始构建设计prompt - userRequirement长度: {}, designSpec包含字段: {}",
                userRequirement != null ? userRequirement.length() : 0,
                designSpec != null ? designSpec.keySet() : "null");

        if (designSpec == null || designSpec.isEmpty()) {
            log.warn("designSpec为空，使用默认prompt");
            return userRequirement;
        }

        StringBuilder prompt = new StringBuilder();

        // 1. 用户需求
        prompt.append("用户需求：\n").append(userRequirement).append("\n\n");

        // 2. 设计约束说明
        prompt.append("请严格按照以下设计规范生成React应用代码：\n\n");

        // 3. 颜色主题 (colorTheme)
        if (designSpec.containsKey("colorTheme")) {
            Map<String, Object> colorTheme = (Map<String, Object>) designSpec.get("colorTheme");
            prompt.append("## 颜色主题\n");

            if (colorTheme.containsKey("primary")) {
                prompt.append("- 主色调：").append(colorTheme.get("primary")).append("\n");
            }
            if (colorTheme.containsKey("secondary")) {
                prompt.append("- 辅助色：").append(colorTheme.get("secondary")).append("\n");
            }
            if (colorTheme.containsKey("accent")) {
                prompt.append("- 强调色：").append(colorTheme.get("accent")).append("\n");
            }
            if (colorTheme.containsKey("background")) {
                prompt.append("- 背景色：").append(colorTheme.get("background")).append("\n");
            }
            if (colorTheme.containsKey("surface")) {
                prompt.append("- 表面色：").append(colorTheme.get("surface")).append("\n");
            }
            if (colorTheme.containsKey("text")) {
                Map<String, Object> textColors = (Map<String, Object>) colorTheme.get("text");
                if (textColors != null) {
                    prompt.append("- 文本颜色：");
                    prompt.append("主要文本 ").append(textColors.get("primary"));
                    prompt.append("，次要文本 ").append(textColors.get("secondary"));
                    prompt.append("\n");
                }
            }
            prompt.append("\n");
        }

        // 4. 字体排版 (typography)
        if (designSpec.containsKey("typography")) {
            Map<String, Object> typography = (Map<String, Object>) designSpec.get("typography");
            prompt.append("## 字体排版\n");

            if (typography.containsKey("fontFamily")) {
                Map<String, Object> fontFamily = (Map<String, Object>) typography.get("fontFamily");
                if (fontFamily != null) {
                    prompt.append("- 主字体：").append(fontFamily.get("primary")).append("\n");
                    prompt.append("- 标题字体：").append(fontFamily.get("heading")).append("\n");
                }
            }
            if (typography.containsKey("fontSize")) {
                Map<String, Object> fontSize = (Map<String, Object>) typography.get("fontSize");
                if (fontSize != null) {
                    prompt.append("- 字体大小：基础 ").append(fontSize.get("base"));
                    prompt.append("，小号 ").append(fontSize.get("sm"));
                    prompt.append("，大号 ").append(fontSize.get("lg"));
                    prompt.append("\n");
                }
            }
            if (typography.containsKey("lineHeight")) {
                prompt.append("- 行高：").append(typography.get("lineHeight")).append("\n");
            }
            prompt.append("\n");
        }

        // 5. 布局风格 (layout)
        if (designSpec.containsKey("layout")) {
            Map<String, Object> layout = (Map<String, Object>) designSpec.get("layout");
            prompt.append("## 布局风格\n");

            if (layout.containsKey("spacing")) {
                prompt.append("- 间距系统：").append(layout.get("spacing")).append("\n");
            }
            if (layout.containsKey("containerWidth")) {
                prompt.append("- 容器宽度：").append(layout.get("containerWidth")).append("\n");
            }
            if (layout.containsKey("borderRadius")) {
                prompt.append("- 圆角大小：").append(layout.get("borderRadius")).append("\n");
            }
            if (layout.containsKey("shadow")) {
                prompt.append("- 阴影效果：").append(layout.get("shadow")).append("\n");
            }
            prompt.append("\n");
        }

        // 6. 组件样式 (components)
        if (designSpec.containsKey("components")) {
            Map<String, Object> components = (Map<String, Object>) designSpec.get("components");
            prompt.append("## 组件样式约束\n");

            if (components.containsKey("button")) {
                Map<String, Object> button = (Map<String, Object>) components.get("button");
                prompt.append("- 按钮：")
                        .append("圆角 ").append(button.get("borderRadius"))
                        .append("，内边距 ").append(button.get("padding"))
                        .append("\n");
            }
            if (components.containsKey("input")) {
                Map<String, Object> input = (Map<String, Object>) components.get("input");
                prompt.append("- 输入框：")
                        .append("边框 ").append(input.get("border"))
                        .append("，圆角 ").append(input.get("borderRadius"))
                        .append("\n");
            }
            if (components.containsKey("card")) {
                Map<String, Object> card = (Map<String, Object>) components.get("card");
                prompt.append("- 卡片：")
                        .append("阴影 ").append(card.get("shadow"))
                        .append("，圆角 ").append(card.get("borderRadius"))
                        .append("\n");
            }
            if (components.containsKey("nav")) {
                Map<String, Object> nav = (Map<String, Object>) components.get("nav");
                prompt.append("- 导航栏：")
                        .append("高度 ").append(nav.get("height"))
                        .append("，背景 ").append(nav.get("background"))
                        .append("\n");
            }
            prompt.append("\n");
        }

        // 7. 技术要求
        prompt.append("## 技术要求\n");
        prompt.append("- 使用 React + Tailwind CSS 构建\n");
        prompt.append("- 使用 Lucide React 图标库\n");
        prompt.append("- 确保响应式设计，支持移动端和桌面端\n");
        prompt.append("- 代码应该简洁、可维护、符合React最佳实践\n");
        prompt.append("- 严格遵守上述颜色、字体、布局、组件样式约束\n");

        String finalPrompt = prompt.toString();
        log.info("设计prompt构建完成，总长度: {}", finalPrompt.length());

        return finalPrompt;
    }

    /**
     * 执行代码生成（用户确认设计后调用）
     *
     * Phase 2.2.3架构桥接方法：
     * - 从AppSpec.metadata提取designSpec
     * - 构建PlanResult并填充designSpec
     * - (新增) 填充analysisContext到PlanResult
     * - 调用ExecuteAgent生成代码
     *
     * @param appSpecId AppSpec ID
     * @param analysisContext 前端回传的SSE分析上下文（可选）
     * @return 代码生成结果
     */
    @Transactional
    public Map<String, Object> executeCodeGeneration(UUID appSpecId, Map<String, Object> analysisContext) {
        log.info("[PlanRouting] 开始执行代码生成 - appSpecId: {}, hasContext: {}", appSpecId, analysisContext != null);

        // Step 1: 查询并验证AppSpec
        AppSpecEntity appSpec = appSpecMapper.selectById(appSpecId);
        if (appSpec == null) {
            throw new BusinessException(ErrorCode.APPSPEC_NOT_FOUND);
        }

        if (!appSpec.getDesignConfirmed()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "设计未确认，无法生成代码");
        }

        // Step 2: 从metadata提取designSpec
        Map<String, Object> metadata = appSpec.getMetadata();
        @SuppressWarnings("unchecked")
        Map<String, Object> designSpec = (metadata != null)
                ? (Map<String, Object>) metadata.get("selectedDesignSpec")
                : null;

        log.info("[PlanRouting] 提取到designSpec: {}", designSpec != null ? "present" : "null");

        // Step 3: 构建或增强PlanResult
        PlanResult planResult = buildPlanResultFromAppSpec(appSpec);

        // 关键：填充designSpec到PlanResult
        planResult.setDesignSpec(designSpec);

        // V2.0新增：填充frontendPrototype到PlanResult
        if (appSpec.getFrontendPrototype() != null) {
            planResult.setFrontendPrototype(appSpec.getFrontendPrototype());
            log.info("[PlanRouting] 已填充frontendPrototype: sandboxId={}",
                    appSpec.getFrontendPrototype().get("sandboxId"));
        }

        // V2.0新增：填充analysisContext到PlanResult
        if (analysisContext != null && !analysisContext.isEmpty()) {
            planResult.setAnalysisContext(analysisContext);
            log.info("[PlanRouting] 已填充analysisContext: keys={}", analysisContext.keySet());
            
            // 也保存到AppSpec metadata以备查
            Map<String, Object> updatedMetadata = appSpec.getMetadata() != null
                    ? new HashMap<>(appSpec.getMetadata())
                    : new HashMap<>();
            updatedMetadata.put("analysisContext", analysisContext);
            appSpec.setMetadata(updatedMetadata);
            // 注意：这里暂时不保存update，下面会统一保存
        }

        log.info("[PlanRouting] PlanResult已填充designSpec和Context，准备调用ExecuteAgent");

        // Step 4: 调用ExecuteAgent
        IExecuteAgent executeAgent = executeAgentFactory.getExecuteAgent();
        Map<String, Object> codeResult = executeAgent.execute(planResult);

        // V2.0新增：保存生成代码到metadata，确保持久化
        if (codeResult != null) {
            Map<String, Object> updatedMetadata = appSpec.getMetadata() != null
                    ? new HashMap<>(appSpec.getMetadata())
                    : new HashMap<>();
            updatedMetadata.put("generatedCode", codeResult);
            updatedMetadata.put("codeGeneratedAt", Instant.now().toString());
            appSpec.setMetadata(updatedMetadata);
            appSpec.setUpdatedAt(Instant.now());
            
            appSpecMapper.updateById(appSpec);
            log.info("[PlanRouting] 生成代码已保存到metadata - appSpecId: {}", appSpecId);
        }

        log.info("[PlanRouting] ✅ 代码生成完成 - appSpecId: {}, version: {}",
                appSpecId, executeAgent.getVersion());

        return codeResult;
    }

    /**
     * 从AppSpec反向构建PlanResult（辅助方法）
     *
     * 根据AppSpec中保存的信息构建ExecuteAgent需要的PlanResult对象
     *
     * @param appSpec AppSpec实体
     * @return PlanResult对象
     */
    private PlanResult buildPlanResultFromAppSpec(AppSpecEntity appSpec) {
        log.info("[PlanRouting] 开始从AppSpec构建PlanResult - appSpecId: {}", appSpec.getId());

        // 从specContent提取用户需求
        Map<String, Object> specContent = appSpec.getSpecContent();
        String userRequirement = (specContent != null && specContent.get("userRequirement") != null)
                ? specContent.get("userRequirement").toString()
                : "用户需求信息不完整";

        // 从intentClassificationResult提取reasoning
        Map<String, Object> intentResult = appSpec.getIntentClassificationResult();
        String reasoning = (intentResult != null && intentResult.get("reasoning") != null)
                ? intentResult.get("reasoning").toString()
                : userRequirement;

        // 构建PlanResult（简化版本，主要传递需求描述）
        PlanResult planResult = PlanResult.builder()
                .reasoning(reasoning)
                .modules(Collections.emptyList()) // 暂时使用空列表
                .complexityScore(5) // 默认中等复杂度
                .estimatedHours(40) // 默认预估时间
                .build();

        log.info("[PlanRouting] PlanResult构建完成 - reasoning长度: {}", reasoning.length());

        return planResult;
    }

    /**
     * 获取风格代码的中文显示名称
     *
     * @param styleCode 风格代码（如：modern_minimal）
     * @return 中文显示名称（如：现代极简）
     */
    private String getStyleDisplayName(String styleCode) {
        if (styleCode == null) {
            return "未知风格";
        }
        return switch (styleCode) {
            case "modern_minimal" -> "现代极简";
            case "vibrant_fashion" -> "活力时尚";
            case "classic_professional" -> "经典专业";
            case "future_tech" -> "未来科技";
            case "immersive_3d" -> "沉浸式3D";
            case "gamified" -> "游戏化设计";
            case "natural_flow" -> "自然流动";
            default -> styleCode;
        };
    }

    /**
     * 等待预创建的沙箱完成（V2.0性能优化辅助方法）
     *
     * 策略：
     * - 最多等待60秒（沙箱创建通常需要30秒）
     * - 如果超时或失败，返回null，由调用方决定是否走正常流程
     *
     * @param sandboxFuture 沙箱创建的Future
     * @return 沙箱信息，如果失败则返回null
     */
    private Map<String, Object> waitForPrewarmedSandbox(CompletableFuture<Map<String, Object>> sandboxFuture) {
        if (sandboxFuture == null) {
            return null;
        }

        try {
            long startWait = System.currentTimeMillis();
            log.info("等待预创建沙箱完成...");

            // 最多等待60秒
            Map<String, Object> result = sandboxFuture.get(60, TimeUnit.SECONDS);

            long waitDuration = System.currentTimeMillis() - startWait;
            if (result != null) {
                log.info("预创建沙箱已就绪: sandboxId={}, 等待耗时={}ms",
                        result.get("sandboxId"), waitDuration);
            } else {
                log.warn("预创建沙箱返回null，等待耗时={}ms", waitDuration);
            }

            return result;

        } catch (TimeoutException e) {
            log.warn("等待预创建沙箱超时（60秒），将使用正常流程创建");
            sandboxFuture.cancel(true);
            return null;
        } catch (InterruptedException e) {
            log.warn("等待预创建沙箱被中断");
            Thread.currentThread().interrupt();
            return null;
        } catch (ExecutionException e) {
            log.warn("预创建沙箱执行失败: {}", e.getCause().getMessage());
            return null;
        } catch (Exception e) {
            log.warn("等待预创建沙箱异常: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 根据用户预选的复杂度和技术栈提示增强需求描述
     *
     * V2.0新增：将用户在首页选择的分类信息融入需求描述，
     * 帮助AI更准确地进行意图识别和技术栈推荐
     *
     * @param userRequirement 原始用户需求
     * @param complexityHint 用户预选的复杂度分类（可选）
     * @param techStackHint 用户预选的技术栈提示（可选）
     * @return 增强后的需求描述
     */
    private String enhanceRequirementWithHint(String userRequirement, String complexityHint, String techStackHint) {
        if ((complexityHint == null || complexityHint.isEmpty()) &&
            (techStackHint == null || techStackHint.isEmpty())) {
            // 没有任何提示，直接返回原始需求
            return userRequirement;
        }

        StringBuilder enhanced = new StringBuilder(userRequirement);
        enhanced.append("\n\n[用户预选信息]");

        // 添加复杂度分类提示
        if (complexityHint != null && !complexityHint.isEmpty()) {
            String complexityDescription = switch (complexityHint) {
                case "SIMPLE" -> "多端套壳应用（H5+WebView），适用于内容展示、表单、列表类应用，无原生功能需求";
                case "MEDIUM" -> "纯Web应用（React+Supabase），仅在浏览器运行，适用于SaaS或Dashboard类应用";
                case "COMPLEX" -> "企业级应用（React+Spring Boot），复杂业务逻辑，多实体关联系统";
                case "NEEDS_CONFIRMATION" -> "原生跨端应用（Kuikly），需要相机/GPS/蓝牙等原生能力";
                default -> complexityHint;
            };
            enhanced.append("\n- 复杂度分类: ").append(complexityDescription);
        }

        // 添加技术栈提示
        if (techStackHint != null && !techStackHint.isEmpty()) {
            enhanced.append("\n- 技术栈偏好: ").append(techStackHint);
        }

        enhanced.append("\n请在分析时优先考虑用户预选的分类和技术栈。");

        String result = enhanced.toString();
        log.debug("需求增强完成 - 原始长度: {}, 增强后长度: {}", userRequirement.length(), result.length());

        return result;
    }

    /**
     * 路由分支枚举
     */
    public enum RoutingBranch {
        CLONE("克隆分支"),
        DESIGN("设计分支"),
        HYBRID("混合分支");

        private final String displayName;

        RoutingBranch(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }
}
