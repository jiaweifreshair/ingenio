package com.ingenio.backend.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import com.ingenio.backend.common.Result;
import com.ingenio.backend.dto.request.PublishRequest;
import com.ingenio.backend.dto.response.PublishResponse;
import com.ingenio.backend.service.MinioService;
import com.ingenio.backend.service.PublishService;
import com.ingenio.backend.service.QRCodeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 多端发布API控制器
 *
 * 功能：
 * 1. 创建发布任务 - 支持5大平台并行构建
 * 2. 查询构建状态 - 实时获取构建进度
 * 3. 取消构建任务 - 中止正在进行的构建
 * 4. 获取构建日志 - 查看详细构建日志
 *
 * 所有接口需要登录
 *
 * @author Ingenio Team
 * @since 1.0.0
 */
@Slf4j
@RestController
@RequestMapping("/v1/publish")
@RequiredArgsConstructor
public class PublishController {

    private final PublishService publishService;
    private final QRCodeService qrCodeService;
    private final MinioService minioService;

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
     * 创建发布任务
     *
     * 请求示例：
     * {
     *   "projectId": "550e8400-e29b-41d4-a716-446655440000",
     *   "platforms": ["android", "ios", "h5"],
     *   "platformConfigs": {
     *     "android": {
     *       "packageName": "com.ingenio.app",
     *       "appName": "IngenioApp",
     *       "versionName": "1.0.0",
     *       "versionCode": 1,
     *       "minSdkVersion": 24,
     *       "targetSdkVersion": 34
     *     },
     *     "ios": {
     *       "bundleId": "com.ingenio.app",
     *       "appName": "IngenioApp",
     *       "versionName": "1.0.0",
     *       "buildNumber": "1",
     *       "teamId": "ABCD1234",
     *       "minIosVersion": "13.0"
     *     },
     *     "h5": {
     *       "title": "IngenioApp",
     *       "domain": "app.ingenio.dev",
     *       "seoKeywords": "app,ingenio",
     *       "seoDescription": "Ingenio应用"
     *     }
     *   },
     *   "parallelBuild": true,
     *   "priority": "NORMAL"
     * }
     *
     * @param request 发布请求
     * @return 发布响应，包含构建任务ID和初始状态
     */
    @PostMapping("/create")
    @SaCheckLogin
    public Result<PublishResponse> createPublishTask(@Valid @RequestBody PublishRequest request) {
        log.info("收到发布请求 - projectId: {}, platforms: {}",
                request.getProjectId(), request.getPlatforms());

        try {
            PublishResponse response = publishService.createPublishTask(request);
            return Result.success(response);
        } catch (IllegalArgumentException e) {
            log.warn("发布请求参数错误: {}", e.getMessage());
            return Result.error(e.getMessage());
        } catch (Exception e) {
            log.error("创建发布任务失败", e);
            return Result.error("创建发布任务失败: " + e.getMessage());
        }
    }

    /**
     * 查询构建状态
     *
     * @param buildId 构建任务ID
     * @return 构建状态响应，包含每个平台的构建进度
     */
    @GetMapping("/status/{buildId}")
    @SaCheckLogin
    public Result<PublishResponse> getBuildStatus(@PathVariable String buildId) {
        log.info("查询构建状态 - buildId: {}", buildId);

        try {
            PublishResponse response = publishService.getBuildStatus(buildId);
            return Result.success(response);
        } catch (Exception e) {
            log.error("查询构建状态失败 - buildId: {}", buildId, e);
            return Result.error("查询构建状态失败: " + e.getMessage());
        }
    }

    /**
     * 取消构建任务
     *
     * @param buildId 构建任务ID
     * @return 成功消息
     */
    @PostMapping("/cancel/{buildId}")
    @SaCheckLogin
    public Result<String> cancelBuild(@PathVariable String buildId) {
        log.info("取消构建任务 - buildId: {}", buildId);

        try {
            publishService.cancelBuild(buildId);
            return Result.success("构建任务已取消");
        } catch (Exception e) {
            log.error("取消构建任务失败 - buildId: {}", buildId, e);
            return Result.error("取消构建任务失败: " + e.getMessage());
        }
    }

