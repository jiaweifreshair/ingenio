package com.ingenio.backend.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.ingenio.backend.agent.IntentClassifier;
import com.ingenio.backend.agent.dto.IntentClassificationResult;
import com.ingenio.backend.agent.dto.RequirementIntent;
import com.ingenio.backend.common.exception.BusinessException;
import com.ingenio.backend.common.exception.ErrorCode;
import com.ingenio.backend.common.response.Result;
import com.ingenio.backend.dto.request.OpenLovableGenerateRequest;
import com.ingenio.backend.dto.request.PrototypeGenerateRequest;
import com.ingenio.backend.dto.response.OpenLovableGenerateResponse;
import com.ingenio.backend.dto.response.PrototypePreviewResponse;
import com.ingenio.backend.entity.AppSpecEntity;
import com.ingenio.backend.enums.DesignStyle;
import com.ingenio.backend.mapper.AppSpecMapper;
import com.ingenio.backend.service.OpenLovableService;
import com.ingenio.backend.service.PlanRoutingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

/**
 * 原型预览控制器
 * 将意图识别结果、设计风格选择与OpenLovable沙箱串联为可调用API
 */
@Slf4j
@RestController
@RequestMapping("/v1/prototype")
@RequiredArgsConstructor
@Tag(name = "原型预览", description = "OpenLovable-CN沙箱预览生成接口")
public class PrototypeController {

    private final IntentClassifier intentClassifier;
    private final OpenLovableService openLovableService;
    private final AppSpecMapper appSpecMapper;
    private final PlanRoutingService planRoutingService;

    /**
     * 根据用户需求和设计风格生成OpenLovable沙箱预览
     */
    @PostMapping("/generate")
    @Operation(summary = "生成原型预览", description = "根据意图识别与SuperDesign风格生成E2B沙箱预览")
    public Result<PrototypePreviewResponse> generatePrototype(
            @Valid @RequestBody PrototypeGenerateRequest request
    ) {
        long startTime = System.currentTimeMillis();
        List<String> warnings = new ArrayList<>();

        try {
            IntentClassificationResult classifierResult = resolveIntentIfNeeded(request, warnings);
            RequirementIntent intent = determineIntent(request, classifierResult, warnings);
            List<String> referenceUrls = mergeReferenceUrls(request, classifierResult);
            boolean needsCrawling = intent != null && intent.needsWebCrawling() || !referenceUrls.isEmpty();
            String customization = firstNonBlank(
                    request.getCustomizationRequirement(),
                    classifierResult != null ? classifierResult.getCustomizationRequirement() : null
            );

            String prompt = buildPrompt(request, intent);

            OpenLovableGenerateRequest openLovableRequest = OpenLovableGenerateRequest.builder()
                    .userRequirement(prompt)
                    .referenceUrls(referenceUrls.isEmpty() ? null : referenceUrls)
                    .customizationRequirement(customization)
                    .needsCrawling(needsCrawling)
                    .sandboxId(request.getSandboxId())
                    .build();

            log.info("[Prototype] 调用OpenLovable: intent={}, style={}, needsCrawling={}", intent, request.getDesignStyle(), needsCrawling);

            OpenLovableGenerateResponse openLovableResponse = openLovableService.generatePrototype(openLovableRequest);

            long durationMs = openLovableResponse.getDurationSeconds() != null
                    ? openLovableResponse.getDurationSeconds() * 1000
                    : System.currentTimeMillis() - startTime;

            PrototypePreviewResponse response = PrototypePreviewResponse.builder()
                    .success(openLovableResponse.isSuccessful())
                    .sandboxUrl(openLovableResponse.getPreviewUrl())
                    .sandboxId(openLovableResponse.getSandboxId())
                    .provider(openLovableResponse.getProvider())
                    .generationTime(durationMs)
                    .intentType(intent)
                    .needsCrawling(needsCrawling)
                    .referenceUrls(referenceUrls)
                    .designStyle(request.getDesignStyle())
                    .error(openLovableResponse.isSuccessful() ? null : openLovableResponse.getErrorMessage())
                    .warnings(warnings)
                    .build();

            // V2.0新增：原型生成成功后创建 AppSpec 实体
            if (openLovableResponse.isSuccessful()) {
                UUID appSpecId = createAppSpecFromPrototype(request, openLovableResponse, intent);
                response.setAppSpecId(appSpecId.toString());
                log.info("[Prototype] AppSpec创建成功: appSpecId={}", appSpecId);
            }

            return Result.success(response);

        } catch (BusinessException e) {
            log.error("[Prototype] 业务错误: {}", e.getMessage());
            return Result.error(e.getCode(), e.getMessage());
        } catch (Exception e) {
            log.error("[Prototype] 系统错误", e);
            return Result.error(ErrorCode.SYSTEM_ERROR);
        }
    }

