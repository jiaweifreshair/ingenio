-- V038: 更新AI Agent能力配置以支持Uniaix
-- 添加Uniaix作为AI Agent类型选项

-- 更新ai_agent_connect能力的配置模板，添加Uniaix选项
UPDATE jeecg_capabilities
SET config_template = '{
    "agentType": {
        "type": "select",
        "required": true,
        "label": "智能体类型",
        "default": "UNIAIX",
        "options": [
            {"label": "Uniaix统一接口", "value": "UNIAIX"},
            {"label": "OpenAI兼容", "value": "OPENAI"},
            {"label": "AgentScope", "value": "AGENT_SCOPE"},
            {"label": "外部URL", "value": "EXTERNAL_URL"}
        ],
        "encrypted": false
    },
    "baseUrl": {
        "type": "string",
        "required": true,
        "label": "服务地址",
        "placeholder": "https://api.uniaix.com",
        "encrypted": false
    },
    "apiKey": {
        "type": "password",
        "required": true,
        "label": "API Key",
        "placeholder": "请输入API Key",
        "encrypted": true
    },
    "model": {
        "type": "string",
        "required": false,
        "label": "模型名称",
        "default": "claude-3-5-sonnet-20241022",
        "placeholder": "claude-3-5-sonnet-20241022, gpt-4-turbo, gemini-pro等",
        "encrypted": false
    },
    "promptTemplate": {
        "type": "textarea",
        "required": false,
        "label": "提示词模板",
        "placeholder": "你是一个${role}...",
        "encrypted": false
    }
}'::jsonb,
description = 'JeecgBoot AI智能体集成能力，支持Uniaix统一接口（161+模型）、AgentScope、Dify、Coze或OpenAI兼容接口。Uniaix提供统一访问Anthropic、Google、OpenAI等多个AI提供商。支持流式对话、工具调用等高级特性。'
WHERE code = 'ai_agent_connect';

-- 添加表注释
COMMENT ON TABLE jeecg_capabilities IS 'JeecgBoot能力清单 - 已更新ai_agent_connect支持Uniaix统一接口';
