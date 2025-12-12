-- 用户表迁移
-- 创建用户表，支持认证和租户隔离

-- 创建用户表
CREATE TABLE IF NOT EXISTS users (
  -- 主键和标识
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

  -- 租户隔离
  tenant_id VARCHAR(255) NOT NULL,

  -- 认证信息
  username VARCHAR(255) NOT NULL UNIQUE,
  email VARCHAR(255) NOT NULL UNIQUE,
  password_hash VARCHAR(255) NOT NULL,

  -- 角色和权限
  role VARCHAR(50) NOT NULL DEFAULT 'user',
  permissions JSONB NOT NULL DEFAULT '[]'::jsonb,

  -- 用户状态
  status VARCHAR(50) NOT NULL DEFAULT 'active',

  -- 用户资料
  display_name VARCHAR(255),
  avatar_url VARCHAR(500),
  phone VARCHAR(20),
  bio TEXT,

  -- 验证状态
  email_verified BOOLEAN DEFAULT FALSE,
  phone_verified BOOLEAN DEFAULT FALSE,

  -- 登录信息
  last_login_at TIMESTAMP,

  -- 审计字段
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

  -- 元数据
  metadata JSONB NOT NULL DEFAULT '{}'::jsonb
);

-- 创建索引
CREATE INDEX idx_users_tenant_id ON users(tenant_id);
CREATE INDEX idx_users_username ON users(username);
CREATE INDEX idx_users_email ON users(email);
CREATE INDEX idx_users_status ON users(status);

-- 创建更新时间触发器
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
  NEW.updated_at = CURRENT_TIMESTAMP;
  RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER update_users_updated_at
  BEFORE UPDATE ON users
  FOR EACH ROW
  EXECUTE FUNCTION update_updated_at_column();

-- 添加注释
COMMENT ON TABLE users IS '用户表 - 管理用户认证信息和基本资料';
COMMENT ON COLUMN users.id IS '用户ID（UUID）';
COMMENT ON COLUMN users.tenant_id IS '租户ID - 用于租户隔离';
COMMENT ON COLUMN users.username IS '用户名 - 唯一标识';
COMMENT ON COLUMN users.email IS '邮箱地址 - 唯一标识';
COMMENT ON COLUMN users.password_hash IS '密码哈希值（bcrypt）';
COMMENT ON COLUMN users.role IS '用户角色：admin/user/viewer';
COMMENT ON COLUMN users.permissions IS '权限列表（JSON数组）';
COMMENT ON COLUMN users.status IS '用户状态：active/inactive/suspended';
COMMENT ON COLUMN users.display_name IS '用户显示名称';
COMMENT ON COLUMN users.avatar_url IS '头像URL';
COMMENT ON COLUMN users.last_login_at IS '最后登录时间';
COMMENT ON COLUMN users.created_at IS '创建时间';
COMMENT ON COLUMN users.updated_at IS '更新时间';
COMMENT ON COLUMN users.metadata IS '元数据（JSON）';
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
-- 生成代码记录表迁移
-- 存储KuiklyUI渲染器生成的代码文件信息

-- 创建生成代码表
CREATE TABLE IF NOT EXISTS generated_code (
  -- 主键和标识
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

  -- 关联AppSpec
  app_spec_id UUID NOT NULL,

  -- 项目标识
  project_id VARCHAR(100) NOT NULL UNIQUE,
  -- 格式：{tenant_id}_{timestamp}_{random}

  -- 渲染器信息
  renderer_type VARCHAR(50) NOT NULL DEFAULT 'kuikly-ui',
  -- kuikly-ui: KuiklyUI/Taro渲染器
  -- react-next: React/Next.js渲染器（未来扩展）
  -- vue-vite: Vue/Vite渲染器（未来扩展）

  framework VARCHAR(20) NOT NULL DEFAULT 'taro',
  -- taro: Taro 3.x
  -- next: Next.js（未来扩展）
  -- nuxt: Nuxt.js（未来扩展）

  -- 存储信息
  storage_key VARCHAR(500) NOT NULL,
  -- MinIO存储路径：projects/{project_id}/code.zip

  -- 文件统计
  file_count INTEGER NOT NULL DEFAULT 0,
  total_size_bytes BIGINT NOT NULL DEFAULT 0,

  -- 构建状态
  build_status VARCHAR(50) NOT NULL DEFAULT 'pending',
  -- pending: 等待构建
  -- building: 构建中
  -- success: 构建成功
  -- failed: 构建失败

  build_log TEXT,
  -- 构建日志（如有错误）

  -- 预览URL
  preview_url VARCHAR(500),
  -- 格式：http://preview.ingenio.local/{project_id}

  -- 审计字段
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

  -- 元数据
  metadata JSONB NOT NULL DEFAULT '{}'::jsonb
  -- 包含：依赖版本、构建配置等
);

