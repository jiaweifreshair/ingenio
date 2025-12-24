-- ==========================================
-- 回滚脚本（删除所有数据库对象）
-- 警告：此操作不可逆！
-- ==========================================

-- 删除触发器
DROP TRIGGER IF EXISTS update_users_updated_at ON users;
DROP TRIGGER IF EXISTS update_todo_lists_updated_at ON todo_lists;
DROP TRIGGER IF EXISTS update_todo_items_updated_at ON todo_items;

-- 删除RLS策略

-- 删除索引

-- 删除表
DROP TABLE IF EXISTS users CASCADE;
DROP TABLE IF EXISTS todo_lists CASCADE;
DROP TABLE IF EXISTS todo_items CASCADE;

-- 删除函数
DROP FUNCTION IF EXISTS update_updated_at_column();
