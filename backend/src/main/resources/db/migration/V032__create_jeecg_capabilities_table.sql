-- V032: 创建JeecgBoot能力清单表
-- G3引擎JeecgBoot能力集成 - 阶段1
-- 用途：存储JeecgBoot平台提供的可集成能力（登录、支付、短信、OSS等）

-- 1. JeecgBoot能力清单表
CREATE TABLE IF NOT EXISTS jeecg_capabilities (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    -- 基础信息
    code VARCHAR(50) NOT NULL UNIQUE,                    -- 能力代码（唯一标识）
    name VARCHAR(100) NOT NULL,                          -- 能力名称（中文）
    description TEXT,                                    -- 详细描述
    category VARCHAR(50) NOT NULL,                       -- 分类：infrastructure/business/third_party
    icon VARCHAR(100),                                   -- 图标名称或URL

    -- JeecgBoot集成信息
    endpoint_prefix VARCHAR(200),                        -- API端点前缀
    apis JSONB,                                          -- API接口列表

    -- 配置模板
    config_template JSONB,                               -- 用户配置模板

    -- 代码生成模板
    code_templates JSONB,                                -- 代码生成模板

    -- 依赖关系
    dependencies JSONB,                                  -- 依赖的其他能力
    conflicts JSONB,                                     -- 互斥的能力

    -- 版本和状态
    version VARCHAR(20) DEFAULT '1.0.0',                 -- 能力版本号
    is_active BOOLEAN DEFAULT true,                      -- 是否启用
    sort_order INTEGER DEFAULT 100,                      -- 排序权重

    -- 文档和帮助
    doc_url VARCHAR(500),                                -- 文档链接
    examples JSONB,                                      -- 使用示例

    -- 审计字段
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 2. 创建索引
CREATE INDEX idx_jeecg_capabilities_category
    ON jeecg_capabilities(category)
    WHERE is_active = true;

CREATE INDEX idx_jeecg_capabilities_code
    ON jeecg_capabilities(code);

CREATE INDEX idx_jeecg_capabilities_sort
    ON jeecg_capabilities(sort_order)
    WHERE is_active = true;

-- 3. 创建触发器（自动更新 updated_at）
CREATE TRIGGER update_jeecg_capabilities_updated_at
    BEFORE UPDATE ON jeecg_capabilities
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();

-- 4. 添加表和列注释
COMMENT ON TABLE jeecg_capabilities IS 'JeecgBoot能力清单 - G3引擎能力集成';

COMMENT ON COLUMN jeecg_capabilities.code IS
    '能力代码（唯一标识）- 如：auth, payment_alipay, sms_aliyun';

COMMENT ON COLUMN jeecg_capabilities.category IS
    '能力分类 - infrastructure(基础设施)/business(业务能力)/third_party(第三方集成)';

COMMENT ON COLUMN jeecg_capabilities.config_template IS
    '配置模板（JSONB）- 定义用户需要填写的配置项及其类型、是否加密等';

COMMENT ON COLUMN jeecg_capabilities.code_templates IS
    '代码生成模板（JSONB）- 包含依赖、配置类、服务接口等代码模板';

COMMENT ON COLUMN jeecg_capabilities.dependencies IS
    '依赖能力（JSONB数组）- 如支付能力依赖认证能力：["auth"]';

COMMENT ON COLUMN jeecg_capabilities.conflicts IS
    '互斥能力（JSONB数组）- 如支付宝和微信支付互斥：["payment_wechat"]';
