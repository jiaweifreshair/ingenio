# G3 Agent 提示词优化方案

**版本**: v1.0
**日期**: 2025-12-27
**目的**: 提升G3引擎各Agent的代码生成质量和准确性

---

## 优化原则

1. **Few-Shot Learning**: 添加具体示例提升生成质量
2. **Chain-of-Thought**: 引导AI分步思考降低错误
3. **Output Constraints**: 明确输出格式避免解析失败
4. **Error Prevention**: 预防常见错误模式
5. **Context Awareness**: 提供充足上下文信息

---

## 1. Architect Agent 提示词优化

### 1.1 OpenAPI契约生成提示词（CONTRACT_PROMPT_TEMPLATE）

**当前问题**:
- 缺少具体示例，AI可能生成不规范的契约
- 未明确版本化和错误响应规范
- 缺少数据验证规则说明

**优化后的提示词**:

```java
private static final String CONTRACT_PROMPT_TEMPLATE = """
你是一个专业的API架构师。请严格按照以下要求生成OpenAPI 3.0规范的API契约文档。

## 需求描述
%s

## 核心要求（Critical）
1. **严格遵循OpenAPI 3.0.3规范**，确保契约可被Swagger Parser正确解析
2. **RESTful设计原则**：使用标准HTTP方法（GET/POST/PUT/DELETE）和语义化URL
3. **完整的Schema定义**：所有request/response必须有完整的schema定义
4. **错误响应规范**：统一错误响应格式（code, message, data字段）

## 详细输出要求

### API设计规范
- **资源命名**：使用复数名词（如/tasks而非/task）
- **路径参数**：使用{id}标记，类型为UUID
- **查询参数**：分页使用page/pageSize，搜索使用keyword
- **状态码**：200成功、201创建、400参数错误、404未找到、500服务器错误

### Schema定义规范
- **主键类型**：所有ID字段使用`type: string, format: uuid`
- **时间字段**：使用`type: string, format: date-time`（ISO 8601格式）
- **必填字段**：明确标注required字段
- **字段验证**：添加minLength、maxLength、pattern等验证规则
- **枚举类型**：使用enum定义状态、类型等枚举值

### 标准响应格式
```yaml
# 成功响应（单个对象）
responses:
  '200':
    description: 操作成功
    content:
      application/json:
        schema:
          type: object
          properties:
            code:
              type: integer
              example: 200
            msg:
              type: string
              example: 操作成功
            data:
              $ref: '#/components/schemas/Task'

# 成功响应（列表）
responses:
  '200':
    description: 查询成功
    content:
      application/json:
        schema:
          type: object
          properties:
            code:
              type: integer
              example: 200
            msg:
              type: string
              example: 查询成功
            data:
              type: object
              properties:
                items:
                  type: array
                  items:
                    $ref: '#/components/schemas/Task'
                total:
                  type: integer
                  description: 总数
                page:
                  type: integer
                  description: 当前页码
                pageSize:
                  type: integer
                  description: 每页数量

# 错误响应
responses:
  '400':
    description: 参数错误
    content:
      application/json:
        schema:
          type: object
          properties:
            code:
              type: integer
              example: 400
            msg:
              type: string
              example: 参数验证失败
            data:
              type: object
              nullable: true
```

## 输出格式要求（Strict）

**⚠️ 重要**：直接输出YAML内容，不要使用```yaml标记包裹，不要添加任何解释文字。

**必须**以下面的结构开头：
```
openapi: '3.0.3'
info:
  title: [根据需求生成的系统名称] API
  version: '1.0.0'
  description: [系统描述]
servers:
  - url: http://localhost:8080/api
    description: 本地开发环境
```

## 示例参考

假设需求是"待办事项管理系统"，期望生成如下结构：

