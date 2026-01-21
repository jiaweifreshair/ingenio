package com.ingenio.backend.service.g3;

import com.ingenio.backend.entity.g3.G3ArtifactEntity;
import com.ingenio.backend.entity.g3.G3JobEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * G3 知识库服务 (RAG Core)
 *
 * 负责将代码产物切片并存入 VectorStore，以及提供语义检索能力。
 * 解决了大型项目中 Coach Agent 无法感知全局上下文的问题。
 *
 * @author Ingenio Team
 * @since 2.2.0 (M6)
 */
@Service
@ConditionalOnProperty(name = "ingenio.g3.rag.engine", havingValue = "spring-ai", matchIfMissing = true)
public class G3KnowledgeStore implements G3KnowledgeStorePort {

    private static final Logger log = LoggerFactory.getLogger(G3KnowledgeStore.class);

    /**
     * 元数据：索引范围标识
     */
    private static final String META_SCOPE = "scope";
    /**
     * 元数据：任务索引范围
     */
    private static final String SCOPE_JOB = "job";
    /**
     * 元数据：仓库索引范围
     */
    private static final String SCOPE_REPO = "repo";
    /**
     * 元数据：租户ID
     */
    private static final String META_TENANT_ID = "tenantId";
    /**
     * 元数据：项目ID（默认使用 appSpecId）
     */
    private static final String META_PROJECT_ID = "projectId";
    /**
     * 元数据：任务ID
     */
    private static final String META_JOB_ID = "jobId";
    /**
     * 元数据：文件路径
     */
    private static final String META_FILE_PATH = "filePath";
    /**
     * 元数据：文件名
     */
    private static final String META_FILE_NAME = "fileName";
    /**
     * 元数据：语言
     */
    private static final String META_LANGUAGE = "language";
    /**
     * 元数据：轮次
     */
    private static final String META_ROUND = "round";
    /**
     * 元数据：内容哈希
     */
    private static final String META_CONTENT_HASH = "contentHash";
    /**
     * 元数据：索引来源引用（commit/快照）
     */
    private static final String META_REF = "ref";

    private final VectorStore vectorStore;

    // 代码切片器：每片约 500 tokens，重叠 100 tokens
    private final TokenTextSplitter tokenTextSplitter;

    /**
     * 构造函数
     *
     * @param vectorStore 向量存储实现
     */
    public G3KnowledgeStore(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
        this.tokenTextSplitter = new TokenTextSplitter(500, 100, 5, 10000, true);
    }

    /**
     * 将生成的产物根据 JobID 存入向量数据库
     *
     * @param job       关联任务
     * @param artifacts 产物列表
     */
    @Override
    public void ingest(G3JobEntity job, List<G3ArtifactEntity> artifacts) {
        if (artifacts == null || artifacts.isEmpty()) {
            return;
        }

        try {
            log.info("[G3KnowledgeStore] 开始构建向量索引: jobId={}, artifacts={}", job.getId(), artifacts.size());

            List<Document> documents = new ArrayList<>();
            for (G3ArtifactEntity artifact : artifacts) {
                if (artifact.getContent() == null || artifact.getContent().isBlank()) {
                    continue;
                }

                // 排除非代码文件或过小文件以节省开销
                if (!isIndexable(artifact.getFileName())) {
                    continue;
                }

                // 创建元数据
                Map<String, Object> metadata = new HashMap<>();
                metadata.put(META_SCOPE, SCOPE_JOB);
                metadata.put(META_JOB_ID, job.getId().toString());
                if (job.getTenantId() != null) {
                    metadata.put(META_TENANT_ID, job.getTenantId().toString());
                }
                if (job.getAppSpecId() != null) {
                    metadata.put(META_PROJECT_ID, job.getAppSpecId().toString());
                }
                metadata.put(META_FILE_PATH, artifact.getFilePath() != null ? artifact.getFilePath() : artifact.getFileName());
                metadata.put(META_FILE_NAME, artifact.getFileName());
                metadata.put(META_LANGUAGE, resolveLanguage(artifact.getFileName(), artifact.getLanguage()));
                metadata.put(META_ROUND, job.getCurrentRound() != null ? job.getCurrentRound() : 0);

                // 创建原始文档
                Document doc = new Document(artifact.getContent(), metadata);
                documents.add(doc);
            }

            int chunks = addDocuments(documents);

            log.info("[G3KnowledgeStore] 索引构建完成: {} artifacts -> {} chunks", artifacts.size(), chunks);

        } catch (Exception e) {
            log.warn("[G3KnowledgeStore] 向量索引构建失败 (可能是 Redis Stack 未启用): {}", e.getMessage());
        }
    }

