-- V025: 创建G3引擎核心表
-- 用于存储G3（Generate-Check-Fix）引擎的任务和产物
-- 支持契约驱动开发和自修复工作流

-- ==========================================
-- 1. G3 Jobs（G3任务主表）
-- 存储每个代码生成任务的状态和配置
-- ==========================================
CREATE TABLE IF NOT EXISTS g3_jobs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    -- 关联字段
    app_spec_id UUID,                                    -- 关联的应用规格ID（可选）
    tenant_id UUID,                                       -- 租户ID（多租户支持）
    user_id UUID,                                         -- 创建用户ID

    -- 任务配置
    requirement TEXT NOT NULL,                            -- 原始需求文本
    status VARCHAR(50) NOT NULL DEFAULT 'QUEUED',         -- 任务状态：QUEUED/PLANNING/CODING/TESTING/COMPLETED/FAILED
    current_round INTEGER NOT NULL DEFAULT 0,             -- 当前修复轮次（0表示首次生成）
    max_rounds INTEGER NOT NULL DEFAULT 3,                -- 最大修复轮次限制

    -- 契约相关
    contract_yaml TEXT,                                   -- OpenAPI契约YAML（由Architect生成）
    db_schema_sql TEXT,                                   -- 数据库Schema SQL（由Architect生成）
    contract_locked BOOLEAN DEFAULT FALSE,                -- 契约锁定状态（锁定后不再修改）
    contract_locked_at TIMESTAMP,                         -- 契约锁定时间

    -- 技术栈配置
    target_stack JSONB DEFAULT '{"backend": "spring-boot", "frontend": "react", "database": "postgresql"}'::jsonb,
    generation_options JSONB DEFAULT '{}'::jsonb,         -- 生成选项（如是否生成测试、是否启用缓存等）

    -- 执行日志（嵌入式存储，避免高频JOIN）
    logs JSONB DEFAULT '[]'::jsonb,                       -- 执行日志数组，格式：[{timestamp, role, message, level}]

    -- E2B沙箱配置
    sandbox_id VARCHAR(100),                              -- E2B沙箱实例ID
    sandbox_url VARCHAR(500),                             -- 沙箱访问URL
    sandbox_provider VARCHAR(50) DEFAULT 'e2b',           -- 沙箱提供商（e2b/docker/local）

    -- 错误追踪
    last_error TEXT,                                      -- 最近一次错误信息
    error_count INTEGER DEFAULT 0,                        -- 累计错误次数

    -- 时间戳
    started_at TIMESTAMP,                                 -- 任务开始执行时间
    completed_at TIMESTAMP,                               -- 任务完成时间
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    -- 外键约束
    CONSTRAINT fk_g3_jobs_app_spec FOREIGN KEY (app_spec_id) REFERENCES app_specs(id) ON DELETE SET NULL,
    CONSTRAINT fk_g3_jobs_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id) ON DELETE CASCADE,
    CONSTRAINT fk_g3_jobs_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE SET NULL
);

-- G3 Jobs 索引
CREATE INDEX idx_g3_jobs_app_spec ON g3_jobs(app_spec_id);
CREATE INDEX idx_g3_jobs_tenant ON g3_jobs(tenant_id);
CREATE INDEX idx_g3_jobs_user ON g3_jobs(user_id);
CREATE INDEX idx_g3_jobs_status ON g3_jobs(status);
CREATE INDEX idx_g3_jobs_created_at ON g3_jobs(created_at DESC);

-- G3 Jobs 触发器：自动更新updated_at
CREATE TRIGGER update_g3_jobs_updated_at
    BEFORE UPDATE ON g3_jobs
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

COMMENT ON TABLE g3_jobs IS 'G3引擎任务主表 - 存储代码生成任务的状态、配置和日志';
COMMENT ON COLUMN g3_jobs.status IS '任务状态：QUEUED(排队)/PLANNING(规划)/CODING(编码)/TESTING(测试)/COMPLETED(完成)/FAILED(失败)';
COMMENT ON COLUMN g3_jobs.current_round IS '当前修复轮次，0表示首次生成，每次Coach修复后+1';
COMMENT ON COLUMN g3_jobs.contract_yaml IS 'OpenAPI 3.0契约文档（YAML格式），由Architect Agent生成';
COMMENT ON COLUMN g3_jobs.logs IS '执行日志数组，格式：[{timestamp, role, message, level}]，role可为PLAYER/COACH/EXECUTOR/ARCHITECT';


