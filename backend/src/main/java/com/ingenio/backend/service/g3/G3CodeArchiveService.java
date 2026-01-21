package com.ingenio.backend.service.g3;

import com.ingenio.backend.entity.g3.G3ArtifactEntity;
import com.ingenio.backend.service.MinioService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * G3产物归档服务
 *
 * 用途：
 * - 将G3产物打包为ZIP并流式上传到MinIO；
 * - 生成归档清单（文件列表/大小/校验），用于版本快照回放与下载。
 *
 * 为什么需要：
 * - 禁止落地本地磁盘；
 * - 统一代码存储在MinIO，便于多端读取与回放。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class G3CodeArchiveService {

    /**
     * ZIP流式上传分片大小（8MB）
     * 说明：控制MinIO分片上传的单片大小，避免内存占用过大。
     */
    private static final long ZIP_PART_SIZE_BYTES = 8L * 1024 * 1024;

    /**
     * 管道缓冲区大小（64KB）
     * 说明：提升流式写入与上传的吞吐稳定性。
     */
    private static final int PIPE_BUFFER_BYTES = 64 * 1024;

    private final MinioService minioService;

    /**
     * 构建归档清单并上传ZIP
     *
     * @param tenantId 租户ID
     * @param userId 用户ID
     * @param taskId 任务ID（等同G3 jobId）
     * @param versionNumber 版本号（用于存储路径）
     * @param artifacts 产物列表（建议传最新版本）
     * @return 归档结果（包含清单与上传信息）
     */
    public ArchiveBuildResult buildAndUploadArchive(
            UUID tenantId,
            UUID userId,
            UUID taskId,
            int versionNumber,
            List<G3ArtifactEntity> artifacts
    ) {
        List<G3ArtifactEntity> safeArtifacts = sanitizeArtifacts(artifacts);
        ArchiveManifest manifest = buildManifest(safeArtifacts);

        if (manifest.getFileCount() == 0) {
            return ArchiveBuildResult.failed(manifest, "产物为空，无法归档");
        }

        String storageKey = buildStorageKey(tenantId, userId, taskId, versionNumber);
        Map<String, String> metadata = buildMetadata(tenantId, userId, taskId, versionNumber, manifest);

        try {
            ZipStreamResult zipResult = streamZipToMinio(storageKey, safeArtifacts, metadata);
            return ArchiveBuildResult.success(manifest, storageKey, zipResult.zipSizeBytes, zipResult.zipChecksum);
        } catch (Exception e) {
            log.warn("G3归档上传失败: taskId={}, version={}, error={}", taskId, versionNumber, e.getMessage());
            return ArchiveBuildResult.failed(manifest, e.getMessage());
        }
    }

    /**
     * 归档清单
     *
     * 说明：
     * - 描述本次归档包含哪些文件及其大小/校验；
     * - 供版本快照与回放查询使用。
     */
    public static class ArchiveManifest {
        private final List<ArchiveFileEntry> files;
        private final int fileCount;
        private final long totalSizeBytes;
        private final Instant generatedAt;

        public ArchiveManifest(List<ArchiveFileEntry> files, long totalSizeBytes, Instant generatedAt) {
            this.files = files;
            this.fileCount = files.size();
            this.totalSizeBytes = totalSizeBytes;
            this.generatedAt = generatedAt;
        }

        public List<ArchiveFileEntry> getFiles() {
            return files;
        }

        public int getFileCount() {
            return fileCount;
        }

        public long getTotalSizeBytes() {
            return totalSizeBytes;
        }

        public Instant getGeneratedAt() {
            return generatedAt;
        }

        /**
         * 转换为快照存储格式（Map结构，便于JSON序列化）
         *
         * @return 清单快照Map
         */
        public Map<String, Object> toSnapshotMap() {
            Map<String, Object> manifest = new HashMap<>();
            manifest.put("file_count", fileCount);
            manifest.put("total_size_bytes", totalSizeBytes);
            manifest.put("generated_at", generatedAt.toString());

            List<Map<String, Object>> fileEntries = new ArrayList<>();
            for (ArchiveFileEntry entry : files) {
                Map<String, Object> item = new HashMap<>();
                item.put("path", entry.getPath());
                item.put("file_name", entry.getFileName());
                item.put("artifact_type", entry.getArtifactType());
                item.put("language", entry.getLanguage());
                item.put("generated_by", entry.getGeneratedBy());
                item.put("generation_round", entry.getGenerationRound());
                item.put("version", entry.getVersion());
                item.put("size_bytes", entry.getSizeBytes());
                item.put("checksum", entry.getChecksum());
                fileEntries.add(item);
            }

            manifest.put("files", fileEntries);
            return manifest;
        }
    }

    /**
     * 归档文件条目
     *
     * 说明：
     * - 描述单个文件的路径/大小/校验；
     * - 用于回放与差异比对。
     */
    public static class ArchiveFileEntry {
        private final String path;
        private final String fileName;
        private final String artifactType;
        private final String language;
        private final String generatedBy;
        private final Integer generationRound;
        private final Integer version;
        private final long sizeBytes;
        private final String checksum;

        public ArchiveFileEntry(String path, String fileName, String artifactType, String language, String generatedBy,
                Integer generationRound, Integer version, long sizeBytes, String checksum) {
            this.path = path;
            this.fileName = fileName;
            this.artifactType = artifactType;
            this.language = language;
            this.generatedBy = generatedBy;
            this.generationRound = generationRound;
            this.version = version;
            this.sizeBytes = sizeBytes;
            this.checksum = checksum;
        }

        public String getPath() {
            return path;
        }

        public String getFileName() {
            return fileName;
        }

        public String getArtifactType() {
            return artifactType;
        }

        public String getLanguage() {
            return language;
        }

        public String getGeneratedBy() {
            return generatedBy;
        }

        public Integer getGenerationRound() {
            return generationRound;
        }

        public Integer getVersion() {
            return version;
        }

        public long getSizeBytes() {
            return sizeBytes;
        }

        public String getChecksum() {
            return checksum;
        }
    }

    /**
     * 归档构建结果
     *
     * 说明：
     * - 统一返回归档清单与上传结果；
     * - 归档失败时保留清单并记录错误原因。
     */
    public static class ArchiveBuildResult {
        private final boolean success;
        private final String storageKey;
        private final long zipSizeBytes;
        private final String zipChecksum;
        private final String errorMessage;
        private final ArchiveManifest manifest;
        private final Instant archivedAt;

        private ArchiveBuildResult(boolean success, String storageKey, long zipSizeBytes, String zipChecksum,
                String errorMessage, ArchiveManifest manifest, Instant archivedAt) {
            this.success = success;
            this.storageKey = storageKey;
            this.zipSizeBytes = zipSizeBytes;
            this.zipChecksum = zipChecksum;
            this.errorMessage = errorMessage;
            this.manifest = manifest;
            this.archivedAt = archivedAt;
        }

        public static ArchiveBuildResult success(ArchiveManifest manifest, String storageKey, long zipSizeBytes,
                String zipChecksum) {
            return new ArchiveBuildResult(true, storageKey, zipSizeBytes, zipChecksum, null, manifest, Instant.now());
        }

        public static ArchiveBuildResult failed(ArchiveManifest manifest, String errorMessage) {
            return new ArchiveBuildResult(false, null, 0L, null, errorMessage, manifest, Instant.now());
        }

        public boolean isSuccess() {
            return success;
        }

        public String getStorageKey() {
            return storageKey;
        }

        public long getZipSizeBytes() {
            return zipSizeBytes;
        }

        public String getZipChecksum() {
            return zipChecksum;
        }

        public String getErrorMessage() {
            return errorMessage;
        }

        public ArchiveManifest getManifest() {
            return manifest;
        }

        public Instant getArchivedAt() {
            return archivedAt;
        }

        /**
         * 转换为归档元数据Map（用于写入版本快照）
         *
         * @return 归档元数据Map
         */
        public Map<String, Object> toArchiveMetadata() {
            Map<String, Object> archive = new HashMap<>();
            archive.put("status", success ? "success" : "failed");
            archive.put("storage_key", storageKey);
            archive.put("zip_size_bytes", zipSizeBytes);
            archive.put("zip_checksum", zipChecksum);
            archive.put("archived_at", archivedAt.toString());
            if (!success) {
                archive.put("error_message", errorMessage);
            }
            return archive;
        }
    }

    /**
     * ZIP写入结果（用于返回ZIP大小与校验）
     *
     * 说明：
     * - 归档上传过程中无法提前得知ZIP大小；
     * - 通过计数与Digest输出得到最终结果。
     */
    private static class ZipStreamResult {
        private final long zipSizeBytes;
        private final String zipChecksum;

        private ZipStreamResult(long zipSizeBytes, String zipChecksum) {
            this.zipSizeBytes = zipSizeBytes;
            this.zipChecksum = zipChecksum;
        }
    }

    /**
     * 过滤无效产物，避免归档失败
     *
     * @param artifacts 原始产物列表
     * @return 可归档的产物列表
     */
    private List<G3ArtifactEntity> sanitizeArtifacts(List<G3ArtifactEntity> artifacts) {
        if (artifacts == null || artifacts.isEmpty()) {
            return List.of();
        }
        List<G3ArtifactEntity> result = new ArrayList<>();
        for (G3ArtifactEntity artifact : artifacts) {
            if (artifact == null || artifact.getFilePath() == null || artifact.getFilePath().isBlank()) {
                continue;
            }
            result.add(artifact);
        }
        return result;
    }

    /**
     * 构建归档清单
     *
     * @param artifacts 产物列表
     * @return 归档清单
     */
    private ArchiveManifest buildManifest(List<G3ArtifactEntity> artifacts) {
        List<ArchiveFileEntry> files = new ArrayList<>();
        long totalSizeBytes = 0L;

        for (G3ArtifactEntity artifact : artifacts) {
            String content = artifact.getContent() == null ? "" : artifact.getContent();
            byte[] contentBytes = content.getBytes(StandardCharsets.UTF_8);
            long size = contentBytes.length;
            totalSizeBytes += size;

            String checksum = artifact.getChecksum();
            if (checksum == null || checksum.isBlank()) {
                checksum = sha256Hex(contentBytes);
            }

            files.add(new ArchiveFileEntry(
                    artifact.getFilePath(),
                    artifact.getFileName(),
                    artifact.getArtifactType(),
                    artifact.getLanguage(),
                    artifact.getGeneratedBy(),
                    artifact.getGenerationRound(),
                    artifact.getVersion(),
                    size,
                    checksum
            ));
        }

        return new ArchiveManifest(files, totalSizeBytes, Instant.now());
    }

    /**
     * 构建MinIO存储路径
     *
     * @param tenantId 租户ID
     * @param userId 用户ID
     * @param taskId 任务ID
     * @param versionNumber 版本号
     * @return 存储路径
     */
    private String buildStorageKey(UUID tenantId, UUID userId, UUID taskId, int versionNumber) {
        String tenant = tenantId != null ? tenantId.toString() : "unknown-tenant";
        String user = userId != null ? userId.toString() : "unknown-user";
        String task = taskId != null ? taskId.toString() : "unknown-task";
        return String.format("g3/snapshots/%s/%s/%s/v%d/code.zip", tenant, user, task, versionNumber);
    }

    /**
     * 构建归档元数据
     *
     * @param tenantId 租户ID
     * @param userId 用户ID
     * @param taskId 任务ID
     * @param versionNumber 版本号
     * @param manifest 归档清单
     * @return 元数据Map
     */
    private Map<String, String> buildMetadata(UUID tenantId, UUID userId, UUID taskId, int versionNumber,
            ArchiveManifest manifest) {
        Map<String, String> metadata = new HashMap<>();
        metadata.put("tenant-id", tenantId != null ? tenantId.toString() : "");
        metadata.put("user-id", userId != null ? userId.toString() : "");
        metadata.put("task-id", taskId != null ? taskId.toString() : "");
        metadata.put("version-number", String.valueOf(versionNumber));
        metadata.put("file-count", String.valueOf(manifest.getFileCount()));
        metadata.put("total-size-bytes", String.valueOf(manifest.getTotalSizeBytes()));
        metadata.put("generated-at", manifest.getGeneratedAt().toString());
        return metadata;
    }

    /**
     * 流式打包ZIP并上传到MinIO
     *
     * @param storageKey MinIO对象路径
     * @param artifacts 产物列表
     * @param metadata 上传元数据
     * @return ZIP写入结果
     */
    private ZipStreamResult streamZipToMinio(String storageKey, List<G3ArtifactEntity> artifacts,
            Map<String, String> metadata) throws IOException {
        PipedOutputStream pipedOut = new PipedOutputStream();
        PipedInputStream pipedIn = new PipedInputStream(pipedOut, PIPE_BUFFER_BYTES);

        AtomicReference<Exception> writeError = new AtomicReference<>();
        AtomicLong zipSizeBytes = new AtomicLong();
        AtomicReference<String> zipChecksum = new AtomicReference<>();

        Thread writer = new Thread(() -> {
            try {
                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                CountingOutputStream counting = new CountingOutputStream(pipedOut);
                DigestOutputStream digestOut = new DigestOutputStream(counting, digest);

                try (ZipOutputStream zipOut = new ZipOutputStream(new BufferedOutputStream(digestOut))) {
                    Set<String> addedPaths = new HashSet<>();
                    for (G3ArtifactEntity artifact : artifacts) {
                        String rawPath = artifact.getFilePath();
                        if (rawPath == null || rawPath.isBlank()) {
                            continue;
                        }
                        String entryPath = rawPath.startsWith("/") ? rawPath.substring(1) : rawPath;
                        if (addedPaths.contains(entryPath)) {
                            continue;
                        }
                        addedPaths.add(entryPath);

                        ZipEntry entry = new ZipEntry(entryPath);
                        zipOut.putNextEntry(entry);

                        String content = artifact.getContent() == null ? "" : artifact.getContent();
                        zipOut.write(content.getBytes(StandardCharsets.UTF_8));
                        zipOut.closeEntry();
                    }
                }

                zipSizeBytes.set(counting.getCount());
                zipChecksum.set(toHex(digest.digest()));
            } catch (Exception e) {
                writeError.set(e);
            } finally {
                try {
                    pipedOut.close();
                } catch (IOException ignored) {
                    // ignore
                }
            }
        }, "g3-archive-writer-" + storageKey.hashCode());

        writer.start();

        try (InputStream inputStream = pipedIn) {
            minioService.uploadStream(storageKey, inputStream, "application/zip", ZIP_PART_SIZE_BYTES, metadata);
        } finally {
            try {
                writer.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        if (writeError.get() != null) {
            throw new IOException("归档写入失败: " + writeError.get().getMessage(), writeError.get());
        }

        return new ZipStreamResult(zipSizeBytes.get(), zipChecksum.get());
    }

    /**
     * 计算SHA-256并输出16进制字符串
     *
     * @param contentBytes 内容字节
     * @return SHA-256十六进制
     */
    private String sha256Hex(byte[] contentBytes) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(contentBytes);
            return toHex(hash);
        } catch (Exception e) {
            return "";
        }
    }

    /**
     * 将字节数组转换为十六进制字符串
     *
     * @param bytes 字节数组
     * @return 十六进制字符串
     */
    private String toHex(byte[] bytes) {
        StringBuilder builder = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            builder.append(String.format(Locale.ROOT, "%02x", b));
        }
        return builder.toString();
    }

    /**
     * 计数输出流
     *
     * 说明：
     * - 记录流式写入的总字节数；
     * - 用于统计ZIP大小。
     */
    private static class CountingOutputStream extends OutputStream {
        private final OutputStream delegate;
        private long count = 0L;

        CountingOutputStream(OutputStream delegate) {
            this.delegate = delegate;
        }

        @Override
        public void write(int b) throws IOException {
            delegate.write(b);
            count += 1;
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            delegate.write(b, off, len);
            count += len;
        }

        @Override
        public void flush() throws IOException {
            delegate.flush();
        }

        @Override
        public void close() throws IOException {
            delegate.close();
        }

        public long getCount() {
            return count;
        }
    }
}