-- 创建索引
CREATE INDEX idx_generated_code_app_spec_id ON generated_code(app_spec_id);
CREATE INDEX idx_generated_code_project_id ON generated_code(project_id);
CREATE INDEX idx_generated_code_renderer_type ON generated_code(renderer_type);
CREATE INDEX idx_generated_code_build_status ON generated_code(build_status);
CREATE INDEX idx_generated_code_created_at ON generated_code(created_at DESC);

-- GIN索引用于JSONB查询
CREATE INDEX idx_generated_code_metadata ON generated_code USING GIN (metadata);

-- 创建更新时间触发器
CREATE TRIGGER update_generated_code_updated_at
  BEFORE UPDATE ON generated_code
  FOR EACH ROW
  EXECUTE FUNCTION update_updated_at_column();

-- 添加外键约束
ALTER TABLE generated_code
  ADD CONSTRAINT fk_generated_code_app_spec
  FOREIGN KEY (app_spec_id)
  REFERENCES app_specs(id)
  ON DELETE CASCADE;

-- 添加约束
ALTER TABLE generated_code
  ADD CONSTRAINT chk_generated_code_renderer_type
  CHECK (renderer_type IN ('kuikly-ui', 'react-next', 'vue-vite'));

ALTER TABLE generated_code
  ADD CONSTRAINT chk_generated_code_framework
  CHECK (framework IN ('taro', 'next', 'nuxt'));

ALTER TABLE generated_code
  ADD CONSTRAINT chk_generated_code_build_status
  CHECK (build_status IN ('pending', 'building', 'success', 'failed'));

ALTER TABLE generated_code
  ADD CONSTRAINT chk_generated_code_file_count
  CHECK (file_count >= 0);

ALTER TABLE generated_code
  ADD CONSTRAINT chk_generated_code_total_size
  CHECK (total_size_bytes >= 0);

-- 添加注释
COMMENT ON TABLE generated_code IS '生成代码记录表 - 存储KuiklyUI渲染器生成的代码文件信息';
COMMENT ON COLUMN generated_code.id IS '记录ID（UUID）';
COMMENT ON COLUMN generated_code.app_spec_id IS '关联的AppSpec ID';
COMMENT ON COLUMN generated_code.project_id IS '项目标识（唯一）';
COMMENT ON COLUMN generated_code.renderer_type IS '渲染器类型：kuikly-ui/react-next/vue-vite';
COMMENT ON COLUMN generated_code.framework IS '框架类型：taro/next/nuxt';
COMMENT ON COLUMN generated_code.storage_key IS 'MinIO存储路径';
COMMENT ON COLUMN generated_code.file_count IS '文件数量';
COMMENT ON COLUMN generated_code.total_size_bytes IS '总大小（字节）';
COMMENT ON COLUMN generated_code.build_status IS '构建状态：pending/building/success/failed';
COMMENT ON COLUMN generated_code.build_log IS '构建日志';
COMMENT ON COLUMN generated_code.preview_url IS '预览URL';
COMMENT ON COLUMN generated_code.created_at IS '创建时间';
COMMENT ON COLUMN generated_code.updated_at IS '更新时间';
COMMENT ON COLUMN generated_code.metadata IS '元数据（JSON）';
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
  app_spec_id UUID,
  -- 当前活跃的AppSpec版本（可为NULL以支持项目创建时的渐进式关联）

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
-- 魔法提示词模板表迁移
-- 存储针对不同年龄段学生的快速启动模板

-- 创建魔法提示词表
CREATE TABLE IF NOT EXISTS magic_prompts (
  -- 主键和标识
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

  -- 模板基本信息
  title VARCHAR(200) NOT NULL,
  description TEXT NOT NULL,
  cover_image_url VARCHAR(500),

  -- 提示词内容
  prompt_template TEXT NOT NULL,
  -- 包含变量占位符的模板，例如：
  -- "创建一个{subject}学习{tool_type}，包含{features}功能"

  -- 预填充变量（可选）
  default_variables JSONB DEFAULT '{}'::jsonb,
  -- 例如：{ "subject": "数学", "tool_type": "工具", "features": "练习和测试" }

  -- 年龄分组
  age_group VARCHAR(50) NOT NULL,
  -- elementary: 小学
  -- middle_school: 初中
  -- high_school: 高中
  -- university: 大学

  -- 分类
  category VARCHAR(50) NOT NULL,
  -- education: 教育学习
  -- game: 游戏娱乐
  -- productivity: 效率工具
  -- creative: 创意设计
  -- social: 社交互动

  -- 难度级别
  difficulty_level VARCHAR(20) NOT NULL DEFAULT 'beginner',
  -- beginner: 初级
  -- intermediate: 中级
  -- advanced: 高级

  -- 示例项目（可选）
  example_project_id UUID,
  -- 关联一个示例项目，学生可以预览效果

  -- 使用统计
  usage_count INTEGER NOT NULL DEFAULT 0,
  like_count INTEGER NOT NULL DEFAULT 0,

  -- 状态
  status VARCHAR(20) NOT NULL DEFAULT 'active',
  -- active: 活跃
  -- draft: 草稿
  -- archived: 已归档

  -- 标签
  tags VARCHAR(50)[] DEFAULT ARRAY[]::VARCHAR[],
  -- 例如：['数学', '游戏化', '可视化']

  -- 审计字段
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  created_by_user_id UUID,

  -- 元数据
  metadata JSONB NOT NULL DEFAULT '{}'::jsonb
);

