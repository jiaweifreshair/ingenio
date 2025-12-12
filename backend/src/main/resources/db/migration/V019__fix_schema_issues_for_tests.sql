-- 修复测试中发现的数据库Schema问题
-- 1. 将projects.app_spec_id改为可为NULL (修复ProjectE2ETest失败)
-- 2. 添加users表缺失的字段 (修复UserMapper错误)

-- ============================================================
-- Part 1: 修复projects表 - app_spec_id字段
-- ============================================================

-- 移除原有的NOT NULL约束
ALTER TABLE projects ALTER COLUMN app_spec_id DROP NOT NULL;

-- 添加注释说明变更原因
COMMENT ON COLUMN projects.app_spec_id IS '关联的AppSpec ID（当前活跃版本）- 可为NULL以支持项目创建时的渐进式关联';

-- ============================================================
-- Part 2: 修复users表 - 添加缺失字段
-- ============================================================

-- 添加手机号字段
ALTER TABLE users ADD COLUMN IF NOT EXISTS phone VARCHAR(20);
CREATE INDEX IF NOT EXISTS idx_users_phone ON users(phone);
COMMENT ON COLUMN users.phone IS '手机号码';

-- 添加个人简介字段
ALTER TABLE users ADD COLUMN IF NOT EXISTS bio TEXT;
COMMENT ON COLUMN users.bio IS '用户个人简介';

-- 添加邮箱验证状态字段
ALTER TABLE users ADD COLUMN IF NOT EXISTS email_verified BOOLEAN DEFAULT FALSE;
CREATE INDEX IF NOT EXISTS idx_users_email_verified ON users(email_verified);
COMMENT ON COLUMN users.email_verified IS '邮箱是否已验证';

-- 添加手机号验证状态字段
ALTER TABLE users ADD COLUMN IF NOT EXISTS phone_verified BOOLEAN DEFAULT FALSE;
CREATE INDEX IF NOT EXISTS idx_users_phone_verified ON users(phone_verified);
COMMENT ON COLUMN users.phone_verified IS '手机号是否已验证';

-- ============================================================
-- 验证脚本（用于确认变更）
-- ============================================================

-- 验证projects.app_spec_id是否可为NULL
DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_name = 'projects'
        AND column_name = 'app_spec_id'
        AND is_nullable = 'YES'
    ) THEN
        RAISE NOTICE '✅ projects.app_spec_id 已成功设置为可为NULL';
    ELSE
        RAISE WARNING '❌ projects.app_spec_id 仍然是NOT NULL';
    END IF;
END $$;

-- 验证users表新增字段
DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_name = 'users'
        AND column_name IN ('phone', 'bio', 'email_verified', 'phone_verified')
    ) THEN
        RAISE NOTICE '✅ users表新字段已成功添加';
    ELSE
        RAISE WARNING '❌ users表新字段添加失败';
    END IF;
END $$;
