package com.ingenio.backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ingenio.backend.dto.response.AnalysisProgressMessage;
import com.ingenio.backend.entity.InteractiveAnalysisSessionEntity;
import com.ingenio.backend.service.InteractiveAnalysisService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 交互式分析Controller
 *
 * 提供AI深度思考的交互式分析接口:
 * - 启动交互式分析会话
 * - SSE流式接收当前步骤的分析过程
 * - 确认当前步骤,进入下一步
 * - 提出修改建议,��新执行当前步骤
 */
@Slf4j
@RestController
@RequestMapping("/v1/interactive-analysis")
@RequiredArgsConstructor
public class InteractiveAnalysisController {

    private final InteractiveAnalysisService analysisService;
    private final ObjectMapper objectMapper;

    /**
     * 启动交互式分析会话
     *
     * POST /api/v1/interactive-analysis/start
     */
    @PostMapping("/start")
    public Map<String, Object> startSession(@Valid @RequestBody StartSessionRequest request) {
        log.info("启动交互式分析会话: requirement={}", request.getRequirement());

        // TODO: 从认证上下文获取用户ID
        Long userId = 1L;

        String sessionId = analysisService.startSession(userId, request.getRequirement());
        InteractiveAnalysisSessionEntity session = analysisService.getSession(sessionId);

        return Map.of(
                "sessionId", sessionId,
                "currentStep", session.getCurrentStep(),
                "status", session.getStatus()
        );
    }

    /**
     * SSE流式接收当前步骤的分析过程
     *
     * GET /api/v1/interactive-analysis/{sessionId}/stream
     */
    @GetMapping(value = "/{sessionId}/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamCurrentStep(@PathVariable String sessionId) {
        log.info("开始流式分析: sessionId={}", sessionId);

        SseEmitter emitter = new SseEmitter(600_000L);

        emitter.onCompletion(() -> log.info("SSE流完成: sessionId={}", sessionId));
        emitter.onTimeout(() -> {
            log.warn("SSE流超时: sessionId={}", sessionId);
            emitter.complete();
        });
        emitter.onError((ex) -> {
            log.warn("SSE流异常: sessionId={}", sessionId, ex);
            emitter.complete();
        });

        CompletableFuture.runAsync(() -> {
            try {
                Object result = analysisService.executeCurrentStep(
                        sessionId,
                        msg -> sendProgress(emitter, msg)
                );

                // 步骤完成,发送complete事件
                sendEvent(emitter, "complete", Map.of(
                        "step", analysisService.getSession(sessionId).getCurrentStep(),
                        "result", result
                ));

            } catch (Exception e) {
                log.error("流式分析失败: sessionId={}", sessionId, e);
                try {
                    sendEvent(emitter, "error", Map.of("error", e.getMessage()));
                } catch (Exception ignore) {
                    // ignore
                } finally {
                    emitter.complete();
                }
            } finally {
                emitter.complete();
            }
        });

        return emitter;
    }

    /**
     * 确认当前步骤,进入下一步
     *
     * POST /api/v1/interactive-analysis/{sessionId}/confirm
     */
    @PostMapping("/{sessionId}/confirm")
    public Map<String, Object> confirmStep(
            @PathVariable String sessionId,
            @Valid @RequestBody ConfirmStepRequest request) {

        log.info("确认步骤: sessionId={}, step={}", sessionId, request.getStep());

        analysisService.confirmStep(sessionId, request.getStep());
        InteractiveAnalysisSessionEntity session = analysisService.getSession(sessionId);

        return Map.of(
                "sessionId", sessionId,
                "currentStep", session.getCurrentStep(),
                "status", session.getStatus(),
                "isCompleted", "COMPLETED".equals(session.getStatus())
        );
    }

    /**
     * 提出修改建议,重新执行当前步骤
     *
     * POST /api/v1/interactive-analysis/{sessionId}/modify
     */
    @PostMapping("/{sessionId}/modify")
    public Map<String, Object> modifyStep(
            @PathVariable String sessionId,
            @Valid @RequestBody ModifyStepRequest request) {

        log.info("修改步骤: sessionId={}, step={}, feedback={}", sessionId, request.getStep(), request.getFeedback());

        analysisService.modifyStep(sessionId, request.getStep(), request.getFeedback());
        InteractiveAnalysisSessionEntity session = analysisService.getSession(sessionId);

        return Map.of(
                "sessionId", sessionId,
                "currentStep", session.getCurrentStep(),
                "status", session.getStatus()
        );
    }

    /**
     * 取消会话
     *
     * POST /api/v1/interactive-analysis/{sessionId}/cancel
     */
    @PostMapping("/{sessionId}/cancel")
    public Map<String, Object> cancelSession(@PathVariable String sessionId) {
        log.info("取消会话: sessionId={}", sessionId);

        analysisService.cancelSession(sessionId);

        return Map.of(
                "sessionId", sessionId,
                "status", "CANCELLED"
        );
    }

    /**
     * 获取会话信息
     *
     * GET /api/v1/interactive-analysis/{sessionId}
     */
    @GetMapping("/{sessionId}")
    public InteractiveAnalysisSessionEntity getSession(@PathVariable String sessionId) {
        return analysisService.getSession(sessionId);
    }

    private void sendProgress(SseEmitter emitter, AnalysisProgressMessage msg) {
        try {
            sendEvent(emitter, "progress", msg);
        } catch (Exception e) {
            log.debug("发送progress事件失败: {}", e.getMessage());
        }
    }

    private void sendEvent(SseEmitter emitter, String eventName, Object data) throws IOException {
        emitter.send(SseEmitter.event()
                .name(eventName)
                .data(objectMapper.writeValueAsString(data)));
    }

    /**
     * 启动会话请求
     */
    @Data
    public static class StartSessionRequest {
        @NotBlank(message = "需求描述不能为空")
        @Size(min = 10, message = "需求描述至少需要10个字符")
        private String requirement;
    }

    /**
     * 确认步骤请求
     */
    @Data
    public static class ConfirmStepRequest {
        private int step;
    }

    /**
     * 修改步骤请求
     */
    @Data
    public static class ModifyStepRequest {
        private int step;

        @NotBlank(message = "修改建议不能为空")
        private String feedback;
    }
}
