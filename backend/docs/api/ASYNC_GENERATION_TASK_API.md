# 异步代码生成任务API文档

> **版本**: v1.0.0
> **基础路径**: `/api/v1/generate`
> **认证要求**: 需要登录（Bearer Token）

---

## 概述

异步代码生成任务API提供了完整的异步任务管理功能，包括任务创建、状态查询、任务取消和任务列表查询。适用于需要较长时间执行的代码生成任务，避免HTTP超时。

### 核心特性

- **异步执行**: 任务在独立线程池中执行，不阻塞HTTP请求
- **实时进度**: 前端可通过轮询获取任务实时进度
- **Redis缓存**: 任务状态缓存到Redis，减少数据库查询
- **任务取消**: 支持取消正在运行的任务
- **分页查询**: 支持分页查询用户所有任务

### 技术架构

```
前端
  │
  ├── POST /async           → 创建异步任务
  ├── GET  /status/{taskId} → 轮询任务状态（每2秒）
  ├── POST /cancel/{taskId} → 取消任务
  └── GET  /tasks           → 查询任务列表

后端
  │
  ├── GenerateController    → API控制层
  ├── IGenerationTaskService → 任务服务接口
  ├── GenerationTaskServiceImpl → 任务服务实现
  ├── GenerationTaskStatusManager → Redis缓存管理
  ├── GenerationTaskMapper → 数据库访问层
  └── AsyncConfig           → 异步线程池配置
```

---

## API端点

### 1. 创建异步生成任务

**端点**: `POST /api/v1/generate/async`
**认证**: 需要登录
**描述**: 创建异步代码生成任务，立即返回任务ID供前端轮询状态

#### 请求体

```json
{
  "userRequirement": "创建一个校园二手交易平台，支持商品发布、搜索、聊天和交易评价",
  "skipValidation": false,
  "qualityThreshold": 70,
  "generatePreview": true,
  "packageName": "com.example.marketplace"
}
```

#### 请求参数说明

| 字段 | 类型 | 必填 | 默认值 | 说明 |
|-----|------|------|--------|------|
| userRequirement | String | 是 | - | 用户需求描述（最长5000字符） |
| skipValidation | Boolean | 否 | false | 是否跳过质量验证 |
| qualityThreshold | Integer | 否 | 70 | 质量评分阈值（0-100） |
| generatePreview | Boolean | 否 | false | 是否生成预览代码 |
| packageName | String | 否 | 自动生成 | Kotlin包名 |

#### 响应示例（成功）

```json
{
  "code": 200,
  "message": "成功",
  "data": "550e8400-e29b-41d4-a716-446655440000",
  "timestamp": "2025-11-11T15:30:00Z"
}
```

#### 响应参数说明

| 字段 | 类型 | 说明 |
|-----|------|------|
| code | Integer | 状态码（200表示成功） |
| message | String | 响应消息 |
| data | String | 任务ID（UUID格式） |
| timestamp | String | 响应时间戳 |

#### 错误响应示例

```json
{
  "code": 400,
  "message": "用户需求不能为空",
  "data": null,
  "timestamp": "2025-11-11T15:30:00Z"
}
```

---

### 2. 查询任务状态

**端点**: `GET /api/v1/generate/status/{taskId}`
**认证**: 需要登录
**描述**: 查询异步任务的实时状态，建议前端每2秒轮询一次

#### 路径参数

| 参数 | 类型 | 必填 | 说明 |
|-----|------|------|------|
| taskId | String | 是 | 任务ID（UUID格式） |

#### 响应示例（运行中）

```json
{
  "code": 200,
  "message": "成功",
  "data": {
    "taskId": "550e8400-e29b-41d4-a716-446655440000",
    "taskName": "校园二手交易平台...",
    "userRequirement": "创建一个校园二手交易平台，支持商品发布、搜索、聊天和交易评价",
    "status": "executing",
    "statusDescription": "AppSpec生成中",
    "currentAgent": "execute",
    "progress": 45,
    "agents": [
      {
        "agentType": "plan",
        "agentName": "规划Agent",
        "status": "completed",
        "statusDescription": "已完成",
        "startedAt": "2025-11-11T15:30:00",
        "completedAt": "2025-11-11T15:30:30",
        "durationMs": 30000,
        "progress": 100,
        "resultSummary": "识别3个实体，5个API端点",
        "errorMessage": null,
        "metadata": {}
      },
      {
        "agentType": "execute",
        "agentName": "执行Agent",
        "status": "running",
        "statusDescription": "运行中",
        "startedAt": "2025-11-11T15:30:30",
        "completedAt": null,
        "durationMs": null,
        "progress": 45,
        "resultSummary": null,
        "errorMessage": null,
        "metadata": {}
      }
    ],
    "startedAt": "2025-11-11T15:30:00",
    "completedAt": null,
    "estimatedRemainingSeconds": 120,
    "appSpecId": null,
    "qualityScore": null,
    "downloadUrl": null,
    "previewUrl": null,
    "tokenUsage": {
      "planTokens": 2500,
      "executeTokens": 0,
      "validateTokens": 0,
      "totalTokens": 2500,
      "estimatedCost": 0.05
    },
    "errorMessage": null,
    "createdAt": "2025-11-11T15:30:00",
    "updatedAt": "2025-11-11T15:32:15"
  },
  "timestamp": "2025-11-11T15:32:15Z"
}
```

