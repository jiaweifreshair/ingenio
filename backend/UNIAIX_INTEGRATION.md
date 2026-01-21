# Uniaix AI Services Integration Configuration

## Overview
Uniaix (https://www.uniaix.com) provides unified access to multiple AI providers including:
- Anthropic (Claude)
- Google Gemini
- OpenAI (GPT)
- Moonshot
- Volcengine
- xAI
- 智谱 (Zhipu)
- 腾讯 (Tencent)
- 阿里云 (Alibaba Cloud)

## Configuration

### Environment Variables
```bash
# Uniaix API Configuration
UNIAIX_BASE_URL=https://api.uniaix.com
UNIAIX_API_KEY=your_uniaix_api_key_here

# Model Selection (OpenAI-compatible format)
UNIAIX_DEFAULT_MODEL=claude-3-5-sonnet-20241022
UNIAIX_EMBEDDING_MODEL=text-embedding-3-large
```

### Spring Boot Configuration
```yaml
ingenio:
  ai:
    uniaix:
      base-url: ${UNIAIX_BASE_URL:https://api.uniaix.com}
      api-key: ${UNIAIX_API_KEY}
      default-model: ${UNIAIX_DEFAULT_MODEL:claude-3-5-sonnet-20241022}
      embedding-model: ${UNIAIX_EMBEDDING_MODEL:text-embedding-3-large}
      timeout: 180000
```

## Integration Points

### 1. AI Provider Factory
Update `AIProviderFactory` to support Uniaix as a unified provider.

### 2. Agent Configuration
- **ArchitectAgent**: Use Claude via Uniaix for contract generation
- **BackendCoderAgent**: Use Claude via Uniaix for code generation
- **CoachAgent**: Use Claude via Uniaix for error analysis and repair

### 3. Capability Configuration
Update `ai_agent_connect` capability to support Uniaix:
```json
{
  "agentType": "UNIAIX",
  "baseUrl": "https://api.uniaix.com",
  "apiKey": "your_key",
  "model": "claude-3-5-sonnet-20241022"
}
```

## Benefits
1. **Unified API**: Single integration point for multiple AI providers
2. **Cost Optimization**: Automatic routing to most cost-effective provider
3. **Reliability**: Automatic failover between providers
4. **Flexibility**: Easy switching between models without code changes