```yaml
openapi: '3.0.3'
info:
  title: 待办事项管理系统 API
  version: '1.0.0'
  description: 提供待办事项的增删改查功能
servers:
  - url: http://localhost:8080/api
    description: 本地开发环境

paths:
  /tasks:
    get:
      summary: 获取待办事项列表
      tags: [Tasks]
      parameters:
        - name: page
          in: query
          schema:
            type: integer
            default: 1
        - name: pageSize
          in: query
          schema:
            type: integer
            default: 20
        - name: keyword
          in: query
          required: false
          schema:
            type: string
      responses:
        '200':
          description: 查询成功
          content:
            application/json:
              schema:
                type: object
                properties:
                  code:
                    type: integer
                    example: 200
                  msg:
                    type: string
                    example: 查询成功
                  data:
                    type: object
                    properties:
                      items:
                        type: array
                        items:
                          $ref: '#/components/schemas/Task'
                      total:
                        type: integer
                      page:
                        type: integer
                      pageSize:
                        type: integer
    post:
      summary: 创建待办事项
      tags: [Tasks]
      requestBody:
        required: true
        content:
          application/json:
            schema:
              type: object
              required:
                - title
              properties:
                title:
                  type: string
                  minLength: 1
                  maxLength: 200
                description:
                  type: string
                  maxLength: 1000
                dueDate:
                  type: string
                  format: date-time
      responses:
        '201':
          description: 创建成功
          content:
            application/json:
              schema:
                type: object
                properties:
                  code:
                    type: integer
                    example: 201
                  msg:
                    type: string
                    example: 创建成功
                  data:
                    $ref: '#/components/schemas/Task'

components:
  schemas:
    Task:
      type: object
      required:
        - id
        - title
        - status
        - createdAt
        - updatedAt
      properties:
        id:
          type: string
          format: uuid
          description: 待办事项ID
        title:
          type: string
          description: 标题
          minLength: 1
          maxLength: 200
        description:
          type: string
          description: 描述
          maxLength: 1000
        status:
          type: string
          enum: [PENDING, IN_PROGRESS, COMPLETED, CANCELLED]
          description: 状态
        priority:
          type: string
          enum: [LOW, MEDIUM, HIGH]
          description: 优先级
        dueDate:
          type: string
          format: date-time
          description: 截止时间
        createdAt:
          type: string
          format: date-time
          description: 创建时间
        updatedAt:
          type: string
          format: date-time
          description: 更新时间
```

## 质量检查清单

生成契约后，请自我检查：
- [ ] 所有endpoints都有完整的request/response schema
- [ ] 所有ID字段都是UUID类型
- [ ] 所有时间字段都是date-time格式
- [ ] 错误响应格式统一（code/msg/data）
- [ ] 枚举类型都有明确的enum定义
- [ ] 分页接口包含total/page/pageSize字段
- [ ] 所有schema都在components/schemas下定义
- [ ] 输出是纯YAML内容（无```标记）

现在请根据上述需求和规范生成OpenAPI契约。
""";
```