    private IntentClassificationResult resolveIntentIfNeeded(PrototypeGenerateRequest request, List<String> warnings) {
        if (Boolean.TRUE.equals(request.getForceReclassify()) || !StringUtils.hasText(request.getIntentType())) {
            try {
                return intentClassifier.classifyIntent(request.getUserRequirement());
            } catch (BusinessException ex) {
                warnings.add("意图识别失败，使用客户端意图：" + ex.getMessage());
            }
        }
        return null;
    }

    private RequirementIntent determineIntent(PrototypeGenerateRequest request,
                                              IntentClassificationResult classifierResult,
                                              List<String> warnings) {
        RequirementIntent intent = null;

        if (classifierResult != null) {
            intent = classifierResult.getIntent();
        }

        if (intent == null && StringUtils.hasText(request.getIntentType())) {
            intent = RequirementIntent.fromCode(request.getIntentType());
        }

        if (intent == null) {
            warnings.add("未能获取有效意图，默认使用DESIGN_FROM_SCRATCH流程");
            intent = RequirementIntent.DESIGN_FROM_SCRATCH;
        }

        return intent;
    }

    private List<String> mergeReferenceUrls(PrototypeGenerateRequest request, IntentClassificationResult classifierResult) {
        Set<String> merged = new LinkedHashSet<>();

        if (classifierResult != null && classifierResult.getReferenceUrls() != null) {
            classifierResult.getReferenceUrls().stream()
                    .filter(StringUtils::hasText)
                    .map(String::trim)
                    .forEach(merged::add);
        }

        if (request.getReferenceUrls() != null) {
            request.getReferenceUrls().stream()
                    .filter(StringUtils::hasText)
                    .map(String::trim)
                    .forEach(merged::add);
        }

        if (StringUtils.hasText(request.getSelectedTemplateReferenceUrl())) {
            merged.add(request.getSelectedTemplateReferenceUrl().trim());
        }

        return new ArrayList<>(merged);
    }

    private String buildPrompt(PrototypeGenerateRequest request, RequirementIntent intent) {
        StringBuilder prompt = new StringBuilder(request.getUserRequirement().trim());

        DesignStyle style = DesignStyle.fromCode(request.getDesignStyle());
        if (style != null) {
            prompt.append("\n\nUI风格要求：").append(style.getDisplayName())
                    .append("- ").append(style.getDescription());
        }

        if (StringUtils.hasText(request.getSelectedTemplateName())) {
            prompt.append("\n\n请结合行业模板《")
                    .append(request.getSelectedTemplateName().trim())
                    .append("》的业务结构优化页面布局。");
        }

        if (intent != null && intent.needsCustomization()) {
            prompt.append("\n\n确保在克隆基础上满足定制化需求，突出差异化价值。");
        }

        prompt.append("\n\n请输出完整的多端可运行前端原型（Web/H5/Android/iOS统一架构），重点突出交互体验和响应速度。");

        return prompt.toString();
    }

