-- 网站抓取任务表迁移
-- 从现有网站抓取内容生成AppSpec

CREATE TABLE IF NOT EXISTS scrape_tasks (
  -- 主键和标识
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  
  -- 租户隔离
  tenant_id UUID NOT NULL,
  
  -- 关联用户和AppSpec
  user_id UUID NOT NULL,
  app_spec_id UUID,
  
  -- 抓取目标
  target_url VARCHAR(500) NOT NULL,
  scrape_type VARCHAR(20) NOT NULL DEFAULT 'full',
  
  -- 抓取配置
  scrape_config JSONB NOT NULL DEFAULT '{}'::jsonb,
  
  -- 抓取状态
  scrape_status VARCHAR(20) NOT NULL DEFAULT 'pending',
  
  -- 抓取结果
  result_data JSONB,
  error_message TEXT,
  
  -- 时间记录
  started_at TIMESTAMP,
  completed_at TIMESTAMP,
  duration_ms INTEGER,
  
  -- 审计字段
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  deleted INTEGER DEFAULT 0,
  
  -- 元数据
  metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
  
  -- 约束
  CONSTRAINT chk_scrape_type 
    CHECK (scrape_type IN ('full', 'screenshot', 'metadata')),
  CONSTRAINT chk_scrape_status 
    CHECK (scrape_status IN ('pending', 'running', 'completed', 'failed'))
);

-- 创建索引
CREATE INDEX idx_scrape_tasks_tenant ON scrape_tasks(tenant_id);
CREATE INDEX idx_scrape_tasks_user ON scrape_tasks(user_id);
CREATE INDEX idx_scrape_tasks_appspec ON scrape_tasks(app_spec_id);
CREATE INDEX idx_scrape_tasks_status ON scrape_tasks(scrape_status);
CREATE INDEX idx_scrape_tasks_created ON scrape_tasks(created_at DESC);
CREATE INDEX idx_scrape_tasks_url ON scrape_tasks(target_url);

-- GIN索引
CREATE INDEX idx_scrape_result_data ON scrape_tasks USING GIN (result_data);
CREATE INDEX idx_scrape_config ON scrape_tasks USING GIN (scrape_config);

-- 创建更新时间触发器
CREATE TRIGGER update_scrape_tasks_updated_at
  BEFORE UPDATE ON scrape_tasks
  FOR EACH ROW
  EXECUTE FUNCTION update_updated_at_column();

-- 添加外键约束
ALTER TABLE scrape_tasks
  ADD CONSTRAINT fk_scrape_tasks_user
  FOREIGN KEY (user_id)
  REFERENCES users(id)
  ON DELETE CASCADE;

ALTER TABLE scrape_tasks
  ADD CONSTRAINT fk_scrape_tasks_appspec
  FOREIGN KEY (app_spec_id)
  REFERENCES app_specs(id)
  ON DELETE CASCADE;

-- 添加注释
COMMENT ON TABLE scrape_tasks IS '网站抓取任务表 - 从现有网站抓取内容生成AppSpec';
COMMENT ON COLUMN scrape_tasks.id IS '抓取任务ID（UUID）';
COMMENT ON COLUMN scrape_tasks.tenant_id IS '租户ID';
COMMENT ON COLUMN scrape_tasks.user_id IS '用户ID';
COMMENT ON COLUMN scrape_tasks.app_spec_id IS '关联的AppSpec ID';
COMMENT ON COLUMN scrape_tasks.target_url IS '抓取目标URL';
COMMENT ON COLUMN scrape_tasks.scrape_type IS '抓取类型：full/screenshot/metadata';
COMMENT ON COLUMN scrape_tasks.scrape_config IS '抓取配置（JSON）';
COMMENT ON COLUMN scrape_tasks.scrape_status IS '抓取状态：pending/running/completed/failed';
COMMENT ON COLUMN scrape_tasks.result_data IS '抓取结果（JSON）';
COMMENT ON COLUMN scrape_tasks.error_message IS '错误信息';
COMMENT ON COLUMN scrape_tasks.started_at IS '开始时间';
COMMENT ON COLUMN scrape_tasks.completed_at IS '完成时间';
COMMENT ON COLUMN scrape_tasks.duration_ms IS '执行时长（毫秒）';
