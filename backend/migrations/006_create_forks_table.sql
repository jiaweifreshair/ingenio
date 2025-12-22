-- 派生关系表迁移
-- 记录项目的派生（Fork）关系

-- 创建派生表
CREATE TABLE IF NOT EXISTS forks (
  -- 主键和标识
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

  -- 源项目和派生项目
  source_project_id UUID NOT NULL,
  forked_project_id UUID NOT NULL,

  -- 派生者信息
  forked_by_tenant_id UUID NOT NULL,
  forked_by_user_id UUID NOT NULL,

  -- 派生时的定制化修改（可选）
  customizations JSONB,
  -- 例如：{ "changedPages": ["home"], "addedFeatures": ["darkMode"] }

  -- 派生说明
  fork_description TEXT,

  -- 审计字段
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

  -- 元数据
  metadata JSONB NOT NULL DEFAULT '{}'::jsonb
);

-- 创建索引
CREATE INDEX idx_forks_source_project_id ON forks(source_project_id);
CREATE INDEX idx_forks_forked_project_id ON forks(forked_project_id);
CREATE INDEX idx_forks_forked_by_user_id ON forks(forked_by_user_id);
CREATE INDEX idx_forks_forked_by_tenant_id ON forks(forked_by_tenant_id);
CREATE INDEX idx_forks_created_at ON forks(created_at DESC);

-- GIN索引用于JSONB查询
CREATE INDEX idx_forks_customizations ON forks USING GIN (customizations);

-- 添加外键约束
ALTER TABLE forks
  ADD CONSTRAINT fk_forks_source_project
  FOREIGN KEY (source_project_id)
  REFERENCES projects(id)
  ON DELETE CASCADE;

ALTER TABLE forks
  ADD CONSTRAINT fk_forks_forked_project
  FOREIGN KEY (forked_project_id)
  REFERENCES projects(id)
  ON DELETE CASCADE;

ALTER TABLE forks
  ADD CONSTRAINT fk_forks_user
  FOREIGN KEY (forked_by_user_id)
  REFERENCES users(id)
  ON DELETE CASCADE;

-- 唯一约束：防止重复派生
CREATE UNIQUE INDEX idx_forks_unique_fork
  ON forks(source_project_id, forked_by_user_id);

-- 添加注释
COMMENT ON TABLE forks IS '派生关系表 - 记录项目的派生（Fork）关系';
COMMENT ON COLUMN forks.id IS '派生记录ID（UUID）';
COMMENT ON COLUMN forks.source_project_id IS '源项目ID';
COMMENT ON COLUMN forks.forked_project_id IS '派生项目ID';
COMMENT ON COLUMN forks.forked_by_tenant_id IS '派生者租户ID';
COMMENT ON COLUMN forks.forked_by_user_id IS '派生者用户ID';
COMMENT ON COLUMN forks.customizations IS '派生时的定制化修改（JSON）';
COMMENT ON COLUMN forks.fork_description IS '派生说明';
COMMENT ON COLUMN forks.created_at IS '派生时间';
COMMENT ON COLUMN forks.metadata IS '元数据（JSON）';