-- 创建索引
CREATE INDEX idx_magic_prompts_age_group ON magic_prompts(age_group);
CREATE INDEX idx_magic_prompts_category ON magic_prompts(category);
CREATE INDEX idx_magic_prompts_difficulty_level ON magic_prompts(difficulty_level);
CREATE INDEX idx_magic_prompts_status ON magic_prompts(status);
CREATE INDEX idx_magic_prompts_usage_count ON magic_prompts(usage_count DESC);
CREATE INDEX idx_magic_prompts_like_count ON magic_prompts(like_count DESC);
CREATE INDEX idx_magic_prompts_created_at ON magic_prompts(created_at DESC);
CREATE INDEX idx_magic_prompts_example_project_id ON magic_prompts(example_project_id);

-- GIN索引用于标签数组搜索
CREATE INDEX idx_magic_prompts_tags ON magic_prompts USING GIN (tags);

-- GIN索引用于JSONB查询
CREATE INDEX idx_magic_prompts_default_variables ON magic_prompts USING GIN (default_variables);

-- 全文搜索索引（标题和描述）
CREATE INDEX idx_magic_prompts_title_search ON magic_prompts USING GIN (to_tsvector('simple', title));
CREATE INDEX idx_magic_prompts_description_search ON magic_prompts USING GIN (to_tsvector('simple', description));

-- 创建更新时间触发器
CREATE TRIGGER update_magic_prompts_updated_at
  BEFORE UPDATE ON magic_prompts
  FOR EACH ROW
  EXECUTE FUNCTION update_updated_at_column();

-- 添加外键约束
ALTER TABLE magic_prompts
  ADD CONSTRAINT fk_magic_prompts_example_project
  FOREIGN KEY (example_project_id)
  REFERENCES projects(id)
  ON DELETE SET NULL;

ALTER TABLE magic_prompts
  ADD CONSTRAINT fk_magic_prompts_creator
  FOREIGN KEY (created_by_user_id)
  REFERENCES users(id)
  ON DELETE SET NULL;

-- 添加约束
ALTER TABLE magic_prompts
  ADD CONSTRAINT chk_magic_prompts_age_group
  CHECK (age_group IN ('elementary', 'middle_school', 'high_school', 'university'));

ALTER TABLE magic_prompts
  ADD CONSTRAINT chk_magic_prompts_category
  CHECK (category IN ('education', 'game', 'productivity', 'creative', 'social'));

ALTER TABLE magic_prompts
  ADD CONSTRAINT chk_magic_prompts_difficulty_level
  CHECK (difficulty_level IN ('beginner', 'intermediate', 'advanced'));

ALTER TABLE magic_prompts
  ADD CONSTRAINT chk_magic_prompts_status
  CHECK (status IN ('active', 'draft', 'archived'));

ALTER TABLE magic_prompts
  ADD CONSTRAINT chk_magic_prompts_usage_count
  CHECK (usage_count >= 0);

ALTER TABLE magic_prompts
  ADD CONSTRAINT chk_magic_prompts_like_count
  CHECK (like_count >= 0);

