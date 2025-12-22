-- V2.0升级迁移：为app_specs表添加意图识别、模板匹配、风格选择、用户确认等字段
-- Phase X.2: Database V2.0 Schema Upgrade
-- 支持三分支路由��Plan阶段）和ExecuteGuard（Execute阶段前置检查）

-- =================================================================================
-- V2.0新增字段：意图识别相关
-- =================================================================================

-- 1. intent_type - 意图类型（CLONE_EXISTING_WEBSITE | DESIGN_FROM_SCRATCH | HYBRID_CLONE_AND_CUSTOMIZE）
ALTER TABLE app_specs
ADD COLUMN intent_type VARCHAR(255);

COMMENT ON COLUMN app_specs.intent_type IS 'V2.0意图类型：CLONE_EXISTING_WEBSITE（克隆网站）| DESIGN_FROM_SCRATCH（从零设计）| HYBRID_CLONE_AND_CUSTOMIZE（混合定制）';

-- 2. confidence_score - 意图识别置信度（0-1）
ALTER TABLE app_specs
ADD COLUMN confidence_score DECIMAL(3,2);

COMMENT ON COLUMN app_specs.confidence_score IS 'V2.0意图识别置信度（0.00-1.00）- IntentClassifier生成';

-- 添加约束：置信度范围检查
ALTER TABLE app_specs
ADD CONSTRAINT chk_app_specs_confidence_score
CHECK (confidence_score IS NULL OR (confidence_score >= 0 AND confidence_score <= 1));

-- =================================================================================
-- V2.0新增字段：模板匹配相关
-- =================================================================================

-- 3. matched_templates - 匹配的行业模板列表（JSON数组）
ALTER TABLE app_specs
ADD COLUMN matched_templates JSONB DEFAULT '[]'::jsonb;

COMMENT ON COLUMN app_specs.matched_templates IS 'V2.0匹配的行业模板列表（JSON数组）- IntentClassifier根据关键词匹配的模板';

-- 为模板匹配创建GIN索引
CREATE INDEX idx_app_specs_matched_templates ON app_specs USING GIN (matched_templates);

-- 4. selected_template_id - 用户选择的模板ID
ALTER TABLE app_specs
ADD COLUMN selected_template_id UUID;

COMMENT ON COLUMN app_specs.selected_template_id IS 'V2.0用户选择的行业模板ID（可选）- 用户从matched_templates中选择';

-- =================================================================================
-- V2.0新增字段：设计风格选择相关
-- =================================================================================

-- 5. selected_style - 用户选择的设计风格（A-G，对应7种SuperDesign风格）
ALTER TABLE app_specs
ADD COLUMN selected_style VARCHAR(255);

COMMENT ON COLUMN app_specs.selected_style IS 'V2.0用户选择的设计风格代码：modern_minimal(A) | vibrant_fashion(B) | classic_professional(C) | future_tech(D) | immersive_3d(E) | gamified(F) | natural_flow(G)';

-- 添加约束：风格代码有效性检查
ALTER TABLE app_specs
ADD CONSTRAINT chk_app_specs_selected_style
CHECK (selected_style IS NULL OR selected_style IN (
  'modern_minimal',
  'vibrant_fashion',
  'classic_professional',
  'future_tech',
  'immersive_3d',
  'gamified',
  'natural_flow'
));

-- =================================================================================
-- V2.0新增字段：用户确认相关（ExecuteGuard关键检查点）
-- =================================================================================

-- 6. design_confirmed - 用户是否确认设计（Boolean）
ALTER TABLE app_specs
ADD COLUMN design_confirmed BOOLEAN DEFAULT FALSE NOT NULL;

COMMENT ON COLUMN app_specs.design_confirmed IS 'V2.0用户是否确认设计 - ExecuteGuard关键检查点：必须为true才能进入Execute阶段';

-- 7. design_confirmed_at - 设计确认时间
ALTER TABLE app_specs
ADD COLUMN design_confirmed_at TIMESTAMP;

COMMENT ON COLUMN app_specs.design_confirmed_at IS 'V2.0设计确认时间 - 用户点击"确认设计"按钮的时间戳';

-- 8. frontend_prototype - 前端原型代码（JSONB）
ALTER TABLE app_specs
ADD COLUMN frontend_prototype JSONB;

COMMENT ON COLUMN app_specs.frontend_prototype IS 'V2.0前端原型代码（JSONB） - OpenLovable生成的React原型代码';

-- 9. frontend_prototype_url - 前端原型预览URL
ALTER TABLE app_specs
ADD COLUMN frontend_prototype_url VARCHAR(500);