    /**
     * 将仓库扫描结果写入向量库（Repo 索引）。
     *
     * @param documents 仓库文档列表（已包含元数据）
     * @return 实际写入的切片数量
     */
    @Override
    public int ingestRepo(List<?> documents) {
        if (documents == null || documents.isEmpty()) {
            return 0;
        }

        List<Document> casted = documents.stream()
                .filter(Document.class::isInstance)
                .map(Document.class::cast)
                .toList();
        return addDocuments(casted);
    }

    /**
     * 通用写入方法：切片后写入 VectorStore。
     *
     * @param documents 原始文档列表
     * @return 实际写入的切片数量
     */
    public int addDocuments(List<Document> documents) {
        if (documents == null || documents.isEmpty()) {
            return 0;
        }

        List<Document> splitDocuments = tokenTextSplitter.apply(documents);
        vectorStore.add(splitDocuments);
        return splitDocuments.size();
    }

    /**
     * 语义搜索相关代码片段
     *
     * @param query 查询语句 (e.g. "Fix NullPointerException in UserService")
     * @param jobId 任务ID (用于隔离)
     * @param topK  返回数量
     * @return 相关文档片段
     */
    @Override
    public List<Document> search(String query, UUID jobId, int topK) {
        try {
            return vectorStore.similaritySearch(SearchRequest.query(query)
                    .withTopK(topK)
                    .withFilterExpression(new FilterExpressionBuilder()
                            .eq(META_JOB_ID, jobId.toString())
                            .build()));
        } catch (Exception e) {
            log.warn("[G3KnowledgeStore] 语义搜索失败: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * 仓库级语义搜索（Repo Index）
     *
     * @param query     检索语句
     * @param tenantId  租户ID（可选）
     * @param projectId 项目ID（可选）
     * @param topK      返回数量
     * @return 文档片段列表
     */
    @Override
    public List<Document> searchRepo(String query, UUID tenantId, UUID projectId, int topK) {
        try {
            FilterExpressionBuilder builder = new FilterExpressionBuilder();
            FilterExpressionBuilder.Op expr = builder.eq(META_SCOPE, SCOPE_REPO);

            if (projectId != null) {
                expr = builder.and(expr, builder.eq(META_PROJECT_ID, projectId.toString()));
            } else if (tenantId != null) {
                expr = builder.and(expr, builder.eq(META_TENANT_ID, tenantId.toString()));
            }

            return vectorStore.similaritySearch(SearchRequest.query(query)
                    .withTopK(topK)
                    .withFilterExpression(expr.build()));
        } catch (Exception e) {
            log.warn("[G3KnowledgeStore] 仓库语义搜索失败: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * 格式化检索结果为 Prompt 上下文
     */
    @Override
    public String formatForContext(List<?> documents) {
        if (documents == null || documents.isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("### 相关代码片段 (RAG)\n");
        for (Object raw : documents) {
            if (!(raw instanceof Document doc)) {
                continue;
            }
            String filePath = String.valueOf(doc.getMetadata().getOrDefault(META_FILE_PATH,
                    doc.getMetadata().getOrDefault(META_FILE_NAME, "unknown")));
            String language = String.valueOf(doc.getMetadata().getOrDefault(META_LANGUAGE, "text"));
            sb.append(String.format("#### %s\n```%s\n%s\n```\n", filePath, language, doc.getContent()));
        }
        return sb.toString();
    }

    private boolean isIndexable(String fileName) {
        if (fileName == null) {
            return false;
        }
        return fileName.endsWith(".java")
                || fileName.endsWith(".xml")
                || fileName.endsWith(".yml")
                || fileName.endsWith(".yaml")
                || fileName.endsWith(".properties")
                || fileName.endsWith(".md")
                || fileName.endsWith(".sql");
    }

    private String resolveLanguage(String fileName, String fallback) {
        if (fallback != null && !fallback.isBlank()) {
            return fallback;
        }
        if (fileName == null) {
            return "text";
        }
        String lower = fileName.toLowerCase();
        if (lower.endsWith(".java")) {
            return "java";
        }
        if (lower.endsWith(".xml")) {
            return "xml";
        }
        if (lower.endsWith(".yml") || lower.endsWith(".yaml")) {
            return "yaml";
        }
        if (lower.endsWith(".properties")) {
            return "properties";
        }
        if (lower.endsWith(".sql")) {
            return "sql";
        }
        if (lower.endsWith(".md")) {
            return "markdown";
        }
        return "text";
    }
}
