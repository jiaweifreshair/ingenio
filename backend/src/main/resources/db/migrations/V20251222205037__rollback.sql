-- ==========================================
-- 回滚脚本（删除所有数据库对象）
-- 警告：此操作不可逆！
-- ==========================================

-- 删除触发器
DROP TRIGGER IF EXISTS update_todos_updated_at ON todos;
DROP TRIGGER IF EXISTS update_users_updated_at ON users;

-- 删除RLS策略

-- 删除索引

-- 删除表
DROP TABLE IF EXISTS todos CASCADE;
DROP TABLE IF EXISTS users CASCADE;

-- 删除函数
DROP FUNCTION IF EXISTS update_updated_at_column();
