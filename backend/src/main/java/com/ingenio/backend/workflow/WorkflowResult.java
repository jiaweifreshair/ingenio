package com.ingenio.backend.workflow;

import java.util.Map;

/**
 * 工作流执行结果。
 *
 * @param success 是否成功
 * @param data 结构化输出（平台返回的关键字段）
 * @param raw 原始输出（便于排查与审计）
 * @param errorMessage 失败原因（成功时可为空）
 */
public record WorkflowResult(
        boolean success,
        Map<String, Object> data,
        String raw,
        String errorMessage
) {

    public static WorkflowResult success(Map<String, Object> data, String raw) {
        return new WorkflowResult(true, data, raw, null);
    }

    public static WorkflowResult failure(String errorMessage, String raw) {
        return new WorkflowResult(false, Map.of(), raw, errorMessage);
    }
}

