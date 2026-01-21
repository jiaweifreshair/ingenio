-- 创建交互式分析会话表
-- 用于管理AI深度思考的交互式分析流程

CREATE TABLE IF NOT EXISTS interactive_analysis_sessions (
    session_id VARCHAR(36) PRIMARY KEY COMMENT '会话ID (UUID)',
    user_id BIGINT COMMENT '用户ID',
    requirement TEXT NOT NULL COMMENT '原始需求描述',
    current_step INT NOT NULL DEFAULT 1 COMMENT '当前执行的步骤 (1-6)',
    status VARCHAR(50) NOT NULL DEFAULT 'RUNNING' COMMENT '会话状态: RUNNING/WAITING_CONFIRMATION/COMPLETED/FAILED/CANCELLED',
    step_results JSONB COMMENT '每个步骤的执行结果 (JSON格式)',
    step_feedback JSONB COMMENT '每个步骤的用户反馈 (JSON格式)',
    step_retries JSONB COMMENT '每个步骤的重试次数 (JSON格式)',
    final_result JSONB COMMENT '最终分析结果 (JSON格式)',
    error_message TEXT COMMENT '错误信息',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '更新时间',
    completed_at TIMESTAMP COMMENT '完成时间'
);

-- 创建索引
CREATE INDEX idx_interactive_analysis_sessions_user_id ON interactive_analysis_sessions(user_id);
CREATE INDEX idx_interactive_analysis_sessions_status ON interactive_analysis_sessions(status);
CREATE INDEX idx_interactive_analysis_sessions_created_at ON interactive_analysis_sessions(created_at DESC);

-- 添加注释
COMMENT ON TABLE interactive_analysis_sessions IS '交互式分析会话表 - 管理AI深度思考的交互式分析流程';
COMMENT ON COLUMN interactive_analysis_sessions.session_id IS '会话ID (UUID)';
COMMENT ON COLUMN interactive_analysis_sessions.user_id IS '用户ID';
COMMENT ON COLUMN interactive_analysis_sessions.requirement IS '原始需求描述';
COMMENT ON COLUMN interactive_analysis_sessions.current_step IS '当前执行的步骤 (1-6)';
COMMENT ON COLUMN interactive_analysis_sessions.status IS '会话状态: RUNNING(执行中)/WAITING_CONFIRMATION(等待确认)/COMPLETED(已完成)/FAILED(失败)/CANCELLED(已取消)';
COMMENT ON COLUMN interactive_analysis_sessions.step_results IS '每个步骤的执行结果 (JSON格式, key: 步骤编号, value: 结果对象)';
COMMENT ON COLUMN interactive_analysis_sessions.step_feedback IS '每个步骤的用户反馈 (JSON格式, key: 步骤编号, value: 修改建议)';
COMMENT ON COLUMN interactive_analysis_sessions.step_retries IS '每个步骤的重试次数 (JSON格式, key: 步骤编号, value: 重试次数)';
COMMENT ON COLUMN interactive_analysis_sessions.final_result IS '最终分析结果 (JSON格式)';
COMMENT ON COLUMN interactive_analysis_sessions.error_message IS '错误信息';
COMMENT ON COLUMN interactive_analysis_sessions.created_at IS '创建时间';
COMMENT ON COLUMN interactive_analysis_sessions.updated_at IS '更新时间';
COMMENT ON COLUMN interactive_analysis_sessions.completed_at IS '完成时间';