-- 添加注释
COMMENT ON TABLE magic_prompts IS '魔法提示词模板表 - 针对不同年龄段学生的快速启动模板';
COMMENT ON COLUMN magic_prompts.id IS '模板ID（UUID）';
COMMENT ON COLUMN magic_prompts.title IS '模板标题';
COMMENT ON COLUMN magic_prompts.description IS '模板描述';
COMMENT ON COLUMN magic_prompts.cover_image_url IS '封面图片URL';
COMMENT ON COLUMN magic_prompts.prompt_template IS '提示词模板（包含变量占位符）';
COMMENT ON COLUMN magic_prompts.default_variables IS '预填充变量（JSON）';
COMMENT ON COLUMN magic_prompts.age_group IS '年龄分组：elementary/middle_school/high_school/university';
COMMENT ON COLUMN magic_prompts.category IS '分类：education/game/productivity/creative/social';
COMMENT ON COLUMN magic_prompts.difficulty_level IS '难度级别：beginner/intermediate/advanced';
COMMENT ON COLUMN magic_prompts.example_project_id IS '示例项目ID（可选）';
COMMENT ON COLUMN magic_prompts.usage_count IS '使用次数';
COMMENT ON COLUMN magic_prompts.like_count IS '点赞数';
COMMENT ON COLUMN magic_prompts.status IS '状态：active/draft/archived';
COMMENT ON COLUMN magic_prompts.tags IS '标签数组';
COMMENT ON COLUMN magic_prompts.created_at IS '创建时间';
COMMENT ON COLUMN magic_prompts.updated_at IS '更新时间';
COMMENT ON COLUMN magic_prompts.created_by_user_id IS '创建者用户ID';
COMMENT ON COLUMN magic_prompts.metadata IS '元数据（JSON）';
-- 创建生成任务表
CREATE TABLE generation_tasks (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    user_id UUID NOT NULL,
    task_name VARCHAR(255) NOT NULL,
    user_requirement TEXT NOT NULL,

    -- 任务状态和进度
    status VARCHAR(50) NOT NULL DEFAULT 'pending',
    current_agent VARCHAR(50),
    progress INTEGER DEFAULT 0,

    -- Agent状态信息（JSON格式存储详细信息）
    agents_info JSONB,

    -- 执行结果
    plan_result JSONB,
    app_spec_content JSONB,
    validate_result JSONB,

    -- 最终输出
    app_spec_id UUID,
    quality_score INTEGER,
    download_url VARCHAR(500),
    preview_url VARCHAR(500),

    -- Token使用统计
    token_usage JSONB,

    -- 错误信息
    error_message TEXT,

    -- 时间信息
    started_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    completed_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),

    -- 元数据
    metadata JSONB
);

-- 创建索引
CREATE INDEX idx_generation_tasks_tenant_id ON generation_tasks(tenant_id);
CREATE INDEX idx_generation_tasks_user_id ON generation_tasks(user_id);
CREATE INDEX idx_generation_tasks_status ON generation_tasks(status);
CREATE INDEX idx_generation_tasks_created_at ON generation_tasks(created_at);
CREATE INDEX idx_generation_tasks_app_spec_id ON generation_tasks(app_spec_id);

-- 添加约束
ALTER TABLE generation_tasks ADD CONSTRAINT chk_generation_tasks_status
    CHECK (status IN ('pending', 'planning', 'executing', 'validating', 'generating', 'completed', 'failed', 'cancelled'));

ALTER TABLE generation_tasks ADD CONSTRAINT chk_generation_tasks_progress
    CHECK (progress >= 0 AND progress <= 100);

ALTER TABLE generation_tasks ADD CONSTRAINT chk_generation_tasks_quality_score
    CHECK (quality_score >= 0 AND quality_score <= 100);

-- 添加外键约束（如果app_specs表存在）
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'app_specs') THEN
        ALTER TABLE generation_tasks
        ADD CONSTRAINT fk_generation_tasks_app_spec_id
        FOREIGN KEY (app_spec_id) REFERENCES app_specs(id) ON DELETE SET NULL;
    END IF;
END
$$;

-- 添加注释
COMMENT ON TABLE generation_tasks IS 'AI生成任务表，存储从需求到代码的完整生成过程状态';
COMMENT ON COLUMN generation_tasks.id IS '任务唯一标识';
COMMENT ON COLUMN generation_tasks.tenant_id IS '租户ID，用于多租户隔离';
COMMENT ON COLUMN generation_tasks.user_id IS '用户ID';
COMMENT ON COLUMN generation_tasks.task_name IS '任务名称';
COMMENT ON COLUMN generation_tasks.user_requirement IS '用户的原始需求描述';
COMMENT ON COLUMN generation_tasks.status IS '任务状态：pending/planning/executing/validating/generating/completed/failed/cancelled';
COMMENT ON COLUMN generation_tasks.current_agent IS '当前执行的Agent：plan/execute/validate/generate';
COMMENT ON COLUMN generation_tasks.progress IS '任务进度百分比（0-100）';
COMMENT ON COLUMN generation_tasks.agents_info IS 'Agent状态信息JSON，包含每个Agent的详细状态';
COMMENT ON COLUMN generation_tasks.plan_result IS 'PlanAgent的执行结果';
COMMENT ON COLUMN generation_tasks.app_spec_content IS 'ExecuteAgent生成的AppSpec内容';
COMMENT ON COLUMN generation_tasks.validate_result IS 'ValidateAgent的验证结果';
COMMENT ON COLUMN generation_tasks.app_spec_id IS '最终生成的AppSpec记录ID';
COMMENT ON COLUMN generation_tasks.quality_score IS 'AppSpec质量评分（0-100）';
COMMENT ON COLUMN generation_tasks.download_url IS '生成代码的下载链接';
COMMENT ON COLUMN generation_tasks.preview_url IS '应用预览链接';
COMMENT ON COLUMN generation_tasks.token_usage IS 'Token使用统计信息';
COMMENT ON COLUMN generation_tasks.error_message IS '任务失败时的错误信息';
COMMENT ON COLUMN generation_tasks.started_at IS '任务开始执行时间';
COMMENT ON COLUMN generation_tasks.completed_at IS '任务完成时间';
COMMENT ON COLUMN generation_tasks.metadata IS '任务元数据JSON';-- 多模态输入记录表迁移
-- 支持文本/语音/视频/图像输入

