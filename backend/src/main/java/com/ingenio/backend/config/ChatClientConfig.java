package com.ingenio.backend.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * ChatClient配置类
 *
 * <p>提供Spring AI ChatClient的Bean定义，用于AI对话功能。</p>
 *
 * <p>设计原则：</p>
 * <ul>
 *   <li>将ChatClient作为Bean注入，便于测试时替换</li>
 *   <li>基于ChatModel构建ChatClient，保持Spring AI的分层架构</li>
 *   <li>支持单元测试通过@TestConfiguration注入Mock实现</li>
 * </ul>
 *
 * <p>技术说明：</p>
 * <ul>
 *   <li>ChatModel是Spring AI的底层接口</li>
 *   <li>ChatClient是高层Fluent API，提供更好的开发体验</li>
 *   <li>通过Bean注入ChatClient，可在测试中使用可控实现</li>
 * </ul>
 *
 * @author Ingenio Team
 * @version 1.0
 * @since 2025-11-20
 * @see ChatClient
 * @see ChatModel
 */
@Configuration
public class ChatClientConfig {

    /**
     * 创建ChatClient Bean
     *
     * <p>基于ChatModel构建ChatClient实例，提供Fluent API风格的AI对话能力。</p>
     *
     * @param chatModel Spring AI自动配置的ChatModel（由spring-ai-alibaba-starter提供）
     * @return ChatClient实例
     */
    @Bean
    public ChatClient chatClient(ChatModel chatModel) {
        return ChatClient.builder(chatModel).build();
    }
}
