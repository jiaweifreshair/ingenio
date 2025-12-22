-- V2.0回滚迁移：移除app_specs表的V2.0字段
-- Phase X.2: Database V2.0 Schema Downgrade

-- =================================================================================
-- 删除V2.0统计视图
-- =================================================================================

DROP VIEW IF EXISTS v2_design_confirmation_stats;
DROP VIEW IF EXISTS v2_style_popularity;
DROP VIEW IF EXISTS v2_intent_distribution;

-- =================================================================================
-- 删除V2.0索引
-- =================================================================================

DROP INDEX IF EXISTS idx_app_specs_intent_type;
DROP INDEX IF EXISTS idx_app_specs_v2_execute_guard;
DROP INDEX IF EXISTS idx_app_specs_matched_templates;

-- =================================================================================
-- 删除V2.0业务约束
-- =================================================================================

ALTER TABLE app_specs DROP CONSTRAINT IF EXISTS chk_app_specs_confirmed_at_consistency;
ALTER TABLE app_specs DROP CONSTRAINT IF EXISTS chk_app_specs_confirmed_must_have_style;
ALTER TABLE app_specs DROP CONSTRAINT IF EXISTS chk_app_specs_selected_style;
ALTER TABLE app_specs DROP CONSTRAINT IF EXISTS chk_app_specs_confidence_score;

-- =================================================================================
-- 删除V2.0新增字段
-- =================================================================================

ALTER TABLE app_specs DROP COLUMN IF EXISTS design_confirmed_at;
ALTER TABLE app_specs DROP COLUMN IF EXISTS design_confirmed;
ALTER TABLE app_specs DROP COLUMN IF EXISTS selected_style;
ALTER TABLE app_specs DROP COLUMN IF EXISTS selected_template_id;
ALTER TABLE app_specs DROP COLUMN IF EXISTS matched_templates;
ALTER TABLE app_specs DROP COLUMN IF EXISTS confidence_score;
ALTER TABLE app_specs DROP COLUMN IF EXISTS intent_type;

-- V2.0回滚完成
