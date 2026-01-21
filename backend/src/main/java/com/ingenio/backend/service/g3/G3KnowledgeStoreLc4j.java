package com.ingenio.backend.service.g3;

import com.ingenio.backend.config.G3RagProperties;
import com.ingenio.backend.entity.g3.G3ArtifactEntity;
import com.ingenio.backend.entity.g3.G3JobEntity;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.Metadata;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.store.embedding.EmbeddingMatch;
import dev.langchain4j.store.embedding.EmbeddingSearchRequest;
import dev.langchain4j.store.embedding.EmbeddingSearchResult;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.filter.Filter;
import dev.langchain4j.store.embedding.filter.MetadataFilterBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * G3 知识库服务（LangChain4j 版本）。
 *
 * 是什么：基于 LangChain4j EmbeddingStore 的知识库实现。
 * 做什么：提供索引、检索与上下文格式化能力。
 * 为什么：支持 Redis 向量检索的渐进迁移。
 */
@Service
@ConditionalOnProperty(name = "ingenio.g3.rag.engine", havingValue = "lc4j")
public class G3KnowledgeStoreLc4j implements G3KnowledgeStorePort {

    private static final Logger log = LoggerFactory.getLogger(G3KnowledgeStoreLc4j.class);

    private static final String META_SCOPE = "scope";
    private static final String SCOPE_JOB = "job";
    private static final String SCOPE_REPO = "repo";
    private static final String META_TENANT_ID = "tenantId";
    private static final String META_PROJECT_ID = "projectId";
    private static final String META_JOB_ID = "jobId";
    private static final String META_FILE_PATH = "filePath";
    private static final String META_FILE_NAME = "fileName";
    private static final String META_LANGUAGE = "language";
    private static final String META_ROUND = "round";
    private static final String META_CONTENT_HASH = "contentHash";
    private static final String META_REF = "ref";

    /**
     * Embedding 模型。
     *
     * 是什么：RAG 嵌入模型实例。
     * 做什么：生成文本向量用于检索。
     * 为什么：向量检索依赖嵌入模型。
     */
    private final EmbeddingModel embeddingModel;
    /**
     * Embedding 存储。
     *
     * 是什么：向量存储实例。
     * 做什么：保存与检索向量数据。
     * 为什么：实现 RAG 检索的存储层。
     */
    private final EmbeddingStore<TextSegment> embeddingStore;
    /**
     * RAG 配置。
     *
     * 是什么：G3 RAG 配置容器。
     * 做什么：提供检索阈值与切片参数。
     * 为什么：保证检索行为可配置。
     */
    private final G3RagProperties ragProperties;
    /**
     * 文档切片器。
     *
     * 是什么：LangChain4j 文档切片器。
     * 做什么：将文档拆分为可嵌入的片段。
     * 为什么：避免过长文本影响 Embedding。
     */
    private final dev.langchain4j.data.document.DocumentSplitter documentSplitter;

    /**
     * 构造函数。
     *
     * 是什么：知识库实现初始化入口。
     * 做什么：注入嵌入模型、存储与配置。
     * 为什么：保证索引与检索能力可用。
     *
     * @param embeddingModel Embedding 模型
     * @param embeddingStore Embedding 存储
     * @param ragProperties RAG 配置
     */
    public G3KnowledgeStoreLc4j(
            EmbeddingModel embeddingModel,
            EmbeddingStore<TextSegment> embeddingStore,
            G3RagProperties ragProperties) {
        this.embeddingModel = embeddingModel;
        this.embeddingStore = embeddingStore;
        this.ragProperties = ragProperties;
        int chunkSize = ragProperties.getChunkSize() > 0 ? ragProperties.getChunkSize() : 800;
        int chunkOverlap = ragProperties.getChunkOverlap() >= 0 ? ragProperties.getChunkOverlap() : 120;
        this.documentSplitter = DocumentSplitters.recursive(chunkSize, chunkOverlap);
    }

