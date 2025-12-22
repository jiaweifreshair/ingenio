# ValidationOrchestrator API 参考文档

## 概览

ValidationOrchestrator 是 Ingenio 平台的自动化测试引擎，提供完整的四步验证流程，确保生成的代码符合质量标准。

**核心能力**：
- 🔧 **多语言编译验证**：支持 Kotlin、Java、TypeScript
- ✅ **智能测试执行**：单元测试 + E2E测试自动化
- 📊 **测试覆盖率监控**：强制要求 ≥85% 代码覆盖率
- ⚡ **性能指标验证**：P95响应时间、内存、错误率全面监控
- 🕐 **时光机集成**：自动保存验证快照，支持版本回溯

---

## 目录

1. [验证流程](#验证流程)
2. [编译验证 (CompilationValidator)](#编译验证)
3. [测试执行 (TestExecutor)](#测试执行)
4. [性能验证 (PerformanceValidator)](#性能验证)
5. [数据结构](#数据结构)
6. [错误处理](#错误处理)
7. [使用示例](#使用示例)
8. [性能优化建议](#性能优化建议)

---

## 验证流程

### 四步标准化验证流程

```
┌─────────────────────────────────────────────────────────────┐
│                  ValidationOrchestrator                     │
│                                                             │
│  ┌────────────┐    ┌────────────┐    ┌────────────┐       │
│  │  Step 1/4  │ -> │  Step 2/4  │ -> │  Step 3/4  │ ->    │
│  │  编译验证   │    │  单元测试   │    │  E2E测试   │       │
│  └────────────┘    └────────────┘    └────────────┘       │
│                                                             │
│       ┌────────────┐         ┌──────────────────┐          │
│    -> │  Step 4/4  │ ------> │  时光机快照保存   │          │
│       │  性能验证   │         └──────────────────┘          │
│       └────────────┘                                        │
└─────────────────────────────────────────────────────────────┘
```

### 流程说明

#### Step 1: 编译验证 (CompilationValidator)

**目的**：确保代码可编译，无语法错误

**验证项**：
- ✅ 编译成功（退出码 = 0）
- ✅ 零编译错误
- ⚠️ 警告数量最小化

**支持的项目类型**：
- `kmp` - Kotlin Multiplatform (Gradle)
- `spring-boot` - Spring Boot (Maven/Gradle)
- `nextjs` - Next.js (TypeScript + npm)

**失败处理**：
- 编译失败 → 立即终止验证流程
- 保存失败快照到时光机
- 返回详细错误信息（文件路径、行号、列号、错误消息）

---

#### Step 2: 单元测试 (TestExecutor)

**目的**：验证代码逻辑正确性

**验证项**：
- ✅ 所有单元测试通过
- ✅ 代码覆盖率 ≥ 85%
- ✅ 无跳过的测试用例

**支持的测试框架**：
- `JUnit 5` - Java/Kotlin
- `Vitest` - TypeScript/JavaScript

**覆盖率类型**：
- **行覆盖率 (Line Coverage)**: 代码行执行百分比
- **分支覆盖率 (Branch Coverage)**: 条件分支覆盖百分比
- **函数覆盖率 (Function Coverage)**: 函数调用覆盖百分比

**失败条件**：
- 任何测试用例失败
- 代码覆盖率 < 85%

---

#### Step 3: E2E 测试 (TestExecutor)

**目的**：验证端到端业务流程

**验证项**：
- ✅ 所有E2E场景通过
- ✅ 关键用户路径正常
- ✅ 前后端集成无误

**支持的测试框架**：
- `Playwright` - 跨浏览器E2E测试
- `Cypress` - 现代Web应用E2E测试

**测试覆盖**：
- 用户登录/注册流程
- 核心业务场景
- 错误处理和边界情况

**失败条件**：
- 任何E2E场景失败

---

#### Step 4: 性能验证 (PerformanceValidator)

**目的**：确保应用性能达标

**验证指标**：

| 指标 | 目标值 | 阻塞标准 |
|-----|-------|---------|
| **P95响应时间** | < 1000ms | > 3000ms |
| **错误率** | < 0.1% | > 1% |
| **内存使用** | < 256MB | > 512MB |
| **CPU使用率** | < 60% | > 80% |
| **数据库查询** | < 50ms | > 200ms |

**性能测试方式**：
- 并发用户数：100
- 测试持续时间：60秒
- 使用工具：wrk / k6

**失败条件**：
- P95响应时间 > 3000ms
- 错误率 > 1%
- 内存使用 > 512MB

---

## 编译验证

### CompilationValidator API

#### 核心方法

```java
public CompilationResult compile(CodeGenerationResult codeResult)
```

**参数**：
- `codeResult` - 代码生成结果，包含项目类型、项目根目录等信息

**返回值**：
- `CompilationResult` - 编译结果，包含成功状态、错误列表、警告列表

---

### 支持的编译器

#### 1. Kotlin Multiplatform (kotlinc + Gradle)

**编译命令**：
```bash
cd ${projectRoot} && ./gradlew build --no-daemon
```

**错误格式解析**：
```
e: file:///path/to/file.kt:10:5: Unresolved reference: SomeClass
```

**解析后的错误对象**：
```json
{
  "filePath": "/path/to/file.kt",
  "lineNumber": 10,
  "columnNumber": 5,
  "message": "Unresolved reference: SomeClass"
}
```

---

#### 2. Spring Boot (javac + Maven/Gradle)

**编译命令（Maven）**：
```bash
cd ${projectRoot} && mvn clean compile -DskipTests
```

**编译命令（Gradle）**：
```bash
cd ${projectRoot} && ./gradlew build -x test
```

**错误格式解析**：
```
[ERROR] /path/to/File.java:[10,5] cannot find symbol
```

**解析后的错误对象**：
```json
{
  "filePath": "/path/to/File.java",
  "lineNumber": 10,
  "columnNumber": 5,
  "message": "cannot find symbol",
  "errorCode": null
}
```

---

#### 3. Next.js (tsc + Next)

**编译命令**：
```bash
cd ${projectRoot} && npm run build
```

**错误格式解析**：
```
src/app/page.tsx(10,5): error TS2322: Type 'string' is not assignable to type 'number'
```

**解析后的错误对象**：
```json
{
  "filePath": "src/app/page.tsx",
  "lineNumber": 10,
  "columnNumber": 5,
  "errorCode": "TS2322",
  "message": "Type 'string' is not assignable to type 'number'"
}
```

---

### CompilationResult 数据结构

```java
@Data
@Builder
public class CompilationResult {
    // 编译是否成功
    private Boolean success;

    // 编译器类型 (kotlinc / javac / tsc)
    private String compiler;

    // 编译器版本
    private String compilerVersion;

    // 编译错误列表
    private List<CompilationError> errors;

    // 编译警告列表
    private List<CompilationWarning> warnings;

    // 编译耗时（毫秒）
    private Long durationMs;

    // 编译输出目录
    private String outputDirectory;

    // 编译命令
    private String command;

    // 完整输出日志
    private String fullOutput;
}
```

---

## 测试执行

### TestExecutor API

#### 核心方法

##### 1. 运行单元测试

```java
public TestResult runUnitTests(CodeGenerationResult codeResult)
```

**支持的测试框架**：
- JUnit 5 (Java/Kotlin)
- Vitest (TypeScript/JavaScript)

**验证标准**：
- ✅ 所有测试通过 (`allPassed = true`)
- ✅ 覆盖率 ≥ 85% (`coverage >= 0.85`)

---

##### 2. 运行E2E测试

```java
public TestResult runE2ETests(CodeGenerationResult codeResult)
```

**支持的测试框架**：
- Playwright (推荐)
- Cypress

**验证标准**：
- ✅ 所有E2E场景通过 (`allPassed = true`)

---

### 单元测试框架详解

#### JUnit 5 (Java/Kotlin)

**测试命令（Maven）**：
```bash
cd ${projectRoot} && mvn test
```

**测试命令（Gradle）**：
```bash
cd ${projectRoot} && ./gradlew test
```

**输出格式解析**：
```
Tests run: 10, Failures: 0, Errors: 0, Skipped: 0
```

**覆盖率报告位置**：
- Maven: `target/site/jacoco/index.html`
- Gradle: `build/reports/jacoco/test/html/index.html`

---

#### Vitest (TypeScript)

**测试命令**：
```bash
cd ${projectRoot} && npm run test:coverage
```

**输出格式解析**：
```
Test Files  1 passed (1)
     Tests  10 passed (10)
```

**覆盖率报告位置**：
- `coverage/index.html`

---

### TestResult 数据结构

```java
@Data
@Builder
public class TestResult {
    // 测试类型 (unit / integration / e2e)
    private String testType;

    // 是否全部通过
    private Boolean allPassed;

    // 总测试数
    private Integer totalTests;

    // 通过的测试数
    private Integer passedTests;

    // 失败的测试数
    private Integer failedTests;

    // 跳过的测试数
    private Integer skippedTests;

    // 测试覆盖率（0-1之间）
    private Double coverage;

    // 行覆盖率
    private Double lineCoverage;

    // 分支覆盖率
    private Double branchCoverage;

    // 函数覆盖率
    private Double functionCoverage;

    // 测试耗时（毫秒）
    private Long durationMs;

    // 失败的测试用例列表
    private List<TestFailure> failures;

    // 测试框架 (JUnit / Vitest / Playwright)
    private String framework;

    // 测试报告路径
    private String reportPath;

    // 完整输出日志
    private String fullOutput;
}
```

---

### 测试失败信息

```java
@Data
@Builder
public static class TestFailure {
    // 测试套件名称
    private String suiteName;

    // 测试用例名称
    private String testName;

    // 失败消息
    private String message;

    // 堆栈跟踪
    private String stackTrace;

    // 预期值
    private String expected;

    // 实际值
    private String actual;

    // 测试耗时（毫秒）
    private Long durationMs;
}
```

---

## 性能验证

### PerformanceValidator API

#### 核心方法

```java
public PerformanceResult validate(CodeGenerationResult codeResult)
```

**验证指标**：
- P50/P95/P99响应时间
- 内存使用和峰值内存
- CPU使用率和峰值CPU
- 数据库查询性能
- 并发用户数和RPS
- 错误率

---

### 性能指标详解

#### 响应时间指标

| 指标 | 含义 | 目标值 | 阻塞标准 |
|-----|-----|-------|---------|
| **P50** | 中位数响应时间，50%的请求都比这个值快 | < 500ms | > 1500ms |
| **P95** | 95%的请求都比这个值快 | < 1000ms | **> 3000ms** |
| **P99** | 99%的请求都比这个值快 | < 2000ms | > 5000ms |
| **Max** | 最慢的请求响应时间 | < 5000ms | > 10000ms |

---

#### 资源使用指标

| 指标 | 目标值 | 阻塞标准 | 说明 |
|-----|-------|---------|-----|
| **内存使用** | < 256MB | **> 512MB** | 平均内存占用 |
| **峰值内存** | < 384MB | > 768MB | 峰值内存占用 |
| **CPU使用率** | < 60% | > 80% | 平均CPU占用 |
| **峰值CPU** | < 75% | > 90% | 峰值CPU占用 |

---

#### 数据库性能指标

| 指标 | 目标值 | 阻塞标准 |
|-----|-------|---------|
| **平均查询时间** | < 50ms | > 200ms |
| **最慢查询** | < 100ms | > 500ms |

---

#### 并发和吞吐量指标

| 指标 | 目标值 | 说明 |
|-----|-------|-----|
| **并发用户数** | 100 | 同时在线用户数 |
| **RPS** | > 500 | 每秒请求数 |
| **错误率** | < 0.1% | **阻塞标准: > 1%** |

---

### PerformanceResult 数据结构

```java
@Data
@Builder
public class PerformanceResult {
    // 是否达标
    private Boolean passed;

    // 平均响应时间（毫秒）
    private Long avgResponseTime;

    // P50响应时间（毫秒）
    private Long p50ResponseTime;

    // P95响应时间（毫秒）
    private Long p95ResponseTime;

    // P99响应时间（毫秒）
    private Long p99ResponseTime;

    // 最大响应时间（毫秒）
    private Long maxResponseTime;

    // 最小响应时间（毫秒）
    private Long minResponseTime;

    // 内存使用（MB）
    private Long memoryUsageMb;

    // 峰值内存使用（MB）
    private Long peakMemoryUsageMb;

    // CPU使用率（0-100）
    private Double cpuUsagePercent;

    // 峰值CPU使用率
    private Double peakCpuUsagePercent;

    // 数据库查询平均耗时（毫秒）
    private Long avgDbQueryTime;

    // 最慢的数据库查询耗时
    private Long slowestDbQueryTime;

    // 并发用户数
    private Integer concurrentUsers;

    // 每秒请求数（RPS）
    private Double requestsPerSecond;

    // 错误率（0-1之间）
    private Double errorRate;

    // 测试持续时间（毫秒）
    private Long testDurationMs;

    // 性能指标详情（按接口）
    private Map<String, EndpointMetrics> endpointMetrics;

    // 失败原因
    private String failureReason;
}
```

---

### 性能验证逻辑

```java
/**
 * 检查是否满足性能要求
 * - P95响应时间 < 3000ms
 * - 错误率 < 1%
 * - 内存使用 < 512MB
 */
public boolean meetsPerformanceGoals() {
    if (p95ResponseTime != null && p95ResponseTime > 3000) {
        return false; // P95响应时间过长
    }
    if (errorRate != null && errorRate > 0.01) {
        return false; // 错误率过高
    }
    if (memoryUsageMb != null && memoryUsageMb > 512) {
        return false; // 内存使用过多
    }
    return true;
}
```

---

## 数据结构

### ValidationResult（完整验证结果）

```java
@Data
@Builder
public class ValidationResult {
    // 任务ID
    private UUID taskId;

    // 是否全部通过
    private Boolean success;

    // 失败原因（如果success=false）
    private String failureReason;

    // 编译验证结果
    private CompilationResult compilationResult;

    // 单元测试结果
    private TestResult unitTestResult;

    // E2E测试结果
    private TestResult e2eTestResult;

    // 性能验证结果
    private PerformanceResult performanceResult;

    // 开始时间
    private LocalDateTime startTime;

    // 结束时间
    private LocalDateTime endTime;

    // 总耗时（毫秒）
    private Long totalDurationMs;
}
```

---

## 错误处理

### 错误码和失败原因

#### 编译失败

**错误码**: `COMPILATION_FAILED`

**失败原因示例**：
```
编译失败: 3个错误
- /path/to/File.kt:10:5: Unresolved reference: SomeClass
- /path/to/File.kt:20:12: Type mismatch
- /path/to/File.kt:30:8: Expecting ';'
```

---

#### 单元测试失败

**错误码**: `UNIT_TEST_FAILED` / `COVERAGE_INSUFFICIENT`

**失败原因示例**：
```
单元测试失败: 2个用例失败
- UserServiceTest.testCreateUser: Expected <200> but was <400>
- UserServiceTest.testDeleteUser: NullPointerException
```

或

```
测试覆盖率不足: 78.5%（要求≥85%）
- 行覆盖率: 78.5%
- 分支覆盖率: 65.3%
- 函数覆盖率: 82.1%
```

---

#### E2E测试失败

**错误码**: `E2E_TEST_FAILED`

**失败原因示例**：
```
E2E测试失败: 1个场景失败
- 用户登录流程: Timeout waiting for selector "#login-button"
```

---

#### 性能验证失败

**错误码**: `PERFORMANCE_FAILED`

**失败原因示例**：
```
性能不达标: P95响应时间过长
- P95响应时间: 4523ms（目标: <3000ms）
- 错误率: 0.3%（目标: <1%）✅
- 内存使用: 256MB（目标: <512MB）✅
```

---

### 时光机快照集成

#### 失败快照保存

当验证失败时，系统自动保存失败快照：

```java
private void saveFailedSnapshot(UUID taskId, ValidationResult result) {
    Map<String, Object> snapshot = new HashMap<>();
    snapshot.put("validation_result", result);
    snapshot.put("status", "failed");
    snapshot.put("failure_reason", result.getFailureReason());
    snapshot.put("timestamp", LocalDateTime.now());

    // 详细的失败信息
    if (result.getCompilationResult() != null) {
        snapshot.put("compilation_errors", result.getCompilationResult().getErrorCount());
    }
    if (result.getUnitTestResult() != null) {
        snapshot.put("unit_test_coverage", result.getUnitTestResult().getCoverage());
        snapshot.put("unit_test_failures", result.getUnitTestResult().getFailedTests());
    }
    if (result.getE2eTestResult() != null) {
        snapshot.put("e2e_test_failures", result.getE2eTestResult().getFailedTests());
    }
    if (result.getPerformanceResult() != null) {
        snapshot.put("p95_response_time", result.getPerformanceResult().getP95ResponseTime());
    }

    snapshotService.createSnapshot(
        taskId,
        VersionType.VALIDATION_FAILED,
        snapshot
    );
}
```

---

#### 成功快照保存

当验证成功时，系统保存成功快照：

```java
private void saveSuccessSnapshot(UUID taskId, ValidationResult result) {
    Map<String, Object> snapshot = new HashMap<>();
    snapshot.put("validation_result", result);
    snapshot.put("status", "success");
    snapshot.put("timestamp", LocalDateTime.now());

    // 成功的统计信息
    snapshot.put("compilation_success", true);
    snapshot.put("unit_test_coverage", result.getUnitTestResult().getCoverage());
    snapshot.put("unit_test_passed", result.getUnitTestResult().getPassedTests());
    snapshot.put("e2e_test_passed", result.getE2eTestResult().getPassedTests());
    snapshot.put("p95_response_time", result.getPerformanceResult().getP95ResponseTime());
    snapshot.put("memory_usage_mb", result.getPerformanceResult().getMemoryUsageMb());
    snapshot.put("total_duration_ms", result.getTotalDurationMs());

    snapshotService.createSnapshot(
        taskId,
        VersionType.VALIDATION_SUCCESS,
        snapshot
    );
}
```

---

## 使用示例

### 基本使用

```java
@Service
@RequiredArgsConstructor
public class CodeGenerationService {

    private final ValidationOrchestrator validationOrchestrator;

    public void generateAndValidate(String prompt) {
        // Step 1: 生成代码
        CodeGenerationResult codeResult = generateCode(prompt);

        // Step 2: 完整验证流程
        ValidationResult validationResult = validationOrchestrator.validate(codeResult);

        // Step 3: 检查验证结果
        if (validationResult.getSuccess()) {
            log.info("✅ 验证全部通过!");
            log.info("编译耗时: {}ms", validationResult.getCompilationResult().getDurationMs());
            log.info("单元测试覆盖率: {}%", validationResult.getUnitTestResult().getCoverage() * 100);
            log.info("E2E测试通过率: {}/{}",
                validationResult.getE2eTestResult().getPassedTests(),
                validationResult.getE2eTestResult().getTotalTests());
            log.info("P95响应时间: {}ms", validationResult.getPerformanceResult().getP95ResponseTime());
        } else {
            log.error("❌ 验证失败: {}", validationResult.getFailureReason());

            // 查看详细错误
            if (validationResult.getCompilationResult() != null
                && !validationResult.getCompilationResult().getSuccess()) {
                logCompilationErrors(validationResult.getCompilationResult());
            }

            if (validationResult.getUnitTestResult() != null
                && !validationResult.getUnitTestResult().getAllPassed()) {
                logTestFailures(validationResult.getUnitTestResult());
            }
        }
    }

    private void logCompilationErrors(CompilationResult result) {
        result.getErrors().forEach(error -> {
            log.error("编译错误 {}:{}:{} - {}",
                error.getFilePath(),
                error.getLineNumber(),
                error.getColumnNumber(),
                error.getMessage());
        });
    }

    private void logTestFailures(TestResult result) {
        result.getFailures().forEach(failure -> {
            log.error("测试失败 {}.{} - {}",
                failure.getSuiteName(),
                failure.getTestName(),
                failure.getMessage());
        });
    }
}
```

---

### 高级用法：单独调用各步骤

```java
@Service
@RequiredArgsConstructor
public class AdvancedValidationService {

    private final CompilationValidator compilationValidator;
    private final TestExecutor testExecutor;
    private final PerformanceValidator performanceValidator;

    /**
     * 仅验证编译
     */
    public CompilationResult validateCompilationOnly(CodeGenerationResult codeResult) {
        return compilationValidator.compile(codeResult);
    }

    /**
     * 仅运行单元测试
     */
    public TestResult runUnitTestsOnly(CodeGenerationResult codeResult) {
        return testExecutor.runUnitTests(codeResult);
    }

    /**
     * 仅运行E2E测试
     */
    public TestResult runE2ETestsOnly(CodeGenerationResult codeResult) {
        return testExecutor.runE2ETests(codeResult);
    }

    /**
     * 仅验证性能
     */
    public PerformanceResult validatePerformanceOnly(CodeGenerationResult codeResult) {
        return performanceValidator.validate(codeResult);
    }

    /**
     * 自定义验证流程（跳过某些步骤）
     */
    public ValidationResult customValidate(
        CodeGenerationResult codeResult,
        boolean skipE2E,
        boolean skipPerformance
    ) {
        ValidationResult.ValidationResultBuilder resultBuilder = ValidationResult.builder()
            .taskId(codeResult.getTaskId())
            .startTime(LocalDateTime.now());

        // Step 1: 编译（必须）
        CompilationResult compilation = compilationValidator.compile(codeResult);
        resultBuilder.compilationResult(compilation);
        if (!compilation.getSuccess()) {
            return buildFailedResult(resultBuilder, "编译失败");
        }

        // Step 2: 单元测试（必须）
        TestResult unitTest = testExecutor.runUnitTests(codeResult);
        resultBuilder.unitTestResult(unitTest);
        if (!unitTest.getAllPassed() || !unitTest.meetsCoverageGoal()) {
            return buildFailedResult(resultBuilder, "单元测试未通过");
        }

        // Step 3: E2E测试（可选）
        if (!skipE2E) {
            TestResult e2eTest = testExecutor.runE2ETests(codeResult);
            resultBuilder.e2eTestResult(e2eTest);
            if (!e2eTest.getAllPassed()) {
                return buildFailedResult(resultBuilder, "E2E测试失败");
            }
        }

        // Step 4: 性能验证（可选）
        if (!skipPerformance) {
            PerformanceResult performance = performanceValidator.validate(codeResult);
            resultBuilder.performanceResult(performance);
            if (!performance.getPassed()) {
                return buildFailedResult(resultBuilder, "性能不达标");
            }
        }

        return buildSuccessResult(resultBuilder);
    }
}
```

---

### 性能测试示例

```java
@Service
@RequiredArgsConstructor
public class PerformanceTestService {

    private final PerformanceValidator performanceValidator;

    /**
     * 运行性能测试并生成详细报告
     */
    public void runPerformanceTest(CodeGenerationResult codeResult) {
        log.info("开始性能测试...");

        PerformanceResult result = performanceValidator.validate(codeResult);

        // 打印性能报告
        printPerformanceReport(result);

        // 检查是否达标
        if (result.getPassed()) {
            log.info("✅ 性能测试通过!");
        } else {
            log.error("❌ 性能测试失败: {}", result.getFailureReason());
            suggestOptimizations(result);
        }
    }

    private void printPerformanceReport(PerformanceResult result) {
        log.info("========== 性能测试报告 ==========");
        log.info("响应时间指标:");
        log.info("  - P50: {}ms", result.getP50ResponseTime());
        log.info("  - P95: {}ms (目标: <3000ms) {}",
            result.getP95ResponseTime(),
            result.getP95ResponseTime() < 3000 ? "✅" : "❌");
        log.info("  - P99: {}ms", result.getP99ResponseTime());
        log.info("  - 最大: {}ms", result.getMaxResponseTime());
        log.info("  - 最小: {}ms", result.getMinResponseTime());

        log.info("资源使用:");
        log.info("  - 内存: {}MB (目标: <512MB) {}",
            result.getMemoryUsageMb(),
            result.getMemoryUsageMb() < 512 ? "✅" : "❌");
        log.info("  - 峰值内存: {}MB", result.getPeakMemoryUsageMb());
        log.info("  - CPU: {}% (目标: <80%)", result.getCpuUsagePercent());
        log.info("  - 峰值CPU: {}%", result.getPeakCpuUsagePercent());

        log.info("吞吐量:");
        log.info("  - 并发用户: {}", result.getConcurrentUsers());
        log.info("  - RPS: {}", result.getRequestsPerSecond());
        log.info("  - 错误率: {}% (目标: <1%) {}",
            result.getErrorRate() * 100,
            result.getErrorRate() < 0.01 ? "✅" : "❌");

        log.info("数据库性能:");
        log.info("  - 平均查询时间: {}ms", result.getAvgDbQueryTime());
        log.info("  - 最慢查询: {}ms", result.getSlowestDbQueryTime());
        log.info("=================================");
    }

    private void suggestOptimizations(PerformanceResult result) {
        log.info("优化建议:");

        if (result.getP95ResponseTime() > 3000) {
            log.info("- P95响应时间过长，建议:");
            log.info("  1. 添加缓存（Redis）");
            log.info("  2. 优化数据库查询（添加索引）");
            log.info("  3. 使用异步处理");
        }

        if (result.getErrorRate() > 0.01) {
            log.info("- 错误率过高，建议:");
            log.info("  1. 检查异常处理逻辑");
            log.info("  2. 添加降级策略");
            log.info("  3. 增强输入验证");
        }

        if (result.getMemoryUsageMb() > 512) {
            log.info("- 内存使用过多，建议:");
            log.info("  1. 检查内存泄漏");
            log.info("  2. 优化对象创建（使用对象池）");
            log.info("  3. 及时释放资源");
        }

        if (result.getSlowestDbQueryTime() > 200) {
            log.info("- 数据库查询慢，建议:");
            log.info("  1. 添加数据库索引");
            log.info("  2. 优化SQL语句");
            log.info("  3. 使用读写分离");
        }
    }
}
```

---

## 性能优化建议

### 编译优化

#### 1. 使用增量编译

**Gradle**:
```groovy
// build.gradle.kts
tasks.withType<KotlinCompile> {
    incremental = true
}
```

**Maven**:
```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-compiler-plugin</artifactId>
    <configuration>
        <useIncrementalCompilation>true</useIncrementalCompilation>
    </configuration>
</plugin>
```

---

#### 2. 启用编译缓存

**Gradle**:
```groovy
// gradle.properties
org.gradle.caching=true
org.gradle.parallel=true
```

---

#### 3. 优化依赖管理

- 使用 BOM（Bill of Materials）统一依赖版本
- 避免传递依赖冲突
- 定期清理无用依赖

---

### 测试优化

#### 1. 并行运行测试

**JUnit 5**:
```properties
# junit-platform.properties
junit.jupiter.execution.parallel.enabled=true
junit.jupiter.execution.parallel.mode.default=concurrent
junit.jupiter.execution.parallel.config.strategy=dynamic
```

**Vitest**:
```typescript
// vitest.config.ts
export default defineConfig({
  test: {
    pool: 'threads',
    poolOptions: {
      threads: {
        maxThreads: 4,
        minThreads: 2
      }
    }
  }
})
```

---

#### 2. 使用测试分片

将大量测试分片执行，减少单次执行时间：

```bash
# 分成4片，执行第1片
npm run test:e2e -- --shard=1/4
```

---

#### 3. 使用TestContainers提升集成测试速度

```java
@Testcontainers
class UserServiceIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15")
        .withReuse(true); // 复用容器
}
```

---

### 性能验证优化

#### 1. 使用专业压测工具

**wrk（推荐）**:
```bash
# 100并发，持续60秒
wrk -t12 -c100 -d60s http://localhost:8080/api/users
```

**k6（更强大）**:
```javascript
import http from 'k6/http';
import { check, sleep } from 'k6';

export let options = {
  stages: [
    { duration: '30s', target: 100 }, // 30秒内增加到100并发
    { duration: '60s', target: 100 }, // 保持100并发60秒
    { duration: '30s', target: 0 },   // 30秒内降到0
  ],
};

export default function () {
  let res = http.get('http://localhost:8080/api/users');
  check(res, { 'status is 200': (r) => r.status === 200 });
  sleep(1);
}
```

---

#### 2. 启用APM监控

推荐使用：
- **Spring Boot Actuator** + **Micrometer**
- **Prometheus** + **Grafana**
- **Elastic APM**

---

#### 3. 数据库性能优化

- 添加合适的索引
- 使用连接池（HikariCP）
- 启用查询缓存
- 使用读写分离

---

## 最佳实践

### 1. 编译阶段

✅ **推荐做法**：
- 启用增量编译
- 使用构建缓存
- 定期清理无用依赖
- 使用最新稳定版编译器

❌ **不推荐做法**：
- 每次全量编译
- 忽略编译警告
- 依赖版本混乱

---

### 2. 测试阶段

✅ **推荐做法**：
- 单元测试覆盖率 ≥ 85%
- 测试用例独立、可重复
- 使用Mock隔离外部依赖
- E2E测试覆盖核心业务流程

❌ **不推荐做法**：
- 跳过测试
- 测试用例相互依赖
- 忽略边界情况
- 测试数据硬编码

---

### 3. 性能验证阶段

✅ **推荐做法**：
- 使用真实数据量测试
- 模拟真实并发场景
- 持续监控性能指标
- 建立性能基线

❌ **不推荐做法**：
- 仅在开发环境测试
- 忽略性能警告
- 没有性能监控

---

### 4. 时光机集成

✅ **推荐做法**：
- 每次验证都保存快照
- 记录详细的失败信息
- 定期清理过期快照

❌ **不推荐做法**：
- 不保存验证历史
- 快照信息不完整

---

## 故障排查

### Q1: 编译超时

**问题**：编译时间超过10分钟

**解决方案**：
1. 检查网络连接（依赖下载）
2. 启用增量编译
3. 增加编译超时时间

```java
// CompilationValidator.java
boolean finished = process.waitFor(20, TimeUnit.MINUTES); // 增加到20分钟
```

---

### Q2: 测试覆盖率不足

**问题**：测试覆盖率始终 < 85%

**解决方案**：
1. 查看覆盖率报告，找到未覆盖的代码
2. 补充测试用例
3. 排除不需要测试的代码（如DTO、配置类）

```java
// Jacoco配置
<plugin>
    <groupId>org.jacoco</groupId>
    <artifactId>jacoco-maven-plugin</artifactId>
    <configuration>
        <excludes>
            <exclude>**/dto/**</exclude>
            <exclude>**/config/**</exclude>
        </excludes>
    </configuration>
</plugin>
```

---

### Q3: E2E测试不稳定

**问题**：E2E测试时而通过时而失败

**解决方案**：
1. 增加等待时间（避免元素未加载）
2. 使用显式等待而非隐式等待
3. 确保测试数据隔离

```typescript
// Playwright
await page.waitForSelector('#login-button', { timeout: 10000 });
```

---

### Q4: 性能验证失败

**问题**：P95响应时间 > 3000ms

**解决方案**：
1. 检查数据库查询是否有索引
2. 启用缓存（Redis）
3. 使用异步处理
4. 优化算法复杂度

---

## 版本历史

| 版本 | 日期 | 变更内容 |
|-----|-----|---------|
| 1.0.0 | 2025-11-09 | 初始版本，支持Kotlin/Java/TypeScript编译验证 |

---

## 参考资料

### 官方文档
- [JUnit 5 User Guide](https://junit.org/junit5/docs/current/user-guide/)
- [Vitest Documentation](https://vitest.dev/)
- [Playwright Documentation](https://playwright.dev/)
- [Gradle Build Cache](https://docs.gradle.org/current/userguide/build_cache.html)
- [Maven Incremental Compilation](https://maven.apache.org/plugins/maven-compiler-plugin/examples/useIncrementalCompilation.html)

### 性能测试工具
- [wrk - HTTP benchmarking tool](https://github.com/wg/wrk)
- [k6 - Modern load testing tool](https://k6.io/)
- [Gatling - Enterprise load testing](https://gatling.io/)

### 代码质量工具
- [SonarQube - Code quality analysis](https://www.sonarqube.org/)
- [JaCoCo - Java Code Coverage](https://www.jacoco.org/jacoco/)
- [Istanbul - JavaScript Code Coverage](https://istanbul.js.org/)

---

## 联系方式

如有问题或建议，请联系：
- 技术团队：tech@ingenio.dev
- 问题反馈：https://github.com/ingenio/backend/issues

---

**最后更新**: 2025-11-09
**文档版本**: 1.0.0
**维护者**: Ingenio Backend Team
