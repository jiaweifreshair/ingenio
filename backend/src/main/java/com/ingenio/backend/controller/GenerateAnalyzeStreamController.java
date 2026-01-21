package com.ingenio.backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ingenio.backend.dto.response.AnalysisProgressMessage;
import com.ingenio.backend.entity.StructuredRequirementEntity;
import com.ingenio.backend.service.NLRequirementAnalyzer;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 需求“深度分析”SSE流式接口
 *
 * 用途：
 * - 为前端“深度分析”面板提供实时进度（progress）与最终结果（complete）。
 *
 * 事件约定（与前端 useAnalysisSse 对齐）：
 * - event: progress  data: AnalysisProgressMessage(JSON)
 * - event: complete  data: { "result": StructuredRequirementEntity }
 * - event: error     data: { "error": "..." }
 *
 * 说明：
 * - 历史前端固定请求路径为 /v1/generate/analyze-stream（经 context-path=/api 实际为 /api/v1/generate/analyze-stream）。
 * - 之前后端缺失该端点导致 404，前端显示 “SSE请求失败: 404 Not Found”。
 */
@Slf4j
@RestController
@RequestMapping("/v1/generate")
@RequiredArgsConstructor
public class GenerateAnalyzeStreamController {

    private final NLRequirementAnalyzer requirementAnalyzer;
    private final ObjectMapper objectMapper;

    /**
     * SSE流式分析入口
     */
    @PostMapping(value = "/analyze-stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter analyzeStream(@Valid @RequestBody AnalyzeStreamRequest request) {
        // 创建SseEmitter，设置10分钟超时（分析+蓝图可能较长）
        SseEmitter emitter = new SseEmitter(600_000L);

        emitter.onCompletion(() -> log.info("SSE分析流完成"));
        emitter.onTimeout(() -> {
            log.warn("SSE分析流超时");
            emitter.complete();
        });
        emitter.onError((ex) -> {
            log.warn("SSE分析流异常关闭: {}", ex.getMessage());
            emitter.complete();
        });

        CompletableFuture.runAsync(() -> {
            try {
                StructuredRequirementEntity result = requirementAnalyzer.analyzeWithProgress(
                        request.getRequirement(),
                        msg -> sendProgress(emitter, msg)
                );

                // 完成事件：前端优先读取 data.result
                sendEvent(emitter, "complete", Map.of("result", result));
            } catch (Exception e) {
                log.error("SSE流式分析失败", e);
                try {
                    sendEvent(emitter, "error", Map.of("error", e.getMessage() != null ? e.getMessage() : "分析失败"));
                } catch (Exception ignore) {
                    // ignore
                } finally {
                    emitter.complete();
                }
            } finally {
                // 正常完成也关闭连接，避免长连接悬挂
                emitter.complete();
            }
        });

        return emitter;
    }

    /**
     * 健康检查：用于排查“路由是否存在/服务是否启动”
     */
    @GetMapping("/analyze-stream")
    public Map<String, Object> analyzeStreamStatus() {
        return Map.of(
                "status", "ready",
                "path", "/v1/generate/analyze-stream",
                "aiConfigured", requirementAnalyzer.isConfigured()
        );
    }

    private void sendProgress(SseEmitter emitter, AnalysisProgressMessage msg) {
        try {
            sendEvent(emitter, "progress", msg);
        } catch (Exception e) {
            // 这里不抛出，避免一次发送失败导致全链路中断（浏览器刷新/断开属于正常情况）
            log.debug("发送 progress 事件失败（可能是客户端已断开）: {}", e.getMessage());
        }
    }

    private void sendEvent(SseEmitter emitter, String eventName, Object data) throws IOException {
        emitter.send(SseEmitter.event()
                .name(eventName)
                .data(objectMapper.writeValueAsString(data)));
    }

    /**
     * 流式分析请求体
     */
    @Data
    public static class AnalyzeStreamRequest {
        /**
         * 用户需求描述（至少10个字符）
         */
        @NotBlank(message = "需求描述不能为空")
        @Size(min = 10, message = "需求描述至少需要10个字符")
        private String requirement;
    }
}
