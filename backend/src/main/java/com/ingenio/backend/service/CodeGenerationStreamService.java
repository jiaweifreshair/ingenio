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
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
            // 1. 获取AppSpec
            AppSpecEntity appSpec = appSpecService.getById(request.getAppSpecId());
            if (appSpec == null) {
                throw new BusinessException(ErrorCode.APPSPEC_NOT_FOUND);
            }

            // 2. 发送"思考中"事件
            sendEvent(emitter, SseEvent.builder()
                .type("thinking")
                .message("分析需求：" + extractRequirement(appSpec))
                .duration(2000)
                .build());

            // 3. 构建AI提示词
            String prompt = buildPrompt(appSpec);
            log.debug("AI提示词长度: {} 字符", prompt.length());

            // 4. 调用AI生成代码 (非流式版本,后续优化为流式)
            String response = chatClient.prompt(prompt).call().content();
            log.info("AI响应长度: {} 字符", response.length());

            // 5. 解析AI响应中的文件
            List<SseEvent.GeneratedFile> generatedFiles = parseGeneratedFiles(response, emitter);

            // 6. 发送完成事件
            sendEvent(emitter, SseEvent.builder()
                .type("complete")
                .files(generatedFiles)
                .message("代码生成完成")
                .build());

            // 7. 完成SSE流
            emitter.complete();
            log.info("流式代码生成完成: appSpecId={}, 文件数={}", request.getAppSpecId(), generatedFiles.size());

        } catch (Exception e) {
            log.error("流式代码生成失败: appSpecId={}", request.getAppSpecId(), e);
            try {
                sendEvent(emitter, SseEvent.builder()
                    .type("error")
                    .message("代码生成失败: " + e.getMessage())
                    .build());
                emitter.complete();
            } catch (IOException ex) {
                emitter.completeWithError(ex);
            }
        }
    }

    /**
     * 解析AI生成的文件
     * 格式: <file path="src/App.tsx">...content...</file>
     *
     * @param aiResponse AI响应内容
     * @param emitter SSE发射器,用于实时发送事件
     * @return 生成的文件列表
     */
    private List<SseEvent.GeneratedFile> parseGeneratedFiles(String aiResponse, SseEmitter emitter) throws IOException {
        List<SseEvent.GeneratedFile> files = new ArrayList<>();

        // 简化解析：使用正则表达式提取<file>标签
        // 实际生产环境可以使用更强大的XML解析器
        String[] parts = aiResponse.split("<file path=\"");

        for (int i = 1; i < parts.length; i++) {
            String part = parts[i];
            int pathEnd = part.indexOf("\">");
            if (pathEnd == -1) continue;

            String filePath = part.substring(0, pathEnd);
            int contentEnd = part.indexOf("</file>");
            if (contentEnd == -1) continue;

            String fileContent = part.substring(pathEnd + 2, contentEnd).trim();

            // 发送文件开始事件
            sendEvent(emitter, SseEvent.builder()
                .type("file-start")
                .path(filePath)
                .fileType(getFileType(filePath))
                .build());

            // 发送文件内容事件
            sendEvent(emitter, SseEvent.builder()
                .type("file-content")
                .path(filePath)
                .content(fileContent)
                .build());

            // 发送文件完成事件
            sendEvent(emitter, SseEvent.builder()
                .type("file-complete")
                .path(filePath)
                .build());

            // 添加到文件列表
            files.add(SseEvent.GeneratedFile.builder()
                .path(filePath)
                .content(fileContent)
                .type(getFileType(filePath))
                .completed(true)
                .edited(false)
                .build());

            log.debug("解析文件: path={}, contentLength={}", filePath, fileContent.length());
        }

        return files;
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
        Map<String, Object> specContent = appSpec.getSpecContent();
        String requirement = extractRequirement(appSpec);

        return String.format("""
            你是一个专业的全栈开发工程师。请根据以下需求生成完整的React应用代码。

            需求描述：
            %s

            要求：
            1. 使用React 19 + TypeScript + Tailwind CSS
            2. 代码必须使用<file path="文件路径">代码内容</file>格式输出
            3. 至少生成以下文件：
               - src/App.tsx (主应用组件)
               - src/index.tsx (入口文件)
               - src/styles.css (样式文件)
            4. 代码要简洁、可读、符合最佳实践
            5. 确保所有import语句正确

            现在开始生成代码：
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
