# Ingenio Backend REST API 文档

## 概述

本文档描述Ingenio平台的REST API接口，涵盖用户管理、AppSpec管理、项目管理和AI生成等核心功能。

**Base URL**: `http://localhost:8080/api/v1`

**认证方式**: SaToken JWT Bearer Token

**请求头**:
```
Authorization: Bearer {access_token}
Content-Type: application/json
```

---

## 1. 用户管理 API（UserController）

### 1.1 用户注册

**接口**: `POST /users/register`

**权限**: 公开接口

**请求体**:
```json
{
  "username": "john_doe",
  "email": "john@example.com",
  "password": "password123",
  "tenantId": "default",
  "displayName": "John Doe"
}
```

**响应**:
```json
{
  "code": "0000",
  "message": "操作成功",
  "data": {
    "id": "uuid",
    "tenantId": "uuid",
    "username": "john_doe",
    "email": "john@example.com",
    "displayName": "John Doe",
    "role": "user",
    "status": "active",
    "createdAt": "2025-01-01 12:00:00"
  },
  "timestamp": 1704067200000
}
```

---

### 1.2 用户登录

**接口**: `POST /users/login`

**权限**: 公开接口

**请求体**:
```json
{
  "username": "john_doe",
  "password": "password123",
  "tenantId": "default",
  "rememberMe": false
}
```

**响应**:
```json
{
  "code": "0000",
  "message": "操作成功",
  "data": {
    "accessToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
    "tokenType": "Bearer",
    "expiresIn": 7200,
    "user": {
      "id": "uuid",
      "username": "john_doe",
      "email": "john@example.com",
      "displayName": "John Doe",
      "role": "user",
      "status": "active"
    }
  },
  "timestamp": 1704067200000
}
```

---

### 1.3 获取当前用户信息

**接口**: `GET /users/me`

**权限**: 需要登录

**请求头**:
```
Authorization: Bearer {access_token}
```

**响应**:
```json
{
  "code": "0000",
  "message": "操作成功",
  "data": {
    "id": "uuid",
    "username": "john_doe",
    "email": "john@example.com",
    "displayName": "John Doe",
    "role": "user",
    "status": "active",
    "lastLoginAt": "2025-01-01 12:00:00"
  },
  "timestamp": 1704067200000
}
```

---

### 1.4 修改密码

**接口**: `PUT /users/password`

**权限**: 需要登录

**请求体**:
```json
{
  "oldPassword": "old_password123",
  "newPassword": "new_password456",
  "confirmPassword": "new_password456"
}
```

**响应**:
```json
{
  "code": "0000",
  "message": "操作成功",
  "data": null,
  "timestamp": 1704067200000
}
```

---

### 1.5 退出登录

**接口**: `POST /users/logout`

**权限**: 需要登录

**响应**:
```json
{
  "code": "0000",
  "message": "退出登录成功",
  "data": null,
  "timestamp": 1704067200000
}
```

---

## 2. AppSpec管理 API（AppSpecController）

### 2.1 创建AppSpec

**接口**: `POST /appspecs`

**权限**: 需要登录

**请求体**:
```json
{
  "specContent": {
    "pages": { ... },
    "dataModels": { ... },
    "flows": { ... }
  },
  "parentVersionId": null,
  "metadata": {}
}
```

**响应**:
```json
{
  "code": "0000",
  "message": "操作成功",
  "data": {
    "id": "uuid",
    "tenantId": "uuid",
    "createdByUserId": "uuid",
    "specContent": { ... },
    "version": 1,
    "status": "draft",
    "qualityScore": null,
    "createdAt": "2025-01-01 12:00:00"
  },
  "timestamp": 1704067200000
}
```

---

### 2.2 获取AppSpec详情

**接口**: `GET /appspecs/{id}`

**权限**: 需要登录（租户隔离）

**路径参数**:
- `id`: AppSpec UUID

**响应**:
```json
{
  "code": "0000",
  "message": "操作成功",
  "data": {
    "id": "uuid",
    "specContent": { ... },
    "version": 1,
    "status": "validated",
    "qualityScore": 85
  },
  "timestamp": 1704067200000
}
```

---

### 2.3 分页查询AppSpec列表

