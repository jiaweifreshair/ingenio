package com.ingenio.backend.module.g3kernel.controller;

import com.ingenio.backend.common.Result;
import com.ingenio.backend.service.g3kernel.G3KernelAssetService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * G3 Kernel 资产 API
 *
 * <p>用途：</p>
 * <ul>
 *   <li>将 `backend/g3_context/` 中的模板与系统 Prompt 作为“运行资产”对外提供（避免前端/Agent 侧硬编码）；</li>
 *   <li>为后续 MetaGPT/Agent-OS 接入提供稳定的 Kernel 资产入口。</li>
 * </ul>
 *
 * <p>说明：</p>
 * <ul>
 *   <li>当前阶段仅提供只读资产（Template/Prompt/Capabilities）；</li>
 *   <li>Guard/Validate 等“可阻断能力”在后续 Phase 以独立接口接入。</li>
 * </ul>
 */
@RestController
@RequestMapping({"/v1/g3/kernel", "/v1/g3-kernel"})
@RequiredArgsConstructor
@Tag(name = "G3 Kernel API", description = "G3 Kernel 运行资产（模板/提示词/能力表）")
public class G3KernelController {

    private final G3KernelAssetService assetService;

    @GetMapping("/health")
    @Operation(summary = "健康检查")
    public Result<HealthResponse> health() {
        return Result.success(new HealthResponse("ok"));
    }

    @GetMapping("/index")
    @Operation(summary = "列出可用资产索引")
    public Result<KernelIndexResponse> index() {
        return Result.success(assetService.getIndex());
    }

    @GetMapping("/templates")
    @Operation(summary = "列出模板文件名列表")
    public Result<List<String>> templates() {
        return Result.success(assetService.getIndex().getTemplates());
    }

    @GetMapping("/prompts")
    @Operation(summary = "列出系统提示词文件名列表")
    public Result<List<String>> prompts() {
        return Result.success(assetService.getIndex().getPrompts());
    }

    @GetMapping(value = "/templates/{name}", produces = MediaType.TEXT_PLAIN_VALUE)
    @Operation(summary = "获取模板原文（例如 JavaController.txt）")
    public String template(@PathVariable String name) {
        return assetService.getTemplateContent(name);
    }

    @GetMapping(value = "/prompts/{name}", produces = MediaType.TEXT_PLAIN_VALUE)
    @Operation(summary = "获取系统提示词原文（例如 SYSTEM_PROMPT_PLAYER.md）")
    public String prompt(@PathVariable String name) {
        return assetService.getPromptContent(name);
    }

    @GetMapping(value = "/capabilities", produces = MediaType.TEXT_PLAIN_VALUE)
    @Operation(summary = "获取能力表原文（capabilities.md）")
    public String capabilities() {
        return assetService.getCapabilitiesContent();
    }

    @Data
    public static class HealthResponse {
        private final String status;
    }

    @Data
    public static class KernelIndexResponse {
        /**
         * 模板文件名列表（不含路径）
         */
        private final List<String> templates;
        /**
         * Prompt 文件名列表（不含路径）
         */
        private final List<String> prompts;
        /**
         * 能力表文件名
         */
        private final String capabilities;
    }
}
