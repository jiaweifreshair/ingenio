# TimeMachine API 参考文档

## 概述

TimeMachine（时光机）是Ingenio项目的版本快照系统，提供版本历史追溯、版本对比、版本回滚等核心功能。帮助开发者在代码生成过程中记录每个阶段的完整状态，支持时光机调试功能。

**版本**: v1
**基础路径**: `/api/v1/timemachine`
**认证要求**: 所有接口需要登录（`@SaCheckLogin`）

---

## 核心功能

1. **版本快照创建** - 记录每个阶段的完整状态
2. **版本历史时间线** - 按时间倒序展示所有版本
3. **版本差异对比** - 对比任意两个版本的差异
4. **版本回滚** - 回退到任意历史版本
5. **版本详情查询** - 查看版本完整快照数据
6. **版本删除** - 物理删除指定版本（慎用）

---

## API端点清单

| 方法 | 路径 | 描述 | 认证 |
|------|------|------|------|
| `GET` | `/v1/timemachine/timeline/{taskId}` | 获取版本历史时间线 | ✅ |
| `GET` | `/v1/timemachine/version/{versionId}` | 获取版本详情 | ✅ |
| `GET` | `/v1/timemachine/diff` | 对比版本差异 | ✅ |
| `POST` | `/v1/timemachine/rollback/{versionId}` | 回滚到指定版本 | ✅ |
| `DELETE` | `/v1/timemachine/version/{versionId}` | 删除版本 | ✅ |

---

## 版本类型说明（VersionType）

TimeMachine支持8种版本类型，对应代码生成的不同阶段：

| 类型 | 显示名称 | 描述 | 触发时机 |
|------|----------|------|----------|
| `PLAN` | 规划 | PlanAgent完成需求分析 | 需求分析完成后 |
| `SCHEMA` | 数据库设计 | DatabaseSchemaGenerator生成DDL | 数据库Schema生成后 |
| `CODE` | 代码生成 | ExecuteAgent生成Kotlin/Compose代码 | 代码生成完成后 |
| `VALIDATION_FAILED` | 验证失败 | 编译/测试/性能验证失败 | 验证失败时 |
| `VALIDATION_SUCCESS` | 验证成功 | 所有测试通过，可发布 | 验证通过时 |
| `FIX` | Bug修复 | 修复验证失败的问题 | Bug修复后 |
| `ROLLBACK` | 版本回滚 | 回滚到历史版本 | 用户主动回滚时 |
| `FINAL` | 最终发布 | 用户确认发布版本 | 用户确认发布时 |

---

## API详细说明

### 1. 获取版本历史时间线

获取指定任务的所有版本历史，按时间倒序排列。

#### 请求

```http
GET /api/v1/timemachine/timeline/{taskId}
```

**路径参数**

| 参数 | 类型 | 必填 | 描述 |
|------|------|------|------|
| `taskId` | UUID | ✅ | 任务ID |

**请求头**

```http
Authorization: Bearer {token}
Content-Type: application/json
```

#### 响应

**成功响应 (200)**

```json
{
  "code": 200,
  "success": true,
  "message": "成功",
  "data": [
    {
      "versionId": "550e8400-e29b-41d4-a716-446655440002",
      "versionNumber": 2,
      "versionType": "schema",
      "versionTypeDisplay": "数据库设计",
      "timestamp": "2025-11-09T14:30:00",
      "summary": "生成 3 个表的DDL",
      "status": "in_progress",
      "canRollback": true,
      "parentVersionId": null
    },
    {
      "versionId": "550e8400-e29b-41d4-a716-446655440001",
      "versionNumber": 1,
      "versionType": "plan",
      "versionTypeDisplay": "规划",
      "timestamp": "2025-11-09T14:25:00",
      "summary": "分析需求，提取 5 个实体",
      "status": "in_progress",
      "canRollback": true,
      "parentVersionId": null
    }
  ]
}
```

**字段说明**

| 字段 | 类型 | 描述 |
|------|------|------|
| `versionId` | UUID | 版本唯一标识 |
| `versionNumber` | Integer | 版本号（从1开始递增） |
| `versionType` | String | 版本类型（枚举值） |
| `versionTypeDisplay` | String | 版本类型显示名称 |
| `timestamp` | DateTime | 版本创建时间 |
| `summary` | String | 版本摘要描述 |
| `status` | String | 版本状态：`in_progress`/`success`/`failed` |
| `canRollback` | Boolean | 是否支持回滚 |
| `parentVersionId` | UUID | 父版本ID（回滚版本专用） |

