-- MCP动态表记录表迁移
-- 支持多数据源动态表创建

CREATE TABLE IF NOT EXISTS mcp_dynamic_tables (
  -- 主键
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  
  -- 租户隔离
  tenant_id UUID NOT NULL,
  
  -- 表基本信息
  table_name VARCHAR(100) NOT NULL,
  display_name VARCHAR(200),
  description TEXT,
  
  -- 表结构定义（JSON Schema）
  schema_definition JSONB NOT NULL,
  
  -- 数据源配置
  datasource_type VARCHAR(50) NOT NULL,
  datasource_config JSONB NOT NULL,
  
  -- 同步配置
  sync_strategy VARCHAR(20) NOT NULL DEFAULT 'manual',
  sync_interval INTEGER,
  last_synced_at TIMESTAMP,
  
  -- 状态
  status VARCHAR(20) NOT NULL DEFAULT 'active',
  error_message TEXT,
  
  -- 审计字段
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  deleted INTEGER DEFAULT 0,
  
  -- 元数据
  metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
  
  -- 约束
  CONSTRAINT chk_mcp_sync_strategy 
    CHECK (sync_strategy IN ('manual', 'realtime', 'scheduled')),
  CONSTRAINT chk_mcp_status 
    CHECK (status IN ('active', 'paused', 'error')),
  CONSTRAINT uk_mcp_tenant_table UNIQUE (tenant_id, table_name)
);

-- 创建索引
CREATE INDEX idx_mcp_dynamic_tables_tenant ON mcp_dynamic_tables(tenant_id);
CREATE INDEX idx_mcp_dynamic_tables_datasource ON mcp_dynamic_tables(datasource_type);
CREATE INDEX idx_mcp_dynamic_tables_status ON mcp_dynamic_tables(status);
CREATE INDEX idx_mcp_dynamic_tables_name ON mcp_dynamic_tables(table_name);

-- GIN索引
CREATE INDEX idx_mcp_schema_def ON mcp_dynamic_tables USING GIN (schema_definition);
CREATE INDEX idx_mcp_datasource_config ON mcp_dynamic_tables USING GIN (datasource_config);

-- 创建更新时间触发器
CREATE TRIGGER update_mcp_dynamic_tables_updated_at
  BEFORE UPDATE ON mcp_dynamic_tables
  FOR EACH ROW
  EXECUTE FUNCTION update_updated_at_column();

-- 添加注释
COMMENT ON TABLE mcp_dynamic_tables IS 'MCP动态表记录 - 支持多数据源动态表创建';
COMMENT ON COLUMN mcp_dynamic_tables.id IS '动态表记录ID（UUID）';
COMMENT ON COLUMN mcp_dynamic_tables.tenant_id IS '租户ID';
COMMENT ON COLUMN mcp_dynamic_tables.table_name IS '表名';
COMMENT ON COLUMN mcp_dynamic_tables.display_name IS '显示名称';
COMMENT ON COLUMN mcp_dynamic_tables.description IS '描述';
COMMENT ON COLUMN mcp_dynamic_tables.schema_definition IS '表结构定义（JSON Schema）';
COMMENT ON COLUMN mcp_dynamic_tables.datasource_type IS '数据源类型：postgresql/mysql/api/graphql';
COMMENT ON COLUMN mcp_dynamic_tables.datasource_config IS '数据源配置（JSON）';
COMMENT ON COLUMN mcp_dynamic_tables.sync_strategy IS '同步策略：manual/realtime/scheduled';
COMMENT ON COLUMN mcp_dynamic_tables.sync_interval IS '同步间隔（秒）';
COMMENT ON COLUMN mcp_dynamic_tables.last_synced_at IS '最后同步时间';
COMMENT ON COLUMN mcp_dynamic_tables.status IS '状态：active/paused/error';
COMMENT ON COLUMN mcp_dynamic_tables.error_message IS '错误信息';
