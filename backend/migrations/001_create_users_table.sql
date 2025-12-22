-- 用户表迁移
-- 创建用户表，支持认证和租户隔离

-- 创建用户表
CREATE TABLE IF NOT EXISTS users (
  -- 主键和标识
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

  -- 租户隔离
  tenant_id VARCHAR(255) NOT NULL,

  -- 认证信息
  username VARCHAR(255) NOT NULL UNIQUE,
  email VARCHAR(255) NOT NULL UNIQUE,
  password_hash VARCHAR(255) NOT NULL,

  -- 角色和权限
  role VARCHAR(50) NOT NULL DEFAULT 'user',
  permissions JSONB NOT NULL DEFAULT '[]'::jsonb,

  -- 用户状态
  status VARCHAR(50) NOT NULL DEFAULT 'active',

  -- 用户资料
  display_name VARCHAR(255),
  avatar_url VARCHAR(500),

  -- 登录信息
  last_login_at TIMESTAMP,

  -- 审计字段
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

  -- 元数据
  metadata JSONB NOT NULL DEFAULT '{}'::jsonb
);

-- 创建索引
CREATE INDEX idx_users_tenant_id ON users(tenant_id);
CREATE INDEX idx_users_username ON users(username);
CREATE INDEX idx_users_email ON users(email);
CREATE INDEX idx_users_status ON users(status);

-- 创建更新时间触发器
CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
  NEW.updated_at = CURRENT_TIMESTAMP;
  RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER update_users_updated_at
  BEFORE UPDATE ON users
  FOR EACH ROW
  EXECUTE FUNCTION update_updated_at_column();

-- 添加注释
COMMENT ON TABLE users IS '用户表 - 管理用户认证信息和基本资料';
COMMENT ON COLUMN users.id IS '用户ID（UUID）';
COMMENT ON COLUMN users.tenant_id IS '租户ID - 用于租户隔离';
COMMENT ON COLUMN users.username IS '用户名 - 唯一标识';
COMMENT ON COLUMN users.email IS '邮箱地址 - 唯一标识';
COMMENT ON COLUMN users.password_hash IS '密码哈希值（bcrypt）';
COMMENT ON COLUMN users.role IS '用户角色：admin/user/viewer';
COMMENT ON COLUMN users.permissions IS '权限列表（JSON数组）';
COMMENT ON COLUMN users.status IS '用户状态：active/inactive/suspended';
COMMENT ON COLUMN users.display_name IS '用户显示名称';
COMMENT ON COLUMN users.avatar_url IS '头像URL';
COMMENT ON COLUMN users.last_login_at IS '最后登录时间';
COMMENT ON COLUMN users.created_at IS '创建时间';
COMMENT ON COLUMN users.updated_at IS '更新时间';
COMMENT ON COLUMN users.metadata IS '元数据（JSON）';
