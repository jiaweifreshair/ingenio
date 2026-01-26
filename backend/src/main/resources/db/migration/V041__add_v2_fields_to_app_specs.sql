-- V021: 添加V2.0版本管理和意图识别相关字段到app_specs表
-- 创建时间: 2026-01-25

-- 添加V2.0意图识别相关字段
ALTER TABLE app_specs ADD COLUMN IF NOT EXISTS intent_type VARCHAR(50);
ALTER TABLE app_specs ADD COLUMN IF NOT EXISTS confidence_score DECIMAL(3,2);
ALTER TABLE app_specs ADD COLUMN IF NOT EXISTS matched_templates JSONB;
ALTER TABLE app_specs ADD COLUMN IF NOT EXISTS selected_template_id UUID;
ALTER TABLE app_specs ADD COLUMN IF NOT EXISTS selected_style VARCHAR(50);
ALTER TABLE app_specs ADD COLUMN IF NOT EXISTS design_confirmed BOOLEAN DEFAULT FALSE;
ALTER TABLE app_specs ADD COLUMN IF NOT EXISTS design_confirmed_at TIMESTAMP WITH TIME ZONE;

-- 添加V2.0原型预览相关字段
ALTER TABLE app_specs ADD COLUMN IF NOT EXISTS frontend_prototype JSONB;
ALTER TABLE app_specs ADD COLUMN IF NOT EXISTS frontend_prototype_url VARCHAR(500);

-- 添加V2.0版本管理相关字段（Phase 1新增）
ALTER TABLE app_specs ADD COLUMN IF NOT EXISTS frontend_code_url VARCHAR(500);
ALTER TABLE app_specs ADD COLUMN IF NOT EXISTS backend_code_url VARCHAR(500);
ALTER TABLE app_specs ADD COLUMN IF NOT EXISTS code_archive_path VARCHAR(500);
ALTER TABLE app_specs ADD COLUMN IF NOT EXISTS code_commit_hash VARCHAR(100);

-- 添加注释
COMMENT ON COLUMN app_specs.intent_type IS 'V2.0意图类型：CLONE_EXISTING_WEBSITE/DESIGN_FROM_SCRATCH/HYBRID_CLONE_AND_CUSTOMIZE';
COMMENT ON COLUMN app_specs.confidence_score IS 'V2.0意图识别置信度分数(0.00-1.00)';
COMMENT ON COLUMN app_specs.matched_templates IS 'V2.0匹配的模板ID列表(JSONB)';
COMMENT ON COLUMN app_specs.selected_template_id IS 'V2.0用户选择的模板ID';
COMMENT ON COLUMN app_specs.selected_style IS 'V2.0用户选择的设计风格(A-G)';
COMMENT ON COLUMN app_specs.design_confirmed IS 'V2.0用户是否确认设计';
COMMENT ON COLUMN app_specs.design_confirmed_at IS 'V2.0设计确认时间';
COMMENT ON COLUMN app_specs.frontend_prototype IS 'V2.0前端原型代码(JSONB)';
COMMENT ON COLUMN app_specs.frontend_prototype_url IS 'V2.0前端原型预览URL';
COMMENT ON COLUMN app_specs.frontend_code_url IS '前端代码仓库地址(GitHub/GitLab)';
COMMENT ON COLUMN app_specs.backend_code_url IS '后端代码仓库地址';
COMMENT ON COLUMN app_specs.code_archive_path IS '代码归档路径(本地存储/OSS)';
COMMENT ON COLUMN app_specs.code_commit_hash IS '代码提交哈希值';

-- 创建索引
CREATE INDEX IF NOT EXISTS idx_app_specs_intent_type ON app_specs(intent_type);
CREATE INDEX IF NOT EXISTS idx_app_specs_selected_style ON app_specs(selected_style);
CREATE INDEX IF NOT EXISTS idx_app_specs_design_confirmed ON app_specs(design_confirmed);
CREATE INDEX IF NOT EXISTS idx_app_specs_code_commit_hash ON app_specs(code_commit_hash);
