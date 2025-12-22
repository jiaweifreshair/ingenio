-- 生成代码记录表迁移
-- 存储KuiklyUI渲染器生成的代码文件信息

-- 创建生成代码表
CREATE TABLE IF NOT EXISTS generated_code (
  -- 主键和标识
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

  -- 关联AppSpec
  app_spec_id UUID NOT NULL,

  -- 项目标识
  project_id VARCHAR(100) NOT NULL UNIQUE,
  -- 格式：{tenant_id}_{timestamp}_{random}

  -- 渲染器信息
  renderer_type VARCHAR(50) NOT NULL DEFAULT 'kuikly-ui',
  -- kuikly-ui: KuiklyUI/Taro渲染器
  -- react-next: React/Next.js渲染器（未来扩展）
  -- vue-vite: Vue/Vite渲染器（未来扩展）

  framework VARCHAR(20) NOT NULL DEFAULT 'taro',
  -- taro: Taro 3.x
  -- next: Next.js（未来扩展）
  -- nuxt: Nuxt.js（未来扩展）

  -- 存储信息
  storage_key VARCHAR(500) NOT NULL,
  -- MinIO存储路径：projects/{project_id}/code.zip

  -- 文件统计
  file_count INTEGER NOT NULL DEFAULT 0,
  total_size_bytes BIGINT NOT NULL DEFAULT 0,

  -- 构建状态
  build_status VARCHAR(50) NOT NULL DEFAULT 'pending',
  -- pending: 等待构建
  -- building: 构建中
  -- success: 构建成功
  -- failed: 构建失败

  build_log TEXT,
  -- 构建日志（如有错误）

  -- 预览URL
  preview_url VARCHAR(500),
  -- 格式：http://preview.ingenio.local/{project_id}

  -- 审计字段
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

  -- 元数据
  metadata JSONB NOT NULL DEFAULT '{}'::jsonb
  -- 包含：依赖版本、构建配置等
);

-- 创建索引
CREATE INDEX idx_generated_code_app_spec_id ON generated_code(app_spec_id);
CREATE INDEX idx_generated_code_project_id ON generated_code(project_id);
CREATE INDEX idx_generated_code_renderer_type ON generated_code(renderer_type);
CREATE INDEX idx_generated_code_build_status ON generated_code(build_status);
CREATE INDEX idx_generated_code_created_at ON generated_code(created_at DESC);

-- GIN索引用于JSONB查询
CREATE INDEX idx_generated_code_metadata ON generated_code USING GIN (metadata);

-- 创建更新时间触发器
CREATE TRIGGER update_generated_code_updated_at
  BEFORE UPDATE ON generated_code
  FOR EACH ROW
  EXECUTE FUNCTION update_updated_at_column();

-- 添加外键约束
ALTER TABLE generated_code
  ADD CONSTRAINT fk_generated_code_app_spec
  FOREIGN KEY (app_spec_id)
  REFERENCES app_specs(id)
  ON DELETE CASCADE;

-- 添加约束
ALTER TABLE generated_code
  ADD CONSTRAINT chk_generated_code_renderer_type
  CHECK (renderer_type IN ('kuikly-ui', 'react-next', 'vue-vite'));

ALTER TABLE generated_code
  ADD CONSTRAINT chk_generated_code_framework
  CHECK (framework IN ('taro', 'next', 'nuxt'));

ALTER TABLE generated_code
  ADD CONSTRAINT chk_generated_code_build_status
  CHECK (build_status IN ('pending', 'building', 'success', 'failed'));

ALTER TABLE generated_code
  ADD CONSTRAINT chk_generated_code_file_count
  CHECK (file_count >= 0);

ALTER TABLE generated_code
  ADD CONSTRAINT chk_generated_code_total_size
  CHECK (total_size_bytes >= 0);

-- 添加注释
COMMENT ON TABLE generated_code IS '生成代码记录表 - 存储KuiklyUI渲染器生成的代码文件信息';
COMMENT ON COLUMN generated_code.id IS '记录ID（UUID）';
COMMENT ON COLUMN generated_code.app_spec_id IS '关联的AppSpec ID';
COMMENT ON COLUMN generated_code.project_id IS '项目标识（唯一）';
COMMENT ON COLUMN generated_code.renderer_type IS '渲染器类型：kuikly-ui/react-next/vue-vite';
COMMENT ON COLUMN generated_code.framework IS '框架类型：taro/next/nuxt';
COMMENT ON COLUMN generated_code.storage_key IS 'MinIO存储路径';
COMMENT ON COLUMN generated_code.file_count IS '文件数量';
COMMENT ON COLUMN generated_code.total_size_bytes IS '总大小（字节）';
COMMENT ON COLUMN generated_code.build_status IS '构建状态：pending/building/success/failed';
COMMENT ON COLUMN generated_code.build_log IS '构建日志';
COMMENT ON COLUMN generated_code.preview_url IS '预览URL';
COMMENT ON COLUMN generated_code.created_at IS '创建时间';
COMMENT ON COLUMN generated_code.updated_at IS '更新时间';
COMMENT ON COLUMN generated_code.metadata IS '元数据（JSON）';
