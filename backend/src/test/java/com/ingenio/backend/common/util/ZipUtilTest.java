package com.ingenio.backend.common.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ZipUtil 工具类测试
 * 测试 ZIP 文件打包功能
 *
 * @author Ingenio Team
 * @since 1.0.0
 */
@DisplayName("ZipUtil 工具类测试")
class ZipUtilTest {

    /**
     * 测试创建 ZIP 文件 - 基本功能
     */
    @Test
    @DisplayName("测试创建 ZIP 文件 - 基本功能")
    void testCreateZipBasic() throws Exception {
        // Arrange
        Map<String, String> files = new HashMap<>();
        files.put("test1.txt", "这是测试文件1");
        files.put("test2.txt", "这是测试文件2");
        files.put("subdir/test3.txt", "这是子目录中的测试文件");

        // Act
        byte[] zipBytes = ZipUtil.createZipBytes(files);

        // Assert
        assertNotNull(zipBytes);
        assertTrue(zipBytes.length > 0, "ZIP 文件大小应该大于 0");

        // 验证 ZIP 文件内容
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
            ZipEntry entry;
            int fileCount = 0;
            while ((entry = zis.getNextEntry()) != null) {
                fileCount++;
                assertTrue(files.containsKey(entry.getName()), 
                    "ZIP 中应该包含文件: " + entry.getName());
                
                // 读取文件内容
                byte[] buffer = new byte[1024];
                StringBuilder content = new StringBuilder();
                int len;
                while ((len = zis.read(buffer)) > 0) {
                    content.append(new String(buffer, 0, len, StandardCharsets.UTF_8));
                }
                
                assertEquals(files.get(entry.getName()), content.toString(),
                    "文件内容应该匹配: " + entry.getName());
            }
            assertEquals(files.size(), fileCount, "ZIP 中应该包含所有文件");
        }
    }

    /**
     * 测试创建 ZIP 文件 - 空文件列表
     */
    @Test
    @DisplayName("测试创建 ZIP 文件 - 空文件列表")
    void testCreateZipEmptyFiles() {
        // Arrange
        Map<String, String> emptyFiles = new HashMap<>();

        // Act & Assert
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> ZipUtil.createZipBytes(emptyFiles)
        );
        assertTrue(exception.getMessage().contains("文件列表不能为空"));
    }

    /**
     * 测试创建 ZIP 文件 - null 文件列表
     */
    @Test
    @DisplayName("测试创建 ZIP 文件 - null 文件列表")
    void testCreateZipNullFiles() {
        // Act & Assert
        IllegalArgumentException exception = assertThrows(
            IllegalArgumentException.class,
            () -> ZipUtil.createZipBytes(null)
        );
        assertTrue(exception.getMessage().contains("文件列表不能为空"));
    }

    /**
     * 测试创建 ZIP 文件 - 大量文件
     */
    @Test
    @DisplayName("测试创建 ZIP 文件 - 大量文件")
    void testCreateZipManyFiles() throws Exception {
        // Arrange
        Map<String, String> files = new HashMap<>();
        for (int i = 0; i < 100; i++) {
            files.put("file" + i + ".txt", "这是文件 " + i + " 的内容");
        }

        // Act
        byte[] zipBytes = ZipUtil.createZipBytes(files);

        // Assert
        assertNotNull(zipBytes);
        assertTrue(zipBytes.length > 0);

        // 验证所有文件都在 ZIP 中
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
            ZipEntry entry;
            int fileCount = 0;
            while ((entry = zis.getNextEntry()) != null) {
                fileCount++;
            }
            assertEquals(100, fileCount, "ZIP 中应该包含 100 个文件");
        }
    }

    /**
     * 测试创建 ZIP 文件 - 包含特殊字符的文件名
     */
    @Test
    @DisplayName("测试创建 ZIP 文件 - 特殊字符文件名")
    void testCreateZipSpecialCharacters() throws Exception {
        // Arrange
        Map<String, String> files = new HashMap<>();
        files.put("文件-测试.txt", "中文文件名测试");
        files.put("file with spaces.txt", "包含空格的文件名");
        files.put("file-with-dashes.txt", "包含连字符的文件名");

        // Act
        byte[] zipBytes = ZipUtil.createZipBytes(files);

        // Assert
        assertNotNull(zipBytes);
        assertTrue(zipBytes.length > 0);

        // 验证文件都在 ZIP 中
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
            ZipEntry entry;
            int fileCount = 0;
            while ((entry = zis.getNextEntry()) != null) {
                fileCount++;
                assertTrue(files.containsKey(entry.getName()));
            }
            assertEquals(files.size(), fileCount);
        }
    }

    /**
     * 测试创建 ZIP 文件 - 返回 InputStream
     */
    @Test
    @DisplayName("测试创建 ZIP 文件 - 返回 InputStream")
    void testCreateZipInputStream() throws Exception {
        // Arrange
        Map<String, String> files = new HashMap<>();
        files.put("test.txt", "测试内容");

        // Act
        InputStream zipStream = ZipUtil.createZip(files);

        // Assert
        assertNotNull(zipStream);
        assertTrue(zipStream.available() > 0, "ZIP 流应该可读");

        // 验证可以读取 ZIP 内容
        try (ZipInputStream zis = new ZipInputStream(zipStream)) {
            ZipEntry entry = zis.getNextEntry();
            assertNotNull(entry, "应该包含 ZIP 条目");
            assertEquals("test.txt", entry.getName());
        }
    }
}