#### 使用示例

**curl**

```bash
curl -X GET 'http://localhost:8080/api/v1/timemachine/timeline/550e8400-e29b-41d4-a716-446655440000' \
  -H 'Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...' \
  -H 'Content-Type: application/json'
```

**JavaScript/TypeScript**

```typescript
const response = await fetch(
  `http://localhost:8080/api/v1/timemachine/timeline/${taskId}`,
  {
    method: 'GET',
    headers: {
      'Authorization': `Bearer ${token}`,
      'Content-Type': 'application/json'
    }
  }
);
const { data } = await response.json();
console.log('版本时间线:', data);
```

---

### 2. 获取版本详情

获取指定版本的完整快照数据。

#### 请求

```http
GET /api/v1/timemachine/version/{versionId}
```

**路径参数**

| 参数 | 类型 | 必填 | 描述 |
|------|------|------|------|
| `versionId` | UUID | ✅ | 版本ID |

#### 响应

**成功响应 (200)**

```json
{
  "code": 200,
  "success": true,
  "message": "成功",
  "data": {
    "id": "550e8400-e29b-41d4-a716-446655440001",
    "tenantId": "550e8400-e29b-41d4-a716-446655440010",
    "taskId": "550e8400-e29b-41d4-a716-446655440000",
    "versionNumber": 1,
    "versionType": "plan",
    "description": "需求分析完成",
    "snapshot": {
      "entities": {
        "book": {
          "name": "Book",
          "fields": "title,author,isbn"
        }
      },
      "techStack": {
        "backend": "Spring Boot",
        "frontend": "React"
      },
      "version_type_display": "规划",
      "version_type_description": "PlanAgent完成需求分析",
      "created_at": "2025-11-09T14:25:00"
    },
    "createdAt": "2025-11-09T14:25:00",
    "parentVersionId": null
  }
}
```

**字段说明**

| 字段 | 类型 | 描述 |
|------|------|------|
| `id` | UUID | 版本ID |
| `tenantId` | UUID | 租户ID |
| `taskId` | UUID | 所属任务ID |
| `versionNumber` | Integer | 版本号 |
| `versionType` | String | 版本类型 |
| `description` | String | 版本描述 |
| `snapshot` | Object | 完整快照数据（JSON） |
| `createdAt` | DateTime | 创建时间 |
| `parentVersionId` | UUID | 父版本ID |

#### 使用示例

**curl**

```bash
curl -X GET 'http://localhost:8080/api/v1/timemachine/version/550e8400-e29b-41d4-a716-446655440001' \
  -H 'Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...' \
  -H 'Content-Type: application/json'
```

**Python**

```python
import requests

