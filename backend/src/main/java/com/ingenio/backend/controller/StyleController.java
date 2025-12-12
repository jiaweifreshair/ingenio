package com.ingenio.backend.controller;

import com.ingenio.backend.common.Result;
import com.ingenio.backend.dto.request.Generate7StylesRequest;
import com.ingenio.backend.dto.response.Generate7StylesResponse;
import com.ingenio.backend.service.SuperDesignService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * 设计风格控制器
 * 提供7种设计风格快速预览生成接口
 *
 * Phase 7实现 - SuperDesign 7风格预览API
 *
 * @author Ingenio Team
 * @version 2.0.0
 * @since 2025-11-15
 */
@Slf4j
@RestController
@RequestMapping("/v1/styles")
@RequiredArgsConstructor
@Tag(name = "设计风格管理", description = "SuperDesign 7种设计风格快速预览生成接口")
public class StyleController {

    private final SuperDesignService superDesignService;

    /**
     * 生成7种设计风格的快速预览
     *
     * 功能：
     * 1. 接收用户需求描述
     * 2. 并行生成7种设计风格的HTML预览
     * 3. 返回完整的预览列表（包括HTML内容、生成时间等）
     *
     * 性能指标：
     * - 目标：P95 < 15秒
     * - 并行执行：使用ExecutorService并行生成
     *
     * @param request 7风格生成请求（包含用户需求描述、应用类型等）
     * @return 7种风格预览响应（包含所有风格的HTML内容、生成时间、警告信息等）
     */
    @PostMapping("/generate-previews")
    @Operation(
        summary = "生成7种设计风格快速预览",
        description = "根据用户需求描述，并行生成7种设计风格的HTML预览（现代极简、活力时尚、经典专业、未来科技、沉浸式3D、游戏化、自然流动）"
    )
    public Result<Generate7StylesResponse> generateStylePreviews(
        @Valid @RequestBody Generate7StylesRequest request
    ) {
        log.info("[StyleController] 收到7风格预览生成请求: requirement={}, appType={}, targetPlatform={}, useAI={}",
            request.getUserRequirement(),
            request.getAppType(),
            request.getTargetPlatform(),
            request.getUseAICustomization()
        );

        try {
            // 调用SuperDesignService生成7种风格预览
            long startTime = System.currentTimeMillis();
            Generate7StylesResponse response = superDesignService.generate7StyleHTMLPreviews(request);
            long totalTime = System.currentTimeMillis() - startTime;

            log.info("[StyleController] 7风格预览生成完成: success={}, stylesCount={}, totalTime={}ms",
                response.getSuccess(),
                response.getStyles().size(),
                totalTime
            );

            // 记录性能指标
            if (totalTime > 15000) {
                log.warn("[StyleController] 7风格生成耗时超过15秒: {}ms", totalTime);
            }

            // 记录警告信息
            if (response.getWarnings() != null && !response.getWarnings().isEmpty()) {
                log.warn("[StyleController] 7风格生成产生警告: {}", response.getWarnings());
            }

            return Result.success(response);

        } catch (Exception e) {
            log.error("[StyleController] 7风格预览生成失败", e);

            // 返回失败响应
            Generate7StylesResponse errorResponse = Generate7StylesResponse.builder()
                .success(false)
                .error("风格预览生成失败: " + e.getMessage())
                .build();

            return Result.success(errorResponse);
        }
    }

    /**
     * 健康检查接口
     *
     * @return 服务状态
     */
    @GetMapping("/health")
    @Operation(summary = "健康检查", description = "检查设计风格服务是否正常运行")
    public Result<String> health() {
        log.debug("[StyleController] 健康检查请求");
        return Result.success("Style service is running");
    }
}
