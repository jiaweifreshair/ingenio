package com.ingenio.backend.service;

import com.ingenio.backend.config.MinioConfig;
import com.ingenio.backend.service.impl.MinioServiceImpl;
import io.minio.MinioClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * MinIO Service E2E集成测试
 * 使用TestContainers启动真实MinIO服务进行测试
 */
@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
class MinioE2ETest {

    /**
     * MinIO TestContainer
     * 使用官方MinIO镜像
     */
    @Container
    static GenericContainer<?> minioContainer = new GenericContainer<>(
            DockerImageName.parse("minio/minio:latest"))
            .withCommand("server /data")
            .withEnv("MINIO_ROOT_USER", "minioadmin")
            .withEnv("MINIO_ROOT_PASSWORD", "minioadmin")
            .withExposedPorts(9000);

    @Autowired
    private MinioService minioService;

    private static final String TEST_OBJECT_NAME = "test/e2e-test-image.jpg";
    private static final String TEST_CONTENT = "This is E2E test content for MinIO service";

    /**
     * 动态配置MinIO连接信息
     */
    @DynamicPropertySource
    static void minioProperties(DynamicPropertyRegistry registry) {
        registry.add("minio.endpoint", () -> 
                String.format("http://%s:%d", 
                        minioContainer.getHost(), 
                        minioContainer.getMappedPort(9000)));
        registry.add("minio.access-key", () -> "minioadmin");
        registry.add("minio.secret-key", () -> "minioadmin");
        registry.add("minio.bucket-name", () -> "test-bucket");
        registry.add("minio.auto-create-bucket", () -> "true");
    }

    @BeforeEach
    void setUp() {
        // 测试前确保MinIO服务就绪
        assertTrue(minioContainer.isRunning(), "MinIO容器应该正在运行");
    }

    @AfterEach
    void cleanup() {
        // 清理测试文件
        try {
            if (minioService.fileExists(TEST_OBJECT_NAME)) {
                minioService.deleteFile(TEST_OBJECT_NAME);
            }
        } catch (Exception e) {
            // 忽略清理错误
        }
    }

    /**
     * E2E测试: 文件上传
     */
    @Test
    void testUploadFile_E2E() {
        // 准备测试数据
        byte[] content = TEST_CONTENT.getBytes(StandardCharsets.UTF_8);
        InputStream inputStream = new ByteArrayInputStream(content);
        String contentType = "image/jpeg";

        // 执行上传
        String fileUrl = minioService.uploadFile(
                TEST_OBJECT_NAME,
                inputStream,
                contentType,
                content.length
        );

        // 验证结果
        assertNotNull(fileUrl, "文件URL不应为空");
        assertTrue(fileUrl.contains(TEST_OBJECT_NAME), "URL应包含对象名称");
        assertTrue(minioService.fileExists(TEST_OBJECT_NAME), "文件应存在于MinIO");
    }

    /**
     * E2E测试: 文件下载
     */
    @Test
    void testDownloadFile_E2E() throws Exception {
        // 先上传文件
        byte[] content = TEST_CONTENT.getBytes(StandardCharsets.UTF_8);
        minioService.uploadFile(
                TEST_OBJECT_NAME,
                new ByteArrayInputStream(content),
                "image/jpeg",
                content.length
        );

        // 执行下载
        InputStream downloadedStream = minioService.downloadFile(TEST_OBJECT_NAME);

        // 验证下载内容
        assertNotNull(downloadedStream, "下载流不应为空");
        String downloadedContent = new String(downloadedStream.readAllBytes(), StandardCharsets.UTF_8);
        assertEquals(TEST_CONTENT, downloadedContent, "下载内容应与上传内容一致");
    }

    /**
     * E2E测试: 文件删除
     */
    @Test
    void testDeleteFile_E2E() {
        // 先上传文件
        byte[] content = TEST_CONTENT.getBytes(StandardCharsets.UTF_8);
        minioService.uploadFile(
                TEST_OBJECT_NAME,
                new ByteArrayInputStream(content),
                "image/jpeg",
                content.length
        );

        // 确认文件存在
        assertTrue(minioService.fileExists(TEST_OBJECT_NAME), "文件应存在");

        // 执行删除
        minioService.deleteFile(TEST_OBJECT_NAME);

        // 验证文件已删除
        assertFalse(minioService.fileExists(TEST_OBJECT_NAME), "文件应已删除");
    }

