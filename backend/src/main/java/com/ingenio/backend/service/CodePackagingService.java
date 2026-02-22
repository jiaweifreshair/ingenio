package com.ingenio.backend.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * 代码打包服务
 * 负责将生成的代码文件打包为ZIP并上传到MinIO
 *
 * 功能：
 * - 将Map<String, String>格式的文件打包为ZIP
 * - 创建临时目录并写入文件
 * - 压缩为ZIP格式
 * - 上传到MinIO对象存储
 * - 清理临时文件
 *
 * @author Ingenio Team
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CodePackagingService {

    private final MinioService minioService;

    /**
     * 默认预签名下载链接有效期（秒）
     *
     * 是什么：MinIO 产物下载链接的默认有效期。
     * 做什么：为打包产物生成 presigned URL，供浏览器直接下载。
     * 为什么：MinIO 默认 bucket 通常为私有直链会 403（AccessDenied），必须用预签名 URL 才能访问。
     */
    private static final int DEFAULT_PRESIGNED_URL_EXPIRY_SECONDS = 24 * 60 * 60;

    /**
     * 打包代码文件并上传到MinIO
     *
     * @param files 文件Map，key为文件路径，value为文件内容
     * @param projectName 项目名称（用于ZIP文件命名）
     * @return MinIO下载URL
     * @throws IOException 当文件操作失败时抛出
     */
    public String packageAndUpload(Map<String, String> files, String projectName) throws IOException {
        log.info("开始打包代码文件: projectName={}, filesCount={}", projectName, files.size());

        // 1. 创建临时目录
        Path tempDir = Files.createTempDirectory("ingenio-code-");
        log.debug("创建临时目录: {}", tempDir);

        try {
            // 2. 写入所有文件到临时目录
            writeFilesToDirectory(files, tempDir);

            // 3. 创建ZIP文件
            String zipFileName = generateZipFileName(projectName);
            Path zipPath = createZipFile(tempDir, zipFileName);

            // 4. 上传到MinIO
            String downloadUrl = uploadToMinio(zipPath, zipFileName);

            log.info("代码打包完成: projectName={}, url={}", projectName, downloadUrl);
            return downloadUrl;

        } finally {
            // 5. 清理临时文件
            cleanupTempFiles(tempDir);
        }
    }

    /**
     * 将文件Map写入临时目录
     *
     * @param files 文件Map
     * @param baseDir 基础目录
     * @throws IOException 当文件写入失败时抛出
     */
    private void writeFilesToDirectory(Map<String, String> files, Path baseDir) throws IOException {
        for (Map.Entry<String, String> entry : files.entrySet()) {
            String filePath = entry.getKey();
            String content = entry.getValue();

            // 创建完整的文件路径
            Path fullPath = baseDir.resolve(filePath);

            // 确保父目录存在
            Files.createDirectories(fullPath.getParent());

            // 写入文件内容
            Files.writeString(fullPath, content, StandardCharsets.UTF_8);

            log.debug("写入文件: {}", filePath);
        }

        log.info("所有文件写入完成: count={}", files.size());
    }

    /**
     * 创建ZIP文件
     *
     * @param sourceDir 源目录
     * @param zipFileName ZIP文件名
     * @return ZIP文件路径
     * @throws IOException 当ZIP创建失败时抛出
     */
    private Path createZipFile(Path sourceDir, String zipFileName) throws IOException {
        Path zipPath = sourceDir.getParent().resolve(zipFileName);

        log.debug("开始创建ZIP: source={}, zip={}", sourceDir, zipPath);

        try (ZipOutputStream zipOut = new ZipOutputStream(
                new BufferedOutputStream(Files.newOutputStream(zipPath)))) {

            // 递归添加目录中的所有文件
            addDirectoryToZip(sourceDir, sourceDir, zipOut);
        }

        // 获取ZIP文件大小
        long zipSize = Files.size(zipPath);
        log.info("ZIP创建成功: path={}, size={}bytes", zipPath, zipSize);

        return zipPath;
    }

    /**
     * 递归添加目录到ZIP
     *
     * @param baseDir 基础目录
     * @param currentDir 当前目录
     * @param zipOut ZIP输出流
     * @throws IOException 当添加失败时抛出
     */
    private void addDirectoryToZip(Path baseDir, Path currentDir, ZipOutputStream zipOut) throws IOException {
        File[] files = currentDir.toFile().listFiles();
        if (files == null || files.length == 0) {
            return;
        }

        for (File file : files) {
            if (file.isDirectory()) {
                // 递归处理子目录
                addDirectoryToZip(baseDir, file.toPath(), zipOut);
            } else {
                // 添加文件到ZIP
                Path relativePath = baseDir.relativize(file.toPath());
                ZipEntry zipEntry = new ZipEntry(relativePath.toString().replace('\\', '/'));
                zipOut.putNextEntry(zipEntry);

                // 写入文件内容
                try (InputStream in = new BufferedInputStream(new FileInputStream(file))) {
                    byte[] buffer = new byte[8192];
                    int bytesRead;
                    while ((bytesRead = in.read(buffer)) != -1) {
                        zipOut.write(buffer, 0, bytesRead);
                    }
                }

                zipOut.closeEntry();
                log.debug("添加文件到ZIP: {}", relativePath);
            }
        }
    }

    /**
     * 上传ZIP到MinIO
     *
     * @param zipPath ZIP文件路径
     * @param zipFileName ZIP文件名
     * @return 下载URL
     * @throws IOException 当上传失败时抛出
     */
    private String uploadToMinio(Path zipPath, String zipFileName) throws IOException {
        log.debug("开始上传ZIP到MinIO: file={}", zipFileName);

        String objectName = "generated-code/" + zipFileName;
        long fileSize = Files.size(zipPath);

        try (InputStream inputStream = new BufferedInputStream(Files.newInputStream(zipPath))) {
            String rawUrl = minioService.uploadFile(
                    objectName,
                    inputStream,
                    "application/zip",
                    fileSize
            );

            // 返回可直接访问的预签名URL，避免浏览器直链访问 MinIO 私有对象时 403
            String presignedUrl = minioService.generatePresignedUrl(objectName, DEFAULT_PRESIGNED_URL_EXPIRY_SECONDS);

            log.info("上传MinIO成功: objectName={}, rawUrl={}, presignedUrl={}", objectName, rawUrl, presignedUrl);
            return presignedUrl;
        }
    }

    /**
     * 清理临时文件
     *
     * @param tempDir 临时目录
     */
    private void cleanupTempFiles(Path tempDir) {
        try {
            if (Files.exists(tempDir)) {
                // 递归删除目录及其内容
                deleteDirectory(tempDir.toFile());
                log.debug("临时文件清理完成: {}", tempDir);
            }
        } catch (Exception e) {
            log.warn("临时文件清理失败: {}", tempDir, e);
        }
    }

    /**
     * 递归删除目录
     *
     * @param dir 目录
     */
    private void deleteDirectory(File dir) {
        File[] files = dir.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    deleteDirectory(file);
                } else {
                    file.delete();
                }
            }
        }
        dir.delete();
    }

    /**
     * 生成ZIP文件名
     *
     * @param projectName 项目名称
     * @return ZIP文件名
     */
    private String generateZipFileName(String projectName) {
        // 清理项目名称（移除非法字符）
        String cleanName = projectName.replaceAll("[^a-zA-Z0-9-_]", "_");

        // 生成唯一文件名
        String timestamp = String.valueOf(System.currentTimeMillis());
        String uuid = UUID.randomUUID().toString().substring(0, 8);

        return String.format("%s-%s-%s.zip", cleanName, timestamp, uuid);
    }

    /**
     * 计算文件统计信息
     *
     * @param files 文件Map
     * @return 统计信息
     */
    public FileStatistics calculateStatistics(Map<String, String> files) {
        int totalFiles = files.size();
        long totalSize = files.values().stream()
                .mapToLong(content -> content.getBytes(StandardCharsets.UTF_8).length)
                .sum();

        // 按文件类型分类统计
        int kotlinFiles = (int) files.keySet().stream()
                .filter(path -> path.endsWith(".kt"))
                .count();

        int sqlFiles = (int) files.keySet().stream()
                .filter(path -> path.endsWith(".sql"))
                .count();

        int configFiles = (int) files.keySet().stream()
                .filter(path -> path.endsWith(".properties") || path.endsWith(".env") || path.endsWith(".gradle"))
                .count();

        int documentFiles = (int) files.keySet().stream()
                .filter(path -> path.endsWith(".md") || path.endsWith(".txt"))
                .count();

        return FileStatistics.builder()
                .totalFiles(totalFiles)
                .totalSize(totalSize)
                .kotlinFiles(kotlinFiles)
                .sqlFiles(sqlFiles)
                .configFiles(configFiles)
                .documentFiles(documentFiles)
                .build();
    }

    /**
     * 文件统计信息
     */
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class FileStatistics {
        private Integer totalFiles;
        private Long totalSize;
        private Integer kotlinFiles;
        private Integer sqlFiles;
        private Integer configFiles;
        private Integer documentFiles;
    }
}