    /**
     * 写入任务级索引。
     *
     * 是什么：任务级索引写入入口。
     * 做什么：将任务产物切片并写入向量库。
     * 为什么：为修复与生成提供检索上下文。
     */
    @Override
    public void ingest(G3JobEntity job, List<G3ArtifactEntity> artifacts) {
        if (job == null || artifacts == null || artifacts.isEmpty()) {
            return;
        }

        List<Document> documents = new ArrayList<>();
        for (G3ArtifactEntity artifact : artifacts) {
            if (artifact == null || artifact.getContent() == null || artifact.getContent().isBlank()) {
                continue;
            }
            if (!isIndexable(artifact.getFileName())) {
                continue;
            }

            Metadata metadata = new Metadata();
            metadata.put(META_SCOPE, SCOPE_JOB);
            metadata.put(META_JOB_ID, job.getId().toString());
            if (job.getTenantId() != null) {
                metadata.put(META_TENANT_ID, job.getTenantId().toString());
            }
            if (job.getAppSpecId() != null) {
                metadata.put(META_PROJECT_ID, job.getAppSpecId().toString());
            }
            metadata.put(META_FILE_PATH, artifact.getFilePath() != null
                    ? artifact.getFilePath() : artifact.getFileName());
            metadata.put(META_FILE_NAME, artifact.getFileName());
            metadata.put(META_LANGUAGE, resolveLanguage(artifact.getFileName(), artifact.getLanguage()));
            metadata.put(META_ROUND, job.getCurrentRound() != null ? job.getCurrentRound() : 0);
            metadata.put(META_CONTENT_HASH, sha256Base64(artifact.getContent()));

            documents.add(new Document(artifact.getContent(), metadata));
        }

        int segments = ingestDocuments(documents);
        log.info("[G3KnowledgeStoreLc4j] 索引写入完成: jobId={}, segments={}", job.getId(), segments);
    }

    /**
     * 写入仓库级索引。
     *
     * 是什么：仓库索引写入入口。
     * 做什么：转换文档并写入向量库。
     * 为什么：支持跨任务检索与复用。
     */
    @Override
    public int ingestRepo(List<?> documents) {
        if (documents == null || documents.isEmpty()) {
            return 0;
        }

        List<Document> converted = new ArrayList<>();
        for (Object raw : documents) {
            Document doc = convertToDocument(raw);
            if (doc != null) {
                converted.add(doc);
            }
        }
        return ingestDocuments(converted);
    }

    /**
     * 任务级语义检索。
     *
     * 是什么：任务范围检索入口。
     * 做什么：按 jobId 过滤并执行检索。
     * 为什么：限定检索范围避免噪声。
     */
    @Override
    public List<TextSegment> search(String query, UUID jobId, int topK) {
        if (query == null || query.isBlank() || jobId == null) {
            return List.of();
        }

        Filter filter = MetadataFilterBuilder.metadataKey(META_SCOPE).isEqualTo(SCOPE_JOB)
                .and(MetadataFilterBuilder.metadataKey(META_JOB_ID).isEqualTo(jobId.toString()));

        return searchWithFilter(query, topK, filter);
    }

    /**
     * 仓库级语义检索。
     *
     * 是什么：仓库范围检索入口。
     * 做什么：按 tenant/project 过滤并检索。
     * 为什么：支持跨任务检索并控制范围。
     */
    @Override
    public List<TextSegment> searchRepo(String query, UUID tenantId, UUID projectId, int topK) {
        if (query == null || query.isBlank()) {
            return List.of();
        }

        Filter filter = MetadataFilterBuilder.metadataKey(META_SCOPE).isEqualTo(SCOPE_REPO);
        if (projectId != null) {
            filter = filter.and(MetadataFilterBuilder.metadataKey(META_PROJECT_ID)
                    .isEqualTo(projectId.toString()));
        } else if (tenantId != null) {
            filter = filter.and(MetadataFilterBuilder.metadataKey(META_TENANT_ID)
                    .isEqualTo(tenantId.toString()));
        }

        return searchWithFilter(query, topK, filter);
    }