-- ==========================================
-- 2. G3 Artifacts（G3产物表）
-- 存储每轮生成的代码产物
-- ==========================================
CREATE TABLE IF NOT EXISTS g3_artifacts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    -- 关联字段
    job_id UUID NOT NULL,                                 -- 关联的G3任务ID

    -- 产物信息
    artifact_type VARCHAR(50) NOT NULL,                   -- 产物类型：CONTRACT/ENTITY/MAPPER/SERVICE/CONTROLLER/CONFIG/TEST/FRONTEND
    file_path VARCHAR(500) NOT NULL,                      -- 文件相对路径（如 src/main/java/com/example/entity/User.java）
    file_name VARCHAR(200) NOT NULL,                      -- 文件名（如 User.java）
    content TEXT NOT NULL,                                -- 文件内容
    language VARCHAR(20) NOT NULL DEFAULT 'java',         -- 编程语言：java/typescript/sql/yaml

    -- 版本控制
    version INTEGER NOT NULL DEFAULT 1,                   -- 产物版本号（每次修复+1）
    checksum VARCHAR(64),                                 -- 内容SHA256校验和
    parent_artifact_id UUID,                              -- 父版本ID（用于追溯修复历史）

    -- 验证状态
    has_errors BOOLEAN DEFAULT FALSE,                     -- 是否有编译/运行错误
    compiler_output TEXT,                                 -- 编译器输出（错误信息）
    validated_at TIMESTAMP,                               -- 验证时间

    -- 生成信息
    generated_by VARCHAR(50),                             -- 生成者：ARCHITECT/BACKEND_CODER/FRONTEND_CODER/COACH
    generation_round INTEGER,                             -- 生成轮次（0=首次，>0=修复轮次）

    -- 时间戳
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    -- 外键约束
    CONSTRAINT fk_g3_artifacts_job FOREIGN KEY (job_id) REFERENCES g3_jobs(id) ON DELETE CASCADE,
    CONSTRAINT fk_g3_artifacts_parent FOREIGN KEY (parent_artifact_id) REFERENCES g3_artifacts(id) ON DELETE SET NULL
);

-- G3 Artifacts 索引
CREATE INDEX idx_g3_artifacts_job ON g3_artifacts(job_id);
CREATE INDEX idx_g3_artifacts_type ON g3_artifacts(artifact_type);
CREATE INDEX idx_g3_artifacts_has_errors ON g3_artifacts(has_errors);
CREATE INDEX idx_g3_artifacts_file_path ON g3_artifacts(file_path);
CREATE INDEX idx_g3_artifacts_generation_round ON g3_artifacts(generation_round);

-- G3 Artifacts 触发器
CREATE TRIGGER update_g3_artifacts_updated_at
    BEFORE UPDATE ON g3_artifacts
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

COMMENT ON TABLE g3_artifacts IS 'G3引擎产物表 - 存储每轮生成的代码文件';
COMMENT ON COLUMN g3_artifacts.artifact_type IS '产物类型：CONTRACT(契约)/ENTITY(实体)/MAPPER(映射)/SERVICE(服务)/CONTROLLER(控制器)/CONFIG(配置)/TEST(测试)/FRONTEND(前端)';
COMMENT ON COLUMN g3_artifacts.version IS '产物版本号，首次生成为1，每次Coach修复后版本号+1';
COMMENT ON COLUMN g3_artifacts.parent_artifact_id IS '父版本产物ID，用于追溯修复历史链';
COMMENT ON COLUMN g3_artifacts.generated_by IS '生成者Agent：ARCHITECT/BACKEND_CODER/FRONTEND_CODER/COACH';


-- ==========================================
-- 3. G3 Validation Results（验证结果表）
-- 存储每次沙箱验证的详细结果
-- ==========================================
CREATE TABLE IF NOT EXISTS g3_validation_results (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    -- 关联字段
    job_id UUID NOT NULL,                                 -- 关联的G3任务ID
    round INTEGER NOT NULL,                               -- 验证轮次

    -- 验证结果
    validation_type VARCHAR(50) NOT NULL,                 -- 验证类型：COMPILE/UNIT_TEST/INTEGRATION_TEST/RUNTIME
    passed BOOLEAN NOT NULL DEFAULT FALSE,                -- 是否通过

    -- 详细信息
    command VARCHAR(500),                                 -- 执行的命令（如 mvn compile）
    exit_code INTEGER,                                    -- 命令退出码
    stdout TEXT,                                          -- 标准输出
    stderr TEXT,                                          -- 错误输出
    duration_ms INTEGER,                                  -- 执行耗时（毫秒）

    -- 解析后的错误
    parsed_errors JSONB DEFAULT '[]'::jsonb,              -- 解析后的错误列表：[{file, line, column, message, severity}]
    error_count INTEGER DEFAULT 0,                        -- 错误数量
    warning_count INTEGER DEFAULT 0,                      -- 警告数量

    -- 时间戳
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    -- 外键约束
    CONSTRAINT fk_g3_validation_results_job FOREIGN KEY (job_id) REFERENCES g3_jobs(id) ON DELETE CASCADE
);

-- G3 Validation Results 索引
CREATE INDEX idx_g3_validation_results_job ON g3_validation_results(job_id);
CREATE INDEX idx_g3_validation_results_round ON g3_validation_results(round);
CREATE INDEX idx_g3_validation_results_type ON g3_validation_results(validation_type);
CREATE INDEX idx_g3_validation_results_passed ON g3_validation_results(passed);

COMMENT ON TABLE g3_validation_results IS 'G3引擎验证结果表 - 存储每次沙箱验证的详细结果';
COMMENT ON COLUMN g3_validation_results.validation_type IS '验证类型：COMPILE(编译)/UNIT_TEST(单元测试)/INTEGRATION_TEST(集成测试)/RUNTIME(运行时)';
COMMENT ON COLUMN g3_validation_results.parsed_errors IS '解析后的错误列表，格式：[{file, line, column, message, severity}]';
