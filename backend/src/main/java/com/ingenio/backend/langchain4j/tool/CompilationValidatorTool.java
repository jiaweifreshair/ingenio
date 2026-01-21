package com.ingenio.backend.langchain4j.tool;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ingenio.backend.dto.CodeGenerationResult;
import com.ingenio.backend.dto.CompilationResult;
import com.ingenio.backend.service.CompilationValidator;
import dev.langchain4j.agent.tool.Tool;

import java.util.UUID;

/**
 * 编译验证工具封装。
 *
 * 是什么：CompilationValidator 的 LangChain4j 工具适配器。
 * 做什么：将编译验证能力暴露为可调用工具。
 * 为什么：为 Agent 提供编译反馈闭环。
 */
public class CompilationValidatorTool {

    /**
     * 编译验证服务。
     *
     * 是什么：现有编译验证组件。
     * 做什么：执行编译并返回结果。
     * 为什么：复用现有验证逻辑。
     */
    private final CompilationValidator compilationValidator;

    /**
     * JSON 序列化器。
     *
     * 是什么：ObjectMapper 实例。
     * 做什么：序列化编译结果为 JSON。
     * 为什么：便于工具调用结果传递。
     */
    private final ObjectMapper objectMapper;

    /**
     * 构造函数。
     *
     * 是什么：编译验证工具初始化入口。
     * 做什么：注入编译验证服务与 JSON 解析器。
     * 为什么：保证工具可输出结构化结果。
     *
     * @param compilationValidator 编译验证服务
     * @param objectMapper JSON 解析器
     */
    public CompilationValidatorTool(CompilationValidator compilationValidator, ObjectMapper objectMapper) {
        this.compilationValidator = compilationValidator;
        this.objectMapper = objectMapper;
    }

    /**
     * 编译验证入口。
     *
     * 是什么：可被 Agent 调用的编译验证工具方法。
     * 做什么：构造 CodeGenerationResult 并触发编译验证。
     * 为什么：让 Agent 能够获取编译结果用于修复。
     *
     * @param projectRoot 项目根目录
     * @param projectType 项目类型
     * @param taskId      任务ID（可选）
     * @return 编译结果 JSON
     */
    @Tool("编译验证指定项目，返回结构化编译结果")
    public String validate(String projectRoot, String projectType, String taskId) {
        CodeGenerationResult request = CodeGenerationResult.builder()
                .projectRoot(projectRoot)
                .projectType(projectType)
                .taskId(parseUuid(taskId))
                .build();
        CompilationResult result = compilationValidator.compile(request);
        try {
            return objectMapper.writeValueAsString(result);
        } catch (JsonProcessingException e) {
            return "{\"success\":false,\"message\":\"编译结果序列化失败\"}";
        }
    }

    /**
     * 解析 UUID。
     *
     * 是什么：UUID 解析工具方法。
     * 做什么：将字符串解析为 UUID。
     * 为什么：避免工具调用输入格式错误导致异常。
     *
     * @param raw 原始字符串
     * @return UUID 或 null
     */
    private UUID parseUuid(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
