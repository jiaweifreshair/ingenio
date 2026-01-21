-- V034: 创建项目能力配置表
-- G3引擎JeecgBoot能力集成 - 阶段2
-- 用途：存储用户为项目配置的JeecgBoot能力参数

-- 1. 项目能力配置表
CREATE TABLE IF NOT EXISTS project_capability_configs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    -- 关联信息
    project_id UUID NOT NULL,                            -- 所属项目ID
    capability_id UUID NOT NULL,                         -- 能力ID
    capability_code VARCHAR(50) NOT NULL,                -- 能力代码（冗余）

    -- 配置数据
    config_values JSONB NOT NULL,                        -- 配置值（敏感字段加密存储）

    -- 状态管理
    status VARCHAR(20) DEFAULT 'pending',                -- 状态：pending/validated/failed
    validation_error TEXT,                               -- 验证错误信息
    validated_at TIMESTAMP,                              -- 最后验证时间

    -- 审计字段
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    -- 约束
    CONSTRAINT fk_project_capability_configs_project
        FOREIGN KEY (project_id) REFERENCES projects(id) ON DELETE CASCADE,
    CONSTRAINT fk_project_capability_configs_capability
        FOREIGN KEY (capability_id) REFERENCES jeecg_capabilities(id),
    CONSTRAINT uk_project_capability UNIQUE (project_id, capability_code),
    CONSTRAINT chk_status CHECK (status IN ('pending', 'validated', 'failed'))
);

-- 2. 创建索引
CREATE INDEX idx_project_capability_configs_project
    ON project_capability_configs(project_id);

CREATE INDEX idx_project_capability_configs_capability
    ON project_capability_configs(capability_code);

CREATE INDEX idx_project_capability_configs_status
    ON project_capability_configs(status);

-- 3. 创建触发器（自动更新 updated_at）
CREATE TRIGGER update_project_capability_configs_updated_at
    BEFORE UPDATE ON project_capability_configs
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

-- 4. 添加表和列注释
COMMENT ON TABLE project_capability_configs IS '项目能力配置 - 存储用户为项目配置的JeecgBoot能力参数';

COMMENT ON COLUMN project_capability_configs.config_values IS
    '配置值（JSONB）- 敏感字段加密存储，格式：{"appId": "xxx", "privateKey": "ENC:AES256:..."}';

COMMENT ON COLUMN project_capability_configs.status IS
    '配置状态 - pending(待验证)/validated(已验证)/failed(验证失败)';