CREATE TABLE IF NOT EXISTS multimodal_inputs (
  -- 主键和标识
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  
  -- 租户隔离
  tenant_id UUID NOT NULL,
  
  -- 关联用户和AppSpec
  user_id UUID NOT NULL,
  app_spec_id UUID,
  
  -- 输入类型
  input_type VARCHAR(20) NOT NULL,
  
  -- 文本输入内容
  text_content TEXT,
  
  -- 文件信息
  file_url VARCHAR(500),
  file_size BIGINT,
  file_mime_type VARCHAR(100),
  
  -- 处理结果
  transcript TEXT,
  analysis_result JSONB,
  
  -- 处理状态
  processing_status VARCHAR(20) NOT NULL DEFAULT 'pending',
  error_message TEXT,
  
  -- 审计字段
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  deleted INTEGER DEFAULT 0,
  
  -- 元数据
  metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
  
  -- 约束
  CONSTRAINT chk_multimodal_input_type 
    CHECK (input_type IN ('text', 'voice', 'video', 'image')),
  CONSTRAINT chk_multimodal_processing_status 
    CHECK (processing_status IN ('pending', 'processing', 'completed', 'failed'))
);

-- 创建索引
CREATE INDEX idx_multimodal_inputs_tenant ON multimodal_inputs(tenant_id);
CREATE INDEX idx_multimodal_inputs_user ON multimodal_inputs(user_id);
CREATE INDEX idx_multimodal_inputs_appspec ON multimodal_inputs(app_spec_id);
CREATE INDEX idx_multimodal_inputs_type ON multimodal_inputs(input_type);
CREATE INDEX idx_multimodal_inputs_status ON multimodal_inputs(processing_status);
CREATE INDEX idx_multimodal_inputs_created ON multimodal_inputs(created_at DESC);

-- GIN索引用于JSONB查询
CREATE INDEX idx_multimodal_analysis ON multimodal_inputs USING GIN (analysis_result);

-- 创建更新时间触发器
CREATE TRIGGER update_multimodal_inputs_updated_at
  BEFORE UPDATE ON multimodal_inputs
  FOR EACH ROW
  EXECUTE FUNCTION update_updated_at_column();

-- 添加外键约束
ALTER TABLE multimodal_inputs
  ADD CONSTRAINT fk_multimodal_inputs_user
  FOREIGN KEY (user_id)
  REFERENCES users(id)
  ON DELETE CASCADE;

ALTER TABLE multimodal_inputs
  ADD CONSTRAINT fk_multimodal_inputs_appspec
  FOREIGN KEY (app_spec_id)
  REFERENCES app_specs(id)
  ON DELETE SET NULL;

-- 添加注释
COMMENT ON TABLE multimodal_inputs IS '多模态输入记录表 - 支持文本/语音/视频/图像输入';
COMMENT ON COLUMN multimodal_inputs.id IS '输入记录ID（UUID）';
COMMENT ON COLUMN multimodal_inputs.tenant_id IS '租户ID - 用于租户隔离';
COMMENT ON COLUMN multimodal_inputs.user_id IS '用户ID';
COMMENT ON COLUMN multimodal_inputs.app_spec_id IS '关联的AppSpec ID（可为空）';
COMMENT ON COLUMN multimodal_inputs.input_type IS '输入类型：text/voice/video/image';
COMMENT ON COLUMN multimodal_inputs.text_content IS '文本输入内容';
COMMENT ON COLUMN multimodal_inputs.file_url IS '文件URL（MinIO）';
COMMENT ON COLUMN multimodal_inputs.file_size IS '文件大小（字节）';
COMMENT ON COLUMN multimodal_inputs.file_mime_type IS '文件MIME类型';
COMMENT ON COLUMN multimodal_inputs.transcript IS '语音转文字结果或OCR结果';
COMMENT ON COLUMN multimodal_inputs.analysis_result IS 'AI分析结果（JSON）';
COMMENT ON COLUMN multimodal_inputs.processing_status IS '处理状态：pending/processing/completed/failed';
COMMENT ON COLUMN multimodal_inputs.error_message IS '错误信息';
COMMENT ON COLUMN multimodal_inputs.created_at IS '创建时间';
COMMENT ON COLUMN multimodal_inputs.updated_at IS '更新时间';
COMMENT ON COLUMN multimodal_inputs.deleted IS '逻辑删除标记（0未删除/1已删除）';
COMMENT ON COLUMN multimodal_inputs.metadata IS '元数据（JSON）';
-- 网站抓取任务表迁移
-- 从现有网站抓取内容生成AppSpec

