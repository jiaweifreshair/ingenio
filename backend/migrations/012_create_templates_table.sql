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
