package com.ingenio.backend.service;

import com.ingenio.backend.common.exception.BusinessException;
import com.ingenio.backend.config.MinioConfig;
import com.ingenio.backend.service.impl.MinioServiceImpl;
import io.minio.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.quality.Strictness;
import org.mockito.junit.jupiter.MockitoSettings;
import io.minio.errors.ErrorResponseException;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * MinioService单元测试
 * 使用Mockito进行单元测试,验证业务逻辑
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class MinioServiceTest {

    @Mock
    private MinioClient minioClient;

    @Mock
    private MinioConfig minioConfig;

    private MinioServiceImpl minioService;

    @BeforeEach
    void setUp() {
        when(minioConfig.getBucketName()).thenReturn("test-bucket");
        when(minioConfig.getEndpoint()).thenReturn("http://localhost:9000");
        when(minioConfig.isAutoCreateBucket()).thenReturn(false); // 禁用自动创建以避免@PostConstruct

        minioService = new MinioServiceImpl(minioClient, minioConfig);
    }

    /**
     * 测试上传文件成功
     */
    @Test
    void testUploadFile_Success() throws Exception {
        // 准备测试数据
        String objectName = "test/image.jpg";
        String contentType = "image/jpeg";
        long size = 1024;
        byte[] content = "test content".getBytes();
        InputStream inputStream = new ByteArrayInputStream(content);

        // 模拟MinIO上传成功
        when(minioClient.putObject(any(PutObjectArgs.class)))
                .thenReturn(mock(ObjectWriteResponse.class));

        // 执行上传
        String result = minioService.uploadFile(objectName, inputStream, contentType, size);

        // 验证结果
        assertNotNull(result);
        assertTrue(result.contains(objectName));
        verify(minioClient, times(1)).putObject(any(PutObjectArgs.class));
    }

    /**
     * 测试上传文件失败 - 空文件名
     */
    @Test
    void testUploadFile_EmptyObjectName() {
        // 准备测试数据
        String objectName = "";
        InputStream inputStream = new ByteArrayInputStream("test".getBytes());

        // 验证抛出异常
        assertThrows(BusinessException.class, () -> {
            minioService.uploadFile(objectName, inputStream, "image/jpeg", 1024);
        });
    }

    /**
     * 测试上传文件失败 - 空输入流
     */
    @Test
    void testUploadFile_NullInputStream() {
        // 验证抛出异常
        assertThrows(BusinessException.class, () -> {
            minioService.uploadFile("test.jpg", null, "image/jpeg", 1024);
        });
    }

    /**
     * 测试下载文件成功
     */
    @Test
    void testDownloadFile_Success() throws Exception {
        // 准备测试数据
        String objectName = "test/image.jpg";
        GetObjectResponse mockResponse = mock(GetObjectResponse.class);

        // 模拟MinIO下载成功
        when(minioClient.getObject(any(GetObjectArgs.class)))
                .thenReturn(mockResponse);

        // 执行下载
        InputStream result = minioService.downloadFile(objectName);

        // 验证结果
        assertNotNull(result);
        verify(minioClient, times(1)).getObject(any(GetObjectArgs.class));
    }

    /**
     * 测试删除文件成功
     */
    @Test
    void testDeleteFile_Success() throws Exception {
        // 准备测试数据
        String objectName = "test/image.jpg";

        // 模拟MinIO删除成功（void方法无需返回值）
        doNothing().when(minioClient).removeObject(any(RemoveObjectArgs.class));

        // 执行删除
        assertDoesNotThrow(() -> minioService.deleteFile(objectName));

        // 验证调用
        verify(minioClient, times(1)).removeObject(any(RemoveObjectArgs.class));
    }

    /**
     * 测试检查文件存在
     */
    @Test
    void testFileExists_True() throws Exception {
        // 准备测试数据
        String objectName = "test/image.jpg";

        // 模拟MinIO文件存在
        when(minioClient.statObject(any(StatObjectArgs.class)))
                .thenReturn(mock(StatObjectResponse.class));

        // 执行检查
        boolean exists = minioService.fileExists(objectName);

        // 验证结果
        assertTrue(exists);
    }

    /**
     * 测试检查文件不存在
     */
    @Test
    void testFileExists_False() throws Exception {
        // 准备测试数据
        String objectName = "test/nonexistent.jpg";

        // 模拟MinIO文件不存在（使用MinIO的ErrorResponseException）
        ErrorResponseException mockException = mock(ErrorResponseException.class);
        when(minioClient.statObject(any(StatObjectArgs.class)))
                .thenThrow(mockException);

        // 执行检查
        boolean exists = minioService.fileExists(objectName);

        // 验证结果
        assertFalse(exists);
    }

    /**
     * 测试获取文件元数据
     */
    @Test
    void testGetFileMetadata_Success() throws Exception {
        // 准备测试数据
        String objectName = "test/image.jpg";
        StatObjectResponse mockResponse = mock(StatObjectResponse.class);

        // Mock所有需要的方法，包括lastModified
        when(mockResponse.contentType()).thenReturn("image/jpeg");
        when(mockResponse.size()).thenReturn(1024L);
        when(mockResponse.etag()).thenReturn("abc123");
        when(mockResponse.userMetadata()).thenReturn(null);
        when(mockResponse.lastModified()).thenReturn(java.time.ZonedDateTime.now());

        // 模拟MinIO返回元数据
        when(minioClient.statObject(any(StatObjectArgs.class)))
                .thenReturn(mockResponse);

        // 执行获取元数据
        Map<String, String> metadata = minioService.getFileMetadata(objectName);

        // 验证结果
        assertNotNull(metadata);
        assertEquals("image/jpeg", metadata.get("contentType"));
        assertEquals("1024", metadata.get("size"));
        assertEquals("abc123", metadata.get("etag"));
        assertNotNull(metadata.get("lastModified")); // 验证lastModified也被设置
    }

    /**
     * 测试生成预签名URL
     */
    @Test
    void testGeneratePresignedUrl_Success() throws Exception {
        // 准备测试数据
        String objectName = "test/image.jpg";
        int expiry = 3600;
        String expectedUrl = "http://localhost:9000/test-bucket/test/image.jpg?X-Amz-Algorithm=...";

        // 模拟MinIO生成预签名URL
        when(minioClient.getPresignedObjectUrl(any(GetPresignedObjectUrlArgs.class)))
                .thenReturn(expectedUrl);

        // 执行生成URL
        String result = minioService.generatePresignedUrl(objectName, expiry);

        // 验证结果
        assertNotNull(result);
        assertEquals(expectedUrl, result);
        verify(minioClient, times(1)).getPresignedObjectUrl(any(GetPresignedObjectUrlArgs.class));
    }
}
