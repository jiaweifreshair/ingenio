-- V033: 初始化JeecgBoot基础能力数据
-- G3引擎JeecgBoot能力集成 - 阶段1
-- 用途：初始化基础能力数据，包括认证、支付、短信、OSS等

-- ==================== 基础设施类能力 ====================

-- 1. 用户认证能力
INSERT INTO jeecg_capabilities (
    code, name, description, category, icon, endpoint_prefix,
    apis, config_template, code_templates, dependencies, conflicts,
    version, is_active, sort_order, doc_url
) VALUES (
    'auth',
    '用户认证',
    'JeecgBoot用户认证能力，提供登录、登出、Token刷新等功能。支持用户名密码登录、短信验证码登录、OAuth2.0第三方登录。',
    'infrastructure',
    'key',
    '/sys/auth',
    '[
        {"method": "POST", "path": "/login", "description": "用户登录", "requestBody": {"username": "string", "password": "string"}, "responseBody": {"token": "string", "userInfo": "object"}},
        {"method": "POST", "path": "/logout", "description": "用户登出"},
        {"method": "POST", "path": "/refresh-token", "description": "刷新Token", "requestBody": {"refreshToken": "string"}, "responseBody": {"token": "string"}},
        {"method": "GET", "path": "/user-info", "description": "获取当前用户信息", "responseBody": {"id": "string", "username": "string", "roles": "array"}}
    ]'::jsonb,
    '{
        "jeecgBaseUrl": {"type": "string", "required": true, "label": "JeecgBoot服务地址", "placeholder": "http://localhost:8080", "encrypted": false},
        "tokenExpireTime": {"type": "string", "required": false, "label": "Token过期时间(秒)", "default": "7200", "encrypted": false}
    }'::jsonb,
    '{
        "dependencies": [],
        "configClass": "JeecgAuthConfig",
        "serviceInterface": "AuthService",
        "serviceImpl": "JeecgAuthServiceImpl",
        "imports": ["org.springframework.web.client.RestTemplate", "com.fasterxml.jackson.databind.ObjectMapper"]
    }'::jsonb,
    '[]'::jsonb,
    '[]'::jsonb,
    '1.0.0',
    true,
    10,
    'https://doc.jeecg.com/3671877'
);

-- ==================== 业务能力类 ====================

-- 2. 支付宝支付能力
INSERT INTO jeecg_capabilities (
    code, name, description, category, icon, endpoint_prefix,
    apis, config_template, code_templates, dependencies, conflicts,
    version, is_active, sort_order, doc_url
) VALUES (
    'payment_alipay',
    '支付宝支付',
    'JeecgBoot支付宝支付能力，支持扫码支付、APP支付、网页支付等多种支付方式。集成支付宝开放平台SDK。',
    'business',
    'credit-card',
    '/payment/alipay',
    '[
        {"method": "POST", "path": "/create-order", "description": "创建支付订单", "requestBody": {"orderId": "string", "amount": "number", "subject": "string"}, "responseBody": {"payUrl": "string", "qrCode": "string"}},
        {"method": "POST", "path": "/query-order", "description": "查询订单状态", "requestBody": {"orderId": "string"}, "responseBody": {"status": "string", "paidAt": "string"}},
        {"method": "POST", "path": "/refund", "description": "退款", "requestBody": {"orderId": "string", "refundAmount": "number"}, "responseBody": {"refundId": "string", "status": "string"}},
        {"method": "POST", "path": "/notify", "description": "支付回调通知"}
    ]'::jsonb,
    '{
        "appId": {"type": "string", "required": true, "label": "应用ID", "placeholder": "请输入支付宝应用ID", "encrypted": false},
        "privateKey": {"type": "password", "required": true, "label": "应用私钥", "placeholder": "请输入RSA私钥", "encrypted": true},
        "alipayPublicKey": {"type": "password", "required": true, "label": "支付宝公钥", "placeholder": "请输入支付宝公钥", "encrypted": true},
        "notifyUrl": {"type": "string", "required": true, "label": "回调通知地址", "placeholder": "https://your-domain.com/api/payment/alipay/notify", "encrypted": false},
        "sandbox": {"type": "boolean", "required": false, "label": "沙箱模式", "default": true, "encrypted": false}
    }'::jsonb,
    '{
        "dependencies": [{"groupId": "com.alipay.sdk", "artifactId": "alipay-sdk-java", "version": "4.38.0.ALL"}],
        "configClass": "AlipayConfig",
        "serviceInterface": "PaymentService",
        "serviceImpl": "AlipayPaymentServiceImpl",
        "imports": ["com.alipay.api.AlipayClient", "com.alipay.api.DefaultAlipayClient", "com.alipay.api.request.AlipayTradePagePayRequest"]
    }'::jsonb,
    '["auth"]'::jsonb,
    '[]'::jsonb,
    '1.0.0',
    true,
    20,
    'https://doc.jeecg.com/3671890'
);

