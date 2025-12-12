package com.ingenio.backend.dto.sse;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * SSE事件DTO
 * 用于流式代码生成的实时事件推送
 *
 * 事件类型定义:
 * - thinking: AI思考中,显示思考内容和预计时长
 * - file-start: 开始生成文件,包含文件路径和类型信息
 * - file-content: 文件内容chunk,逐步推送生成的代码
 * - file-complete: 文件生成完成
 * - status: 状态更新,提示当前进度
 * - error: 错误信息
 * - complete: 全部完成,包含所有生成的文件列表
 *
 * @author Ingenio Team
 * @since 2.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SseEvent {

    /**
     * 事件类型
     * 可选值: thinking, file-start, file-content, file-complete, status, error, complete
     */
    private String type;

    /**
     * 消息内容
     * 用于thinking和error类型事件
     */
    private String message;

    /**
     * 文件路径
     * 用于file-start, file-content, file-complete事件
     */
    private String path;

    /**
     * 文件内容chunk
     * 用于file-content事件,逐步推送生成的代码
     */
    private String content;

    /**
     * 文件类型
     * 例如: typescript, javascript, jsx, tsx, css, json, html
     */
    private String fileType;

    /**
     * 思考持续时间(毫秒)
     * 用于thinking事件,提示AI思考的预计时长
     */
    private Integer duration;

    /**
     * 生成的文件列表
     * 用于complete事件,返回所有生成的文件
     */
    private List<GeneratedFile> files;

    /**
     * 元数据
     * 用于存储额外的上下文信息
     */
    private Map<String, Object> metadata;

    /**
     * 生成的文件信息
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class GeneratedFile {
        /**
         * 文件路径
         */
        private String path;

        /**
         * 文件内容
         */
        private String content;

        /**
         * 文件类型
         */
        private String type;

        /**
         * 是否已完成
         */
        private Boolean completed;

        /**
         * 是否已编辑
         */
        private Boolean edited;
    }
}
