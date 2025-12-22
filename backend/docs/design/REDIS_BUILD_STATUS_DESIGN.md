# Redis构建状态存储设计

> **版本**: v1.0.0
> **创建时间**: 2025-11-09
> **目的**: 定义多端发布系统的Redis持久化存储规范

---

## 1. 设计目标

### 1.1 核心目标
- ✅ **持久化存储**：服务重启后状态不丢失
- ✅ **分布式支持**：多实例共享构建状态
- ✅ **自动过期**：30天自动清理旧数据
- ✅ **高性能**：查询延迟<10ms

### 1.2 替换方案
将当前的内存存储（ConcurrentHashMap）替换为Redis存储：

| 特性 | 内存存储 | Redis存储 |
|-----|---------|----------|
| 持久化 | ❌ 否 | ✅ 是 |
| 分布式 | ❌ 否 | ✅ 是 |
| 自动过期 | ❌ 否 | ✅ 是 |
| 性能 | 极快 | 快 |

---

## 2. Key命名规范

### 2.1 Key结构

使用冒号分隔的层级结构：

```
ingenio:publish:build:{buildId}
```

**组成部分**：
- `ingenio` - 项目名前缀，避免Key冲突
- `publish` - 功能模块标识
- `build` - 资源类型
- `{buildId}` - 构建任务ID（UUID格式）

### 2.2 Key示例

```redis
# 完整构建状态
ingenio:publish:build:550e8400-e29b-41d4-a716-446655440000

# Key匹配模式（用于批量查询）
ingenio:publish:build:*
```

---

## 3. 数据结构设计

### 3.1 存储类型选择

**选择方案：Redis String（存储JSON）**

**原因**：
1. PublishResponse是复杂嵌套对象（包含Map和List）
2. JSON序列化简单，易于调试
3. 整体读写，无需部分更新
4. Jackson已配置好，无额外依赖

**备选方案：Redis Hash**
- 优点：可部分更新字段
- 缺点：嵌套对象需要序列化，复杂度高

### 3.2 存储格式

**Key**: `ingenio:publish:build:{buildId}`
**Type**: String
**Value**: JSON格式的PublishResponse

```json
{
  "buildId": "550e8400-e29b-41d4-a716-446655440000",
  "projectId": "test-project-001",
  "platforms": ["android", "ios"],
  "status": "IN_PROGRESS",
  "platformResults": {
    "android": {
      "platform": "android",
      "status": "SUCCESS",
      "progress": 100,
      "logUrl": null,
      "downloadUrl": "550e8400.../android/app.apk",
      "errorMessage": null,
      "startedAt": "2025-11-09T10:30:00",
      "completedAt": "2025-11-09T10:35:00"
    },
    "ios": {
      "platform": "ios",
      "status": "IN_PROGRESS",
      "progress": 50,
      "logUrl": null,
      "downloadUrl": null,
      "errorMessage": null,
      "startedAt": "2025-11-09T10:30:00",
      "completedAt": null
    }
  },
  "estimatedTime": 15,
  "createdAt": "2025-11-09T10:30:00",
  "updatedAt": "2025-11-09T10:33:00"
}
```

---

## 4. TTL过期策略

### 4.1 过期时间配置

**过期时间**: 30天（与MinIO生命周期一致）
- 秒数：2,592,000秒 = 30 * 24 * 60 * 60
- 配置：通过`application.yml`配置

```yaml
publish:
  build-status:
    ttl-days: 30  # 30天过期
```

### 4.2 过期行为

```java
// 设置TTL（秒）
redisTemplate.expire(key, 30 * 24 * 60 * 60, TimeUnit.SECONDS);
```

**过期后的影响**：
- Redis自动删除Key
- 查询返回null
- 业务层返回"构建任务不存在"错误

---

## 5. 操作接口设计

### 5.1 核心操作

| 操作 | Redis命令 | 说明 |
|-----|----------|------|
| 保存状态 | SET | 保存完整PublishResponse |
| 查询状态 | GET | 获取完整PublishResponse |
| 删除状态 | DEL | 手动删除构建状态 |
| 批量查询 | KEYS | 查询所有构建（生产慎用） |
| 检查存在 | EXISTS | 检查buildId是否存在 |

### 5.2 Java API设计

```java
public class BuildStatusManager {

    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;

    /**
     * 保存构建状态
     */
    public void saveBuildStatus(String buildId, PublishResponse response) {
        String key = buildKey(buildId);
        String json = objectMapper.writeValueAsString(response);
        redisTemplate.opsForValue().set(key, json, 30, TimeUnit.DAYS);
    }

    /**
     * 获取构建状态
     */
    public PublishResponse getBuildStatus(String buildId) {
        String key = buildKey(buildId);
        String json = redisTemplate.opsForValue().get(key);
        if (json == null) return null;
        return objectMapper.readValue(json, PublishResponse.class);
    }

    /**
     * 删除构建状态
     */
    public void removeBuildStatus(String buildId) {
        String key = buildKey(buildId);
        redisTemplate.delete(key);
    }

    /**
     * 生成Redis Key
     */
    private String buildKey(String buildId) {
        return "ingenio:publish:build:" + buildId;
    }
}
```

---

## 6. 序列化配置

### 6.1 LocalDateTime序列化

