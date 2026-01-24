package com.ingenio.backend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

/**
 * G3 Redis Pub/Sub 配置
 *
 * 用于支持 G3 日志流的分布式订阅
 *
 * @author Gemini
 * @since 2.1.0
 */
@Configuration
public class G3RedisConfig {

    /**
     * Redis 消息监听容器
     *
     * 为 G3LogStreamService 提供 Pub/Sub 能力
     */
    @Bean
    public RedisMessageListenerContainer redisMessageListenerContainer(
            RedisConnectionFactory connectionFactory) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        return container;
    }
}
