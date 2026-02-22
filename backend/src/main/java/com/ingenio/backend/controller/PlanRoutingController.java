package com.ingenio.backend.controller;

import cn.dev33.satoken.secure.BCrypt;
import cn.dev33.satoken.stp.StpUtil;
import com.ingenio.backend.agent.dto.RequirementIntent;
import com.ingenio.backend.common.response.Result;
import com.ingenio.backend.dto.request.PlanRoutingRequest;
import com.ingenio.backend.dto.request.OpenLovableGenerateRequest;
import com.ingenio.backend.dto.response.OpenLovableGenerateResponse;
import com.ingenio.backend.entity.AppSpecEntity;
import com.ingenio.backend.entity.GenerationTaskEntity;
import com.ingenio.backend.entity.ProjectEntity;
import com.ingenio.backend.entity.UserEntity;
import com.ingenio.backend.enums.DesignStyle;
import com.ingenio.backend.enums.TechStackType;
import com.ingenio.backend.mapper.UserMapper;
import com.ingenio.backend.mapper.GenerationTaskMapper;
import com.ingenio.backend.service.AppSpecService;
import com.ingenio.backend.service.BillingService;
import com.ingenio.backend.service.OpenLovableService;
import com.ingenio.backend.service.ProjectService;
import com.ingenio.backend.service.VersionSnapshotService;
import com.ingenio.backend.service.g3.G3OrchestratorService;
import com.ingenio.backend.dto.VersionType;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Plan Routing（V2）控制器
 *
 * 目标：
 * - 修复前端“深度分析/生成流程”在调用 `/v2/plan-routing/route` 时返回 500 的问题。
 *
 * 背景：
 * - 前端 `frontend/src/lib/api/plan-routing.ts:1` 会调用 `/v2/plan-routing/route` 获取
 * appSpecId + 路由分支。
 * - 之前 Spring Boot 侧缺失该接口，导致 NoHandler 异常被全局异常处理器包装为“系统错误(1000)”，前端显示 HTTP 500。
 *
 * 说明：
 * - 当前实现优先保证“可用性与一致性”：返回结构遵循 `shared/types/plan-routing.types.ts:1`。
 * - 后续可在此基础上接入真实的 IntentClassifier / 模板匹配 / Blueprint 绑定等能力。
 */
@Slf4j
@RestController
@RequestMapping("/v2/plan-routing")
@RequiredArgsConstructor
public class PlanRoutingController {

    /**
     * 默认租户ID（与 AppSpecController 保持一致）
     * 用于本地未登录/Session缺失时的兜底，避免写入空租户导致数据库约束失败。
     */
    private static final UUID DEFAULT_TENANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    /**
     * 默认系统用户名前缀
     *
     * 作用：为无登录态的路由请求生成占位用户，避免 app_specs 外键约束失败。
     * 说明：前缀稳定且与真实用户区分，便于后续排查与清理。
     */
    private static final String DEFAULT_USER_PREFIX = "system_user";

    private static final Pattern URL_PATTERN = Pattern.compile("https?://\\S+", Pattern.CASE_INSENSITIVE);
    private static final Pattern DOMAIN_PATTERN = Pattern.compile("\\b([a-zA-Z0-9.-]+\\.[a-zA-Z]{2,})(?:/[^\\s]*)?\\b");

    private final AppSpecService appSpecService;
    private final BillingService billingService;
    private final OpenLovableService openLovableService;
    private final UserMapper userMapper;
    private final ProjectService projectService;
    private final GenerationTaskMapper generationTaskMapper;
    private final VersionSnapshotService versionSnapshotService;
    private final com.ingenio.backend.service.NLRequirementAnalyzer nlRequirementAnalyzer;
    /**
     * G3 编排器服务
     *
     * 是什么：G3 引擎任务的统一入口。
     * 做什么：在 Execute 阶段触发 G3 任务并返回 jobId。
     * 为什么：保证前端/测试能够基于 jobId 订阅日志与产物。
     */
    private final G3OrchestratorService g3OrchestratorService;

