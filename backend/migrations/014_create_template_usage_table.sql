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
