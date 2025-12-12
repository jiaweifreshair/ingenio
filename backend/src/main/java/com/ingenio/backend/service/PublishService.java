package com.ingenio.backend.service;

import com.ingenio.backend.dto.request.PublishRequest;
import com.ingenio.backend.dto.response.PublishResponse;
import com.ingenio.backend.dto.response.PublishResponse.PlatformBuildResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * 多端发布服务
 *
 * 核心功能：
 * 1. 创建发布任务 - 初始化多平台构建流程
 * 2. 查询构建状态 - 实时获取构建进度
 * 3. 取消构建任务 - 中止正在进行的构建
 * 4. 并行构建编排 - CompletableFuture并行执行
 *
 * 支持5大平台：
 * - Android: Kotlin Multiplatform + Jetpack Compose
 * - iOS: Kotlin Multiplatform + SwiftUI
 * - H5: React + Next.js
 * - 小程序: Taro + React
 * - 桌面端: Tauri + React
 *
 * @author Ingenio Team
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PublishService {

    private final MinioService minioService;
    private final BuildStatusManager buildStatusManager;

    /**
     * MinIO bucket名称
     */
    private static final String MINIO_BUCKET = "ingenio-builds";

    /**
     * 平台预计构建时间（分钟）
     */
    private static final Map<String, Integer> PLATFORM_BUILD_TIME = Map.of(
            "android", 8,
            "ios", 10,
            "h5", 3,
            "miniapp", 5,
            "desktop", 12
    );

    /**
     * 平台文件扩展名映射
     */
    private static final Map<String, String> PLATFORM_FILE_EXTENSION = Map.of(
            "android", "apk",
            "ios", "ipa",
            "h5", "zip",
            "miniapp", "zip",
            "desktop", "exe"
    );

    /**
     * 平台Content-Type映射
     */
    private static final Map<String, String> PLATFORM_CONTENT_TYPE = Map.of(
            "android", "application/vnd.android.package-archive",
            "ios", "application/octet-stream",
            "h5", "application/zip",
            "miniapp", "application/zip",
            "desktop", "application/x-msdownload"
    );

    /**
     * 创建发布任务
     *
     * @param request 发布请求，包含平台列表和配置
     * @return 发布响应，包含构建任务ID和状态
     */
    public PublishResponse createPublishTask(PublishRequest request) {
        log.info("创建发布任务 - projectId: {}, platforms: {}",
                request.getProjectId(), request.getPlatforms());

        // 生成构建任务ID
        String buildId = UUID.randomUUID().toString();

        // 计算预计完成时间
        int estimatedTime = calculateEstimatedTime(request.getPlatforms(), request.getParallelBuild());

        // 初始化平台构建结果
        Map<String, PlatformBuildResult> platformResults = request.getPlatforms().stream()
                .collect(Collectors.toMap(
                        platform -> platform,
                        platform -> PlatformBuildResult.builder()
                                .platform(platform)
                                .status("PENDING")
                                .progress(0)
                                .build()
                ));

        // 构建响应
        PublishResponse response = PublishResponse.builder()
                .buildId(buildId)
                .projectId(request.getProjectId())
                .platforms(request.getPlatforms())
                .status("PENDING")
                .platformResults(platformResults)
                .estimatedTime(estimatedTime)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        // 保存初始状态到BuildStatusManager
        buildStatusManager.saveBuildStatus(buildId, response);

        // 异步执行构建任务
        if (request.getParallelBuild()) {
            // 并行构建
            CompletableFuture.runAsync(() -> executeParallelBuild(buildId, request));
        } else {
            // 串行构建
            CompletableFuture.runAsync(() -> executeSequentialBuild(buildId, request));
        }

        log.info("发布任务创建成功 - buildId: {}, estimatedTime: {}分钟", buildId, estimatedTime);
        return response;
    }

    /**
     * 查询构建状态
     *
     * @param buildId 构建任务ID
     * @return 构建状态响应
     */
    public PublishResponse getBuildStatus(String buildId) {
        log.info("查询构建状态 - buildId: {}", buildId);

        // 从BuildStatusManager查询构建状态
        PublishResponse response = buildStatusManager.getBuildStatus(buildId);

        if (response == null) {
            // 构建任务不存在，返回空响应
            log.warn("构建任务不存在 - buildId: {}", buildId);
            throw new IllegalArgumentException("构建任务不存在: " + buildId);
        }

        return response;
    }

    /**
     * 取消构建任务
     *
     * @param buildId 构建任务ID
     */
    public void cancelBuild(String buildId) {
        log.info("取消构建任务 - buildId: {}", buildId);
        // TODO: 实现取消构建逻辑
    }

    /**
     * 并行构建所有平台
     *
     * @param buildId 构建任务ID
     * @param request 发布请求
     */
    private void executeParallelBuild(String buildId, PublishRequest request) {
        log.info("开始并行构建 - buildId: {}", buildId);

        List<CompletableFuture<Void>> futures = request.getPlatforms().stream()
                .map(platform -> CompletableFuture.runAsync(() ->
                        buildPlatform(buildId, platform, request.getPlatformConfigs().get(platform))
                ))
                .collect(Collectors.toList());

        // 等待所有平台构建完成
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .thenRun(() -> {
                    log.info("所有平台构建完成 - buildId: {}", buildId);
                    // TODO: 更新总体状态为SUCCESS
                })
                .exceptionally(ex -> {
                    log.error("并行构建失败 - buildId: {}", buildId, ex);
                    // TODO: 更新总体状态为FAILED
                    return null;
                });
    }

    /**
     * 串行构建所有平台
     *
     * @param buildId 构建任务ID
     * @param request 发布请求
     */
    private void executeSequentialBuild(String buildId, PublishRequest request) {
        log.info("开始串行构建 - buildId: {}", buildId);

        for (String platform : request.getPlatforms()) {
            try {
                buildPlatform(buildId, platform, request.getPlatformConfigs().get(platform));
            } catch (Exception e) {
                log.error("平台构建失败 - buildId: {}, platform: {}", buildId, platform, e);
                // TODO: 更新平台状态为FAILED
                break;
            }
        }

        log.info("串行构建完成 - buildId: {}", buildId);
    }

    /**
     * 构建单个平台
     *
     * @param buildId        构建任务ID
     * @param platform       平台类型
     * @param platformConfig 平台配置
     */
    private void buildPlatform(String buildId, String platform, Map<String, Object> platformConfig) {
        log.info("开始构建平台 - buildId: {}, platform: {}", buildId, platform);

        try {
            // 更新平台状态为IN_PROGRESS
            buildStatusManager.updatePlatformStatus(buildId, platform, "IN_PROGRESS", 0);

            // 模拟构建过程
            InputStream buildArtifact;
            switch (platform) {
                case "android":
                    buildArtifact = buildAndroid(platformConfig);
                    break;
                case "ios":
                    buildArtifact = buildIos(platformConfig);
                    break;
                case "h5":
                    buildArtifact = buildH5(platformConfig);
                    break;
                case "miniapp":
                    buildArtifact = buildMiniApp(platformConfig);
                    break;
                case "desktop":
                    buildArtifact = buildDesktop(platformConfig);
                    break;
                default:
                    throw new IllegalArgumentException("不支持的平台类型: " + platform);
            }

            // 更新进度：构建完成50%
            buildStatusManager.updatePlatformStatus(buildId, platform, "IN_PROGRESS", 50);

            // 上传构建产物到MinIO
            uploadBuildArtifact(buildId, platform, buildArtifact, platformConfig);

            // 更新平台状态为SUCCESS
            buildStatusManager.updatePlatformStatus(buildId, platform, "SUCCESS", 100);
            log.info("平台构建成功 - buildId: {}, platform: {}", buildId, platform);

        } catch (Exception e) {
            log.error("平台构建失败 - buildId: {}, platform: {}", buildId, platform, e);
            // 更新平台状态为FAILED，记录错误信息
            buildStatusManager.updatePlatformError(buildId, platform, e.getMessage());
            throw new RuntimeException("平台构建失败: " + platform, e);
        }
    }

    /**
     * 上传构建产物到MinIO
     *
     * @param buildId        构建任务ID
     * @param platform       平台类型
     * @param artifact       构建产物输入流
     * @param platformConfig 平台配置
     */
    private void uploadBuildArtifact(String buildId, String platform, InputStream artifact,
                                      Map<String, Object> platformConfig) {
        log.info("开始上传构建产物 - buildId: {}, platform: {}", buildId, platform);

        try {
            // 构建对象名称：{buildId}/{platform}/app.{ext}
            String fileExtension = PLATFORM_FILE_EXTENSION.get(platform);
            String objectName = String.format("%s/%s/app.%s", buildId, platform, fileExtension);

            // Content-Type
            String contentType = PLATFORM_CONTENT_TYPE.get(platform);

            // 自定义元数据
            Map<String, String> metadata = new HashMap<>();
            metadata.put("build-id", buildId);
            metadata.put("platform", platform);
            metadata.put("build-time", Instant.now().toString());
            metadata.put("content-disposition", String.format("attachment; filename=\"app.%s\"", fileExtension));

            // 添加平台配置到元数据
            if (platformConfig != null) {
                platformConfig.forEach((key, value) ->
                        metadata.put("config-" + key, String.valueOf(value)));
            }

            // 获取文件大小（对于ByteArrayInputStream）
            long fileSize = artifact.available();

            // 上传到MinIO（带重试机制）
            String uploadUrl = uploadWithRetry(objectName, artifact, contentType, fileSize, metadata, 3);

            log.info("构建产物上传成功 - buildId: {}, platform: {}, objectName: {}, size: {}B",
                    buildId, platform, objectName, fileSize);

            // 更新平台下载URL（注意：这里的uploadUrl不是presigned URL，需要通过API动态生成）
            // 这里记录MinIO对象路径，实际下载时通过API生成presigned URL
            buildStatusManager.updatePlatformDownloadUrl(buildId, platform, objectName);

        } catch (Exception e) {
            log.error("构建产物上传失败 - buildId: {}, platform: {}", buildId, platform, e);
            throw new RuntimeException("构建产物上传失败: " + platform, e);
        }
    }

    /**
     * 带重试机制的MinIO上传
     *
     * @param objectName  对象名称
     * @param inputStream 输入流
     * @param contentType Content-Type
     * @param size        文件大小
     * @param metadata    元数据
     * @param maxRetries  最大重试次数
     * @return 上传URL
     */
    private String uploadWithRetry(String objectName, InputStream inputStream, String contentType,
                                    long size, Map<String, String> metadata, int maxRetries) {
        Exception lastException = null;

        for (int attempt = 0; attempt < maxRetries; attempt++) {
            try {
                if (attempt > 0) {
                    // 指数退避：1s, 2s, 4s...
                    long backoffMillis = (long) Math.pow(2, attempt) * 1000;
                    log.info("MinIO上传重试 - attempt: {}/{}, backoff: {}ms",
                            attempt + 1, maxRetries, backoffMillis);
                    Thread.sleep(backoffMillis);
                }

                // 执行上传
                String uploadUrl = minioService.uploadFile(objectName, inputStream, contentType, size, metadata);
                log.info("MinIO上传成功 - objectName: {}, attempt: {}", objectName, attempt + 1);
                return uploadUrl;

            } catch (Exception e) {
                lastException = e;
                log.warn("MinIO上传失败 - objectName: {}, attempt: {}/{}, error: {}",
                        objectName, attempt + 1, maxRetries, e.getMessage());
            }
        }

        // 所有重试都失败
        throw new RuntimeException("MinIO上传失败，已重试" + maxRetries + "次", lastException);
    }

    /**
     * 构建Android应用
     *
     * @param config Android配置
     * @return 构建产物输入流
     */
    private InputStream buildAndroid(Map<String, Object> config) {
        log.info("构建Android应用 - config: {}", config);
        // TODO: 实现真实的Android构建逻辑
        // 1. 生成Kotlin Multiplatform代码
        // 2. 配置Gradle构建
        // 3. 执行构建生成APK
        // 4. 返回APK文件流

        // 当前为模拟构建，生成测试APK文件
        simulateBuild(8);
        return generateMockBuildArtifact("android", config);
    }

    /**
     * 构建iOS应用
     *
     * @param config iOS配置
     * @return 构建产物输入流
     */
    private InputStream buildIos(Map<String, Object> config) {
        log.info("构建iOS应用 - config: {}", config);
        // TODO: 实现真实的iOS构建逻辑
        // 1. 生成Kotlin Multiplatform代码
        // 2. 配置Xcode项目
        // 3. 执行构建生成IPA
        // 4. 返回IPA文件流

        // 当前为模拟构建，生成测试IPA文件
        simulateBuild(10);
        return generateMockBuildArtifact("ios", config);
    }

    /**
     * 构建H5网页
     *
     * @param config H5配置
     * @return 构建产物输入流
     */
    private InputStream buildH5(Map<String, Object> config) {
        log.info("构建H5网页 - config: {}", config);
        // TODO: 实现真实的H5构建逻辑
        // 1. 生成React + Next.js代码
        // 2. 执行npm build
        // 3. 生成静态文件ZIP包
        // 4. 返回ZIP文件流

        // 当前为模拟构建，生成测试ZIP文件
        simulateBuild(3);
        return generateMockBuildArtifact("h5", config);
    }

    /**
     * 构建小程序
     *
     * @param config 小程序配置
     * @return 构建产物输入流
     */
    private InputStream buildMiniApp(Map<String, Object> config) {
        log.info("构建小程序 - config: {}", config);
        // TODO: 实现真实的小程序构建逻辑
        // 1. 生成Taro + React代码
        // 2. 根据平台执行构建（微信/支付宝/字节）
        // 3. 生成代码包ZIP
        // 4. 返回ZIP文件流

        // 当前为模拟构建，生成测试ZIP文件
        simulateBuild(5);
        return generateMockBuildArtifact("miniapp", config);
    }

    /**
     * 构建桌面应用
     *
     * @param config 桌面端配置
     * @return 构建产物输入流
     */
    private InputStream buildDesktop(Map<String, Object> config) {
        log.info("构建桌面应用 - config: {}", config);
        // TODO: 实现真实的桌面端构建逻辑
        // 1. 生成Tauri + React代码
        // 2. 根据目标平台执行构建（Windows/macOS/Linux）
        // 3. 生成安装包
        // 4. 返回安装包文件流

        // 当前为模拟构建，生成测试EXE文件
        simulateBuild(12);
        return generateMockBuildArtifact("desktop", config);
    }

    /**
     * 生成模拟构建产物（用于测试MinIO上传流程）
     *
     * 注意：这是临时方法，等待真实构建引擎集成后将被替换
     *
     * @param platform 平台类型
     * @param config   平台配置
     * @return 模拟的构建产物输入流
     */
    private InputStream generateMockBuildArtifact(String platform, Map<String, Object> config) {
        // 创建包含构建信息的文本内容
        StringBuilder content = new StringBuilder();
        content.append("=== Ingenio Mock Build Artifact ===\n");
        content.append("Platform: ").append(platform).append("\n");
        content.append("Build Time: ").append(Instant.now()).append("\n");
        content.append("File Extension: ").append(PLATFORM_FILE_EXTENSION.get(platform)).append("\n");
        content.append("\n=== Platform Configuration ===\n");

        if (config != null && !config.isEmpty()) {
            config.forEach((key, value) ->
                    content.append(key).append(": ").append(value).append("\n"));
        } else {
            content.append("No configuration provided\n");
        }

        content.append("\n=== Build Instructions ===\n");
        content.append("This is a mock build artifact for testing MinIO upload.\n");
        content.append("Replace this with real build engine output in production.\n");
        content.append("\nGenerated by Ingenio Build System v1.0.0\n");

        // 将文本转换为字节数组输入流
        byte[] contentBytes = content.toString().getBytes();
        return new ByteArrayInputStream(contentBytes);
    }

    /**
     * 模拟构建过程（用于测试）
     *
     * @param minutes 构建时间（分钟）
     */
    private void simulateBuild(int minutes) {
        try {
            // 实际环境中这里是真实的构建逻辑
            // 模拟环境sleep 2秒代表构建完成
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("构建被中断", e);
        }
    }

    /**
     * 计算预计完成时间
     *
     * @param platforms     平台列表
     * @param parallelBuild 是否并行构建
     * @return 预计时间（分钟）
     */
    private int calculateEstimatedTime(List<String> platforms, Boolean parallelBuild) {
        if (parallelBuild) {
            // 并行构建：取最大构建时间
            return platforms.stream()
                    .map(p -> PLATFORM_BUILD_TIME.getOrDefault(p, 5))
                    .max(Integer::compareTo)
                    .orElse(5);
        } else {
            // 串行构建：累加所有构建时间
            return platforms.stream()
                    .map(p -> PLATFORM_BUILD_TIME.getOrDefault(p, 5))
                    .reduce(0, Integer::sum);
        }
    }
}
