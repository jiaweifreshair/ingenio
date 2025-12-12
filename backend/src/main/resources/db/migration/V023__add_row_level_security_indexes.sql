-- V023: Row Level Security索引优化
-- 用途：为RBAC和Row Level Security提供高性能的所有权查询索引
-- Phase 4.4: 数据库迁移脚本和索引优化
-- 日期：2025-11-21

-- 背景说明：
-- OwnershipAspect使用AOP验证资源所有权，频繁执行以下查询模式：
-- 1. projects: WHERE tenant_id = ? AND user_id = ?
-- 2. app_specs: WHERE tenant_id = ? AND created_by_user_id = ?
-- 3. generation_tasks: WHERE tenant_id = ? AND user_id = ?
-- 4. api_keys: WHERE user_id = ?
--
-- 这些查询需要高效的复合索引支持

-- ==================================================
-- 1. Projects表索引优化
-- ==================================================

-- Projects表的所有权查询索引（tenant_id + user_id）
-- 用于：projectMapper.selectById() + 所有权验证
CREATE INDEX IF NOT EXISTS idx_projects_ownership
ON projects(tenant_id, user_id);

COMMENT ON INDEX idx_projects_ownership IS
'Row Level Security索引：用于快速验证项目所有权（OwnershipAspect）';

-- Projects表的状态查询索引（tenant_id + status）
-- 用于：按状态筛选项目（draft/published/archived）
CREATE INDEX IF NOT EXISTS idx_projects_tenant_status
ON projects(tenant_id, status);

COMMENT ON INDEX idx_projects_tenant_status IS
'状态筛选索引：用于按租户和状态查询项目';


-- ==================================================
-- 2. AppSpecs表索引优化
-- ==================================================

-- AppSpecs表的所有权查询索引（tenant_id + created_by_user_id）
-- 用于：appSpecMapper.selectById() + 所有权验证
CREATE INDEX IF NOT EXISTS idx_app_specs_ownership
ON app_specs(tenant_id, created_by_user_id);

COMMENT ON INDEX idx_app_specs_ownership IS
'Row Level Security索引：用于快速验证AppSpec所有权（OwnershipAspect）';

-- AppSpecs表的状态查询索引（tenant_id + status）
-- 用于：按状态筛选AppSpec（draft/validated/generated/published）
CREATE INDEX IF NOT EXISTS idx_app_specs_tenant_status
ON app_specs(tenant_id, status);

COMMENT ON INDEX idx_app_specs_tenant_status IS
'状态筛选索引：用于按租户和状态查询AppSpec';

-- AppSpecs表的版本查询索引（parent_version_id）
-- 用于：查询AppSpec的版本树
CREATE INDEX IF NOT EXISTS idx_app_specs_parent_version
ON app_specs(parent_version_id)
WHERE parent_version_id IS NOT NULL;

COMMENT ON INDEX idx_app_specs_parent_version IS
'版本树索引：用于查询AppSpec的父子版本关系（部分索引）';


-- ==================================================
-- 3. GenerationTasks表索引优化
-- ==================================================

-- GenerationTasks表的所有权查询索引（tenant_id + user_id）
-- 用于：generationTaskMapper.selectById() + 所有权验证
CREATE INDEX IF NOT EXISTS idx_generation_tasks_ownership
ON generation_tasks(tenant_id, user_id);

COMMENT ON INDEX idx_generation_tasks_ownership IS
'Row Level Security索引：用于快速验证生���任务所有权（OwnershipAspect）';

-- GenerationTasks表的状态查询索引（tenant_id + status）
-- 用于：按状态筛选任务（pending/running/completed/failed/cancelled）
CREATE INDEX IF NOT EXISTS idx_generation_tasks_tenant_status
ON generation_tasks(tenant_id, status);

COMMENT ON INDEX idx_generation_tasks_tenant_status IS
'状态筛选索引：用于按租户和状态查询生成任务';

-- GenerationTasks表的活跃任务索引（status + updated_at）
-- 用于：监控长时间运行的任务
CREATE INDEX IF NOT EXISTS idx_generation_tasks_active
ON generation_tasks(status, updated_at)
WHERE status IN ('pending', 'running');

COMMENT ON INDEX idx_generation_tasks_active IS
'活跃任务监控索引：用于识别长时间运行或挂起的任务（部分索引）';


-- ==================================================
-- 4. ApiKeys表索引优化
-- ==================================================

-- ApiKeys表的所有权查询索引（user_id）
-- 用于：apiKeyMapper.selectById() + 所有权验证
-- 注意：ApiKey没有tenant_id，仅用user_id验证
CREATE INDEX IF NOT EXISTS idx_api_keys_ownership
ON api_keys(user_id);

COMMENT ON INDEX idx_api_keys_ownership IS
'Row Level Security索引：用于快速验证API密钥所有权（OwnershipAspect）';

-- ApiKeys表的密钥查找索引（key_value）
-- 用于：apiKeyMapper.findByKeyValue() - API密钥验证
CREATE INDEX IF NOT EXISTS idx_api_keys_key_value
ON api_keys(key_value);

COMMENT ON INDEX idx_api_keys_key_value IS
'密钥查找索引：用于快速验证API密钥（SHA256哈希值）';

-- ApiKeys表的活跃密钥索引（is_active + expires_at）
-- 用于：清理过期密钥
CREATE INDEX IF NOT EXISTS idx_api_keys_active
ON api_keys(is_active, expires_at)
WHERE is_active = TRUE;

COMMENT ON INDEX idx_api_keys_active IS
'活跃密钥索引：用于快速查找有效和即将过期的密钥（部分索引）';


-- ==================================================
-- 5. Users表索引优化（如果需要）
-- ==================================================

-- Users表的角色查询索引（role）
-- 用于：StpInterfaceImpl.getRoleList() - 权限检查
CREATE INDEX IF NOT EXISTS idx_users_role
ON users(role);

COMMENT ON INDEX idx_users_role IS
'角色查询索引：用于快速查找特定角色的用户（RBAC）';

-- Users表的邮箱查询索引（email）
-- 用于：登录验证
CREATE INDEX IF NOT EXISTS idx_users_email
ON users(email);

COMMENT ON INDEX idx_users_email IS
'邮箱查询索引：用于登录验证和邮箱唯一性检查';


-- ==================================================
-- 6. 性能监控建议
-- ==================================================

-- 查询索引使用情况的SQL（供DBA使用）：
--
-- SELECT
--     schemaname,
--     tablename,
--     indexname,
--     idx_scan AS index_scans,
--     idx_tup_read AS tuples_read,
--     idx_tup_fetch AS tuples_fetched
-- FROM pg_stat_user_indexes
-- WHERE schemaname = 'public'
--   AND indexname LIKE 'idx_%ownership%'
-- ORDER BY idx_scan DESC;

-- 分析慢查询（供DBA使用）：
--
-- SELECT
--     query,
--     calls,
--     total_time / calls AS avg_time_ms,
--     total_time
-- FROM pg_stat_statements
-- WHERE query LIKE '%WHERE tenant_id%user_id%'
-- ORDER BY total_time DESC
-- LIMIT 20;

-- ==================================================
-- 7. 索引维护计划
-- ==================================================

-- 建议每月执行一次索引维护：
-- REINDEX TABLE CONCURRENTLY projects;
-- REINDEX TABLE CONCURRENTLY app_specs;
-- REINDEX TABLE CONCURRENTLY generation_tasks;
-- REINDEX TABLE CONCURRENTLY api_keys;
-- ANALYZE projects, app_specs, generation_tasks, api_keys;

-- ==================================================
-- 迁移完成
-- ==================================================
