# LangChain4j自动修复系统集成指南

## 概述

本文档说明如何将LangChain4j自动修复系统集成到G3引擎中，实现前端代码语法错误的自动检测和修复。

## 已完成的工作

### 1. 核心组件实现 ✅

**FrontendErrorParser** (`backend/src/main/java/com/ingenio/backend/langchain4j/FrontendErrorParser.java`)
- 解析Vite/Babel编译错误信息
- 提取文件路径、行号、列号、错误消息
- 支持Babel语法错误（如Unterminated string constant）

**LangChain4jRepairService** (`backend/src/main/java/com/ingenio/backend/langchain4j/LangChain4jRepairService.java`)
- 使用LangChain4j框架实现AI自动修复
- 集成现有的AIProvider配置（Claude/DeepSeek）
- 低温度（0.1）确保输出稳定
- 智能提取修复后的代码

**单元测试** ✅
- `FrontendErrorParserTest.java`：测试错误解析功能
- `LangChain4jRepairServiceTest.java`：测试修复服务

### 2. 依赖配置 ✅

LangChain4j依赖已在 `backend/pom.xml` 中配置：
```xml
<langchain4j.version>0.36.2</langchain4j.version>

<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j</artifactId>
    <version>${langchain4j.version}</version>
</dependency>
<dependency>
    <groupId>dev.langchain4j</groupId>
    <artifactId>langchain4j-open-ai</artifactId>
    <version>${langchain4j.version}</version>
</dependency>
```

## 集成步骤

### Step 1: 修改G3SandboxService构造函数

在 `backend/src/main/java/com/ingenio/backend/service/g3/G3SandboxService.java:140` 添加LangChain4jRepairService注入：

```java
// 添加字段
private final LangChain4jRepairService repairService;

// 修改构造函数
public G3SandboxService(
        RestTemplate restTemplate,
        ObjectMapper objectMapper,
        G3ValidationAdapter g3ValidationAdapter,
        com.ingenio.backend.service.ValidationService validationService,
        LangChain4jRepairService repairService) {  // 新增参数
    this.restTemplate = restTemplate;
    this.objectMapper = objectMapper;
    this.g3ValidationAdapter = g3ValidationAdapter;
    this.validationService = validationService;
    this.repairService = repairService;  // 新增赋值
}
```

### Step 2: 在编译失败时添加自动修复逻辑

找到G3SandboxService中处理编译失败的位置（通常在 `compileInSandbox()` 或类似方法中），添加自动修复逻辑：

```java
/**
 * 尝试自动修复前端错误
 *
 * @param errorOutput 错误输出
 * @param sandboxId 沙箱ID
 * @param files 文件列表
 * @return 是否修复成功
 */
private boolean tryAutoRepairFrontendError(
        String errorOutput,
        String sandboxId,
        List<FileContent> files) {

    log.info("尝试自动修复前端错误: sandboxId={}", sandboxId);

    // 1. 解析错误信息
    FrontendErrorParser.ParsedError parsedError =
        repairService.frontendErrorParser.parse(errorOutput);

    if (parsedError == null) {
        log.warn("无法解析错误信息，跳过自动修复");
        return false;
    }

    // 2. 找到错误文件
    String errorFilePath = parsedError.getFilePath();
    FileContent errorFile = files.stream()
            .filter(f -> f.path().endsWith(errorFilePath))
            .findFirst()
            .orElse(null);

    if (errorFile == null) {
        log.warn("未找到错误文件: {}", errorFilePath);
        return false;
    }

    // 3. 调用AI修复
    String fixedContent = repairService.autoRepairFrontendError(
            errorOutput,
            errorFile.content()
    );

    if (fixedContent == null || fixedContent.equals(errorFile.content())) {
        log.warn("AI未能生成有效的修复代码");
        return false;
    }

    // 4. 更新文件到沙箱
    try {
        FileContent updatedFile = new FileContent(
                errorFile.path(),
                fixedContent
        );

        // 同步文件到沙箱
        syncFilesToSandbox(sandboxId, List.of(updatedFile));

        log.info("自动修复成功: file={}", errorFilePath);
        return true;

    } catch (Exception e) {
        log.error("更新修复后的文件失败", e);
        return false;
    }
}
```

### Step 3: 在编译流程中集成自动修复

修改编译方法，在编译失败时尝试自动修复：

