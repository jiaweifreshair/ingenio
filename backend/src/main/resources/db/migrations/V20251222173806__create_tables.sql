-- ==========================================
-- Supabase数据库迁移脚本
-- 版本: V1.0.0
-- 生成时间: 2025-12-22 17:38:06
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
-- Table: tasks
-- Description: 存储所有任务数据
-- ==========================================
CREATE TABLE tasks (
  -- 标准字段（Supabase自动管理）
  id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  created_at TIMESTAMPTZ DEFAULT NOW(),
  updated_at TIMESTAMPTZ DEFAULT NOW(),

  -- 业务字段
  id UUID NOT NULL DEFAULT uuid_generate_v4(),
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  title VARCHAR(255) NOT NULL,
  description TEXT,
  status VARCHAR(50) NOT NULL,
  due_date TIMESTAMPTZ,
  priority VARCHAR(50) NOT NULL DEFAULT 'medium'
);

COMMENT ON TABLE tasks IS '存储所有任务数据';
COMMENT ON COLUMN tasks.id IS '任务唯一标识符';
COMMENT ON COLUMN tasks.created_at IS '创建时间';
COMMENT ON COLUMN tasks.updated_at IS '最后更新时间';
COMMENT ON COLUMN tasks.title IS '任务标题';
COMMENT ON COLUMN tasks.description IS '任务详细描述';
COMMENT ON COLUMN tasks.status IS '任务状态';
COMMENT ON COLUMN tasks.due_date IS '任务截止日期';
COMMENT ON COLUMN tasks.priority IS '任务优先级';

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
-- Trigger: Auto-update updated_at for tasks
-- ==========================================
CREATE TRIGGER update_tasks_updated_at
  BEFORE UPDATE ON tasks
  FOR EACH ROW
  EXECUTE FUNCTION update_updated_at_column();

-- ==========================================
-- 迁移脚本结束
-- ==========================================