COMMENT ON COLUMN app_specs.frontend_prototype_url IS 'V2.0前端原型预览URL - E2B Sandbox预览地址';

-- 10. prototype_generated_at - 原型生成时间
ALTER TABLE app_specs
ADD COLUMN prototype_generated_at TIMESTAMP;

COMMENT ON COLUMN app_specs.prototype_generated_at IS 'V2.0原型生成时间';

-- 11. intent_classification_result - 意图识别结果（JSONB）
ALTER TABLE app_specs
ADD COLUMN intent_classification_result JSONB;

COMMENT ON COLUMN app_specs.intent_classification_result IS 'V2.0意图识别结果（JSONB） - IntentClassifier完整结果';

-- =================================================================================
-- V2.0索引优化
-- =================================================================================

-- 为ExecuteGuard快速查询创建复合索引
CREATE INDEX idx_app_specs_v2_execute_guard
ON app_specs(id, design_confirmed, selected_style)
WHERE design_confirmed = TRUE AND selected_style IS NOT NULL;

-- 为意图类型快速过滤创建索引
CREATE INDEX idx_app_specs_intent_type ON app_specs(intent_type);

-- =================================================================================
-- V2.0数据完整性验证
-- =================================================================================

-- 添加业务规则约束：
-- 如果design_confirmed=true，则必须有selected_style（风格选择是必选项）
ALTER TABLE app_specs
ADD CONSTRAINT chk_app_specs_confirmed_must_have_style
CHECK (
  design_confirmed = FALSE OR
  (design_confirmed = TRUE AND selected_style IS NOT NULL)
);

-- 如果有design_confirmed_at，则design_confirmed必须为true
ALTER TABLE app_specs
ADD CONSTRAINT chk_app_specs_confirmed_at_consistency
CHECK (
  design_confirmed_at IS NULL OR
  (design_confirmed_at IS NOT NULL AND design_confirmed = TRUE)
);

-- =================================================================================
-- V2.0统计视图（可选）
-- =================================================================================

-- 创建视图：V2.0意图分布统计
CREATE OR REPLACE VIEW v2_intent_distribution AS
SELECT
  intent_type,
  COUNT(*) AS count,
  AVG(confidence_score) AS avg_confidence,
  ROUND(COUNT(*)::DECIMAL / NULLIF((SELECT COUNT(*) FROM app_specs WHERE intent_type IS NOT NULL), 0) * 100, 2) AS percentage
FROM app_specs
WHERE intent_type IS NOT NULL
GROUP BY intent_type
ORDER BY count DESC;

COMMENT ON VIEW v2_intent_distribution IS 'V2.0意图类型分布统计视图';

-- 创建视图：V2.0设计风格热度统计
CREATE OR REPLACE VIEW v2_style_popularity AS
SELECT
  selected_style,
  COUNT(*) AS count,
  ROUND(COUNT(*)::DECIMAL / NULLIF((SELECT COUNT(*) FROM app_specs WHERE selected_style IS NOT NULL), 0) * 100, 2) AS percentage
FROM app_specs
WHERE selected_style IS NOT NULL
GROUP BY selected_style
ORDER BY count DESC;

COMMENT ON VIEW v2_style_popularity IS 'V2.0设计风格热度排行榜';

-- 创建视图：V2.0确认率统计
CREATE OR REPLACE VIEW v2_design_confirmation_stats AS
SELECT
  COUNT(*) AS total_specs,
  COUNT(CASE WHEN design_confirmed = TRUE THEN 1 END) AS confirmed_count,
  ROUND(COUNT(CASE WHEN design_confirmed = TRUE THEN 1 END)::DECIMAL / NULLIF(COUNT(*), 0) * 100, 2) AS confirmation_rate,
  AVG(EXTRACT(EPOCH FROM (design_confirmed_at - created_at))) AS avg_time_to_confirm_seconds
FROM app_specs
WHERE created_at >= NOW() - INTERVAL '30 days';

COMMENT ON VIEW v2_design_confirmation_stats IS 'V2.0设计确认率统计（最近30天）';

-- =================================================================================
-- V2.0迁移完成标记
-- =================================================================================

-- 插入迁移记录（假设有migration_history表）
-- INSERT INTO migration_history (migration_id, description, executed_at)
-- VALUES ('019_add_v2_fields_to_app_specs', 'V2.0升级：添加意图识别、模板匹配、风格选择、用户确认字段', NOW());

-- V2.0迁移完成
