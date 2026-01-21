package com.ingenio.backend.service.g3;

import com.ingenio.backend.config.G3RagProperties;
import com.ingenio.backend.entity.g3.G3JobEntity;
import com.ingenio.backend.entity.g3.G3LogEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

/**
 * G3 仓库索引服务（Repo Index）。
 *
 * <p>职责：</p>
 * <ul>
 *   <li>扫描指定仓库目录并过滤可索引文件；</li>
 *   <li>构建向量文档并写入 VectorStore；</li>
 *   <li>提供“自动索引/手动索引”的统一入口。</li>
 * </ul>
 */
@Service
public class G3RepoIndexService {

    private static final Logger log = LoggerFactory.getLogger(G3RepoIndexService.class);

    private static final String META_SCOPE = "scope";
    private static final String META_TENANT_ID = "tenantId";
    private static final String META_PROJECT_ID = "projectId";
    private static final String META_FILE_PATH = "filePath";
    private static final String META_FILE_NAME = "fileName";
    private static final String META_LANGUAGE = "language";
    private static final String META_CONTENT_HASH = "contentHash";
    private static final String META_REF = "ref";
    private static final String SCOPE_REPO = "repo";

    private final G3KnowledgeStorePort knowledgeStore;
    private final G3RagProperties ragProperties;

    /**
     * 运行期索引记录（避免重复索引）。
     * Key: projectId/tenantId
     */
    private final Map<String, RepoIndexSnapshot> indexSnapshots = new ConcurrentHashMap<>();

    public G3RepoIndexService(G3KnowledgeStorePort knowledgeStore, G3RagProperties ragProperties) {
        this.knowledgeStore = knowledgeStore;
        this.ragProperties = ragProperties;
    }

    /**
     * 在任务启动时尝试自动索引（best-effort）。
     *
     * @param job         G3 任务
     * @param logConsumer 日志回调
     * @return 索引结果（若未触发则返回 SKIPPED）
     */
    public RepoIndexResult ensureIndexed(G3JobEntity job, Consumer<G3LogEntry> logConsumer) {
        if (job == null) {
            return RepoIndexResult.skipped("任务为空，跳过索引");
        }
        if (!ragProperties.isRepoIndexEnabled() || !ragProperties.isAutoIndexOnJob()) {
            return RepoIndexResult.skipped("Repo Index 未启用或未开启自动索引");
        }

        UUID projectId = job.getAppSpecId();
        UUID tenantId = job.getTenantId();
        String key = buildIndexKey(projectId, tenantId);
        if (key == null) {
            return RepoIndexResult.skipped("缺少 projectId/tenantId，跳过索引");
        }

        if (indexSnapshots.containsKey(key)) {
            return RepoIndexResult.skipped("Repo Index 已存在，跳过重复索引");
        }

        RepoIndexRequest request = new RepoIndexRequest();
        request.setProjectId(projectId != null ? projectId.toString() : null);
        request.setTenantId(tenantId != null ? tenantId.toString() : null);
        request.setRef("workspace");
        RepoIndexResult result = indexRepo(request, logConsumer);

        if (result.success()) {
            indexSnapshots.put(key, new RepoIndexSnapshot(System.currentTimeMillis(), result.chunkCount()));
        }

        return result;
    }

