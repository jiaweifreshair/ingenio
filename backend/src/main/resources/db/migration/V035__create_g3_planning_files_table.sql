-- V035: 创建G3规划文件表
-- G3引擎任务规划增强 - 阶段3
-- 用途：存储基于Manus工作流的规划文件（task_plan/notes/context）

-- 1. G3规划文件表
CREATE TABLE IF NOT EXISTS g3_planning_files (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    -- 关联信息
    job_id UUID NOT NULL,                                -- 所属G3任务ID

    -- 文件信息
    file_type VARCHAR(20) NOT NULL,                      -- 文件类型：task_plan/notes/context
    content TEXT NOT NULL,                               -- 文件内容（Markdown格式）
    version INTEGER DEFAULT 1,                           -- 版本号
    last_updated_by VARCHAR(50),                         -- 最后更新者

    -- 审计字段
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    -- 约束
    CONSTRAINT fk_g3_planning_files_job
        FOREIGN KEY (job_id) REFERENCES g3_jobs(id) ON DELETE CASCADE,
    CONSTRAINT uk_job_file_type UNIQUE (job_id, file_type),
    CONSTRAINT chk_file_type CHECK (file_type IN ('task_plan', 'notes', 'context')),
    CONSTRAINT chk_last_updated_by CHECK (last_updated_by IN ('system', 'architect', 'coder', 'coach', 'user'))
);

-- 2. 创建索引
CREATE INDEX idx_g3_planning_files_job
    ON g3_planning_files(job_id);

CREATE INDEX idx_g3_planning_files_type
    ON g3_planning_files(file_type);

-- 3. 创建触发器（自动更新 updated_at 和 version）
CREATE OR REPLACE FUNCTION update_g3_planning_file_version()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    NEW.version = OLD.version + 1;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER update_g3_planning_files_version
    BEFORE UPDATE ON g3_planning_files
    FOR EACH ROW
    EXECUTE FUNCTION update_g3_planning_file_version();

-- 4. 添加表和列注释
COMMENT ON TABLE g3_planning_files IS 'G3规划文件 - 基于Manus工作流的三文件模式';

COMMENT ON COLUMN g3_planning_files.file_type IS
    '文件类型 - task_plan(任务计划)/notes(笔记)/context(上下文)';

COMMENT ON COLUMN g3_planning_files.content IS
    '文件内容（Markdown格式）- 支持表格、代码块等Markdown语法';

COMMENT ON COLUMN g3_planning_files.version IS
    '版本号 - 每次更新自动递增，用于追踪变更历史';

COMMENT ON COLUMN g3_planning_files.last_updated_by IS
    '最后更新者 - system/architect/coder/coach/user';