-- 3. 微信支付能力
INSERT INTO jeecg_capabilities (
    code, name, description, category, icon, endpoint_prefix,
    apis, config_template, code_templates, dependencies, conflicts,
    version, is_active, sort_order, doc_url
) VALUES (
    'payment_wechat',
    '微信支付',
    'JeecgBoot微信支付能力，支持JSAPI支付、Native支付、APP支付、H5支付等多种支付方式。集成微信支付V3 API。',
    'business',
    'smartphone',
    '/payment/wechat',
    '[
        {"method": "POST", "path": "/create-order", "description": "创建支付订单", "requestBody": {"orderId": "string", "amount": "number", "description": "string"}, "responseBody": {"prepayId": "string", "codeUrl": "string"}},
        {"method": "POST", "path": "/query-order", "description": "查询订单状态", "requestBody": {"orderId": "string"}, "responseBody": {"tradeState": "string", "paidAt": "string"}},
        {"method": "POST", "path": "/refund", "description": "退款", "requestBody": {"orderId": "string", "refundAmount": "number"}, "responseBody": {"refundId": "string", "status": "string"}},
        {"method": "POST", "path": "/notify", "description": "支付回调通知"}
    ]'::jsonb,
    '{
        "appId": {"type": "string", "required": true, "label": "应用ID", "placeholder": "请输入微信应用ID", "encrypted": false},
        "mchId": {"type": "string", "required": true, "label": "商户号", "placeholder": "请输入微信商户号", "encrypted": false},
        "apiKey": {"type": "password", "required": true, "label": "API密钥", "placeholder": "请输入API v3密钥", "encrypted": true},
        "certPath": {"type": "file", "required": true, "label": "商户证书", "placeholder": "上传apiclient_cert.p12文件", "encrypted": false},
        "notifyUrl": {"type": "string", "required": true, "label": "回调通知地址", "placeholder": "https://your-domain.com/api/payment/wechat/notify", "encrypted": false}
    }'::jsonb,
    '{
        "dependencies": [{"groupId": "com.github.wechatpay-apiv3", "artifactId": "wechatpay-java", "version": "0.2.12"}],
        "configClass": "WechatPayConfig",
        "serviceInterface": "PaymentService",
        "serviceImpl": "WechatPaymentServiceImpl",
        "imports": ["com.wechat.pay.java.core.Config", "com.wechat.pay.java.service.payments.nativepay.NativePayService"]
    }'::jsonb,
    '["auth"]'::jsonb,
    '[]'::jsonb,
    '1.0.0',
    true,
    21,
    'https://doc.jeecg.com/3671891'
);

-- ==================== 第三方集成类能力 ====================