    /**
     * 路由用户需求（意图识别 + 分支决策 + 生成 AppSpec）
     *
     * @param request 路由请求（用户需求 + 可选租户/用户）
     * @return 路由结果（包含 appSpecId 与下一步指引）
     */
    @PostMapping("/route")
    public Result<PlanRoutingResult> routeRequirement(@Valid @RequestBody PlanRoutingRequest request) {
        String requirement = request.getUserRequirement().trim();

        UUID userId = coerceUuid(request.getUserId());
        UUID tenantId = coerceUuid(request.getTenantId());

        // 若传入 appSpecId，则优先复用已有 AppSpec，避免“修改一次就新建一条记录”导致上下文断裂
        AppSpecEntity appSpec = null;
        if (request.getAppSpecId() != null) {
            appSpec = appSpecService.getById(request.getAppSpecId());
            if (appSpec == null) {
                log.warn("传入的 appSpecId 不存在，将回退到新建: {}", request.getAppSpecId());
            } else {
                // 复用 AppSpec 的租户/用户，保持一致性
                if (appSpec.getTenantId() != null) {
                    tenantId = appSpec.getTenantId();
                }
                if (appSpec.getCreatedByUserId() != null) {
                    userId = appSpec.getCreatedByUserId();
                }
            }
        }

        // 优先从登录态/Session 中补齐（即使 /v2/** 在拦截器中被 exclude，StpUtil 仍可能读取到 token）
        if (userId == null) {
            userId = getLoginUserIdOrNull();
        }
        if (tenantId == null) {
            tenantId = getSessionTenantIdOrNull();
        }
        boolean isFallbackUser = false;
        if (tenantId == null) {
            tenantId = DEFAULT_TENANT_ID;
        }
        if (userId == null) {
            userId = tenantId;
            isFallbackUser = true;
        }

        if (isFallbackUser) {
            ensureFallbackUserExists(userId, tenantId);
        }

        RoutingDecision decision = decideRouting(requirement);

        // 1) 创建/更新 AppSpec（最小 specContent，后续由 Execute 阶段继续填充）
        if (appSpec == null) {
            Map<String, Object> specContent = new HashMap<>();
            specContent.put("userRequirement", requirement);
            specContent.put("stage", "planning");
            appSpec = appSpecService.createAppSpec(tenantId, userId, specContent);
        } else {
            Map<String, Object> nextSpecContent = appSpec.getSpecContent() != null
                    ? new HashMap<>(appSpec.getSpecContent())
                    : new HashMap<>();
            nextSpecContent.put("userRequirement", requirement);
            nextSpecContent.putIfAbsent("stage", "planning");
            appSpec.setSpecContent(nextSpecContent);
        }

        // 2) 写入 V2 关键元数据（便于后续流程读取）
        appSpec.setIntentType(decision.intent.name());
        appSpec.setConfidenceScore(BigDecimal.valueOf(decision.confidence));
        Map<String, Object> metadata = appSpec.getMetadata() != null ? new HashMap<>(appSpec.getMetadata())
                : new HashMap<>();
        metadata.put("intent", decision.intent.name());
        metadata.put("branch", decision.branch.name());
        metadata.put("confidence", decision.confidence);
        metadata.putIfAbsent("createdAt", Instant.now().toString());
        metadata.put("updatedAt", Instant.now().toString());
        metadata.put("requirementUpdatedAt", Instant.now().toString());
        appSpec.setMetadata(metadata);

        // DESIGN 分支默认选择风格（避免前端 selectedStyleId 为空导致流程阻塞）
        String selectedStyleId = appSpec.getSelectedStyle();
        if (decision.branch == RoutingBranch.DESIGN && !StringUtils.hasText(selectedStyleId)) {
            selectedStyleId = DesignStyle.MODERN_MINIMAL.getCode();
            appSpec.setSelectedStyle(selectedStyleId);
        }

        appSpecService.updateById(appSpec);

        // 2.3) 创建或获取关联的 Project（确保 Dashboard 可以显示）
        final UUID finalUserId = userId;
        final UUID finalTenantId = tenantId;
        ProjectEntity project = projectService.findByAppSpecId(appSpec.getId());
        if (project == null) {
            // 从需求中提取项目名称（取前30个字符作为名称）
            String projectName = requirement.length() > 30
                    ? requirement.substring(0, 30) + "..."
                    : requirement;

            project = ProjectEntity.builder()
                    .tenantId(finalTenantId)
                    .userId(finalUserId)
                    .name(projectName)
                    .description(requirement)
                    .appSpecId(appSpec.getId())
                    .status(ProjectEntity.Status.DRAFT.getValue())
                    .visibility(ProjectEntity.Visibility.PRIVATE.getValue())
                    .build();
            project = projectService.createProject(project);
            log.info("创建关联项目成功: projectId={}, appSpecId={}", project.getId(), appSpec.getId());
        } else {
            // 更新已存在的 Project（如果需求有变化）
            if (!requirement.equals(project.getDescription())) {
                project.setDescription(requirement);
                if (requirement.length() <= 30) {
                    project.setName(requirement);
                }
                projectService.updateById(project);
                log.info("更新关联项目: projectId={}, appSpecId={}", project.getId(), appSpec.getId());
            }
        }
        final UUID projectId = project.getId();

        // 2.5) CLONE/HYBRID 分支尝试快速生成原型（OpenLovable）
        if (decision.branch != RoutingBranch.DESIGN) {
            boolean hasPrototype = StringUtils.hasText(appSpec.getFrontendPrototypeUrl())
                    || appSpec.getPrototypeGeneratedAt() != null;
            if (!hasPrototype) {
                try {
                    List<String> referenceUrls = extractReferenceUrls(requirement);
                    OpenLovableGenerateRequest openLovableRequest = OpenLovableGenerateRequest.builder()
                            .userRequirement(requirement)
                            .referenceUrls(referenceUrls)
                            .needsCrawling(!referenceUrls.isEmpty())
                            // CLONE/HYBRID 直出原型：需要等待生成完成后再返回 prototypeUrl
                            .streaming(false)
                            // 关键修复：不强行指定默认模型（避免模型/密钥不匹配导致返回空代码）
                            // 由 open-lovable-cn 侧选择其默认可用模型，必要时再在前端发起 SSE 生成
                            .aiModel(null)
                            // 克隆/爬取链路可能明显慢于"纯生成"，默认放宽超时，避免过早返回空原型
                            .timeoutSeconds(180)
                            .build();
                    OpenLovableGenerateResponse preview = openLovableService.generatePrototype(openLovableRequest);
                    if (preview != null && preview.isSuccessful()) {
                        applyPrototypeToAppSpec(appSpec, preview);
                        appSpecService.updateById(appSpec);
                    } else {
                        log.warn("OpenLovable 原型生成未成功: appSpecId={}, message={}",
                                appSpec.getId(), preview != null ? preview.getErrorMessage() : "unknown");
                    }
                } catch (Exception e) {
                    log.warn("OpenLovable 原型生成异常: appSpecId={}", appSpec.getId(), e);
                }
            }
        }

        // 2.6) 确定技术栈类型（V2.0新增：根据 complexityHint/techStackHint/需求内容 确定）
        TechStackType techStack = determineTechStack(
                request.getComplexityHint(),
                request.getTechStackHint(),
                requirement);

        // 将技术栈信息写入 AppSpec metadata
        metadata.put("techStackType", techStack.name());
        metadata.put("techStackCode", techStack.getCode());
        metadata.put("techStackDescription", techStack.getDescription());
        appSpec.setMetadata(metadata);
        appSpecService.updateById(appSpec);

        // 3) 构建返回结果
        List<StyleVariant> styleVariants = decision.branch == RoutingBranch.DESIGN
                ? buildAllStyleVariants()
                : List.of();

        boolean prototypeGenerated = StringUtils.hasText(appSpec.getFrontendPrototypeUrl())
                || appSpec.getPrototypeGeneratedAt() != null;

        PlanRoutingResult result = PlanRoutingResult.builder()
                .appSpecId(appSpec.getId().toString())
                .projectId(projectId.toString())
                .intent(decision.intent)
                .confidence(decision.confidence)
                .branch(decision.branch)
                .matchedTemplateResults(List.of())
                .styleVariants(styleVariants)
                .prototypeGenerated(prototypeGenerated)
                .prototypeUrl(appSpec.getFrontendPrototypeUrl())
                .selectedStyleId(
                        StringUtils.hasText(appSpec.getSelectedStyle()) ? appSpec.getSelectedStyle() : selectedStyleId)
                .nextAction(decision.branch == RoutingBranch.DESIGN ? "请选择您喜欢的设计风格" : "请提供参考网站或选择模板")
                .requiresUserConfirmation(true)
                // V2.0新增：技术栈相关字段
                .techStackType(techStack.name())
                .techStackCode(techStack.getCode())
                .techStackDescription(techStack.getDescription())
                .metadata(Map.of(
                        "tenantId", tenantId.toString(),
                        "userId", userId.toString(),
                        "projectId", projectId.toString(),
                        "techStackType", techStack.name(),
                        "techStackCode", techStack.getCode()))
                .build();

        return Result.success(result);
    }

    /**
     * 更新 AppSpec 的需求描述（原型确认前的 Chat 持续修改）
     *
     * 设计意图：
     * - 该接口只更新 AppSpec.specContent.userRequirement，不改变路由分支/风格选择/原型状态；
     * - 用于“原型确认前”用户通过 Chat 反复修改需求时，确保后续 G3 能读取到最新需求。
     */
    @PostMapping("/{appSpecId}/update-requirement")
    public Result<Map<String, Object>> updateRequirement(
            @PathVariable String appSpecId,
            @Valid @RequestBody UpdateRequirementRequest request) {
        try {
            UUID id = UUID.fromString(appSpecId);
            AppSpecEntity appSpec = appSpecService.getById(id);
            if (appSpec == null) {
                return Result.error("404", "AppSpec不存在");
            }

            String requirement = request.getUserRequirement().trim();
            Map<String, Object> nextSpecContent = appSpec.getSpecContent() != null
                    ? new HashMap<>(appSpec.getSpecContent())
                    : new HashMap<>();
            nextSpecContent.put("userRequirement", requirement);
            nextSpecContent.putIfAbsent("stage", "planning");
            appSpec.setSpecContent(nextSpecContent);

            // Re-analyze intent using NLRequirementAnalyzer
            try {
                Map<String, Object> intentResult = nlRequirementAnalyzer.analyzeIntent(requirement, id);
                if (intentResult != null && !intentResult.isEmpty()) {
                    String intent = (String) intentResult.get("intent");
                    Object confidenceObj = intentResult.get("confidence");
                    Double confidence = confidenceObj instanceof Number ? ((Number) confidenceObj).doubleValue() : 0.8;

                    if (intent != null) {
                        appSpec.setIntentType(intent);
                        appSpec.setConfidenceScore(BigDecimal.valueOf(confidence));

                        // Update metadata with reasoning
                        Map<String, Object> meta = appSpec.getMetadata() != null
                                ? new HashMap<>(appSpec.getMetadata())
                                : new HashMap<>();
                        meta.put("intent", intent);
                        meta.put("confidence", confidence);
                        meta.put("intentReasoning", intentResult.getOrDefault("reasoning", ""));
                        meta.put("intentKeywords", intentResult.getOrDefault("keywords", List.of()));
                        appSpec.setMetadata(meta);
                    }
                }
            } catch (Exception ex) {
                log.warn("Failed to re-analyze intent for appSpecId={}", appSpecId, ex);
                // Fallback to simple logic if AI fails
                RoutingDecision decision = decideRouting(requirement);
                appSpec.setIntentType(decision.intent.name());
            }

            Map<String, Object> metadata = appSpec.getMetadata() != null
                    ? new HashMap<>(appSpec.getMetadata())
                    : new HashMap<>();
            metadata.put("requirementUpdatedAt", Instant.now().toString());
            appSpec.setMetadata(metadata);
            appSpecService.updateById(appSpec);

            return Result.success(Map.of(
                    "success", true,
                    "message", "需求已更新，意图已重新分析",
                    "appSpecId", appSpecId,
                    "updatedAt", Instant.now().toString()));
        } catch (IllegalArgumentException e) {
            return Result.error("400", "无效的AppSpec ID");
        } catch (Exception e) {
            log.error("更新需求失败: appSpecId={}", appSpecId, e);
            return Result.error("500", "更新需求失败: " + e.getMessage());
        }
    }

