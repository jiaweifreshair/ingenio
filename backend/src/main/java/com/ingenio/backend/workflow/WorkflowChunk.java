package com.ingenio.backend.workflow;

import java.util.Map;

/**
 * 工作流流式输出片段（Chunk）。
 *
 * <p>说明：</p>
 * <ul>
 *   <li>type 用于标识片段类型（例如：token、log、progress、result）；</li>
 *   <li>content 用于承载文本类输出；</li>
 *   <li>data 用于承载结构化输出（例如进度百分比、阶段标识等）。</li>
 * </ul>
 *
 * @param type 片段类型
 * @param content 文本内容（可为空）
 * @param data 结构化数据（可为空）
 * @param done 是否为最后一个片段
 */
public record WorkflowChunk(
        String type,
        String content,
        Map<String, Object> data,
        boolean done
) {
    public static WorkflowChunk token(String content) {
        return new WorkflowChunk("token", content, Map.of(), false);
    }

    public static WorkflowChunk done(Map<String, Object> data) {
        return new WorkflowChunk("result", null, data == null ? Map.of() : data, true);
    }
}

