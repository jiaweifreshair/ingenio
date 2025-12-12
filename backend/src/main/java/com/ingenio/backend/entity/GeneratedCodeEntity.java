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
 * 生成代码记录实体类
 * 存储KuiklyUI渲染器生成的代码文件信息
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName(value = "generated_code")
public class GeneratedCodeEntity {

    /**
     * 记录ID（UUID）
     */
    @TableId(value = "id", type = IdType.AUTO)
    private UUID id;

    /**
     * 关联的AppSpec ID
     */
    @TableField("app_spec_id")
    private UUID appSpecId;

    /**
     * 项目标识（唯一）
     * 格式：{tenant_id}_{timestamp}_{random}
     */
    @TableField("project_id")
    private String projectId;

    /**
     * 渲染器类型：kuikly-ui/react-next/vue-vite
     */
    @TableField("renderer_type")
    private String rendererType;

    /**
     * 框架类型：taro/next/nuxt
     */
    @TableField("framework")
    private String framework;

    /**
     * MinIO存储路径
     */
    @TableField("storage_key")
    private String storageKey;

    /**
     * 文件数量
     */
    @TableField("file_count")
    private Integer fileCount;

    /**
     * 总大小（字节）
     */
    @TableField("total_size_bytes")
    private Long totalSizeBytes;

    /**
     * 构建状态：pending/building/success/failed
     */
    @TableField("build_status")
    private String buildStatus;

    /**
     * 构建日志
     */
    @TableField("build_log")
    private String buildLog;

    /**
     * 预览URL
     */
    @TableField("preview_url")
    private String previewUrl;

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
     * 元数据（JSON）
     */
    @TableField(value = "metadata", typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> metadata;

    /**
     * 渲染器类型枚举
     */
    public enum RendererType {
        KUIKLY_UI("kuikly-ui"),
        REACT_NEXT("react-next"),
        VUE_VITE("vue-vite");

        private final String value;

        RendererType(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }
    }

    /**
     * 框架类型枚举
     */
    public enum Framework {
        TARO("taro"),
        NEXT("next"),
        NUXT("nuxt");

        private final String value;

        Framework(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }
    }

    /**
     * 构建状态枚举
     */
    public enum BuildStatus {
        PENDING("pending"),
        BUILDING("building"),
        SUCCESS("success"),
        FAILED("failed");

        private final String value;

        BuildStatus(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }
    }
}
