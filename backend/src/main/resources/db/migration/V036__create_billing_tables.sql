-- =====================================================
-- V036: 创建计费系统相关表
-- 功能：用户余额管理、支付订单、余额变动记录
-- =====================================================

-- 用户余额表：记录用户的生成次数余额
CREATE TABLE IF NOT EXISTS user_credits (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    -- 用户关联
    user_id UUID NOT NULL,

    -- 余额信息
    total_credits INTEGER NOT NULL DEFAULT 0,        -- 总购买次数
    used_credits INTEGER NOT NULL DEFAULT 0,         -- 已使用次数

    -- 审计字段
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    -- 约束
    CONSTRAINT fk_user_credits_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT uk_user_credits_user UNIQUE (user_id),
    CONSTRAINT chk_credits_non_negative CHECK (total_credits >= 0 AND used_credits >= 0),
    CONSTRAINT chk_used_not_exceed_total CHECK (used_credits <= total_credits)
);

-- 索引
CREATE INDEX idx_user_credits_user ON user_credits(user_id);

-- 注释
COMMENT ON TABLE user_credits IS '用户余额表：记录用户的生成次数余额';
COMMENT ON COLUMN user_credits.total_credits IS '总购买次数';
COMMENT ON COLUMN user_credits.used_credits IS '已使用次数';

-- =====================================================
-- 支付订单表：记录所有支付订单
-- =====================================================
CREATE TABLE IF NOT EXISTS pay_orders (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    -- 订单基本信息
    order_no VARCHAR(64) NOT NULL,                   -- 业务订单号（唯一）
    user_id UUID NOT NULL,                           -- 用户ID

    -- 套餐信息
    package_code VARCHAR(32) NOT NULL,               -- 套餐编码：PACK_10/PACK_30/PACK_80
    package_name VARCHAR(100) NOT NULL,              -- 套餐名称
    credits_amount INTEGER NOT NULL,                 -- 购买次数

    -- 金额信息
    amount DECIMAL(10,2) NOT NULL,                   -- 订单金额（元）
    currency VARCHAR(10) DEFAULT 'CNY',              -- 货币类型

    -- 支付信息
    pay_channel VARCHAR(32) NOT NULL,                -- 支付渠道：ALIPAY_PC/ALIPAY_WAP
    pay_data_type VARCHAR(20),                       -- 支付数据类型：URL/FORM/QR
    pay_data TEXT,                                   -- 支付数据（二维码URL/表单等）
    transaction_id VARCHAR(128),                     -- 第三方支付流水号

    -- 状态信息
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',   -- 状态：PENDING/PAID/FAILED/CANCELLED/EXPIRED

    -- 时间信息
    expire_time TIMESTAMP,                           -- 订单过期时间
    pay_time TIMESTAMP,                              -- 支付成功时间

    -- 审计字段
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    -- 约束
    CONSTRAINT fk_pay_orders_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT uk_pay_orders_order_no UNIQUE (order_no),
    CONSTRAINT chk_pay_orders_status CHECK (status IN ('PENDING', 'PAID', 'FAILED', 'CANCELLED', 'EXPIRED')),
    CONSTRAINT chk_pay_orders_channel CHECK (pay_channel IN ('ALIPAY_PC', 'ALIPAY_WAP')),
    CONSTRAINT chk_pay_orders_package CHECK (package_code IN ('PACK_10', 'PACK_30', 'PACK_80'))
);

-- 索引
CREATE INDEX idx_pay_orders_user ON pay_orders(user_id);
CREATE INDEX idx_pay_orders_status ON pay_orders(status);
CREATE INDEX idx_pay_orders_created ON pay_orders(created_at);

-- 注释
COMMENT ON TABLE pay_orders IS '支付订单表：记录所有支付订单';
COMMENT ON COLUMN pay_orders.order_no IS '业务订单号（唯一）';
COMMENT ON COLUMN pay_orders.package_code IS '套餐编码：PACK_10(199元10次)/PACK_30(399元30次)/PACK_80(800元80次)';
COMMENT ON COLUMN pay_orders.status IS '订单状态：PENDING-待支付/PAID-已支付/FAILED-失败/CANCELLED-取消/EXPIRED-过期';

-- =====================================================
-- 余额变动记录表：记录每次余额变动
-- =====================================================
CREATE TABLE IF NOT EXISTS credit_transactions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    -- 关联信息
    user_id UUID NOT NULL,
    order_id UUID,                                   -- 关联订单（充值时有值）

    -- 变动信息
    type VARCHAR(20) NOT NULL,                       -- 类型：PURCHASE/CONSUME/REFUND/GIFT
    credits_change INTEGER NOT NULL,                 -- 变动数量（正数增加，负数减少）
    credits_before INTEGER NOT NULL,                 -- 变动前余额
    credits_after INTEGER NOT NULL,                  -- 变动后余额

    -- 描述信息
    description VARCHAR(255),                        -- 变动描述
    reference_id VARCHAR(128),                       -- 关联业务ID（如appSpecId）

    -- 审计字段
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    -- 约束
    CONSTRAINT fk_credit_trans_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_credit_trans_order FOREIGN KEY (order_id) REFERENCES pay_orders(id) ON DELETE SET NULL,
    CONSTRAINT chk_credit_trans_type CHECK (type IN ('PURCHASE', 'CONSUME', 'REFUND', 'GIFT'))
);

-- 索引
CREATE INDEX idx_credit_trans_user ON credit_transactions(user_id);
CREATE INDEX idx_credit_trans_created ON credit_transactions(created_at);

-- 注释
COMMENT ON TABLE credit_transactions IS '余额变动记录表：记录每次余额变动';
COMMENT ON COLUMN credit_transactions.type IS '变动类型：PURCHASE-购买/CONSUME-消费/REFUND-退款/GIFT-赠送';
COMMENT ON COLUMN credit_transactions.credits_change IS '变动数量（正数增加，负数减少）';
