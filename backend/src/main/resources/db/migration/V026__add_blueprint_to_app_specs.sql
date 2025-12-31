-- V026: 为 app_specs 表补齐 Blueprint 相关字段
--
-- 目的：
-- 1) 在 PlanRouting 阶段持久化用户选中的模板 Blueprint
-- 2) 支持 Blueprint Mode 的前后端闭环（约束注入/合规校验/可追溯）
--
-- 字段说明：
-- - blueprint_id: Blueprint 唯一标识（来自 blueprintSpec.id）
-- - blueprint_spec: Blueprint 完整规范（JSONB）
-- - blueprint_mode_enabled: 是否启用 Blueprint 模式（由 blueprint_spec 是否为空推导，也支持显式控制）

ALTER TABLE app_specs ADD COLUMN IF NOT EXISTS blueprint_id VARCHAR(64);
ALTER TABLE app_specs ADD COLUMN IF NOT EXISTS blueprint_spec JSONB;
ALTER TABLE app_specs ADD COLUMN IF NOT EXISTS blueprint_mode_enabled BOOLEAN DEFAULT FALSE;

COMMENT ON COLUMN app_specs.blueprint_id IS 'Blueprint ID（来自 blueprintSpec.id）';
COMMENT ON COLUMN app_specs.blueprint_spec IS 'Blueprint 完整规范（JSONB）';
COMMENT ON COLUMN app_specs.blueprint_mode_enabled IS '是否启用 Blueprint 模式';

-- 查询优化：只对启用 Blueprint 的记录建立部分索引
CREATE INDEX IF NOT EXISTS idx_app_specs_blueprint_mode_enabled
ON app_specs(blueprint_mode_enabled)
WHERE blueprint_mode_enabled = TRUE;