**优化说明**:
1. ✅ 添加了完整的示例参考（Few-Shot Learning）
2. ✅ 明确了输出格式约束（避免```标记导致解析失败）
3. ✅ 增加了详细的Schema定义规范
4. ✅ 添加了质量检查清单（Self-Verification）
5. ✅ 统一了错误响应格式
6. ✅ 添加了枚举类型、验证规则等细节要求

---

### 1.2 数据库Schema生成提示词（SCHEMA_PROMPT_TEMPLATE）

**当前问题**:
- 缺少索引策略指导
- 外键约束可能导致循环依赖
- 缺少性能优化建议

**优化后的提示词**:

```java
private static final String SCHEMA_PROMPT_TEMPLATE = """
你是一个专业的PostgreSQL数据库架构师。请根据需求和API契约生成高质量的DDL SQL。

## 需求描述
%s

## API契约参考
```yaml
%s
```

## 核心设计原则

### 1. 表设计规范
- **主键策略**：使用UUID类型，DEFAULT gen_random_uuid()
- **命名规范**：表名使用复数形式的snake_case（如tasks、user_profiles）
- **字段命名**：使用snake_case（如created_at、user_id）
- **外键命名**：fk_[表名]_[关联表名]（如fk_tasks_users）
- **索引命名**：idx_[表名]_[字段名]（如idx_tasks_status）

### 2. 必需字段
每个表必须包含以下字段：
```sql
id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
```

### 3. 索引策略

**必需索引**：
- 外键字段（如user_id、task_id）
- 常用查询字段（如status、type）
- 时间字段用于范围查询（created_at、due_date）

**复合索引**：
- 多字段组合查询（如WHERE user_id = ? AND status = ?）

**不建议索引**：
- 低基数字段（如is_active的boolean）
- 大文本字段（TEXT、JSON类型）

### 4. 约束设计

**外键约束**：
⚠️ 注意：避免循环依赖！仅在明确的父子关系中使用。
- 使用ON DELETE CASCADE谨慎（考虑业务影响）
- 使用ON DELETE SET NULL作为安全选项
- 对于多对多关系，使用中间表

**CHECK约束**：
- 枚举类型值验证：`CHECK (status IN ('PENDING', 'COMPLETED'))`
- 数值范围验证：`CHECK (priority >= 1 AND priority <= 5)`
- 字符串长度：`CHECK (LENGTH(title) > 0)`

### 5. 性能优化

**分区表**：
- 大数据量表（>百万行）考虑按时间分区
- 示例：`PARTITION BY RANGE (created_at)`

**JSONB字段**：
- 使用JSONB而非JSON（支持索引）
- 添加GIN索引：`CREATE INDEX idx_metadata ON table USING GIN (metadata)`

## 输出格式要求

**⚠️ 重要**：直接输出SQL内容，不要使用```sql标记包裹。

**必须**按以下顺序组织：
1. 注释说明
2. 核心表（无外键依赖）
3. 关联表（有外键依赖）
4. 索引创建
5. 触发器（如果需要updated_at自动更新）

## 示例参考

假设需求是"待办事项管理系统"，期望生成如下SQL：

```sql
-- ===========================================
-- 待办事项管理系统数据库Schema
-- 生成时间: 2025-12-27
-- ===========================================

-- ===========================================
-- 1. 核心表：tasks（待办事项）
-- ===========================================
CREATE TABLE IF NOT EXISTS tasks (
    -- 主键
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    -- 业务字段
    title VARCHAR(200) NOT NULL CHECK (LENGTH(title) > 0),
    description TEXT,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING'
        CHECK (status IN ('PENDING', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED')),
    priority VARCHAR(10) DEFAULT 'MEDIUM'
        CHECK (priority IN ('LOW', 'MEDIUM', 'HIGH')),
    due_date TIMESTAMP,

    -- 关联字段
    user_id UUID NOT NULL,
    category_id UUID,

    -- 系统字段
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMP,  -- 软删除标记

    -- 表注释
    COMMENT ON TABLE tasks IS '待办事项表';
    COMMENT ON COLUMN tasks.id IS '主键ID';
    COMMENT ON COLUMN tasks.title IS '待办事项标题';
    COMMENT ON COLUMN tasks.description IS '详细描述';
    COMMENT ON COLUMN tasks.status IS '状态：PENDING-待处理, IN_PROGRESS-进行中, COMPLETED-已完成, CANCELLED-已取消';
    COMMENT ON COLUMN tasks.priority IS '优先级：LOW-低, MEDIUM-中, HIGH-高';
);

-- ===========================================
-- 2. 索引创建
-- ===========================================
-- 外键索引（必需）
CREATE INDEX idx_tasks_user_id ON tasks(user_id);
CREATE INDEX idx_tasks_category_id ON tasks(category_id) WHERE category_id IS NOT NULL;

-- 查询优化索引
CREATE INDEX idx_tasks_status ON tasks(status) WHERE deleted_at IS NULL;
CREATE INDEX idx_tasks_due_date ON tasks(due_date) WHERE due_date IS NOT NULL;
CREATE INDEX idx_tasks_created_at ON tasks(created_at DESC);

-- 复合索引（常见查询模式）
CREATE INDEX idx_tasks_user_status ON tasks(user_id, status) WHERE deleted_at IS NULL;

-- 软删除索引
CREATE INDEX idx_tasks_deleted_at ON tasks(deleted_at) WHERE deleted_at IS NOT NULL;

-- ===========================================
-- 3. 触发器：自动更新updated_at
-- ===========================================
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER update_tasks_updated_at
    BEFORE UPDATE ON tasks
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

-- ===========================================
-- 4. 性能优化（可选）
-- ===========================================
-- 为高频查询字段添加部分索引
CREATE INDEX idx_tasks_active_high_priority ON tasks(created_at DESC)
    WHERE status IN ('PENDING', 'IN_PROGRESS') AND priority = 'HIGH' AND deleted_at IS NULL;

-- 统计信息更新（提升查询计划质量）
ANALYZE tasks;
```

## 质量检查清单

生成Schema后，请自我检查：
- [ ] 所有表都有id、created_at、updated_at字段
- [ ] 所有外键字段都有对应索引
- [ ] 枚举类型字段都有CHECK约束
- [ ] 表和关键字段都有注释（COMMENT ON）
- [ ] 索引命名规范一致（idx_开头）
- [ ] 无循环外键依赖
- [ ] 触发器已创建用于auto-update updated_at
- [ ] 输出是纯SQL内容（无```标记）
- [ ] 使用了IF NOT EXISTS避免重复创建错误

## 特别注意

**⚠️ 外键约束问题**：
在G3引擎沙箱验证环境中，如果被引用的表（如users、tenants）不存在，外键约束会导致创建失败。
建议：
1. 暂时不添加FOREIGN KEY约束，仅创建索引
2. 在注释中说明外键关系
3. 在生产环境部署时再添加外键约束

现在请根据上述需求和API契约生成PostgreSQL DDL Schema。
""";
```

**优化说明**:
1. ✅ 添加了完整的DDL示例（Few-Shot Learning）
2. ✅ 明确了索引策略（性能优化）
3. ✅ 增加了CHECK约束规范（数据完整性）
4. ✅ 添加了触发器示例（自动更新updated_at）
5. ✅ 添加了注释规范（COMMENT ON）
6. ✅ 特别说明了外键约束问题（避免Phase 2遇到的问题）

---

## 2. Backend Coder Agent 提示词优化

### 2.1 实体类生成提示词（ENTITY_PROMPT_TEMPLATE）

**当前问题**:
- 未明确UUID生成策略（Phase 2遇到的问题）
- 缺少TypeHandler配置说明
- 缺少JsonType处理

**优化后的提示词**:

```java
private static final String ENTITY_PROMPT_TEMPLATE = """
你是一个专业的Java后端工程师。请根据数据库Schema生成高质量的MyBatis-Plus实体类。

## 数据库Schema
```sql
%s
```

## 核心要求

### 1. 基础配置
- **Java版本**：Java 17（使用现代语法特性）
- **Lombok**：@Data, @Builder, @NoArgsConstructor, @AllArgsConstructor
- **MyBatis-Plus**：@TableName, @TableId, @TableField
- **包路径**：com.ingenio.backend.entity.generated

### 2. UUID主键处理（Critical）

⚠️ **重要**：PostgreSQL的DEFAULT gen_random_uuid()在MyBatis-Plus中不生效！

**正确做法**：
```java
import com.ingenio.backend.config.UUIDv8TypeHandler;
import java.util.UUID;

@TableId(value = "id", type = IdType.ASSIGN_UUID)
@TableField(typeHandler = UUIDv8TypeHandler.class)
private UUID id;
```

**所有UUID类型字段**（包括外键）都需要：
```java
@TableField(value = "user_id", typeHandler = UUIDv8TypeHandler.class)
private UUID userId;
```

### 3. 字段映射规范

| 数据库类型 | Java类型 | 注解 |
|----------|---------|-----|
| UUID | java.util.UUID | @TableField(typeHandler = UUIDv8TypeHandler.class) |
| TIMESTAMP | java.time.Instant | 无需特殊处理 |
| VARCHAR | String | @TableField("column_name") |
| TEXT | String | @TableField("column_name") |
| INTEGER | Integer | @TableField("column_name") |
| BOOLEAN | Boolean | @TableField("column_name") |
| JSONB | String 或 自定义类 | @TableField(typeHandler = JacksonTypeHandler.class) |

### 4. 枚举类型处理

**数据库枚举字段**（如status VARCHAR CHECK IN ('PENDING', 'COMPLETED')）

**方法1：使用内部枚举类**（推荐）
```java
@TableField("status")
private String status;  // 直接存储字符串值

/**
 * 状态枚举
 */
public enum Status {
    PENDING("PENDING", "待处理"),
    IN_PROGRESS("IN_PROGRESS", "进行中"),
    COMPLETED("COMPLETED", "已完成");

    private final String value;
    private final String label;

    Status(String value, String label) {
        this.value = value;
        this.label = label;
    }

    public String getValue() {
        return value;
    }

    public String getLabel() {
        return label;
    }
}
```

### 5. JSONB字段处理

**示例**：
```java
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;

/**
 * 扩展配置（JSONB存储）
 */
@TableField(value = "metadata", typeHandler = JacksonTypeHandler.class)
private Map<String, Object> metadata;
```

### 6. 软删除支持

**如果表包含deleted_at字段**：
```java
@TableLogic
@TableField("deleted_at")
private Instant deletedAt;
```

### 7. JavaDoc注释规范

**类注释**：
```java
/**
 * [表名]实体类
 * 对应数据库表：table_name
 *
 * 业务功能：[描述]
 *
 * @author G3 Engine
 * @since 1.0.0
 */
```

**字段注释**：
```java
/**
 * [字段中文名]
 * 数据库字段：column_name
 * [字段说明]
 */
private String field;
```

## 完整示例

假设数据库有tasks表，期望生成：

```java
// === 文件: TaskEntity.java ===
package com.ingenio.backend.entity.generated;

import com.baomidou.mybatisplus.annotation.*;
import com.ingenio.backend.config.UUIDv8TypeHandler;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * 待办事项实体类
 * 对应数据库表：tasks
 *
 * 业务功能：管理用户的待办事项，包含标题、描述、状态、优先级等信息
 *
 * @author G3 Engine
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName(value = "tasks", autoResultMap = true)
public class TaskEntity {

    /**
     * 主键ID
     * 数据库字段：id
     * 类型：UUID，自动生成
     */
    @TableId(value = "id", type = IdType.ASSIGN_UUID)
    @TableField(typeHandler = UUIDv8TypeHandler.class)
    private UUID id;

    /**
     * 标题
     * 数据库字段：title
     * 必填，最大长度200
     */
    @TableField("title")
    private String title;

    /**
     * 详细描述
     * 数据库字段：description
     * 可选
     */
    @TableField("description")
    private String description;

    /**
     * 状态
     * 数据库字段：status
     * 枚举值：PENDING-待处理, IN_PROGRESS-进行中, COMPLETED-已完成, CANCELLED-已取消
     */
    @TableField("status")
    private String status;

    /**
     * 优先级
     * 数据库字段：priority
     * 枚举值：LOW-低, MEDIUM-中, HIGH-高
     */
    @TableField("priority")
    private String priority;

    /**
     * 截止时间
     * 数据库字段：due_date
     * 可选
     */
    @TableField("due_date")
    private Instant dueDate;

    /**
     * 用户ID（外键）
     * 数据库字段：user_id
     * 关联users表
     */
    @TableField(value = "user_id", typeHandler = UUIDv8TypeHandler.class)
    private UUID userId;

    /**
     * 分类ID（外键）
     * 数据库字段：category_id
     * 关联categories表，可选
     */
    @TableField(value = "category_id", typeHandler = UUIDv8TypeHandler.class)
    private UUID categoryId;

    /**
     * 创建时间
     * 数据库字段：created_at
     * 自动生成
     */
    @TableField("created_at")
    private Instant createdAt;

    /**
     * 更新时间
     * 数据库字段：updated_at
     * 自动更新（通过数据库触发器）
     */
    @TableField("updated_at")
    private Instant updatedAt;

    /**
     * 删除时间（软删除标记）
     * 数据库字段：deleted_at
     * 非空表示已删除
     */
    @TableLogic
    @TableField("deleted_at")
    private Instant deletedAt;

    /**
     * 状态枚举
     * 用于业务逻辑中的类型安全操作
     */
    public enum Status {
        /** 待处理 */
        PENDING("PENDING", "待处理"),
        /** 进行中 */
        IN_PROGRESS("IN_PROGRESS", "进行中"),
        /** 已完成 */
        COMPLETED("COMPLETED", "已完成"),
        /** 已取消 */
        CANCELLED("CANCELLED", "已取消");

        private final String value;
        private final String label;

        Status(String value, String label) {
            this.value = value;
            this.label = label;
        }

        public String getValue() {
            return value;
        }

        public String getLabel() {
            return label;
        }
    }

    /**
     * 优先级枚举
     */
    public enum Priority {
        /** 低优先级 */
        LOW("LOW", "低"),
        /** 中优先级 */
        MEDIUM("MEDIUM", "中"),
        /** 高优先级 */
        HIGH("HIGH", "高");

        private final String value;
        private final String label;

        Priority(String value, String label) {
            this.value = value;
            this.label = label;
        }

        public String getValue() {
            return value;
        }

        public String getLabel() {
            return label;
        }
    }
}
```

## 输出格式要求

**⚠️ 重要**：
1. 每个表生成一个独立的实体类文件
2. 使用`// === 文件: ClassName.java ===`标记文件边界
3. 不要使用```java标记包裹代码
4. 多个文件之间用一个空行分隔

## 质量检查清单

生成实体类后，请自我检查：
- [ ] 所有UUID字段都使用UUIDv8TypeHandler
- [ ] 主键使用@TableId(type = IdType.ASSIGN_UUID)
- [ ] 所有字段都有@TableField注解
- [ ] 枚举字段定义了内部枚举类
- [ ] JSONB字段使用JacksonTypeHandler
- [ ] 软删除字段使用@TableLogic
- [ ] 所有字段都有完整的JavaDoc注释
- [ ] 类注释包含表名和业务功能说明
- [ ] 输出格式符合要求（无```标记）

现在请根据上述Schema生成实体类。
""";
```

**优化说明**:
1. ✅ 解决了Phase 2的UUID生成问题（显式说明需要UUIDv8TypeHandler）
2. ✅ 添加了完整的实体类示例
3. ✅ 增加了枚举类型处理方案
4. ✅ 添加了JSONB字段处理
5. ✅ 增加了软删除支持
6. ✅ 提供了详细的字段映射表

---

## 3. Coach Agent 提示词优化

### 3.1 代码修复提示词（FIX_PROMPT_TEMPLATE）

**当前问题**:
- 缺少常见错误的修复示例
- 未指导如何定位错误位置
- 缺少修复策略说明

**优化后的提示词**:

```java
private static final String FIX_PROMPT_TEMPLATE = """
你是一个专业的Java调试专家。请系统性地分析编译错误并修复代码。

## 原始代码
文件路径：%s
```java
%s
```

## 编译错误信息
```
%s
```

## 修复流程（Chain-of-Thought）

### Step 1: 错误定位
1. 识别错误行号和列号
2. 确定错误类型（符号未定义、类型不匹配、语法错误等）
3. 分析错误的根本原因

### Step 2: 修复策略选择

**常见错误类型及修复策略**：

#### 类型1：cannot find symbol
- **原因**：类、变量、方法未定义或import缺失
- **修复**：检查import语句，确认类名拼写，添加必要的import

#### 类型2：incompatible types
- **原因**：类型不匹配（如UUID vs String）
- **修复**：添加类型转换或修改变量类型

#### 类型3：package does not exist
- **原因**：依赖缺失或import路径错误
- **修复**：检查依赖配置，修正import路径

#### 类型4：method cannot be applied
- **原因**：方法参数类型或数量不匹配
- **修复**：调整参数类型或数量，或使用builder模式

#### 类型5：missing return statement
- **原因**：方法缺少返回语句或分支未覆盖所有情况
- **修复**：添加return语句或完善分支逻辑

#### 类型6：non-static cannot be referenced from static
- **原因**：在静态方法中引用非静态成员
- **修复**：将字段改为静态或通过实例访问

### Step 3: 应用修复

**修复原则**：
1. **最小化修改**：只修改必要的部分，保持原有逻辑
2. **保留注释**：不删除原有的JavaDoc和注释
3. **保持格式**：维持原有的代码格式和缩进
4. **类型安全**：确保修复后的代码类型正确

## 修复示例

### 示例1：UUID类型错误修复

**错误代码**：
```java
@TableId(value = "id", type = IdType.AUTO)
private UUID id;
```

**编译错误**：
```
incompatible types: UUID cannot be converted to String
```

**修复后代码**：
```java
@TableId(value = "id", type = IdType.ASSIGN_UUID)
@TableField(typeHandler = UUIDv8TypeHandler.class)
private UUID id;
```

**修复说明**：UUID主键需要使用ASSIGN_UUID而非AUTO，并添加UUIDv8TypeHandler。

---

### 示例2：import缺失修复

**错误代码**：
```java
package com.example.entity;

@Data
@Builder
public class User {
    private UUID id;
}
```

**编译错误**：
```
cannot find symbol: class Data
cannot find symbol: class Builder
cannot find symbol: class UUID
```

**修复后代码**：
```java
package com.example.entity;

import lombok.Builder;
import lombok.Data;
import java.util.UUID;

@Data
@Builder
public class User {
    private UUID id;
}
```

**修复说明**：添加缺失的Lombok和Java标准库import。

---

### 示例3：方法参数错误修复

**错误代码**：
```java
G3ArtifactEntity artifact = G3ArtifactEntity.builder()
    .jobId(jobId)
    .filePath(filePath)
    .build();
artifactMapper.insert(artifact);
```

**编译错误**：
```
java.sql.SQLException: null value in column "id" violates not-null constraint
```

**修复后代码**：
```java
G3ArtifactEntity artifact = G3ArtifactEntity.builder()
    .id(UUID.randomUUID())  // 显式生成UUID
    .jobId(jobId)
    .filePath(filePath)
    .build();
artifactMapper.insert(artifact);
```

**修复说明**：MyBatis-Plus的ASSIGN_UUID在PostgreSQL中不生效，需要显式生成UUID。

---

## 输出格式要求

**⚠️ 重要**：
1. 直接输出修复后的**完整Java代码**
2. **不要**使用```java标记包裹
3. **不要**添加任何解释文字或注释（除非是代码本身的注释）
4. **保持**原有的package语句和类结构
5. **保留**原有的JavaDoc注释

现在请按照上述流程修复代码。
""";
```

**优化说明**:
1. ✅ 添加了Chain-of-Thought修复流程
2. ✅ 提供了6种常见错误的修复策略
3. ✅ 增加了3个完整的修复示例（Few-Shot）
4. ✅ 特别说明了UUID问题修复（解决Phase 2遇到的问题）
5. ✅ 明确了修复原则和输出格式

---

## 实施建议

### 短期（立即实施）
1. ✅ **Architect Agent**：更新CONTRACT_PROMPT和SCHEMA_PROMPT
2. ✅ **Backend Coder Agent**：更新ENTITY_PROMPT（重点解决UUID问题）
3. ✅ **Coach Agent**：更新FIX_PROMPT（添加Few-Shot示例）

### 中期（1-2周）
1. 收集真实生成案例，持续优化提示词
2. 建立提示词版本管理机制
3. 添加提示词A/B测试框架

### 长期（1个月+）
1. 基于用户反馈和生成质量指标持续迭代
2. 探索使用更先进的提示词技术（如Tree-of-Thought）
3. 建立自动化提示词优化pipeline

---

## 评估指标

### 质量指标
- **一次性通过率**：生成代码首次编译通过率 >80%
- **修复成功率**：Coach Agent修复成功率 >85%
- **契约规范率**：OpenAPI契约通过Swagger验证 >95%

### 性能指标
- **生成耗时**：单次Agent调用 <10秒
- **Token使用量**：优化后减少20%（通过更精准的输出）

---

## 附录

### A. 提示词版本管理

建议使用Git管理提示词版本：
```java
/**
 * 提示词版本：v1.1.0
 * 更新日期：2025-12-27
 * 更新内容：添加UUID处理说明和Few-Shot示例
 */
private static final String CONTRACT_PROMPT_TEMPLATE = """
...
""";
```

### B. 提示词测试用例

为每个Agent建立标准测试用例集：
1. **简单需求**：待办事项管理系统
2. **中等需求**：电商订单管理系统
3. **复杂需求**：多租户SaaS平台

### C. 常见问题FAQ

**Q1: 为什么需要显式生成UUID？**
A: PostgreSQL的DEFAULT gen_random_uuid()在MyBatis-Plus插入时会被null覆盖，必须在Java层显式生成。

**Q2: 为什么不使用外键约束？**
A: 在沙箱环境中被引用表可能不存在，导致创建失败。建议仅创建索引，在生产环境再添加外键。

**Q3: 如何处理JSONB字段？**
A: 使用@TableField(typeHandler = JacksonTypeHandler.class)将JSONB映射为Java对象或Map。

---

**Made with ❤️ by G3 Engine Team**
