-- V017: 创建NL2Backend核心表
-- 用于存储自然语言生成后端服务的任务和产物

-- 1. 生成的数据库Schema
CREATE TABLE IF NOT EXISTS generated_schemas (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID,
    user_id UUID,
    task_id UUID NOT NULL,                           -- 关联的生成任务ID
    schema_name VARCHAR(100) NOT NULL,                -- Schema名称（如blog_schema）
    description TEXT,                                 -- Schema描述
    tables JSONB NOT NULL,                            -- 表结构定义（JSON数组）
    ddl_sql TEXT NOT NULL,                            -- DDL SQL脚本
    version INTEGER NOT NULL DEFAULT 1,               -- Schema版本号
    status VARCHAR(50) NOT NULL DEFAULT 'draft',      -- draft/approved/deployed/deprecated
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_generated_schemas_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id) ON DELETE CASCADE,
    CONSTRAINT fk_generated_schemas_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE SET NULL,
    CONSTRAINT fk_generated_schemas_task FOREIGN KEY (task_id) REFERENCES generation_tasks(id) ON DELETE CASCADE
);

-- 索引
CREATE INDEX idx_generated_schemas_tenant ON generated_schemas(tenant_id);
CREATE INDEX idx_generated_schemas_user ON generated_schemas(user_id);
CREATE INDEX idx_generated_schemas_task ON generated_schemas(task_id);
CREATE INDEX idx_generated_schemas_status ON generated_schemas(status);

-- 触发器：自动更新updated_at
CREATE TRIGGER update_generated_schemas_updated_at
    BEFORE UPDATE ON generated_schemas
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

COMMENT ON TABLE generated_schemas IS '生成的数据库Schema';
COMMENT ON COLUMN generated_schemas.tables IS '表结构定义（JSON数组，包含表名、字段、索引等）';
COMMENT ON COLUMN generated_schemas.ddl_sql IS '完整的DDL SQL脚本';


-- 2. 生成的API接口
CREATE TABLE IF NOT EXISTS generated_apis (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID,
    user_id UUID,
    task_id UUID NOT NULL,                           -- 关联的生成任务ID
    schema_id UUID,                                   -- 关联的Schema ID
    api_name VARCHAR(100) NOT NULL,                   -- API名称（如BlogAPI）
    description TEXT,                                 -- API描述
    openapi_spec JSONB NOT NULL,                      -- OpenAPI 3.0规格（完整JSON）
    endpoints JSONB NOT NULL,                         -- API端点列表（简化索引用）
    version VARCHAR(20) NOT NULL DEFAULT '1.0.0',     -- API版本号
    status VARCHAR(50) NOT NULL DEFAULT 'draft',      -- draft/approved/deployed/deprecated
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_generated_apis_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id) ON DELETE CASCADE,
    CONSTRAINT fk_generated_apis_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE SET NULL,
    CONSTRAINT fk_generated_apis_task FOREIGN KEY (task_id) REFERENCES generation_tasks(id) ON DELETE CASCADE,
    CONSTRAINT fk_generated_apis_schema FOREIGN KEY (schema_id) REFERENCES generated_schemas(id) ON DELETE SET NULL
);

-- 索引
CREATE INDEX idx_generated_apis_tenant ON generated_apis(tenant_id);
CREATE INDEX idx_generated_apis_user ON generated_apis(user_id);
CREATE INDEX idx_generated_apis_task ON generated_apis(task_id);
CREATE INDEX idx_generated_apis_schema ON generated_apis(schema_id);
CREATE INDEX idx_generated_apis_status ON generated_apis(status);

-- 触发器
CREATE TRIGGER update_generated_apis_updated_at
    BEFORE UPDATE ON generated_apis
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

COMMENT ON TABLE generated_apis IS '生成的API接口规格';
COMMENT ON COLUMN generated_apis.openapi_spec IS 'OpenAPI 3.0完整规格（JSON格式）';
COMMENT ON COLUMN generated_apis.endpoints IS 'API端点列表，格式：[{method, path, summary}]';


-- 3. 生成的代码文件
CREATE TABLE IF NOT EXISTS generated_code_files (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID,
    user_id UUID,
    task_id UUID NOT NULL,                           -- 关联的生成任务ID
    api_id UUID,                                      -- 关联的API ID
    file_type VARCHAR(50) NOT NULL,                   -- entity/mapper/service/controller/test/config
    file_path VARCHAR(500) NOT NULL,                  -- 文件路径（相对路径）
    file_name VARCHAR(200) NOT NULL,                  -- 文件名
    content TEXT NOT NULL,                            -- 文件内容（代码）
    language VARCHAR(20) NOT NULL DEFAULT 'java',     -- 编程语言
    framework VARCHAR(50) DEFAULT 'spring-boot',      -- 框架
    checksum VARCHAR(64),                             -- 文件内容SHA256校验和
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_generated_code_files_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id) ON DELETE CASCADE,
    CONSTRAINT fk_generated_code_files_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE SET NULL,
    CONSTRAINT fk_generated_code_files_task FOREIGN KEY (task_id) REFERENCES generation_tasks(id) ON DELETE CASCADE,
    CONSTRAINT fk_generated_code_files_api FOREIGN KEY (api_id) REFERENCES generated_apis(id) ON DELETE CASCADE
);