**接口**: `GET /appspecs?current=1&size=10`

**权限**: 需要登录

**查询参数**:
- `current`: 当前页码（默认1）
- `size`: 每页数量（默认10）

**响应**:
```json
{
  "code": "0000",
  "message": "操作成功",
  "data": {
    "records": [ ... ],
    "total": 100,
    "size": 10,
    "current": 1,
    "pages": 10,
    "hasNext": true,
    "hasPrevious": false
  },
  "timestamp": 1704067200000
}
```

---

### 2.4 更新AppSpec状态

**接口**: `PUT /appspecs/{id}/status`

**权限**: 需要登录

**请求体**:
```json
{
  "status": "validated"
}
```

**响应**:
```json
{
  "code": "0000",
  "message": "操作成功",
  "data": null,
  "timestamp": 1704067200000
}
```

---

### 2.5 创建AppSpec新版本

**接口**: `POST /appspecs/{id}/versions`

**权限**: 需要登录

**路径参数**:
- `id`: 父版本AppSpec UUID

**请求体**:
```json
{
  "specContent": { ... }
}
```

**响应**:
```json
{
  "code": "0000",
  "message": "操作成功",
  "data": {
    "id": "new_uuid",
    "version": 2,
    "parentVersionId": "parent_uuid",
    "status": "draft"
  },
  "timestamp": 1704067200000
}
```

---

### 2.6 删除AppSpec

**接口**: `DELETE /appspecs/{id}`

**权限**: 需要登录（租户隔离）

**响应**:
```json
{
  "code": "0000",
  "message": "删除成功",
  "data": null,
  "timestamp": 1704067200000
}
```

---

## 3. 项目管理 API（ProjectController）

### 3.1 创建项目

**接口**: `POST /projects`

**权限**: 需要登录

**请求体**:
```json
{
  "name": "我的第一个项目",
  "description": "这是一个学习项目",
  "coverImageUrl": "https://example.com/cover.jpg",
  "appSpecId": "uuid",
  "visibility": "public",
  "tags": ["教育", "游戏"],
  "ageGroup": "middle_school",
  "metadata": {}
}
```

**响应**:
```json
{
  "code": "0000",
  "message": "操作成功",
  "data": {
    "id": "uuid",
    "name": "我的第一个项目",
    "status": "draft",
    "visibility": "public",
    "viewCount": 0,
    "likeCount": 0,
    "forkCount": 0,
    "createdAt": "2025-01-01 12:00:00"
  },
  "timestamp": 1704067200000
}
```

---

### 3.2 获取项目详情

**接口**: `GET /projects/{id}`

**权限**: 需要登录（租户隔离）

**响应**:
```json
{
  "code": "0000",
  "message": "操作成功",
  "data": {
    "id": "uuid",
    "name": "我的第一个项目",
    "description": "这是一个学习项目",
    "status": "published",
    "viewCount": 150,
    "likeCount": 20,
    "isLiked": false,
    "isFavorited": false
  },
  "timestamp": 1704067200000
}
```

---

### 3.3 更新项目

**接口**: `PUT /projects/{id}`

**权限**: 需要登录（只能更新自己的项目）

**请求体**:
```json
{
  "name": "更新后的项目名称",
  "description": "更新后的描述",
  "visibility": "public"
}
```

---

### 3.4 删除项目

**接口**: `DELETE /projects/{id}`

**权限**: 需要登录（只能删除自己的项目）

---

### 3.5 分页查询用户项目列表

**接口**: `GET /projects?current=1&size=10`

**权限**: 需要登录

**查询参数**:
- `current`: 当前页码
- `size`: 每页数量

---

### 3.6 查询公开项目（社区广场）

**接口**: `GET /projects/public?current=1&size=10&ageGroup=middle_school`

**权限**: 公开接口

**查询参数**:
- `current`: 当前页码
- `size`: 每页数量
- `ageGroup`: 年龄分组过滤（可选）

---

### 3.7 派生项目（Fork）

**接口**: `POST /projects/{id}/fork`

**权限**: 需要登录

**响应**:
```json
{
  "code": "0000",
  "message": "操作成功",
  "data": {
    "id": "new_project_uuid",
    "name": "我的第一个项目（派生）",
    "status": "draft"
  },
  "timestamp": 1704067200000
}
```

