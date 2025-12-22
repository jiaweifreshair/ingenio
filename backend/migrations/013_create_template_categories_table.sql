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
