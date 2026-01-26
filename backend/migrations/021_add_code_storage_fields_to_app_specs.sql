-- 021_add_code_storage_fields_to_app_specs.sql
-- 添加代码存储字段到app_specs表

-- 添加代码存储字段
ALTER TABLE app_specs ADD COLUMN IF NOT EXISTS frontend_code_url VARCHAR(500);
ALTER TABLE app_specs ADD COLUMN IF NOT EXISTS backend_code_url VARCHAR(500);
ALTER TABLE app_specs ADD COLUMN IF NOT EXISTS code_archive_path VARCHAR(500);
ALTER TABLE app_specs ADD COLUMN IF NOT EXISTS code_commit_hash VARCHAR(100);

-- 添加注释
COMMENT ON COLUMN app_specs.frontend_code_url IS '前端代码仓库地址（GitHub/GitLab）';
COMMENT ON COLUMN app_specs.backend_code_url IS '后端代码仓库地址';
COMMENT ON COLUMN app_specs.code_archive_path IS '代码归档路径（本地存储/OSS）';
COMMENT ON COLUMN app_specs.code_commit_hash IS '代码提交哈希值';

-- 创建索引
CREATE INDEX IF NOT EXISTS idx_app_specs_code_commit_hash ON app_specs(code_commit_hash);
