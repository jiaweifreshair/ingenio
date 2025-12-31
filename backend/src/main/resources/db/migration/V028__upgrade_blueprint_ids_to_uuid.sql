-- V028: Blueprint 主键升级为 UUID（兼容历史 slug）
--
-- 目标：
-- 1) 将 Blueprint JSON 中的 id 统一为 UUID 字符串（与现有 G3/TypeHandler/DDL 风格一致）
-- 2) 将 app_specs.blueprint_id 从 VARCHAR 升级为 UUID，便于强类型追溯与索引扩展
-- 3) 对已存在的历史 slug 数据（campus-marketplace/personal-blog）做兼容迁移
--
-- 映射关系（历史 slug → 新 UUID）：
-- - campus-marketplace → 28f12c4d-db84-4583-ba26-96426a415c68
-- - personal-blog      → f46a9ce0-e682-4a4d-922b-bfd8ba70807d

-- 1) 统一 industry_templates.blueprint_spec.id
UPDATE industry_templates
SET blueprint_spec = jsonb_set(blueprint_spec, '{id}', to_jsonb('28f12c4d-db84-4583-ba26-96426a415c68'::text), true)
WHERE blueprint_spec IS NOT NULL
  AND blueprint_spec->>'id' = 'campus-marketplace';

UPDATE industry_templates
SET blueprint_spec = jsonb_set(blueprint_spec, '{id}', to_jsonb('f46a9ce0-e682-4a4d-922b-bfd8ba70807d'::text), true)
WHERE blueprint_spec IS NOT NULL
  AND blueprint_spec->>'id' = 'personal-blog';

-- 2) 统一 app_specs.blueprint_spec.id（如历史数据已写入 AppSpec）
UPDATE app_specs
SET blueprint_spec = jsonb_set(blueprint_spec, '{id}', to_jsonb('28f12c4d-db84-4583-ba26-96426a415c68'::text), true)
WHERE blueprint_spec IS NOT NULL
  AND blueprint_spec->>'id' = 'campus-marketplace';

UPDATE app_specs
SET blueprint_spec = jsonb_set(blueprint_spec, '{id}', to_jsonb('f46a9ce0-e682-4a4d-922b-bfd8ba70807d'::text), true)
WHERE blueprint_spec IS NOT NULL
  AND blueprint_spec->>'id' = 'personal-blog';

-- 3) 统一 g3_jobs.blueprint_spec.id（如历史数据已写入 G3 Job）
UPDATE g3_jobs
SET blueprint_spec = jsonb_set(blueprint_spec, '{id}', to_jsonb('28f12c4d-db84-4583-ba26-96426a415c68'::text), true)
WHERE blueprint_spec IS NOT NULL
  AND blueprint_spec->>'id' = 'campus-marketplace';

UPDATE g3_jobs
SET blueprint_spec = jsonb_set(blueprint_spec, '{id}', to_jsonb('f46a9ce0-e682-4a4d-922b-bfd8ba70807d'::text), true)
WHERE blueprint_spec IS NOT NULL
  AND blueprint_spec->>'id' = 'personal-blog';

-- 4) app_specs.blueprint_id: VARCHAR → UUID（使用 CASE 兼容历史 slug / 空值 / 已是 UUID 的情况）
ALTER TABLE app_specs
ALTER COLUMN blueprint_id TYPE UUID
USING (
  CASE
    WHEN blueprint_id IS NULL THEN NULL
    WHEN BTRIM(blueprint_id) = '' THEN NULL
    WHEN blueprint_id ~* '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$'
      THEN blueprint_id::uuid
    WHEN blueprint_id = 'campus-marketplace' THEN '28f12c4d-db84-4583-ba26-96426a415c68'::uuid
    WHEN blueprint_id = 'personal-blog' THEN 'f46a9ce0-e682-4a4d-922b-bfd8ba70807d'::uuid
    ELSE NULL
  END
);

COMMENT ON COLUMN app_specs.blueprint_id IS 'Blueprint ID（UUID，来自 blueprintSpec.id）';

