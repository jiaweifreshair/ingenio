-- V022: 添加V2.0原型预览相关字段到app_specs表
-- 支持ExecuteGuard前置条件检查功能
-- 包含：前端原型代码、预览URL、生成时间、意图识别结果

-- 添加前端原型代码字段（JSONB格式存储React组件代码）
ALTER TABLE app_specs ADD COLUMN IF NOT EXISTS frontend_prototype JSONB;
COMMENT ON COLUMN app_specs.frontend_prototype IS 'V2.0前端原型代码，OpenLovable生成的React原型（JSONB）';

-- 添加前端原型预览URL字段
ALTER TABLE app_specs ADD COLUMN IF NOT EXISTS frontend_prototype_url VARCHAR(500);
COMMENT ON COLUMN app_specs.frontend_prototype_url IS 'V2.0前端原型预览URL，E2B Sandbox部署地址';

-- 添加原型生成时间字段
ALTER TABLE app_specs ADD COLUMN IF NOT EXISTS prototype_generated_at TIMESTAMPTZ;
COMMENT ON COLUMN app_specs.prototype_generated_at IS 'V2.0原型生成时间戳';

-- 添加意图识别结果字段（JSONB格式存储完整分类结果）
ALTER TABLE app_specs ADD COLUMN IF NOT EXISTS intent_classification_result JSONB;
COMMENT ON COLUMN app_specs.intent_classification_result IS 'V2.0意图识别完整结果，包含intent/confidence/reasoning/keywords/urls';

-- 创建索引以优化查询性能
-- 对设计确认状态创建索引（用于ExecuteGuard快速查询未确认的AppSpec）
CREATE INDEX IF NOT EXISTS idx_app_specs_design_confirmed ON app_specs(design_confirmed) WHERE design_confirmed = FALSE;

-- 对原型生成时间创建索引（用于监控原型生成状态）
CREATE INDEX IF NOT EXISTS idx_app_specs_prototype_generated_at ON app_specs(prototype_generated_at) WHERE prototype_generated_at IS NOT NULL;

-- 对意图类型创建索引（用于统计分析不同意图类型的分布）
CREATE INDEX IF NOT EXISTS idx_app_specs_intent_type ON app_specs(intent_type) WHERE intent_type IS NOT NULL;