CREATE TABLE IF NOT EXISTS scrape_tasks (
  -- 主键和标识
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  
  -- 租户隔离
  tenant_id UUID NOT NULL,
  
  -- 关联用户和AppSpec
  user_id UUID NOT NULL,
  app_spec_id UUID,
  
  -- 抓取目标
  target_url VARCHAR(500) NOT NULL,
  scrape_type VARCHAR(20) NOT NULL DEFAULT 'full',
  
  -- 抓取配置
  scrape_config JSONB NOT NULL DEFAULT '{}'::jsonb,
  
  -- 抓取状态
  scrape_status VARCHAR(20) NOT NULL DEFAULT 'pending',
  
  -- 抓取结果
  result_data JSONB,
  error_message TEXT,
  
  -- 时间记录
  started_at TIMESTAMP,
  completed_at TIMESTAMP,
  duration_ms INTEGER,
  
  -- 审计字段
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  deleted INTEGER DEFAULT 0,
  
  -- 元数据
  metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
  
  -- 约束
  CONSTRAINT chk_scrape_type 
    CHECK (scrape_type IN ('full', 'screenshot', 'metadata')),
  CONSTRAINT chk_scrape_status 
    CHECK (scrape_status IN ('pending', 'running', 'completed', 'failed'))
);

-- 创建索引
CREATE INDEX idx_scrape_tasks_tenant ON scrape_tasks(tenant_id);
CREATE INDEX idx_scrape_tasks_user ON scrape_tasks(user_id);
CREATE INDEX idx_scrape_tasks_appspec ON scrape_tasks(app_spec_id);
CREATE INDEX idx_scrape_tasks_status ON scrape_tasks(scrape_status);
CREATE INDEX idx_scrape_tasks_created ON scrape_tasks(created_at DESC);
CREATE INDEX idx_scrape_tasks_url ON scrape_tasks(target_url);

-- GIN索引
CREATE INDEX idx_scrape_result_data ON scrape_tasks USING GIN (result_data);
CREATE INDEX idx_scrape_config ON scrape_tasks USING GIN (scrape_config);

-- 创建更新时间触发器
CREATE TRIGGER update_scrape_tasks_updated_at
  BEFORE UPDATE ON scrape_tasks
  FOR EACH ROW
  EXECUTE FUNCTION update_updated_at_column();

-- 添加外键约束
ALTER TABLE scrape_tasks
  ADD CONSTRAINT fk_scrape_tasks_user
  FOREIGN KEY (user_id)
  REFERENCES users(id)
  ON DELETE CASCADE;

ALTER TABLE scrape_tasks
  ADD CONSTRAINT fk_scrape_tasks_appspec
  FOREIGN KEY (app_spec_id)
  REFERENCES app_specs(id)
  ON DELETE CASCADE;

-- 添加注释
COMMENT ON TABLE scrape_tasks IS '网站抓取任务表 - 从现有网站抓取内容生成AppSpec';
COMMENT ON COLUMN scrape_tasks.id IS '抓取任务ID（UUID）';
COMMENT ON COLUMN scrape_tasks.tenant_id IS '租户ID';
COMMENT ON COLUMN scrape_tasks.user_id IS '用户ID';
COMMENT ON COLUMN scrape_tasks.app_spec_id IS '关联的AppSpec ID';
COMMENT ON COLUMN scrape_tasks.target_url IS '抓取目标URL';
COMMENT ON COLUMN scrape_tasks.scrape_type IS '抓取类型：full/screenshot/metadata';
COMMENT ON COLUMN scrape_tasks.scrape_config IS '抓取配置（JSON）';
COMMENT ON COLUMN scrape_tasks.scrape_status IS '抓取状态：pending/running/completed/failed';
COMMENT ON COLUMN scrape_tasks.result_data IS '抓取结果（JSON）';
COMMENT ON COLUMN scrape_tasks.error_message IS '错误信息';
COMMENT ON COLUMN scrape_tasks.started_at IS '开始时间';
COMMENT ON COLUMN scrape_tasks.completed_at IS '完成时间';
COMMENT ON COLUMN scrape_tasks.duration_ms IS '执行时长（毫秒）';
-- 模板表迁移
-- 官方和社区提供的应用模板

