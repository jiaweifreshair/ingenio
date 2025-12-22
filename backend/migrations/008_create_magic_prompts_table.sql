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
