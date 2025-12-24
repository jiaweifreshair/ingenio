-- ==========================================
-- Supabase数据库迁移脚本
-- 版本: V1.0.0
-- 生成时间: 2025-12-22 18:12:33
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
-- Description: 存储任务的核心信息
-- ==========================================
CREATE TABLE tasks (
  -- 标准字段（Supabase自动管理）
  id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  created_at TIMESTAMPTZ DEFAULT NOW(),
  updated_at TIMESTAMPTZ DEFAULT NOW(),

  -- 业务字段
  id UUID NOT NULL DEFAULT uuid_generate_v4(),
  title VARCHAR(255) NOT NULL,
  description TEXT,
  due_date TIMESTAMPTZ,
  status_id UUID NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE tasks IS '存储任务的核心信息';
COMMENT ON COLUMN tasks.id IS '任务唯一标识符';
COMMENT ON COLUMN tasks.title IS '任务标题';
COMMENT ON COLUMN tasks.description IS '任务详细描述';
COMMENT ON COLUMN tasks.due_date IS '任务截止日期';
COMMENT ON COLUMN tasks.status_id IS '关联的任务状态ID';
COMMENT ON COLUMN tasks.created_at IS '创建时间';
COMMENT ON COLUMN tasks.updated_at IS '最后更新时间';

-- ==========================================
-- Table: task_statuses
-- Description: 定义任务状态的枚举值
-- ==========================================
CREATE TABLE task_statuses (
  -- 标准字段（Supabase自动管理）
  id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
  created_at TIMESTAMPTZ DEFAULT NOW(),
  updated_at TIMESTAMPTZ DEFAULT NOW(),

  -- 业务字段
  id UUID NOT NULL DEFAULT uuid_generate_v4(),
  name VARCHAR(50) NOT NULL UNIQUE,
  description TEXT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

COMMENT ON TABLE task_statuses IS '定义任务状态的枚举值';
COMMENT ON COLUMN task_statuses.id IS '状态唯一标识符';
COMMENT ON COLUMN task_statuses.name IS '状态名称（如：pending, in_progress, completed）';
COMMENT ON COLUMN task_statuses.description IS '状态描述';
COMMENT ON COLUMN task_statuses.created_at IS '创建时间';
COMMENT ON COLUMN task_statuses.updated_at IS '最后更新时间';

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
-- Trigger: Auto-update updated_at for task_statuses
-- ==========================================
CREATE TRIGGER update_task_statuses_updated_at
  BEFORE UPDATE ON task_statuses
  FOR EACH ROW
  EXECUTE FUNCTION update_updated_at_column();

-- ==========================================
-- 迁移脚本结束
-- ==========================================
