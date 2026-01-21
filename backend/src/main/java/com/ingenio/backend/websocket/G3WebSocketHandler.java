package com.ingenio.backend.websocket;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.net.URI;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * G3 WebSocket 处理器（骨架）。
 *
 * <p>连接方式：</p>
 * <ul>
 *   <li>`/ws/g3?jobId=<uuid>`</li>
 * </ul>
 *
 * <p>说明：</p>
 * <ul>
 *   <li>当前阶段仅用于订阅某个 jobId 的事件推送；</li>
 *   <li>后续将升级为 projectId/role 维度的 AgentEvent 流。</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class G3WebSocketHandler extends TextWebSocketHandler {

    private final G3WebSocketBroadcaster broadcaster;

    /**
     * sessionId -> jobId 映射，用于关闭时注销。
     */
    private final Map<String, UUID> sessionJobMap = new ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        UUID jobId = parseJobId(session.getUri());
        if (jobId == null) {
            session.close(CloseStatus.NOT_ACCEPTABLE.withReason("缺少或非法的 jobId"));
            return;
        }
        sessionJobMap.put(session.getId(), jobId);
        broadcaster.register(jobId, session);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        // MVP：仅处理 ping/pong，后续可扩展 subscribe/command 等协议
        String payload = message.getPayload() != null ? message.getPayload().trim() : "";
        if ("ping".equalsIgnoreCase(payload)) {
            session.sendMessage(new TextMessage("pong"));
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        UUID jobId = sessionJobMap.remove(session.getId());
        if (jobId != null) {
            broadcaster.unregister(jobId, session);
        }
    }

    private UUID parseJobId(URI uri) {
        if (uri == null) return null;
        String query = uri.getQuery();
        if (query == null || query.isBlank()) return null;

        for (String pair : query.split("&")) {
            String[] kv = pair.split("=", 2);
            if (kv.length == 2 && "jobId".equalsIgnoreCase(kv[0])) {
                try {
                    return UUID.fromString(kv[1]);
                } catch (Exception e) {
                    return null;
                }
            }
        }
        return null;
    }
}

