-- V024: 修复V022遗漏的V2.0字段定义
-- 补充添加intent_type, confidence_score, design_confirmed三个字段
-- 这些字段在V022中创建了索引但忘记了ADD COLUMN语句

-- 添加意图类型字段（CLONE_EXISTING_WEBSITE / DESIGN_FROM_SCRATCH / HYBRID_CLONE_AND_CUSTOMIZE）
ALTER TABLE app_specs ADD COLUMN IF NOT EXISTS intent_type VARCHAR(50);
COMMENT ON COLUMN app_specs.intent_type IS 'V2.0意图类型：CLONE_EXISTING_WEBSITE(克隆网站) / DESIGN_FROM_SCRATCH(从零设计) / HYBRID_CLONE_AND_CUSTOMIZE(混合模式)';

-- 添加置信度分数字段（0.0-1.0，表示AI意图识别的准确度）
ALTER TABLE app_specs ADD COLUMN IF NOT EXISTS confidence_score DECIMAL(5,4);
COMMENT ON COLUMN app_specs.confidence_score IS 'V2.0意图识别置信度分数（0.0000-1.0000），如0.9543表示95.43%准确率';

-- 添加设计确认标志字段（ExecuteGuard前置条件）
ALTER TABLE app_specs ADD COLUMN IF NOT EXISTS design_confirmed BOOLEAN DEFAULT FALSE;
COMMENT ON COLUMN app_specs.design_confirmed IS 'V2.0设计确认标志：用户是否点击"确认设计"按钮，ExecuteGuard强制要求为true';

-- 说明：V022中已经为这三个字段创建了索引（idx_app_specs_design_confirmed和idx_app_specs_intent_type）
-- 本迁移仅补充缺失的字段定义