    /**
     * 手动触发仓库索引。
     *
     * @param request     索引请求
     * @param logConsumer 日志回调
     * @return 索引结果
     */
    public RepoIndexResult indexRepo(RepoIndexRequest request, Consumer<G3LogEntry> logConsumer) {
        if (!ragProperties.isRepoIndexEnabled()) {
            return RepoIndexResult.skipped("Repo Index 功能未启用");
        }

        Consumer<G3LogEntry> safeLogConsumer = logConsumer != null ? logConsumer : entry -> {};

        Path root = resolveRoot(request.getRootPath());
        if (root == null || !Files.exists(root)) {
            return RepoIndexResult.failure("索引根目录不存在: " + request.getRootPath());
        }

        int maxFiles = request.getMaxFiles() != null ? request.getMaxFiles() : ragProperties.getMaxFiles();
        long maxFileBytes = request.getMaxFileBytes() != null ? request.getMaxFileBytes() : ragProperties.getMaxFileBytes();
        List<String> includeExt = normalizeExtensions(request.getIncludeExtensions(), ragProperties.getIncludeExtensions());
        Set<String> excludeDirs = new HashSet<>(ragProperties.getExcludeDirs());

        safeLogConsumer.accept(G3LogEntry.info(G3LogEntry.Role.EXECUTOR,
                "Repo Index: 开始扫描目录 " + root + " (maxFiles=" + maxFiles + ")"));

        List<Document> documents = new ArrayList<>();
        int scanned = 0;
        int skipped = 0;

        try {
            for (Path path : iterable(root)) {
                if (scanned >= maxFiles) {
                    break;
                }
                if (!Files.isRegularFile(path)) {
                    continue;
                }
                if (isExcluded(path, excludeDirs)) {
                    continue;
                }

                String fileName = path.getFileName().toString();
                String ext = getExtension(fileName);
                if (!includeExt.contains(ext)) {
                    continue;
                }

                long size = Files.size(path);
                if (size > maxFileBytes) {
                    skipped++;
                    continue;
                }

                String content = Files.readString(path, StandardCharsets.UTF_8);
                if (content.isBlank()) {
                    skipped++;
                    continue;
                }

                String relativePath = root.relativize(path).toString().replace("\\", "/");
                Map<String, Object> metadata = new HashMap<>();
                metadata.put(META_SCOPE, SCOPE_REPO);
                if (request.getTenantId() != null) {
                    metadata.put(META_TENANT_ID, request.getTenantId());
                }
                if (request.getProjectId() != null) {
                    metadata.put(META_PROJECT_ID, request.getProjectId());
                }
                metadata.put(META_FILE_PATH, relativePath);
                metadata.put(META_FILE_NAME, fileName);
                metadata.put(META_LANGUAGE, resolveLanguage(ext));
                metadata.put(META_REF, request.getRef() != null ? request.getRef() : "workspace");
                metadata.put(META_CONTENT_HASH, sha256Base64(content));

                documents.add(new Document(content, metadata));
                scanned++;
            }
        } catch (Exception e) {
            log.warn("[G3RepoIndex] 扫描失败: {}", e.getMessage());
            return RepoIndexResult.failure("扫描失败: " + e.getMessage());
        }

        int chunks = 0;
        try {
            chunks = knowledgeStore.ingestRepo(documents);
        } catch (Exception e) {
            return RepoIndexResult.failure("向量索引写入失败: " + e.getMessage());
        }

        RepoIndexResult result = RepoIndexResult.success(scanned, skipped, chunks);
        safeLogConsumer.accept(G3LogEntry.success(G3LogEntry.Role.EXECUTOR,
                "Repo Index 完成: files=" + scanned + ", chunks=" + chunks));
        return result;
    }

    private Path resolveRoot(String rootPath) {
        if (rootPath != null && !rootPath.isBlank()) {
            return Paths.get(rootPath).normalize();
        }
        String configured = ragProperties.getRepoRoot();
        if (configured != null && !configured.isBlank()) {
            return Paths.get(configured).normalize();
        }
        return Paths.get(System.getProperty("user.dir")).normalize();
    }

    private String buildIndexKey(UUID projectId, UUID tenantId) {
        if (projectId != null) {
            return "project:" + projectId;
        }
        if (tenantId != null) {
            return "tenant:" + tenantId;
        }
        return null;
    }

    private List<String> normalizeExtensions(List<String> requestExt, List<String> defaults) {
        List<String> raw = requestExt != null && !requestExt.isEmpty() ? requestExt : defaults;
        List<String> normalized = new ArrayList<>();
        for (String ext : raw) {
            if (ext == null) {
                continue;
            }
            String value = ext.trim().toLowerCase();
            if (value.startsWith(".")) {
                value = value.substring(1);
            }
            if (!value.isBlank()) {
                normalized.add(value);
            }
        }
        return normalized;
    }

    private boolean isExcluded(Path path, Set<String> excludeDirs) {
        for (Path part : path) {
            if (excludeDirs.contains(part.toString())) {
                return true;
            }
        }
        return false;
    }

    private Iterable<Path> iterable(Path root) throws IOException {
        return Files.walk(root).toList();
    }

    private String getExtension(String fileName) {
        int idx = fileName.lastIndexOf('.');
        if (idx < 0 || idx == fileName.length() - 1) {
            return "";
        }
        return fileName.substring(idx + 1).toLowerCase();
    }