-- 索引
CREATE INDEX idx_generated_code_files_tenant ON generated_code_files(tenant_id);
CREATE INDEX idx_generated_code_files_user ON generated_code_files(user_id);
CREATE INDEX idx_generated_code_files_task ON generated_code_files(task_id);
CREATE INDEX idx_generated_code_files_api ON generated_code_files(api_id);
CREATE INDEX idx_generated_code_files_type ON generated_code_files(file_type);

-- 触发器
CREATE TRIGGER update_generated_code_files_updated_at
    BEFORE UPDATE ON generated_code_files
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

COMMENT ON TABLE generated_code_files IS '生成的代码文件';
COMMENT ON COLUMN generated_code_files.file_type IS '文件类型：entity/mapper/service/controller/test/config等';
COMMENT ON COLUMN generated_code_files.checksum IS 'SHA256校验和，用于检测文件变更';


-- 4. 生成版本管理
CREATE TABLE IF NOT EXISTS generation_versions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID,
    user_id UUID,
    task_id UUID NOT NULL,                           -- 关联的生成任务ID
    version_number INTEGER NOT NULL,                  -- 版本号（自增）
    version_tag VARCHAR(50),                          -- 版本标签（如v1.0.0）
    description TEXT,                                 -- 版本描述
    changes JSONB,                                    -- 变更记录（与上一版本对比）
    snapshot JSONB NOT NULL,                          -- 完整快照（包含schema/api/code）
    parent_version_id UUID,                           -- 父版本ID（用于版本树）
    is_deployed BOOLEAN DEFAULT FALSE,                -- 是否已部署
    deployed_at TIMESTAMP,                            -- 部署时间
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_generation_versions_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id) ON DELETE CASCADE,
    CONSTRAINT fk_generation_versions_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE SET NULL,
    CONSTRAINT fk_generation_versions_task FOREIGN KEY (task_id) REFERENCES generation_tasks(id) ON DELETE CASCADE,
    CONSTRAINT fk_generation_versions_parent FOREIGN KEY (parent_version_id) REFERENCES generation_versions(id) ON DELETE SET NULL
);

-- 索引
CREATE INDEX idx_generation_versions_tenant ON generation_versions(tenant_id);
CREATE INDEX idx_generation_versions_user ON generation_versions(user_id);
CREATE INDEX idx_generation_versions_task ON generation_versions(task_id);
CREATE INDEX idx_generation_versions_deployed ON generation_versions(is_deployed);
CREATE UNIQUE INDEX idx_generation_versions_task_number ON generation_versions(task_id, version_number);

COMMENT ON TABLE generation_versions IS '生成版本管理（支持回滚）';
COMMENT ON COLUMN generation_versions.snapshot IS '完整快照（包含schema、api、code的完整数据）';
COMMENT ON COLUMN generation_versions.changes IS '变更记录（与父版本对比的差异）';


-- 5. 结构化需求表（AI分析结果）
CREATE TABLE IF NOT EXISTS structured_requirements (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID,
    user_id UUID,
    task_id UUID NOT NULL,                           -- 关联的生成任务ID
    raw_requirement TEXT NOT NULL,                    -- 原始自然语言需求
    entities JSONB NOT NULL,                          -- 提取的实体列表
    relationships JSONB,                              -- 实体间关系
    operations JSONB,                                 -- 业务操作列表
    constraints JSONB,                                -- 约束条件
    ai_model VARCHAR(50),                             -- 使用的AI模型（qwen/gpt-4等）
    confidence_score DECIMAL(5,2),                    -- 置信度分数（0-100）
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_structured_requirements_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id) ON DELETE CASCADE,
    CONSTRAINT fk_structured_requirements_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE SET NULL,
    CONSTRAINT fk_structured_requirements_task FOREIGN KEY (task_id) REFERENCES generation_tasks(id) ON DELETE CASCADE
);

-- 索引
CREATE INDEX idx_structured_requirements_tenant ON structured_requirements(tenant_id);
CREATE INDEX idx_structured_requirements_user ON structured_requirements(user_id);
CREATE INDEX idx_structured_requirements_task ON structured_requirements(task_id);

-- 触发器
CREATE TRIGGER update_structured_requirements_updated_at
    BEFORE UPDATE ON structured_requirements
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

COMMENT ON TABLE structured_requirements IS '结构化需求（AI分析结果）';
COMMENT ON COLUMN structured_requirements.entities IS '实体列表，格式：[{name, attributes, description}]';
COMMENT ON COLUMN structured_requirements.relationships IS '关系列表，格式：[{from, to, type, cardinality}]';
COMMENT ON COLUMN structured_requirements.operations IS '操作列表，格式：[{name, type, params, description}]';
