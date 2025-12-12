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
 * 网站抓取任务实体类
 * 从现有网站抓取内容生成AppSpec
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName(value = "scrape_tasks")
public class ScrapeTaskEntity {

    /**
     * 抓取任务ID（UUID）
     */
    @TableId(value = "id", type = IdType.AUTO)
    private UUID id;

    /**
     * 租户ID
     */
    @TableField("tenant_id")
    private UUID tenantId;

    /**
     * 用户ID
     */
    @TableField("user_id")
    private UUID userId;

    /**
     * 关联的AppSpec ID
     */
    @TableField("app_spec_id")
    private UUID appSpecId;

    /**
     * 抓取目标URL
     */
    @TableField("target_url")
    private String targetUrl;

    /**
     * 抓取类型：full/screenshot/metadata
     */
    @TableField("scrape_type")
    private String scrapeType;

    /**
     * 抓取配置（JSON）
     */
    @TableField(value = "scrape_config", typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> scrapeConfig;

    /**
     * 抓取状态：pending/running/completed/failed
     */
    @TableField("scrape_status")
    private String scrapeStatus;

    /**
     * 抓取结果（JSON）
     */
    @TableField(value = "result_data", typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> resultData;

    /**
     * 错误信息
     */
    @TableField("error_message")
    private String errorMessage;

    /**
     * 开始时间
     */
    @TableField("started_at")
    private Instant startedAt;

    /**
     * 完成时间
     */
    @TableField("completed_at")
    private Instant completedAt;

    /**
     * 执行时长（毫秒）
     */
    @TableField("duration_ms")
    private Integer durationMs;

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
     * 逻辑删除标记
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
     * 抓取类型枚举
     */
    public enum ScrapeType {
        FULL("full"),
        SCREENSHOT("screenshot"),
        METADATA("metadata");

        private final String value;

        ScrapeType(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }
    }

    /**
     * 抓取状态枚举
     */
    public enum ScrapeStatus {
        PENDING("pending"),
        RUNNING("running"),
        COMPLETED("completed"),
        FAILED("failed");

        private final String value;

        ScrapeStatus(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }
    }
}
