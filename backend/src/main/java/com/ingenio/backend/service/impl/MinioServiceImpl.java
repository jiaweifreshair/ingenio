package com.ingenio.backend.service.impl;

import com.ingenio.backend.common.exception.BusinessException;
import com.ingenio.backend.common.exception.ErrorCode;
import com.ingenio.backend.config.MinioConfig;
import com.ingenio.backend.service.MinioService;
import io.minio.*;
import io.minio.http.Method;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * MinIO对象存储Service实现类
 * 提供完整的MinIO文件操作功能
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MinioServiceImpl implements MinioService {

    /**
     * MinIO分片上传最小分片大小（5MB）
     * 说明：MinIO要求分片上传最小5MB，这里统一兜底避免配置错误。
     */
    private static final long MIN_PART_SIZE_BYTES = 5L * 1024 * 1024;

    private final MinioClient minioClient;
    private final MinioConfig minioConfig;

    /**
     * 初始化方法
     * 确保bucket存在，如果不存在则自动创建
     */
    @PostConstruct
    public void init() {
        if (!minioConfig.isAutoCreateBucket()) {
            log.info("MinIO自动创建bucket已禁用");
            return;
        }

        try {
            String bucketName = minioConfig.getBucketName();
            boolean bucketExists = minioClient.bucketExists(
                    BucketExistsArgs.builder()
                            .bucket(bucketName)
                            .build()
            );

            if (!bucketExists) {
                minioClient.makeBucket(
                        MakeBucketArgs.builder()
                                .bucket(bucketName)
                                .build()
                );
                log.info("✅ MinIO bucket自动创建成功: {}", bucketName);
            } else {
                log.info("✅ MinIO bucket已存在: {}", bucketName);
            }
        } catch (Exception e) {
            log.error("❌ MinIO bucket初始化失败", e);
            throw new RuntimeException("MinIO bucket初始化失败", e);
        }
    }

    /**
     * 上传文件
     *
     * @param objectName 对象名称（文件路径）
     * @param inputStream 输入流
     * @param contentType 内容类型
     * @param size 文件大小
     * @return 文件访问URL
     * @throws BusinessException 当上传失败时抛出
     */
    @Override
    public String uploadFile(String objectName, InputStream inputStream, String contentType, long size) {
        return uploadFile(objectName, inputStream, contentType, size, new HashMap<>());
    }

    /**
     * 上传文件（带元数据）
     *
     * @param objectName 对象名称（文件路径）
     * @param inputStream 输入流
     * @param contentType 内容类型
     * @param size 文件大小
     * @param metadata 自定义元数据
     * @return 文件访问URL
     * @throws BusinessException 当上传失败时抛出
     */
    @Override
    public String uploadFile(String objectName, InputStream inputStream, String contentType, long size, Map<String, String> metadata) {
        if (objectName == null || objectName.isEmpty()) {
            log.error("上传文件失败: objectName不能为空");
            throw new BusinessException(ErrorCode.PARAM_ERROR, "文件名不能为空");
        }

        if (inputStream == null) {
            log.error("上传文件失败: inputStream不能为空");
            throw new BusinessException(ErrorCode.PARAM_ERROR, "文件内容不能为空");
        }

        try {
            // 构建上传参数
            PutObjectArgs.Builder builder = PutObjectArgs.builder()
                    .bucket(minioConfig.getBucketName())
                    .object(objectName)
                    .stream(inputStream, size, -1)
                    .contentType(contentType);

            // 添加自定义元数据
            if (metadata != null && !metadata.isEmpty()) {
                builder.userMetadata(metadata);
            }

            // 执行上传
            minioClient.putObject(builder.build());

            // 生成文件访问URL
            String fileUrl = String.format("%s/%s/%s",
                    minioConfig.getEndpoint(),
                    minioConfig.getBucketName(),
                    objectName);

            log.info("✅ 上传文件成功: objectName={}, size={}, contentType={}",
                    objectName, size, contentType);
            return fileUrl;

        } catch (Exception e) {
            log.error("❌ 上传文件失败: objectName={}, error={}", objectName, e.getMessage(), e);
            throw new BusinessException(ErrorCode.STORAGE_UPLOAD_FAILED, "文件上传失败: " + e.getMessage());
        }
    }

    /**
     * 流式上传文件（不需要提前知道文件总大小）
     *
     * @param objectName 对象名称（文件路径）
     * @param inputStream 输入流
     * @param contentType 内容类型
     * @param partSize 分片大小（字节）
     * @param metadata 自定义元数据
     * @return 文件访问URL
     * @throws BusinessException 当上传失败时抛出
     */
    @Override
    public String uploadStream(String objectName, InputStream inputStream, String contentType, long partSize,
            Map<String, String> metadata) {
        if (objectName == null || objectName.isEmpty()) {
            log.error("流式上传失败: objectName不能为空");
            throw new BusinessException(ErrorCode.PARAM_ERROR, "文件名不能为空");
        }
        if (inputStream == null) {
            log.error("流式上传失败: inputStream不能为空");
            throw new BusinessException(ErrorCode.PARAM_ERROR, "文件内容不能为空");
        }

        long effectivePartSize = Math.max(partSize, MIN_PART_SIZE_BYTES);

        try {
            PutObjectArgs.Builder builder = PutObjectArgs.builder()
                    .bucket(minioConfig.getBucketName())
                    .object(objectName)
                    .stream(inputStream, -1, effectivePartSize)
                    .contentType(contentType);

            if (metadata != null && !metadata.isEmpty()) {
                builder.userMetadata(metadata);
            }

            minioClient.putObject(builder.build());

            String fileUrl = String.format("%s/%s/%s",
                    minioConfig.getEndpoint(),
                    minioConfig.getBucketName(),
                    objectName);

            log.info("✅ 流式上传成功: objectName={}, partSize={}bytes", objectName, effectivePartSize);
            return fileUrl;
        } catch (Exception e) {
            log.error("❌ 流式上传失败: objectName={}, error={}", objectName, e.getMessage(), e);
            throw new BusinessException(ErrorCode.STORAGE_UPLOAD_FAILED, "文件上传失败: " + e.getMessage());
        }
    }

    /**
     * 下载文件
     *
     * @param objectName 对象名称（文件路径）
     * @return 输入流
     * @throws BusinessException 当下载失败时抛出
     */
    @Override
    public InputStream downloadFile(String objectName) {
        if (objectName == null || objectName.isEmpty()) {
            log.error("下载文件失败: objectName不能为空");
            throw new BusinessException(ErrorCode.PARAM_ERROR, "文件名不能为空");
        }

        try {
            InputStream inputStream = minioClient.getObject(
                    GetObjectArgs.builder()
                            .bucket(minioConfig.getBucketName())
                            .object(objectName)
                            .build()
            );

            log.info("✅ 下载文件成功: objectName={}", objectName);
            return inputStream;

        } catch (Exception e) {
            log.error("❌ 下载文件失败: objectName={}, error={}", objectName, e.getMessage(), e);
            throw new BusinessException(ErrorCode.STORAGE_DOWNLOAD_FAILED, "文件下载失败: " + e.getMessage());
        }
    }

    /**
     * 生成预签名URL（用于临时访问）
     *
     * @param objectName 对象名称（文件路径）
     * @param expiry 过期时间（秒）
     * @return 预签名URL
     * @throws BusinessException 当生成失败时抛出
     */
    @Override
    public String generatePresignedUrl(String objectName, int expiry) {
        if (objectName == null || objectName.isEmpty()) {
            log.error("生成预签名URL失败: objectName不能为空");
            throw new BusinessException(ErrorCode.PARAM_ERROR, "文件名不能为空");
        }

        if (expiry <= 0) {
            expiry = 7 * 24 * 60 * 60; // 默认7天
        }

        try {
            String presignedUrl = minioClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .method(Method.GET)
                            .bucket(minioConfig.getBucketName())
                            .object(objectName)
                            .expiry(expiry, TimeUnit.SECONDS)
                            .build()
            );

            log.info("✅ 生成预签名URL成功: objectName={}, expiry={}s", objectName, expiry);
            return presignedUrl;

        } catch (Exception e) {
            log.error("❌ 生成预签名URL失败: objectName={}, error={}", objectName, e.getMessage(), e);
            throw new BusinessException(ErrorCode.STORAGE_DOWNLOAD_FAILED, "生成预签名URL失败: " + e.getMessage());
        }
    }

    /**
     * 删除文件
     *
     * @param objectName 对象名称（文件路径）
     * @throws BusinessException 当删除失败时抛出
     */
    @Override
    public void deleteFile(String objectName) {
        if (objectName == null || objectName.isEmpty()) {
            log.error("删除文件失败: objectName不能为空");
            throw new BusinessException(ErrorCode.PARAM_ERROR, "文件名不能为空");
        }

        try {
            minioClient.removeObject(
                    RemoveObjectArgs.builder()
                            .bucket(minioConfig.getBucketName())
                            .object(objectName)
                            .build()
            );

            log.info("✅ 删除文件成功: objectName={}", objectName);

        } catch (Exception e) {
            log.error("❌ 删除文件失败: objectName={}, error={}", objectName, e.getMessage(), e);
            throw new BusinessException(ErrorCode.STORAGE_NOT_FOUND, "文件删除失败: " + e.getMessage());
        }
    }

    /**
     * 检查文件是否存在
     *
     * @param objectName 对象名称（文件路径）
     * @return 是否存在
     */
    @Override
    public boolean fileExists(String objectName) {
        if (objectName == null || objectName.isEmpty()) {
            return false;
        }

        try {
            minioClient.statObject(
                    StatObjectArgs.builder()
                            .bucket(minioConfig.getBucketName())
                            .object(objectName)
                            .build()
            );
            return true;

        } catch (Exception e) {
            log.debug("文件不存在: objectName={}", objectName);
            return false;
        }
    }

    /**
     * 获取文件元数据
     *
     * @param objectName 对象名称（文件路径）
     * @return 元数据
     * @throws BusinessException 当获取失败时抛出
     */
    @Override
    public Map<String, String> getFileMetadata(String objectName) {
        if (objectName == null || objectName.isEmpty()) {
            log.error("获取文件元数据失败: objectName不能为空");
            throw new BusinessException(ErrorCode.PARAM_ERROR, "文件名不能为空");
        }

        try {
            StatObjectResponse statObjectResponse = minioClient.statObject(
                    StatObjectArgs.builder()
                            .bucket(minioConfig.getBucketName())
                            .object(objectName)
                            .build()
            );

            Map<String, String> metadata = new HashMap<>();
            metadata.put("contentType", statObjectResponse.contentType());
            metadata.put("size", String.valueOf(statObjectResponse.size()));
            metadata.put("etag", statObjectResponse.etag());
            metadata.put("lastModified", statObjectResponse.lastModified().toString());

            // 添加用户自定义元数据
            if (statObjectResponse.userMetadata() != null) {
                metadata.putAll(statObjectResponse.userMetadata());
            }

            log.info("✅ 获取文件元数据成功: objectName={}", objectName);
            return metadata;

        } catch (Exception e) {
            log.error("❌ 获取文件元数据失败: objectName={}, error={}", objectName, e.getMessage(), e);
            throw new BusinessException(ErrorCode.STORAGE_NOT_FOUND, "获取文件元数据失败: " + e.getMessage());
        }
    }
}
