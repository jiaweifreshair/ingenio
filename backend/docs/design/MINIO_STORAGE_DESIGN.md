# MinIO构建产物存储设计

> **版本**: v1.0.0
> **创建时间**: 2025-11-09
> **目的**: 定义多端发布系统的MinIO存储规范

---

## 1. Bucket设计

### 主Bucket
- **名称**: `ingenio-builds`
- **用途**: 存储所有平台的构建产物
- **访问策略**: Private（需要presigned URL访问）
- **生命周期**: 30天自动清理旧构建

### 备选Bucket（未来扩展）
- `ingenio-builds-archive`: 长期归档存储
- `ingenio-builds-cache`: 构建缓存存储

---

## 2. 路径规范

### 2.1 基础路径结构

```
ingenio-builds/
├── {buildId}/                    # 构建任务ID（UUID）
│   ├── android/
│   │   └── app.apk              # Android APK文件
│   ├── ios/
│   │   └── app.ipa              # iOS IPA文件
│   ├── h5/
│   │   └── app.zip              # H5静态文件包
│   ├── miniapp/
│   │   ├── wechat.zip           # 微信小程序包
│   │   ├── alipay.zip           # 支付宝小程序包
│   │   └── bytedance.zip        # 字节小程序包
│   └── desktop/
│       ├── app-windows.exe      # Windows安装包
│       ├── app-macos.dmg        # macOS安装包
│       └── app-linux.deb        # Linux安装包
```

### 2.2 文件命名规范

| 平台 | 文件名 | 扩展名 | 示例 |
|-----|--------|--------|------|
| **Android** | `app.apk` | `.apk` | `550e8400.../android/app.apk` |
| **iOS** | `app.ipa` | `.ipa` | `550e8400.../ios/app.ipa` |
| **H5** | `app.zip` | `.zip` | `550e8400.../h5/app.zip` |
| **小程序** | `{platform}.zip` | `.zip` | `550e8400.../miniapp/wechat.zip` |
| **桌面端** | `app-{os}.{ext}` | 见下表 | `550e8400.../desktop/app-windows.exe` |

**桌面端文件扩展名映射**：
- Windows: `.exe`
- macOS: `.dmg`
- Linux: `.deb` / `.rpm` / `.AppImage`

---

## 3. URL生成策略

### 3.1 Presigned URL配置

```java
// 生成有时效性的下载链接
String presignedUrl = minioService.generatePresignedUrl(
    "ingenio-builds",                    // bucket
    "{buildId}/{platform}/app.{ext}",    // objectName
    24 * 60 * 60                         // 有效期：24小时
);
```

**关键参数**：
- **有效期**: 24小时（86400秒）
- **HTTP方法**: GET
- **响应类型**: `application/octet-stream` (强制下载)

### 3.2 URL示例

```
# Android下载链接
https://minio.ingenio.dev/ingenio-builds/550e8400-e29b-41d4-a716-446655440000/android/app.apk?
X-Amz-Algorithm=AWS4-HMAC-SHA256&
X-Amz-Credential=minio/20251109/us-east-1/s3/aws4_request&
X-Amz-Date=20251109T100000Z&
X-Amz-Expires=86400&
X-Amz-SignedHeaders=host&
X-Amz-Signature=abc123...

# iOS下载链接
https://minio.ingenio.dev/ingenio-builds/550e8400-e29b-41d4-a716-446655440000/ios/app.ipa?...
```

---

## 4. 文件元数据

### 4.1 自定义Metadata

每个上传文件附加以下元数据：

```java
Map<String, String> metadata = Map.of(
    "build-id", buildId,
    "platform", platform,
    "project-id", projectId,
    "version-name", versionName,
    "build-time", LocalDateTime.now().toString(),
    "content-disposition", "attachment; filename=\"" + fileName + "\""
);
```

### 4.2 Content-Type映射

| 扩展名 | Content-Type | 用途 |
|--------|-------------|------|
| `.apk` | `application/vnd.android.package-archive` | Android APK |
| `.ipa` | `application/octet-stream` | iOS IPA |
| `.zip` | `application/zip` | 压缩包 |
| `.exe` | `application/x-msdownload` | Windows可执行文件 |
| `.dmg` | `application/x-apple-diskimage` | macOS镜像 |
| `.deb` | `application/vnd.debian.binary-package` | Debian包 |

---

## 5. 上传策略

### 5.1 分片上传配置

对于大文件（>100MB），使用分片上传：

