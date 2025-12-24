# ADR 003: 零Mock策略实施

**状态**: 已接受 (Accepted)
**日期**: 2025-11-11
**决策者**: Ingenio团队

---

## 上下文

Ingenio项目的测试策略需要决策是否使用Mock对象（如Mockito）来模拟外部依赖（数据库、Redis、AI API等）。我们面临以下考虑：

1. **真实性需求**：
   - 测试环境应尽可能接近生产环境
   - 需要发现真实的集成问题
   - 避免"测试通过但生产失败"的情况

2. **成本约束**：
   - 测试不应产生高额API调用费用
   - 测试环境维护成本要可控
   - CI/CD流程要高效（<10分钟）

3. **质量要求**：
   - 测试覆盖率≥85%
   - E2E测试通过率100%
   - 发现真实的性能问题

---

## 决策

### 主要决策：采用**零Mock策略**（Zero Mock Policy）

具体实现：
1. **数据库**：使用TestContainers启动真实PostgreSQL 14
2. **Redis**：使用TestContainers启动真实Redis 7
3. **AI API**：集成真实的Qwen API（测试环境使用限流）
4. **MinIO**：使用TestContainers启动真实MinIO

**禁止使用**：
- ❌ Mockito
- ❌ @MockBean
- ❌ 内存数据库H2（作为PostgreSQL替代）
- ❌ 假的Redis实现（如Embedded Redis）

---

## 理由

### 1. 三种测试策略对比

| 策略 | Mock使用程度 | 真实性 | 速度 | 成本 | 维护性 |
|-----|------------|-------|------|------|--------|
| **传统Mock策略** | 高（80%+） | ⭐⭐ | ⭐⭐⭐⭐⭐ | ⭐⭐⭐⭐⭐ | ⭐⭐ |
| **混合策略** | 中（50%） | ⭐⭐⭐ | ⭐⭐⭐⭐ | ⭐⭐⭐⭐ | ⭐⭐⭐ |
| **零Mock策略（选择）** | 低（<10%） | ⭐⭐⭐⭐⭐ | ⭐⭐⭐ | ⭐⭐⭐ | ⭐⭐⭐⭐ |

### 2. 传统Mock策略的问题

#### 问题1：测试和生产环境不一致

```java
// ❌ 传统Mock策略示例
@MockBean
private UserRepository userRepository;

@Test
public void testFindUser() {
    // Mock返回数据
    when(userRepository.findById("user-123"))
        .thenReturn(Optional.of(new User("user-123", "test@example.com")));

    // 测试通过
    User user = userService.findById("user-123");
    assertEquals("test@example.com", user.getEmail());
}
```

**问题**：
- ✅ 测试通过
- ❌ 但生产环境可能失败（SQL语法错误、索引缺失等）
- ❌ 无法发现数据库性能问题
- ❌ 无法测试事务和锁机制

#### 问题2：Mock配置复杂且易出错

```java
// ❌ 复杂的Mock配置
@MockBean
private QwenClient qwenClient;

@Test
public void testGenerateCode() {
    // 需要Mock大量方法
    when(qwenClient.generate(any()))
        .thenReturn(new AIResponse("...", 1000, 0.95));
    when(qwenClient.getModel())
        .thenReturn("qwen-max");
    when(qwenClient.getApiKey())
        .thenReturn("test-key");

    // ... 10行Mock配置
}
```

**问题**：
- ⚠️ Mock配置比业务逻辑还多
- ⚠️ 容易配置错误（返回值类型、参数匹配）
- ⚠️ 维护成本高（API变更需要更新Mock）

#### 问题3：无法测试真实的集成问题

```java
// ❌ Mock无法发现的问题
@Test
public void testSaveUser() {
    when(userRepository.save(any()))
        .thenReturn(new User("user-123", "test@example.com"));

    // 测试通过，但实际问题无法发现：
    // 1. email字段超长（数据库限制100字符）
    // 2. 唯一约束冲突（email重复）
    // 3. 外键约束失败（关联的部门不存在）
    // 4. 事务提交失败（并发修改）
}
```

