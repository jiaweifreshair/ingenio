package com.ingenio.backend.service;

import java.io.InputStream;
import java.util.Map;

/**
 * MinIO对象存储Service接口
 * 提供文件上传、下载、删除等功能
 */
public interface MinioService {

    /**
     * 上传文件
     *
     * @param objectName 对象名称（文件路径）
     * @param inputStream 输入流
     * @param contentType 内容类型
     * @param size 文件大小
     * @return 文件访问URL
     */
    String uploadFile(String objectName, InputStream inputStream, String contentType, long size);

    /**
     * 上传文件（带元数据）
     *
     * @param objectName 对象名称（文件路径）
     * @param inputStream 输入流
     * @param contentType 内容类型
     * @param size 文件大小
     * @param metadata 自定义元数据
     * @return 文件访问URL
     */
    String uploadFile(String objectName, InputStream inputStream, String contentType, long size, Map<String, String> metadata);

    /**
     * 下载文件
     *
     * @param objectName 对象名称（文件路径）
     * @return 输入流
     */
    InputStream downloadFile(String objectName);

    /**
     * 生成预签名URL（用于临时访问）
     *
     * @param objectName 对象名称（文件路径）
     * @param expiry 过期时间（秒）
     * @return 预签名URL
     */
    String generatePresignedUrl(String objectName, int expiry);

    /**
     * 删除文件
     *
     * @param objectName 对象名称（文件路径）
     */
    void deleteFile(String objectName);

    /**
     * 检查文件是否存在
     *
     * @param objectName 对象名称（文件路径）
     * @return 是否存在
     */
    boolean fileExists(String objectName);

    /**
     * 获取文件元数据
     *
     * @param objectName 对象名称（文件路径）
     * @return 元数据
     */
    Map<String, String> getFileMetadata(String objectName);
}