    private String resolveLanguage(String ext) {
        return switch (ext) {
            case "java" -> "java";
            case "xml" -> "xml";
            case "yml", "yaml" -> "yaml";
            case "properties" -> "properties";
            case "md" -> "markdown";
            case "sql" -> "sql";
            case "ts" -> "typescript";
            case "tsx" -> "tsx";
            case "js" -> "javascript";
            default -> "text";
        };
    }

    private String sha256Base64(String content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(content.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (Exception e) {
            return "unknown";
        }
    }

    /**
     * Repo Index 请求体。
     */
    public static class RepoIndexRequest {
        /**
         * 项目ID（UUID字符串，可选）。
         */
        private String projectId;
        /**
         * 租户ID（UUID字符串，可选）。
         */
        private String tenantId;
        /**
         * 代码基准（commit/branch/快照标识）。
         */
        private String ref;
        /**
         * 扫描根目录（可选）。
         */
        private String rootPath;
        /**
         * 最大文件数（可选）。
         */
        private Integer maxFiles;
        /**
         * 最大文件字节数（可选）。
         */
        private Long maxFileBytes;
        /**
         * 允许的扩展名列表（可选）。
         */
        private List<String> includeExtensions;

        public String getProjectId() {
            return projectId;
        }

        public void setProjectId(String projectId) {
            this.projectId = projectId;
        }

        public String getTenantId() {
            return tenantId;
        }

        public void setTenantId(String tenantId) {
            this.tenantId = tenantId;
        }

        public String getRef() {
            return ref;
        }

        public void setRef(String ref) {
            this.ref = ref;
        }

        public String getRootPath() {
            return rootPath;
        }

        public void setRootPath(String rootPath) {
            this.rootPath = rootPath;
        }

        public Integer getMaxFiles() {
            return maxFiles;
        }

        public void setMaxFiles(Integer maxFiles) {
            this.maxFiles = maxFiles;
        }

        public Long getMaxFileBytes() {
            return maxFileBytes;
        }

        public void setMaxFileBytes(Long maxFileBytes) {
            this.maxFileBytes = maxFileBytes;
        }

        public List<String> getIncludeExtensions() {
            return includeExtensions;
        }

        public void setIncludeExtensions(List<String> includeExtensions) {
            this.includeExtensions = includeExtensions;
        }
    }

    /**
     * Repo Index 结果。
     */
    public static class RepoIndexResult {
        /**
         * 是否成功。
         */
        private boolean success;
        /**
         * 是否跳过执行。
         */
        private boolean skipped;
        /**
         * 处理文件数。
         */
        private int fileCount;
        /**
         * 跳过文件数。
         */
        private int skippedCount;
        /**
         * 写入切片数。
         */
        private int chunkCount;
        /**
         * 消息描述。
         */
        private String message;

        public static RepoIndexResult success(int fileCount, int skippedCount, int chunkCount) {
            RepoIndexResult result = new RepoIndexResult();
            result.success = true;
            result.skipped = false;
            result.fileCount = fileCount;
            result.skippedCount = skippedCount;
            result.chunkCount = chunkCount;
            result.message = "ok";
            return result;
        }

        public static RepoIndexResult failure(String message) {
            RepoIndexResult result = new RepoIndexResult();
            result.success = false;
            result.skipped = false;
            result.message = message;
            return result;
        }

        public static RepoIndexResult skipped(String message) {
            RepoIndexResult result = new RepoIndexResult();
            result.success = false;
            result.skipped = true;
            result.message = message;
            return result;
        }

        public boolean success() {
            return success;
        }

        public boolean skipped() {
            return skipped;
        }

        public int fileCount() {
            return fileCount;
        }

        public int skippedCount() {
            return skippedCount;
        }

        public int chunkCount() {
            return chunkCount;
        }

        public String message() {
            return message;
        }

        public boolean isSuccess() {
            return success;
        }

        public boolean isSkipped() {
            return skipped;
        }

        public int getFileCount() {
            return fileCount;
        }

        public int getSkippedCount() {
            return skippedCount;
        }

        public int getChunkCount() {
            return chunkCount;
        }

        public String getMessage() {
            return message;
        }
    }

    /**
     * 索引快照（仅在运行期用于去重）。
     */
    private static class RepoIndexSnapshot {
        private final long indexedAt;
        private final int chunkCount;

        RepoIndexSnapshot(long indexedAt, int chunkCount) {
            this.indexedAt = indexedAt;
            this.chunkCount = chunkCount;
        }

        public long getIndexedAt() {
            return indexedAt;
        }

        public int getChunkCount() {
            return chunkCount;
        }
    }
}
