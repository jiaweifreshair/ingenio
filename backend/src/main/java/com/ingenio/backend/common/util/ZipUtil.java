package com.ingenio.backend.common.util;

import lombok.extern.slf4j.Slf4j;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * ZIP文件打包工具类
 * 用于将多个文件打包成ZIP压缩包
 *
 * @author Ingenio Team
 * @since 1.0.0
 */
@Slf4j
public class ZipUtil {

    /**
     * 将文件映射打包成ZIP文件
     *
     * @param files 文件映射（文件路径 -> 文件内容）
     * @return ZIP文件的字节数组输入流
     * @throws IOException 当打包失败时抛出
     */
    public static InputStream createZip(Map<String, String> files) throws IOException {
        if (files == null || files.isEmpty()) {
            throw new IllegalArgumentException("文件列表不能为空");
        }

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos, StandardCharsets.UTF_8)) {
            for (Map.Entry<String, String> entry : files.entrySet()) {
                String filePath = entry.getKey();
                String fileContent = entry.getValue();

                if (filePath == null || filePath.isEmpty()) {
                    log.warn("跳过空文件路径");
                    continue;
                }

                // 创建ZIP条目
                ZipEntry zipEntry = new ZipEntry(filePath);
                zos.putNextEntry(zipEntry);

                // 写入文件内容
                if (fileContent != null) {
                    byte[] contentBytes = fileContent.getBytes(StandardCharsets.UTF_8);
                    zos.write(contentBytes, 0, contentBytes.length);
                }

                zos.closeEntry();
                log.debug("已添加文件到ZIP: {}", filePath);
            }
        }

        byte[] zipBytes = baos.toByteArray();
        log.info("ZIP文件打包完成: 文件数={}, 大小={} bytes", files.size(), zipBytes.length);
        return new ByteArrayInputStream(zipBytes);
    }

    /**
     * 将文件映射打包成ZIP文件，返回字节数组
     *
     * @param files 文件映射（文件路径 -> 文件内容）
     * @return ZIP文件的字节数组
     * @throws IOException 当打包失败时抛出
     */
    public static byte[] createZipBytes(Map<String, String> files) throws IOException {
        try (InputStream zipStream = createZip(files);
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            zipStream.transferTo(baos);
            return baos.toByteArray();
        }
    }
}