-- 4. 阿里云短信能力
INSERT INTO jeecg_capabilities (
    code, name, description, category, icon, endpoint_prefix,
    apis, config_template, code_templates, dependencies, conflicts,
    version, is_active, sort_order, doc_url
) VALUES (
    'sms_aliyun',
    '阿里云短信',
    'JeecgBoot阿里云短信能力，支持发送验证码、通知短信、营销短信等。集成阿里云短信服务SDK。',
    'third_party',
    'message-square',
    '/sms/aliyun',
    '[
        {"method": "POST", "path": "/send", "description": "发送短信", "requestBody": {"phone": "string", "templateCode": "string", "templateParam": "object"}, "responseBody": {"bizId": "string", "code": "string"}},
        {"method": "POST", "path": "/send-batch", "description": "批量发送短信", "requestBody": {"phones": "array", "templateCode": "string", "templateParams": "array"}, "responseBody": {"bizId": "string"}},
        {"method": "GET", "path": "/query-status", "description": "查询发送状态", "requestBody": {"bizId": "string", "phone": "string"}, "responseBody": {"sendStatus": "string", "receiveDate": "string"}}
    ]'::jsonb,
    '{
        "accessKeyId": {"type": "string", "required": true, "label": "AccessKey ID", "placeholder": "请输入阿里云AccessKey ID", "encrypted": false},
        "accessKeySecret": {"type": "password", "required": true, "label": "AccessKey Secret", "placeholder": "请输入阿里云AccessKey Secret", "encrypted": true},
        "signName": {"type": "string", "required": true, "label": "短信签名", "placeholder": "请输入短信签名，如：阿里云", "encrypted": false},
        "regionId": {"type": "select", "required": false, "label": "区域", "default": "cn-hangzhou", "options": [{"label": "华东1(杭州)", "value": "cn-hangzhou"}, {"label": "华北2(北京)", "value": "cn-beijing"}], "encrypted": false}
    }'::jsonb,
    '{
        "dependencies": [{"groupId": "com.aliyun", "artifactId": "alibabacloud-dysmsapi20170525", "version": "2.0.24"}],
        "configClass": "AliyunSmsConfig",
        "serviceInterface": "SmsService",
        "serviceImpl": "AliyunSmsServiceImpl",
        "imports": ["com.aliyun.dysmsapi20170525.Client", "com.aliyun.dysmsapi20170525.models.SendSmsRequest"]
    }'::jsonb,
    '[]'::jsonb,
    '[]'::jsonb,
    '1.0.0',
    true,
    30,
    'https://doc.jeecg.com/3671895'
);

-- 5. 阿里云OSS能力
INSERT INTO jeecg_capabilities (
    code, name, description, category, icon, endpoint_prefix,
    apis, config_template, code_templates, dependencies, conflicts,
    version, is_active, sort_order, doc_url
) VALUES (
    'oss_aliyun',
    '阿里云OSS',
    'JeecgBoot阿里云OSS能力，支持文件上传、下载、删除等操作。支持图片处理、CDN加速等高级功能。',
    'third_party',
    'cloud',
    '/oss/aliyun',
    '[
        {"method": "POST", "path": "/upload", "description": "上传文件", "requestBody": {"file": "file", "path": "string"}, "responseBody": {"url": "string", "key": "string"}},
        {"method": "GET", "path": "/download", "description": "下载文件", "requestBody": {"key": "string"}, "responseBody": {"url": "string"}},
        {"method": "DELETE", "path": "/delete", "description": "删除文件", "requestBody": {"key": "string"}},
        {"method": "POST", "path": "/presigned-url", "description": "获取预签名URL", "requestBody": {"key": "string", "expiration": "number"}, "responseBody": {"url": "string"}}
    ]'::jsonb,
    '{
        "endpoint": {"type": "string", "required": true, "label": "Endpoint", "placeholder": "oss-cn-hangzhou.aliyuncs.com", "encrypted": false},
        "accessKeyId": {"type": "string", "required": true, "label": "AccessKey ID", "placeholder": "请输入阿里云AccessKey ID", "encrypted": false},
        "accessKeySecret": {"type": "password", "required": true, "label": "AccessKey Secret", "placeholder": "请输入阿里云AccessKey Secret", "encrypted": true},
        "bucketName": {"type": "string", "required": true, "label": "Bucket名称", "placeholder": "请输入OSS Bucket名称", "encrypted": false},
        "customDomain": {"type": "string", "required": false, "label": "自定义域名", "placeholder": "cdn.your-domain.com", "encrypted": false}
    }'::jsonb,
    '{
        "dependencies": [{"groupId": "com.aliyun.oss", "artifactId": "aliyun-sdk-oss", "version": "3.17.4"}],
        "configClass": "AliyunOssConfig",
        "serviceInterface": "OssService",
        "serviceImpl": "AliyunOssServiceImpl",
        "imports": ["com.aliyun.oss.OSS", "com.aliyun.oss.OSSClientBuilder", "com.aliyun.oss.model.PutObjectRequest"]
    }'::jsonb,
    '[]'::jsonb,
    '["oss_minio"]'::jsonb,
    '1.0.0',
    true,
    31,
    'https://doc.jeecg.com/3671896'
);