response = requests.get(
    f'http://localhost:8080/api/v1/timemachine/version/{version_id}',
    headers={
        'Authorization': f'Bearer {token}',
        'Content-Type': 'application/json'
    }
)
version_data = response.json()['data']
print(f"版本号: {version_data['versionNumber']}")
print(f"快照数据: {version_data['snapshot']}")
```

---

### 3. 对比版本差异

对比任意两个版本的差异，生成详细的变更报告。

#### 请求

```http
GET /api/v1/timemachine/diff?version1={v1}&version2={v2}
```

**查询参数**

| 参数 | 类型 | 必填 | 描述 |
|------|------|------|------|
| `version1` | UUID | ✅ | 版本1 ID |
| `version2` | UUID | ✅ | 版本2 ID |

#### 响应

**成功响应 (200)**

```json
{
  "code": 200,
  "success": true,
  "message": "成功",
  "data": {
    "version1": {
      "id": "550e8400-e29b-41d4-a716-446655440001",
      "versionNumber": 1,
      "versionType": "plan"
    },
    "version2": {
      "id": "550e8400-e29b-41d4-a716-446655440002",
      "versionNumber": 2,
      "versionType": "schema"
    },
    "differences": {
      "ddl": {
        "type": "added",
        "value": "CREATE TABLE book (id BIGSERIAL PRIMARY KEY, title VARCHAR(200))"
      },
      "entities": {
        "type": "changed",
        "old_value": {
          "book": {
            "name": "Book",
            "fields": "title,author,isbn"
          }
        },
        "new_value": {
          "book": {
            "name": "Book",
            "fields": "id,title,author,isbn"
          }
        }
      }
    },
    "changeCount": 2,
    "changeSummary": "新增 1 项；修改 1 项；",
    "hasMajorChanges": true
  }
}
```

**字段说明**

| 字段 | 类型 | 描述 |
|------|------|------|
| `version1` | Object | 版本1基本信息 |
| `version2` | Object | 版本2基本信息 |
| `differences` | Object | 差异详情（key为变更字段） |
| `differences.*.type` | String | 变更类型：`added`/`removed`/`changed` |
| `changeCount` | Integer | 变更数量 |
| `changeSummary` | String | 变更摘要文本 |
| `hasMajorChanges` | Boolean | 是否包含重大变更 |

**重大变更检测规则**

以下字段的变更会被标记为重大变更：
- 包含 `schema` 的字段
- 包含 `code` 的字段
- 包含 `entity` 的字段
- 包含 `table` 的字段

#### 使用示例

**curl**

```bash
curl -X GET 'http://localhost:8080/api/v1/timemachine/diff?version1=550e8400-e29b-41d4-a716-446655440001&version2=550e8400-e29b-41d4-a716-446655440002' \
  -H 'Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...' \
  -H 'Content-Type: application/json'
```

**Java**

```java
String url = String.format(
    "http://localhost:8080/api/v1/timemachine/diff?version1=%s&version2=%s",
    version1Id, version2Id
);

HttpRequest request = HttpRequest.newBuilder()
    .uri(URI.create(url))
    .header("Authorization", "Bearer " + token)
    .header("Content-Type", "application/json")
    .GET()
    .build();

HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
VersionDiff diff = objectMapper.readValue(response.body(), VersionDiff.class);
```

---

### 4. 回滚到指定版本

创建新任务，复制目标版本的快照数据，实现版本回滚功能。

#### 请求

```http
POST /api/v1/timemachine/rollback/{versionId}
```

**路径参数**

| 参数 | 类型 | 必填 | 描述 |
|------|------|------|------|
| `versionId` | UUID | ✅ | 目标版本ID |

#### 响应

**成功响应 (200)**

```json
{
  "code": 200,
  "success": true,
  "message": "成功",
  "data": {
    "id": "550e8400-e29b-41d4-a716-446655440099",
    "tenantId": "550e8400-e29b-41d4-a716-446655440010",
    "userId": "550e8400-e29b-41d4-a716-446655440020",
    "userRequirement": "创建一个图书管理系统",
    "status": "completed",
    "createdAt": "2025-11-09T15:00:00",
    "completedAt": "2025-11-09T15:00:00"
  }
}
```

**回滚机制说明**

1. **创建新任务**: 不修改原任务，创建新的任务实体
2. **复制快照数据**: 将目标版本的快照数据复制到新任务
3. **创建回滚版本**: 自动创建类型为 `ROLLBACK` 的版本快照
4. **保留回滚记录**: 快照中记录回滚来源（`rollback_from_version_id`）

**回滚快照数据结构**

```json
{
  "rollback_from_version_id": "550e8400-e29b-41d4-a716-446655440001",
  "rollback_from_version_number": 1,
  "rollback_at": "2025-11-09T15:00:00",
  "original_snapshot": {
    "entities": { ... },
    "techStack": { ... }
  }
}
```

#### 使用示例

**curl**

```bash
curl -X POST 'http://localhost:8080/api/v1/timemachine/rollback/550e8400-e29b-41d4-a716-446655440001' \
  -H 'Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...' \
  -H 'Content-Type: application/json'
