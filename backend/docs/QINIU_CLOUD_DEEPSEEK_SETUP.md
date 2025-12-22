# 七牛云DeepSeek API配置指南

**更新时间**: 2025-11-10
**目的**: 配置Ingenio后端使用七牛云大模型平台的DeepSeek-R1 API

---

## 📋 概述

本文档说明如何配置Ingenio后端使用七牛云大模型平台提供的DeepSeek-R1 API服务。

### 为什么使用七牛云？

- ✅ **完整DeepSeek-R1支持**: 提供最新的DeepSeek-R1和DeepSeek-V3模型
- ✅ **兼容OpenAI格式**: 无需修改代码，直接使用Spring AI OpenAI客户端
- ✅ **高性能推理**: 专用GPU云服务器，低延迟高吞吐
- ✅ **国内访问优化**: 七牛云CDN加速，访问速度快
- ✅ **成本优化**: 量化模型支持，降低推理成本

---

## 🔧 配置步骤

### 1. 获取七牛云AI API密钥

1. 访问七牛云官网：https://www.qiniu.com
2. 注册/登录账号
3. 进入"AI大模型推理"服务
4. 创建API密钥（Access Key）
5. 保存密钥（格式类似：`qiniu_ak_xxxxxxxxxx`）

**官方文档**: https://developer.qiniu.com/aitokenapi/12882/ai-inference-api

### 2. 配置环境变量

#### 方式1：系统环境变量（推荐生产环境）

```bash
# Linux/macOS
export DEEPSEEK_API_KEY="your-qiniu-api-key-here"

# Windows
set DEEPSEEK_API_KEY=your-qiniu-api-key-here
```

#### 方式2：.env文件（开发环境）

在项目根目录创建`.env`文件：

```properties
# 七牛云DeepSeek API密钥
DEEPSEEK_API_KEY=your-qiniu-api-key-here
```

**⚠️ 重要**: 确保`.env`文件已添加到`.gitignore`，避免泄露密钥。

### 3. 验证配置

启动后端服务：

```bash
cd backend
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

检查日志输出：

```
✅ 成功示例:
2025-11-10 10:00:00 [main] INFO  o.s.ai.openai.OpenAiChatModel - Initialized OpenAI Chat Model with base URL: https://api.qnaigc.com

❌ 失败示例:
2025-11-10 10:00:00 [main] ERROR o.s.ai.openai.OpenAiChatModel - Failed to initialize: Invalid API key
```

---

## 🔌 API端点信息

### 七牛云DeepSeek API

| 配置项 | 值 |
|--------|---|
| **Spring AI base-url** | `https://api.qnaigc.com` |
| **Chat Completions** | `https://api.qnaigc.com/v1/chat/completions` |
| **认证方式** | Bearer Token (API Key) |
| **支持模型** | `deepseek-r1`, `deepseek-v3` |

### 请求示例（curl）

```bash
curl https://api.qnaigc.com/v1/chat/completions \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $DEEPSEEK_API_KEY" \
  -d '{
    "model": "deepseek-r1",
    "messages": [
      {
        "role": "user",
        "content": "你好，请介绍一下DeepSeek-R1模型"
      }
    ],
    "temperature": 0.7,
    "max_tokens": 4000
  }'
```

---

## 🎯 Agent模型配置

Ingenio使用三个AI Agent，已配置使用DeepSeek-R1模型：

| Agent | 模型 | Temperature | Max Tokens | 用途 |
|-------|------|-------------|-----------|------|
| **PlanAgent** | deepseek-r1 | 0.7 | 4000 | 需求分析和规划 |
| **ExecuteAgent** | deepseek-r1 | 0.3 | 8000 | 代码生成执行 |
| **ValidateAgent** | deepseek-r1 | 0.5 | 2000 | 质量验证校验 |

配置文件位置：`backend/src/main/resources/application.yml`

---

## 🧪 测试API连接

### 使用Health Check端点

```bash
curl http://localhost:8080/api/actuator/health
```

预期响应：