-- 6. MinIO存储能力
INSERT INTO jeecg_capabilities (
    code, name, description, category, icon, endpoint_prefix,
    apis, config_template, code_templates, dependencies, conflicts,
    version, is_active, sort_order, doc_url
) VALUES (
    'oss_minio',
    'MinIO存储',
    'JeecgBoot MinIO存储能力，私有化部署的对象存储服务。兼容S3协议，适合内网环境使用。',
    'third_party',
    'hard-drive',
    '/oss/minio',
    '[
        {"method": "POST", "path": "/upload", "description": "上传文件", "requestBody": {"file": "file", "path": "string"}, "responseBody": {"url": "string", "key": "string"}},
        {"method": "GET", "path": "/download", "description": "下载文件", "requestBody": {"key": "string"}, "responseBody": {"url": "string"}},
        {"method": "DELETE", "path": "/delete", "description": "删除文件", "requestBody": {"key": "string"}},
        {"method": "POST", "path": "/presigned-url", "description": "获取预签名URL", "requestBody": {"key": "string", "expiration": "number"}, "responseBody": {"url": "string"}}
    ]'::jsonb,
    '{
        "endpoint": {"type": "string", "required": true, "label": "MinIO地址", "placeholder": "http://localhost:9000", "encrypted": false},
        "accessKey": {"type": "string", "required": true, "label": "Access Key", "placeholder": "请输入MinIO Access Key", "encrypted": false},
        "secretKey": {"type": "password", "required": true, "label": "Secret Key", "placeholder": "请输入MinIO Secret Key", "encrypted": true},
        "bucketName": {"type": "string", "required": true, "label": "Bucket名称", "placeholder": "请输入Bucket名称", "encrypted": false}
    }'::jsonb,
    '{
        "dependencies": [{"groupId": "io.minio", "artifactId": "minio", "version": "8.5.7"}],
        "configClass": "MinioConfig",
        "serviceInterface": "OssService",
        "serviceImpl": "MinioOssServiceImpl",
        "imports": ["io.minio.MinioClient", "io.minio.PutObjectArgs", "io.minio.GetPresignedObjectUrlArgs"]
    }'::jsonb,
    '[]'::jsonb,
    '["oss_aliyun"]'::jsonb,
    '1.0.0',
    true,
    32,
    'https://doc.jeecg.com/3671897'
);