    /**
     * 更新需求请求体
     */
    @Data
    public static class UpdateRequirementRequest {
        @jakarta.validation.constraints.NotBlank(message = "用户需求不能为空")
        private String userRequirement;
    }

    /**
     * 选择模板请求体
     */
    @Data
    public static class SelectTemplateRequest {
        @jakarta.validation.constraints.NotBlank(message = "模板ID不能为空")
        private String templateId;
    }

    /**
     * 选择行业模板（Blueprint 模式）
     *
     * 说明：
     * - 当前为最小可用实现：写入 selectedTemplateId + metadata.blueprintModeEnabled
     * - 返回结构与 PlanRoutingResult 对齐，确保前端可继续走 Blueprint 流程
     */
    @PostMapping("/{appSpecId}/select-template")
    public Result<PlanRoutingResult> selectTemplate(
            @PathVariable String appSpecId,
            @Valid @RequestBody SelectTemplateRequest request) {
        try {
            UUID id = UUID.fromString(appSpecId);
            AppSpecEntity appSpec = appSpecService.getById(id);
            if (appSpec == null) {
                return Result.error("404", "AppSpec不存在");
            }

            UUID templateId = UUID.fromString(request.getTemplateId());
            appSpec.setSelectedTemplateId(templateId);

            Map<String, Object> metadata = appSpec.getMetadata() != null
                    ? new HashMap<>(appSpec.getMetadata())
                    : new HashMap<>();
            metadata.put("blueprintModeEnabled", true);
            metadata.put("selectedTemplateId", templateId.toString());
            metadata.put("blueprintSelectedAt", Instant.now().toString());
            appSpec.setMetadata(metadata);

            RoutingBranch branch = resolveBranch(appSpec.getIntentType());

            if (branch == RoutingBranch.DESIGN && !StringUtils.hasText(appSpec.getSelectedStyle())) {
                appSpec.setSelectedStyle(DesignStyle.MODERN_MINIMAL.getCode());
            }

            appSpecService.updateById(appSpec);

            List<StyleVariant> styleVariants = branch == RoutingBranch.DESIGN
                    ? buildAllStyleVariants()
                    : List.of();
            boolean prototypeGenerated = StringUtils.hasText(appSpec.getFrontendPrototypeUrl())
                    || appSpec.getPrototypeGeneratedAt() != null;

            PlanRoutingResult result = PlanRoutingResult.builder()
                    .appSpecId(appSpec.getId().toString())
                    .intent(resolveIntent(appSpec.getIntentType()))
                    .confidence(appSpec.getConfidenceScore() != null ? appSpec.getConfidenceScore().doubleValue() : 0.0)
                    .branch(branch)
                    .matchedTemplateResults(List.of())
                    .styleVariants(styleVariants)
                    .prototypeGenerated(prototypeGenerated)
                    .prototypeUrl(appSpec.getFrontendPrototypeUrl())
                    .selectedStyleId(appSpec.getSelectedStyle())
                    .nextAction("Blueprint 模式已启用，请继续确认设计")
                    .requiresUserConfirmation(true)
                    .metadata(metadata)
                    .build();

            return Result.success(result);
        } catch (IllegalArgumentException e) {
            return Result.error("400", "无效的模板ID");
        } catch (Exception e) {
            log.error("选择模板失败: appSpecId={}", appSpecId, e);
            return Result.error("500", "选择模板失败: " + e.getMessage());
        }
    }

    /**
     * 选择设计风格（更新 AppSpec 的选中风格）。
     *
     * 说明：
     * - 作为 /v2/plan-routing/{appSpecId}/select-style 的最小可用实现；
     * - 写入 selectedStyle 与 metadata，返回 PlanRoutingResult；
     * - 若 AppSpec 不存在则返回 404，确保前端可识别错误类型。
     *
     * @param appSpecId AppSpec ID
     * @param styleId   风格标识（code 或 identifier）
     * @return 更新后的路由结果
     */
    @PostMapping("/{appSpecId}/select-style")
    public Result<PlanRoutingResult> selectStyle(
            @PathVariable String appSpecId,
            @RequestParam("styleId") String styleId) {
        if (!StringUtils.hasText(styleId)) {
            return Result.error("400", "styleId不能为空");
        }

        try {
            UUID id = UUID.fromString(appSpecId);
            AppSpecEntity appSpec = appSpecService.getById(id);
            if (appSpec == null) {
                return Result.error("404", "AppSpec不存在");
            }

            DesignStyle style = DesignStyle.fromCode(styleId);
            if (style == null) {
                style = DesignStyle.fromIdentifier(styleId);
            }
            if (style == null) {
                return Result.error("400", "无效的styleId");
            }

            appSpec.setSelectedStyle(style.getCode());

            Map<String, Object> metadata = appSpec.getMetadata() != null
                    ? new HashMap<>(appSpec.getMetadata())
                    : new HashMap<>();
            metadata.put("selectedStyleId", style.getCode());
            metadata.put("selectedStyleIdentifier", style.getIdentifier());
            metadata.put("styleSelectedAt", Instant.now().toString());
            appSpec.setMetadata(metadata);

            RoutingBranch branch = resolveBranch(appSpec.getIntentType());
            appSpecService.updateById(appSpec);

            List<StyleVariant> styleVariants = branch == RoutingBranch.DESIGN
                    ? buildAllStyleVariants()
                    : List.of();
            boolean prototypeGenerated = StringUtils.hasText(appSpec.getFrontendPrototypeUrl())
                    || appSpec.getPrototypeGeneratedAt() != null;

            PlanRoutingResult result = PlanRoutingResult.builder()
                    .appSpecId(appSpec.getId().toString())
                    .intent(resolveIntent(appSpec.getIntentType()))
                    .confidence(appSpec.getConfidenceScore() != null ? appSpec.getConfidenceScore().doubleValue() : 0.0)
                    .branch(branch)
                    .matchedTemplateResults(List.of())
                    .styleVariants(styleVariants)
                    .prototypeGenerated(prototypeGenerated)
                    .prototypeUrl(appSpec.getFrontendPrototypeUrl())
                    .selectedStyleId(appSpec.getSelectedStyle())
                    .nextAction("设计风格已选择，请继续确认设计")
                    .requiresUserConfirmation(true)
                    .metadata(metadata)
                    .build();

            return Result.success(result);
        } catch (IllegalArgumentException e) {
            return Result.error("400", "无效的AppSpec ID");
        } catch (Exception e) {
            log.error("选择风格失败: appSpecId={}", appSpecId, e);
            return Result.error("500", "选择风格失败: " + e.getMessage());
        }
    }