#### 响应示例（已完成）

```json
{
  "code": 200,
  "message": "成功",
  "data": {
    "taskId": "550e8400-e29b-41d4-a716-446655440000",
    "taskName": "校园二手交易平台...",
    "status": "completed",
    "statusDescription": "任务完成",
    "currentAgent": "generate",
    "progress": 100,
    "agents": [...],
    "startedAt": "2025-11-11T15:30:00",
    "completedAt": "2025-11-11T15:35:00",
    "estimatedRemainingSeconds": 0,
    "appSpecId": "660e8400-e29b-41d4-a716-446655440001",
    "qualityScore": 85,
    "downloadUrl": "https://minio.example.com/ingenio/generated/campus-marketplace.zip",
    "previewUrl": "https://preview.example.com/660e8400",
    "tokenUsage": {
      "planTokens": 2500,
      "executeTokens": 5000,
      "validateTokens": 1500,
      "totalTokens": 9000,
      "estimatedCost": 0.18
    },
    "errorMessage": null,
    "createdAt": "2025-11-11T15:30:00",
    "updatedAt": "2025-11-11T15:35:00"
  },
  "timestamp": "2025-11-11T15:35:00Z"
}
```

#### 任务状态枚举

| 状态值 | 描述 | 前端行为 |
|-------|------|---------|
| pending | 等待执行 | 继续轮询 |
| planning | 需求规划中 | 继续轮询 |
| executing | AppSpec生成中 | 继续轮询 |
| validating | 质量验证中 | 继续轮询 |
| generating | 代码生成中 | 继续轮询 |
| completed | 任务完成 | 停止轮询，显示下载链接 |
| failed | 任务失败 | 停止轮询，显示错误信息 |
| cancelled | 任务取消 | 停止轮询 |

#### 轮询建议

```typescript
// 前端轮询逻辑示例
const pollTaskStatus = async (taskId: string) => {
  const poll = async () => {
    const response = await fetch(`/api/v1/generate/status/${taskId}`, {
      headers: { Authorization: `Bearer ${token}` }
    });
    const result = await response.json();

    const status = result.data.status;

    // 检查是否完成
    if (status === 'completed' || status === 'failed' || status === 'cancelled') {
      clearInterval(intervalId);
      handleComplete(result.data);
    } else {
      // 更新进度条
      updateProgress(result.data.progress);
    }
  };

  // 立即执行一次
  await poll();

  // 每2秒轮询一次
  const intervalId = setInterval(poll, 2000);
};
```

---

### 3. 取消任务

**端点**: `POST /api/v1/generate/cancel/{taskId}`
**认证**: 需要登录
**描述**: 取消正在运行或等待的任务

#### 路径参数

| 参数 | 类型 | 必填 | 说明 |
|-----|------|------|------|
| taskId | String | 是 | 任务ID（UUID格式） |

#### 响应示例（成功）

```json
{
  "code": 200,
  "message": "成功",
  "data": "任务已取消",
  "timestamp": "2025-11-11T15:35:00Z"
}
```

#### 错误响应示例（任务已完成，无法取消）

```json
{
  "code": 400,
  "message": "任务无法取消（当前状态：completed）",
  "data": null,
  "timestamp": "2025-11-11T15:35:00Z"
}
```

#### 可取消状态

| 状态 | 是否可取消 |
|-----|-----------|
| pending | 是 |
| planning | 是 |
| executing | 是 |
| validating | 是 |
| generating | 是 |
| completed | 否 |
| failed | 否 |
| cancelled | 否 |

---

### 4. 获取用户任务列表

**端点**: `GET /api/v1/generate/tasks`
**认证**: 需要登录
**描述**: 分页查询当前用户的所有任务，支持状态筛选

#### 查询参数

| 参数 | 类型 | 必填 | 默认值 | 说明 |
|-----|------|------|--------|------|
| pageNum | Integer | 否 | 1 | 页码（从1开始） |
| pageSize | Integer | 否 | 10 | 页大小（最大100） |
| status | String | 否 | - | 任务状态筛选 |

#### 请求示例