```

**TypeScript**

```typescript
async function rollbackToVersion(versionId: string, token: string) {
  const response = await fetch(
    `http://localhost:8080/api/v1/timemachine/rollback/${versionId}`,
    {
      method: 'POST',
      headers: {
        'Authorization': `Bearer ${token}`,
        'Content-Type': 'application/json'
      }
    }
  );

  const result = await response.json();
  if (result.success) {
    console.log('回滚成功，新任务ID:', result.data.id);
    return result.data;
  } else {
    throw new Error(result.message);
  }
}
```

---

### 5. 删除版本

物理删除指定版本（慎用，不可恢复）。

#### 请求

```http
DELETE /api/v1/timemachine/version/{versionId}
```

**路径参数**

| 参数 | 类型 | 必填 | 描述 |
|------|------|------|------|
| `versionId` | UUID | ✅ | 版本ID |

#### 响应

**成功响应 (200)**

```json
{
  "code": 200,
  "success": true,
  "message": "成功",
  "data": "版本已删除"
}
```

#### 注意事项

⚠️ **危险操作警告**

1. **物理删除**: 数据将从数据库中永久删除，无法恢复
2. **依赖检查**: 删除前会检查是否有子版本依赖（仅警告，不阻止）
3. **权限要求**: 建议限制此接口的调用权限
4. **审计日志**: 删除操作会记录到日志中

#### 使用示例

**curl**

```bash
curl -X DELETE 'http://localhost:8080/api/v1/timemachine/version/550e8400-e29b-41d4-a716-446655440002' \
  -H 'Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...' \
  -H 'Content-Type: application/json'
```

---

## 错误码说明

### 标准错误响应格式

```json
{
  "code": 404,
  "success": false,
  "message": "版本不存在: 550e8400-e29b-41d4-a716-446655440999",
  "data": null
}
```

### 常见错误码

| 错误码 | 描述 | 可能原因 |
|--------|------|----------|
| `400` | 参数错误 | UUID格式错误、必填参数缺失 |
| `401` | 未认证 | Token缺失或无效 |
| `404` | 资源不存在 | 版本ID或任务ID不存在 |
| `500` | 服务器错误 | 数据库连接失败、快照序列化失败 |

### 错误处理示例

**JavaScript/TypeScript**

```typescript
try {
  const response = await fetch(`/api/v1/timemachine/version/${versionId}`, {
    headers: { 'Authorization': `Bearer ${token}` }
  });

  const result = await response.json();

  if (!result.success) {
    if (result.code === 404) {
      console.error('版本不存在');
    } else if (result.code === 401) {
      console.error('请先登录');
      // 跳转到登录页
    } else {
      console.error('操作失败:', result.message);
    }
    return;
  }

  // 处理成功响应
  console.log('版本详情:', result.data);
} catch (error) {
  console.error('网络错误:', error);
}
```

---

## 使用场景示例

### 场景1: 查看代码生成历史

```typescript
// 1. 获取版本时间线
const timelineResponse = await fetch(
  `/api/v1/timemachine/timeline/${taskId}`,
  { headers: { 'Authorization': `Bearer ${token}` } }
);
const timeline = await timelineResponse.json();

// 2. 渲染时间线UI
timeline.data.forEach(item => {
  console.log(`
    版本 ${item.versionNumber}: ${item.versionTypeDisplay}
    时间: ${item.timestamp}
    摘要: ${item.summary}
    状态: ${item.status}
  `);
});
```

### 场景2: 对比两个版本找出变更

```typescript
// 1. 选择两个版本进行对比
const v1 = timeline.data[2].versionId; // 版本1
const v2 = timeline.data[0].versionId; // 版本3

// 2. 获取差异
const diffResponse = await fetch(
  `/api/v1/timemachine/diff?version1=${v1}&version2=${v2}`,
  { headers: { 'Authorization': `Bearer ${token}` } }
);
const diff = await diffResponse.json();

// 3. 分析变更
console.log(`变更数量: ${diff.data.changeCount}`);
console.log(`变更摘要: ${diff.data.changeSummary}`);
console.log(`是否重大变更: ${diff.data.hasMajorChanges}`);

// 4. 展示详细差异
Object.entries(diff.data.differences).forEach(([key, change]) => {
  switch (change.type) {
    case 'added':
      console.log(`新增 ${key}:`, change.value);
      break;
    case 'removed':
      console.log(`删除 ${key}:`, change.value);
      break;
    case 'changed':
      console.log(`修改 ${key}:`);
      console.log(`  旧值:`, change.old_value);
      console.log(`  新值:`, change.new_value);
      break;
  }
});
```

### 场景3: 回滚到上一个稳定版本

```typescript
// 1. 获取时间线
const timeline = await getTimeline(taskId);

