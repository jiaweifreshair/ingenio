-- =====================================================
-- V029: 创建审计日志表
-- 用途：记录系统关键操作，支持 JeecgBoot 集成审计
-- =====================================================

-- 审计日志表
CREATE TABLE IF NOT EXISTS audit_logs (
    -- 主键
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    -- 租户隔离
    tenant_id VARCHAR(64),

    -- 链路追踪
    trace_id VARCHAR(64),

    -- 操作人信息
    actor_id VARCHAR(64) NOT NULL,
    actor_type VARCHAR(32) NOT NULL DEFAULT 'USER',

    -- 操作信息
    action VARCHAR(64) NOT NULL,
    resource_type VARCHAR(64),
    resource_id VARCHAR(128),

    -- 状态变更（JSONB）
    before_state JSONB,
    after_state JSONB,

    -- 结果信息
    result VARCHAR(32) NOT NULL DEFAULT 'SUCCESS',
    error_message TEXT,

    -- 请求上下文
    client_ip VARCHAR(64),
    user_agent VARCHAR(512),
    request_path VARCHAR(512),
    request_method VARCHAR(16),

    -- 扩展数据
    extra_data JSONB,

    -- 时间戳
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- 索引：按租户+时间查询
CREATE INDEX IF NOT EXISTS idx_audit_logs_tenant_time
    ON audit_logs(tenant_id, created_at DESC);

-- 索引：按操作人查询
CREATE INDEX IF NOT EXISTS idx_audit_logs_actor
    ON audit_logs(actor_id, created_at DESC);

-- 索引：按动作类型查询
CREATE INDEX IF NOT EXISTS idx_audit_logs_action
    ON audit_logs(action, created_at DESC);

-- 索引：按资源查询
CREATE INDEX IF NOT EXISTS idx_audit_logs_resource
    ON audit_logs(resource_type, resource_id);

-- 索引：按链路ID查询
CREATE INDEX IF NOT EXISTS idx_audit_logs_trace
    ON audit_logs(trace_id);

-- 添加表注释
COMMENT ON TABLE audit_logs IS '审计日志表 - 记录系统关键操作';
COMMENT ON COLUMN audit_logs.id IS '审计日志ID';
COMMENT ON COLUMN audit_logs.tenant_id IS '租户ID';
COMMENT ON COLUMN audit_logs.trace_id IS '链路追踪ID';
COMMENT ON COLUMN audit_logs.actor_id IS '操作人ID';
COMMENT ON COLUMN audit_logs.actor_type IS '操作人类型: USER/ADMIN/SERVICE/SYSTEM';
COMMENT ON COLUMN audit_logs.action IS '操作动作: RESOURCE_ACTION 格式';
COMMENT ON COLUMN audit_logs.resource_type IS '资源类型: USER/TEMPLATE/PROMPT等';
COMMENT ON COLUMN audit_logs.resource_id IS '资源ID';
COMMENT ON COLUMN audit_logs.before_state IS '操作前状态(JSONB)';
COMMENT ON COLUMN audit_logs.after_state IS '操作后状态(JSONB)';
COMMENT ON COLUMN audit_logs.result IS '操作结果: SUCCESS/FAILURE/PARTIAL';
COMMENT ON COLUMN audit_logs.error_message IS '错误消息';
COMMENT ON COLUMN audit_logs.client_ip IS '客户端IP';
COMMENT ON COLUMN audit_logs.user_agent IS 'User-Agent';
COMMENT ON COLUMN audit_logs.request_path IS '请求路径';
COMMENT ON COLUMN audit_logs.request_method IS '请求方法';
COMMENT ON COLUMN audit_logs.extra_data IS '扩展数据(JSONB)';
COMMENT ON COLUMN audit_logs.created_at IS '创建时间';