```bash
GET /api/v1/generate/tasks?pageNum=1&pageSize=10&status=completed
Authorization: Bearer eyJhbGciOiJIUzI1NiIs...
```

#### 响应示例

```json
{
  "code": 200,
  "message": "成功",
  "data": {
    "total": 25,
    "pageNum": 1,
    "pageSize": 10,
    "pages": 3,
    "tasks": [
      {
        "taskId": "550e8400-e29b-41d4-a716-446655440000",
        "taskName": "校园二手交易平台...",
        "status": "completed",
        "statusDescription": "任务完成",
        "progress": 100,
        "currentAgent": "generate",
        "appSpecId": "660e8400-e29b-41d4-a716-446655440001",
        "qualityScore": 85,
        "downloadUrl": "https://minio.example.com/...",
        "errorMessage": null,
        "createdAt": "2025-11-11T15:30:00",
        "completedAt": "2025-11-11T15:35:00",
        "updatedAt": "2025-11-11T15:35:00"
      },
      {
        "taskId": "660e8400-e29b-41d4-a716-446655440002",
        "taskName": "图书管理系统...",
        "status": "failed",
        "statusDescription": "任务失败",
        "progress": 65,
        "currentAgent": "validate",
        "appSpecId": null,
        "qualityScore": null,
        "downloadUrl": null,
        "errorMessage": "质量评分(55)低于阈值(70)，请优化需求后重试",
        "createdAt": "2025-11-10T10:00:00",
        "completedAt": "2025-11-10T10:05:00",
        "updatedAt": "2025-11-10T10:05:00"
      }
    ]
  },
  "timestamp": "2025-11-11T15:35:00Z"
}
```

#### 响应参数说明

| 字段 | 类型 | 说明 |
|-----|------|------|
| total | Long | 总记录数 |
| pageNum | Integer | 当前页码 |
| pageSize | Integer | 页大小 |
| pages | Long | 总页数 |
| tasks | Array | 任务列表 |

---

## 错误码

| 错误码 | 说明 | 示例 |
|-------|------|------|
| 200 | 成功 | - |
| 400 | 请求参数错误 | 任务ID格式错误 |
| 401 | 未登录 | Token过期或无效 |
| 404 | 任务不存在 | 任务ID不存在 |
| 500 | 服务器错误 | 数据库连接失败 |

---

## 完整工作流示例

```javascript
// 1. 创建异步任务
async function createAsyncTask(userRequirement) {
  const response = await fetch('/api/v1/generate/async', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'Authorization': `Bearer ${token}`
    },
    body: JSON.stringify({
      userRequirement: userRequirement,
      skipValidation: false,
      qualityThreshold: 70,
      generatePreview: true
    })
  });

  const result = await response.json();
  return result.data; // 返回任务ID
}

// 2. 轮询任务状态
async function pollTaskStatus(taskId, onProgress, onComplete) {
  let intervalId = null;

  const poll = async () => {
    try {
      const response = await fetch(`/api/v1/generate/status/${taskId}`, {
        headers: { 'Authorization': `Bearer ${token}` }
      });
      const result = await response.json();

      const { status, progress, errorMessage } = result.data;

      // 更新进度
      onProgress(progress, status);

      // 检查是否完成
      if (status === 'completed') {
        clearInterval(intervalId);
        onComplete(result.data);
      } else if (status === 'failed' || status === 'cancelled') {
        clearInterval(intervalId);
        onComplete(null, errorMessage);
      }
    } catch (error) {
      console.error('轮询失败:', error);
      clearInterval(intervalId);
      onComplete(null, error.message);
    }
  };

  // 立即执行一次
  await poll();

  // 每2秒轮询一次
  intervalId = setInterval(poll, 2000);

  // 返回取消轮询的方法
  return () => clearInterval(intervalId);
}

// 3. 取消任务
async function cancelTask(taskId) {
  const response = await fetch(`/api/v1/generate/cancel/${taskId}`, {
    method: 'POST',
    headers: { 'Authorization': `Bearer ${token}` }
  });

  return await response.json();
}

// 4. 完整流程示例
async function generateApp(userRequirement) {
  // 创建任务
  const taskId = await createAsyncTask(userRequirement);
  console.log('任务已创建:', taskId);

  // 轮询状态
  const stopPolling = await pollTaskStatus(
    taskId,
    (progress, status) => {
      console.log(`进度: ${progress}%, 状态: ${status}`);
      updateProgressBar(progress);
    },
    (result, error) => {
      if (error) {
        console.error('生成失败:', error);
        showError(error);
      } else {
        console.log('生成成功:', result);
        showDownloadLink(result.downloadUrl);
      }
    }
  );

  // 用户点击取消按钮
  cancelButton.onclick = async () => {
    await cancelTask(taskId);
    stopPolling();
  };
}
```

