-- ==========================================
-- Supabase数据库迁移脚本
-- 版本: V1.0.0
-- 生成时间: 2025-12-22 20:50:37
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
-- Table: todos
-- Description: 待办事项表
-- ==========================================
CREATE TABLE todos (
  -- 标准字段（Supabase自动管理）
  id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  created_at TIMESTAMPTZ DEFAULT NOW(),
  updated_at TIMESTAMPTZ DEFAULT NOW(),

  -- 业务字段
  id UUID NOT NULL DEFAULT uuid_generate_v4(),
  user_id UUID NOT NULL,
  title VARCHAR(255) NOT NULL,
  description TEXT,
  status VARCHAR(50) NOT NULL,
  due_date TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE todos IS '待办事项表';
COMMENT ON COLUMN todos.id IS '待办事项唯一标识';
COMMENT ON COLUMN todos.user_id IS '关联的用户ID';
COMMENT ON COLUMN todos.title IS '待办事项标题';
COMMENT ON COLUMN todos.description IS '待办事项描述';
COMMENT ON COLUMN todos.status IS '待办事项状态';
COMMENT ON COLUMN todos.due_date IS '待办事项截止时间';
COMMENT ON COLUMN todos.created_at IS '创建时间';
COMMENT ON COLUMN todos.updated_at IS '最后更新时间';

-- ==========================================
-- Table: users
-- Description: 用户表
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
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE users IS '用户表';
COMMENT ON COLUMN users.id IS '用户唯一标识';
COMMENT ON COLUMN users.email IS '用户邮箱';
COMMENT ON COLUMN users.username IS '用户名';
COMMENT ON COLUMN users.created_at IS '创建时间';
COMMENT ON COLUMN users.updated_at IS '最后更新时间';

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
-- Trigger: Auto-update updated_at for todos
-- ==========================================
CREATE TRIGGER update_todos_updated_at
  BEFORE UPDATE ON todos
  FOR EACH ROW
  EXECUTE FUNCTION update_updated_at_column();

-- ==========================================
-- Trigger: Auto-update updated_at for users
-- ==========================================
CREATE TRIGGER update_users_updated_at
  BEFORE UPDATE ON users
  FOR EACH ROW
  EXECUTE FUNCTION update_updated_at_column();

-- ==========================================
-- 迁移脚本结束
-- ==========================================