### 3. 零Mock策略的优势

#### 优势1：真实环境测试

```java
// ✅ 零Mock策略示例
@SpringBootTest(webEnvironment = RANDOM_PORT)
@Testcontainers
public class UserServiceE2ETest {
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:14-alpine")
        .withDatabaseName("ingenio_test")
        .withUsername("test")
        .withPassword("test");

    @Autowired
    private UserService userService;

    @Test
    public void testFindUser() {
        // 使用真实数据库
        User user = userService.create(new CreateUserRequest("test@example.com"));

        // 测试真实的查询
        User found = userService.findById(user.getId());

        assertEquals("test@example.com", found.getEmail());
    }

    @Test
    public void testEmailUniqueness() {
        // 可以测试数据库约束
        userService.create(new CreateUserRequest("test@example.com"));

        assertThrows(DuplicateException.class, () ->
            userService.create(new CreateUserRequest("test@example.com"))
        );
    }
}
```

**优势**：
- ✅ 发现真实的SQL错误
- ✅ 发现数据库约束问题
- ✅ 测试真实的事务行为
- ✅ 测试真实的性能特征

#### 优势2：简化测试代码

```java
// ✅ 零Mock策略：无需Mock配置
@SpringBootTest
@Testcontainers
public class AICodeGeneratorE2ETest {
    @Autowired
    private AICodeGeneratorService aiCodeGenerator;

    @Test
    public void testGenerateCode() {
        // 直接调用真实服务
        GenerateResult result = aiCodeGenerator.generate(
            createRequest("com.example.myapp", List.of("VIDEO_ANALYSIS"))
        );

        // 验证结果
        assertNotNull(result.getTaskId());
        assertTrue(result.getFileCount() >= 3);
    }
}
```

**优势**：
- ✅ 测试代码简洁（无Mock配置）
- ✅ 易于维护（API变更无需更新Mock）
- ✅ 可读性高（直接调用真实服务）

#### 优势3：发现真实问题

**案例1：数据库索引缺失**
```java
@Test
@DisplayName("性能测试：查询1000个用户")
public void testQueryPerformance() {
    // 插入1000个用户
    for (int i = 0; i < 1000; i++) {
        userService.create(new CreateUserRequest("user" + i + "@example.com"));
    }

    // 测试查询性能
    long startTime = System.currentTimeMillis();
    List<User> users = userService.findByEmailPrefix("user");
    long duration = System.currentTimeMillis() - startTime;

    // 如果>100ms，说明索引缺失
    assertTrue(duration < 100, "查询耗时: " + duration + "ms（可能缺少索引）");
}
```

**发现的问题**：
- email字段缺少索引，查询耗时300ms
- 添加索引后，查询耗时降至15ms

**案例2：AI API限流**
```java
@Test
@DisplayName("压力测试：100个并发请求")
public void testAIAPIRateLimit() {
    ExecutorService executor = Executors.newFixedThreadPool(100);
    List<CompletableFuture<AIResponse>> futures = new ArrayList<>();

    // 发送100个并发请求
    for (int i = 0; i < 100; i++) {
        futures.add(CompletableFuture.supplyAsync(() ->
            qwenClient.generate("测试请求"), executor
        ));
    }

    // 统计成功率
    long successCount = futures.stream()
        .map(CompletableFuture::join)
        .filter(r -> r.getStatus() == 200)
        .count();

    // 发现限流问题：成功率仅60%
    assertEquals(100, successCount, "成功率: " + successCount + "%");
}
```

**发现的问题**：
- Qwen API限流：100次/分钟
- 需要实现重试和限流机制

### 4. 性能对比