---

### 3.8 点赞项目

**接口**: `POST /projects/{id}/like`

**权限**: 需要登录

---

### 3.9 取消点赞项目

**接口**: `DELETE /projects/{id}/like`

**权限**: 需要登录

---

### 3.10 收藏项目

**接口**: `POST /projects/{id}/favorite`

**权限**: 需要登录

---

### 3.11 取消收藏项目

**接口**: `DELETE /projects/{id}/favorite`

**权限**: 需要登录

---

### 3.12 发布项目

**接口**: `POST /projects/{id}/publish`

**权限**: 需要登录

**说明**: 将项目状态从draft变为published

---

### 3.13 归档项目

**接口**: `POST /projects/{id}/archive`

**权限**: 需要登录

**说明**: 将项目状态变为archived

---

## 4. AI生成 API（GenerateController）

### 4.1 需求规划（PlanAgent）

**接口**: `POST /generate/plan`

**权限**: 需要登录

**请求体**:
```json
{
  "userRequirement": "我想做一个简单的待办事项应用，可以添加、删除、标记完成任务"
}
```

**响应**:
```json
{
  "code": "0000",
  "message": "操作成功",
  "data": {
    "modules": [
      {
        "name": "任务管理",
        "description": "管理待办事项的增删改查",
        "priority": "high",
        "complexity": 5,
        "dependencies": [],
        "dataModels": ["Task"],
        "pages": ["TaskListPage", "TaskDetailPage"]
      }
    ],
    "complexityScore": 6,
    "reasoning": "这是一个简单的CRUD应用...",
    "suggestedTechStack": ["React", "LocalStorage"],
    "estimatedHours": 4,
    "recommendations": "建议使用本地存储..."
  },
  "timestamp": 1704067200000
}
```

---

### 4.2 生成AppSpec（ExecuteAgent）

**接口**: `POST /generate/appspec`

**权限**: 需要登录

**请求体**:
```json
{
  "modules": [ ... ],
  "complexityScore": 6
}
```

**响应**:
```json
{
  "code": "0000",
  "message": "操作成功",
  "data": {
    "pages": { ... },
    "dataModels": { ... },
    "flows": { ... },
    "permissions": { ... }
  },
  "timestamp": 1704067200000
}
```

---

### 4.3 验证AppSpec（ValidateAgent）

**接口**: `POST /generate/validate`

**权限**: 需要登录

**请求体**:
```json
{
  "pages": { ... },
  "dataModels": { ... }
}
```

**响应**:
```json
{
  "code": "0000",
  "message": "操作成功",
  "data": {
    "isValid": true,
    "qualityScore": 85,
    "errors": [],
    "warnings": [
      {
        "path": "pages.TaskListPage",
        "message": "建议添加搜索功能",
        "suggestion": "增加搜索框组件"
      }
    ],
    "suggestions": ["优化页面布局", "增加数据验证"],
    "summary": "AppSpec结构完整，质量良好"
  },
  "timestamp": 1704067200000
}
```

---

### 4.4 生成代码（KuiklyUIRenderer）

**接口**: `POST /generate/code`

**权限**: 需要登录

**请求体**:
```json
{
  "appSpecId": "uuid"
}
```

**响应**:
```json
{
  "code": "0000",
  "message": "操作成功",
  "data": {
    "downloadUrl": "https://minio.example.com/generated-code/uuid.zip",
    "appSpecId": "uuid"
  },
  "timestamp": 1704067200000
}
```

---

### 4.5 完整生成流程（Plan → Execute → Validate → Code）

**接口**: `POST /generate/full`

**权限**: 需要登录

**请求体**:
```json
{
  "userRequirement": "我想做一个简单的待办事项应用，可以添加、删除、标记完成任务",
  "ageGroup": "middle_school",
  "skipValidation": false,
  "qualityThreshold": 60,
  "generatePreview": true,
  "options": {}
}
```

