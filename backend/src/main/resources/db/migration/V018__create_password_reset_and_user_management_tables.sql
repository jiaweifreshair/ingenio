-- V018: 创建密码重置和用户管理相关表
-- 用于支持密码重置、登录设备管理、操作日志、API密钥管理

-- 1. 密码重置令牌表
CREATE TABLE IF NOT EXISTS password_reset_tokens (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    token VARCHAR(64) NOT NULL UNIQUE,
    expires_at TIMESTAMP NOT NULL,
    used BOOLEAN DEFAULT FALSE,
    used_at TIMESTAMP,
    ip_address VARCHAR(50),
    user_agent TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_password_reset_tokens_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

-- 索引
CREATE INDEX idx_password_reset_tokens_user ON password_reset_tokens(user_id);
CREATE INDEX idx_password_reset_tokens_token ON password_reset_tokens(token);
CREATE INDEX idx_password_reset_tokens_expires ON password_reset_tokens(expires_at);
CREATE INDEX idx_password_reset_tokens_used ON password_reset_tokens(used);

COMMENT ON TABLE password_reset_tokens IS '密码重置令牌表';
COMMENT ON COLUMN password_reset_tokens.token IS '重置令牌（64位随机字符串）';
COMMENT ON COLUMN password_reset_tokens.expires_at IS '过期时间（默认1小时）';
COMMENT ON COLUMN password_reset_tokens.used IS '是否已使用';


-- 2. 用户登录设备表
CREATE TABLE IF NOT EXISTS user_devices (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    device_name VARCHAR(200),                         -- 设备名称（自动识别）
    device_type VARCHAR(50),                          -- desktop/mobile/tablet
    browser VARCHAR(100),                             -- 浏览器信息
    os VARCHAR(100),                                  -- 操作系统
    ip_address VARCHAR(50),                           -- IP地址
    location VARCHAR(200),                            -- 地理位置（可选）
    token_id VARCHAR(100),                            -- SaToken的TokenId
    last_active_at TIMESTAMP NOT NULL,                -- 最后活跃时间
    is_current BOOLEAN DEFAULT FALSE,                 -- 是否为当前设备
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_user_devices_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

-- 索引
CREATE INDEX idx_user_devices_user ON user_devices(user_id);
CREATE INDEX idx_user_devices_token ON user_devices(token_id);
CREATE INDEX idx_user_devices_active ON user_devices(last_active_at);

COMMENT ON TABLE user_devices IS '用户登录设备表';
COMMENT ON COLUMN user_devices.device_name IS '设备名称（如：Chrome on Windows）';
COMMENT ON COLUMN user_devices.token_id IS '关联的SaToken ID';
COMMENT ON COLUMN user_devices.is_current IS '是否为当前请求的设备';


-- 3. 用户操作日志表
CREATE TABLE IF NOT EXISTS user_logs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    action VARCHAR(100) NOT NULL,                     -- 操作类型（login/logout/change_password/delete_project等）
    action_category VARCHAR(50) NOT NULL,             -- 操作分类（auth/user/project/app等）
    description TEXT,                                 -- 操作描述
    resource_type VARCHAR(50),                        -- 资源类型（project/app/version）
    resource_id VARCHAR(100),                         -- 资源ID
    ip_address VARCHAR(50),                           -- IP地址
    user_agent TEXT,                                  -- User Agent
    request_method VARCHAR(10),                       -- HTTP方法
    request_path VARCHAR(500),                        -- 请求路径
    status VARCHAR(20) NOT NULL DEFAULT 'success',    -- success/failure
    error_message TEXT,                               -- 错误信息（失败时）
    execution_time_ms INTEGER,                        -- 执行时间（毫秒）
    metadata JSONB,                                   -- 额外元数据
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_user_logs_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

-- 索引
CREATE INDEX idx_user_logs_user ON user_logs(user_id);
CREATE INDEX idx_user_logs_action ON user_logs(action);
CREATE INDEX idx_user_logs_category ON user_logs(action_category);
CREATE INDEX idx_user_logs_created ON user_logs(created_at);
CREATE INDEX idx_user_logs_status ON user_logs(status);
CREATE INDEX idx_user_logs_resource ON user_logs(resource_type, resource_id);

COMMENT ON TABLE user_logs IS '用户操作日志表';
COMMENT ON COLUMN user_logs.action IS '操作类型（如：login/logout/create_project）';
COMMENT ON COLUMN user_logs.action_category IS '操作分类（auth/user/project/app等）';
COMMENT ON COLUMN user_logs.status IS '操作状态（success/failure）';


-- 4. API密钥表
CREATE TABLE IF NOT EXISTS api_keys (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,
    name VARCHAR(100) NOT NULL,                       -- 密钥名称（用户自定义）
    key_value VARCHAR(64) NOT NULL UNIQUE,            -- API密钥值（哈希存储）
    key_prefix VARCHAR(10) NOT NULL,                  -- 密钥前缀（用于显示，如：ing_xxxxx）
    description TEXT,                                 -- 密钥描述
    scopes JSONB,                                     -- 权限范围（如：["read", "write"]）
    is_active BOOLEAN DEFAULT TRUE,                   -- 是否启用
    last_used_at TIMESTAMP,                           -- 最后使用时间
    last_used_ip VARCHAR(50),                         -- 最后使用IP
    usage_count INTEGER DEFAULT 0,                    -- 使用次数
    rate_limit INTEGER,                               -- 速率限制（每分钟请求数）
    expires_at TIMESTAMP,                             -- 过期时间（可选，永久有效则为NULL）
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_api_keys_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

-- 索引
CREATE INDEX idx_api_keys_user ON api_keys(user_id);
CREATE INDEX idx_api_keys_value ON api_keys(key_value);
CREATE INDEX idx_api_keys_active ON api_keys(is_active);
CREATE INDEX idx_api_keys_expires ON api_keys(expires_at);

-- 触发器：自动更新updated_at
CREATE TRIGGER update_api_keys_updated_at
    BEFORE UPDATE ON api_keys
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

COMMENT ON TABLE api_keys IS 'API密钥表';
COMMENT ON COLUMN api_keys.key_value IS 'API密钥值（SHA256哈希存储）';
COMMENT ON COLUMN api_keys.key_prefix IS '密钥前缀（显示用，如：ing_xxxxx）';
COMMENT ON COLUMN api_keys.scopes IS '权限范围（JSON数组，如：["read", "write", "admin"]）';
COMMENT ON COLUMN api_keys.rate_limit IS '速率限制（每分钟最大请求数，NULL表示使用默认限制）';


-- 5. 添加users表缺失的字段（如果不存在）
DO $$
BEGIN
    -- 检查并添加phone字段
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                   WHERE table_name='users' AND column_name='phone') THEN
        ALTER TABLE users ADD COLUMN phone VARCHAR(20);
        CREATE INDEX idx_users_phone ON users(phone);
        COMMENT ON COLUMN users.phone IS '手机号码';
    END IF;

    -- 检查并添加bio字段
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                   WHERE table_name='users' AND column_name='bio') THEN
        ALTER TABLE users ADD COLUMN bio TEXT;
        COMMENT ON COLUMN users.bio IS '个人简介';
    END IF;

    -- 检查并添加email_verified字段
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                   WHERE table_name='users' AND column_name='email_verified') THEN
        ALTER TABLE users ADD COLUMN email_verified BOOLEAN DEFAULT FALSE;
        CREATE INDEX idx_users_email_verified ON users(email_verified);
        COMMENT ON COLUMN users.email_verified IS '邮箱是否已验证';
    END IF;

    -- 检查并添加phone_verified字段
    IF NOT EXISTS (SELECT 1 FROM information_schema.columns
                   WHERE table_name='users' AND column_name='phone_verified') THEN
        ALTER TABLE users ADD COLUMN phone_verified BOOLEAN DEFAULT FALSE;
        COMMENT ON COLUMN users.phone_verified IS '手机号是否已验证';
    END IF;
END $$;


-- 6. 创建清理过期token的函数（定时任务调用）
CREATE OR REPLACE FUNCTION cleanup_expired_password_reset_tokens()
RETURNS INTEGER AS $$
DECLARE
    deleted_count INTEGER;
BEGIN
    DELETE FROM password_reset_tokens
    WHERE expires_at < NOW() AND used = FALSE;

    GET DIAGNOSTICS deleted_count = ROW_COUNT;
    RETURN deleted_count;
END;
$$ LANGUAGE plpgsql;

COMMENT ON FUNCTION cleanup_expired_password_reset_tokens IS '清理过期的密码重置令牌（返回删除数量）';
