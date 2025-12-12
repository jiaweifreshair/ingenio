-- V020: 创建微信用户绑定表
-- 用于支持微信扫码登录功能，存储微信用户信息和系统用户的绑定关系

-- 1. 微信用户绑定表
CREATE TABLE IF NOT EXISTS wx_user_bindings (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL,                            -- 关联的系统用户ID
    wx_openid VARCHAR(128) NOT NULL UNIQUE,           -- 微信OpenID（唯一标识）
    wx_unionid VARCHAR(128),                          -- 微信UnionID（同一主体多应用统一）
    wx_nickname VARCHAR(100),                         -- 微信昵称
    wx_avatar_url VARCHAR(500),                       -- 微信头像URL
    wx_country VARCHAR(50),                           -- 国家
    wx_province VARCHAR(50),                          -- 省份
    wx_city VARCHAR(50),                              -- 城市
    wx_sex INTEGER DEFAULT 0,                         -- 性别（0:未知, 1:男, 2:女）
    wx_language VARCHAR(20),                          -- 语言
    bind_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,  -- 绑定时间
    last_login_time TIMESTAMP,                        -- 最后登录时间
    login_count INTEGER DEFAULT 0,                    -- 登录次数
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_wx_user_bindings_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

-- 2. 索引优化
CREATE INDEX idx_wx_user_bindings_user ON wx_user_bindings(user_id);
CREATE INDEX idx_wx_user_bindings_openid ON wx_user_bindings(wx_openid);
CREATE INDEX idx_wx_user_bindings_unionid ON wx_user_bindings(wx_unionid);
CREATE INDEX idx_wx_user_bindings_last_login ON wx_user_bindings(last_login_time);

-- 3. 触发器：自动更新updated_at
CREATE TRIGGER update_wx_user_bindings_updated_at
    BEFORE UPDATE ON wx_user_bindings
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

-- 4. 表注释
COMMENT ON TABLE wx_user_bindings IS '微信用户绑定表';
COMMENT ON COLUMN wx_user_bindings.user_id IS '关联的系统用户ID';
COMMENT ON COLUMN wx_user_bindings.wx_openid IS '微信OpenID（唯一标识，每个应用不同）';
COMMENT ON COLUMN wx_user_bindings.wx_unionid IS '微信UnionID（同一开放平台下多个应用统一ID）';
COMMENT ON COLUMN wx_user_bindings.wx_nickname IS '微信昵称';
COMMENT ON COLUMN wx_user_bindings.wx_avatar_url IS '微信头像URL';
COMMENT ON COLUMN wx_user_bindings.wx_sex IS '性别（0:未知, 1:男, 2:女）';
COMMENT ON COLUMN wx_user_bindings.bind_time IS '首次绑定时间';
COMMENT ON COLUMN wx_user_bindings.last_login_time IS '最后一次微信登录时间';
COMMENT ON COLUMN wx_user_bindings.login_count IS '微信登录总次数（统计用）';

-- 5. 初始化数据（测试环境可选）
-- 注意：生产环境不需要初始化数据，用户首次扫码时自动创建绑定关系
