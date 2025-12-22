-- 多模态输入记录表迁移
-- 支持文本/语音/视频/图像输入

CREATE TABLE IF NOT EXISTS multimodal_inputs (
  -- 主键和标识
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  
  -- 租户隔离
  tenant_id UUID NOT NULL,
  
  -- 关联用户和AppSpec
  user_id UUID NOT NULL,
  app_spec_id UUID,
  
  -- 输入类型
  input_type VARCHAR(20) NOT NULL,
  
  -- 文本输入内容
  text_content TEXT,
  
  -- 文件信息
  file_url VARCHAR(500),
  file_size BIGINT,
  file_mime_type VARCHAR(100),
  
  -- 处理结果
  transcript TEXT,
  analysis_result JSONB,
  
  -- 处理状态
  processing_status VARCHAR(20) NOT NULL DEFAULT 'pending',
  error_message TEXT,
  
  -- 审计字段
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  deleted INTEGER DEFAULT 0,
  
  -- 元数据
  metadata JSONB NOT NULL DEFAULT '{}'::jsonb,
  
  -- 约束
  CONSTRAINT chk_multimodal_input_type 
    CHECK (input_type IN ('text', 'voice', 'video', 'image')),
  CONSTRAINT chk_multimodal_processing_status 
    CHECK (processing_status IN ('pending', 'processing', 'completed', 'failed'))
);

-- 创建索引
CREATE INDEX idx_multimodal_inputs_tenant ON multimodal_inputs(tenant_id);
CREATE INDEX idx_multimodal_inputs_user ON multimodal_inputs(user_id);
CREATE INDEX idx_multimodal_inputs_appspec ON multimodal_inputs(app_spec_id);
CREATE INDEX idx_multimodal_inputs_type ON multimodal_inputs(input_type);
CREATE INDEX idx_multimodal_inputs_status ON multimodal_inputs(processing_status);
CREATE INDEX idx_multimodal_inputs_created ON multimodal_inputs(created_at DESC);

-- GIN索引用于JSONB查询
CREATE INDEX idx_multimodal_analysis ON multimodal_inputs USING GIN (analysis_result);

-- 创建更新时间触发器
CREATE TRIGGER update_multimodal_inputs_updated_at
  BEFORE UPDATE ON multimodal_inputs
  FOR EACH ROW
  EXECUTE FUNCTION update_updated_at_column();

-- 添加外键约束
ALTER TABLE multimodal_inputs
  ADD CONSTRAINT fk_multimodal_inputs_user
  FOREIGN KEY (user_id)
  REFERENCES users(id)
  ON DELETE CASCADE;

ALTER TABLE multimodal_inputs
  ADD CONSTRAINT fk_multimodal_inputs_appspec
  FOREIGN KEY (app_spec_id)
  REFERENCES app_specs(id)
  ON DELETE SET NULL;

-- 添加注释
COMMENT ON TABLE multimodal_inputs IS '多模态输入记录表 - 支持文本/语音/视频/图像输入';
COMMENT ON COLUMN multimodal_inputs.id IS '输入记录ID（UUID）';
COMMENT ON COLUMN multimodal_inputs.tenant_id IS '租户ID - 用于租户隔离';
COMMENT ON COLUMN multimodal_inputs.user_id IS '用户ID';
COMMENT ON COLUMN multimodal_inputs.app_spec_id IS '关联的AppSpec ID（可为空）';
COMMENT ON COLUMN multimodal_inputs.input_type IS '输入类型：text/voice/video/image';
COMMENT ON COLUMN multimodal_inputs.text_content IS '文本输入内容';
COMMENT ON COLUMN multimodal_inputs.file_url IS '文件URL（MinIO）';
COMMENT ON COLUMN multimodal_inputs.file_size IS '文件大小（字节）';
COMMENT ON COLUMN multimodal_inputs.file_mime_type IS '文件MIME类型';
COMMENT ON COLUMN multimodal_inputs.transcript IS '语音转文字结果或OCR结果';
COMMENT ON COLUMN multimodal_inputs.analysis_result IS 'AI分析结果（JSON）';
COMMENT ON COLUMN multimodal_inputs.processing_status IS '处理状态：pending/processing/completed/failed';
COMMENT ON COLUMN multimodal_inputs.error_message IS '错误信息';
COMMENT ON COLUMN multimodal_inputs.created_at IS '创建时间';
COMMENT ON COLUMN multimodal_inputs.updated_at IS '更新时间';
COMMENT ON COLUMN multimodal_inputs.deleted IS '逻辑删除标记（0未删除/1已删除）';
COMMENT ON COLUMN multimodal_inputs.metadata IS '元数据（JSON）';
