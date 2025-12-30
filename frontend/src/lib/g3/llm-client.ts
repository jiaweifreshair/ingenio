export interface LLMResponse {
  content: string;
  tokensUsed?: number;
}

export interface LLMClient {
  chat(messages: Array<{ role: 'system' | 'user' | 'assistant'; content: string }>): Promise<LLMResponse>;
}

// 模拟 LLM (用于开发测试，不消耗 Token)
export class MockLLMClient implements LLMClient {
  async chat(messages: Array<{ role: string; content: string }>): Promise<LLMResponse> {
    await new Promise(resolve => setTimeout(resolve, 100));
    const lastMsg = messages[messages.length - 1].content;
    
    // 简单的关键词模拟
    if (lastMsg.includes('Architect')) {
      return {
        content: JSON.stringify({
          modules: [{
            moduleName: "demo",
            description: "Demo Module",
            entities: [{ name: "DemoEntity", fields: [], apis: [] }]
          }],
          dependencies: []
        })
      };
    }
    
    if (lastMsg.includes('Player')) {
      return {
        content: `
// File: DemoController.java
package com.ingenio...
public class DemoController {}

// File: DemoService.java
package com.ingenio...
public class DemoService {}
        `
      };
    }

    if (lastMsg.includes('Coach')) {
        return {
            content: `
// File: AttackTest.java
@Test
void attack() {
    // Mock attack
}
            `
        };
    }

    return { content: "I am a mock AI." };
  }
}
