package com.ingenio.backend.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.ingenio.backend.common.response.Result;
import com.ingenio.backend.dto.request.PlanRoutingRequest;
import com.ingenio.backend.service.PlanRoutingResult;
import com.ingenio.backend.service.PlanRoutingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.UUID;
import com.ingenio.backend.dto.response.DesignConfirmResponse;
import com.ingenio.backend.dto.response.CodeGenerationResponse;

/**
 * Plan阶段路由控制器
 *
 * V2.0核心API：
 * 1. 意图识别与路由决策
 * 2. 风格选择与原型生成
 * 3. 设计确认（触发Execute阶段的关键点）
 *
 * 业务流程：
 * routeRequirement → selectStyle → confirmDesign → Execute阶段
 */
@RestController
@RequestMapping("/v2/plan-routing")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Plan路由", description = "V2.0 Plan阶段智能路由API")
@Validated
public class PlanRoutingController {

    private final PlanRoutingService planRoutingService;

    /**
     * 路由用户需求
     * 执行意图识别、模板匹配、路由决策
     *
     * @param request 路由请求
     * @return 路由结果，包含匹配的模板、风格选项等
     */
    @PostMapping("/route")
    @Operation(summary = "路由用户需求", description = "分析用户需求，识别意图，匹配模板，生成风格选项")
    public Result<PlanRoutingResult> routeRequirement(
            @RequestBody @Validated PlanRoutingRequest request
    ) {
        log.info("收到路由请求 - userRequirement: {}", request.getUserRequirement());

        // 获取userId：优先使用请求中的值，否则从Sa-Token会话获取
        UUID userId = request.getUserId();
        if (userId == null) {
            // V2.0修复：使用getLoginIdDefaultNull()安全获取登录ID，避免未登录时抛出异常
            Object loginIdObj = StpUtil.getLoginIdDefaultNull();
            String loginId = loginIdObj != null ? loginIdObj.toString() : null;
            if (loginId != null && !loginId.isEmpty()) {
                userId = UUID.fromString(loginId);
            } else {
                // 默认用户ID（用于未登录场景的测试）
                userId = UUID.fromString("00000000-0000-0000-0000-000000000001");
            }
        }

        // 获取tenantId：优先使用请求中的值，否则使用默认租户
        UUID tenantId = request.getTenantId();
        if (tenantId == null) {
            // V2.0修复：使用getSession(false)安全获取session，避免未登录时抛出异常
            // getSession(false)：如果用户未登录，返回null而不是抛出NotLoginException
            var session = StpUtil.getSession(false);
            Object sessionTenantId = session != null ? session.get("tenantId") : null;
            if (sessionTenantId != null) {
                tenantId = UUID.fromString(sessionTenantId.toString());
            } else {
                // 默认租户ID（用于未登录场景）
                tenantId = UUID.fromString("00000000-0000-0000-0000-000000000001");
            }
        }

        // V2.0增强：传递用户预选的复杂度和技术栈提示
        PlanRoutingResult result = planRoutingService.route(
                request.getUserRequirement(),
                tenantId,
                userId,
                request.getComplexityHint(),
                request.getTechStackHint()
        );

        log.info("路由完成 - appSpecId: {}, branch: {}, nextAction: {}, complexityHint: {}, techStackHint: {}",
                result.getAppSpecId(), result.getBranch(), result.getNextAction(),
                request.getComplexityHint(), request.getTechStackHint());

        return Result.success(result);
    }

    /**
     * 选择设计风格并生成原型
     * 仅设计分支有效，生成完整的前端原型代码
     *
     * @param appSpecId 应用规格ID
     * @param styleId 选择的风格ID
     * @return 更新后的路由结果，包含原型URL
     */
    @PostMapping("/{appSpecId}/select-style")
    @Operation(summary = "选择设计风格", description = "选择风格并生成前端原型")
    public Result<PlanRoutingResult> selectStyleAndGeneratePrototype(
            @Parameter(description = "AppSpec ID") @PathVariable UUID appSpecId,
            @Parameter(description = "风格ID") @RequestParam String styleId
    ) {
        log.info("收到风格选择请求 - appSpecId: {}, styleId: {}", appSpecId, styleId);

        PlanRoutingResult result = planRoutingService.selectStyleAndGeneratePrototype(appSpecId, styleId);

        log.info("风格选择完成 - prototypeUrl: {}", result.getPrototypeUrl());

        return Result.success(result);
    }

    /**
     * 确认设计方案
     * V2.0关键API：用户确认设计后，才能进入Execute阶段
     * 更新AppSpec的designConfirmed标志
     *
     * @param appSpecId 应用规格ID
     * @return 确认结果，包含是否可进入Execute阶段的标志
     */
    @PostMapping("/{appSpecId}/confirm-design")
    @Operation(summary = "确认设计方案", description = "用户确认设计，标记为可执行状态")
    public Result<DesignConfirmResponse> confirmDesign(
            @Parameter(description = "AppSpec ID") @PathVariable UUID appSpecId
    ) {
        log.info("收到设计确认请求 - appSpecId: {}", appSpecId);

        planRoutingService.confirmDesign(appSpecId);

        Instant confirmedAt = Instant.now();
        log.info("设计确认完成 - appSpecId: {}, designConfirmed: true, confirmedAt: {}", appSpecId, confirmedAt);

        DesignConfirmResponse response = DesignConfirmResponse.builder()
                .success(true)
                .appSpecId(appSpecId)
                .canProceedToExecute(true)
                .designConfirmedAt(confirmedAt)
                .message("设计确认成功")
                .nextAction("调用 /execute-code-generation 开始生成代码")
                .build();

        return Result.success(response);
    }

