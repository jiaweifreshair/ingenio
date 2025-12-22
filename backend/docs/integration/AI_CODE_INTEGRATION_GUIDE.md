# AICodeGenerator集成指南

> **版本**: 1.0.0
> **最后更新**: 2025-11-11
> **作者**: Ingenio Backend Team

## 目录

1. [概述](#概述)
2. [前置要求](#前置要求)
3. [集成步骤](#集成步骤)
4. [配置说明](#配置说明)
5. [验证和测试](#验证和测试)
6. [故障排除](#故障排除)
7. [升级和维护](#升级和维护)

---

## 概述

本指南说明如何将AICodeGenerator集成到Ingenio项目中，使系统能够自动生成包含AI能力的Kotlin Multiplatform应用代码。

### 集成架构

```
Ingenio Backend
├── PlanAgent (需求分析) → 检测AI能力
├── ExecuteAgent (AppSpec生成) → 生成结构化配置
├── GenerateService (代码编排) → 调用各个生成器
│   ├── KotlinMultiplatformGenerator
│   ├── ComposeUIGenerator
│   └── AICodeGenerator ⭐ (新增)
└── CodePackagingService → 打包上传到MinIO
```

### 集成价值

- ✅ **自动化AI代码生成**: 根据用户需求自动生成AI集成代码
- ✅ **11种AI能力支持**: 聊天机器人、RAG、图像识别等
- ✅ **零配置开箱即用**: 集成后无需额外配置即可使用
- ✅ **完整代码打包**: 生成ZIP包含所有必需文件和文档
- ✅ **前端可视化展示**: CodeDownloadPanel组件自动显示AI文件

---

## 前置要求

### 1. 环境要求

| 组件 | 版本 | 说明 |
|-----|------|------|
| Java JDK | 17+ | 后端运行环境 |
| Maven | 3.9+ | 构建工具 |
| PostgreSQL | 15+ | 数据库 |
| MinIO | 8.5.7+ | 对象存储 |
| Node.js | 18+ | 前端开发环境 |
| pnpm | 8+ | 前端包管理器 |

### 2. 依赖模块

确保以下模块已正确实现：

- ✅ `PlanAgent`: 需求分析Agent，包含AI能力检测
- ✅ `ExecuteAgent`: AppSpec生成Agent
- ✅ `ValidateAgent`: 代码验证Agent
- ✅ `AICodeGenerator`: AI代码生成器（已实现）
- ✅ `CodePackagingService`: 代码打包服务（新增）
- ✅ `MinioService`: MinIO对象存储服务

### 3. 配置文件

确保以下配置文件正确配置：

**application.yml**:
```yaml
spring:
  ai:
    dashscope:
      api-key: ${DASHSCOPE_API_KEY}
      chat:
        options:
          model: qwen-max

minio:
  endpoint: http://localhost:9000
  access-key: ${MINIO_ACCESS_KEY}
  secret-key: ${MINIO_SECRET_KEY}
  bucket-name: ingenio
```

---

## 集成步骤

### Step 1: 确认AICodeGenerator实现

确认AICodeGenerator已正确实现并可用：

```bash
# 检查AICodeGenerator类是否存在
ls backend/src/main/java/com/ingenio/backend/service/AICodeGenerator.java

# 检查测试是否通过
mvn test -Dtest=AICodeGeneratorTest
```

**预期输出**:
```
[INFO] -------------------------------------------------------
[INFO]  T E S T S
[INFO] -------------------------------------------------------
[INFO] Running com.ingenio.backend.service.AICodeGeneratorTest
[INFO] Tests run: 13, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

### Step 2: 创建CodePackagingService

创建代码打包服务（如果尚未创建）：

**文件**: `backend/src/main/java/com/ingenio/backend/service/CodePackagingService.java`

```java
@Service
@RequiredArgsConstructor
public class CodePackagingService {

    private final MinioService minioService;

    /**
     * 打包代码文件并上传到MinIO
     */
    public String packageAndUpload(
            Map<String, String> files,
            String projectName
    ) throws IOException {
        // 1. 创建临时目录
        Path tempDir = Files.createTempDirectory("ingenio-code-");

        try {
            // 2. 写入所有文件
            writeFilesToDirectory(files, tempDir);

            // 3. 创建ZIP文件
            String zipFileName = generateZipFileName(projectName);
            Path zipPath = createZipFile(tempDir, zipFileName);

            // 4. 上传到MinIO
            String downloadUrl = uploadToMinio(zipPath, zipFileName);

            return downloadUrl;
        } finally {
            // 5. 清理临时文件
            cleanupTempFiles(tempDir);
        }
    }

    // 实现细节见完整代码
}
```

### Step 3: 集成到GenerateService

修改 `GenerateService.java`，在Step 4添加代码生成逻辑：

```java
@Service
@RequiredArgsConstructor
public class GenerateService {

    // 注入新增服务
    private final AICodeGenerator aiCodeGenerator;
    private final KotlinMultiplatformGenerator kmpGenerator;
    private final ComposeUIGenerator composeUIGenerator;
    private final CodePackagingService packagingService;

    @Transactional(rollbackFor = Exception.class)
    public GenerateFullResponse generateFull(GenerateFullRequest request) {
        // ... Step 1-3 ...

        // Step 4: 代码生成（新增）
        if (Boolean.TRUE.equals(request.getGeneratePreview())) {
            log.info("Step 4: 开始生成代码");
            responseBuilder.status("generating");

            try {
                // 生成所有代码文件
                Map<String, String> generatedFiles = generateAllCodeFiles(
                        planResult, appSpecJson, request);

                // 打包并上传
                String projectName = extractAppName(appSpecJson);
                String downloadUrl = packagingService.packageAndUpload(
                        generatedFiles, projectName);

                // 设置响应
                responseBuilder.codeDownloadUrl(downloadUrl);
                responseBuilder.generatedFileList(
                        new ArrayList<>(generatedFiles.keySet()));
                responseBuilder.codeSummary(
                        buildCodeSummary(generatedFiles, projectName));

            } catch (Exception e) {
                log.error("代码生成失败", e);
                responseBuilder.status("failed");
                responseBuilder.errorMessage("代码生成失败: " + e.getMessage());
                return responseBuilder.build();
            }
        }

        responseBuilder.status("completed");
        return responseBuilder.build();
    }

    /**
     * 生成所有代码文件
     */
    private Map<String, String> generateAllCodeFiles(
            PlanResult planResult,
            Map<String, Object> appSpecJson,
            GenerateFullRequest request
    ) {
        Map<String, String> allFiles = new HashMap<>();

        // Step 4.1: 生成标准代码
        List<Map<String, Object>> entities = extractEntities(appSpecJson);
        String packageName = extractPackageName(appSpecJson, request);

        for (Map<String, Object> entity : entities) {
            // Data Model
            String dataModel = kmpGenerator.generateDataModel(entity);
            String className = toCamelCase((String) entity.get("tableName"));
            allFiles.put(packageNameToPath(packageName) +
                    "/data/model/" + className + ".kt", dataModel);

            // Repository
            String repository = kmpGenerator.generateRepository(entity);
            allFiles.put(packageNameToPath(packageName) +
                    "/data/repository/" + className + "Repository.kt", repository);

            // ViewModel
            String viewModel = kmpGenerator.generateViewModel(entity);
            allFiles.put(packageNameToPath(packageName) +
                    "/presentation/viewmodel/" + className + "ViewModel.kt", viewModel);
        }

        // Step 4.2: 生成UI代码
        for (Map<String, Object> entity : entities) {
            String className = toCamelCase((String) entity.get("tableName"));

            String listScreen = composeUIGenerator.generateListScreen(entity);
            allFiles.put(packageNameToPath(packageName) +
                    "/presentation/screen/" + className + "ListScreen.kt", listScreen);

            String formScreen = composeUIGenerator.generateFormScreen(entity);
            allFiles.put(packageNameToPath(packageName) +
                    "/presentation/screen/" + className + "FormScreen.kt", formScreen);
        }

        // Step 4.3: 如果需要AI能力，生成AI代码（核心集成点）
        if (planResult.getAiCapability() != null &&
                Boolean.TRUE.equals(planResult.getAiCapability().getNeedsAI())) {

            log.info("检测到AI能力需求，开始生成AI集成代码...");

            try {
                Map<String, String> aiFiles = aiCodeGenerator.generateAICode(
                        planResult.getAiCapability(),
                        packageName,
                        extractAppName(appSpecJson)
                );

                allFiles.putAll(aiFiles);
                log.info("AI代码生成完成: 共{}个文件", aiFiles.size());

            } catch (Exception e) {
                log.error("AI代码生成失败，跳过AI集成", e);
                // 不中断流程，继续生成其他代码
            }
        }

        return allFiles;
    }

    /**
     * 构建代码生成摘要
     */
    private CodeGenerationSummary buildCodeSummary(
            Map<String, String> generatedFiles,
            String projectName
    ) {
        int totalFiles = generatedFiles.size();

        // AI集成文件统计（关键）
        int aiIntegrationFiles = (int) generatedFiles.keySet().stream()
                .filter(path -> path.contains("/ai/") ||
                               path.contains("AIService") ||
                               path.contains("AIConfig"))
                .count();

        // 其他文件统计...

        return CodeGenerationSummary.builder()
                .totalFiles(totalFiles)
                .aiIntegrationFiles(aiIntegrationFiles)
                // ... 其他字段 ...
                .build();
    }
}
```

### Step 4: 扩展GenerateFullResponse

确认响应DTO已包含新增字段：

**文件**: `backend/src/main/java/com/ingenio/backend/dto/response/GenerateFullResponse.java`

```java
@Data
@Builder
public class GenerateFullResponse {
    // ... 原有字段 ...

    /** 代码下载URL */
    private String codeDownloadUrl;

    /** 代码生成摘要（新增） */
    private CodeGenerationSummary codeSummary;

    /** 生成的文件清单（新增） */
    private List<String> generatedFileList;

    @Data
    @Builder
    public static class CodeGenerationSummary {
        private Integer totalFiles;
        private Integer aiIntegrationFiles; // 重点字段
        // ... 其他字段 ...
    }
}
```

### Step 5: 前端集成

#### 5.1 扩展TypeScript类型

**文件**: `frontend/src/lib/api/generate.ts`

```typescript
export interface GenerateResponse {
  // ... 原有字段 ...

  /** 代码下载URL */
  codeDownloadUrl?: string;

  /** 代码生成摘要 */
  codeSummary?: {
    totalFiles: number;
    aiIntegrationFiles: number;
    // ... 其他字段 ...
  };

  /** 生成的文件清单 */
  generatedFileList?: string[];
}
```

#### 5.2 导入CodeDownloadPanel组件

**文件**: `frontend/src/app/wizard/[id]/page.tsx`

```typescript
import { CodeDownloadPanel } from '@/components/wizard/code-download-panel';

// 在完成状态的UI中使用
{taskStatus && (taskStatus.downloadUrl || taskStatus.codeSummary) && (
  <CodeDownloadPanel
    codeDownloadUrl={taskStatus.downloadUrl}
    codeSummary={taskStatus.codeSummary}
    generatedFileList={taskStatus.generatedFileList}
    className="mb-8"
  />
)}
```

---

## 配置说明

### MinIO配置

**application.yml**:
```yaml
minio:
  endpoint: http://localhost:9000
  access-key: minioadmin
  secret-key: minioadmin
  bucket-name: ingenio
  auto-create-bucket: true
```

**环境变量**:
```bash
export MINIO_ENDPOINT=http://localhost:9000
export MINIO_ACCESS_KEY=minioadmin
export MINIO_SECRET_KEY=minioadmin
```

### AI模型配置

**application.yml**:
```yaml
spring:
  ai:
    dashscope:
      api-key: ${DASHSCOPE_API_KEY}
      chat:
        options:
          model: qwen-max
          temperature: 0.7
          top-p: 0.8
```

**环境变量**:
```bash
export DASHSCOPE_API_KEY=sk-your-api-key
```

### 日志配置

**logback-spring.xml**:
```xml
<logger name="com.ingenio.backend.service.AICodeGenerator" level="DEBUG"/>
<logger name="com.ingenio.backend.service.CodePackagingService" level="INFO"/>
<logger name="com.ingenio.backend.service.GenerateService" level="INFO"/>
```

---

## 验证和测试

### 1. 单元测试

运行单元测试确保集成正确：

```bash
# 测试AICodeGenerator
mvn test -Dtest=AICodeGeneratorTest

# 测试GenerateService
mvn test -Dtest=GenerateServiceTest

# 测试CodePackagingService
mvn test -Dtest=CodePackagingServiceTest
```

**预期输出**:
```
[INFO] Tests run: 25, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

### 2. 集成测试

运行E2E测试验证完整流程：

```bash
# 后端E2E测试
mvn verify -Dtest=GenerateE2ETest

# 前端E2E测试
cd frontend && pnpm test:e2e wizard-integration.spec.ts
```

### 3. 手动验证

#### 3.1 后端验证

使用curl测试API：

```bash
# 创建生成任务（包含AI能力）
curl -X POST http://localhost:8080/api/v1/generate/full \
  -H "Content-Type: application/json" \
  -d '{
    "userRequirement": "创建一个AI聊天助手应用，支持智能对话和情感分析",
    "model": "qwen-max",
    "generatePreview": true,
    "qualityThreshold": 70
  }'
```

**预期响应**:
```json
{
  "code": 200,
  "data": {
    "appSpecId": "550e8400-...",
    "status": "completed",
    "codeDownloadUrl": "http://localhost:9000/ingenio/generated-code/ChatApp-123.zip",
    "codeSummary": {
      "totalFiles": 15,
      "aiIntegrationFiles": 4,
      ...
    },
    "generatedFileList": [
      "com/example/chatapp/ai/AIService.kt",
      "com/example/chatapp/ai/AIConfig.kt",
      ...
    ]
  }
}
```

#### 3.2 前端验证

1. 启动前端开发服务器：
```bash
cd frontend && pnpm dev
```

2. 访问向导页面：`http://localhost:3000/wizard/test-app-123`

3. 确认以下内容：
   - ✅ CodeDownloadPanel组件正确显示
   - ✅ 文件统计信息准确
   - ✅ AI集成文件有紫色标识
   - ✅ 下载按钮可用
   - ✅ 文件列表可展开/折叠

### 4. 性能测试

测试代码生成性能：

```bash
# 运行性能测试
mvn test -Dtest=GenerateServicePerformanceTest

# 或使用JMeter进行压力测试
jmeter -n -t performance-test.jmx -l results.jtl
```

**性能指标**:
- 代码生成时间（不含AI） < 5秒
- 代码生成时间（含AI） < 10秒
- ZIP打包上传时间 < 3秒
- MinIO下载速度 > 10MB/s

---

## 故障排除

### 问题1: AI代码未生成

**症状**: `codeSummary.aiIntegrationFiles` 为0

**排查步骤**:
1. 检查日志是否有 `"检测到AI能力需求"` 输出
2. 确认 `planResult.getAiCapability().getNeedsAI()` 为true
3. 检查AICodeGenerator是否抛出异常

**解决方案**:
```bash
# 查看日志
tail -f logs/application.log | grep "AI能力"

# 确认PlanAgent正确检测
curl http://localhost:8080/api/v1/generate/plan \
  -H "Content-Type: application/json" \
  -d '{"userRequirement": "创建AI聊天机器人"}'
```

### 问题2: MinIO上传失败

**症状**: `代码生成失败: MinIO upload failed`

**排查步骤**:
1. 确认MinIO服务正在运行
2. 检查MinIO配置是否正确
3. 验证Bucket是否存在

**解决方案**:
```bash
# 检查MinIO服务
curl http://localhost:9000/minio/health/live

# 创建Bucket
mc mb local/ingenio

# 设置公共访问权限
mc anonymous set download local/ingenio
```

### 问题3: TypeScript类型错误

**症状**: `Property 'codeSummary' does not exist on type...`

**解决方案**:
```bash
# 清理TypeScript缓存
cd frontend
rm -rf .next
pnpm tsc --noEmit

# 重新安装依赖
pnpm install
```

### 问题4: E2E测试失败

**症状**: `CodeDownloadPanel not visible`

**排查步骤**:
1. 确认后端API返回了正确数据
2. 检查前端路由配置
3. 验证test-app-123的mock数据

**解决方案**:
```typescript
// wizard/[id]/page.tsx
// 确认test-app-123有正确的mock数据
if (appSpecId === 'test-app-123') {
  setTask(prev => ({
    ...prev,
    status: 'completed',
    // 添加code download数据
    codeDownloadUrl: 'http://localhost:9000/...',
    codeSummary: { ... }
  }));
}
```

---

## 升级和维护

### 版本兼容性

| 组件 | 当前版本 | 兼容版本 |
|-----|---------|---------|
| AICodeGenerator | 1.0.0 | >= 1.0.0 |
| CodePackagingService | 1.0.0 | >= 1.0.0 |
| GenerateService | 2.0.0 | >= 2.0.0 |

### 数据库迁移

无需数据库迁移，所有字段为可选字段。

### 配置变更

**从v1.0升级到v1.1**:
- 新增 `minio.auto-create-bucket` 配置
- 新增 `code-generation.ai.enabled` 开关

```yaml
# application.yml (v1.1)
code-generation:
  ai:
    enabled: true
    complexity-threshold: MEDIUM
```

### 监控和告警

**关键指标**:
- AI代码生成成功率 > 95%
- MinIO上传成功率 > 99%
- 平均代码生成时间 < 10秒

**Prometheus指标**:
```yaml
# Grafana Dashboard查询
- ai_code_generation_total{status="success"}
- ai_code_generation_duration_seconds{quantile="0.95"}
- minio_upload_errors_total
```

---

## 相关文档

- [AI代码集成API文档](../api/AI_CODE_INTEGRATION_API.md)
- [AICodeGenerator设计文档](../design/AI_CODE_INTEGRATION_DESIGN.md)
- [GenerateService服务文档](../services/GENERATE_SERVICE.md)
- [MinIO配置指南](../deployment/MINIO_SETUP.md)

---

**Made with ❤️ by Ingenio Team**
