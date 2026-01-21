package com.ingenio.backend.config;

import com.ingenio.backend.websocket.G3WebSocketHandler;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * G3 WebSocket 配置（骨架）。
 *
 * <p>用途：</p>
 * <ul>
 *   <li>为前端“Atoms 玻璃盒”提供更实时的事件通道（相比 SSE 更易做多路订阅/交互）；</li>
 *   <li>当前阶段先提供日志广播，后续升级为结构化 AgentEvent。</li>
 * </ul>
 */
@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class G3WebSocketConfig implements WebSocketConfigurer {

    private static final Logger log = LoggerFactory.getLogger(G3WebSocketConfig.class);

    private final G3WebSocketHandler g3WebSocketHandler;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(g3WebSocketHandler, "/ws/g3")
                // 生产环境建议改为白名单域名（并考虑携带鉴权/签名）
                .setAllowedOrigins("*");

        log.info("[G3WS] WebSocket 端点已注册: /ws/g3?jobId=<uuid>");
    }
}
