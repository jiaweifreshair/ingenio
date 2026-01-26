package com.ingenio.backend.service;

import com.ingenio.backend.dto.CodeRetrievalResult;
import com.ingenio.backend.dto.CodeStorageResult;
import com.ingenio.backend.entity.AppSpecEntity;
import com.ingenio.backend.mapper.AppSpecMapper;
import com.ingenio.backend.service.impl.CodeStorageServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class CodeStorageServiceTest {

    @Mock
    private AppSpecMapper appSpecMapper;

    @InjectMocks
    private CodeStorageServiceImpl codeStorageService;

    @TempDir
    Path tempDir;

    private UUID testAppSpecId;
    private AppSpecEntity testAppSpec;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        testAppSpecId = UUID.randomUUID();
        testAppSpec = new AppSpecEntity();
        testAppSpec.setId(testAppSpecId);

        // 设置归档路径
        ReflectionTestUtils.setField(codeStorageService, "archiveBasePath", tempDir.toString());
    }

    @Test
    void testSaveGeneratedCode_Success() throws IOException {
        // 创建��试代码目录
        Path frontendDir = tempDir.resolve("frontend");
        Path backendDir = tempDir.resolve("backend");
        Files.createDirectories(frontendDir);
        Files.createDirectories(backendDir);
        Files.writeString(frontendDir.resolve("test.js"), "console.log('test')");
        Files.writeString(backendDir.resolve("test.java"), "public class Test {}");

        when(appSpecMapper.selectById(testAppSpecId)).thenReturn(testAppSpec);
        when(appSpecMapper.updateById(any(AppSpecEntity.class))).thenReturn(1);

        CodeStorageResult result = codeStorageService.saveGeneratedCode(
                testAppSpecId,
                frontendDir.toString(),
                backendDir.toString()
        );

        assertTrue(result.getSuccess());
        assertNotNull(result.getFrontendArchivePath());
        assertNotNull(result.getBackendArchivePath());
        assertTrue(Files.exists(Path.of(result.getFrontendArchivePath())));
        assertTrue(Files.exists(Path.of(result.getBackendArchivePath())));
        verify(appSpecMapper).updateById(any(AppSpecEntity.class));
    }

    @Test
    void testGetCode_Success() {
        String archivePath = "/path/to/frontend.zip,/path/to/backend.zip";
        testAppSpec.setCodeArchivePath(archivePath);
        testAppSpec.setFrontendCodeUrl("https://github.com/test/frontend");
        testAppSpec.setBackendCodeUrl("https://github.com/test/backend");

        when(appSpecMapper.selectById(testAppSpecId)).thenReturn(testAppSpec);

        CodeRetrievalResult result = codeStorageService.getCode(testAppSpecId);

        assertTrue(result.getSuccess());
        assertEquals("/path/to/frontend.zip", result.getFrontendArchivePath());
        assertEquals("/path/to/backend.zip", result.getBackendArchivePath());
        assertEquals("https://github.com/test/frontend", result.getFrontendCodeUrl());
        assertEquals("https://github.com/test/backend", result.getBackendCodeUrl());
    }

    @Test
    void testGetCode_AppSpecNotFound() {
        when(appSpecMapper.selectById(testAppSpecId)).thenReturn(null);

        CodeRetrievalResult result = codeStorageService.getCode(testAppSpecId);

        assertFalse(result.getSuccess());
        assertEquals("AppSpec不存在", result.getErrorMessage());
    }

    @Test
    void testArchiveCode_Success() throws IOException {
        Path codeDir = tempDir.resolve("code");
        Files.createDirectories(codeDir);
        Files.writeString(codeDir.resolve("test.txt"), "test content");

        String archivePath = codeStorageService.archiveCode(testAppSpecId, codeDir.toString(), "frontend");

        assertNotNull(archivePath);
        assertTrue(Files.exists(Path.of(archivePath)));
        assertTrue(archivePath.endsWith(".zip"));
    }

    @Test
    void testArchiveCode_PathNotExists() {
        String nonExistentPath = tempDir.resolve("nonexistent").toString();

        assertThrows(RuntimeException.class, () -> {
            codeStorageService.archiveCode(testAppSpecId, nonExistentPath, "frontend");
        });
    }
}
