-- ==========================================
-- Supabase数据库迁移脚本
-- 版本: V1.0.0
-- 生成时间: 2025-12-22 20:54:38
-- 生成工具: Ingenio V2.0 DatabaseSchemaGenerator
-- ==========================================

-- 启用UUID扩展（Supabase标准）
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- ==========================================
-- Function: update_updated_at_column
-- Description: 自动更新updated_at字段（所有表共用）
-- ==========================================
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = NOW();
    RETURN NEW;
END;
$$ language 'plpgsql';

-- ==========================================
-- 创建表
-- ==========================================

-- ==========================================
-- Table: users
-- Description: 应用的用户表，存储注册用户的基本信息
-- ==========================================
CREATE TABLE users (
  -- 标准字段（Supabase自动管理）
  id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  created_at TIMESTAMPTZ DEFAULT NOW(),
  updated_at TIMESTAMPTZ DEFAULT NOW(),

  -- 业务字段
  id UUID NOT NULL DEFAULT uuid_generate_v4(),
  email VARCHAR(255) NOT NULL UNIQUE,
  username VARCHAR(50) NOT NULL UNIQUE,
  full_name VARCHAR(100),
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE users IS '应用的用户表，存储注册用户的基本信息';
COMMENT ON COLUMN users.id IS '用户唯一标识';
COMMENT ON COLUMN users.email IS '用户邮箱，用于登录';
COMMENT ON COLUMN users.username IS '用户名';
COMMENT ON COLUMN users.full_name IS '用户全名';
COMMENT ON COLUMN users.created_at IS '用户创建时间';
COMMENT ON COLUMN users.updated_at IS '用户信息更新时间';

-- ==========================================
-- Table: todo_lists
-- Description: 待办事项列表，用户可创建多个列表
-- ==========================================
CREATE TABLE todo_lists (
  -- 标准字段（Supabase自动管理）
  id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  created_at TIMESTAMPTZ DEFAULT NOW(),
  updated_at TIMESTAMPTZ DEFAULT NOW(),

  -- 业务字段
  id UUID NOT NULL DEFAULT uuid_generate_v4(),
  title VARCHAR(100) NOT NULL,
  description TEXT,
  user_id UUID NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE todo_lists IS '待办事项列表，用户可创建多个列表';
COMMENT ON COLUMN todo_lists.id IS '待办列表唯一标识';
COMMENT ON COLUMN todo_lists.title IS '列表标题';
COMMENT ON COLUMN todo_lists.description IS '列表描述';
COMMENT ON COLUMN todo_lists.user_id IS '创建该列表的用户ID';
COMMENT ON COLUMN todo_lists.created_at IS '列表创建时间';
COMMENT ON COLUMN todo_lists.updated_at IS '列表更新时间';

-- ==========================================
-- Table: todo_items
-- Description: 具体的待办事项条目
-- ==========================================
CREATE TABLE todo_items (
  -- 标准字段（Supabase自动管理）
  id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  created_at TIMESTAMPTZ DEFAULT NOW(),
  updated_at TIMESTAMPTZ DEFAULT NOW(),

  -- 业务字段
  id UUID NOT NULL DEFAULT uuid_generate_v4(),
  title VARCHAR(255) NOT NULL,
  description TEXT,
  status VARCHAR(20) NOT NULL DEFAULT pending,
  priority VARCHAR(10) NOT NULL DEFAULT medium,
  due_date TIMESTAMPTZ,
  todo_list_id UUID NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE todo_items IS '具体的待办事项条目';
COMMENT ON COLUMN todo_items.id IS '待办事项唯一标识';
COMMENT ON COLUMN todo_items.title IS '待办事项标题';
COMMENT ON COLUMN todo_items.description IS '事项详细描述';
COMMENT ON COLUMN todo_items.status IS '事项状态';
COMMENT ON COLUMN todo_items.priority IS '优先级';
COMMENT ON COLUMN todo_items.due_date IS '截止日期';
COMMENT ON COLUMN todo_items.todo_list_id IS '所属待办列表ID';
COMMENT ON COLUMN todo_items.created_at IS '事项创建时间';
COMMENT ON COLUMN todo_items.updated_at IS '事项更新时间';

-- ==========================================
-- 创建索引
-- ==========================================

-- ==========================================
-- 启用RLS和创建策略
-- ==========================================

-- ==========================================
-- 创建触发器（自动更新updated_at）
-- ==========================================

-- ==========================================
-- Trigger: Auto-update updated_at for users
-- ==========================================
CREATE TRIGGER update_users_updated_at
  BEFORE UPDATE ON users
  FOR EACH ROW
  EXECUTE FUNCTION update_updated_at_column();

-- ==========================================
-- Trigger: Auto-update updated_at for todo_lists
-- ==========================================
CREATE TRIGGER update_todo_lists_updated_at
  BEFORE UPDATE ON todo_lists
  FOR EACH ROW
  EXECUTE FUNCTION update_updated_at_column();

-- ==========================================
-- Trigger: Auto-update updated_at for todo_items
-- ==========================================
CREATE TRIGGER update_todo_items_updated_at
  BEFORE UPDATE ON todo_items
  FOR EACH ROW
  EXECUTE FUNCTION update_updated_at_column();

-- ==========================================
-- 迁移脚本结束
-- ==========================================
