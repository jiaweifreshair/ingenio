-- =====================================================
-- V037: 初始化 justin 账号的计费数据
-- 功能：为 justin 用户初始化100次生成次数和一条订单记录
-- =====================================================

-- 为 justin 用户初始化余额（100次）
INSERT INTO user_credits (user_id, total_credits, used_credits)
SELECT id, 100, 0
FROM users
WHERE username = 'justin'
ON CONFLICT (user_id) DO UPDATE SET
    total_credits = 100,
    used_credits = 0,
    updated_at = CURRENT_TIMESTAMP;

-- 创建一条已支付的订单记录
INSERT INTO pay_orders (
    order_no,
    user_id,
    package_code,
    package_name,
    credits_amount,
    amount,
    currency,
    pay_channel,
    status,
    pay_time
)
SELECT
    'INIT-' || TO_CHAR(CURRENT_TIMESTAMP, 'YYYYMMDDHH24MISS') || '-001',
    id,
    'PACK_80',
    '80次套餐（初始化赠送）',
    100,
    0.00,
    'CNY',
    'ALIPAY_PC',
    'PAID',
    CURRENT_TIMESTAMP
FROM users
WHERE username = 'justin';

-- 创建余额变动记录
INSERT INTO credit_transactions (
    user_id,
    order_id,
    type,
    credits_change,
    credits_before,
    credits_after,
    description
)
SELECT
    u.id,
    p.id,
    'GIFT',
    100,
    0,
    100,
    '系统初始化赠送100次生成次数'
FROM users u
JOIN pay_orders p ON p.user_id = u.id
WHERE u.username = 'justin'
AND p.order_no LIKE 'INIT-%'
ORDER BY p.created_at DESC
LIMIT 1;
