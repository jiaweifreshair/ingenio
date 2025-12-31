-- V027: 为 g3_jobs 表补齐 Blueprint 相关字段
--
-- 目的：
-- 1) G3 任务在创建时可直接绑定 Blueprint（来自 AppSpec 或 templateId）
-- 2) 使 Agent Prompt 注入与合规验证在 G3 运行期可用（F3/F4）
--
-- 字段说明：
-- - matched_template_id: 选中的行业模板ID（用于追溯/复现）
-- - blueprint_spec: Blueprint 完整规范（JSONB）
-- - blueprint_mode_enabled: 是否启用 Blueprint 模式
-- - template_context: Scout 推荐模版上下文（补齐字段，避免实体与表结构漂移）

ALTER TABLE g3_jobs ADD COLUMN IF NOT EXISTS matched_template_id UUID;
ALTER TABLE g3_jobs ADD COLUMN IF NOT EXISTS blueprint_spec JSONB;
ALTER TABLE g3_jobs ADD COLUMN IF NOT EXISTS blueprint_mode_enabled BOOLEAN DEFAULT FALSE;

-- 兼容：当前实体已包含 template_context 字段，但 V025 建表未创建该列
ALTER TABLE g3_jobs ADD COLUMN IF NOT EXISTS template_context TEXT;

COMMENT ON COLUMN g3_jobs.matched_template_id IS '匹配/选中的行业模板ID';
COMMENT ON COLUMN g3_jobs.blueprint_spec IS 'Blueprint 完整规范（JSONB）';
COMMENT ON COLUMN g3_jobs.blueprint_mode_enabled IS '是否启用 Blueprint 模式';
COMMENT ON COLUMN g3_jobs.template_context IS 'Scout 推荐模版上下文（可选）';

CREATE INDEX IF NOT EXISTS idx_g3_jobs_matched_template_id ON g3_jobs(matched_template_id);
CREATE INDEX IF NOT EXISTS idx_g3_jobs_blueprint_mode_enabled
ON g3_jobs(blueprint_mode_enabled)
WHERE blueprint_mode_enabled = TRUE;