    /**
     * 确认设计（用户确认原型后，进入代码生成阶段）
     * 此接口会检查用户余额并扣费
     *
     * @param appSpecId AppSpec ID
     * @return 确认结果
     */
    @PostMapping("/{appSpecId}/confirm-design")
    public Result<DesignConfirmResult> confirmDesign(@PathVariable String appSpecId) {
        log.info("确认设计: appSpecId={}", appSpecId);

        // 获取当前用户ID
        UUID userId = getLoginUserIdOrNull();
        if (userId == null) {
            return Result.error("401", "请先登录");
        }

        // 检查余额
        if (!billingService.hasCredits(userId, 1)) {
            log.warn("用户 {} 余额不足，无法确认设计", userId);
            return Result.error("402", "生成次数不足，请先购买套餐");
        }

        // 扣除余额
        boolean consumed = billingService.consumeCredits(
                userId,
                1,
                appSpecId,
                "确认设计 - " + appSpecId);

        if (!consumed) {
            return Result.error("402", "扣费失败，请重试");
        }

        // 更新 AppSpec 设计确认状态
        try {
            UUID appSpecUuid = UUID.fromString(appSpecId);
            AppSpecEntity appSpec = appSpecService.getById(appSpecUuid);
            if (appSpec != null) {
                log.info("更新设计确认状态前: appSpecId={}, 当前design_confirmed={}",
                    appSpecId, appSpec.getDesignConfirmed());

                appSpec.setDesignConfirmed(true);
                appSpec.setDesignConfirmedAt(Instant.now());
                appSpec.setUpdatedAt(Instant.now()); // 确保更新时间也被设置

                boolean updateSuccess = appSpecService.updateById(appSpec);

                if (updateSuccess) {
                    log.info("设计确认状态更新成功: appSpecId={}", appSpecId);
                } else {
                    log.error("设计确认状态更新失败（updateById返回false）: appSpecId={}", appSpecId);
                    // 不再静默失败，记录为错误但继续流程（已经扣费）
                }
            } else {
                log.error("找不到AppSpec: appSpecId={}", appSpecId);
            }
        } catch (Exception e) {
            log.error("更新设计确认状态异常: appSpecId={}", appSpecId, e);
        }

        log.info("用户 {} 确认设计成功，已扣除1次生成次数", userId);

        DesignConfirmResult result = DesignConfirmResult.builder()
                .success(true)
                .message("设计确认成功")
                .appSpecId(appSpecId)
                .canProceedToExecute(true)
                .build();

        return Result.success(result);
    }

    /**
     * 执行代码生成（确认设计后进入 Execute 阶段）
     *
     * 说明：
     * - 若设计未确认，返回 1001 错误码（与前端/测试约定一致）
     * - 创建 generation_tasks 记录以支持版本快照功能
     */
    @PostMapping("/{appSpecId}/execute-code-generation")
    public Result<Map<String, Object>> executeCodeGeneration(
            @PathVariable String appSpecId,
            @RequestBody(required = false) Map<String, Object> analysisContext) {
        try {
            UUID id = UUID.fromString(appSpecId);
            AppSpecEntity appSpec = appSpecService.getById(id);
            if (appSpec == null) {
                return Result.error("404", "AppSpec不存在");
            }

            Boolean confirmed = appSpec.getDesignConfirmed();
            if (confirmed == null || !confirmed) {
                return Result.error("1001", "设计未确认");
            }

            // 修复：验证技术栈一致性
            if (appSpec.getMetadata() != null) {
                String techStackTypeStr = (String) appSpec.getMetadata().get("techStackType");
                if (techStackTypeStr != null) {
                    try {
                        TechStackType expectedTechStack = TechStackType.valueOf(techStackTypeStr);
                        validateTechStackConsistency(appSpec, expectedTechStack);
                    } catch (IllegalArgumentException e) {
                        log.warn("⚠️ 无效的技术栈类型: {}", techStackTypeStr);
                    }
                }
            }

            // 如有分析上下文，写回 AppSpec 以便 G3 透传使用
            if (analysisContext != null && !analysisContext.isEmpty()) {
                Map<String, Object> specContent = appSpec.getSpecContent() != null
                        ? new HashMap<>(appSpec.getSpecContent())
                        : new HashMap<>();
                specContent.put("analysisContext", analysisContext);
                appSpec.setSpecContent(specContent);
                appSpecService.updateById(appSpec);
            }

            // 创建 generation_tasks 记录
            GenerationTaskEntity task = new GenerationTaskEntity();
            task.setId(UUID.randomUUID());
            task.setTenantId(appSpec.getTenantId() != null ? appSpec.getTenantId() : DEFAULT_TENANT_ID);
            task.setUserId(appSpec.getCreatedByUserId());
            task.setTaskName("代码生成任务 - " + appSpecId.substring(0, 8));
            task.setUserRequirement(appSpec.getSpecContent() != null
                    ? (String) appSpec.getSpecContent().getOrDefault("userRequirement", "")
                    : "");
            task.setStatus(GenerationTaskEntity.Status.GENERATING.getValue());
            task.setCurrentAgent(GenerationTaskEntity.AgentType.GENERATE.getValue());
            task.setProgress(10);
            task.setAppSpecId(id);
            task.setAppSpecContent(appSpec.getSpecContent());
            task.setStartedAt(Instant.now());
            task.setCreatedAt(Instant.now());
            task.setUpdatedAt(Instant.now());

            int insertResult = generationTaskMapper.insert(task);
            if (insertResult > 0) {
                log.info("创建 generation_task 成功: taskId={}, appSpecId={}", task.getId(), appSpecId);
            } else {
                log.error("创建 generation_task 失败: appSpecId={}", appSpecId);
            }

            UUID jobId;
            try {
                jobId = g3OrchestratorService.submitJob(
                        task.getUserRequirement(),
                        task.getUserId(),
                        task.getTenantId(),
                        id,
                        appSpec.getSelectedTemplateId(),
                        null);
            } catch (Exception e) {
                task.setStatus(GenerationTaskEntity.Status.FAILED.getValue());
                task.setErrorMessage("G3任务提交失败: " + e.getMessage());
                task.setUpdatedAt(Instant.now());
                generationTaskMapper.updateById(task);
                log.error("触发G3任务失败: appSpecId={}", appSpecId, e);
                return Result.error("500", "执行代码生成失败: " + e.getMessage());
            }

            // 记录 jobId 到任务与 AppSpec 元数据
            Map<String, Object> taskMetadata = task.getMetadata() != null
                    ? new HashMap<>(task.getMetadata())
                    : new HashMap<>();
            taskMetadata.put("g3JobId", jobId.toString());
            task.setMetadata(taskMetadata);
            generationTaskMapper.updateById(task);

            Map<String, Object> metadata = appSpec.getMetadata() != null
                    ? new HashMap<>(appSpec.getMetadata())
                    : new HashMap<>();
            metadata.put("latestG3JobId", jobId.toString());
            metadata.put("latestG3JobSubmittedAt", Instant.now().toString());
            appSpec.setMetadata(metadata);
            appSpecService.updateById(appSpec);

            return Result.success(Map.of(
                    "success", true,
                    "message", "已进入代码生成阶段",
                    "appSpecId", appSpecId,
                    "taskId", task.getId().toString(),
                    "jobId", jobId.toString()));
        } catch (IllegalArgumentException e) {
            return Result.error("400", "无效的AppSpec ID");
        } catch (Exception e) {
            log.error("执行代码生成失败: appSpecId={}", appSpecId, e);
            return Result.error("500", "执行代码生成失败: " + e.getMessage());
        }
    }

