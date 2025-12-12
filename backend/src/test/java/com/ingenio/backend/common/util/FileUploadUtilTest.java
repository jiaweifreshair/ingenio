package com.ingenio.backend.common.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import static org.junit.jupiter.api.Assertions.*;

/**
 * FileUploadUtil单元测试
 *
 * 测试覆盖范围:
 * - 文件路径生成（通用、图片、音频）
 * - 文件扩展名提取
 * - 文件大小格式化
 * - 边界条件测试
 * - 异常情况处理
 *
 * @author Ingenio Team
 * @since 2025-11-10
 */
@DisplayName("文件上传工具类单元测试")
class FileUploadUtilTest {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy/MM/dd");

    // ==================== 文件路径生成测试 ====================

    @Test
    @DisplayName("生成通用对象存储路径 - 基本功能")
    void testGenerateObjectName() {
        String objectName = FileUploadUtil.generateObjectName("documents", "report.pdf");

        // 验证路径格式
        assertNotNull(objectName);
        assertTrue(objectName.startsWith("documents/"));
        assertTrue(objectName.endsWith(".pdf"));

        // 验证日期路径格式（yyyy/MM/dd）
        String datePath = LocalDate.now().format(DATE_FORMATTER);
        assertTrue(objectName.contains(datePath));
    }

    @Test
    @DisplayName("生成图片存储路径")
    void testGenerateImagePath() {
        String imagePath = FileUploadUtil.generateImagePath("photo.jpg");

        assertNotNull(imagePath);
        assertTrue(imagePath.startsWith("images/"));
        assertTrue(imagePath.endsWith(".jpg"));

        // 验证日期路径
        String datePath = LocalDate.now().format(DATE_FORMATTER);
        assertTrue(imagePath.contains(datePath));
    }

    @Test
    @DisplayName("生成音频存储路径")
    void testGenerateAudioPath() {
        String audioPath = FileUploadUtil.generateAudioPath("song.mp3");

        assertNotNull(audioPath);
        assertTrue(audioPath.startsWith("audio/"));
        assertTrue(audioPath.endsWith(".mp3"));

        // 验证日期路径
        String datePath = LocalDate.now().format(DATE_FORMATTER);
        assertTrue(audioPath.contains(datePath));
    }

    @Test
    @DisplayName("生成路径的唯一性验证")
    void testObjectNameUniqueness() {
        String path1 = FileUploadUtil.generateObjectName("test", "file.txt");
        String path2 = FileUploadUtil.generateObjectName("test", "file.txt");

        // 两次生成的路径应该不同（因为UUID不同）
        assertNotEquals(path1, path2);
    }

    @Test
    @DisplayName("验证路径格式：category/yyyy/MM/dd/uuid.extension")
    void testObjectNameFormat() {
        String objectName = FileUploadUtil.generateObjectName("videos", "movie.mp4");

        // 分割路径验证各部分
        String[] parts = objectName.split("/");
        assertEquals(5, parts.length, "路径应包含5部分：category, year, month, day, filename");

        assertEquals("videos", parts[0], "第一部分应为category");
        assertTrue(parts[1].matches("\\d{4}"), "第二部分应为4位年份");
        assertTrue(parts[2].matches("\\d{2}"), "第三部分应为2位月份");
        assertTrue(parts[3].matches("\\d{2}"), "第四部分应为2位日期");
        assertTrue(parts[4].endsWith(".mp4"), "第五部分应为uuid文件名");
    }

    @ParameterizedTest
    @CsvSource({
        "images, photo.jpg, .jpg",
        "audio, song.mp3, .mp3",
        "documents, report.pdf, .pdf",
        "videos, movie.mp4, .mp4"
    })
    @DisplayName("参数化测试：不同分类和扩展名的路径生成")
    void testGenerateObjectNameWithVariousTypes(String category, String filename, String extension) {
        String objectName = FileUploadUtil.generateObjectName(category, filename);

        assertTrue(objectName.startsWith(category + "/"));
        assertTrue(objectName.endsWith(extension));
    }

    @Test
    @DisplayName("生成无扩展名文件的路径")
    void testGenerateObjectNameWithoutExtension() {
        String objectName = FileUploadUtil.generateObjectName("files", "README");

        assertNotNull(objectName);
        assertTrue(objectName.startsWith("files/"));
        // 无扩展名时，路径只包含UUID
        assertFalse(objectName.contains("."));
    }

    // ==================== 文件扩展名提取测试 ====================

