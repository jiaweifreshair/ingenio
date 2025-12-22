-- 项目表迁移
-- 学生创作的项目（用于社区展示和分享）

-- 创建项目表
CREATE TABLE IF NOT EXISTS projects (
  -- 主键和标识
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

  -- 租户和所有者
  tenant_id UUID NOT NULL,
  user_id UUID NOT NULL,

  -- 项目基本信息
  name VARCHAR(200) NOT NULL,
  description TEXT,
  cover_image_url VARCHAR(500),

  -- 关联AppSpec
  app_spec_id UUID NOT NULL,
  -- 当前活跃的AppSpec版本

  -- 项目状态
  status VARCHAR(20) NOT NULL DEFAULT 'draft',
  -- draft: 草稿
  -- published: 已发布
  -- archived: 已归档

  -- 可见性
  visibility VARCHAR(20) NOT NULL DEFAULT 'private',
  -- private: 私有（仅自己可见）
  -- public: 公开（所有人可见）
  -- unlisted: 不公开列表（仅通过链接可见）

  -- 社交统计
  view_count INTEGER NOT NULL DEFAULT 0,
  like_count INTEGER NOT NULL DEFAULT 0,
  fork_count INTEGER NOT NULL DEFAULT 0,
  comment_count INTEGER NOT NULL DEFAULT 0,

  -- 标签（用于分类和搜索）
  tags VARCHAR(50)[] DEFAULT ARRAY[]::VARCHAR[],
  -- 例如：['教育', '小学', '数学', '游戏']

  -- 年龄分组标签
  age_group VARCHAR(50),
  -- elementary: 小学
  -- middle_school: 初中
  -- high_school: 高中
  -- university: 大学

  -- 审计字段
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  published_at TIMESTAMP,

  -- 元数据
  metadata JSONB NOT NULL DEFAULT '{}'::jsonb
);

-- 创建索引
CREATE INDEX idx_projects_tenant_id ON projects(tenant_id);
CREATE INDEX idx_projects_user_id ON projects(user_id);
CREATE INDEX idx_projects_app_spec_id ON projects(app_spec_id);
CREATE INDEX idx_projects_status ON projects(status);
CREATE INDEX idx_projects_visibility ON projects(visibility);
CREATE INDEX idx_projects_age_group ON projects(age_group);
CREATE INDEX idx_projects_created_at ON projects(created_at DESC);
CREATE INDEX idx_projects_view_count ON projects(view_count DESC);
CREATE INDEX idx_projects_like_count ON projects(like_count DESC);
CREATE INDEX idx_projects_fork_count ON projects(fork_count DESC);

-- GIN索引用于标签数组搜索
CREATE INDEX idx_projects_tags ON projects USING GIN (tags);

-- GIN索引用于JSONB查询
CREATE INDEX idx_projects_metadata ON projects USING GIN (metadata);

-- 全文搜索索引（项目名称和描述）
CREATE INDEX idx_projects_name_search ON projects USING GIN (to_tsvector('simple', name));
CREATE INDEX idx_projects_description_search ON projects USING GIN (to_tsvector('simple', description));

-- 创建更新时间触发器
CREATE TRIGGER update_projects_updated_at
  BEFORE UPDATE ON projects
  FOR EACH ROW
  EXECUTE FUNCTION update_updated_at_column();

-- 添加外键约束
ALTER TABLE projects
  ADD CONSTRAINT fk_projects_user
  FOREIGN KEY (user_id)
  REFERENCES users(id)
  ON DELETE CASCADE;

ALTER TABLE projects
  ADD CONSTRAINT fk_projects_app_spec
  FOREIGN KEY (app_spec_id)
  REFERENCES app_specs(id)
  ON DELETE CASCADE;

-- 添加约束
ALTER TABLE projects
  ADD CONSTRAINT chk_projects_status
  CHECK (status IN ('draft', 'published', 'archived'));

ALTER TABLE projects
  ADD CONSTRAINT chk_projects_visibility
  CHECK (visibility IN ('private', 'public', 'unlisted'));

ALTER TABLE projects
  ADD CONSTRAINT chk_projects_age_group
  CHECK (age_group IS NULL OR age_group IN ('elementary', 'middle_school', 'high_school', 'university'));

ALTER TABLE projects
  ADD CONSTRAINT chk_projects_view_count
  CHECK (view_count >= 0);

ALTER TABLE projects
  ADD CONSTRAINT chk_projects_like_count
  CHECK (like_count >= 0);

ALTER TABLE projects
  ADD CONSTRAINT chk_projects_fork_count
  CHECK (fork_count >= 0);

ALTER TABLE projects
  ADD CONSTRAINT chk_projects_comment_count
  CHECK (comment_count >= 0);

-- 添加注释
COMMENT ON TABLE projects IS '项目表 - 学生创作的项目（用于社区展示和分享）';
COMMENT ON COLUMN projects.id IS '项目ID（UUID）';
COMMENT ON COLUMN projects.tenant_id IS '租户ID';
COMMENT ON COLUMN projects.user_id IS '所有者用户ID';
COMMENT ON COLUMN projects.name IS '项目名称';
COMMENT ON COLUMN projects.description IS '项目描述';
COMMENT ON COLUMN projects.cover_image_url IS '封面图片URL';
COMMENT ON COLUMN projects.app_spec_id IS '关联的AppSpec ID（当前活跃版本）';
COMMENT ON COLUMN projects.status IS '项目状态：draft/published/archived';
COMMENT ON COLUMN projects.visibility IS '可见性：private/public/unlisted';
COMMENT ON COLUMN projects.view_count IS '浏览次数';
COMMENT ON COLUMN projects.like_count IS '点赞数';
COMMENT ON COLUMN projects.fork_count IS '派生数';
COMMENT ON COLUMN projects.comment_count IS '评论数';
COMMENT ON COLUMN projects.tags IS '标签数组（用于分类和搜索）';
COMMENT ON COLUMN projects.age_group IS '年龄分组：elementary/middle_school/high_school/university';
COMMENT ON COLUMN projects.created_at IS '创建时间';
COMMENT ON COLUMN projects.updated_at IS '更新时间';
COMMENT ON COLUMN projects.published_at IS '发布时间';
COMMENT ON COLUMN projects.metadata IS '元数据（JSON）';