**响应**:
```json
{
  "code": "0000",
  "message": "操作成功",
  "data": {
    "appSpecId": "uuid",
    "projectId": null,
    "planResult": {
      "modules": [ ... ],
      "complexityScore": 6
    },
    "validateResult": {
      "isValid": true,
      "qualityScore": 85
    },
    "isValid": true,
    "qualityScore": 85,
    "codeDownloadUrl": "https://minio.example.com/generated-code/uuid.zip",
    "previewUrl": "https://preview.ingenio.com/uuid",
    "status": "completed",
    "errorMessage": null,
    "durationMs": 12500,
    "generatedAt": "2025-01-01 12:00:00",
    "tokenUsage": {
      "planTokens": 1500,
      "executeTokens": 3000,
      "validateTokens": 1000,
      "totalTokens": 5500,
      "estimatedCost": 0.055
    }
  },
  "timestamp": 1704067200000
}
```

---

## 5. 错误码说明

| 错误码 | 说明 |
|--------|------|
| 0000 | 操作成功 |
| 1000 | 系统错误 |
| 1001 | 参数错误 |
| 1002 | 资源不存在 |
| 1003 | 未授权 |
| 1004 | 无权限 |
| 2000 | 用户不存在 |
| 2001 | 用户已存在 |
| 2002 | 密码错误 |
| 2004 | 用户未登录 |
| 3000 | AppSpec不存在 |
| 3001 | AppSpec格式错误 |
| 3003 | AppSpec质量评分过低 |
| 4000 | 代码生成失败 |
| 5000 | 项目不存在 |
| 5001 | 无权访问项目 |
| 6000 | 需求规划失败 |
| 6001 | AppSpec生成失败 |
| 6002 | AppSpec验证失败 |
| 7000 | 文件上传失败 |

---

## 6. 通用响应格式

**成功响应**:
```json
{
  "code": "0000",
  "message": "操作成功",
  "data": { ... },
  "timestamp": 1704067200000
}
```

**失败响应**:
```json
{
  "code": "2002",
  "message": "密码错误",
  "data": null,
  "timestamp": 1704067200000
}
```

**参数验证失败响应**:
```json
{
  "code": "1001",
  "message": "参数错误",
  "data": {
    "username": "用户名不能为空",
    "password": "密码长度必须在6-128字符之间"
  },
  "timestamp": 1704067200000
}
```

---

## 7. 分页响应格式

```json
{
  "records": [ ... ],
  "total": 100,
  "size": 10,
  "current": 1,
  "pages": 10,
  "hasNext": true,
  "hasPrevious": false
}
```

---

## 8. 认证流程

1. **注册**: `POST /users/register`
2. **登录**: `POST /users/login` → 获取`accessToken`
3. **使用Token**: 在请求头中添加 `Authorization: Bearer {accessToken}`
4. **刷新Token**: 使用SaToken的自动续签机制
5. **退出登录**: `POST /users/logout`

---

## 9. 租户隔离说明

- 所有需要登录的接口都会自动进行租户隔离
- 租户ID从Token的Session中获取
- 用户只能访问自己租户下的资源
- 公开项目（社区广场）不受租户隔离限制

---

## 10. 使用示例

### 完整的生成流程示例

```bash
# 1. 用户登录
curl -X POST http://localhost:8080/api/v1/users/login \
  -H "Content-Type: application/json" \
  -d '{
    "username": "john_doe",
    "password": "password123"
  }'

# 响应获取到 accessToken

# 2. 执行完整生成流程
curl -X POST http://localhost:8080/api/v1/generate/full \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer {accessToken}" \
  -d '{
    "userRequirement": "我想做一个简单的待办事项应用",
    "ageGroup": "middle_school",
    "generatePreview": true
  }'

# 3. 下载生成的代码
# 使用响应中的 codeDownloadUrl
```

---

## 11. 注意事项

1. **Token有效期**: 默认2小时，可通过`rememberMe`延长
2. **请求频率限制**: 暂未实现，建议合理使用
3. **文件上传大小限制**: AppSpec内容建议不超过5MB
4. **生成超时**: 完整生成流程可能需要10-30秒
5. **并发限制**: AI生成接口建议串行调用，避免并发冲突

---

## 12. 联系方式

- **技术支持**: support@ingenio.com
- **API问题**: api-support@ingenio.com
- **文档更新**: 2025-01-01
- **版本**: v1.0.0
