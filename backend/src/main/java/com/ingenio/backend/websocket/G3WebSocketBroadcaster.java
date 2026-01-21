package com.ingenio.backend.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * G3 WebSocket 广播器（骨架）。
 *
 * <p>用途：</p>
 * <ul>
 *   <li>为“Atoms 玻璃盒”提供 WebSocket 事件推送通道；</li>
 *   <li>当前阶段仅用于把后端 G3 日志条目推送到订阅者（后续可扩展为结构化 AgentEvent）。</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
public class G3WebSocketBroadcaster {

    private static final Logger log = LoggerFactory.getLogger(G3WebSocketBroadcaster.class);

    private final ObjectMapper objectMapper;

    /**
     * 活跃连接：Key=jobId，Value=会话集合
     */
    private final Map<UUID, CopyOnWriteArraySet<WebSocketSession>> sessionsByJobId = new ConcurrentHashMap<>();

    public void register(UUID jobId, WebSocketSession session) {
        sessionsByJobId.computeIfAbsent(jobId, k -> new CopyOnWriteArraySet<>()).add(session);
        log.info("[G3WS] 注册连接: jobId={}, sessionId={}, total={}",
                jobId, session.getId(), sessionsByJobId.get(jobId).size());
    }

    public void unregister(UUID jobId, WebSocketSession session) {
        CopyOnWriteArraySet<WebSocketSession> sessions = sessionsByJobId.get(jobId);
        if (sessions == null) return;

        sessions.remove(session);
        if (sessions.isEmpty()) {
            sessionsByJobId.remove(jobId);
        }
        log.info("[G3WS] 注销连接: jobId={}, sessionId={}, remain={}",
                jobId, session.getId(), sessionsByJobId.getOrDefault(jobId, new CopyOnWriteArraySet<>()).size());
    }

    /**
     * 广播任意 payload（会被包装为 { type, data }）。
     */
    public void broadcast(UUID jobId, String type, Object payload) {
        CopyOnWriteArraySet<WebSocketSession> sessions = sessionsByJobId.get(jobId);
        if (sessions == null || sessions.isEmpty()) {
            return;
        }

        try {
            String json = objectMapper.writeValueAsString(Map.of(
                    "type", type,
                    "data", payload
            ));
            TextMessage msg = new TextMessage(json);

            for (WebSocketSession session : sessions) {
                if (!session.isOpen()) {
                    sessions.remove(session);
                    continue;
                }
                try {
                    session.sendMessage(msg);
                } catch (Exception e) {
                    log.debug("[G3WS] 推送失败: jobId={}, sessionId={}, error={}",
                            jobId, session.getId(), e.getMessage());
                }
            }
        } catch (Exception e) {
            log.debug("[G3WS] 序列化失败: jobId={}, type={}, error={}", jobId, type, e.getMessage());
        }
    }
}
