package com.ingenio.backend.controller;

import cn.dev33.satoken.secure.BCrypt;
import cn.dev33.satoken.stp.StpUtil;
import com.ingenio.backend.agent.dto.RequirementIntent;
import com.ingenio.backend.common.response.Result;
import com.ingenio.backend.dto.request.PlanRoutingRequest;
import com.ingenio.backend.dto.request.OpenLovableGenerateRequest;
import com.ingenio.backend.dto.response.OpenLovableGenerateResponse;
import com.ingenio.backend.entity.AppSpecEntity;
import com.ingenio.backend.entity.ProjectEntity;
import com.ingenio.backend.entity.UserEntity;
import com.ingenio.backend.enums.DesignStyle;
import com.ingenio.backend.mapper.UserMapper;
import com.ingenio.backend.service.AppSpecService;
import com.ingenio.backend.service.BillingService;
import com.ingenio.backend.service.OpenLovableService;
import com.ingenio.backend.service.ProjectService;
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
    private final com.ingenio.backend.service.NLRequirementAnalyzer nlRequirementAnalyzer;

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
                            // 克隆/爬取链路可能明显慢于“纯生成”，默认放宽超时，避免过早返回空原型
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
                .metadata(Map.of(
                        "tenantId", tenantId.toString(),
                        "userId", userId.toString(),
                        "projectId", projectId.toString()))
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
                Map<String, Object> intentResult = nlRequirementAnalyzer.analyzeIntent(requirement);
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
                appSpec.setDesignConfirmed(true);
                appSpec.setDesignConfirmedAt(Instant.now());
                appSpecService.updateById(appSpec);
            }
        } catch (Exception e) {
            log.warn("更新设计确认状态失败: appSpecId={}", appSpecId, e);
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
     * - 当前仅提供状态校验与占位响应，后续可对接真实 G3 执行
     */
    @PostMapping("/{appSpecId}/execute-code-generation")
    public Result<Map<String, Object>> executeCodeGeneration(@PathVariable String appSpecId) {
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

            return Result.success(Map.of(
                    "success", true,
                    "message", "已进入代码生成阶段",
                    "appSpecId", appSpecId));
        } catch (IllegalArgumentException e) {
            return Result.error("400", "无效的AppSpec ID");
        } catch (Exception e) {
            log.error("执行代码生成失败: appSpecId={}", appSpecId, e);
            return Result.error("500", "执行代码生成失败: " + e.getMessage());
        }
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

    private static UUID coerceUuid(UUID value) {
        return value;
    }

    private UUID getLoginUserIdOrNull() {
        try {
            String id = StpUtil.getLoginIdAsString();
            return id != null ? UUID.fromString(id) : null;
        } catch (Exception ignore) {
            return null;
        }
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