    /**
     * 获取构建日志
     *
     * @param buildId  构建任务ID
     * @param platform 平台类型（可选，不传则返回总体日志）
     * @return 构建日志内容
     */
    @GetMapping("/logs/{buildId}")
    @SaCheckLogin
    public Result<String> getBuildLogs(
            @PathVariable String buildId,
            @RequestParam(required = false) String platform
    ) {
        log.info("获取构建日志 - buildId: {}, platform: {}", buildId, platform);

        try {
            // TODO: 实现日志查询逻辑
            String logs = "构建日志功能开发中...";
            return Result.success(logs);
        } catch (Exception e) {
            log.error("获取构建日志失败 - buildId: {}", buildId, e);
            return Result.error("获取构建日志失败: " + e.getMessage());
        }
    }

    /**
     * 获取下载链接
     *
     * 生成带有效期的MinIO presigned URL，用于直接下载构建产物
     *
     * @param buildId  构建任务ID
     * @param platform 平台类型
     * @return 下载URL（有效期24小时）
     */
    @GetMapping("/download/{buildId}/{platform}")
    @SaCheckLogin
    public Result<String> getDownloadUrl(
            @PathVariable String buildId,
            @PathVariable String platform
    ) {
        log.info("获取下载链接 - buildId: {}, platform: {}", buildId, platform);

        try {
            // 验证平台类型
            String fileExtension = PLATFORM_FILE_EXTENSION.get(platform);
            if (fileExtension == null) {
                log.warn("不支持的平台类型 - platform: {}", platform);
                return Result.error("不支持的平台类型: " + platform);
            }

            // 构建MinIO对象名称：{buildId}/{platform}/app.{ext}
            String objectName = String.format("%s/%s/app.%s", buildId, platform, fileExtension);

            // 检查文件是否存在
            if (!minioService.fileExists(objectName)) {
                log.warn("构建产物不存在 - objectName: {}", objectName);
                return Result.error("构建产物不存在，请确认构建已完成");
            }

            // 生成presigned URL（有效期24小时）
            int expiry = 24 * 60 * 60; // 24小时
            String presignedUrl = minioService.generatePresignedUrl(objectName, expiry);

            log.info("下载链接生成成功 - buildId: {}, platform: {}, expiry: {}s",
                    buildId, platform, expiry);

            return Result.success(presignedUrl);

        } catch (Exception e) {
            log.error("获取下载链接失败 - buildId: {}, platform: {}", buildId, platform, e);
            return Result.error("获取下载链接失败: " + e.getMessage());
        }
    }

    /**
     * 获取下载二维码
     *
     * 生成包含下载链接的二维码图片，用于移动端扫码下载
     *
     * @param buildId  构建任务ID
     * @param platform 平台类型
     * @return PNG格式的二维码图片
     */
    @GetMapping("/qrcode/{buildId}/{platform}")
    @SaCheckLogin
    public ResponseEntity<byte[]> getDownloadQRCode(
            @PathVariable String buildId,
            @PathVariable String platform
    ) {
        log.info("生成下载二维码 - buildId: {}, platform: {}", buildId, platform);

        try {
            // 验证平台类型
            String fileExtension = PLATFORM_FILE_EXTENSION.get(platform);
            if (fileExtension == null) {
                log.warn("不支持的平台类型 - platform: {}", platform);
                return ResponseEntity.badRequest().build();
            }

            // 构建MinIO对象名称
            String objectName = String.format("%s/%s/app.%s", buildId, platform, fileExtension);

            // 检查文件是否存在
            if (!minioService.fileExists(objectName)) {
                log.warn("构建产物不存在 - objectName: {}", objectName);
                return ResponseEntity.notFound().build();
            }

            // 获取真实的下载链接
            int expiry = 24 * 60 * 60; // 24小时
            String downloadUrl = minioService.generatePresignedUrl(objectName, expiry);

            // 生成二维码
            byte[] qrCodeImage = qrCodeService.generateDownloadQRCode(downloadUrl);

            // 设置响应头
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.IMAGE_PNG);
            headers.setContentLength(qrCodeImage.length);
            headers.set(HttpHeaders.CACHE_CONTROL, "max-age=3600"); // 缓存1小时

            log.info("二维码生成成功 - buildId: {}, platform: {}, size: {} bytes",
                    buildId, platform, qrCodeImage.length);

            return ResponseEntity.ok()
                    .headers(headers)
                    .body(qrCodeImage);

        } catch (Exception e) {
            log.error("生成下载二维码失败 - buildId: {}, platform: {}", buildId, platform, e);
            // 返回错误状态
            return ResponseEntity.status(500).build();
        }
    }
}