**问题**：LocalDateTime序列化为数组格式不友好
```json
"createdAt": [2025, 11, 9, 10, 30, 0, 0]
```

**解决方案**：配置Jackson使用ISO-8601格式
```java
@Bean
public ObjectMapper objectMapper() {
    ObjectMapper mapper = new ObjectMapper();
    mapper.registerModule(new JavaTimeModule());
    mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    return mapper;
}
```

**结果**：
```json
"createdAt": "2025-11-09T10:30:00"
```

### 6.2 Redis序列化配置

```java
@Configuration
public class RedisConfig {

    @Bean
    public RedisTemplate<String, String> redisTemplate(
            RedisConnectionFactory connectionFactory) {

        RedisTemplate<String, String> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);

        // Key序列化：String
        template.setKeySerializer(new StringRedisSerializer());

        // Value序列化：String（JSON）
        template.setValueSerializer(new StringRedisSerializer());

        return template;
    }
}
```

---

## 7. 性能优化

### 7.1 批量操作

**避免N+1查询问题**：
```java
// ❌ 错误：N次查询
for (String buildId : buildIds) {
    PublishResponse response = getBuildStatus(buildId);
}

// ✅ 正确：使用Pipeline
List<Object> results = redisTemplate.executePipelined(
    (RedisCallback<Object>) connection -> {
        for (String buildId : buildIds) {
            connection.get(buildKey(buildId).getBytes());
        }
        return null;
    }
);
```

### 7.2 缓存预热

**启动时预加载**（可选）：
```java
@PostConstruct
public void warmupCache() {
    // 预加载最近1小时的构建状态到本地缓存
}
```

---

## 8. 监控和告警

### 8.1 关键指标

| 指标 | 阈值 | 告警级别 |
|-----|------|---------|
| Redis连接失败率 | >5% | Critical |
| 查询延迟P95 | >50ms | Warning |
| 查询延迟P95 | >100ms | Critical |
| 内存使用率 | >80% | Warning |
| Key数量 | >100万 | Warning |

### 8.2 日志记录

```java
log.info("Redis保存构建状态 - buildId: {}, size: {}B", buildId, json.length());
log.info("Redis查询构建状态 - buildId: {}, found: {}", buildId, response != null);
log.error("Redis操作失败 - operation: {}, buildId: {}", operation, buildId, e);
```

---

## 9. 降级策略

### 9.1 Redis不可用时的降级

**方案1：回退到内存存储**
```java
@Service
public class BuildStatusManager {

    private final RedisTemplate<String, String> redisTemplate;
    private final Map<String, PublishResponse> fallbackCache = new ConcurrentHashMap<>();

    public void saveBuildStatus(String buildId, PublishResponse response) {
        try {
            // 优先使用Redis
            saveToRedis(buildId, response);
        } catch (Exception e) {
            log.error("Redis保存失败，使用内存降级 - buildId: {}", buildId, e);
            fallbackCache.put(buildId, response);
        }
    }
}
```

**方案2：快速失败**
```java
public PublishResponse getBuildStatus(String buildId) {
    try {
        return getFromRedis(buildId);
    } catch (Exception e) {
        log.error("Redis查询失败 - buildId: {}", buildId, e);
        throw new ServiceUnavailableException("构建状态服务暂时不可用");
    }
}
```

### 9.2 熔断器配置

使用Resilience4j配置熔断器：
```yaml
resilience4j:
  circuitbreaker:
    instances:
      redis:
        failure-rate-threshold: 50
        wait-duration-in-open-state: 10s
        sliding-window-size: 10
```

---

## 10. 测试计划

### 10.1 单元测试

- [ ] 保存构建状态
- [ ] 查询构建状态（存在/不存在）
- [ ] 更新平台状态
- [ ] 删除构建状态
- [ ] TTL验证（模拟过期）

### 10.2 集成测试

- [ ] 使用TestContainers启动真实Redis
- [ ] 验证序列化/反序列化正确性
- [ ] 验证并发读写安全性

### 10.3 E2E测试

- [ ] 验证PublishE2ETest仍然通过
- [ ] 验证状态持久化（重启后查询）

---

## 11. 实施清单

### 11.1 代码改动

- [ ] 修改BuildStatusManager注入RedisTemplate
- [ ] 替换ConcurrentHashMap为Redis操作
- [ ] 配置ObjectMapper序列化LocalDateTime
- [ ] 添加Redis配置类（可选，Spring Boot已自动配置）

### 11.2 配置改动

- [ ] `application.yml`添加Redis连接配置
- [ ] `application-test.yml`配置TestContainers Redis

### 11.3 测试验证

- [ ] 运行单元测试
- [ ] 运行E2E测试
- [ ] 手动验证Redis存储

---

## 12. 部署检查清单

### 12.1 生产环境

- [ ] Redis服务已部署（版本≥7.0）
- [ ] Redis密码已配置
- [ ] Redis持久化已启用（AOF或RDB）
- [ ] Redis内存限制已设置
- [ ] 监控和告警已配置

### 12.2 配置验证

```bash
# 检查Redis连接
redis-cli -h localhost -p 6379 PING

# 检查TTL配置
redis-cli -h localhost -p 6379 TTL ingenio:publish:build:xxx

# 检查内存使用
redis-cli -h localhost -p 6379 INFO memory
```

---

**Made with ❤️ by Ingenio Team**