    @Test
    @DisplayName("提取常见文件扩展名")
    void testGetFileExtension() {
        assertEquals("jpg", FileUploadUtil.getFileExtension("photo.jpg"));
        assertEquals("png", FileUploadUtil.getFileExtension("screenshot.png"));
        assertEquals("mp3", FileUploadUtil.getFileExtension("song.mp3"));
        assertEquals("pdf", FileUploadUtil.getFileExtension("document.pdf"));
    }

    @Test
    @DisplayName("提取大写扩展名（自动转小写）")
    void testGetFileExtensionWithUpperCase() {
        assertEquals("jpg", FileUploadUtil.getFileExtension("PHOTO.JPG"));
        assertEquals("png", FileUploadUtil.getFileExtension("Screenshot.PNG"));
        assertEquals("mp3", FileUploadUtil.getFileExtension("Song.Mp3"));
    }

    @Test
    @DisplayName("提取多个点的文件扩展名")
    void testGetFileExtensionWithMultipleDots() {
        // 只提取最后一个点之后的扩展名（标准行为）
        assertEquals("gz", FileUploadUtil.getFileExtension("archive.tar.gz"));
        assertEquals("backup", FileUploadUtil.getFileExtension("file.txt.backup"));
    }

    @Test
    @DisplayName("处理无扩展名的文件")
    void testGetFileExtensionWithoutExtension() {
        assertNull(FileUploadUtil.getFileExtension("README"));
        assertNull(FileUploadUtil.getFileExtension("Makefile"));
    }

    @Test
    @DisplayName("处理以点结尾的文件名")
    void testGetFileExtensionEndingWithDot() {
        assertNull(FileUploadUtil.getFileExtension("file."));
        assertNull(FileUploadUtil.getFileExtension("document.txt."));
    }

    @Test
    @DisplayName("处理null和空字符串")
    void testGetFileExtensionWithNullOrEmpty() {
        assertNull(FileUploadUtil.getFileExtension(null));
        assertNull(FileUploadUtil.getFileExtension(""));
    }

    @Test
    @DisplayName("处理仅包含点的文件名")
    void testGetFileExtensionWithOnlyDots() {
        assertNull(FileUploadUtil.getFileExtension("."));
        assertNull(FileUploadUtil.getFileExtension(".."));
        assertNull(FileUploadUtil.getFileExtension("..."));
    }

    @Test
    @DisplayName("处理路径分隔符的文件名")
    void testGetFileExtensionWithPathSeparators() {
        assertEquals("jpg", FileUploadUtil.getFileExtension("/path/to/photo.jpg"));
        assertEquals("txt", FileUploadUtil.getFileExtension("C:\\Users\\Documents\\file.txt"));
    }

    @ParameterizedTest
    @CsvSource({
        "photo.jpg, jpg",
        "screenshot.png, png",
        "song.mp3, mp3",
        "document.pdf, pdf",
        "archive.tar.gz, gz",
        "README, null",
        "file., null",
        "'', null"
    })
    @DisplayName("参数化测试：提取各种文件扩展名")
    void testGetFileExtensionParameterized(String filename, String expected) {
        String expectedValue = "null".equals(expected) ? null : expected;
        assertEquals(expectedValue, FileUploadUtil.getFileExtension(filename));
    }

    // ==================== 文件大小格式化测试 ====================

    @Test
    @DisplayName("格式化字节大小（小于1KB）")
    void testFormatFileSizeBytes() {
        assertEquals("0B", FileUploadUtil.formatFileSize(0));
        assertEquals("1B", FileUploadUtil.formatFileSize(1));
        assertEquals("512B", FileUploadUtil.formatFileSize(512));
        assertEquals("1023B", FileUploadUtil.formatFileSize(1023));
    }

    @Test
    @DisplayName("格式化KB大小")
    void testFormatFileSizeKilobytes() {
        assertEquals("1.00KB", FileUploadUtil.formatFileSize(1024));
        assertEquals("1.50KB", FileUploadUtil.formatFileSize(1536));
        assertEquals("10.25KB", FileUploadUtil.formatFileSize(10496));
        assertEquals("1023.50KB", FileUploadUtil.formatFileSize(1048064));
    }

    @Test
    @DisplayName("格式化MB大小")
    void testFormatFileSizeMegabytes() {
        assertEquals("1.00MB", FileUploadUtil.formatFileSize(1024 * 1024));
        assertEquals("1.50MB", FileUploadUtil.formatFileSize((long) (1.5 * 1024 * 1024)));
        assertEquals("10.00MB", FileUploadUtil.formatFileSize(10 * 1024 * 1024));
        assertEquals("100.50MB", FileUploadUtil.formatFileSize((long) (100.5 * 1024 * 1024)));
    }

