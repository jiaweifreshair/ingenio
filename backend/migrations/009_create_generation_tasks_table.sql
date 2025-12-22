-- 创建生成任务表
CREATE TABLE generation_tasks (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL,
    user_id UUID NOT NULL,
    task_name VARCHAR(255) NOT NULL,
    user_requirement TEXT NOT NULL,

    -- 任务状态和进度
    status VARCHAR(50) NOT NULL DEFAULT 'pending',
    current_agent VARCHAR(50),
    progress INTEGER DEFAULT 0,

    -- Agent状态信息（JSON格式存储详细信息）
    agents_info JSONB,

    -- 执行结果
    plan_result JSONB,
    app_spec_content JSONB,
    validate_result JSONB,

    -- 最终输出
    app_spec_id UUID,
    quality_score INTEGER,
    download_url VARCHAR(500),
    preview_url VARCHAR(500),

    -- Token使用统计
    token_usage JSONB,

    -- 错误信息
    error_message TEXT,

    -- 时间信息
    started_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    completed_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT NOW(),

    -- 元数据
    metadata JSONB
);

-- 创建索引
CREATE INDEX idx_generation_tasks_tenant_id ON generation_tasks(tenant_id);
CREATE INDEX idx_generation_tasks_user_id ON generation_tasks(user_id);
CREATE INDEX idx_generation_tasks_status ON generation_tasks(status);
CREATE INDEX idx_generation_tasks_created_at ON generation_tasks(created_at);
CREATE INDEX idx_generation_tasks_app_spec_id ON generation_tasks(app_spec_id);

-- 添加约束
ALTER TABLE generation_tasks ADD CONSTRAINT chk_generation_tasks_status
    CHECK (status IN ('pending', 'planning', 'executing', 'validating', 'generating', 'completed', 'failed', 'cancelled'));

ALTER TABLE generation_tasks ADD CONSTRAINT chk_generation_tasks_progress
    CHECK (progress >= 0 AND progress <= 100);

ALTER TABLE generation_tasks ADD CONSTRAINT chk_generation_tasks_quality_score
    CHECK (quality_score >= 0 AND quality_score <= 100);

-- 添加外键约束（如果app_specs表存在）
DO $$
BEGIN
    IF EXISTS (SELECT 1 FROM information_schema.tables WHERE table_name = 'app_specs') THEN
        ALTER TABLE generation_tasks
        ADD CONSTRAINT fk_generation_tasks_app_spec_id
        FOREIGN KEY (app_spec_id) REFERENCES app_specs(id) ON DELETE SET NULL;
    END IF;
END
$$;

-- 添加注释
COMMENT ON TABLE generation_tasks IS 'AI生成任务表，存储从需求到代码的完整生成过程状态';
COMMENT ON COLUMN generation_tasks.id IS '任务唯一标识';
COMMENT ON COLUMN generation_tasks.tenant_id IS '租户ID，用于多租户隔离';
COMMENT ON COLUMN generation_tasks.user_id IS '用户ID';
COMMENT ON COLUMN generation_tasks.task_name IS '任务名称';
COMMENT ON COLUMN generation_tasks.user_requirement IS '用户的原始需求描述';
COMMENT ON COLUMN generation_tasks.status IS '任务状态：pending/planning/executing/validating/generating/completed/failed/cancelled';
COMMENT ON COLUMN generation_tasks.current_agent IS '当前执行的Agent：plan/execute/validate/generate';
COMMENT ON COLUMN generation_tasks.progress IS '任务进度百分比（0-100）';
COMMENT ON COLUMN generation_tasks.agents_info IS 'Agent状态信息JSON，包含每个Agent的详细状态';
COMMENT ON COLUMN generation_tasks.plan_result IS 'PlanAgent的执行结果';
COMMENT ON COLUMN generation_tasks.app_spec_content IS 'ExecuteAgent生成的AppSpec内容';
COMMENT ON COLUMN generation_tasks.validate_result IS 'ValidateAgent的验证结果';
COMMENT ON COLUMN generation_tasks.app_spec_id IS '最终生成的AppSpec记录ID';
COMMENT ON COLUMN generation_tasks.quality_score IS 'AppSpec质量评分（0-100）';
COMMENT ON COLUMN generation_tasks.download_url IS '生成代码的下载链接';
COMMENT ON COLUMN generation_tasks.preview_url IS '应用预览链接';
COMMENT ON COLUMN generation_tasks.token_usage IS 'Token使用统计信息';
COMMENT ON COLUMN generation_tasks.error_message IS '任务失败时的错误信息';
COMMENT ON COLUMN generation_tasks.started_at IS '任务开始执行时间';
COMMENT ON COLUMN generation_tasks.completed_at IS '任务完成时间';
COMMENT ON COLUMN generation_tasks.metadata IS '任务元数据JSON';