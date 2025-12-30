package com.ingenio.backend.module.g3.service;

import com.ingenio.backend.module.g3.dto.G3LogEntry;
import com.ingenio.backend.module.g3.dto.G3StartRequest;
import com.ingenio.backend.module.g3.dto.G3TaskResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class G3EngineService {

    private final WebClient g3WebClient;

    public Mono<G3TaskResponse> startTask(String requirement, String tenantId) {
        G3StartRequest req = new G3StartRequest();
        req.setRequirement(requirement);
        req.setTenantId(tenantId);

        return g3WebClient.post()
                .uri("/api/v1/g3/start")
                .bodyValue(req)
                .retrieve()
                .bodyToMono(G3TaskResponse.class)
                .doOnSuccess(res -> log.info("Started G3 Task: {}", res.getTask_id()))
                .doOnError(err -> log.error("Failed to start G3 Task", err));
    }

    // 简单轮询获取日志 (实际生产应用 SSE 连接 Python 服务，或者通过 Redis Pub/Sub)
    // 这里为了演示，我们使用 Flux.interval 轮询 Python API
    public Flux<G3LogEntry> streamLogs(String taskId) {
        return Flux.interval(Duration.ofSeconds(1))
                .flatMap(tick -> g3WebClient.get()
                        .uri("/api/v1/g3/logs/" + taskId)
                        .retrieve()
                        .bodyToFlux(G3LogEntry.class)
                        .onErrorResume(e -> Flux.empty()) // 忽略错误，继续重试
                )
                // 这里有个问题：轮询会不断返回全量日志。我们需要在客户端去重，或者让 Python API 支持 ?since=timestamp
                // MVP 简化：直接推全量，前端去重
                .distinct(G3LogEntry::getTimestamp); // 简单的去重尝试
    }
}