-- 7. 邮件服务能力
INSERT INTO jeecg_capabilities (
    code, name, description, category, icon, endpoint_prefix,
    apis, config_template, code_templates, dependencies, conflicts,
    version, is_active, sort_order, doc_url
) VALUES (
    'email',
    '邮件服务',
    'JeecgBoot邮件服务能力，支持发送HTML邮件、附件邮件、模板邮件等。基于Spring Mail实现。',
    'third_party',
    'mail',
    '/email',
    '[
        {"method": "POST", "path": "/send", "description": "发送邮件", "requestBody": {"to": "string", "subject": "string", "content": "string", "html": "boolean"}, "responseBody": {"messageId": "string"}},
        {"method": "POST", "path": "/send-template", "description": "发送模板邮件", "requestBody": {"to": "string", "templateCode": "string", "params": "object"}, "responseBody": {"messageId": "string"}},
        {"method": "POST", "path": "/send-attachment", "description": "发送带附件邮件", "requestBody": {"to": "string", "subject": "string", "content": "string", "attachments": "array"}, "responseBody": {"messageId": "string"}}
    ]'::jsonb,
    '{
        "host": {"type": "string", "required": true, "label": "SMTP服务器", "placeholder": "smtp.example.com", "encrypted": false},
        "port": {"type": "string", "required": true, "label": "端口", "placeholder": "465", "encrypted": false},
        "username": {"type": "string", "required": true, "label": "用户名", "placeholder": "your-email@example.com", "encrypted": false},
        "password": {"type": "password", "required": true, "label": "密码/授权码", "placeholder": "请输入邮箱密码或授权码", "encrypted": true},
        "sslEnabled": {"type": "boolean", "required": false, "label": "启用SSL", "default": true, "encrypted": false},
        "fromName": {"type": "string", "required": false, "label": "发件人名称", "placeholder": "系统通知", "encrypted": false}
    }'::jsonb,
    '{
        "dependencies": [{"groupId": "org.springframework.boot", "artifactId": "spring-boot-starter-mail", "version": ""}],
        "configClass": "EmailConfig",
        "serviceInterface": "EmailService",
        "serviceImpl": "SmtpEmailServiceImpl",
        "imports": ["org.springframework.mail.javamail.JavaMailSender", "org.springframework.mail.javamail.MimeMessageHelper"]
    }'::jsonb,
    '[]'::jsonb,
    '[]'::jsonb,
    '1.0.0',
    true,
    33,
    'https://doc.jeecg.com/3671898'
);

-- ==================== 智能体集成类 ====================

-- 8. AI 智能体对接能力
INSERT INTO jeecg_capabilities (
    code, name, description, category, icon, endpoint_prefix,
    apis, config_template, code_templates, dependencies, conflicts,
    version, is_active, sort_order, doc_url
) VALUES (
    'ai_agent_connect',
    'AI 智能体对接',
    'JeecgBoot AI智能体集成能力，支持对接AgentScope、Dify、Coze或OpenAI兼容接口。支持流式对话、工具调用等高级特性。',
    'intelligence',
    'bot',
    '/ai/agent',
    '[
        {"method": "POST", "path": "/chat", "description": "与智能体对话", "requestBody": {"query": "string", "history": "array"}, "responseBody": {"response": "string", "toolCalls": "array"}},
        {"method": "GET", "path": "/stream", "description": "流式对话", "requestBody": {"query": "string"}, "responseBody": {"stream": "text/event-stream"}}
    ]'::jsonb,
    '{
        "agentType": {"type": "select", "required": true, "label": "智能体类型", "default": "OPENAI", "options": [{"label": "OpenAI兼容", "value": "OPENAI"}, {"label": "AgentScope", "value": "AGENT_SCOPE"}, {"label": "外部URL", "value": "EXTERNAL_URL"}], "encrypted": false},
        "baseUrl": {"type": "string", "required": true, "label": "服务地址", "placeholder": "http://localhost:8080或https://api.openai.com", "encrypted": false},
        "apiKey": {"type": "password", "required": false, "label": "API Key", "placeholder": "请输入API Key", "encrypted": true},
        "model": {"type": "string", "required": false, "label": "模型名称", "default": "gpt-4", "encrypted": false},
        "promptTemplate": {"type": "textarea", "required": false, "label": "提示词模板", "placeholder": "你是一个${role}...", "encrypted": false}
    }'::jsonb,
    '{
        "dependencies": [{"groupId": "org.springframework.ai", "artifactId": "spring-ai-openai-spring-boot-starter", "version": "${spring-ai.version}"}],
        "configClass": "SpringAIConfig",
        "serviceInterface": "AgentService",
        "serviceImpl": "AgentServiceImpl",
        "imports": ["org.springframework.ai.chat.client.ChatClient"]
    }'::jsonb,
    '[]'::jsonb,
    '[]'::jsonb,
    '1.0.0',
    true,
    40,
    'https://doc.jeecg.com/ai-agent'
);

-- 添加表注释
COMMENT ON TABLE jeecg_capabilities IS 'JeecgBoot能力清单 - 初始化了8个基础能力';