```java
public CompileResult compileInSandbox(
        String sandboxId,
        List<FileContent> files,
        Consumer<G3LogEntry> logConsumer) {

    // ... 现有的编译逻辑 ...

    // 编译失败时尝试自动修复
    if (!compileResult.success() && compileResult.stderr().contains("plugin:vite:react-babel")) {
        logConsumer.accept(G3LogEntry.info(G3LogEntry.Role.SYSTEM,
                "检测到前端语法错误，尝试自动修复..."));

        // 最多重试3次
        int maxRetries = 3;
        for (int i = 0; i < maxRetries; i++) {
            boolean repaired = tryAutoRepairFrontendError(
                    compileResult.stderr(),
                    sandboxId,
                    files
            );

            if (!repaired) {
                logConsumer.accept(G3LogEntry.warn(G3LogEntry.Role.SYSTEM,
                        String.format("自动修复失败 (尝试 %d/%d)", i + 1, maxRetries)));
                break;
            }

            logConsumer.accept(G3LogEntry.info(G3LogEntry.Role.SYSTEM,
                    String.format("自动修复成功，重新编译... (尝试 %d/%d)", i + 1, maxRetries)));

            // 重新编译
            compileResult = compileInSandbox(sandboxId, files, logConsumer);

            if (compileResult.success()) {
                logConsumer.accept(G3LogEntry.success(G3LogEntry.Role.SYSTEM,
                        "自动修复后编译成功！"));
                break;
            }
        }
    }

    return compileResult;
}
```

## 使用示例

### 场景：修复字符串未闭合错误

**错误输入**：
```javascript
const [email, setEmail] = useState(')
const [password, setPassword] = useState(')
```

**错误信息**：
```
[plugin:vite:react-babel] /home/user/app/src/pages/TeacherLogin.jsx: Unterminated string constant. (7:36)
```

**自动修复后**：
```javascript
const [email, setEmail] = useState('');
const [password, setPassword] = useState('');
```

## 测试验证

### 单元测试

```bash
cd backend
mvn test -Dtest=FrontendErrorParserTest
mvn test -Dtest=LangChain4jRepairServiceTest
```

### 集成测试

1. 启动后端服务
2. 通过G3引擎生成包含语法错误的前端代码
3. 观察日志，确认自动修复流程被触发
4. 验证修复后的代码编译成功

## 配置说明

### AI模型配置

自动修复使用现有的AIProvider配置，无需额外配置。确保以下环境变量已设置：

```bash
# Claude API（推荐）
SPRING_AI_OPENAI_API_KEY=sk-xxx
SPRING_AI_OPENAI_BASE_URL=http://127.0.0.1:8045/v1

# 或使用DeepSeek
DEEPSEEK_API_KEY=sk-xxx
```

### 修复参数调整

在 `LangChain4jRepairService.java` 中可以调整以下参数：

```java
// AI温度（0.0-1.0，越低越稳定）
.temperature(0.1)

// 超时时间
.timeout(Duration.ofSeconds(30))

// 最大重试次数（在G3SandboxService中配置）
int maxRetries = 3;
```

## 监控和日志

### 关键日志

```
INFO  - 开始自动修复: file=src/pages/TeacherLogin.jsx, line=7, error=Unterminated string constant.
INFO  - 自动修复成功: file=src/pages/TeacherLogin.jsx
INFO  - 自动修复后编译成功！
```

### 失败日志

```
WARN  - 无法解析错误信息，跳过自动修复
WARN  - AI未能生成有效的修复代码
ERROR - 自动修复失败
```

## 性能影响

- **首次修复时间**：5-10秒（AI推理时间）
- **重新编译时间**：5-10秒（Maven编译）
- **总体影响**：增加10-20秒（相比手动修复节省大量时间）

## 限制和注意事项

1. **仅支持前端语法错误**：当前版本仅支持Babel/Vite错误，不支持Java编译错误
2. **最多重试3次**：避免无限循环
3. **依赖AI质量**：修复质量取决于AI模型能力
4. **不保证100%成功**：复杂错误可能需要人工介入

## 后续优化

### 短期优化（1-2周）

1. 支持更多错误类型（TypeScript类型错误、ESLint错误）
2. 添加修复历史记录和回滚功能
3. 优化AI提示词提高修复成功率

### 长期优化（1-3个月）

1. 支持Java编译错误自动修复
2. 实现多轮对话修复（AI与用户交互）
3. 建立错误修复知识库（常见错误模式）
4. 集成到CI/CD流程

## 相关文档

- [G3引擎架构设计](./G3_ENGINE_ARCHITECTURE.md)
- [LangChain4j官方文档](https://docs.langchain4j.dev/)
- [G3沙箱服务文档](./G3_SANDBOX_SERVICE.md)

## 变更历史

| 日期 | 版本 | 变更内容 | 作者 |
|-----|------|---------|------|
| 2026-01-19 | 1.0.0 | 初始版本：实现前端语法错误自动修复 | Ingenio Team |

---

**维护者**：Ingenio DevOps Team
**最后更新**：2026-01-19
**状态**：✅ 核心组件已实现，待集成到G3SandboxService