CREATE TABLE IF NOT EXISTS templates (
  -- 主键和标识
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  
  -- 租户隔离
  tenant_id UUID NOT NULL,
  
  -- 模板基本信息
  name VARCHAR(100) NOT NULL,
  display_name VARCHAR(200) NOT NULL,
  description TEXT,
  thumbnail_url VARCHAR(500),
  
  -- 模板分类
  category_id UUID,
  tags JSONB DEFAULT '[]'::jsonb,
  
  -- 模板内容（完整AppSpec）
  template_content JSONB NOT NULL,
  
  -- 模板类型
  template_type VARCHAR(20) NOT NULL DEFAULT 'community',
  
  -- 状态
  status VARCHAR(20) NOT NULL DEFAULT 'draft',
  
  -- 统计数据
  usage_count INTEGER DEFAULT 0,
  rating_average DECIMAL(3,2),
  rating_count INTEGER DEFAULT 0,
  
  -- 创建者信息
  created_by_user_id UUID,
  
  -- 审计字段
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  deleted INTEGER DEFAULT 0,
  
  -- 元数据
  metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
  
  -- 约束
  CONSTRAINT chk_template_type 
    CHECK (template_type IN ('official', 'community', 'private')),
  CONSTRAINT chk_template_status 
    CHECK (status IN ('draft', 'published', 'archived')),
  CONSTRAINT chk_template_rating 
    CHECK (rating_average IS NULL OR (rating_average >= 0 AND rating_average <= 5))
);

-- 创建索引
CREATE INDEX idx_templates_tenant ON templates(tenant_id);
CREATE INDEX idx_templates_category ON templates(category_id);
CREATE INDEX idx_templates_type ON templates(template_type);
CREATE INDEX idx_templates_status ON templates(status);
CREATE INDEX idx_templates_created_by ON templates(created_by_user_id);
CREATE INDEX idx_templates_usage ON templates(usage_count DESC);
CREATE INDEX idx_templates_rating ON templates(rating_average DESC NULLS LAST);
CREATE INDEX idx_templates_created ON templates(created_at DESC);

-- GIN索引（JSONB字段）
CREATE INDEX idx_templates_tags ON templates USING GIN (tags jsonb_path_ops);
CREATE INDEX idx_templates_content ON templates USING GIN (template_content jsonb_path_ops);

-- 创建更新时间触发器
CREATE TRIGGER update_templates_updated_at
  BEFORE UPDATE ON templates
  FOR EACH ROW
  EXECUTE FUNCTION update_updated_at_column();

-- 添加外键约束
ALTER TABLE templates
  ADD CONSTRAINT fk_templates_user
  FOREIGN KEY (created_by_user_id)
  REFERENCES users(id)
  ON DELETE SET NULL;

-- 添加注释
COMMENT ON TABLE templates IS '模板表 - 官方和社区提供的应用模板';
COMMENT ON COLUMN templates.id IS '模板ID（UUID）';
COMMENT ON COLUMN templates.tenant_id IS '租户ID';
COMMENT ON COLUMN templates.name IS '模板唯一标识名称';
COMMENT ON COLUMN templates.display_name IS '模板显示名称';
COMMENT ON COLUMN templates.description IS '模板描述';
COMMENT ON COLUMN templates.thumbnail_url IS '缩略图URL';
COMMENT ON COLUMN templates.category_id IS '分类ID';
COMMENT ON COLUMN templates.tags IS '标签数组';
COMMENT ON COLUMN templates.template_content IS '模板内容（完整AppSpec JSON）';
COMMENT ON COLUMN templates.template_type IS '模板类型：official/community/private';
COMMENT ON COLUMN templates.status IS '状态：draft/published/archived';
COMMENT ON COLUMN templates.usage_count IS '使用次数';
COMMENT ON COLUMN templates.rating_average IS '平均评分（0-5）';
COMMENT ON COLUMN templates.rating_count IS '评分次数';
COMMENT ON COLUMN templates.created_by_user_id IS '创建者用户ID';
-- 模板分类表迁移
-- 支持层级分类

CREATE TABLE IF NOT EXISTS template_categories (
  -- 主键
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  
  -- 分类信息
  name VARCHAR(50) NOT NULL,
  display_name VARCHAR(100) NOT NULL,
  description TEXT,
  icon_url VARCHAR(500),
  
  -- 排序和层级
  sort_order INTEGER DEFAULT 0,
  parent_id UUID,
  
  -- 审计字段
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  deleted INTEGER DEFAULT 0,
  
  -- 元数据
  metadata JSONB NOT NULL DEFAULT '{}'::jsonb
);

-- 创建索引
CREATE INDEX idx_template_categories_parent ON template_categories(parent_id);
CREATE INDEX idx_template_categories_order ON template_categories(sort_order);
CREATE INDEX idx_template_categories_name ON template_categories(name);

-- 创建更新时间触发器
CREATE TRIGGER update_template_categories_updated_at
  BEFORE UPDATE ON template_categories
  FOR EACH ROW
  EXECUTE FUNCTION update_updated_at_column();

-- 添加外键约束（自引用）
ALTER TABLE template_categories
  ADD CONSTRAINT fk_template_categories_parent
  FOREIGN KEY (parent_id)
  REFERENCES template_categories(id)
  ON DELETE SET NULL;

