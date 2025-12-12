package com.ingenio.backend.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import com.ingenio.backend.dto.request.CodeGenerationRequest;
import com.ingenio.backend.service.CodeGenerationStreamService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;

/**
 * 代码生成流式控制器
 * 提供基于SSE的实时代码生成API
 *
 * 核心功能:
 * 1. 接收代码生成请求(appSpecId)
 * 2. 创建SSE长连接
 * 3. 异步执行AI代码生成
 * 4. 实时推送生成进度和结果
 * 5. 支持错误处理和超时控制
 *
 * 使用场景:
 * - 前端向导页面实时显示代码生成过程
 * - 用户可见AI思考过程和文件生成进度
 * - 生成完成后可立即预览代码和效果
 *
 * @author Ingenio Team
 * @since 2.0.0
 */
@Slf4j
@RestController
@RequestMapping("/v1/code-generation")
@RequiredArgsConstructor
public class CodeGenerationStreamController {

    private final CodeGenerationStreamService codeGenerationStreamService;

    /**
     * 流式代码生成接口
     *
     * SSE事件流格式:
     * 1. event: thinking | data: {"type":"thinking","message":"分析需求...","duration":2000}
     * 2. event: file-start | data: {"type":"file-start","path":"src/App.tsx","fileType":"tsx"}
     * 3. event: file-content | data: {"type":"file-content","path":"src/App.tsx","content":"..."}
     * 4. event: file-complete | data: {"type":"file-complete","path":"src/App.tsx"}
     * 5. event: complete | data: {"type":"complete","files":[...],"message":"代码生成完成"}
     *
     * 前端使用示例:
     * ```typescript
     * const eventSource = new EventSource('/api/v1/code-generation/stream?appSpecId=xxx');
     * eventSource.addEventListener('thinking', (e) => {
     *   const data = JSON.parse(e.data);
     *   console.log(data.message); // "分析需求..."
     * });
     * eventSource.addEventListener('file-content', (e) => {
     *   const data = JSON.parse(e.data);
     *   console.log(data.path, data.content); // 实时显示代码
     * });
     * ```
     *
     * @param request 代码生成请求参数
     * @return SseEmitter SSE发射器
     */
    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @SaCheckLogin
    public SseEmitter generateCodeStream(@Valid @RequestBody CodeGenerationRequest request) {
        log.info("接收代码生成请求: appSpecId={}, regenerate={}, streaming={}",
            request.getAppSpecId(), request.getRegenerate(), request.getStreaming());

        // 创建SseEmitter，设置10分钟超时（代码生成可能需要较长时间）
        SseEmitter emitter = new SseEmitter(600_000L);

        // 设置完成和超时回调
        emitter.onCompletion(() -> {
            log.info("SSE流完成: appSpecId={}", request.getAppSpecId());
        });

        emitter.onTimeout(() -> {
            log.warn("SSE流超时: appSpecId={}", request.getAppSpecId());
            emitter.complete();
        });

        emitter.onError((ex) -> {
            log.error("SSE流错误: appSpecId={}", request.getAppSpecId(), ex);
            emitter.completeWithError(ex);
        });

        // 异步执行代码生成，避免阻塞HTTP线程
        CompletableFuture.runAsync(() -> {
            try {
                codeGenerationStreamService.generateCodeStream(request, emitter);
            } catch (IOException e) {
                log.error("代码生成流式处理失败: appSpecId={}", request.getAppSpecId(), e);
                try {
                    emitter.completeWithError(e);
                } catch (Exception ex) {
                    log.error("发送错误事件失败", ex);
                }
            }
        });

        return emitter;
    }

    /**
     * 健康检查接口
     * 用于验证SSE服务是否正常运行
     *
     * @return 健康状态信息
     */
    @GetMapping("/health")
    public String health() {
        return "Code Generation Stream Service is running";
    }
}