    /**
     * 格式化检索结果。
     *
     * 是什么：检索结果格式化方法。
     * 做什么：将片段拼接为 Prompt 上下文。
     * 为什么：提升模型可读性。
     */
    @Override
    public String formatForContext(List<?> documents) {
        if (documents == null || documents.isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("### 相关代码片段 (RAG)\n");
        for (Object raw : documents) {
            String content = extractContent(raw);
            if (content == null || content.isBlank()) {
                continue;
            }
            String filePath = extractMetadata(raw, META_FILE_PATH, META_FILE_NAME, "unknown");
            String language = extractMetadata(raw, META_LANGUAGE, null, "text");
            sb.append(String.format("#### %s\n```%s\n%s\n```\n", filePath, language, content));
        }
        return sb.toString();
    }

    /**
     * 写入文档切片。
     *
     * 是什么：通用写入方法。
     * 做什么：切片、嵌入并写入向量存储。
     * 为什么：复用写入逻辑并集中处理异常。
     *
     * @param documents 文档列表
     * @return 写入的切片数量
     */
    private int ingestDocuments(List<Document> documents) {
        if (documents == null || documents.isEmpty()) {
            return 0;
        }

        List<TextSegment> segments = documentSplitter.splitAll(documents);
        if (segments.isEmpty()) {
            return 0;
        }

        List<dev.langchain4j.data.embedding.Embedding> embeddings;
        try {
            embeddings = embeddingModel.embedAll(segments).content();
        } catch (Exception e) {
            log.warn("[G3KnowledgeStoreLc4j] Embedding 生成失败，已跳过写入: {}", e.getMessage());
            return 0;
        }
        if (embeddings == null || embeddings.isEmpty()) {
            log.warn("[G3KnowledgeStoreLc4j] Embedding 结果为空，跳过写入");
            return 0;
        }

        if (embeddings.size() != segments.size()) {
            log.warn("[G3KnowledgeStoreLc4j] Embedding 数量不匹配: embeddings={}, segments={}",
                    embeddings.size(), segments.size());
        }

        embeddingStore.addAll(embeddings, segments);
        return segments.size();
    }

    /**
     * 带过滤条件的检索。
     *
     * 是什么：过滤检索方法。
     * 做什么：生成向量并执行检索请求。
     * 为什么：统一过滤检索逻辑。
     *
     * @param query 查询语句
     * @param topK 返回数量
     * @param filter 过滤条件
     * @return 片段列表
     */
    private List<TextSegment> searchWithFilter(String query, int topK, Filter filter) {
        try {
            int maxResults = topK > 0 ? topK : 5;
            dev.langchain4j.data.embedding.Embedding embedding = embeddingModel.embed(query).content();
            EmbeddingSearchRequest request = EmbeddingSearchRequest.builder()
                    .queryEmbedding(embedding)
                    .maxResults(maxResults)
                    .minScore(ragProperties.getEmbeddingMinScore())
                    .filter(filter)
                    .build();
            EmbeddingSearchResult<TextSegment> result = embeddingStore.search(request);
            if (result == null || result.matches() == null) {
                return List.of();
            }
            return result.matches().stream()
                    .map(EmbeddingMatch::embedded)
                    .toList();
        } catch (Exception e) {
            log.warn("[G3KnowledgeStoreLc4j] 语义检索失败: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * 转换为 LangChain4j Document。
     *
     * 是什么：文档转换方法。
     * 做什么：将不同文档类型转换为 LC4J Document。
     * 为什么：统一索引写入的数据结构。
     *
     * @param raw 原始文档对象
     * @return LC4J Document
     */
    private Document convertToDocument(Object raw) {
        if (raw == null) {
            return null;
        }
        if (raw instanceof Document doc) {
            return doc;
        }
        if (raw instanceof org.springframework.ai.document.Document springDoc) {
            Map<String, Object> metadataMap = springDoc.getMetadata();
            Metadata metadata = metadataMap != null ? Metadata.from(metadataMap) : new Metadata();
            if (!metadata.containsKey(META_SCOPE)) {
                metadata.put(META_SCOPE, SCOPE_REPO);
            }
            return new Document(springDoc.getContent(), metadata);
        }
        return null;
    }

    /**
     * 提取文档内容。
     *
     * 是什么：内容抽取方法。
     * 做什么：从不同片段类型获取文本。
     * 为什么：统一格式化输出使用。
     *
     * @param raw 文档对象
     * @return 文本内容
     */
    private String extractContent(Object raw) {
        if (raw instanceof TextSegment segment) {
            return segment.text();
        }
        if (raw instanceof Document doc) {
            return doc.text();
        }
        if (raw instanceof org.springframework.ai.document.Document springDoc) {
            return springDoc.getContent();
        }
        return null;
    }

    /**
     * 提取元数据。
     *
     * 是什么：元数据抽取方法。
     * 做什么：从不同片段类型获取元数据字段。
     * 为什么：统一格式化输出与过滤逻辑。
     *
     * @param raw 文档对象
     * @param primaryKey 主键
     * @param fallbackKey 备用键
     * @param defaultValue 默认值
     * @return 元数据值
     */
    private String extractMetadata(Object raw, String primaryKey, String fallbackKey, String defaultValue) {
        if (raw instanceof TextSegment segment) {
            Metadata metadata = segment.metadata();
            return valueOrFallback(metadata.getString(primaryKey),
                    fallbackKey != null ? metadata.getString(fallbackKey) : null,
                    defaultValue);
        }
        if (raw instanceof Document doc) {
            Metadata metadata = doc.metadata();
            return valueOrFallback(metadata.getString(primaryKey),
                    fallbackKey != null ? metadata.getString(fallbackKey) : null,
                    defaultValue);
        }
        if (raw instanceof org.springframework.ai.document.Document springDoc) {
            Object primary = springDoc.getMetadata().get(primaryKey);
            Object fallback = fallbackKey != null ? springDoc.getMetadata().get(fallbackKey) : null;
            return valueOrFallback(primary != null ? primary.toString() : null,
                    fallback != null ? fallback.toString() : null,
                    defaultValue);
        }
        return defaultValue;
    }

    /**
     * 取值或回退。
     *
     * 是什么：元数据取值辅助方法。
     * 做什么：优先取 primary，再取 fallback，最后取默认值。
     * 为什么：避免缺失字段导致空值。
     *
     * @param primary 主值
     * @param fallback 备用值
     * @param defaultValue 默认值
     * @return 结果值
     */
    private String valueOrFallback(String primary, String fallback, String defaultValue) {
        if (primary != null && !primary.isBlank()) {
            return primary;
        }
        if (fallback != null && !fallback.isBlank()) {
            return fallback;
        }
        return defaultValue;
    }

    /**
     * 判断是否可索引。
     *
     * 是什么：文件类型过滤方法。
     * 做什么：仅允许支持的扩展名。
     * 为什么：控制索引范围与成本。
     *
     * @param fileName 文件名
     * @return 是否可索引
     */
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

    /**
     * 解析语言标识。
     *
     * 是什么：语言推断方法。
     * 做什么：优先使用已有语言，再根据扩展名推断。
     * 为什么：用于格式化输出的代码块语言。
     *
     * @param fileName 文件名
     * @param fallback 备用语言
     * @return 语言标识
     */
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

    /**
     * 计算 SHA-256 哈希。
     *
     * 是什么：内容哈希生成方法。
     * 做什么：计算 Base64 编码的 SHA-256。
     * 为什么：用于内容去重与追踪。
     *
     * @param content 文本内容
     * @return Base64 哈希
     */
    private String sha256Base64(String content) {
        if (content == null) {
            return "";
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(content.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hashed);
        } catch (Exception e) {
            return "";
        }
    }
}