-- 添加注释
COMMENT ON TABLE template_categories IS '模板分类表 - 支持层级分类';
COMMENT ON COLUMN template_categories.id IS '分类ID（UUID）';
COMMENT ON COLUMN template_categories.name IS '分类唯一标识名称';
COMMENT ON COLUMN template_categories.display_name IS '分类显示名称';
COMMENT ON COLUMN template_categories.description IS '分类描述';
COMMENT ON COLUMN template_categories.icon_url IS '图标URL';
COMMENT ON COLUMN template_categories.sort_order IS '排序顺序';
COMMENT ON COLUMN template_categories.parent_id IS '父分类ID（支持层级）';
-- 模板使用记录表迁移
-- 统计模板使用情况和用户反馈

CREATE TABLE IF NOT EXISTS template_usage (
  -- 主键
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  
  -- 关联信息
  template_id UUID NOT NULL,
  user_id UUID NOT NULL,
  app_spec_id UUID,
  
  -- 使用反馈
  rating INTEGER,
  feedback TEXT,
  
  -- 审计字段
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  
  -- 约束
  CONSTRAINT chk_template_usage_rating 
    CHECK (rating IS NULL OR (rating >= 1 AND rating <= 5))
);

-- 创建索引
CREATE INDEX idx_template_usage_template ON template_usage(template_id);
CREATE INDEX idx_template_usage_user ON template_usage(user_id);
CREATE INDEX idx_template_usage_appspec ON template_usage(app_spec_id);
CREATE INDEX idx_template_usage_created ON template_usage(created_at DESC);
CREATE INDEX idx_template_usage_rating ON template_usage(rating);

-- 添加外键约束
ALTER TABLE template_usage
  ADD CONSTRAINT fk_template_usage_template
  FOREIGN KEY (template_id)
  REFERENCES templates(id)
  ON DELETE CASCADE;

ALTER TABLE template_usage
  ADD CONSTRAINT fk_template_usage_user
  FOREIGN KEY (user_id)
  REFERENCES users(id)
  ON DELETE CASCADE;

ALTER TABLE template_usage
  ADD CONSTRAINT fk_template_usage_appspec
  FOREIGN KEY (app_spec_id)
  REFERENCES app_specs(id)
  ON DELETE SET NULL;

-- 添加注释
COMMENT ON TABLE template_usage IS '模板使用记录表 - 统计模板使用情况和用户反馈';
COMMENT ON COLUMN template_usage.id IS '使用记录ID（UUID）';
COMMENT ON COLUMN template_usage.template_id IS '模板ID';
COMMENT ON COLUMN template_usage.user_id IS '用户ID';
COMMENT ON COLUMN template_usage.app_spec_id IS '生成的AppSpec ID';
COMMENT ON COLUMN template_usage.rating IS '用户评分（1-5）';
COMMENT ON COLUMN template_usage.feedback IS '用户反馈文本';
COMMENT ON COLUMN template_usage.created_at IS '创建时间';
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

-- =====================================================
-- V021: 行业模板库表（Phase X.4）
-- =====================================================

CREATE TABLE IF NOT EXISTS industry_templates (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    -- 基础信息
    name VARCHAR(200) NOT NULL,
    description TEXT NOT NULL,
    category VARCHAR(100) NOT NULL,
    subcategory VARCHAR(100),

    -- 匹配关键词
    keywords JSONB NOT NULL,

    -- 参考来源
    reference_url VARCHAR(500),
    thumbnail_url VARCHAR(500),

    -- 业务定义
    entities JSONB NOT NULL,
    features JSONB NOT NULL,
    workflows JSONB,

    -- 技术栈建议
    tech_stack JSONB,

    -- 复杂度和估算
    complexity_score INTEGER DEFAULT 5 CHECK (complexity_score BETWEEN 1 AND 10),
    estimated_hours INTEGER,

    -- 使用统计
    usage_count INTEGER DEFAULT 0,
    rating DECIMAL(3,2) CHECK (rating IS NULL OR rating BETWEEN 0 AND 5),

    -- 状态管理
    is_active BOOLEAN DEFAULT true,

    -- 审计字段
    created_by VARCHAR(100),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 创建索引
CREATE INDEX idx_industry_templates_keywords ON industry_templates USING GIN (keywords);
CREATE INDEX idx_industry_templates_category ON industry_templates (category, subcategory) WHERE is_active = true;
CREATE INDEX idx_industry_templates_usage_count ON industry_templates (usage_count DESC) WHERE is_active = true;
CREATE INDEX idx_industry_templates_rating ON industry_templates (rating DESC NULLS LAST) WHERE is_active = true AND rating IS NOT NULL;

-- 创建触发器
CREATE TRIGGER update_industry_templates_updated_at
    BEFORE UPDATE ON industry_templates
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

-- 添加表注释
COMMENT ON TABLE industry_templates IS '行业应用模板库 - Phase X.4';