    /**
     * E2E测试: 获取文件元数据
     */
    @Test
    void testGetFileMetadata_E2E() {
        // 先上传文件
        byte[] content = TEST_CONTENT.getBytes(StandardCharsets.UTF_8);
        Map<String, String> customMetadata = new HashMap<>();
        customMetadata.put("test-key", "test-value");
        
        minioService.uploadFile(
                TEST_OBJECT_NAME,
                new ByteArrayInputStream(content),
                "image/jpeg",
                content.length,
                customMetadata
        );

        // 执行获取元数据
        Map<String, String> metadata = minioService.getFileMetadata(TEST_OBJECT_NAME);

        // 验证元数据
        assertNotNull(metadata, "元数据不应为空");
        assertEquals("image/jpeg", metadata.get("contentType"), "Content-Type应匹配");
        assertEquals(String.valueOf(content.length), metadata.get("size"), "文件大小应匹配");
        assertEquals("test-value", metadata.get("test-key"), "自定义元数据应匹配");
    }

    /**
     * E2E测试: 生成预签名URL
     */
    @Test
    void testGeneratePresignedUrl_E2E() {
        // 先上传文件
        byte[] content = TEST_CONTENT.getBytes(StandardCharsets.UTF_8);
        minioService.uploadFile(
                TEST_OBJECT_NAME,
                new ByteArrayInputStream(content),
                "image/jpeg",
                content.length
        );

        // 生成预签名URL
        String presignedUrl = minioService.generatePresignedUrl(TEST_OBJECT_NAME, 3600);

        // 验证URL
        assertNotNull(presignedUrl, "预签名URL不应为空");
        assertTrue(presignedUrl.contains(TEST_OBJECT_NAME), "URL应包含对象名称");
        assertTrue(presignedUrl.contains("X-Amz-Algorithm"), "URL应包含签名参数");
    }

    /**
     * E2E测试: 检查文件存在性
     */
    @Test
    void testFileExists_E2E() {
        // 检查不存在的文件
        assertFalse(minioService.fileExists(TEST_OBJECT_NAME), "文件应不存在");

        // 上传文件
        byte[] content = TEST_CONTENT.getBytes(StandardCharsets.UTF_8);
        minioService.uploadFile(
                TEST_OBJECT_NAME,
                new ByteArrayInputStream(content),
                "image/jpeg",
                content.length
        );

        // 检查文件存在
        assertTrue(minioService.fileExists(TEST_OBJECT_NAME), "文件应存在");
    }

    /**
     * E2E测试: 完整流程（上传→下载→删除）
     */
    @Test
    void testCompleteWorkflow_E2E() throws Exception {
        String objectName = "test/workflow-test.jpg";
        byte[] content = "Workflow test content".getBytes(StandardCharsets.UTF_8);

        try {
            // 1. 上传文件
            String fileUrl = minioService.uploadFile(
                    objectName,
                    new ByteArrayInputStream(content),
                    "image/jpeg",
                    content.length
            );
            assertNotNull(fileUrl);

            // 2. 验证文件存在
            assertTrue(minioService.fileExists(objectName));

            // 3. 下载文件
            InputStream downloadStream = minioService.downloadFile(objectName);
            String downloadedContent = new String(downloadStream.readAllBytes(), StandardCharsets.UTF_8);
            assertEquals("Workflow test content", downloadedContent);

            // 4. 获取元数据
            Map<String, String> metadata = minioService.getFileMetadata(objectName);
            assertEquals("image/jpeg", metadata.get("contentType"));

            // 5. 生成预签名URL
            String presignedUrl = minioService.generatePresignedUrl(objectName, 3600);
            assertTrue(presignedUrl.contains(objectName));

            // 6. 删除文件
            minioService.deleteFile(objectName);
            assertFalse(minioService.fileExists(objectName));

        } finally {
            // 确保清理
            if (minioService.fileExists(objectName)) {
                minioService.deleteFile(objectName);
            }
        }
    }
}
