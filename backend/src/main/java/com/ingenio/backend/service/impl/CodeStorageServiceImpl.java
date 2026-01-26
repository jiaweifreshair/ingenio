package com.ingenio.backend.service.impl;

import com.ingenio.backend.dto.CodeRetrievalResult;
import com.ingenio.backend.dto.CodeStorageResult;
import com.ingenio.backend.entity.AppSpecEntity;
import com.ingenio.backend.mapper.AppSpecMapper;
import com.ingenio.backend.service.CodeStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.file.*;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * 代码存储服务实现
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CodeStorageServiceImpl implements CodeStorageService {

    private final AppSpecMapper appSpecMapper;

    @Value("${ingenio.code.archive.base-path:/tmp/ingenio/archives}")
    private String archiveBasePath;

    @Override
    public CodeStorageResult saveGeneratedCode(UUID appSpecId, String frontendCodePath, String backendCodePath) {
        try {
            log.info("开始保存代码，appSpecId: {}", appSpecId);

            // 归档前端代码
            String frontendArchivePath = null;
            if (frontendCodePath != null && !frontendCodePath.isEmpty()) {
                frontendArchivePath = archiveCode(appSpecId, frontendCodePath, "frontend");
                log.info("前端代码归档完成: {}", frontendArchivePath);
            }

            // 归��后端代码
            String backendArchivePath = null;
            if (backendCodePath != null && !backendCodePath.isEmpty()) {
                backendArchivePath = archiveCode(appSpecId, backendCodePath, "backend");
                log.info("后端代码归档完成: {}", backendArchivePath);
            }

            // 更新数据库
            AppSpecEntity appSpec = appSpecMapper.selectById(appSpecId);
            if (appSpec != null) {
                appSpec.setFrontendCodeUrl(null); // Git URL暂时为空
                appSpec.setBackendCodeUrl(null);
                appSpec.setCodeArchivePath(frontendArchivePath + "," + backendArchivePath);
                appSpec.setCodeCommitHash(null); // Git commit hash暂时为空
                appSpecMapper.updateById(appSpec);
                log.info("数据库更新完成");
            }

            return CodeStorageResult.builder()
                    .frontendArchivePath(frontendArchivePath)
                    .backendArchivePath(backendArchivePath)
                    .success(true)
                    .build();

        } catch (Exception e) {
            log.error("保存代码失败", e);
            return CodeStorageResult.builder()
                    .success(false)
                    .errorMessage(e.getMessage())
                    .build();
        }
    }

    @Override
    public CodeRetrievalResult getCode(UUID appSpecId) {
        try {
            AppSpecEntity appSpec = appSpecMapper.selectById(appSpecId);
            if (appSpec == null) {
                return CodeRetrievalResult.builder()
                        .success(false)
                        .errorMessage("AppSpec不存在")
                        .build();
            }

            String archivePath = appSpec.getCodeArchivePath();
            if (archivePath == null || archivePath.isEmpty()) {
                return CodeRetrievalResult.builder()
                        .success(false)
                        .errorMessage("代码归档路径为空")
                        .build();
            }

            String[] paths = archivePath.split(",");
            return CodeRetrievalResult.builder()
                    .frontendArchivePath(paths.length > 0 ? paths[0] : null)
                    .backendArchivePath(paths.length > 1 ? paths[1] : null)
                    .frontendCodeUrl(appSpec.getFrontendCodeUrl())
                    .backendCodeUrl(appSpec.getBackendCodeUrl())
                    .success(true)
                    .build();

        } catch (Exception e) {
            log.error("获取代码失败", e);
            return CodeRetrievalResult.builder()
                    .success(false)
                    .errorMessage(e.getMessage())
                    .build();
        }
    }

    @Override
    public String archiveCode(UUID appSpecId, String codePath, String codeType) {
        try {
            // 创建归档目录
            Path archiveDir = Paths.get(archiveBasePath, appSpecId.toString());
            Files.createDirectories(archiveDir);

            // 生成ZIP文件名
            String zipFileName = String.format("%s-%s.zip", codeType, System.currentTimeMillis());
            Path zipFilePath = archiveDir.resolve(zipFileName);

            // 压缩代码
            Path sourceDir = Paths.get(codePath);
            if (!Files.exists(sourceDir)) {
                throw new IOException("代码路径不存在: " + codePath);
            }

            try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipFilePath.toFile()))) {
                Files.walk(sourceDir)
                        .filter(path -> !Files.isDirectory(path))
                        .forEach(path -> {
                            try {
                                String zipEntryName = sourceDir.relativize(path).toString();
                                zos.putNextEntry(new ZipEntry(zipEntryName));
                                Files.copy(path, zos);
                                zos.closeEntry();
                            } catch (IOException e) {
                                log.error("压缩文件失败: {}", path, e);
                            }
                        });
            }

            log.info("代码归档完成: {}", zipFilePath);
            return zipFilePath.toString();

        } catch (Exception e) {
            log.error("归档代码失败", e);
            throw new RuntimeException("归档代码失败: " + e.getMessage(), e);
        }
    }
}
