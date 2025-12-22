-- MCP认证配置表迁移
-- 存储多数据源的认证信息

CREATE TABLE IF NOT EXISTS mcp_auth_configs (
  -- 主键
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  
  -- 租户隔离
  tenant_id UUID NOT NULL,
  
  -- 认证配置名称
  config_name VARCHAR(100) NOT NULL,
  display_name VARCHAR(200),
  
  -- 认证类型
  auth_type VARCHAR(50) NOT NULL,
  
  -- 认证配置（需加密存储）
  auth_config JSONB NOT NULL,
  
  -- 状态
  status VARCHAR(20) NOT NULL DEFAULT 'active',
  expires_at TIMESTAMP,
  
  -- 审计字段
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  deleted INTEGER DEFAULT 0,
  
  -- 元数据
  metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
  
  -- 约束
  CONSTRAINT chk_mcp_auth_type 
    CHECK (auth_type IN ('oauth2', 'api_key', 'basic', 'jwt')),
  CONSTRAINT chk_mcp_auth_status 
    CHECK (status IN ('active', 'expired', 'revoked')),
  CONSTRAINT uk_mcp_tenant_config UNIQUE (tenant_id, config_name)
);

-- 创建索引
CREATE INDEX idx_mcp_auth_configs_tenant ON mcp_auth_configs(tenant_id);
CREATE INDEX idx_mcp_auth_configs_type ON mcp_auth_configs(auth_type);
CREATE INDEX idx_mcp_auth_configs_status ON mcp_auth_configs(status);
CREATE INDEX idx_mcp_auth_configs_name ON mcp_auth_configs(config_name);
CREATE INDEX idx_mcp_auth_configs_expires ON mcp_auth_configs(expires_at);

-- 创建更新时间触发器
CREATE TRIGGER update_mcp_auth_configs_updated_at
  BEFORE UPDATE ON mcp_auth_configs
  FOR EACH ROW
  EXECUTE FUNCTION update_updated_at_column();

-- 添加注释
COMMENT ON TABLE mcp_auth_configs IS 'MCP认证配置表 - 存储多数据源的认证信息';
COMMENT ON COLUMN mcp_auth_configs.id IS '认证配置ID（UUID）';
COMMENT ON COLUMN mcp_auth_configs.tenant_id IS '租户ID';
COMMENT ON COLUMN mcp_auth_configs.config_name IS '配置名称';
COMMENT ON COLUMN mcp_auth_configs.display_name IS '显示名称';
COMMENT ON COLUMN mcp_auth_configs.auth_type IS '认证类型：oauth2/api_key/basic/jwt';
COMMENT ON COLUMN mcp_auth_configs.auth_config IS '认证配置（JSON，需加密）';
COMMENT ON COLUMN mcp_auth_configs.status IS '状态：active/expired/revoked';
COMMENT ON COLUMN mcp_auth_configs.expires_at IS '过期时间';
