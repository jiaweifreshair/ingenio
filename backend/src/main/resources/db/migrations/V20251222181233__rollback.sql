-- ==========================================
-- 回滚脚本（删除所有数据库对象）
-- 警告：此操作不可逆！
-- ==========================================

-- 删除触发器
DROP TRIGGER IF EXISTS update_tasks_updated_at ON tasks;
DROP TRIGGER IF EXISTS update_task_statuses_updated_at ON task_statuses;

-- 删除RLS策略

-- 删除索引

-- 删除表
DROP TABLE IF EXISTS tasks CASCADE;
DROP TABLE IF EXISTS task_statuses CASCADE;

-- 删除函数
DROP FUNCTION IF EXISTS update_updated_at_column();
