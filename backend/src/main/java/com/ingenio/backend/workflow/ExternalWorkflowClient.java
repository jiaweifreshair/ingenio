package com.ingenio.backend.workflow;

import reactor.core.publisher.Flux;

import java.util.Map;
import java.util.function.Consumer;

/**
 * 外部工作流客户端接口。
 *
 * <p>用途：对接 Coze / Dify / n8n 等外部工作流平台，将“编排/重试/回调/流式输出”等复杂流程从
 * 核心代码生成链路中剥离，避免在服务端实现难以维护的手写状态机。</p>
 *
 * <p>设计原则：</p>
 * <ul>
 *   <li>API 优先：以 workflowId + input 作为通用入参，便于跨平台迁移；</li>
 *   <li>三种执行形态：同步、流式、异步回调；</li>
 *   <li>实现可插拔：具体平台的鉴权、URL、协议由实现类负责。</li>
 * </ul>
 */
public interface ExternalWorkflowClient {

    /**
     * 同步执行工作流（阻塞等待结果）。
     *
     * @param workflowId 外部平台中的工作流ID（由平台配置）
     * @param input 输入参数（建议为可序列化的 Map）
     * @return 工作流执行结果（含成功标记、输出数据与错误信息）
     */
    WorkflowResult execute(String workflowId, Map<String, Object> input);

    /**
     * 流式执行工作流（适用于 LLM 流式输出、长任务进度）。
     *
     * @param workflowId 外部平台中的工作流ID（由平台配置）
     * @param input 输入参数
     * @return 流式输出的 Chunk 序列
     */
    Flux<WorkflowChunk> executeStream(String workflowId, Map<String, Object> input);

    /**
     * 异步执行工作流（带回调）。
     *
     * <p>常见实现方式：</p>
     * <ul>
     *   <li>本地线程池异步执行同步接口；</li>
     *   <li>调用外部平台异步任务接口，等待 webhook 回调后触发 callback。</li>
     * </ul>
     *
     * @param workflowId 外部平台中的工作流ID
     * @param input 输入参数
     * @param callback 回调函数（实现方需保证至少一次回调或给出超时策略）
     */
    void executeAsync(String workflowId, Map<String, Object> input, Consumer<WorkflowResult> callback);
}

