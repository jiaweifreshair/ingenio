package com.ingenio.backend.langchain4j.tool;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ingenio.backend.service.g3.G3KnowledgeStorePort;
import dev.langchain4j.agent.tool.Tool;

import java.util.List;
import java.util.UUID;

/**
 * 知识检索工具封装。
 *
 * 是什么：G3 知识库的 LangChain4j 工具适配器。
 * 做什么：提供任务级与仓库级语义检索能力。
 * 为什么：提升 Agent 的上下文检索命中率。
 */
public class KnowledgeSearchTool {

    /**
     * 知识库访问接口。
     *
     * 是什么：G3 知识库端口。
     * 做什么：执行语义检索与格式化。
     * 为什么：屏蔽 RAG 实现差异。
     */
    private final G3KnowledgeStorePort knowledgeStore;

    /**
     * JSON 序列化器。
     *
     * 是什么：ObjectMapper 实例。
     * 做什么：序列化检索结果。
     * 为什么：便于工具输出传递。
     */
    private final ObjectMapper objectMapper;

    /**
     * 构造函数。
     *
     * 是什么：知识检索工具初始化入口。
     * 做什么：注入知识库端口与 JSON 解析器。
     * 为什么：保证检索结果可被格式化输出。
     *
     * @param knowledgeStore 知识库端口
     * @param objectMapper JSON 解析器
     */
    public KnowledgeSearchTool(G3KnowledgeStorePort knowledgeStore, ObjectMapper objectMapper) {
        this.knowledgeStore = knowledgeStore;
        this.objectMapper = objectMapper;
    }

    /**
     * 任务级语义检索。
     *
     * 是什么：按 jobId 范围检索。
     * 做什么：返回格式化的上下文片段。
     * 为什么：支持修复与生成阶段的上下文增强。
     *
     * @param query 检索语句
     * @param jobId 任务ID
     * @param topK  返回数量
     * @return 上下文文本
     */
    @Tool("检索任务级上下文，返回格式化片段")
    public String searchJob(String query, String jobId, int topK) {
        UUID parsedJobId = parseUuid(jobId);
        if (parsedJobId == null) {
            return "jobId 无效，无法检索";
        }
        List<?> docs = knowledgeStore.search(query, parsedJobId, topK);
        return knowledgeStore.formatForContext(docs);
    }

    /**
     * 仓库级语义检索。
     *
     * 是什么：按 tenantId/projectId 范围检索。
     * 做什么：返回 JSON 格式的检索结果。
     * 为什么：支持跨任务资产复用。
     *
     * @param query     检索语句
     * @param tenantId  租户ID
     * @param projectId 项目ID
     * @param topK      返回数量
     * @return JSON 结果
     */
    @Tool("检索仓库级上下文，返回 JSON 片段")
    public String searchRepo(String query, String tenantId, String projectId, int topK) {
        List<?> docs = knowledgeStore.searchRepo(
                query,
                parseUuid(tenantId),
                parseUuid(projectId),
                topK);
        try {
            return objectMapper.writeValueAsString(docs);
        } catch (JsonProcessingException e) {
            return "检索结果序列化失败";
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