```java
// 分片大小：10MB
long partSize = 10 * 1024 * 1024;

// 启用分片上传
minioService.uploadFileWithMultipart(
    "ingenio-builds",
    objectName,
    fileInputStream,
    fileSize,
    partSize
);
```

### 5.2 上传错误处理

```java
try {
    minioService.uploadFile(bucket, objectName, file, metadata);
} catch (MinioException e) {
    // 重试3次
    for (int i = 0; i < 3; i++) {
        try {
            Thread.sleep(1000 * (i + 1)); // 指数退避
            minioService.uploadFile(bucket, objectName, file, metadata);
            break;
        } catch (MinioException retryException) {
            if (i == 2) throw retryException;
        }
    }
}
```

---

## 6. 生命周期管理

### 6.1 自动清理策略

使用MinIO生命周期规则自动清理30天前的构建：

```xml
<LifecycleConfiguration>
  <Rule>
    <ID>expire-old-builds</ID>
    <Status>Enabled</Status>
    <Prefix>ingenio-builds/</Prefix>
    <Expiration>
      <Days>30</Days>
    </Expiration>
  </Rule>
</LifecycleConfiguration>
```

### 6.2 手动清理API

```java
// 清理指定构建的所有文件
public void cleanupBuild(String buildId) {
    String prefix = buildId + "/";
    minioService.removeObjects("ingenio-builds", prefix);
}
```

---

## 7. 安全性设计

### 7.1 访问控制

- ✅ **Bucket私有**: 所有Bucket设置为Private
- ✅ **Presigned URL**: 仅通过有时效的presigned URL访问
- ✅ **认证检查**: 生成下载链接前验证用户权限
- ✅ **HTTPS强制**: 所有URL使用HTTPS协议

### 7.2 防盗链

```java
// 在生成presigned URL时添加IP限制
String clientIp = request.getRemoteAddr();
String presignedUrl = minioService.generatePresignedUrlWithIpRestriction(
    bucket,
    objectName,
    24 * 60 * 60,
    clientIp  // 仅允许此IP访问
);
```

---

## 8. 监控和告警

### 8.1 关键指标

| 指标 | 阈值 | 告警级别 |
|-----|------|---------|
| 上传失败率 | >5% | Warning |
| 上传失败率 | >10% | Critical |
| 平均上传时间 | >30s | Warning |
| 存储空间使用率 | >80% | Warning |
| 存储空间使用率 | >90% | Critical |

### 8.2 日志记录

```java
log.info("MinIO上传 - buildId: {}, platform: {}, size: {}MB, duration: {}ms",
    buildId, platform, fileSizeMB, uploadDuration);
```

---

## 9. 实现清单

### 9.1 需要修改的文件

- [ ] `PublishService.java` - 集成MinIO上传逻辑
- [ ] `PublishController.java` - 修改`getDownloadUrl()`使用真实MinIO URL
- [ ] `application.yml` - 添加MinIO配置

### 9.2 需要的环境变量

```yaml
# MinIO配置
minio:
  endpoint: ${MINIO_ENDPOINT:http://localhost:9000}
  access-key: ${MINIO_ACCESS_KEY:minioadmin}
  secret-key: ${MINIO_SECRET_KEY:minioadmin}
  bucket: ingenio-builds
  region: us-east-1
```

---

## 10. 测试计划

### 10.1 单元测试

- [ ] MinIO上传成功场景
- [ ] MinIO上传失败重试机制
- [ ] Presigned URL生成验证
- [ ] 元数据正确性验证

### 10.2 集成测试

- [ ] 完整构建流程：构建 → 上传 → 生成下载链接
- [ ] 大文件上传（>100MB）
- [ ] 并发上传（5个平台并行）

### 10.3 E2E测试

- [ ] 用户创建发布任务 → 等待构建完成 → 下载APK
- [ ] QR码扫描 → 跳转下载页 → 成功下载

---

## 11. 部署检查清单

### 11.1 生产环境配置

- [ ] MinIO服务已部署（版本≥8.5.7）
- [ ] Bucket `ingenio-builds` 已创建
- [ ] 访问策略设置为Private
- [ ] 生命周期规则已配置（30天过期）
- [ ] 环境变量已设置
- [ ] HTTPS证书已配置

### 11.2 性能基准

- 上传速度（100MB文件）: <30秒
- 下载速度（100MB文件）: <20秒
- Presigned URL生成: <100ms
- 并发上传（5个平台）: <60秒

---

**Made with ❤️ by Ingenio Team**