| 测试类型 | Mock策略 | 零Mock策略 | 差异 |
|---------|---------|-----------|------|
| **单元测试** | 50ms | 150ms | +3倍 |
| **集成测试** | 200ms | 500ms | +2.5倍 |
| **E2E测试** | 500ms | 2000ms | +4倍 |
| **全部测试** | 3分钟 | 8分钟 | +2.7倍 |

**分析**：
- ⚠️ 零Mock策略速度略慢（~2-4倍）
- ✅ 但仍在可接受范围（<10分钟）
- ✅ 换来的是更高的测试可靠性

### 5. 成本对比

#### 数据库成本

| 方案 | 实现 | 成本（月） | 维护性 |
|-----|------|-----------|--------|
| **H2内存数据库** | 内存模拟 | $0 | ⭐⭐ |
| **TestContainers PostgreSQL** | Docker容器 | $0 | ⭐⭐⭐⭐⭐ |
| **云数据库（测试环境）** | RDS | $50 | ⭐⭐⭐ |

**结论**：TestContainers成本为0，维护性最高

#### AI API成本

| 方案 | 实现 | 成本（月） | 真实性 |
|-----|------|-----------|--------|
| **Mock** | Mockito | $0 | ⭐⭐ |
| **真实API（无限流）** | Qwen | $150 | ⭐⭐⭐⭐⭐ |
| **真实API（限流）** | Qwen + Rate Limit | $20 | ⭐⭐⭐⭐ |

**结论**：限流后成本可控（$20/月），真实性高

---

## 决策细节

### 零Mock策略实施细则

#### 规则1：数据库使用真实PostgreSQL

```java
@Testcontainers
public class BaseE2ETest {
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:14-alpine")
        .withDatabaseName("ingenio_test")
        .withUsername("test")
        .withPassword("test")
        .withInitScript("schema.sql");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }
}
```

**优势**：
- ✅ 与生产环境完全一致（PostgreSQL 14）
- ✅ 自动启动和关闭（无需手动管理）
- ✅ 隔离性好（每个测试独立数据库）

#### 规则2：Redis使用真实Redis 7

```java
@Testcontainers
public class BaseE2ETest {
    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine")
        .withExposedPorts(6379);

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.redis.host", redis::getHost);
        registry.add("spring.redis.port", () -> redis.getMappedPort(6379));
    }
}
```

#### 规则3：AI API使用真实API（限流）

```java
@Configuration
@Profile("test")
public class TestAIConfig {
    @Bean
    public QwenClient qwenClient() {
        return new RateLimitedQwenClient(
            System.getenv("QWEN_API_KEY"),
            10 // 最多10次/分钟
        );
    }
}

public class RateLimitedQwenClient extends QwenClient {
    private final RateLimiter rateLimiter;

    public RateLimitedQwenClient(String apiKey, int requestsPerMinute) {
        super(apiKey);
        this.rateLimiter = RateLimiter.create(requestsPerMinute / 60.0);
    }

    @Override
    public AIResponse generate(AIRequest request) {
        rateLimiter.acquire(); // 限流
        return super.generate(request);
    }
}
```

**成本控制**：
- 限流：10次/分钟 = 600次/小时 = 14,400次/天
- 月成本：14,400 × 30 × ¥0.002 / 7.15 ≈ $20
- 可接受范围

#### 规则4：MinIO使用真实MinIO

```java
@Testcontainers
public class BaseE2ETest {
    @Container
    static MinIOContainer minio = new MinIOContainer("minio/minio:latest")
        .withUserName("minioadmin")
        .withPassword("minioadmin");

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("minio.url", minio::getS3URL);
        registry.add("minio.access-key", minio::getUserName);
        registry.add("minio.secret-key", minio::getPassword);
    }
}
```

### 例外情况（允许Mock的场景）

#### 例外1：第三方不可控服务