    /**
     * 执行代码生成
     * Phase 2.2.4: 用户确认设计后触发代码生成
     *
     * V2.0完整流程：
     * 1. 从AppSpec.metadata提取designSpec
     * 2. 构建PlanResult并填充designSpec
     * 3. (新增) 接收前端回传的analysisContext（来自SSE分析结果）并注入PlanResult
     * 4. 调用ExecuteAgent生成完整的全栈代码
     *
     * @param appSpecId 应用规格ID
     * @param analysisContext 前端回传的SSE分析上下文（可选）
     * @return 代码生成结果，包含任务ID和预计完成时间
     */
    @PostMapping("/{appSpecId}/execute-code-generation")
    @Operation(summary = "执行代码生成", description = "用户确认设计后，触发ExecuteAgent生成完整代码")
    public Result<CodeGenerationResponse> executeCodeGeneration(
            @Parameter(description = "AppSpec ID") @PathVariable UUID appSpecId,
            @RequestBody(required = false) java.util.Map<String, Object> analysisContext
    ) {
        log.info("收到代码生成请求 - appSpecId: {}, hasContext: {}", appSpecId, analysisContext != null);

        Instant startedAt = Instant.now();
        java.util.Map<String, Object> codeResult = planRoutingService.executeCodeGeneration(appSpecId, analysisContext);

        // 从结果中提取关键信息
        boolean success = codeResult.getOrDefault("success", false).equals(true);
        UUID projectId = codeResult.get("projectId") != null
                ? UUID.fromString(codeResult.get("projectId").toString()) : null;
        UUID generationTaskId = codeResult.get("generationTaskId") != null
                ? UUID.fromString(codeResult.get("generationTaskId").toString()) : UUID.randomUUID();
        String status = codeResult.getOrDefault("status", "GENERATING").toString();
        
        // 提取previewUrl（支持从顶层或嵌套结构中提取）
        String previewUrl = null;
        if (codeResult.get("previewUrl") != null) {
            previewUrl = codeResult.get("previewUrl").toString();
        } else if (codeResult.get("frontend") instanceof java.util.Map) {
            @SuppressWarnings("unchecked")
            java.util.Map<String, Object> frontend = (java.util.Map<String, Object>) codeResult.get("frontend");
            if (frontend.get("web") instanceof java.util.Map) {
                @SuppressWarnings("unchecked")
                java.util.Map<String, Object> web = (java.util.Map<String, Object>) frontend.get("web");
                if (web.get("previewUrl") != null) {
                    previewUrl = web.get("previewUrl").toString();
                }
            }
        }

        String errorMessage = codeResult.get("errorMessage") != null
                ? codeResult.get("errorMessage").toString() : null;

        log.info("代码生成完成 - appSpecId: {}, success: {}, status: {}", appSpecId, success, status);

        CodeGenerationResponse response = CodeGenerationResponse.builder()
                .success(success)
                .appSpecId(appSpecId)
                .projectId(projectId)
                .generationTaskId(generationTaskId)
                .estimatedCompletionTime(120) // 预计120秒
                .status(status)
                .message(success ? "代码生成已启动" : "代码生成失败")
                .progress(success ? 10 : 0)
                .startedAt(startedAt)
                .previewUrl(previewUrl)
                .errorMessage(errorMessage)
                .build();

        return Result.success(response);
    }

    /**
     * 获取路由状态
     * 查询当前AppSpec的路由状态和进度
     *
     * @param appSpecId 应用规格ID
     * @return 当前路由状态
     */
    @GetMapping("/{appSpecId}/status")
    @Operation(summary = "获取路由状态", description = "查询AppSpec的当前路由状态")
    public Result<PlanRoutingResult> getRoutingStatus(
            @Parameter(description = "AppSpec ID") @PathVariable UUID appSpecId
    ) {
        log.info("查询路由状态 - appSpecId: {}", appSpecId);

        // TODO: 实现状态查询逻辑
        throw new UnsupportedOperationException("状态查询功能尚未实现");
    }

    /**
     * 更新原型状态
     * V2.0新增：前端使用OpenLovable生成预览后，调用此API更新AppSpec的原型信息
     *
     * 解决问题：前端通过SSE流式生成预览后，需要同步更新后端AppSpec的frontendPrototype字段，
     * 否则confirmDesign会检查失败
     *
     * @param appSpecId 应用规格ID
     * @param prototypeInfo 原型信息（包含sandboxId、previewUrl等）
     * @return 更新结果
     */
    @PostMapping("/{appSpecId}/update-prototype")
    @Operation(summary = "更新原型状态", description = "前端生成预览后，同步更新AppSpec的原型信息")
    public Result<java.util.Map<String, Object>> updatePrototypeStatus(
            @Parameter(description = "AppSpec ID") @PathVariable UUID appSpecId,
            @RequestBody java.util.Map<String, Object> prototypeInfo
    ) {
        log.info("收到原型状态更新请求 - appSpecId: {}, prototypeInfo: {}", appSpecId, prototypeInfo);

        planRoutingService.updatePrototypeStatus(appSpecId, prototypeInfo);

        log.info("原型状态更新完成 - appSpecId: {}", appSpecId);

        java.util.Map<String, Object> response = new java.util.HashMap<>();
        response.put("success", true);
        response.put("appSpecId", appSpecId);
        response.put("message", "原型状态更新成功");
        response.put("updatedAt", Instant.now().toString());

        return Result.success(response);
    }
}
