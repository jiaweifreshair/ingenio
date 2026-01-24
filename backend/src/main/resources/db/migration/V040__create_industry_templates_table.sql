-- V021: 创建行业模板库表
-- Phase X.4: 行业模板库开发 - 支持基于关键词和分类的模板匹配
-- 用途：存储40+行业应用模板，提供意图识别后的模板推荐功能

-- 1. 行业模板表
CREATE TABLE IF NOT EXISTS industry_templates (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    -- 基础信息
    name VARCHAR(200) NOT NULL,                           -- 模板名称（如"民宿预订平台模板"）
    description TEXT NOT NULL,                            -- 详细描述
    category VARCHAR(100) NOT NULL,                       -- 一级分类（电商/教育/社交/生活服务等）
    subcategory VARCHAR(100),                             -- 二级分类（综合电商/垂直电商等）

    -- 匹配关键词（用于模板匹配算法）
    keywords JSONB NOT NULL,                              -- 关键词数组，如 ["民宿", "预订", "airbnb"]

    -- 参考来源
    reference_url VARCHAR(500),                           -- 参考网站URL（如 https://airbnb.com）
    thumbnail_url VARCHAR(500),                           -- 模板预览图URL

    -- 业务定义（核心字段）
    entities JSONB NOT NULL,                              -- 预定义实体列表，格式：[{name, attributes, description}]
    features JSONB NOT NULL,                              -- 核心功能清单，格式：["用户注册", "房源浏览", "预订支付"]
    workflows JSONB,                                      -- 业务流程定义，格式：[{name, steps, description}]

    -- 技术栈建议
    tech_stack JSONB,                                     -- 技术栈建议，格式：{frontend: [], backend: [], database: []}

    -- 复杂度和估算
    complexity_score INTEGER DEFAULT 5                    -- 复杂度评分 1-10
        CHECK (complexity_score BETWEEN 1 AND 10),
    estimated_hours INTEGER,                              -- 预估开发工时（小时）

    -- 使用统计
    usage_count INTEGER DEFAULT 0,                        -- 使用次数统计
    rating DECIMAL(3,2)                                   -- 用户评分 0-5
        CHECK (rating IS NULL OR rating BETWEEN 0 AND 5),

    -- 状态管理
    is_active BOOLEAN DEFAULT true,                       -- 是否启用

    -- 审计字段
    created_by VARCHAR(100),                              -- 创建人
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 2. 创建索引（优化查询性能）

-- GIN索引：支持JSONB数组的高效查询（关键词匹配）
CREATE INDEX IF NOT EXISTS idx_industry_templates_keywords
    ON industry_templates USING GIN (keywords);

-- 复合索引：支持分类浏览
CREATE INDEX IF NOT EXISTS idx_industry_templates_category
    ON industry_templates (category, subcategory)
    WHERE is_active = true;

-- 排序索引：支持按使用次数排序（热门模板）
CREATE INDEX IF NOT EXISTS idx_industry_templates_usage_count
    ON industry_templates (usage_count DESC)
    WHERE is_active = true;

-- 排序索引：支持按评分排序（优质模板）
CREATE INDEX IF NOT EXISTS idx_industry_templates_rating
    ON industry_templates (rating DESC NULLS LAST)
    WHERE is_active = true AND rating IS NOT NULL;

-- 3. 创建触发器（自动更新 updated_at）
-- 说明：兼容旧版初始化脚本已创建触发器的场景，避免重复创建失败
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_trigger
        WHERE tgname = 'update_industry_templates_updated_at'
          AND tgrelid = 'industry_templates'::regclass
    ) THEN
        CREATE TRIGGER update_industry_templates_updated_at
            BEFORE UPDATE ON industry_templates
            FOR EACH ROW
            EXECUTE FUNCTION update_updated_at_column();
    END IF;
END $$;

-- 4. 添加表和列注释（便于维护）
COMMENT ON TABLE industry_templates IS '行业应用模板库 - Phase X.4';

COMMENT ON COLUMN industry_templates.keywords IS
    '关键词数组（JSONB格式）- 用于模板匹配算法，支持GIN索引快速查询。示例：["民宿", "预订", "住宿", "airbnb"]';

COMMENT ON COLUMN industry_templates.entities IS
    '预定义实体列表（JSONB格式）- 格式：[{name: "User", attributes: [{name: "email", type: "string"}], description: "用户实体"}]';

COMMENT ON COLUMN industry_templates.features IS
    '核心功能清单（JSONB格式）- 格式：["用户注册登录", "房源浏览搜索", "在线预订支付", "评价反馈"]';

COMMENT ON COLUMN industry_templates.workflows IS
    '业务流程定义（JSONB格式）- 格式：[{name: "预订流程", steps: ["选择日期", "确认房源", "支付订金"], description: "..."}]';

COMMENT ON COLUMN industry_templates.tech_stack IS
    '技术栈建议（JSONB格式）- 格式：{frontend: ["React", "Next.js"], backend: ["Spring Boot", "PostgreSQL"], database: ["PostgreSQL"]}';

COMMENT ON COLUMN industry_templates.complexity_score IS
    '复杂度评分（1-10）- 1=极简单（单表CRUD），5=中等（多表关联），10=极复杂（分布式系统）';

COMMENT ON COLUMN industry_templates.usage_count IS
    '使用次数统计 - 每次被用户选择使用时自动递增，用于计算热门模板';

COMMENT ON COLUMN industry_templates.rating IS
    '用户评分（0-5）- 基于使用该模板的用户反馈计算的平均评分';

-- 5. 初始化系统配置
-- 预留：后续 Task 6 将初始化 40+ 行业模板数据
