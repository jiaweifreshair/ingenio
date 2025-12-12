package com.ingenio.backend.entity;

import com.baomidou.mybatisplus.annotation.*;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * 多模态输入实体类
 * 支持文本/语音/视频/图像输入
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName(value = "multimodal_inputs")
public class MultimodalInputEntity {

    /**
     * 输入记录ID（UUID）
     */
    @TableId(value = "id", type = IdType.AUTO)
    private UUID id;

    /**
     * 租户ID - 用于租户隔离
     */
    @TableField("tenant_id")
    private UUID tenantId;

    /**
     * 用户ID
     */
    @TableField("user_id")
    private UUID userId;

    /**
     * 关联的AppSpec ID（可为空）
     */
    @TableField("app_spec_id")
    private UUID appSpecId;

    /**
     * 输入类型：text/voice/video/image
     */
    @TableField("input_type")
    private String inputType;

    /**
     * 文本输入内容
     */
    @TableField("text_content")
    private String textContent;

    /**
     * 文件URL（MinIO）
     */
    @TableField("file_url")
    private String fileUrl;

    /**
     * 文件大小（字节）
     */
    @TableField("file_size")
    private Long fileSize;

    /**
     * 文件MIME类型
     */
    @TableField("file_mime_type")
    private String fileMimeType;

    /**
     * 语音转文字结果或OCR结果
     */
    @TableField("transcript")
    private String transcript;

    /**
     * AI分析结果（JSON）
     */
    @TableField(value = "analysis_result", typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> analysisResult;

    /**
     * 处理状态：pending/processing/completed/failed
     */
    @TableField("processing_status")
    private String processingStatus;

    /**
     * 错误信息
     */
    @TableField("error_message")
    private String errorMessage;

    /**
     * 创建时间
     */
    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private Instant createdAt;

    /**
     * 更新时间
     */
    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private Instant updatedAt;

    /**
     * 逻辑删除标记（0未删除/1已删除）
     */
    @TableLogic
    @TableField("deleted")
    private Integer deleted;

    /**
     * 元数据（JSON）
     */
    @TableField(value = "metadata", typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> metadata;

    /**
     * 输入类型枚举
     */
    public enum InputType {
        TEXT("text"),
        VOICE("voice"),
        VIDEO("video"),
        IMAGE("image");

        private final String value;

        InputType(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }
    }

    /**
     * 处理状态枚举
     */
    public enum ProcessingStatus {
        PENDING("pending"),
        PROCESSING("processing"),
        COMPLETED("completed"),
        FAILED("failed");

        private final String value;

        ProcessingStatus(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }
    }
}