// 2. 找到最后一个验证成功的版本
const lastSuccessVersion = timeline.data.find(
  v => v.versionType === 'validation_success'
);

if (!lastSuccessVersion) {
  console.error('未找到验证成功的版本');
  return;
}

// 3. 确认回滚
const confirmed = await confirm(
  `确认回滚到版本 ${lastSuccessVersion.versionNumber}？\n` +
  `摘要: ${lastSuccessVersion.summary}`
);

if (!confirmed) return;

// 4. 执行回滚
const rollbackResponse = await fetch(
  `/api/v1/timemachine/rollback/${lastSuccessVersion.versionId}`,
  {
    method: 'POST',
    headers: { 'Authorization': `Bearer ${token}` }
  }
);

const result = await rollbackResponse.json();
console.log('回滚成功，新任务ID:', result.data.id);
```

### 场景4: 清理旧版本快照

```typescript
// 1. 获取时间线
const timeline = await getTimeline(taskId);

// 2. 保留最近10个版本，删除其他
const versionsToDelete = timeline.data.slice(10);

// 3. 批量删除
for (const version of versionsToDelete) {
  if (version.versionType !== 'final') {
    await fetch(
      `/api/v1/timemachine/version/${version.versionId}`,
      {
        method: 'DELETE',
        headers: { 'Authorization': `Bearer ${token}` }
      }
    );
    console.log(`已删除版本 ${version.versionNumber}`);
  }
}
```

---

## 数据模型

### GenerationVersionEntity

```java
public class GenerationVersionEntity {
    private UUID id;                      // 版本ID
    private UUID tenantId;                // 租户ID
    private UUID taskId;                  // 任务ID
    private Integer versionNumber;        // 版本号（递增）
    private String versionType;           // 版本类型（VersionType枚举）
    private String description;           // 版本描述
    private Map<String, Object> snapshot; // 快照数据（JSONB）
    private UUID parentVersionId;         // 父版本ID（回滚时使用）
    private LocalDateTime createdAt;      // 创建时间
}
```

### VersionTimelineItem

```java
public class VersionTimelineItem {
    private UUID versionId;               // 版本ID
    private Integer versionNumber;        // 版本号
    private String versionType;           // 版本类型
    private String versionTypeDisplay;    // 版本类型显示名称
    private LocalDateTime timestamp;      // 时间戳
    private String summary;               // 摘要
    private String status;                // 状态
    private Boolean canRollback;          // 是否可回滚
    private UUID parentVersionId;         // 父版本ID
}
```

### VersionDiff

```java
public class VersionDiff {
    private GenerationVersionEntity version1;  // 版本1
    private GenerationVersionEntity version2;  // 版本2
    private Map<String, Object> differences;   // 差异详情
    private Integer changeCount;               // 变更数量
    private String changeSummary;              // 变更摘要
    private Boolean hasMajorChanges;           // 是否重大变更
}
```

---

## 性能优化建议

### 1. 时间线查询优化

```sql
-- 添加索引优化查询
CREATE INDEX idx_version_task_created
ON generation_versions(task_id, created_at DESC);
```

### 2. 快照数据大小控制

```java
// 快照数据建议限制在100KB以内
if (objectMapper.writeValueAsString(snapshotData).length() > 100_000) {
    log.warn("快照数据过大，建议优化: {} bytes", size);
}
```

### 3. 分页查询

对于版本数量超过100的任务，建议实现分页：

```typescript
// 前端分页
const PAGE_SIZE = 20;
const pagedTimeline = timeline.data.slice(
  (page - 1) * PAGE_SIZE,
  page * PAGE_SIZE
);
```

### 4. 缓存策略

```java
// 使用Redis缓存频繁访问的版本
@Cacheable(value = "version", key = "#versionId")
public GenerationVersionEntity getVersion(UUID versionId) {
    return versionMapper.selectById(versionId);
}
```

---

## 安全注意事项

### 1. 权限控制

```java
// 验证用户是否有权限访问该任务的版本
@PreAuthorize("@permissionService.canAccessTask(#taskId)")
public List<VersionTimelineItem> getTimeline(UUID taskId) {
    // ...
}
```

### 2. 敏感数据脱敏

```java
// 快照中不应包含敏感信息
Map<String, Object> snapshot = new HashMap<>(snapshotData);
snapshot.remove("password");
snapshot.remove("apiKey");
snapshot.remove("secretToken");
```

### 3. 删除操作审计

```java
@Transactional
@Audited
public void deleteVersion(UUID versionId) {
    log.warn("删除版本: versionId={}, operator={}",
        versionId, SecurityUtils.getCurrentUser());
    // ...
}
```

---

## 常见问题解答（FAQ）

### Q1: 版本快照何时自动创建？

**A**: 每个关键阶段完成后自动创建：
- `PLAN`: PlanAgent分析完需求
- `SCHEMA`: DatabaseSchemaGenerator生成DDL
- `CODE`: ExecuteAgent生成代码
- `VALIDATION_FAILED`: ValidateAgent测试失败
- `VALIDATION_SUCCESS`: ValidateAgent测试通过
- `FIX`: ExecuteAgent修复Bug
- `ROLLBACK`: 用户主动回滚（手动触发）
- `FINAL`: 用户确认发布（手动触发）

### Q2: 版本号是如何分配的？

**A**: 版本号从1开始递增，每创建一个版本自动+1：
```java
int nextVersion = (latestVersion == null) ? 1 : latestVersion.getVersionNumber() + 1;
```

### Q3: 回滚操作会删除后续版本吗？

**A**: 不会。回滚操作是创建新任务，原任务的所有版本保持不变。这样可以：
- 保留完整历史记录
- 支持多次回滚到不同版本
- 避免误删重要数据

### Q4: 快照数据的大小有限制吗？

**A**: 数据库字段类型为 `JSONB`，理论上无限制，但建议：
- 单个快照 < 100KB（性能考虑）
- 超大数据使用对象存储（如S3），快照仅存储引用

### Q5: 版本对比支持哪些数据类型？

**A**: 支持所有JSON可表示的数据类型：
- 基础类型：String, Number, Boolean
- 复合类型：Object, Array
- 对比策略：深度比较（递归）

### Q6: 删除版本是否可恢复？

**A**: 不可以。删除操作是物理删除，数据永久丢失。建议：
- 仅删除确认无用的版本
- 定期备份数据库
- 实施软删除策略（添加 `deleted` 字段）

### Q7: 如何处理并发创建版本？

**A**: 使用数据库事务 + 版本号递增锁：
```java
@Transactional(isolation = Isolation.SERIALIZABLE)
public GenerationVersionEntity createSnapshot(...) {
    // 串行化隔离级别，防止版本号冲突
}
```

### Q8: 时间线默认排序是什么？

**A**: 按创建时间倒序（最新的在前）：
```sql
ORDER BY created_at DESC
```

### Q9: 版本快照会影响性能吗？

**A**: 影响很小，因为：
- 异步创建（不阻塞主流程）
- JSONB字段索引优化
- 使用连接池和缓存

### Q10: 如何查看某个版本的完整代码？

**A**:
```typescript
const version = await getVersion(versionId);
const code = version.snapshot.generatedCode;
```

---

## 版本历史

### v1.0.0 (2025-11-09)

**初始版本发布**

- ✅ 实现8种版本类型支持
- ✅ 实现版本时间线查询
- ✅ 实现版本差异对比
- ✅ 实现版本回滚功能
- ✅ 实现版本详情查询
- ✅ 实现版本删除功能
- ✅ 通过E2E测试（7个测试用例全部通过）

**已知限制**

- 版本对比不支持可视化展示（仅返回JSON）
- 时间线查询不支持分页（前端分页）
- 快照数据不支持压缩存储

**未来计划**

- [ ] 支持版本标签（Tag）功能
- [ ] 支持版本对比可视化
- [ ] 支持快照数据压缩
- [ ] 支持时间线分页查询
- [ ] 支持版本导出/导入

---

## 相关文档

- [Controller API参考](./CONTROLLER_API.md)
- [数据库Schema设计](../database/SCHEMA.md)
- [E2E测试文档](../../src/test/java/com/ingenio/backend/e2e/README.md)

---

## 技术支持

如有问题或建议，请联系：
- **邮箱**: support@ingenio.com
- **Issue**: https://github.com/ingenio/backend/issues
- **文档更新**: 2025-11-09
