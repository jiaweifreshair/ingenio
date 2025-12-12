package com.ingenio.backend.common.util;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * 文件类型验证器
 * 用于验证上传文件的类型是否合法
 */
@Slf4j
@Component
public class FileTypeValidator {

    /**
     * 支持的图片类型（MIME类型）
     */
    private static final Set<String> ALLOWED_IMAGE_TYPES = new HashSet<>(Arrays.asList(
            "image/jpeg",
            "image/jpg",
            "image/png",
            "image/gif",
            "image/webp",
            "image/bmp",
            "image/svg+xml"
    ));

    /**
     * 支持的图片扩展名
     */
    private static final Set<String> ALLOWED_IMAGE_EXTENSIONS = new HashSet<>(Arrays.asList(
            "jpg", "jpeg", "png", "gif", "webp", "bmp", "svg"
    ));

    /**
     * 支持的音频类型（MIME类型）
     */
    private static final Set<String> ALLOWED_AUDIO_TYPES = new HashSet<>(Arrays.asList(
            "audio/mpeg",      // mp3
            "audio/mp3",       // mp3
            "audio/wav",       // wav
            "audio/wave",      // wav
            "audio/x-wav",     // wav
            "audio/mp4",       // m4a
            "audio/x-m4a",     // m4a
            "audio/ogg",       // ogg
            "audio/webm"       // webm
    ));

    /**
     * 支持的音频扩展名
     */
    private static final Set<String> ALLOWED_AUDIO_EXTENSIONS = new HashSet<>(Arrays.asList(
            "mp3", "wav", "m4a", "ogg", "webm"
    ));

    /**
     * 最大文件大小（100MB）
     */
    private static final long MAX_FILE_SIZE = 100 * 1024 * 1024;

    /**
     * 验证是否为图片文件
     *
     * @param contentType MIME类型
     * @param filename 文件名
     * @return 是否为合法图片
     */
    public boolean isValidImage(String contentType, String filename) {
        if (contentType == null || filename == null) {
            log.warn("文件验证失败: contentType或filename为空");
            return false;
        }

        // 验证MIME类型
        boolean validContentType = ALLOWED_IMAGE_TYPES.contains(contentType.toLowerCase());
        
        // 验证文件扩展名
        String extension = getFileExtension(filename);
        boolean validExtension = extension != null && ALLOWED_IMAGE_EXTENSIONS.contains(extension.toLowerCase());

        if (!validContentType || !validExtension) {
            log.warn("图片类型验证失败: contentType={}, filename={}", contentType, filename);
            return false;
        }

        return true;
    }

    /**
     * 验证是否为音频文件
     *
     * @param contentType MIME类型
     * @param filename 文件名
     * @return 是否为合法音频
     */
    public boolean isValidAudio(String contentType, String filename) {
        if (contentType == null || filename == null) {
            log.warn("文件验证失败: contentType或filename为空");
            return false;
        }

        // 验证MIME类型
        boolean validContentType = ALLOWED_AUDIO_TYPES.contains(contentType.toLowerCase());
        
        // 验证文件扩展名
        String extension = getFileExtension(filename);
        boolean validExtension = extension != null && ALLOWED_AUDIO_EXTENSIONS.contains(extension.toLowerCase());

        if (!validContentType || !validExtension) {
            log.warn("音频类型验证失败: contentType={}, filename={}", contentType, filename);
            return false;
        }

        return true;
    }

    /**
     * 验证文件大小
     *
     * @param fileSize 文件大小（字节）
     * @return 是否符合大小限制
     */
    public boolean isValidFileSize(long fileSize) {
        if (fileSize <= 0) {
            log.warn("文件大小验证失败: fileSize={}字节", fileSize);
            return false;
        }

        if (fileSize > MAX_FILE_SIZE) {
            log.warn("文件大小超出限制: fileSize={}字节, maxSize={}字节", fileSize, MAX_FILE_SIZE);
            return false;
        }

        return true;
    }

    /**
     * 获取文件扩展名
     *
     * @param filename 文件名
     * @return 扩展名（不含点）
     */
    private String getFileExtension(String filename) {
        if (filename == null || filename.isEmpty()) {
            return null;
        }

        int lastDotIndex = filename.lastIndexOf('.');
        if (lastDotIndex == -1 || lastDotIndex == filename.length() - 1) {
            return null;
        }

        return filename.substring(lastDotIndex + 1);
    }

    /**
     * 获取最大文件大小（字节）
     *
     * @return 最大文件大小
     */
    public long getMaxFileSize() {
        return MAX_FILE_SIZE;
    }
}