```java
// ✅ 允许Mock：第三方支付API（无测试环境）
@MockBean
private PaymentGatewayClient paymentClient;

@Test
public void testPayment() {
    when(paymentClient.charge(any()))
        .thenReturn(new PaymentResult("success"));
    // ...
}
```

**理由**：
- 第三方服务无测试环境
- 调用真实API会产生费用
- 无法自动化测试

#### 例外2：外部服务临时故障

```java
// ✅ 允许Mock：测试降级逻辑
@Test
public void testAIFallback() {
    // 模拟AI API故障
    QwenClient mockClient = mock(QwenClient.class);
    when(mockClient.generate(any()))
        .thenThrow(new ApiException("Service Unavailable"));

    // 测试降级逻辑
    String result = aiService.generateWithFallback(request, mockClient);

    assertNotNull(result); // 应使用缓存或通用模板
}
```

**理由**：
- 测试异常处理逻辑
- 无法手动触发真实故障

---

## 实施计划

### Phase 1: 基础设施搭建（1周）

- [ ] 集成TestContainers（PostgreSQL、Redis、MinIO）
- [ ] 配置动态属性注入
- [ ] 创建BaseE2ETest基类
- [ ] 编写数据库初始化脚本

**交付物**：
- `BaseE2ETest.java` 基类
- `docker-compose.test.yml` 本地测试环境
- `schema.sql` 数据库初始化脚本

### Phase 2: AI API集成（1周）

- [ ] 集成真实Qwen API
- [ ] 实现RateLimitedQwenClient
- [ ] 配置测试环境API密钥
- [ ] 编写AI调用监控

**交付物**：
- `RateLimitedQwenClient.java` 限流客户端
- `TestAIConfig.java` 测试配置
- `AICallMonitor.java` 调用监控

### Phase 3: E2E测试迁移（2周）

- [ ] 迁移现有测试（删除@MockBean）
- [ ] 添加真实场景测试
- [ ] 添加性能基准测试
- [ ] 测试覆盖率≥85%

**交付物**：
- 50+ E2E测试用例
- 性能基准报告
- CI/CD集成

---

## 风险和缓解措施

### 风险1：测试速度变慢

**风险描述**：零Mock测试耗时~2-4倍

**缓解措施**：
1. **并行测试**：使用JUnit 5并行执行
2. **测试分层**：快速测试优先运行
3. **增量测试**：只运行变更相关的测试
4. **缓存优化**：缓存TestContainers镜像

**效果**：
- 并行后：8分钟 → 4分钟
- 增量测试：4分钟 → 1分钟（日常开发）

### 风险2：AI API成本增加

**风险描述**：真实API调用产生费用

**缓解措施**：
1. **严格限流**：10次/分钟上限
2. **缓存策略**：相似请求返回缓存
3. **成本监控**：超过$30/月告警
4. **按需测试**：部分测试可跳过AI调用

**效果**：
- 月成本：$20
- 可接受范围

### 风险3：环境不一致

**风险描述**：本地和CI环境可能不一致

**缓解措施**：
1. **Docker统一**：本地和CI都使用Docker
2. **版本锁定**：锁定PostgreSQL、Redis版本
3. **初始化脚本**：使用相同的schema.sql
4. **环境变量**：使用.env.test统一配置

---

## 后续评审计划

1. **1个月后评审**（2025-12-11）：
   - 评估测试速度（目标<5分钟）
   - 分析AI API成本（目标<$30/月）
   - 统计发现的真实问题数量

2. **3个月后评审**（2026-02-11）：
   - 评估测试可靠性（目标通过率>99%）
   - 优化TestContainers启动速度
   - 扩展到更多真实依赖

---

## 相关文档

- [E2E测试指南](../../testing/E2E_TESTING_GUIDE.md)
- [TestContainers最佳实践](../../testing/TESTCONTAINERS_BEST_PRACTICES.md)
- [AI API限流策略](../AI_API_RATE_LIMITING.md)

---

**文档结束**
