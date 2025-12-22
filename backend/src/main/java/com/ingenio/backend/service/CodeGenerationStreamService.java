package com.ingenio.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ingenio.backend.common.exception.BusinessException;
import com.ingenio.backend.common.exception.ErrorCode;
import com.ingenio.backend.dto.request.CodeGenerationRequest;
import com.ingenio.backend.dto.sse.SseEvent;
import com.ingenio.backend.entity.AppSpecEntity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 代码生成流式服务
 * 实现SSE流式推送AI生成的代码
 *
 * 核心功能:
 * 1. 基于AppSpec调用AI生成代码
 * 2. 实时解析AI输出的文件标记 (<file path="...">...</file>)
 * 3. 通过SSE推送thinking/file-start/file-content/file-complete/complete事件
 * 4. 支持流式传输,用户可实时看到代码生成过程
 *
 * @author Ingenio Team
 * @since 2.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CodeGenerationStreamService {

    private final ChatClient chatClient;
    private final AppSpecService appSpecService;
    private final ObjectMapper objectMapper;

    /**
     * 流式生成代码并实时推送SSE事件
     *
     * @param request 代码生成请求参数
     * @param emitter SSE发射器
     * @throws IOException SSE发送失败时抛出
     */
    public void generateCodeStream(CodeGenerationRequest request, SseEmitter emitter) throws IOException {
        log.info("开始流式代码生成: appSpecId={}", request.getAppSpecId());

        try {
            // 立即发送初始状态，确保连接建立后客户端收到数据
            sendEvent(emitter, SseEvent.builder()
                .type("thinking")
                .message("正在连接AI服务，准备生成代码...")
                .build());

            // 1. 获取AppSpec
            AppSpecEntity appSpec = appSpecService.getById(request.getAppSpecId());
            if (appSpec == null) {
                throw new BusinessException(ErrorCode.APPSPEC_NOT_FOUND);
            }

            // 2. 构建AI提示词
            String prompt = buildPrompt(appSpec);
            log.debug("AI提示词长度: {} 字符", prompt.length());

            // 3. 调用AI生成代码 (流式)
            Flux<String> stream = chatClient.prompt(prompt).stream().content();

            // 4. 处理流式响应
            StreamContext context = new StreamContext(emitter);
            
            stream.subscribe(
                content -> {
                    try {
                        context.processChunk(content);
                    } catch (IOException e) {
                        log.error("SSE发送失败", e);
                        emitter.completeWithError(e);
                    }
                },
                error -> {
                    log.error("AI流式生成出错", error);
                    try {
                        sendEvent(emitter, SseEvent.builder()
                            .type("error")
                            .message("AI生成出错: " + error.getMessage())
                            .build());
                        emitter.complete();
                    } catch (IOException e) {
                        emitter.completeWithError(e);
                    }
                },
                () -> {
                    try {
                        // 处理剩余缓冲区
                        context.flush();
                        
                        // 发送完成事件
                        sendEvent(emitter, SseEvent.builder()
                            .type("complete")
                            .message("代码生成完成")
                            .build());
                        
                        emitter.complete();
                        log.info("流式代码生成完成: appSpecId={}", request.getAppSpecId());
                    } catch (IOException e) {
                        emitter.completeWithError(e);
                    }
                }
            );

        } catch (Exception e) {
            log.error("流式代码生成失败: appSpecId={}", request.getAppSpecId(), e);
            try {
                sendEvent(emitter, SseEvent.builder()
                    .type("error")
                    .message("代码生成失败: " + e.getMessage())
                    .build());
                emitter.complete();
            } catch (IOException ex) {
                emitter.complete();
            }
        }
    }

    /**
     * 流处理上下文，用于维护解析状态
     */
    private class StreamContext {
        private final SseEmitter emitter;
        private final StringBuilder buffer = new StringBuilder();
        private State state = State.IDLE;
        private String currentFilePath = null;
        private final Pattern FILE_START_PATTERN = Pattern.compile("<file path=\"([^\"]+)\">");

        private enum State {
            IDLE,       // 空闲/普通文本
            THINKING,   // 思考中
            CODE_FILE   // 代码文件生成中
        }

        public StreamContext(SseEmitter emitter) {
            this.emitter = emitter;
        }

        public void processChunk(String chunk) throws IOException {
            if (chunk == null || chunk.isEmpty()) return;
            
            buffer.append(chunk);
            parseBuffer();
        }
        
        public void flush() throws IOException {
            // 如果缓冲区还有内容，并且处于THINKING状态，作为思考内容发送
            if (!buffer.isEmpty() && state == State.THINKING) {
                sendEvent(emitter, SseEvent.builder()
                    .type("thinking")
                    .message(buffer.toString())
                    .build());
                buffer.setLength(0);
            }
        }

        private void parseBuffer() throws IOException {
            boolean bufferChanged = true;
            while (bufferChanged) {
                bufferChanged = false;
                String currentBuffer = buffer.toString();

                switch (state) {
                    case IDLE:
                        // 检查是否开始思考
                        int thinkingStart = currentBuffer.indexOf("<thinking>");
                        if (thinkingStart != -1) {
                            // 发送之前的普通文本（如果有）
                            if (thinkingStart > 0) {
                                // 忽略非特定标签外的普通文本，或者是作为普通的说明
                            }
                            
                            state = State.THINKING;
                            buffer.delete(0, thinkingStart + 10); // 移除 <thinking>
                            bufferChanged = true;
                        } else {
                            // 检查是否开始文件
                            Matcher matcher = FILE_START_PATTERN.matcher(currentBuffer);
                            if (matcher.find()) {
                                currentFilePath = matcher.group(1);
                                state = State.CODE_FILE;
                                
                                // 发送文件开始事件
                                sendEvent(emitter, SseEvent.builder()
                                    .type("file-start")
                                    .path(currentFilePath)
                                    .fileType(getFileType(currentFilePath))
                                    .build());
                                
                                buffer.delete(0, matcher.end());
                                bufferChanged = true;
                            }
                        }
                        break;

                    case THINKING:
                        int thinkingEnd = currentBuffer.indexOf("</thinking>");
                        if (thinkingEnd != -1) {
                            // 发送这一段思考内容
                            String thinkingContent = currentBuffer.substring(0, thinkingEnd);
                            if (!thinkingContent.isEmpty()) {
                                sendEvent(emitter, SseEvent.builder()
                                    .type("thinking")
                                    .message(thinkingContent)
                                    .build());
                            }
                            
                            state = State.IDLE;
                            buffer.delete(0, thinkingEnd + 11); // 移除 </thinking>
                            bufferChanged = true;
                        } else {
                            // 实时推送思考内容，保留最后一部分以防止截断标签
                            // 例如: "thinking... </thi" -> 只发送 "thinking... "
                            // 简单的策略：如果缓冲区太长，或者不包含潜在的标签开头，就发送出去
                            if (currentBuffer.length() > 20 && !currentBuffer.contains("</")) {
                                sendEvent(emitter, SseEvent.builder()
                                    .type("thinking")
                                    .message(currentBuffer)
                                    .build());
                                buffer.setLength(0);
                                // buffer为空，不需要设置bufferChanged，等待新数据
                            }
                        }
                        break;

                    case CODE_FILE:
                        int fileEnd = currentBuffer.indexOf("</file>");
                        if (fileEnd != -1) {
                            // 发送这一段代码内容
                            String fileContent = currentBuffer.substring(0, fileEnd);
                            if (!fileContent.isEmpty()) {
                                sendEvent(emitter, SseEvent.builder()
                                    .type("file-content")
                                    .path(currentFilePath)
                                    .content(fileContent)
                                    .build());
                            }
                            
                            // 发送文件完成事件
                            sendEvent(emitter, SseEvent.builder()
                                .type("file-complete")
                                .path(currentFilePath)
                                .build());
                                
                            state = State.IDLE;
                            currentFilePath = null;
                            buffer.delete(0, fileEnd + 7); // 移除 </file>
                            bufferChanged = true;
                        } else {
                            // 实时推送代码内容
                            // 同样防止截断标签
                            if (currentBuffer.length() > 20 && !currentBuffer.contains("</")) {
                                sendEvent(emitter, SseEvent.builder()
                                    .type("file-content")
                                    .path(currentFilePath)
                                    .content(currentBuffer)
                                    .build());
                                buffer.setLength(0);
                            }
                        }
                        break;
                }
            }
        }
    }

    /**
     * 发送SSE事件
     *
     * @param emitter SSE发射器
     * @param event 事件对象
     * @throws IOException 发送失败时抛出
     */
    private void sendEvent(SseEmitter emitter, SseEvent event) throws IOException {
        String jsonData = objectMapper.writeValueAsString(event);
        emitter.send(SseEmitter.event()
            .name(event.getType())
            .data(jsonData));
        log.trace("发送SSE事件: type={}", event.getType());
    }

    /**
     * 构建AI提示词
     *
     * @param appSpec 应用规范
     * @return AI提示词
     */
    private String buildPrompt(AppSpecEntity appSpec) {
        String requirement = extractRequirement(appSpec);

        return String.format("""
            你是一个专业的全栈开发工程师。请根据以下需求生成完整的React应用代码。

            需求描述：
            %s

            要求：
            1. 思考过程：在生成代码前，请先进行详细的思考分析，包括组件设计、状态管理、样式策略等。
               请务必将思考过程包裹在 <thinking>...</thinking> 标签中。
               
            2. 代码生成：
               - 使用React 19 + TypeScript + Tailwind CSS
               - 代码必须使用 <file path="文件路径">代码内容</file> 格式输出
               - 至少生成以下文件：
                 - src/App.tsx (主应用组件)
                 - src/components/ui/button.tsx (示例组件)
                 - src/lib/utils.ts (工具函数)
                 
            3. 格式规范：
               - 确保所有import语句正确
               - 不要使用Markdown代码块(```)，直接输出XML格式标签
            
            输出示例：
            <thinking>
            用户需要一个待办事项应用...
            我需要设计一个TodoList组件...
            </thinking>
            <file path="src/App.tsx">
            import React from 'react';
            ...
            </file>

            现在开始：
            """, requirement);
    }

    /**
     * 从AppSpec提取需求描述
     *
     * @param appSpec 应用规范
     * @return 需求描述
     */
    private String extractRequirement(AppSpecEntity appSpec) {
        Map<String, Object> specContent = appSpec.getSpecContent();
        if (specContent != null && specContent.containsKey("requirement")) {
            return specContent.get("requirement").toString();
        }
        return "未指定需求";
    }

    /**
     * 根据文件路径获取文件类型
     *
     * @param filePath 文件路径
     * @return 文件类型 (typescript, javascript, jsx, tsx, css, json, html等)
     */
    private String getFileType(String filePath) {
        if (filePath.endsWith(".tsx")) return "tsx";
        if (filePath.endsWith(".ts")) return "typescript";
        if (filePath.endsWith(".jsx")) return "jsx";
        if (filePath.endsWith(".js")) return "javascript";
        if (filePath.endsWith(".css")) return "css";
        if (filePath.endsWith(".json")) return "json";
        if (filePath.endsWith(".html")) return "html";
        return "text";
    }
}
