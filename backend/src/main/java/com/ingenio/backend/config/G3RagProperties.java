package com.ingenio.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

/**
 * G3 RAG 配置（仓库级索引与检索）。
 *
 * <p>说明：</p>
 * <ul>
 *   <li>用于控制 Repo Index 是否启用、扫描范围与资源限制；</li>
 *   <li>默认面向后端代码仓库，可通过 repoRoot 指定扫描根目录。</li>
 * </ul>
 */
@Configuration
@ConfigurationProperties(prefix = "ingenio.g3.rag")
public class G3RagProperties {

    /**
     * 是否启用仓库索引能力。
     */
    private boolean repoIndexEnabled = true;

    /**
     * 是否在 G3 任务开始时自动触发 Repo Index。
     */
    private boolean autoIndexOnJob = true;

    /**
     * 仓库扫描根目录（为空时使用后端工作目录）。
     */
    private String repoRoot;

    /**
     * 单次索引最大文件数（防止扫描过大仓库）。
     */
    private int maxFiles = 4000;

    /**
     * 单个文件最大字节数（超出则跳过）。
     */
    private long maxFileBytes = 200_000;

    /**
     * 允许索引的文件扩展名（不含点）。
     */
    private List<String> includeExtensions = new ArrayList<>(
            List.of("java", "xml", "yml", "yaml", "properties", "md", "sql"));

    /**
     * 需要排除的目录名（按目录名匹配）。
     */
    private List<String> excludeDirs = new ArrayList<>(
            List.of(".git", "node_modules", "target", "dist", ".next", "build",
                    "out", "logs", ".pnpm-store", ".idea", ".vscode"));

    /**
     * Embedding 提供商标识（LangChain4j providerKey）。
     *
     * 是什么：用于选择 Embedding 提供商的标识。
     * 做什么：决定使用哪个 EmbeddingModel。
     * 为什么：支持多模型切换与灰度。
     */
    private String embeddingProvider = "uniaix";

    /**
     * EmbeddingStore 类型（redis/memory）。
     *
     * 是什么：向量存储实现类型。
     * 做什么：控制使用 Redis 还是内存存储。
     * 为什么：适配不同环境部署。
     */
    private String embeddingStore = "redis";

    /**
     * Redis 向量索引名称。
     *
     * 是什么：Redis Vector Index 名称。
     * 做什么：区分不同业务索引。
     * 为什么：避免索引冲突。
     */
    private String embeddingIndexName = "g3_embeddings";

    /**
     * Redis 向量索引 key 前缀。
     *
     * 是什么：Redis Key 前缀。
     * 做什么：统一向量数据命名空间。
     * 为什么：避免与其他业务 Key 混用。
     */
    private String embeddingPrefix = "g3:";

    /**
     * Embedding 向量维度。
     *
     * 是什么：Embedding 向量长度。
     * 做什么：用于初始化 Redis 向量索引维度。
     * 为什么：维度不一致会导致检索失败。
     */
    private int embeddingDimension = 2048;

    /**
     * 最小相似度阈值（0-1）。
     *
     * 是什么：向量检索最小相似度。
     * 做什么：过滤低相关度结果。
     * 为什么：提升检索准确率。
     */
    private double embeddingMinScore = 0.1d;

    /**
     * 文档切片长度（字符）。
     *
     * 是什么：单个文本切片的最大字符数。
     * 做什么：控制切片粒度。
     * 为什么：避免单次 Embedding 过长。
     */
    private int chunkSize = 800;

    /**
     * 文档切片重叠长度（字符）。
     *
     * 是什么：相邻切片的重叠字符数。
     * 做什么：保留上下文连续性。
     * 为什么：降低切片边界信息丢失。
     */
    private int chunkOverlap = 120;

    public boolean isRepoIndexEnabled() {
        return repoIndexEnabled;
    }

    public void setRepoIndexEnabled(boolean repoIndexEnabled) {
        this.repoIndexEnabled = repoIndexEnabled;
    }

    public boolean isAutoIndexOnJob() {
        return autoIndexOnJob;
    }

    public void setAutoIndexOnJob(boolean autoIndexOnJob) {
        this.autoIndexOnJob = autoIndexOnJob;
    }

    public String getRepoRoot() {
        return repoRoot;
    }

    public void setRepoRoot(String repoRoot) {
        this.repoRoot = repoRoot;
    }

    public int getMaxFiles() {
        return maxFiles;
    }

    public void setMaxFiles(int maxFiles) {
        this.maxFiles = maxFiles;
    }

    public long getMaxFileBytes() {
        return maxFileBytes;
    }

    public void setMaxFileBytes(long maxFileBytes) {
        this.maxFileBytes = maxFileBytes;
    }

    public List<String> getIncludeExtensions() {
        return includeExtensions;
    }

    public void setIncludeExtensions(List<String> includeExtensions) {
        this.includeExtensions = includeExtensions;
    }

    public List<String> getExcludeDirs() {
        return excludeDirs;
    }

    public void setExcludeDirs(List<String> excludeDirs) {
        this.excludeDirs = excludeDirs;
    }

    public String getEmbeddingProvider() {
        return embeddingProvider;
    }

    public void setEmbeddingProvider(String embeddingProvider) {
        this.embeddingProvider = embeddingProvider;
    }

    public String getEmbeddingStore() {
        return embeddingStore;
    }

    public void setEmbeddingStore(String embeddingStore) {
        this.embeddingStore = embeddingStore;
    }

    public String getEmbeddingIndexName() {
        return embeddingIndexName;
    }

    public void setEmbeddingIndexName(String embeddingIndexName) {
        this.embeddingIndexName = embeddingIndexName;
    }

    public String getEmbeddingPrefix() {
        return embeddingPrefix;
    }

    public void setEmbeddingPrefix(String embeddingPrefix) {
        this.embeddingPrefix = embeddingPrefix;
    }

    public int getEmbeddingDimension() {
        return embeddingDimension;
    }

    public void setEmbeddingDimension(int embeddingDimension) {
        this.embeddingDimension = embeddingDimension;
    }

    public double getEmbeddingMinScore() {
        return embeddingMinScore;
    }

    public void setEmbeddingMinScore(double embeddingMinScore) {
        this.embeddingMinScore = embeddingMinScore;
    }

    public int getChunkSize() {
        return chunkSize;
    }

    public void setChunkSize(int chunkSize) {
        this.chunkSize = chunkSize;
    }

    public int getChunkOverlap() {
        return chunkOverlap;
    }

    public void setChunkOverlap(int chunkOverlap) {
        this.chunkOverlap = chunkOverlap;
    }
}
