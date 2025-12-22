-- AppSpec规范表迁移
-- 存储AI生成的应用规范（AppSpec JSON）

-- 创建AppSpec表
CREATE TABLE IF NOT EXISTS app_specs (
  -- 主键和标识
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

  -- 租户隔离
  tenant_id UUID NOT NULL,

  -- 创建者信息
  created_by_user_id UUID NOT NULL,

  -- AppSpec内容（完整JSON）
  spec_content JSONB NOT NULL,

  -- 版本控制
  version INTEGER NOT NULL DEFAULT 1,
  parent_version_id UUID,

  -- 状态管理
  status VARCHAR(50) NOT NULL DEFAULT 'draft',
  -- draft: 草稿, validated: 已验证, generated: 已生成代码, published: 已发布

  -- 质量评分（由ValidateAgent生成）
  quality_score INTEGER,
  -- 0-100分，来自AI的语义验证和结构验证

  -- 审计字段
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

  -- 元数据
  metadata JSONB NOT NULL DEFAULT '{}'::jsonb
);

-- 创建索引
CREATE INDEX idx_app_specs_tenant_id ON app_specs(tenant_id);
CREATE INDEX idx_app_specs_created_by ON app_specs(created_by_user_id);
CREATE INDEX idx_app_specs_status ON app_specs(status);
CREATE INDEX idx_app_specs_version ON app_specs(version);
CREATE INDEX idx_app_specs_parent_version ON app_specs(parent_version_id);

-- GIN索引用于JSONB查询
CREATE INDEX idx_app_specs_spec_content ON app_specs USING GIN (spec_content);

-- 创建更新时间触发器
CREATE TRIGGER update_app_specs_updated_at
  BEFORE UPDATE ON app_specs
  FOR EACH ROW
  EXECUTE FUNCTION update_updated_at_column();

-- 添加外键约束
ALTER TABLE app_specs
  ADD CONSTRAINT fk_app_specs_user
  FOREIGN KEY (created_by_user_id)
  REFERENCES users(id)
  ON DELETE CASCADE;

ALTER TABLE app_specs
  ADD CONSTRAINT fk_app_specs_parent_version
  FOREIGN KEY (parent_version_id)
  REFERENCES app_specs(id)
  ON DELETE SET NULL;

-- 添加约束
ALTER TABLE app_specs
  ADD CONSTRAINT chk_app_specs_status
  CHECK (status IN ('draft', 'validated', 'generated', 'published'));

ALTER TABLE app_specs
  ADD CONSTRAINT chk_app_specs_quality_score
  CHECK (quality_score IS NULL OR (quality_score >= 0 AND quality_score <= 100));

-- 添加注释
COMMENT ON TABLE app_specs IS 'AppSpec规范表 - 存储AI生成的应用规范';
COMMENT ON COLUMN app_specs.id IS 'AppSpec ID（UUID）';
COMMENT ON COLUMN app_specs.tenant_id IS '租户ID - 用于租户隔离';
COMMENT ON COLUMN app_specs.created_by_user_id IS '创建者用户ID';
COMMENT ON COLUMN app_specs.spec_content IS 'AppSpec完整JSON内容（包含pages/dataModels/flows/permissions）';
COMMENT ON COLUMN app_specs.version IS '版本号（从1开始递增）';
COMMENT ON COLUMN app_specs.parent_version_id IS '父版本ID（用于版本链追踪）';
COMMENT ON COLUMN app_specs.status IS '状态：draft/validated/generated/published';
COMMENT ON COLUMN app_specs.quality_score IS '质量评分（0-100）- 由ValidateAgent生成';
COMMENT ON COLUMN app_specs.created_at IS '创建时间';
COMMENT ON COLUMN app_specs.updated_at IS '更新时间';
COMMENT ON COLUMN app_specs.metadata IS '元数据（JSON）';
