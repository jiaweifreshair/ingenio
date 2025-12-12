ALTER TABLE industry_templates ADD COLUMN blueprint_spec JSONB;
COMMENT ON COLUMN industry_templates.blueprint_spec IS '模版蓝图定义：包含表结构、API定义和生成约束';
