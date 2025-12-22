-- 生成版本表迁移
-- TimeMachine版本快照功能

CREATE TABLE IF NOT EXISTS generation_versions (
  -- 主键
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

  -- 租户隔离
  tenant_id UUID,

  -- 关联用户
  user_id UUID,

  -- 关联生成任务
  task_id UUID NOT NULL,

  -- 版本信息
  version_number INTEGER NOT NULL,
  version_type VARCHAR(50) NOT NULL,
  version_tag VARCHAR(50),
  description TEXT,

  -- 变更和快照（JSONB）
  changes JSONB DEFAULT '{}'::jsonb,
  snapshot JSONB NOT NULL DEFAULT '{}'::jsonb,

  -- 版本树
  parent_version_id UUID,

  -- 部署信息
  is_deployed BOOLEAN DEFAULT FALSE,
  deployed_at TIMESTAMP,

  -- 审计字段
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

  -- 约束
  CONSTRAINT chk_version_type
    CHECK (version_type IN ('plan', 'schema', 'code', 'validation_failed', 'validation_success', 'fix', 'rollback', 'final'))
);

-- 创建索引
CREATE INDEX idx_generation_versions_tenant ON generation_versions(tenant_id);
CREATE INDEX idx_generation_versions_user ON generation_versions(user_id);
CREATE INDEX idx_generation_versions_task ON generation_versions(task_id);
CREATE INDEX idx_generation_versions_parent ON generation_versions(parent_version_id);
CREATE INDEX idx_generation_versions_type ON generation_versions(version_type);
CREATE INDEX idx_generation_versions_created ON generation_versions(created_at DESC);

-- GIN索引（JSONB字段）
CREATE INDEX idx_generation_versions_changes ON generation_versions USING GIN (changes jsonb_path_ops);
CREATE INDEX idx_generation_versions_snapshot ON generation_versions USING GIN (snapshot jsonb_path_ops);

-- 添加外键约束
ALTER TABLE generation_versions
  ADD CONSTRAINT fk_generation_versions_task
  FOREIGN KEY (task_id)
  REFERENCES generation_tasks(id)
  ON DELETE CASCADE;

-- 添加注释
COMMENT ON TABLE generation_versions IS '生成版本表 - TimeMachine版本快照功能';
COMMENT ON COLUMN generation_versions.id IS '版本ID（UUID）';
COMMENT ON COLUMN generation_versions.tenant_id IS '租户ID';
COMMENT ON COLUMN generation_versions.user_id IS '用户ID';
COMMENT ON COLUMN generation_versions.task_id IS '关联的生成任务ID';
COMMENT ON COLUMN generation_versions.version_number IS '版本号（自增，从1开始）';
COMMENT ON COLUMN generation_versions.version_type IS '版本类型：plan/schema/code/validation_failed/validation_success/fix/rollback/final';
COMMENT ON COLUMN generation_versions.version_tag IS '版本标签（如v1.0.0）';
COMMENT ON COLUMN generation_versions.description IS '版本描述';
COMMENT ON COLUMN generation_versions.changes IS '变更记录（JSON格式）';
COMMENT ON COLUMN generation_versions.snapshot IS '完整快照（JSON格式）';
COMMENT ON COLUMN generation_versions.parent_version_id IS '父版本ID（用于构建版本树）';
COMMENT ON COLUMN generation_versions.is_deployed IS '是否已部署';
COMMENT ON COLUMN generation_versions.deployed_at IS '部署时间';
COMMENT ON COLUMN generation_versions.created_at IS '创建时间';
