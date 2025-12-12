-- V2.0 Phase 3-4: 创建验证结果和修复记录表
-- 功能：三环验证框架 + AI自动修复
-- 作者：Ingenio Team
-- 日期：2025-11-22

-- ==================== validation_results（验证结果表）====================
CREATE TABLE IF NOT EXISTS validation_results (
    -- 主键和关联
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    app_spec_id UUID NOT NULL REFERENCES app_specs(id) ON DELETE CASCADE,

    -- 验证类型和状态
    validation_type VARCHAR(50) NOT NULL, -- compile, test, quality_gate, contract, schema, business_flow, full
    status VARCHAR(20) NOT NULL,          -- running, passed, failed, skipped
    is_passed BOOLEAN NOT NULL DEFAULT FALSE,

    -- 验证详情
    validation_details JSONB,             -- 验证结果详情（JSON格式）
    error_messages JSONB,                 -- 错误消息列表（JSON数组）
    warning_messages JSONB,               -- 警告消息列表（JSON数组）

    -- 质量评分和时间
    quality_score INTEGER,                -- 0-100分
    started_at TIMESTAMP WITH TIME ZONE,
    completed_at TIMESTAMP WITH TIME ZONE,
    duration_ms BIGINT,

    -- 元数据
    metadata JSONB,

    -- 审计字段
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 索引优化
CREATE INDEX idx_validation_results_app_spec_id ON validation_results(app_spec_id);
CREATE INDEX idx_validation_results_tenant_id ON validation_results(tenant_id);
CREATE INDEX idx_validation_results_validation_type ON validation_results(validation_type);
CREATE INDEX idx_validation_results_status ON validation_results(status);
CREATE INDEX idx_validation_results_created_at ON validation_results(created_at DESC);

-- 注释
COMMENT ON TABLE validation_results IS 'V2.0 验证结果表 - 记录三环验证（编译→测试→业务）的完整结果';
COMMENT ON COLUMN validation_results.validation_type IS '验证类型：compile, test, quality_gate, contract, schema, business_flow, full';
COMMENT ON COLUMN validation_results.status IS '验证状态：running, passed, failed, skipped';
COMMENT ON COLUMN validation_results.quality_score IS '质量评分（0-100）';

-- ==================== repair_records（修复记录表）====================
CREATE TABLE IF NOT EXISTS repair_records (
    -- 主键和关联
    id UUID PRIMARY KEY,
    tenant_id UUID NOT NULL,
    app_spec_id UUID NOT NULL REFERENCES app_specs(id) ON DELETE CASCADE,
    validation_result_id UUID REFERENCES validation_results(id) ON DELETE SET NULL,

    -- 失败类型和修复状态
    failure_type VARCHAR(50) NOT NULL,    -- compile, test, type_error, dependency, business_logic
    status VARCHAR(20) NOT NULL,          -- pending, analyzing, repairing, validating, success, failed, escalated
    current_iteration INTEGER NOT NULL DEFAULT 0,
    max_iterations INTEGER NOT NULL DEFAULT 3,

    -- 修复策略和建议
    repair_strategy VARCHAR(50),          -- type_inference, dependency_install, code_refactor, business_logic_fix, ai_suggestion
    error_details JSONB,                  -- 错误详情（JSON格式）
    repair_suggestions JSONB,             -- AI生成的修复建议列表（JSON数组）
    selected_suggestion_index INTEGER,    -- 选中的修复方案索引

    -- 代码变更
    code_changes JSONB,                   -- 修复后的代码变更（JSON格式）
    affected_files JSONB,                 -- 受影响的文件列表（JSON数组）
    repair_validation_result_id UUID REFERENCES validation_results(id) ON DELETE SET NULL,

    -- 修复结果
    is_success BOOLEAN NOT NULL DEFAULT FALSE,
    failure_reason TEXT,

    -- 人工介入
    is_escalated BOOLEAN NOT NULL DEFAULT FALSE,
    escalated_at TIMESTAMP WITH TIME ZONE,
    notification_sent BOOLEAN NOT NULL DEFAULT FALSE,

    -- AI相关
    ai_reasoning JSONB,                   -- AI推理过程（JSON格式）
    token_usage JSONB,                    -- Token使用量统计（JSON格式）

    -- 时间统计
    started_at TIMESTAMP WITH TIME ZONE,
    completed_at TIMESTAMP WITH TIME ZONE,
    duration_ms BIGINT,

    -- 元数据
    metadata JSONB,

    -- 审计字段
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 索引优化
CREATE INDEX idx_repair_records_app_spec_id ON repair_records(app_spec_id);
CREATE INDEX idx_repair_records_tenant_id ON repair_records(tenant_id);
CREATE INDEX idx_repair_records_validation_result_id ON repair_records(validation_result_id);
CREATE INDEX idx_repair_records_status ON repair_records(status);
CREATE INDEX idx_repair_records_failure_type ON repair_records(failure_type);
CREATE INDEX idx_repair_records_is_escalated ON repair_records(is_escalated);
CREATE INDEX idx_repair_records_created_at ON repair_records(created_at DESC);

-- 注释
COMMENT ON TABLE repair_records IS 'V2.0 AI自动修复记录表 - 记录AI修复的完整历程（最多3次迭代）';
COMMENT ON COLUMN repair_records.failure_type IS '失败类型：compile, test, type_error, dependency, business_logic';
COMMENT ON COLUMN repair_records.status IS '修复状态：pending, analyzing, repairing, validating, success, failed, escalated';
COMMENT ON COLUMN repair_records.repair_strategy IS '修复策略：type_inference, dependency_install, code_refactor, business_logic_fix, ai_suggestion';
COMMENT ON COLUMN repair_records.current_iteration IS '当前迭代次数（1-3）';
COMMENT ON COLUMN repair_records.is_escalated IS '是否已升级人工介入';

-- ==================== 统计视图 ====================

-- 验证通过率统计视图
CREATE OR REPLACE VIEW v_validation_pass_rate AS
SELECT
    app_spec_id,
    COUNT(*) AS total_validations,
    SUM(CASE WHEN is_passed THEN 1 ELSE 0 END) AS passed_count,
    ROUND(SUM(CASE WHEN is_passed THEN 1 ELSE 0 END)::DECIMAL / COUNT(*), 2) AS pass_rate,
    AVG(quality_score) AS avg_quality_score
FROM validation_results
GROUP BY app_spec_id;

COMMENT ON VIEW v_validation_pass_rate IS '验证通过率统计视图';

-- 修复成功率统计视图
CREATE OR REPLACE VIEW v_repair_success_rate AS
SELECT
    app_spec_id,
    COUNT(*) AS total_repairs,
    SUM(CASE WHEN is_success THEN 1 ELSE 0 END) AS success_count,
    ROUND(SUM(CASE WHEN is_success THEN 1 ELSE 0 END)::DECIMAL / COUNT(*), 2) AS success_rate,
    SUM(CASE WHEN is_escalated THEN 1 ELSE 0 END) AS escalated_count,
    AVG(current_iteration) AS avg_iterations
FROM repair_records
GROUP BY app_spec_id;

COMMENT ON VIEW v_repair_success_rate IS '修复成功率统计视图';
