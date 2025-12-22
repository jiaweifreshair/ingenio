-- AppSpec版本表迁移
-- 记录每次AI生成的完整推理过程（用于"时光机调试"功能）

-- 创建AppSpec版本表
CREATE TABLE IF NOT EXISTS app_spec_versions (
  -- 主键和标识
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

  -- 关联AppSpec
  app_spec_id UUID NOT NULL,

  -- 版本号
  version_number INTEGER NOT NULL,

  -- 快照内容（完整AppSpec JSON）
  snapshot_content JSONB NOT NULL,

  -- AI推理过程元数据（关键：时光机调试的核心数据）
  generation_metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
  -- 包含：
  -- {
  --   "planAgent": { "understanding": "...", "decisions": [...], "alternatives": [...] },
  --   "executeAgent": { "reasoning": "...", "codeChoices": [...], "tradeoffs": [...] },
  --   "validateAgent": { "checks": [...], "warnings": [...], "suggestions": [...] },
  --   "userInput": "原始用户需求",
  --   "timestamp": "2024-01-01T00:00:00Z",
  --   "modelVersion": "deepseek-chat-v1",
  --   "confidence": 0.95
  -- }

  -- 变更描述
  change_description TEXT,

  -- 变更类型
  change_type VARCHAR(50) NOT NULL DEFAULT 'user_edit',
  -- user_edit: 用户主动编辑
  -- ai_generation: AI生成
  -- rollback: 版本回滚
  -- fork: 派生修改

  -- 审计字段
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  created_by_user_id UUID NOT NULL,

  -- 元数据
  metadata JSONB NOT NULL DEFAULT '{}'::jsonb
);

-- 创建索引
CREATE INDEX idx_app_spec_versions_app_spec_id ON app_spec_versions(app_spec_id);
CREATE INDEX idx_app_spec_versions_version_number ON app_spec_versions(version_number);
CREATE INDEX idx_app_spec_versions_created_at ON app_spec_versions(created_at DESC);
CREATE INDEX idx_app_spec_versions_created_by ON app_spec_versions(created_by_user_id);
CREATE INDEX idx_app_spec_versions_change_type ON app_spec_versions(change_type);

-- GIN索引用于JSONB查询（时光机调试功能的关键索引）
CREATE INDEX idx_app_spec_versions_generation_metadata ON app_spec_versions USING GIN (generation_metadata);
CREATE INDEX idx_app_spec_versions_snapshot_content ON app_spec_versions USING GIN (snapshot_content);

-- 添加外键约束
ALTER TABLE app_spec_versions
  ADD CONSTRAINT fk_app_spec_versions_app_spec
  FOREIGN KEY (app_spec_id)
  REFERENCES app_specs(id)
  ON DELETE CASCADE;

ALTER TABLE app_spec_versions
  ADD CONSTRAINT fk_app_spec_versions_user
  FOREIGN KEY (created_by_user_id)
  REFERENCES users(id)
  ON DELETE CASCADE;

-- 添加约束
ALTER TABLE app_spec_versions
  ADD CONSTRAINT chk_app_spec_versions_change_type
  CHECK (change_type IN ('user_edit', 'ai_generation', 'rollback', 'fork'));

-- 唯一约束：每个AppSpec的版本号唯一
CREATE UNIQUE INDEX idx_app_spec_versions_unique_version
  ON app_spec_versions(app_spec_id, version_number);

-- 添加注释
COMMENT ON TABLE app_spec_versions IS 'AppSpec版本表 - 记录每次AI生成的完整推理过程（时光机调试核心）';
COMMENT ON COLUMN app_spec_versions.id IS '版本ID（UUID）';
COMMENT ON COLUMN app_spec_versions.app_spec_id IS '关联的AppSpec ID';
COMMENT ON COLUMN app_spec_versions.version_number IS '版本号（从1开始递增）';
COMMENT ON COLUMN app_spec_versions.snapshot_content IS 'AppSpec快照（完整JSON）';
COMMENT ON COLUMN app_spec_versions.generation_metadata IS 'AI推理过程元数据（包含PlanAgent/ExecuteAgent/ValidateAgent的决策过程）';
COMMENT ON COLUMN app_spec_versions.change_description IS '变更描述（用户可编辑）';
COMMENT ON COLUMN app_spec_versions.change_type IS '变更类型：user_edit/ai_generation/rollback/fork';
COMMENT ON COLUMN app_spec_versions.created_at IS '创建时间';
COMMENT ON COLUMN app_spec_versions.created_by_user_id IS '创建者用户ID';
COMMENT ON COLUMN app_spec_versions.metadata IS '元数据（JSON）';
