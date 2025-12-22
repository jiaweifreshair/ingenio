-- 社交互动表迁移
-- 记录点赞、收藏、评论等社交互动

-- 创建社交互动表
CREATE TABLE IF NOT EXISTS social_interactions (
  -- 主键和标识
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

  -- 租户和用户
  tenant_id UUID NOT NULL,
  user_id UUID NOT NULL,

  -- 目标项目
  project_id UUID NOT NULL,

  -- 互动类型
  interaction_type VARCHAR(20) NOT NULL,
  -- like: 点赞
  -- favorite: 收藏
  -- comment: 评论
  -- view: 浏览（用于浏览记录）

  -- 评论内容（仅当interaction_type为comment时）
  comment_content TEXT,

  -- 审计字段
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

  -- 元数据
  metadata JSONB NOT NULL DEFAULT '{}'::jsonb
);

-- 创建索引
CREATE INDEX idx_social_interactions_tenant_id ON social_interactions(tenant_id);
CREATE INDEX idx_social_interactions_user_id ON social_interactions(user_id);
CREATE INDEX idx_social_interactions_project_id ON social_interactions(project_id);
CREATE INDEX idx_social_interactions_type ON social_interactions(interaction_type);
CREATE INDEX idx_social_interactions_created_at ON social_interactions(created_at DESC);

-- 复合索引：用户+项目+类型（用于查询用户是否点赞/收藏某项目）
CREATE INDEX idx_social_interactions_user_project_type
  ON social_interactions(user_id, project_id, interaction_type);

-- GIN索引用于JSONB查询
CREATE INDEX idx_social_interactions_metadata ON social_interactions USING GIN (metadata);

-- 全文搜索索引（评论内容）
CREATE INDEX idx_social_interactions_comment_search
  ON social_interactions USING GIN (to_tsvector('simple', comment_content));

-- 添加外键约束
ALTER TABLE social_interactions
  ADD CONSTRAINT fk_social_interactions_user
  FOREIGN KEY (user_id)
  REFERENCES users(id)
  ON DELETE CASCADE;

ALTER TABLE social_interactions
  ADD CONSTRAINT fk_social_interactions_project
  FOREIGN KEY (project_id)
  REFERENCES projects(id)
  ON DELETE CASCADE;

-- 添加约束
ALTER TABLE social_interactions
  ADD CONSTRAINT chk_social_interactions_type
  CHECK (interaction_type IN ('like', 'favorite', 'comment', 'view'));

-- 添加约束：评论类型必须有评论内容
ALTER TABLE social_interactions
  ADD CONSTRAINT chk_social_interactions_comment_content
  CHECK (
    (interaction_type = 'comment' AND comment_content IS NOT NULL AND LENGTH(TRIM(comment_content)) > 0)
    OR
    (interaction_type != 'comment')
  );

-- 唯一约束：防止重复点赞/收藏（不包括评论和浏览）
CREATE UNIQUE INDEX idx_social_interactions_unique_like_favorite
  ON social_interactions(user_id, project_id, interaction_type)
  WHERE interaction_type IN ('like', 'favorite');

-- 添加注释
COMMENT ON TABLE social_interactions IS '社交互动表 - 记录点赞、收藏、评论等社交互动';
COMMENT ON COLUMN social_interactions.id IS '互动记录ID（UUID）';
COMMENT ON COLUMN social_interactions.tenant_id IS '租户ID';
COMMENT ON COLUMN social_interactions.user_id IS '用户ID';
COMMENT ON COLUMN social_interactions.project_id IS '目标项目ID';
COMMENT ON COLUMN social_interactions.interaction_type IS '互动类型：like/favorite/comment/view';
COMMENT ON COLUMN social_interactions.comment_content IS '评论内容（仅评论类型）';
COMMENT ON COLUMN social_interactions.created_at IS '互动时间';
COMMENT ON COLUMN social_interactions.metadata IS '元数据（JSON）';
