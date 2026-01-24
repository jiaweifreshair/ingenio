package com.ingenio.backend.service.g3;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ingenio.backend.entity.g3.G3LogEntry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * G3 日志流分布式服务
 *
 * 核心功能：
 * 1. 使用 Redis Pub/Sub 发布日志
 * 2. 支持跨节点日志订阅
 * 3. 日志流自动清理
 *
 * Redis 设计：
 * - Channel 格式：g3:log:{jobId}
 * - 消息格式：JSON (G3LogEntry)
 *
 * @author Gemini
 * @since 2.1.0 (分布式架构兼容性修复)
 */
@Slf4j
@Service
public class G3LogStreamService {

    private final RedisTemplate<String, String> redisTemplate;
    private final RedisMessageListenerContainer listenerContainer;
    private final ObjectMapper objectMapper;

    /**
     * Redis Channel 前缀
     */
    @Value("${ingenio.g3.log.channel-prefix:g3:log:}")
    private String channelPrefix;

    /**
     * 本地订阅缓存（用于管理 Subscription 生命周期）
     * Key: jobId, Value: Sinks.Many
     */
    private final Map<UUID, SubscriptionHolder> activeSubscriptions = new ConcurrentHashMap<>();

    public G3LogStreamService(
            RedisTemplate<String, String> redisTemplate,
            RedisMessageListenerContainer listenerContainer,
            ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.listenerContainer = listenerContainer;
        this.objectMapper = objectMapper;
    }

    /**
     * 发布日志到 Redis Pub/Sub
     *
     * @param jobId 任务ID
     * @param entry 日志条目
     */
    public void publishLog(UUID jobId, G3LogEntry entry) {
        if (jobId == null || entry == null) {
            return;
        }

        String channel = buildChannel(jobId);
        try {
            String json = objectMapper.writeValueAsString(entry);
            redisTemplate.convertAndSend(channel, json);
            log.trace("[G3LogStream] 发布日志: jobId={}, level={}", jobId, entry.getLevel());
        } catch (JsonProcessingException e) {
            log.warn("[G3LogStream] 日志序列化失败: jobId={}, error={}", jobId, e.getMessage());
        }
    }

    /**
     * 订阅日志流
     *
     * @param jobId 任务ID
     * @return 日志流 (Flux)
     */
    public Flux<G3LogEntry> subscribeLog(UUID jobId) {
        if (jobId == null) {
            return Flux.empty();
        }

        // 创建或复用已有的 Sink
        SubscriptionHolder holder = activeSubscriptions.computeIfAbsent(jobId, id -> {
            Sinks.Many<G3LogEntry> sink = Sinks.many().multicast().onBackpressureBuffer(1000);

            String channel = buildChannel(id);
            MessageListener listener = (message, pattern) -> {
                try {
                    String json = new String(message.getBody());
                    G3LogEntry entry = objectMapper.readValue(json, G3LogEntry.class);
                    sink.tryEmitNext(entry);
                } catch (Exception e) {
                    log.warn("[G3LogStream] 日志反序列化失败: {}", e.getMessage());
                }
            };

            // 注册 Redis 订阅
            listenerContainer.addMessageListener(listener, new ChannelTopic(channel));
            log.debug("[G3LogStream] 订阅日志频道: {}", channel);

            return new SubscriptionHolder(sink, listener, channel);
        });

        return holder.sink.asFlux()
                .doOnCancel(() -> cleanup(jobId))
                .doOnTerminate(() -> cleanup(jobId));
    }

    /**
     * 获取日志发布的 Consumer（兼容旧接口）
     *
     * @param jobId 任务ID
     * @return 日志 Consumer
     */
    public java.util.function.Consumer<G3LogEntry> getLogConsumer(UUID jobId) {
        return entry -> publishLog(jobId, entry);
    }

    /**
     * 清理订阅资源
     *
     * @param jobId 任务ID
     */
    public void cleanup(UUID jobId) {
        if (jobId == null) {
            return;
        }

        SubscriptionHolder holder = activeSubscriptions.remove(jobId);
        if (holder != null) {
            try {
                listenerContainer.removeMessageListener(holder.listener);
                holder.sink.tryEmitComplete();
                log.debug("[G3LogStream] 清理订阅: jobId={}", jobId);
            } catch (Exception e) {
                log.warn("[G3LogStream] 清理订阅失败: jobId={}, error={}", jobId, e.getMessage());
            }
        }
    }

    /**
     * 检查是否有活跃订阅
     *
     * @param jobId 任务ID
     * @return 是否活跃
     */
    public boolean hasActiveSubscription(UUID jobId) {
        return activeSubscriptions.containsKey(jobId);
    }

    /**
     * 构建 Redis Channel 名称
     */
    private String buildChannel(UUID jobId) {
        return channelPrefix + jobId.toString();
    }

    /**
     * 订阅持有者（内部类）
     */
    private record SubscriptionHolder(
            Sinks.Many<G3LogEntry> sink,
            MessageListener listener,
            String channel) {
    }
}