    @Test
    @DisplayName("格式化GB大小")
    void testFormatFileSizeGigabytes() {
        assertEquals("1.00GB", FileUploadUtil.formatFileSize(1024L * 1024 * 1024));
        assertEquals("1.50GB", FileUploadUtil.formatFileSize((long) (1.5 * 1024 * 1024 * 1024)));
        assertEquals("10.00GB", FileUploadUtil.formatFileSize(10L * 1024 * 1024 * 1024));
    }

    @ParameterizedTest
    @CsvSource({
        "0, 0B",
        "1, 1B",
        "1024, 1.00KB",
        "1048576, 1.00MB",              // 1MB
        "10485760, 10.00MB",            // 10MB
        "104857600, 100.00MB",          // 100MB
        "1073741824, 1.00GB",           // 1GB
        "10737418240, 10.00GB"          // 10GB
    })
    @DisplayName("参数化测试：格式化各种文件大小")
    void testFormatFileSizeParameterized(long bytes, String expected) {
        assertEquals(expected, FileUploadUtil.formatFileSize(bytes));
    }

    @Test
    @DisplayName("格式化负数文件大小")
    void testFormatFileSizeNegative() {
        // 负数应返回负的字节表示
        assertEquals("-1B", FileUploadUtil.formatFileSize(-1));
        assertEquals("-1024B", FileUploadUtil.formatFileSize(-1024));
    }

    @Test
    @DisplayName("格式化极大文件大小")
    void testFormatFileSizeVeryLarge() {
        // 100GB
        String result = FileUploadUtil.formatFileSize(100L * 1024 * 1024 * 1024);
        assertEquals("100.00GB", result);

        // 1TB（以GB显示）
        String result2 = FileUploadUtil.formatFileSize(1024L * 1024 * 1024 * 1024);
        assertEquals("1024.00GB", result2);
    }

    // ==================== 边界条件和集成测试 ====================

    @Test
    @DisplayName("集成测试：完整的文件上传流程")
    void testCompleteFileUploadFlow() {
        // 模拟完整上传流程
        String originalFilename = "vacation-photo-2025.jpg";

        // 1. 提取扩展名
        String extension = FileUploadUtil.getFileExtension(originalFilename);
        assertEquals("jpg", extension);

        // 2. 生成存储路径
        String storagePath = FileUploadUtil.generateImagePath(originalFilename);
        assertTrue(storagePath.startsWith("images/"));
        assertTrue(storagePath.endsWith(".jpg"));

        // 3. 格式化文件大小
        long fileSize = 2 * 1024 * 1024; // 2MB
        String formattedSize = FileUploadUtil.formatFileSize(fileSize);
        assertEquals("2.00MB", formattedSize);
    }

    @Test
    @DisplayName("验证不同category的隔离性")
    void testCategoryIsolation() {
        String filename = "file.txt";

        String imagesPath = FileUploadUtil.generateImagePath(filename);
        String audioPath = FileUploadUtil.generateAudioPath(filename);
        String customPath = FileUploadUtil.generateObjectName("custom", filename);

        // 不同category应生成不同的路径前缀
        assertTrue(imagesPath.startsWith("images/"));
        assertTrue(audioPath.startsWith("audio/"));
        assertTrue(customPath.startsWith("custom/"));

        // 但日期路径应相同
        String datePath = LocalDate.now().format(DATE_FORMATTER);
        assertTrue(imagesPath.contains(datePath));
        assertTrue(audioPath.contains(datePath));
        assertTrue(customPath.contains(datePath));
    }

    @Test
    @DisplayName("验证UUID不包含连字符")
    void testUuidWithoutHyphens() {
        String objectName = FileUploadUtil.generateObjectName("test", "file.txt");

        // 提取UUID部分（最后一个路径段的文件名）
        String[] parts = objectName.split("/");
        String filename = parts[parts.length - 1];
        String uuidPart = filename.substring(0, filename.lastIndexOf('.'));

        // 验证UUID不包含连字符
        assertFalse(uuidPart.contains("-"));

        // 验证UUID长度（32个字符，不含连字符）
        assertEquals(32, uuidPart.length());
    }

    @Test
    @DisplayName("性能测试：批量生成路径")
    void testBatchPathGeneration() {
        int count = 1000;
        long startTime = System.currentTimeMillis();

        for (int i = 0; i < count; i++) {
            FileUploadUtil.generateImagePath("photo" + i + ".jpg");
        }

        long endTime = System.currentTimeMillis();
        long duration = endTime - startTime;

        // 验证性能（1000次生成应在1秒内完成）
        assertTrue(duration < 1000, "生成" + count + "个路径耗时" + duration + "ms，应小于1000ms");
    }
}