    /**
     * V2.0新增：创建 AppSpec 实体（供 SSE 流式生成完成后调用）
     * 
     * 桥接方案：前端在原型生成完成后调用此 API，创建 AppSpec 以支持后续代码生成
     *
     * @param request 包含沙箱信息和用户需求的请求
     * @return 包含 appSpecId 的响应
     */
    @PostMapping("/create-app-spec")
    @Operation(summary = "创建AppSpec", description = "原型生成后创建AppSpec，用于后续代码生成")
    public Result<Map<String, Object>> createAppSpec(@RequestBody Map<String, Object> request) {
        log.info("[Prototype] 创建AppSpec请求: {}", request);

        String userRequirement = (String) request.get("userRequirement");
        String sandboxId = (String) request.get("sandboxId");
        String sandboxUrl = (String) request.get("sandboxUrl");
        String designStyle = (String) request.get("designStyle");
        String intentType = (String) request.getOrDefault("intentType", "DESIGN_FROM_SCRATCH");

        if (userRequirement == null || userRequirement.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "userRequirement 不能为空");
        }
        if (sandboxUrl == null || sandboxUrl.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR, "sandboxUrl 不能为空");
        }

        // 获取用户ID
        UUID userId;
        UUID tenantId;
        try {
            String loginId = StpUtil.getLoginIdAsString();
            if (loginId != null && !loginId.isEmpty()) {
                userId = UUID.fromString(loginId);
            } else {
                userId = UUID.fromString("00000000-0000-0000-0000-000000000001");
            }
            
            Object sessionTenantId = StpUtil.getSession().get("tenantId");
            if (sessionTenantId != null) {
                tenantId = UUID.fromString(sessionTenantId.toString());
            } else {
                tenantId = UUID.fromString("00000000-0000-0000-0000-000000000001");
            }
        } catch (Exception e) {
            log.warn("[Prototype] 获取用户会话失败，使用默认ID: {}", e.getMessage());
            userId = UUID.fromString("00000000-0000-0000-0000-000000000001");
            tenantId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        }

        // 构建 specContent
        Map<String, Object> specContent = new HashMap<>();
        specContent.put("userRequirement", userRequirement);
        specContent.put("createdAt", Instant.now().toString());

        // 构建 frontendPrototype
        Map<String, Object> frontendPrototype = new HashMap<>();
        frontendPrototype.put("sandboxId", sandboxId);
        frontendPrototype.put("previewUrl", sandboxUrl);
        frontendPrototype.put("generatedAt", Instant.now().toString());

        // 构建 metadata
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("selectedStyleId", designStyle);
        metadata.put("sandboxId", sandboxId);
        metadata.put("prototypeGeneratedAt", Instant.now().toString());

        // 创建 AppSpec 实体
        AppSpecEntity appSpec = AppSpecEntity.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .createdByUserId(userId)
                .specContent(specContent)
                .version(1)
                .status(AppSpecEntity.Status.DRAFT.getValue())
                .intentType(intentType)
                .confidenceScore(BigDecimal.valueOf(0.95))
                .selectedStyle(designStyle)
                .frontendPrototype(frontendPrototype)
                .frontendPrototypeUrl(sandboxUrl)
                .prototypeGeneratedAt(Instant.now())
                .designConfirmed(false)
                .metadata(metadata)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        appSpecMapper.insert(appSpec);

        log.info("[Prototype] AppSpec已创建 - id={}, userId={}, style={}, sandboxUrl={}",
                appSpec.getId(), userId, designStyle, sandboxUrl);

        Map<String, Object> response = new HashMap<>();
        response.put("appSpecId", appSpec.getId().toString());
        response.put("success", true);

        return Result.success(response);
    }

    /**
     * 生成原型预览（SSE流式响应，支持实时进度显示）
     *
     * V2.0优化：支持基于AppSpec + DesignSpec的流式生成
     */
    @PostMapping(value = "/generate/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "生成原型预览（流式）", description = "SSE流式响应，实时显示生成进度")
    public ResponseEntity<StreamingResponseBody> generatePrototypeStream(
            @Valid @RequestBody PrototypeGenerateRequest request
    ) {
        log.info("[Prototype SSE] 开始流式生成: requirement={}, appSpecId={}", request.getUserRequirement(), request.getAppSpecId());

        StreamingResponseBody stream = outputStream -> {
            try {
                String prompt;
                List<String> referenceUrls = new ArrayList<>();
                boolean needsCrawling = false;
                String customization = null;

                // V2.0 Logic: Generate from AppSpec + Style
                if (StringUtils.hasText(request.getAppSpecId()) && StringUtils.hasText(request.getDesignStyle())) {
                    log.info("[Prototype SSE] 使用AppSpec生成: id={}, style={}", request.getAppSpecId(), request.getDesignStyle());
                    UUID appSpecId = UUID.fromString(request.getAppSpecId());
                    AppSpecEntity appSpec = appSpecMapper.selectById(appSpecId);

                    Map<String, Object> designSpec = null;
                    
                    if (appSpec != null && appSpec.getMetadata() != null) {
                        @SuppressWarnings("unchecked")
                        List<Map<String, Object>> styleVariants = (List<Map<String, Object>>) appSpec.getMetadata().get("styleVariants");
                        
                        if (styleVariants != null) {
                            Map<String, Object> selectedVariant = styleVariants.stream()
                                .filter(v -> request.getDesignStyle().equals(v.get("styleId")))
                                .findFirst()
                                .orElse(null);
                                
                            if (selectedVariant != null) {
                                @SuppressWarnings("unchecked")
                                Map<String, Object> ds = (Map<String, Object>) selectedVariant.get("designSpec");
                                designSpec = ds;
                            }
                        }
                    }

                    if (designSpec != null) {
                        // Get requirement from AppSpec if not provided in request
                        String userReq = request.getUserRequirement();
                        if (!StringUtils.hasText(userReq) && appSpec.getSpecContent() != null) {
                            userReq = (String) appSpec.getSpecContent().get("userRequirement");
                        }
                        
                        prompt = planRoutingService.buildDesignPromptFromSpec(userReq, designSpec);
                        log.info("[Prototype SSE] 已构建DesignSpec Prompt");
                    } else {
                        log.warn("[Prototype SSE] 未找到指定风格或AppSpec: id={}, style={}", request.getAppSpecId(), request.getDesignStyle());
                        prompt = StringUtils.hasText(request.getUserRequirement()) ? request.getUserRequirement() : "Generate a web app"; 
                    }
                } else {
                    // Existing V1 Logic
                    List<String> warnings = new ArrayList<>();
                    IntentClassificationResult classifierResult = resolveIntentIfNeeded(request, warnings);
                    RequirementIntent intent = determineIntent(request, classifierResult, warnings);
                    referenceUrls.addAll(mergeReferenceUrls(request, classifierResult));
                    needsCrawling = intent != null && intent.needsWebCrawling() || !referenceUrls.isEmpty();
                    customization = firstNonBlank(
                            request.getCustomizationRequirement(),
                            classifierResult != null ? classifierResult.getCustomizationRequirement() : null
                    );

                    prompt = buildPrompt(request, intent);
                }

                // Step 2: 调用 OpenLovableService 的流式方法
                openLovableService.generatePrototypeStream(
                        prompt,
                        request.getSandboxId(),
                        referenceUrls,
                        customization,
                        needsCrawling,
                        outputStream
                );

                log.info("[Prototype SSE] 流式生成完成");

            } catch (Exception e) {
                log.error("[Prototype SSE] 流式生成失败", e);
                String errorEvent = "data: {\"type\":\"error\",\"error\":\"" + e.getMessage() + "\"}\n\n";
                try {
                    outputStream.write(errorEvent.getBytes());
                    outputStream.flush();
                } catch (IOException ioException) {
                    log.warn("[Prototype SSE] 错误事件写入失败（客户端可能已断开）: {}", ioException.getMessage());
                }
            }
        };

        return ResponseEntity.ok()
                .contentType(MediaType.TEXT_EVENT_STREAM)
                .header("Cache-Control", "no-cache")
                .header("Connection", "keep-alive")
                .header("X-Accel-Buffering", "no") // 禁用Nginx缓冲
                .body(stream);
    }

    private String firstNonBlank(String first, String second) {
        if (StringUtils.hasText(first)) {
            return first;
        }
        return StringUtils.hasText(second) ? second : null;
    }

    /**
     * V2.0新增：从原型生成结果创建 AppSpec 实体
     * 
     * 桥接方案：保留现有的原型生成流程，同时创建 AppSpec 以支持后续代码生成
     *
     * @param request 原型生成请求
     * @param response OpenLovable 响应
     * @param intent 意图分类结果
     * @return 创建的 AppSpec ID
     */
    private UUID createAppSpecFromPrototype(
            PrototypeGenerateRequest request,
            OpenLovableGenerateResponse response,
            RequirementIntent intent) {
        
        // 获取用户ID：优先从Sa-Token会话获取
        UUID userId;
        UUID tenantId;
        try {
            String loginId = StpUtil.getLoginIdAsString();
            if (loginId != null && !loginId.isEmpty()) {
                userId = UUID.fromString(loginId);
            } else {
                userId = UUID.fromString("00000000-0000-0000-0000-000000000001");
            }
            
            Object sessionTenantId = StpUtil.getSession().get("tenantId");
            if (sessionTenantId != null) {
                tenantId = UUID.fromString(sessionTenantId.toString());
            } else {
                tenantId = UUID.fromString("00000000-0000-0000-0000-000000000001");
            }
        } catch (Exception e) {
            log.warn("[Prototype] 获取用户会话失败，使用默认ID: {}", e.getMessage());
            userId = UUID.fromString("00000000-0000-0000-0000-000000000001");
            tenantId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        }

        // 构建 specContent（用户需求）
        Map<String, Object> specContent = new HashMap<>();
        specContent.put("userRequirement", request.getUserRequirement());
        specContent.put("createdAt", Instant.now().toString());

        // 构建 frontendPrototype（保存沙箱信息）
        Map<String, Object> frontendPrototype = new HashMap<>();
        frontendPrototype.put("sandboxId", response.getSandboxId());
        frontendPrototype.put("previewUrl", response.getPreviewUrl());
        frontendPrototype.put("provider", response.getProvider());
        frontendPrototype.put("generatedAt", Instant.now().toString());

        // 构建 metadata（扩展信息）
        Map<String, Object> metadata = new HashMap<>();
        metadata.put("selectedStyleId", request.getDesignStyle());
        metadata.put("sandboxId", response.getSandboxId());
        metadata.put("selectedTemplateId", request.getSelectedTemplateId());
        metadata.put("selectedTemplateName", request.getSelectedTemplateName());
        metadata.put("prototypeGeneratedAt", Instant.now().toString());

        // 创建 AppSpec 实体
        AppSpecEntity appSpec = AppSpecEntity.builder()
                .id(UUID.randomUUID())
                .tenantId(tenantId)
                .createdByUserId(userId)
                .specContent(specContent)
                .version(1)
                .status(AppSpecEntity.Status.DRAFT.getValue())
                .intentType(intent != null ? intent.name() : "DESIGN_FROM_SCRATCH")
                .confidenceScore(BigDecimal.valueOf(0.95))
                .selectedStyle(request.getDesignStyle())
                .frontendPrototype(frontendPrototype)
                .frontendPrototypeUrl(response.getPreviewUrl())
                .prototypeGeneratedAt(Instant.now())
                .designConfirmed(false)
                .metadata(metadata)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        appSpecMapper.insert(appSpec);

        log.info("[Prototype] AppSpec已创建 - id={}, userId={}, style={}, sandboxUrl={}",
                appSpec.getId(), userId, request.getDesignStyle(), response.getPreviewUrl());

        return appSpec.getId();
    }
}