    /**
     * 完成代码生成（前端在 SSE 代码生成完成后调用）
     *
     * 功能：
     * - 更新 generation_tasks 状态为 completed
     * - 创建版本快照 (generation_versions)
     * - 更新项目状态为 completed
     *
     * @param appSpecId AppSpec ID
     * @param request 完成请求（包含生成的文件信息）
     * @return 完成结果
     */
    @PostMapping("/{appSpecId}/complete-code-generation")
    public Result<Map<String, Object>> completeCodeGeneration(
            @PathVariable String appSpecId,
            @RequestBody(required = false) CompleteCodeGenerationRequest request) {
        log.info("完成代码生成: appSpecId={}", appSpecId);

        try {
            UUID id = UUID.fromString(appSpecId);
            AppSpecEntity appSpec = appSpecService.getById(id);
            if (appSpec == null) {
                return Result.error("404", "AppSpec不存在");
            }

            // 查找关联的 generation_task
            com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<GenerationTaskEntity> queryWrapper =
                    new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<>();
            queryWrapper.eq("app_spec_id", id).orderByDesc("created_at").last("LIMIT 1");
            GenerationTaskEntity task = generationTaskMapper.selectOne(queryWrapper);

            if (task == null) {
                log.warn("未找到关联的 generation_task，创建新任务: appSpecId={}", appSpecId);
                // 如果没有找到 task，创建一个
                task = new GenerationTaskEntity();
                task.setId(UUID.randomUUID());
                UUID resolvedTenantId = appSpec.getTenantId() != null ? appSpec.getTenantId() : DEFAULT_TENANT_ID;
                UUID resolvedUserId = appSpec.getCreatedByUserId() != null
                        ? appSpec.getCreatedByUserId()
                        : resolvedTenantId;
                task.setTenantId(resolvedTenantId);
                task.setUserId(resolvedUserId);
                task.setTaskName("代码生成任务 - " + appSpecId.substring(0, 8));
                task.setAppSpecId(id);
                task.setAppSpecContent(appSpec.getSpecContent());
                // 从 appSpec 的 specContent 中提取 userRequirement（必填字段）
                String userRequirement = "";
                if (appSpec.getSpecContent() != null) {
                    Object reqObj = appSpec.getSpecContent().get("userRequirement");
                    if (reqObj != null) {
                        userRequirement = reqObj.toString();
                    }
                }
                // 如果仍然为空，使用默认值
                if (userRequirement.isEmpty()) {
                    userRequirement = "代码生成任务 - " + appSpecId.substring(0, 8);
                }
                task.setUserRequirement(userRequirement);
                task.setStatus(GenerationTaskEntity.Status.PENDING.getValue());
                task.setStartedAt(Instant.now());
                task.setCreatedAt(Instant.now());
                task.setUpdatedAt(Instant.now());
                generationTaskMapper.insert(task);
            }

            // 更新任务状态为完成
            task.setStatus(GenerationTaskEntity.Status.COMPLETED.getValue());
            task.setProgress(100);
            task.setCompletedAt(Instant.now());
            task.setUpdatedAt(Instant.now());

            // 如果有前端原型 URL，保存到任务中
            if (appSpec.getFrontendPrototypeUrl() != null) {
                task.setPreviewUrl(appSpec.getFrontendPrototypeUrl());
            }

            generationTaskMapper.updateById(task);
            log.info("更新 generation_task 状态为完成: taskId={}", task.getId());

            // 创建版本快照
            Map<String, Object> snapshotData = new HashMap<>();
            snapshotData.put("appSpecId", appSpecId);
            snapshotData.put("userRequirement", appSpec.getSpecContent() != null
                    ? appSpec.getSpecContent().getOrDefault("userRequirement", "")
                    : "");
            snapshotData.put("selectedStyle", appSpec.getSelectedStyle());
            snapshotData.put("frontendPrototypeUrl", appSpec.getFrontendPrototypeUrl());
            snapshotData.put("techStackType", appSpec.getMetadata() != null
                    ? appSpec.getMetadata().getOrDefault("techStackType", "")
                    : "");

            // 如果请求中有文件信息，添加到快照数据
            if (request != null && request.getFiles() != null) {
                snapshotData.put("generatedFiles", request.getFiles());
                snapshotData.put("fileCount", request.getFiles().size());
            }

            try {
                versionSnapshotService.createSnapshot(
                        task.getId(),
                        task.getTenantId(),
                VersionType.CODE,
                        snapshotData);
                log.info("版本快照创建成功: taskId={}, appSpecId={}", task.getId(), appSpecId);
            } catch (Exception e) {
                log.error("创建版本快照失败: taskId={}, appSpecId={}", task.getId(), appSpecId, e);
                // 不阻塞流程，只记录错误
            }

            // 更新项目状态为 completed
            com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<ProjectEntity> projectQueryWrapper =
                    new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<>();
            projectQueryWrapper.eq("app_spec_id", id);
            ProjectEntity project = projectService.getOne(projectQueryWrapper);
            if (project != null) {
                project.setStatus("completed");
                project.setUpdatedAt(Instant.now());
                projectService.updateById(project);
                log.info("更新项目状态为 completed: projectId={}", project.getId());
            }

            return Result.success(Map.of(
                    "success", true,
                    "message", "代码生成完成",
                    "appSpecId", appSpecId,
                    "taskId", task.getId().toString(),
                    "versionCreated", true));
        } catch (IllegalArgumentException e) {
            return Result.error("400", "无效的AppSpec ID");
        } catch (Exception e) {
            log.error("完成代码生成失败: appSpecId={}", appSpecId, e);
            return Result.error("500", "完成代码生成失败: " + e.getMessage());
        }
    }