---

## 性能指标

| 指标 | 目标值 |
|-----|-------|
| 创建任务响应时间 | <500ms |
| 状态查询响应时间（缓存命中） | <50ms |
| 状态查询响应时间（缓存miss） | <200ms |
| 任务执行时间（小型应用） | 2-5分钟 |
| 任务执行时间（中型应用） | 5-10分钟 |
| 并发任务数（核心线程） | 3个 |
| 并发任务数（最大线程） | 5个 |
| 排队任务数（队列容量） | 50个 |

---

## 监控和日志

### 日志级别

- **INFO**: 任务创建、完成、取消
- **DEBUG**: 任务状态更新、Redis缓存操作
- **ERROR**: 任务执行异常、数据库错误

### 日志示例

```log
2025-11-11 15:30:00 [async-generation-1] INFO  GenerationTaskServiceImpl - 创建异步生成任务 - userRequirement: 创建一个校园二手交易平台
2025-11-11 15:30:00 [async-generation-1] INFO  GenerationTaskServiceImpl - 任务创建成功 - taskId: 550e8400-e29b-41d4-a716-446655440000, userId: 770e8400-e29b-41d4-a716-446655440003
2025-11-11 15:30:00 [async-generation-1] INFO  GenerationTaskServiceImpl - 异步任务开始执行 - taskId: 550e8400-e29b-41d4-a716-446655440000
2025-11-11 15:35:00 [async-generation-1] INFO  GenerationTaskServiceImpl - 异步任务执行成功 - taskId: 550e8400-e29b-41d4-a716-446655440000, appSpecId: 660e8400-e29b-41d4-a716-446655440001
```

---

## 常见问题

### Q1: 任务执行需要多久？

**A**: 取决于应用复杂度：
- 简单应用（3个实体）：2-5分钟
- 中等应用（5-10个实体）：5-10分钟
- 复杂应用（10个以上实体）：10-15分钟

### Q2: 轮询频率应该设置多少？

**A**: 建议2秒轮询一次。轮询太频繁会增加服务器负载，太慢会影响用户体验。

### Q3: 任务失败后可以重试吗？

**A**: 可以。任务失败后，用户可以修改需求重新创建任务。系统会保留失败的任务记录供参考。

### Q4: 如何处理长时间未完成的任务？

**A**: 系统会在任务创建30分钟后自动标记为超时失败。用户可以取消长时间未完成的任务。

### Q5: Redis缓存失效后会怎样？

**A**: 系统会自动从PostgreSQL数据库加载任务状态，并重新写入Redis缓存。对用户无感知。

---

## 数据库Schema

```sql
-- 生成任务表
CREATE TABLE generation_tasks (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    user_id UUID NOT NULL,
    task_name VARCHAR(255) NOT NULL,
    user_requirement TEXT NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'pending',
    current_agent VARCHAR(50),
    progress INTEGER DEFAULT 0,
    agents_info JSONB,
    plan_result JSONB,
    app_spec_content JSONB,
    validate_result JSONB,
    app_spec_id UUID,
    quality_score INTEGER,
    download_url VARCHAR(500),
    preview_url VARCHAR(500),
    token_usage JSONB,
    error_message TEXT,
    started_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    completed_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    metadata JSONB
);

-- 索引
CREATE INDEX idx_generation_tasks_user_id ON generation_tasks(user_id);
CREATE INDEX idx_generation_tasks_status ON generation_tasks(status);
CREATE INDEX idx_generation_tasks_created_at ON generation_tasks(created_at);
```

---

## Redis Key设计

```
# 任务状态缓存
Key: ingenio:generation:task:{taskId}
Type: String (JSON序列化)
TTL: 1小时（运行中） / 24小时（已完成）
Value: GenerationTaskEntity的JSON表示

# 示例
Key: ingenio:generation:task:550e8400-e29b-41d4-a716-446655440000
Value: {"id":"550e8400-...","status":"executing","progress":45,...}
TTL: 3600秒
```

---

## 安全考虑

1. **认证**: 所有接口需要Sa-Token JWT认证
2. **权限**: 用户只能查询和操作自己创建的任务
3. **限流**: AI API调用有每小时限流（100次）
4. **输入验证**: 用户需求长度限制5000字符
5. **SQL注入防护**: 使用MyBatis-Plus参数化查询

---

## 相关文档

- [GenerateService API文档](./GENERATE_SERVICE_API.md)
- [AI Agent架构文档](../architecture/AI_AGENT_ARCHITECTURE.md)
- [Redis缓存设计](../architecture/REDIS_CACHE_DESIGN.md)
- [异步任务配置](../configuration/ASYNC_CONFIG.md)

---

**Made with ❤️ by Ingenio Team**
