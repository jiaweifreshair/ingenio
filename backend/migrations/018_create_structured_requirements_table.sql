-- 结构化需求表迁移
-- 存储AI分析后的结构化需求数据

-- 创建结构化需求表
CREATE TABLE IF NOT EXISTS structured_requirements (
  -- 主键和标识
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

  -- 租户隔离
  tenant_id UUID NOT NULL,

  -- 用户关联
  user_id UUID NOT NULL,

  -- 原始需求
  raw_requirement TEXT NOT NULL,

  -- AI分析结果 (JSONB格式)
  entities JSONB DEFAULT '[]'::jsonb,           -- 实体列表
  relationships JSONB DEFAULT '[]'::jsonb,      -- 关系列表
  operations JSONB DEFAULT '[]'::jsonb,         -- 操作列表
  constraints JSONB DEFAULT '[]'::jsonb,        -- 约束条件列表

  -- AI模型信息
  ai_model VARCHAR(50),                         -- 使用的AI模型名称
  confidence_score DECIMAL(3,2),                -- 置信度分数 (0.00-1.00)

  -- 审计字段
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

  -- 元数据
  metadata JSONB NOT NULL DEFAULT '{}'::jsonb,

  -- 外键约束
  CONSTRAINT fk_structured_requirements_user
    FOREIGN KEY (user_id)
    REFERENCES users(id)
    ON DELETE CASCADE
);

-- 创建索引
CREATE INDEX idx_structured_requirements_tenant_id ON structured_requirements(tenant_id);
CREATE INDEX idx_structured_requirements_user_id ON structured_requirements(user_id);
CREATE INDEX idx_structured_requirements_created_at ON structured_requirements(created_at DESC);
CREATE INDEX idx_structured_requirements_ai_model ON structured_requirements(ai_model);

-- 创建JSONB字段的GIN索引 (用于快速查询)
CREATE INDEX idx_structured_requirements_entities ON structured_requirements USING GIN (entities);
CREATE INDEX idx_structured_requirements_relationships ON structured_requirements USING GIN (relationships);
CREATE INDEX idx_structured_requirements_operations ON structured_requirements USING GIN (operations);
CREATE INDEX idx_structured_requirements_constraints ON structured_requirements USING GIN (constraints);
CREATE INDEX idx_structured_requirements_metadata ON structured_requirements USING GIN (metadata);

-- 创建更新时间触发器
CREATE TRIGGER trigger_update_structured_requirements_updated_at
  BEFORE UPDATE ON structured_requirements
  FOR EACH ROW
  EXECUTE FUNCTION update_updated_at_column();

-- 添加表注释
COMMENT ON TABLE structured_requirements IS 'AI分析后的结构化需求存储表';
COMMENT ON COLUMN structured_requirements.id IS '唯一标识符';
COMMENT ON COLUMN structured_requirements.tenant_id IS '租户ID，用于多租户隔离';
COMMENT ON COLUMN structured_requirements.user_id IS '创建此需求的用户ID';
COMMENT ON COLUMN structured_requirements.raw_requirement IS '用户输入的原始需求描述';
COMMENT ON COLUMN structured_requirements.entities IS 'AI识别的实体列表 (JSONB格式)';
COMMENT ON COLUMN structured_requirements.relationships IS 'AI识别的实体关系列表 (JSONB格式)';
COMMENT ON COLUMN structured_requirements.operations IS 'AI识别的操作列表 (JSONB格式)';
COMMENT ON COLUMN structured_requirements.constraints IS 'AI识别的约束条件列表 (JSONB格式)';
COMMENT ON COLUMN structured_requirements.ai_model IS '执行分析的AI模型名称 (如: qwen-max)';
COMMENT ON COLUMN structured_requirements.confidence_score IS 'AI分析结果的置信度 (0.00-1.00)';
COMMENT ON COLUMN structured_requirements.created_at IS '创建时间';
COMMENT ON COLUMN structured_requirements.updated_at IS '最后更新时间';
COMMENT ON COLUMN structured_requirements.metadata IS '扩展元数据 (JSONB格式)';