    /**
     * 完成代码生成请求
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CompleteCodeGenerationRequest {
        /** 生成的文件列表 */
        private List<Map<String, Object>> files;
        /** 沙箱 ID */
        private String sandboxId;
        /** 预览 URL */
        private String previewUrl;
    }

    /**
     * 设计确认结果
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DesignConfirmResult {
        private boolean success;
        private String message;
        private String appSpecId;
        private boolean canProceedToExecute;
    }

    /**
     * 更新原型状态请求
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UpdatePrototypeRequest {
        private String sandboxId;
        private String previewUrl;
        private String provider;
    }

    /**
     * 更新原型状态（前端生成预览后同步到后端）
     *
     * @param appSpecId AppSpec ID
     * @param request   原型信息
     * @return 更新结果
     */
    @PostMapping("/{appSpecId}/update-prototype")
    public Result<Map<String, Object>> updatePrototype(
            @PathVariable String appSpecId,
            @RequestBody UpdatePrototypeRequest request) {
        log.info("更新原型状态: appSpecId={}, sandboxId={}, previewUrl={}",
                appSpecId, request.getSandboxId(), request.getPreviewUrl());

        try {
            UUID id = UUID.fromString(appSpecId);
            AppSpecEntity appSpec = appSpecService.getById(id);

            if (appSpec == null) {
                return Result.error("404", "AppSpec不存在");
            }

            // 更新原型相关字段
            appSpec.setFrontendPrototypeUrl(request.getPreviewUrl());
            appSpec.setPrototypeGeneratedAt(Instant.now());

            // 更新metadata
            Map<String, Object> metadata = appSpec.getMetadata() != null
                    ? new HashMap<>(appSpec.getMetadata())
                    : new HashMap<>();
            metadata.put("sandboxId", request.getSandboxId());
            metadata.put("sandboxProvider", request.getProvider() != null ? request.getProvider() : "e2b");
            metadata.put("prototypeUpdatedAt", Instant.now().toString());
            appSpec.setMetadata(metadata);

            appSpecService.updateById(appSpec);

            log.info("原型状态更新成功: appSpecId={}", appSpecId);

            return Result.success(Map.of(
                    "success", true,
                    "message", "原型状态更新成功",
                    "updatedAt", Instant.now().toString()));
        } catch (IllegalArgumentException e) {
            log.error("无效的AppSpec ID: {}", appSpecId, e);
            return Result.error("400", "无效的AppSpec ID");
        } catch (Exception e) {
            log.error("更新原型状态失败: appSpecId={}", appSpecId, e);
            return Result.error("500", "更新原型状态失败: " + e.getMessage());
        }
    }

    /**
     * 自动生成原型并写回 AppSpec（用于补齐 sandboxId / frontendPrototypeUrl）
     *
     * 是什么：为缺失原型信息的 AppSpec 触发 OpenLovable 生成，并把 sandboxId/previewUrl 写回。
     * 做什么：读取 AppSpec 的 userRequirement，调用 OpenLovableService 生成原型，持久化到 AppSpec。
     * 为什么：支持“下载前端页面”等功能依赖的 sandboxId，避免用户必须手动走“确认并生成原型”才能下载。
     */
    @PostMapping("/{appSpecId}/generate-prototype")
    public Result<Map<String, Object>> generatePrototype(@PathVariable String appSpecId) {
        log.info("自动生成原型: appSpecId={}", appSpecId);

        try {
            UUID id = UUID.fromString(appSpecId);
            AppSpecEntity appSpec = appSpecService.getById(id);
            if (appSpec == null) {
                return Result.error("404", "AppSpec不存在");
            }

            Map<String, Object> metadata = appSpec.getMetadata() != null
                    ? new HashMap<>(appSpec.getMetadata())
                    : new HashMap<>();
            String existingSandboxId = metadata.get("sandboxId") instanceof String s && StringUtils.hasText(s)
                    ? s
                    : null;
            String existingProvider = metadata.get("sandboxProvider") instanceof String s && StringUtils.hasText(s)
                    ? s
                    : null;

            boolean hasExistingSandboxId = StringUtils.hasText(existingSandboxId);
            boolean sandboxHealthy = hasExistingSandboxId && openLovableService.isSandboxHealthy(existingSandboxId);

            // 原型已存在且沙箱健康：直接返回（幂等）
            if (StringUtils.hasText(appSpec.getFrontendPrototypeUrl()) && hasExistingSandboxId && sandboxHealthy) {
                return Result.success(Map.of(
                        "success", true,
                        "message", "原型已存在，无需重复生成",
                        "sandboxId", existingSandboxId,
                        "previewUrl", appSpec.getFrontendPrototypeUrl(),
                        "provider", existingProvider != null ? existingProvider : "e2b",
                        "updatedAt", Instant.now().toString()
                ));
            }

            // 沙箱不健康：先销毁再重建，避免 OpenLovable 继续复用“已存在但不响应”的沙箱导致下载/执行持续失败
            if (hasExistingSandboxId && !sandboxHealthy) {
                log.warn("检测到旧沙箱不可用，准备销毁并重建: appSpecId={}, sandboxId={}", appSpecId, existingSandboxId);
                try {
                    openLovableService.killSandbox(existingSandboxId);
                } catch (Exception e) {
                    // 销毁失败不应直接阻塞生成：后续仍尝试生成，失败时由上游返回错误
                    log.warn("销毁旧沙箱失败（将继续尝试生成新原型）: sandboxId={}, reason={}", existingSandboxId, e.getMessage());
                }

                // 清理旧值，避免前端继续误用旧 sandboxId/previewUrl
                metadata.remove("sandboxId");
                metadata.remove("sandboxProvider");
                appSpec.setMetadata(metadata);
                appSpec.setFrontendPrototypeUrl(null);
            }

            String requirement = resolveUserRequirement(appSpec);
            if (!StringUtils.hasText(requirement)) {
                return Result.error("400", "未找到用户需求，无法生成原型");
            }

            List<String> referenceUrls = extractReferenceUrls(requirement);
            OpenLovableGenerateRequest openLovableRequest = OpenLovableGenerateRequest.builder()
                    .userRequirement(requirement)
                    .referenceUrls(referenceUrls)
                    .needsCrawling(!referenceUrls.isEmpty())
                    // 快速启动：先创建沙箱并写回 sandboxId/previewUrl，代码生成在后台进行
                    // 目的：避免同步等待导致接口耗时过长，前端可在需要时再触发刷新/下载。
                    .streaming(true)
                    // 由 OpenLovable 侧选择其默认可用模型，避免本地密钥/模型不匹配
                    .aiModel(null)
                    // 原型生成可能慢于纯计划路由，放宽超时避免过早失败
                    .timeoutSeconds(180)
                    .build();

            OpenLovableGenerateResponse preview = openLovableService.generatePrototype(openLovableRequest);
            if (preview == null || !preview.isSuccessful()) {
                String message = preview != null && StringUtils.hasText(preview.getErrorMessage())
                        ? preview.getErrorMessage()
                        : "unknown";
                return Result.error("500", "原型生成失败: " + message);
            }

            applyPrototypeToAppSpec(appSpec, preview);
            appSpecService.updateById(appSpec);

            return Result.success(Map.of(
                    "success", true,
                    "message", "原型生成已启动",
                    "sandboxId", preview.getSandboxId(),
                    "previewUrl", preview.getPreviewUrl(),
                    "provider", preview.getProvider() != null ? preview.getProvider() : "e2b",
                    "updatedAt", Instant.now().toString()
            ));
        } catch (IllegalArgumentException e) {
            log.error("无效的AppSpec ID: {}", appSpecId, e);
            return Result.error("400", "无效的AppSpec ID");
        } catch (Exception e) {
            log.error("自动生成原型失败: appSpecId={}", appSpecId, e);
            return Result.error("500", "自动生成原型失败: " + e.getMessage());
        }
    }

    private static UUID coerceUuid(UUID value) {
        return value;
    }

    /**
     * 从 AppSpec 中解析用户需求
     *
     * 是什么：从 AppSpec.specContent 读取 userRequirement。
     * 做什么：兼容 specContent 缺失或类型不匹配时的空值情况。
     * 为什么：原型生成依赖需求文本，避免因字段缺失导致 500。
     */
    private String resolveUserRequirement(AppSpecEntity appSpec) {
        if (appSpec == null || appSpec.getSpecContent() == null) {
            return null;
        }
        Object value = appSpec.getSpecContent().get("userRequirement");
        if (!(value instanceof String requirement) || !StringUtils.hasText(requirement)) {
            return null;
        }
        return requirement.trim();
    }

    /**
     * 获取当前登录用户ID，支持在白名单路径中从token获取
     *
     * 由于 /v2/** 路径在 SaTokenConfig 中被排除认证，
     * StpUtil.getLoginIdAsString() 可能返回 null。
     * 此方法会尝试：
     * 1. 先从 Sa-Token 登录状态获取
     * 2. 如果失败，尝试从 Authorization header 中获取 token 并解析用户ID
     *
     * @return 用户ID，如果无法获取则返回 null
     */
    private UUID getLoginUserIdOrNull() {
        try {
            // 方式1：从 Sa-Token 登录状态获取
            String id = StpUtil.getLoginIdAsString();
            if (id != null) {
                return UUID.fromString(id);
            }
        } catch (Exception ignore) {
            // 继续尝试其他方式
        }

        try {
            // 方式2：从 Authorization header 获取 token，然后查询用户ID
            String token = StpUtil.getTokenValue();
            if (token != null && !token.isEmpty()) {
                Object loginId = StpUtil.getLoginIdByToken(token);
                if (loginId != null) {
                    log.debug("从 token 解析到用户ID: {}", loginId);
                    return UUID.fromString(loginId.toString());
                }
            }
        } catch (Exception e) {
            log.debug("从 token 获取用户ID失败: {}", e.getMessage());
        }

        return null;
    }

    private UUID getSessionTenantIdOrNull() {
        try {
            var session = StpUtil.getSession(false);
            Object tenantId = session != null ? session.get("tenantId") : null;
            return tenantId != null ? UUID.fromString(tenantId.toString()) : null;
        } catch (Exception ignore) {
            return null;
        }
    }

    /**
     * 确保默认占位用户存在
     *
     * 场景：未登录情况下创建 AppSpec，会触发 app_specs.created_by_user_id 外键约束。
     * 处理：创建与默认 tenant/user 对应的系统用户，保证路由流程可继续。
     */
    private void ensureFallbackUserExists(UUID userId, UUID tenantId) {
        if (userId == null || tenantId == null) {
            return;
        }

        try {
            if (userMapper.findByIdWithCast(userId.toString()).isPresent()) {
                return;
            }
        } catch (Exception e) {
            log.warn("检查默认用户失败，将尝试创建: userId={}", userId, e);
        }

        try {
            String suffix = userId.toString().replace("-", "").substring(0, 8);
            UserEntity user = new UserEntity();
            user.setId(userId);
            user.setTenantId(tenantId);
            user.setUsername(DEFAULT_USER_PREFIX + "_" + suffix);
            user.setEmail(DEFAULT_USER_PREFIX + "_" + suffix + "@local.invalid");
            user.setPasswordHash(BCrypt.hashpw(UUID.randomUUID().toString()));
            user.setRole(UserEntity.Role.USER.getValue());
            user.setStatus(UserEntity.Status.ACTIVE.getValue());
            user.setCreatedAt(Instant.now());
            user.setUpdatedAt(Instant.now());
            userMapper.insert(user);
            log.info("已创建默认占位用户: userId={}", userId);
        } catch (Exception e) {
            log.warn("创建默认占位用户失败: userId={}", userId, e);
        }
    }

    /**
     * 最小可用的路由决策（规则 + 兜底）
     *
     * 规则：
     * - 含 URL 或明确“参考/仿照/克隆”等关键词 → CLONE / HYBRID
     * - 同时出现“但/但是/修改/定制/增加”等 → HYBRID
     * - 否则 → DESIGN
     */
    private RoutingDecision decideRouting(String requirement) {
        String lower = requirement.toLowerCase();
        boolean hasUrl = URL_PATTERN.matcher(requirement).find();
        boolean hasCloneKeyword = hasUrl ||
                lower.contains("clone") ||
                lower.contains("copy") ||
                requirement.contains("仿") ||
                requirement.contains("参考") ||
                requirement.contains("类似") ||
                requirement.contains("克隆") ||
                requirement.contains("爬取");
        boolean hasCustomizeKeyword = requirement.contains("但") ||
                requirement.contains("但是") ||
                requirement.contains("修改") ||
                requirement.contains("定制") ||
                requirement.contains("增加") ||
                requirement.contains("在") && requirement.contains("基础上");

        if (hasCloneKeyword && hasCustomizeKeyword) {
            return new RoutingDecision(RequirementIntent.HYBRID_CLONE_AND_CUSTOMIZE, RoutingBranch.HYBRID, 0.85);
        }
        if (hasCloneKeyword) {
            return new RoutingDecision(RequirementIntent.CLONE_EXISTING_WEBSITE, RoutingBranch.CLONE,
                    hasUrl ? 0.92 : 0.80);
        }
        return new RoutingDecision(RequirementIntent.DESIGN_FROM_SCRATCH, RoutingBranch.DESIGN, 0.80);
    }

    private List<StyleVariant> buildAllStyleVariants() {
        return Arrays.stream(DesignStyle.values())
                .map(style -> StyleVariant.builder()
                        .styleId(style.getIdentifier())
                        .styleName(style.getDisplayName())
                        .styleCode(style.getCode())
                        .build())
                .toList();
    }

    /**
     * 根据 intentType 字符串推导路由分支
     *
     * 说明：
     * - 若 intentType 为空，默认 DESIGN
     * - 与 RequirementIntent 枚举保持一致
     */
    private RoutingBranch resolveBranch(String intentType) {
        if ("CLONE_EXISTING_WEBSITE".equals(intentType)) {
            return RoutingBranch.CLONE;
        }
        if ("HYBRID_CLONE_AND_CUSTOMIZE".equals(intentType)) {
            return RoutingBranch.HYBRID;
        }
        return RoutingBranch.DESIGN;
    }

    /**
     * 根据 intentType 字符串恢复 RequirementIntent
     *
     * 说明：
     * - 解析失败时默认 DESIGN_FROM_SCRATCH，避免空值影响前端流程
     */
    private RequirementIntent resolveIntent(String intentType) {
        if ("CLONE_EXISTING_WEBSITE".equals(intentType)) {
            return RequirementIntent.CLONE_EXISTING_WEBSITE;
        }
        if ("HYBRID_CLONE_AND_CUSTOMIZE".equals(intentType)) {
            return RequirementIntent.HYBRID_CLONE_AND_CUSTOMIZE;
        }
        return RequirementIntent.DESIGN_FROM_SCRATCH;
    }

    /**
     * 从用户需求中提取参考链接（支持含协议 URL 与裸域名）
     *
     * 设计意图：
     * - CLONE/HYBRID 场景需要尽快拿到参考站点，触发 OpenLovable 快速原型
     * - 兼容“airbnb.com”这种无协议写法，自动补全 https://
     */
    private List<String> extractReferenceUrls(String requirement) {
        if (!StringUtils.hasText(requirement)) {
            return List.of();
        }

        String content = requirement.trim();
        java.util.LinkedHashSet<String> urls = new java.util.LinkedHashSet<>();

        var urlMatcher = URL_PATTERN.matcher(content);
        while (urlMatcher.find()) {
            String candidate = urlMatcher.group();
            while (!candidate.isEmpty() && ".,;!?))]".indexOf(candidate.charAt(candidate.length() - 1)) >= 0) {
                candidate = candidate.substring(0, candidate.length() - 1);
            }
            if (!candidate.isEmpty()) {
                urls.add(candidate);
            }
        }

        var domainMatcher = DOMAIN_PATTERN.matcher(content);
        while (domainMatcher.find()) {
            String domain = domainMatcher.group(1);
            if (!StringUtils.hasText(domain)) {
                continue;
            }
            boolean exists = urls.stream().anyMatch(url -> url.contains(domain));
            if (!exists) {
                urls.add("https://" + domain);
            }
        }

        return new java.util.ArrayList<>(urls);
    }

    /**
     * 将 OpenLovable 原型信息写回 AppSpec
     *
     * 说明：
     * - 统一补齐 prototypeUrl + 生成时间 + sandbox 元数据
     * - 便于后续 UI 直接展示原型并允许确认设计
     */
    private void applyPrototypeToAppSpec(AppSpecEntity appSpec, OpenLovableGenerateResponse preview) {
        appSpec.setFrontendPrototypeUrl(preview.getPreviewUrl());
        appSpec.setPrototypeGeneratedAt(Instant.now());

        Map<String, Object> metadata = appSpec.getMetadata() != null
                ? new HashMap<>(appSpec.getMetadata())
                : new HashMap<>();
        metadata.put("sandboxId", preview.getSandboxId());
        metadata.put("sandboxProvider", preview.getProvider() != null ? preview.getProvider() : "e2b");
        metadata.put("prototypeUpdatedAt", Instant.now().toString());
        appSpec.setMetadata(metadata);
    }

    /**
     * 根据用户预选和需求内容确定技术栈类型（V2.0新增）
     *
     * 优先级：
     * 1. 用户显式指定的 techStackHint（前端首页选择的技术栈）
     * 2. 用户预选的 complexityHint（前端首页选择的复杂度分类）
     * 3. 基于需求内容的智能推断
     *
     * @param complexityHint 用户预选的复杂度分类（SIMPLE/MEDIUM/COMPLEX/NEEDS_CONFIRMATION）
     * @param techStackHint  用户预选的技术栈提示（H5+WebView/React+Supabase/React+SpringBoot/Kuikly）
     * @param requirement    用户需求描述
     * @return 确定的技术栈类型
     */
    private TechStackType determineTechStack(String complexityHint, String techStackHint, String requirement) {
        TechStackType explicitTechStack = inferExplicitTechStackFromRequirement(requirement);
        if (explicitTechStack != null) {
            log.info("✅ 命中显式技术栈关键词: {}", explicitTechStack);
            return explicitTechStack;
        }

        // 优先级1：用户显式指定的技术栈
        if (StringUtils.hasText(techStackHint)) {
            TechStackType fromCode = TechStackType.fromCode(techStackHint);
            if (fromCode != null) {
                log.info("✅ 使用用户指定的技术栈: techStackHint={} -> {}", techStackHint, fromCode);
                return fromCode;
            } else {
                log.warn("⚠️ 技术栈匹配失败: techStackHint={}, 将尝试其他方式", techStackHint);
            }
        }

        // 优先级2：用户预选的复杂度分类
        if (StringUtils.hasText(complexityHint)) {
            TechStackType fromComplexity = TechStackType.fromComplexityHint(complexityHint);
            if (fromComplexity != null) {
                log.info("✅ 根据复杂度分类确定技术栈: complexityHint={} -> {}", complexityHint, fromComplexity);
                return fromComplexity;
            }
        }

        // 优先级3：基于需求内容的智能推断
        TechStackType inferred = inferTechStackFromRequirement(requirement);
        log.info("✅ 基于需求内容推断技术栈: {}", inferred);
        return inferred;
    }

    /**
     * 基于需求文本中的显式技术栈关键词做快速判断
     *
     * 是什么：识别明确提到的技术栈名称（如 Spring Boot、Supabase）。
     * 做什么：当用户需求已明确技术栈时，优先覆盖前端提示。
     * 为什么：避免“需求明确 + 提示误选”导致的技术栈误判。
     *
     * @param requirement 用户需求描述
     * @return 显式命中的技术栈类型，未命中返回 null
     */
    private TechStackType inferExplicitTechStackFromRequirement(String requirement) {
        if (!StringUtils.hasText(requirement)) {
            return null;
        }

        String lower = requirement.toLowerCase();

        if (lower.contains("spring boot") || lower.contains("springboot") ||
            lower.contains("spring-boot") || lower.contains("jeecg") ||
            lower.contains("jeecgboot")) {
            return TechStackType.REACT_SPRING_BOOT;
        }

        if (lower.contains("supabase") || lower.contains("superbase")) {
            return TechStackType.REACT_SUPABASE;
        }

        if (lower.contains("h5") || lower.contains("webview") ||
            lower.contains("web-view") || lower.contains("web view")) {
            return TechStackType.H5_WEBVIEW;
        }

        if (lower.contains("kuikly")) {
            return TechStackType.KUIKLY;
        }

        return null;
    }

    /**
     * 验证技术栈一致性（V2.0新增）
     *
     * 检查 AppSpec 中保存的技术栈是否与预期一致，如果不一致则记录警告并使用用户原始选择
     *
     * @param appSpec         AppSpec实体
     * @param expectedTechStack 预期的技术栈类型
     */
    private void validateTechStackConsistency(AppSpecEntity appSpec, TechStackType expectedTechStack) {
        if (appSpec == null || appSpec.getMetadata() == null || expectedTechStack == null) {
            return;
        }

        String actualTechStackType = (String) appSpec.getMetadata().get("techStackType");
        if (actualTechStackType != null && !expectedTechStack.name().equals(actualTechStackType)) {
            log.warn("⚠️ 技术栈不一致检测: appSpecId={}, expected={}, actual={}",
                    appSpec.getId(), expectedTechStack.name(), actualTechStackType);
            log.warn("⚠️ 将使用用户原始选择: {}", expectedTechStack.name());

            // 修正为用户原始选择
            Map<String, Object> metadata = new HashMap<>(appSpec.getMetadata());
            metadata.put("techStackType", expectedTechStack.name());
            metadata.put("techStackCode", expectedTechStack.getCode());
            metadata.put("techStackDescription", expectedTechStack.getDescription());
            metadata.put("techStackCorrectedAt", Instant.now().toString());
            appSpec.setMetadata(metadata);
            appSpecService.updateById(appSpec);

            log.info("✅ 技术栈已修正: appSpecId={}, techStackType={}", appSpec.getId(), expectedTechStack.name());
        } else {
            log.debug("✅ 技术栈一致性验证通过: appSpecId={}, techStackType={}",
                    appSpec.getId(), expectedTechStack.name());
        }
    }

    /**
     * 基于需求内容智能推断技术栈（V2.0新增）
     *
     * 规则：
     * - 包含"相机/GPS/蓝牙/NFC/指纹"等原生能力关键词 → KUIKLY
     * - 包含"高并发/微服务/企业级/复杂业务"等关键词 → REACT_SPRING_BOOT
     * - 包含"H5/WebView"等关键词 → H5_WEBVIEW
     * - 包含"静态/展示/落地页/宣传"等关键词 → H5_WEBVIEW
     * - 默认 → REACT_SUPABASE（BaaS模式，适合大部分Web应用）
     *
     * @param requirement 用户需求描述
     * @return 推断的技术栈类型
     */
    private TechStackType inferTechStackFromRequirement(String requirement) {
        if (!StringUtils.hasText(requirement)) {
            return TechStackType.REACT_SUPABASE;
        }

        String lower = requirement.toLowerCase();

        // 原生能力关键词 → KUIKLY
        if (lower.contains("相机") || lower.contains("摄像头") ||
            lower.contains("gps") || lower.contains("定位") ||
            lower.contains("蓝牙") || lower.contains("bluetooth") ||
            lower.contains("nfc") || lower.contains("指纹") ||
            lower.contains("人脸识别") || lower.contains("原生") ||
            lower.contains("离线") || lower.contains("推送通知")) {
            log.info("需求包含原生能力关键词，推断为 KUIKLY: {}", requirement.substring(0, Math.min(50, requirement.length())));
            return TechStackType.KUIKLY;
        }

        // 企业级/复杂应用关键词 → REACT_SPRING_BOOT
        if (lower.contains("高并发") || lower.contains("微服务") ||
            lower.contains("企业级") || lower.contains("复杂业务") ||
            lower.contains("erp") || lower.contains("crm") ||
            lower.contains("后台管理") || lower.contains("权限管理") ||
            lower.contains("spring boot") || lower.contains("springboot") ||
            lower.contains("spring-boot") || lower.contains("jeecg") ||
            lower.contains("jeecgboot") ||
            lower.contains("多租户") || lower.contains("工作流") ||
            lower.contains("城市大脑") || lower.contains("指挥中枢") ||
            lower.contains("指挥中心") || lower.contains("多智能体") ||
            lower.contains("态势") || lower.contains("调度")) {
            log.info("需求包含企业级关键词，推断为 REACT_SPRING_BOOT: {}", requirement.substring(0, Math.min(50, requirement.length())));
            return TechStackType.REACT_SPRING_BOOT;
        }

        // H5/WebView 关键词 → H5_WEBVIEW
        if (lower.contains("h5") || lower.contains("webview") ||
            lower.contains("web-view") || lower.contains("web view")) {
            log.info("需求包含H5/WebView关键词，推断为 H5_WEBVIEW: {}", requirement.substring(0, Math.min(50, requirement.length())));
            return TechStackType.H5_WEBVIEW;
        }

        boolean isPosterGenerator =
            (lower.contains("海报") || lower.contains("poster")) &&
            (lower.contains("生成") || lower.contains("一键生成") ||
                lower.contains("生成器") || lower.contains("模板") ||
                lower.contains("下载"));
        if (isPosterGenerator) {
            log.info("需求包含海报生成关键词，推断为 REACT_SPRING_BOOT: {}", requirement.substring(0, Math.min(50, requirement.length())));
            return TechStackType.REACT_SPRING_BOOT;
        }

        // 简单静态页面关键词 → H5_WEBVIEW
        if (lower.contains("静态") || lower.contains("纯展示") ||
            lower.contains("落地页") || lower.contains("宣传页") ||
            lower.contains("产品介绍") || lower.contains("简单网页")) {
            log.info("需求包含静态页面关键词，推断为 H5_WEBVIEW: {}", requirement.substring(0, Math.min(50, requirement.length())));
            return TechStackType.H5_WEBVIEW;
        }

        // 默认：BaaS模式，适合博客、商城、Dashboard等常见Web应用
        log.info("默认使用 REACT_SUPABASE (BaaS模式): {}", requirement.substring(0, Math.min(50, requirement.length())));
        return TechStackType.REACT_SUPABASE;
    }

    /**
     * 路由分支枚举（与 shared/types/plan-routing.types.ts 保持一致）
     */
    public enum RoutingBranch {
        CLONE,
        DESIGN,
        HYBRID
    }

    @Data
    @AllArgsConstructor
    private static class RoutingDecision {
        private RequirementIntent intent;
        private RoutingBranch branch;
        private double confidence;
    }

    /**
     * 路由结果（与 shared/types/plan-routing.types.ts:1 对齐）
     *
     * V2.0新增字段：
     * - techStackType: 技术栈类型（H5_WEBVIEW / REACT_SUPABASE / REACT_SPRING_BOOT / KUIKLY）
     * - techStackCode: 技术栈代码（用于前端展示）
     * - techStackDescription: 技术栈描述
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PlanRoutingResult {
        private String appSpecId;
        private String projectId;
        private RequirementIntent intent;
        private double confidence;
        private RoutingBranch branch;
        private List<Object> matchedTemplateResults;
        private List<StyleVariant> styleVariants;
        private boolean prototypeGenerated;
        private String prototypeUrl;
        private String selectedStyleId;
        private String nextAction;
        private boolean requiresUserConfirmation;
        private Map<String, Object> metadata;

        /**
         * 技术栈类型（V2.0新增）
         * 取值：H5_WEBVIEW / REACT_SUPABASE / REACT_SPRING_BOOT / KUIKLY
         */
        private String techStackType;

        /**
         * 技术栈代码（V2.0新增）
         * 取值：H5+WebView / React+Supabase / React+SpringBoot / Kuikly
         */
        private String techStackCode;

        /**
         * 技术栈描述（V2.0新增）
         * 中文描述，用于前端展示
         */
        private String techStackDescription;
    }

    /**
     * 风格变体（PlanRoutingResult 的子结构）
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class StyleVariant {
        private String styleId;
        private String styleName;
        private String styleCode;
        private String previewHtml;
        private String thumbnailUrl;
        private String colorTheme;
    }
}