```json
{
  "status": "UP",
  "components": {
    "ai": {
      "status": "UP",
      "details": {
        "model": "deepseek-r1",
        "provider": "openai",
        "baseUrl": "https://api.qnaigc.com"
      }
    }
  }
}
```

### 运行AI集成测试

```bash
# 设置API密钥
export DEEPSEEK_API_KEY="your-qiniu-api-key-here"

# 运行测试
cd backend
mvn test -Dtest=*AI*Test
```

---

## 🚀 性能优化建议

### 1. 模型选择

| 模型 | 参数量 | 适用场景 | 推理速度 | 成本 |
|-----|--------|---------|---------|------|
| **deepseek-r1** | 671B（量化） | 复杂推理，质量优先 | 中 | 高 |
| **deepseek-v3** | 混合专家（MoE） | 平衡性能和成本 | 快 | 中 |

**推荐**：生产环境使用`deepseek-v3`，开发测试使用`deepseek-r1`。

### 2. 缓存策略

启用Redis缓存AI响应：

```yaml
spring:
  ai:
    openai:
      chat:
        options:
          # 启用响应缓存（相同prompt返回缓存结果）
          cache-enabled: true
```

### 3. 并发控制

限制AI API并发调用：

```yaml
ingenio:
  rate-limit:
    ai-calls-per-hour: 100  # 每小时最大AI调用次数
```

---

## 📊 成本估算

七牛云DeepSeek API定价（参考）：

| 模型 | 输入价格 | 输出价格 | 估算月成本 |
|-----|---------|---------|-----------|
| deepseek-r1 | ¥0.002/1k tokens | ¥0.008/1k tokens | ¥500-2000 |
| deepseek-v3 | ¥0.001/1k tokens | ¥0.004/1k tokens | ¥300-1000 |

**月成本计算**：基于10,000次AI调用，平均每次4k tokens。

---

## 🐛 常见问题

### Q1: 401 Unauthorized错误

**原因**: API密钥无效或未配置。

**解决方案**:
```bash
# 检查环境变量
echo $DEEPSEEK_API_KEY

# 重新设置密钥
export DEEPSEEK_API_KEY="your-correct-key"

# 重启服务
mvn spring-boot:run
```

### Q2: 模型不支持错误

**错误信息**: `Model 'deepseek-chat' not found`

**原因**: 旧模型名称已弃用。

**解决方案**: 使用最新模型名称`deepseek-r1`或`deepseek-v3`。

### Q3: 响应超时

**原因**: 网络延迟或模型推理时间长。

**解决方案**:
```yaml
spring:
  ai:
    openai:
      chat:
        options:
          timeout: 60000  # 增加超时时间到60秒
```

### Q4: 速率限制错误

**错误信息**: `429 Too Many Requests`

**原因**: 超过七牛云API速率限制。

**解决方案**:
- 降低并发请求数
- 启用响应缓存
- 升级七牛云账号套餐

---

## 📚 相关文档

- [七牛云AI平台官方文档](https://developer.qiniu.com/aitokenapi/12882/ai-inference-api)
- [DeepSeek-R1模型介绍](https://deepseek.com)
- [Spring AI OpenAI文档](https://docs.spring.io/spring-ai/reference/api/clients/openai-chat.html)
- [Ingenio后端架构文档](../docs/architecture/ARCHITECTURE.md)

---

## 🔐 安全注意事项

1. **密钥安全**:
   - ❌ 不要将API密钥提交到Git仓库
   - ❌ 不要在日志中打印密钥
   - ✅ 使用环境变量或Secret管理服务
   - ✅ 定期轮换API密钥

2. **访问控制**:
   - 限制API调用频率（防止滥用）
   - 监控异常调用模式
   - 启用IP白名单（生产环境）

3. **数据隐私**:
   - 不要发送用户敏感数据到AI API
   - 遵守数据合规要求（GDPR、网络安全法）

---

## 📞 技术支持

- **七牛云客服**: https://support.qiniu.com
- **Ingenio项目**: https://github.com/ingenio/ingenio/issues
- **紧急联系**: dev@ingenio.dev

---

**Made with ❤️ by Ingenio Team**

> 最后更新: 2025-11-10 | Phase 4 配置更新
