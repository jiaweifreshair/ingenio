# Ingenio E2E测试完整指南

## 📋 目录

- [1. 概述](#1-概述)
- [2. E2E测试架构](#2-e2e测试架构)
- [3. 零Mock策略](#3-零mock策略)
- [4. 测试环境配置](#4-测试环境配置)
- [5. 测试用例组织结构](#5-测试用例组织结构)
- [6. 编写E2E测试的最佳实践](#6-编写e2e测试的最佳实践)
- [7. 运行测试](#7-运行测试)
- [8. 测试覆盖率报告](#8-测试覆盖率报告)
- [9. CI/CD集成](#9-cicd集成)
- [10. 常见问题排查](#10-常见问题排查)

---

## 1. 概述

### 1.1 什么是E2E测试

端到端（End-to-End）测试是一种验证整个应用程序工作流程的测试方法，从用户界面到数据库层，覆盖所有系统组件的集成测试。

### 1.2 Ingenio项目E2E测试特点

- **TestContainers驱动**：使用Docker容器运行真实PostgreSQL数据库
- **零Mock策略**：不使用Mock对象，所有依赖使用真实实例
- **Spring Boot集成**：基于`@SpringBootTest`启动完整应用上下文
- **MockMvc测试**：通过MockMvc模拟HTTP请求，无需启动真实服务器
- **自动化清理**：每个测试前自动清理数据，保证测试独立性

### 1.3 技术栈

| 组件 | 技术 | 版本 |
|-----|------|------|
| 测试框架 | JUnit 5 | Spring Boot 3.4.0内置 |
| 容器化 | TestContainers | 1.19.3 |
| 数据库 | PostgreSQL | 14-alpine |
| HTTP测试 | MockMvc | Spring Boot内置 |
| JSON断言 | JsonPath | Spring Boot内置 |
| 构建工具 | Maven | 3.8+ |

---

## 2. E2E测试架构

### 2.1 整体架构图

```
┌─────────────────────────────────────────────────────────────┐
│                      E2E测试层                               │
│  ┌──────────────────┐  ┌──────────────────┐                │
│  │ TimeMachineE2E   │  │ SuperDesignE2E   │  (继承)         │
│  └──────────────────┘  └──────────────────┘                │
│           │                      │                          │
│           └──────────┬───────────┘                          │
│                      ▼                                      │
│           ┌──────────────────┐                             │
│           │  BaseE2ETest     │  (抽象基类)                  │
│           └──────────────────┘                             │
└─────────────────────────────────────────────────────────────┘
                       │
                       ▼
┌─────────────────────────────────────────────────────────────┐
│                  Spring Boot应用层                           │
│  ┌──────────┐  ┌──────────┐  ┌──────────┐                 │
│  │Controller│→ │ Service  │→ │  Mapper  │                 │
│  └──────────┘  └──────────┘  └──────────┘                 │
└─────────────────────────────────────────────────────────────┘
                       │
                       ▼
┌─────────────────────────────────────────────────────────────┐
│              TestContainers基础设施层                        │
│  ┌────────────────────────┐  ┌──────────────────┐         │
│  │ PostgreSQL Container   │  │ Redis Container  │         │
│  │   (postgres:14-alpine) │  │  (redis:7-alpine)│         │
│  └────────────────────────┘  └──────────────────┘         │
└─────────────────────────────────────────────────────────────┘
```

### 2.2 核心组件

#### 2.2.1 BaseE2ETest（抽象基类）

**职责**：
- 配置TestContainers，启动PostgreSQL容器
- 配置Spring Boot测试环境（`@SpringBootTest`）
- 提供MockMvc实例用于HTTP请求测试
- 动态注入数据库连接配置
- 提供setUp钩子供子类初始化测试数据

**关键代码解析**：

```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
public abstract class BaseE2ETest {

    // PostgreSQL容器定义
    @Container
    protected static PostgreSQLContainer<?> postgresContainer =
        new PostgreSQLContainer<>("postgres:14-alpine")
            .withDatabaseName("ingenio_test")
            .withUsername("test_user")
            .withPassword("test_password")
            .withReuse(true); // 容器重用，加快测试速度

    // 动态配置数据源属性
    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgresContainer::getJdbcUrl);
        registry.add("spring.datasource.username", postgresContainer::getUsername);
        registry.add("spring.datasource.password", postgresContainer::getPassword);
    }

    @Autowired
    protected MockMvc mockMvc; // HTTP请求测试工具
}
```

**注解说明**：
- `@SpringBootTest(webEnvironment = RANDOM_PORT)`：启动完整Spring Boot应用，使用随机端口
- `@AutoConfigureMockMvc`：自动配置MockMvc
- `@Testcontainers`：启用TestContainers支持
- `@ActiveProfiles("test")`：激活test配置文件
- `@Container`：标记TestContainers容器
- `@DynamicPropertySource`：动态设置Spring属性

#### 2.2.2 具体测试类

**TimeMachineE2ETest**：测试时光机API（版本管理）
- 测试版本时间线查询
- 测试版本详情获取
- 测试版本差异对比
- 测试版本回滚
- 测试版本删除
- 测试错误处理场景

**SuperDesignE2ETest**：测试AI设计生成API
- 测试设计示例获取
- 测试3个设计方案并行生成（需要真实API KEY）
- 测试并行执行性能
- 测试生成代码包含Kotlin语法
- 测试请求参数校验

### 2.3 TestContainers容器管理

#### 2.3.1 容器生命周期

```
测试套件开始 → 启动PostgreSQL容器 → 运行所有测试 → 销毁容器
```

#### 2.3.2 容器重用机制

```java
.withReuse(true); // 启用容器重用
```

**优势**：
- **加速测试**：多次运行测试时复用同一容器，避免重复启动
- **资源节省**：减少Docker容器的创建和销毁开销
- **一致性**：确保所有测试使用相同的数据库环境

**注意**：容器重用需要在Docker Desktop中启用，且需要`.testcontainers.properties`配置文件：

```properties
testcontainers.reuse.enable=true
```

---

## 3. 零Mock策略

### 3.1 什么是零Mock策略

**零Mock策略**是指E2E测试中不使用Mock对象模拟任何依赖，所有服务、数据库、第三方API都使用真实实例。

### 3.2 Ingenio项目的零Mock实践

#### 3.2.1 真实数据库（PostgreSQL）

```java
// ❌ 不使用Mock数据库
// @Mock
// private UserRepository userRepository;

// ✅ 使用真实PostgreSQL容器
@Container
protected static PostgreSQLContainer<?> postgresContainer =
    new PostgreSQLContainer<>("postgres:14-alpine");
```

**验证方式**：
- 测试前插入真实数据到PostgreSQL
- 测试后查询数据库验证数据变更
- 支持数据库事务回滚测试

#### 3.2.2 真实Spring Boot应用

```java
// ❌ 不使用Mock Controller
// @WebMvcTest(TimeMachineController.class)

// ✅ 启动完整Spring Boot应用
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
```

**验证方式**：
- 测试真实的Controller → Service → Mapper调用链
- 验证Spring依赖注入、AOP、事务管理
- 测试全局异常处理器

#### 3.2.3 真实第三方API（需要环境变量）

```java
@Test
@EnabledIfEnvironmentVariable(named = "DASHSCOPE_API_KEY", matches = ".+")
public void testGenerateVariants() throws Exception {
    // ✅ 调用真实的阿里云通义千问API
    // 不使用WireMock或MockServer
}
```

**验证方式**：
- 测试真实API的响应格式和数据结构
- 验证并行请求处理能力
- 测试API超时、限流等边界情况

### 3.3 零Mock策略的优势

| 优势 | 说明 |
|-----|------|
| **真实性** | 测试环境与生产环境高度一致，发现真实问题 |
| **可靠性** | 避免Mock对象与真实实现不一致导致的误判 |
| **覆盖性** | 测试完整的集成路径，包括事务、连接池、并发等 |
| **维护性** | 无需维护大量Mock规则，代码更简洁 |

### 3.4 零Mock策略的挑战

| 挑战 | 解决方案 |
|-----|---------|
| **测试速度** | 使用TestContainers容器重用机制 |
| **数据隔离** | 每个测试前清理数据库，使用事务回滚 |
| **外部依赖** | 使用`@EnabledIfEnvironmentVariable`条件执行 |
| **成本控制** | 在CI/CD中使用免费额度或Mock特定外部服务 |

---

## 4. 测试环境配置

### 4.1 application-test.yml配置文件

**位置**：`src/test/resources/application-test.yml`

```yaml
# Ingenio Backend - E2E测试配置
spring:
  application:
    name: ingenio-backend-test

  # 数据源配置（由TestContainers动态提供）
  datasource:
    driver-class-name: org.postgresql.Driver
    hikari:
      maximum-pool-size: 5
      minimum-idle: 2

  # Jackson配置
  jackson:
    default-property-inclusion: non_null
    time-zone: Asia/Shanghai
    date-format: yyyy-MM-dd HH:mm:ss

# MyBatis-Plus配置
mybatis-plus:
  configuration:
    map-underscore-to-camel-case: true
    log-impl: org.apache.ibatis.logging.nop.NoOpImpl
  global-config:
    db-config:
      id-type: auto

# SaToken配置（测试环境简化配置）
sa-token:
  token-name: Authorization
  timeout: 3600
  is-concurrent: true
  is-share: false
  token-style: jwt
  is-log: false
  jwt-secret-key: test-jwt-secret-key

# 服务器配置
server:
  port: 0  # 随机端口
  servlet:
    context-path: /api

# 日志配置（测试环境减少日志输出）
logging:
  level:
    root: WARN
    com.ingenio.backend: INFO
    com.baomidou.mybatisplus: WARN
    org.springframework.ai: WARN
```

### 4.2 Maven依赖配置

**位置**：`pom.xml`

```xml
<!-- TestContainers JUnit Jupiter Support -->
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>junit-jupiter</artifactId>
    <version>1.19.3</version>
    <scope>test</scope>
</dependency>

<!-- TestContainers PostgreSQL Module -->
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>postgresql</artifactId>
    <version>1.19.3</version>
    <scope>test</scope>
</dependency>

<!-- Spring Boot Test Starter -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-test</artifactId>
    <scope>test</scope>
</dependency>

<!-- PostgreSQL驱动 -->
<dependency>
    <groupId>org.postgresql</groupId>
    <artifactId>postgresql</artifactId>
    <version>42.7.1</version>
    <scope>runtime</scope>
</dependency>
```

### 4.3 Docker环境要求

E2E测试依赖Docker运行TestContainers，请确保：

```bash
# 检查Docker是否运行
docker ps

# 检查Docker版本
docker --version
# 要求：Docker 20.10+

# 检查Docker Compose版本（可选）
docker-compose --version
```

### 4.4 环境变量配置

#### 4.4.1 本地开发环境

**方式1：IDE配置（推荐）**

IntelliJ IDEA：
1. Run → Edit Configurations
2. 选择测试配置
3. Environment variables添加：`DASHSCOPE_API_KEY=sk-xxx`

**方式2：命令行配置**

```bash
# macOS/Linux
export DASHSCOPE_API_KEY=sk-xxx
mvn test

# Windows
set DASHSCOPE_API_KEY=sk-xxx
mvn test
```

#### 4.4.2 CI/CD环境

GitHub Actions示例：

```yaml
- name: Run E2E Tests
  env:
    DASHSCOPE_API_KEY: ${{ secrets.DASHSCOPE_API_KEY }}
  run: mvn test -Dtest=**/*E2ETest
```

---

## 5. 测试用例组织结构

### 5.1 目录结构

```
backend/src/test/java/com/ingenio/backend/
├── e2e/                            # E2E测试包
│   ├── BaseE2ETest.java            # 抽象基类
│   ├── TimeMachineE2ETest.java     # 时光机API测试
│   ├── SuperDesignE2ETest.java     # AI设计生成测试
│   └── (future) UserE2ETest.java   # 用户管理测试
└── unit/                           # 单元测试包
    ├── service/
    └── mapper/

backend/src/test/resources/
├── application-test.yml            # 测试配置
├── data/                           # 测试数据文件
└── .testcontainers.properties      # TestContainers配置
```

### 5.2 测试类命名规范

| 类型 | 命名规范 | 示例 |
|-----|---------|------|
| 基类 | `Base*Test` | `BaseE2ETest` |
| E2E测试 | `*E2ETest` | `TimeMachineE2ETest` |
| 单元测试 | `*Test` | `UserServiceTest` |
| 集成测试 | `*IntegrationTest` | `DatabaseIntegrationTest` |

### 5.3 测试方法命名规范

```java
@Test
@DisplayName("测试1: 获取版本时间线")
public void testGetTimeline() throws Exception {
    // 测试实现
}
```

**命名建议**：
- 方法名：`test + 功能描述`（驼峰命名）
- DisplayName：`测试X: 中文功能描述`
- 保持简洁，描述测试的业务场景

### 5.4 测试数据管理

#### 5.4.1 在setUp中初始化

```java
@Override
@BeforeEach
public void setUp() {
    super.setUp();

    // 清理旧数据
    versionMapper.delete(null);
    taskMapper.delete(null);

    // 创建测试数据
    testTaskId = UUID.randomUUID();
    GenerationTaskEntity task = new GenerationTaskEntity();
    task.setId(testTaskId);
    task.setUserRequirement("创建一个图书管理系统");
    task.setStatus("processing");
    taskMapper.insert(task);
}
```

#### 5.4.2 使用Builder模式

```java
DesignRequest request = DesignRequest.builder()
    .taskId(UUID.randomUUID())
    .userPrompt("创建一个待办事项应用")
    .entities(List.of(
        DesignRequest.EntityInfo.builder()
            .name("todo")
            .displayName("待办事项")
            .primaryFields(List.of("title", "description"))
            .viewType("list")
            .build()
    ))
    .targetPlatform("android")
    .uiFramework("compose_multiplatform")
    .build();
```

---

## 6. 编写E2E测试的最佳实践

### 6.1 八步标准化测试流程

#### Step 1: 需求理解（5分钟）
- 阅读业务需求文档
- 理解API的输入输出
- 明确测试覆盖范围

#### Step 2: 测试场景设计（10分钟）
- 正常场景：成功的业务流程
- 异常场景：错误处理、边界条件
- 性能场景：并发、大数据量

#### Step 3: 测试数据准备（10分钟）
- 设计测试数据模型
- 准备多组测试数据
- 考虑数据关联关系

#### Step 4: 编写测试骨架（5分钟）
```java
@Test
@DisplayName("测试X: 功能描述")
public void testFeatureName() throws Exception {
    // Given: 准备测试数据

    // When: 执行测试操作

    // Then: 验证结果
}
```

#### Step 5: 实现测试逻辑（30分钟）
- 使用MockMvc发送HTTP请求
- 使用JsonPath断言响应
- 验证数据库状态变更

#### Step 6: 运行测试（5分钟）
```bash
mvn test -Dtest=TimeMachineE2ETest
```

#### Step 7: 优化和重构（10分钟）
- 提取公共方法
- 优化断言可读性
- 添加详细注释

#### Step 8: 代码审查（10分钟）
- 检查测试覆盖率
- 验证测试独立性
- 确保测试可维护性

### 6.2 MockMvc使用技巧

#### 6.2.1 GET请求

```java
mockMvc.perform(get("/api/v1/timemachine/timeline/{taskId}", testTaskId)
        .contentType(MediaType.APPLICATION_JSON))
    .andExpect(status().isOk())
    .andExpect(jsonPath("$.code").value(200))
    .andExpect(jsonPath("$.data", hasSize(2)));
```

#### 6.2.2 POST请求

```java
String requestJson = objectMapper.writeValueAsString(request);

mockMvc.perform(post("/api/v1/superdesign/generate")
        .contentType(MediaType.APPLICATION_JSON)
        .content(requestJson))
    .andExpect(status().isOk())
    .andExpect(jsonPath("$.data", hasSize(3)));
```

#### 6.2.3 带查询参数

```java
mockMvc.perform(get("/api/v1/timemachine/diff")
        .param("version1", testVersionId1.toString())
        .param("version2", testVersionId2.toString())
        .contentType(MediaType.APPLICATION_JSON))
    .andExpect(status().isOk());
```

#### 6.2.4 DELETE请求

```java
mockMvc.perform(delete("/api/v1/timemachine/version/{versionId}", testVersionId2)
        .contentType(MediaType.APPLICATION_JSON))
    .andExpect(status().isOk())
    .andExpect(jsonPath("$.message").value("版本删除成功"));
```

### 6.3 JsonPath断言技巧

#### 6.3.1 基础断言

```java
// 断言状态码
.andExpect(jsonPath("$.code").value(200))

// 断言布尔值
.andExpect(jsonPath("$.success").value(true))

// 断言字符串
.andExpect(jsonPath("$.message").value("操作成功"))

// 断言数字
.andExpect(jsonPath("$.data.versionNumber").value(1))
```

#### 6.3.2 集合断言

```java
// 断言数组长度
.andExpect(jsonPath("$.data", hasSize(2)))

// 断言数组不为空
.andExpect(jsonPath("$.data", not(empty())))

// 断言数组元素
.andExpect(jsonPath("$.data[0].versionNumber").value(2))
.andExpect(jsonPath("$.data[1].versionNumber").value(1))
```

#### 6.3.3 嵌套对象断言

```java
.andExpect(jsonPath("$.data.snapshot.techStack.backend").value("Spring Boot"))
.andExpect(jsonPath("$.data.colorTheme.primaryColor").value("#6200EE"))
```

#### 6.3.4 动态值断言

```java
// 断言存在性
.andExpect(jsonPath("$.data.id").exists())

// 断言非空
.andExpect(jsonPath("$.data.userPrompt").isNotEmpty())

// 断言数字类型
.andExpect(jsonPath("$.data.generationTimeMs").isNumber())

// 断言大于0
.andExpect(jsonPath("$.data", hasSize(greaterThan(0))))
```

### 6.4 测试独立性原则

#### 6.4.1 数据隔离

```java
@BeforeEach
public void setUp() {
    // 每个测试前清理数据
    versionMapper.delete(null);
    taskMapper.delete(null);

    // 重新初始化测试数据
    createTestData();
}
```

#### 6.4.2 避免测试间依赖

```java
// ❌ 错误：依赖其他测试的数据
@Test
public void test2() {
    // 假设test1已经创建了数据
    mockMvc.perform(get("/api/v1/user/{id}", testUserId));
}

// ✅ 正确：每个测试独立准备数据
@Test
public void test2() {
    UUID userId = createTestUser();
    mockMvc.perform(get("/api/v1/user/{id}", userId));
}
```

### 6.5 条件测试执行

#### 6.5.1 基于环境变量

```java
@Test
@EnabledIfEnvironmentVariable(named = "DASHSCOPE_API_KEY", matches = ".+")
public void testRealAPI() {
    // 仅在API KEY存在时运行
}
```

#### 6.5.2 基于系统属性

```java
@Test
@EnabledIfSystemProperty(named = "test.integration", matches = "true")
public void testIntegration() {
    // 仅在集成测试模式下运行
}
```

### 6.6 性能测试

```java
@Test
public void testPerformance() throws Exception {
    long startTime = System.currentTimeMillis();

    mockMvc.perform(post("/api/v1/superdesign/generate")
            .contentType(MediaType.APPLICATION_JSON)
            .content(requestJson))
        .andExpect(status().isOk());

    long totalTime = System.currentTimeMillis() - startTime;

    // 验证响应时间 < 45秒
    assertTrue(totalTime < 45000,
        "API响应时间过长: " + totalTime + "ms");
}
```

---

## 7. 运行测试

### 7.1 本地运行

#### 7.1.1 运行所有E2E测试

```bash
# 使用Maven
mvn test -Dtest=**/*E2ETest

# 使用Maven Wrapper
./mvnw test -Dtest=**/*E2ETest
```

#### 7.1.2 运行单个测试类

```bash
mvn test -Dtest=TimeMachineE2ETest
```

#### 7.1.3 运行单个测试方法

```bash
mvn test -Dtest=TimeMachineE2ETest#testGetTimeline
```

#### 7.1.4 跳过需要API KEY的测试

```bash
# 不设置DASHSCOPE_API_KEY环境变量
# 标记@EnabledIfEnvironmentVariable的测试会自动跳过
mvn test -Dtest=**/*E2ETest
```

#### 7.1.5 运行需要API KEY的测试

```bash
# macOS/Linux
export DASHSCOPE_API_KEY=sk-xxx
mvn test -Dtest=SuperDesignE2ETest

# Windows
set DASHSCOPE_API_KEY=sk-xxx
mvn test -Dtest=SuperDesignE2ETest
```

### 7.2 IDE运行

#### 7.2.1 IntelliJ IDEA

**方式1：右键运行**
1. 打开测试类（如`TimeMachineE2ETest.java`）
2. 右键点击类名或方法名
3. 选择"Run 'TimeMachineE2ETest'"

**方式2：绿色箭头**
1. 点击类名或方法名旁的绿色箭头
2. 选择"Run"或"Debug"

**方式3：快捷键**
- 运行：`Ctrl+Shift+F10`（Windows/Linux）或`Control+Shift+R`（macOS）
- 调试：`Ctrl+Shift+F9`（Windows/Linux）或`Control+Shift+D`（macOS）

**配置环境变量**：
1. Run → Edit Configurations
2. 选择测试配置
3. Environment variables添加：`DASHSCOPE_API_KEY=sk-xxx`

#### 7.2.2 Eclipse

1. 右键测试类或方法
2. 选择"Run As" → "JUnit Test"

#### 7.2.3 VS Code

1. 安装Java Test Runner插件
2. 点击方法上方的"Run Test"按钮

### 7.3 TestContainers故障排查

#### 7.3.1 Docker未运行

**错误信息**：
```
Could not find a valid Docker environment
```

**解决方案**：
```bash
# 启动Docker Desktop
# macOS: 打开Docker Desktop应用
# Linux: sudo systemctl start docker
# Windows: 启动Docker Desktop
```

#### 7.3.2 端口冲突

**错误信息**：
```
Bind for 0.0.0.0:5432 failed: port is already allocated
```

**解决方案**：
```bash
# 查找占用端口的进程
lsof -i :5432

# 停止占用端口的容器
docker ps
docker stop <container_id>
```

#### 7.3.3 容器启动超时

**错误信息**：
```
Container startup failed
```

**解决方案**：
```bash
# 清理旧容器
docker system prune -a

# 拉取最新镜像
docker pull postgres:14-alpine

# 检查Docker资源限制
# Docker Desktop → Preferences → Resources
# 增加内存限制到4GB+
```

---

## 8. 测试覆盖率报告

### 8.1 生成覆盖率报告

#### 8.1.1 使用JaCoCo（推荐）

**配置pom.xml**：

```xml
<build>
    <plugins>
        <plugin>
            <groupId>org.jacoco</groupId>
            <artifactId>jacoco-maven-plugin</artifactId>
            <version>0.8.11</version>
            <executions>
                <execution>
                    <goals>
                        <goal>prepare-agent</goal>
                    </goals>
                </execution>
                <execution>
                    <id>report</id>
                    <phase>test</phase>
                    <goals>
                        <goal>report</goal>
                    </goals>
                </execution>
            </executions>
        </plugin>
    </plugins>
</build>
```

**生成报告**：

```bash
mvn clean test jacoco:report
```

**查看报告**：
- HTML报告：`target/site/jacoco/index.html`
- XML报告：`target/site/jacoco/jacoco.xml`

#### 8.1.2 覆盖率目标

| 层级 | 目标覆盖率 | 阻塞标准 |
|-----|-----------|---------|
| E2E测试 | ≥75% | <60% |
| Service层 | ≥90% | <85% |
| Controller层 | ≥80% | <70% |
| 整体覆盖率 | ≥85% | <75% |

### 8.2 查看覆盖率详情

#### 8.2.1 IDE集成

**IntelliJ IDEA**：
1. Run → Run 'Tests' with Coverage
2. 查看Coverage窗口
3. 双击类查看行级覆盖率

**Eclipse**：
1. 安装EclEmma插件
2. Run → Coverage As → JUnit Test

#### 8.2.2 命令行查看

```bash
# 查看覆盖率摘要
mvn jacoco:report
cat target/site/jacoco/index.html

# 查看未覆盖的代码
mvn jacoco:check
```

### 8.3 提高覆盖率策略

#### 8.3.1 识别未覆盖代码

```bash
# 生成覆盖率报告
mvn clean test jacoco:report

# 打开HTML报告
open target/site/jacoco/index.html
```

**报告说明**：
- 🟢 绿色：代码已覆盖
- 🔴 红色：代码未覆盖
- 🟡 黄色：部分分支覆盖

#### 8.3.2 补充测试用例

```java
// 识别未覆盖的异常处理分支
@Test
public void testErrorHandling() throws Exception {
    mockMvc.perform(get("/api/v1/timemachine/version/{versionId}", 99999L))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value(404))
        .andExpect(jsonPath("$.success").value(false));
}
```

---

## 9. CI/CD集成

### 9.1 GitHub Actions配置

**文件路径**：`.github/workflows/backend-test.yml`

```yaml
name: Backend E2E Tests

on:
  push:
    branches: [ master, develop ]
  pull_request:
    branches: [ master, develop ]

jobs:
  test:
    runs-on: ubuntu-latest

    steps:
      - name: Checkout代码
        uses: actions/checkout@v4

      - name: 设置Java 17
        uses: actions/setup-java@v4
        with:
          java-version: '17'
          distribution: 'temurin'
          cache: maven

      - name: 启动Docker（TestContainers需要）
        run: |
          sudo systemctl start docker
          docker --version

      - name: 运行E2E测试（跳过需要API KEY的测试）
        run: |
          cd backend
          mvn clean test -Dtest=**/*E2ETest

      - name: 运行需要API KEY的测试（可选）
        if: github.ref == 'refs/heads/master'
        env:
          DASHSCOPE_API_KEY: ${{ secrets.DASHSCOPE_API_KEY }}
        run: |
          cd backend
          mvn test -Dtest=SuperDesignE2ETest

      - name: 生成覆盖率报告
        run: |
          cd backend
          mvn jacoco:report

      - name: 上传覆盖率报告到Codecov
        uses: codecov/codecov-action@v4
        with:
          files: ./backend/target/site/jacoco/jacoco.xml
          flags: backend-e2e
          name: backend-e2e-coverage

      - name: 上传测试报告
        if: always()
        uses: actions/upload-artifact@v4
        with:
          name: test-reports
          path: backend/target/surefire-reports/
```

### 9.2 GitLab CI配置

**文件路径**：`.gitlab-ci.yml`

```yaml
stages:
  - test

backend-e2e-test:
  stage: test
  image: maven:3.9-eclipse-temurin-17
  services:
    - docker:dind
  variables:
    DOCKER_HOST: tcp://docker:2375
    DOCKER_TLS_CERTDIR: ""
  before_script:
    - apt-get update && apt-get install -y docker.io
  script:
    - cd backend
    - mvn clean test -Dtest=**/*E2ETest
    - mvn jacoco:report
  coverage: '/Total.*?([0-9]{1,3})%/'
  artifacts:
    reports:
      junit: backend/target/surefire-reports/TEST-*.xml
      coverage_report:
        coverage_format: cobertura
        path: backend/target/site/jacoco/jacoco.xml
    paths:
      - backend/target/site/jacoco/
    expire_in: 1 week
```

### 9.3 Jenkins Pipeline配置

```groovy
pipeline {
    agent any

    tools {
        maven 'Maven 3.9'
        jdk 'JDK 17'
    }

    stages {
        stage('Checkout') {
            steps {
                checkout scm
            }
        }

        stage('E2E Tests') {
            steps {
                dir('backend') {
                    sh 'mvn clean test -Dtest=**/*E2ETest'
                }
            }
        }

        stage('Coverage Report') {
            steps {
                dir('backend') {
                    sh 'mvn jacoco:report'
                    publishHTML([
                        reportDir: 'target/site/jacoco',
                        reportFiles: 'index.html',
                        reportName: 'JaCoCo Coverage Report'
                    ])
                }
            }
        }
    }

    post {
        always {
            junit 'backend/target/surefire-reports/*.xml'
        }
    }
}
```

### 9.4 质量门禁配置

#### 9.4.1 Maven配置

```xml
<plugin>
    <groupId>org.jacoco</groupId>
    <artifactId>jacoco-maven-plugin</artifactId>
    <executions>
        <execution>
            <id>check</id>
            <goals>
                <goal>check</goal>
            </goals>
            <configuration>
                <rules>
                    <rule>
                        <element>BUNDLE</element>
                        <limits>
                            <limit>
                                <counter>LINE</counter>
                                <value>COVEREDRATIO</value>
                                <minimum>0.75</minimum>
                            </limit>
                        </limits>
                    </rule>
                </rules>
            </configuration>
        </execution>
    </executions>
</plugin>
```

#### 9.4.2 SonarQube集成

```bash
# 运行SonarQube分析
mvn clean verify sonar:sonar \
  -Dsonar.projectKey=ingenio-backend \
  -Dsonar.host.url=http://localhost:9000 \
  -Dsonar.login=$SONAR_TOKEN
```

---

## 10. 常见问题排查

### 10.1 TestContainers相关问题

#### Q1: Docker未启动

**问题描述**：
```
org.testcontainers.dockerclient.EnvironmentAndSystemPropertyClientProviderStrategy -
Could not find a valid Docker environment
```

**解决方案**：
```bash
# macOS
open -a Docker

# Linux
sudo systemctl start docker

# Windows
# 启动Docker Desktop应用
```

#### Q2: 容器启动超时

**问题描述**：
```
org.testcontainers.containers.ContainerLaunchException:
Container startup failed
```

**解决方案**：
```bash
# 1. 增加Docker资源限制
# Docker Desktop → Settings → Resources
# 内存：4GB → 8GB
# CPU：2核 → 4核

# 2. 清理Docker缓存
docker system prune -a

# 3. 拉取镜像
docker pull postgres:14-alpine

# 4. 禁用容器重用（调试用）
# 修改BaseE2ETest.java
.withReuse(false)
```

#### Q3: 端口冲突

**问题描述**：
```
Bind for 0.0.0.0:5432 failed: port is already allocated
```

**解决方案**：
```bash
# 查找占用端口的进程
lsof -i :5432

# 停止PostgreSQL服务
brew services stop postgresql  # macOS
sudo systemctl stop postgresql  # Linux

# 或停止占用端口的Docker容器
docker ps
docker stop <container_id>
```

### 10.2 数据库相关问题

#### Q4: 数据库连接失败

**问题描述**：
```
java.sql.SQLException: Connection refused
```

**解决方案**：
```bash
# 1. 检查容器是否启动
docker ps | grep postgres

# 2. 查看容器日志
docker logs <container_id>

# 3. 检查数据库配置
# 确认application-test.yml中的配置正确
```

#### Q5: 数据库迁移失败

**问题描述**：
```
Flyway migration failed
```

**解决方案**：
```bash
# 1. 检查SQL脚本语法
# 查看src/main/resources/db/migration/

# 2. 清空数据库重新迁移
mvn flyway:clean flyway:migrate

# 3. 查看Flyway历史
mvn flyway:info
```

### 10.3 测试运行相关问题

#### Q6: 测试超时

**问题描述**：
```
org.junit.jupiter.api.extension.TestInstantiationException:
TestInstanceFactory timed out
```

**解决方案**：
```java
// 增加测试超时时间
@Test
@Timeout(value = 2, unit = TimeUnit.MINUTES)
public void testSlowOperation() {
    // 测试代码
}
```

#### Q7: 测试数据污染

**问题描述**：
```
测试A通过，但测试B失败（依赖测试A的数据）
```

**解决方案**：
```java
@Override
@BeforeEach
public void setUp() {
    super.setUp();

    // 每个测试前清理所有数据
    versionMapper.delete(null);
    taskMapper.delete(null);
    userMapper.delete(null);

    // 重新初始化测试数据
    createTestData();
}
```

#### Q8: JSON断言失败

**问题描述**：
```
java.lang.AssertionError:
JSON path "$.data.versionNumber" doesn't match.
Expected: 1
Actual: null
```

**解决方案**：
```java
// 1. 打印实际响应
MvcResult result = mockMvc.perform(...)
    .andReturn();
System.out.println("响应: " + result.getResponse().getContentAsString());

// 2. 使用正确的JSON路径
// 检查响应结构是否与断言匹配

// 3. 检查数据库是否有数据
List<GenerationVersionEntity> versions = versionMapper.selectList(null);
System.out.println("数据库版本数: " + versions.size());
```

### 10.4 CI/CD集成问题

#### Q9: GitHub Actions中Docker不可用

**问题描述**：
```
Cannot connect to the Docker daemon
```

**解决方案**：
```yaml
# 确保GitHub Actions使用ubuntu-latest
runs-on: ubuntu-latest

# 不需要额外启动Docker，GitHub Actions已内置
```

#### Q10: 环境变量未生效

**问题描述**：
```
@EnabledIfEnvironmentVariable标记的测试未运行
```

**解决方案**：
```yaml
# GitHub Actions中添加环境变量
- name: 运行需要API KEY的测试
  env:
    DASHSCOPE_API_KEY: ${{ secrets.DASHSCOPE_API_KEY }}
  run: mvn test -Dtest=SuperDesignE2ETest

# 确保在GitHub仓库设置中配置了Secret
# Settings → Secrets → Actions → New repository secret
```

### 10.5 性能相关问题

#### Q11: 测试运行缓慢

**问题分析**：
- 容器启动时间：每次10-30秒
- API调用时间：真实API 5-30秒
- 数据库操作：批量插入耗时

**优化方案**：
```java
// 1. 启用容器重用
.withReuse(true)

// 2. 使用@EnabledIfEnvironmentVariable跳过慢速测试
@EnabledIfEnvironmentVariable(named = "RUN_SLOW_TESTS", matches = "true")

// 3. 使用@Tag分类测试
@Tag("slow")
@Test
public void testSlowAPI() { }

// 运行时跳过慢速测试
mvn test -Dgroups="!slow"

// 4. 批量插入数据
taskMapper.insertBatch(testTasks);
```

#### Q12: 内存溢出

**问题描述**：
```
java.lang.OutOfMemoryError: Java heap space
```

**解决方案**：
```bash
# 1. 增加Maven内存限制
export MAVEN_OPTS="-Xmx2048m -XX:MaxPermSize=512m"
mvn test

# 2. 配置pom.xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-surefire-plugin</artifactId>
    <configuration>
        <argLine>-Xmx2048m</argLine>
    </configuration>
</plugin>

# 3. 清理测试数据
@AfterEach
public void tearDown() {
    // 释放大对象引用
    testData = null;
}
```

---

## 11. 总结与最佳实践清单

### 11.1 测试编写清单

- [ ] 继承`BaseE2ETest`基类
- [ ] 添加`@DisplayName`注解描述测试场景
- [ ] 在`setUp()`中清理旧数据并初始化测试数据
- [ ] 使用MockMvc发送HTTP请求
- [ ] 使用JsonPath断言响应结构和数据
- [ ] 验证数据库状态变更
- [ ] 测试异常场景和边界条件
- [ ] 添加性能断言（如适用）
- [ ] 测试独立运行，不依赖其他测试
- [ ] 添加详细的中文注释

### 11.2 代码质量清单

- [ ] 测试覆盖率 ≥ 75%
- [ ] 所有测试通过
- [ ] 无测试数据污染
- [ ] 无硬编码测试数据
- [ ] 使用Builder模式构建测试对象
- [ ] 提取公共测试方法到基类
- [ ] 异常场景有明确的错误信息
- [ ] 性能测试有合理的超时限制

### 11.3 CI/CD集成清单

- [ ] GitHub Actions配置文件存在
- [ ] TestContainers Docker环境可用
- [ ] 环境变量正确配置（如API KEY）
- [ ] 覆盖率报告自动生成
- [ ] 质量门禁配置（≥75%覆盖率）
- [ ] 测试报告自动上传
- [ ] PR自动触发测试
- [ ] 失败测试阻塞合并

### 11.4 维护清单

- [ ] 定期更新TestContainers版本
- [ ] 定期更新PostgreSQL镜像版本
- [ ] 清理无用的测试数据
- [ ] 优化慢速测试
- [ ] 更新测试文档
- [ ] 审查测试覆盖率报告
- [ ] 识别并补充缺失的测试场景

---

## 12. 附录

### 12.1 参考资源

- [TestContainers官方文档](https://www.testcontainers.org/)
- [Spring Boot Testing文档](https://docs.spring.io/spring-boot/docs/current/reference/html/features.html#features.testing)
- [JUnit 5用户指南](https://junit.org/junit5/docs/current/user-guide/)
- [MockMvc文档](https://docs.spring.io/spring-framework/docs/current/reference/html/testing.html#spring-mvc-test-framework)
- [JaCoCo覆盖率工具](https://www.jacoco.org/jacoco/trunk/doc/)

### 12.2 常用命令速查表

| 功能 | 命令 |
|-----|------|
| 运行所有E2E测试 | `mvn test -Dtest=**/*E2ETest` |
| 运行单个测试类 | `mvn test -Dtest=TimeMachineE2ETest` |
| 运行单个测试方法 | `mvn test -Dtest=TimeMachineE2ETest#testGetTimeline` |
| 生成覆盖率报告 | `mvn clean test jacoco:report` |
| 检查覆盖率门禁 | `mvn jacoco:check` |
| 跳过测试 | `mvn install -DskipTests` |
| 仅编译不测试 | `mvn compile` |
| 清理并测试 | `mvn clean test` |
| 调试模式运行 | `mvnDebug test -Dtest=TimeMachineE2ETest` |
| 查看Docker容器 | `docker ps \| grep postgres` |
| 查看容器日志 | `docker logs <container_id>` |
| 清理Docker | `docker system prune -a` |

### 12.3 术语表

| 术语 | 英文 | 说明 |
|-----|------|------|
| 端到端测试 | E2E Test | 从用户界面到数据库的完整流程测试 |
| 容器化 | Containerization | 使用Docker容器隔离测试环境 |
| 零Mock策略 | Zero-Mock Strategy | 不使用Mock对象，所有依赖使用真实实例 |
| 测试覆盖率 | Test Coverage | 代码被测试覆盖的百分比 |
| 测试隔离 | Test Isolation | 每个测试独立运行，不影响其他测试 |
| 断言 | Assertion | 验证测试结果是否符合预期 |
| 测试夹具 | Test Fixture | 测试前准备的数据和环境 |
| 质量门禁 | Quality Gate | 代码质量的最低标准要求 |

---

## 13. 文档维护

**最后更新**：2025-11-09
**版本**：v1.0.0
**维护者**：Ingenio Backend Team

**变更日志**：
- 2025-11-09：初始版本，包含完整的E2E测试指南

**反馈**：
- 如发现文档错误或需要补充内容，请提交Issue或PR
- 联系方式：ingenio-backend@example.com
