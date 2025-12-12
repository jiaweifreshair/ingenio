package com.ingenio.backend.common.util;

import lombok.extern.slf4j.Slf4j;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

/**
 * 文件上传工具类
 * 提供文件命名、路径生成等辅助功能
 */
@Slf4j
public class FileUploadUtil {

    /**
     * 日期格式化器（用于目录结构）
     */
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy/MM/dd");

    /**
     * 生成文件存储路径
     * 格式: {category}/{yyyy}/{MM}/{dd}/{uuid}.{extension}
     *
     * @param category 文件分类（如: images, audio）
     * @param originalFilename 原始文件名
     * @return 存储路径
     */
    public static String generateObjectName(String category, String originalFilename) {
        // 获取当前日期路径
        String datePath = LocalDate.now().format(DATE_FORMATTER);
        
        // 生成UUID文件名
        String uuid = UUID.randomUUID().toString().replace("-", "");
        
        // 获取文件扩展名
        String extension = getFileExtension(originalFilename);
        String filename = extension != null ? uuid + "." + extension : uuid;
        
        // 拼接完整路径
        String objectName = String.format("%s/%s/%s", category, datePath, filename);
        
        log.debug("生成对象存储路径: originalFilename={}, objectName={}", originalFilename, objectName);
        return objectName;
    }

    /**
     * 生成图片存储路径
     *
     * @param originalFilename 原始文件名
     * @return 存储路径
     */
    public static String generateImagePath(String originalFilename) {
        return generateObjectName("images", originalFilename);
    }

    /**
     * 生成音频存储路径
     *
     * @param originalFilename 原始文件名
     * @return 存储路径
     */
    public static String generateAudioPath(String originalFilename) {
        return generateObjectName("audio", originalFilename);
    }

    /**
     * 获取文件扩展名（不含点）
     *
     * @param filename 文件名
     * @return 扩展名
     */
    public static String getFileExtension(String filename) {
        if (filename == null || filename.isEmpty()) {
            return null;
        }

        int lastDotIndex = filename.lastIndexOf('.');
        if (lastDotIndex == -1 || lastDotIndex == filename.length() - 1) {
            return null;
        }

        return filename.substring(lastDotIndex + 1).toLowerCase();
    }

    /**
     * 格式化文件大小（字节转可读格式）
     *
     * @param bytes 文件大小（字节）
     * @return 可读格式（如: 1.5MB）
     */
    public static String formatFileSize(long bytes) {
        if (bytes < 1024) {
            return bytes + "B";
        }
        
        double kb = bytes / 1024.0;
        if (kb < 1024) {
            return String.format("%.2fKB", kb);
        }
        
        double mb = kb / 1024.0;
        if (mb < 1024) {
            return String.format("%.2fMB", mb);
        }
        
        double gb = mb / 1024.0;
        return String.format("%.2fGB", gb);
    }
}
