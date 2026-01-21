package com.ingenio.backend.entity.g3;

import com.baomidou.mybatisplus.annotation.*;
import com.ingenio.backend.config.UUIDv8TypeHandler;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * G3规划文件实体类
 *
 * 基于Manus工作流的规划文件存储，实现"文件系统作为外部记忆"的核心理念。
 */
@Data
@Builder
@NoArgsConstructor
@TableName(value = "g3_planning_files", autoResultMap = true)
public class G3PlanningFileEntity {

    /**
     * 文件ID（UUID自动生成）
     */
    @TableId(value = "id", type = IdType.ASSIGN_UUID)
    @TableField(typeHandler = UUIDv8TypeHandler.class)
    private UUID id;

    // ==================== 关联信息 ====================

    /**
     * 所属G3任务ID
     */
    @TableField(value = "job_id", typeHandler = UUIDv8TypeHandler.class)
    private UUID jobId;

    // ==================== 文件信息 ====================

    /**
     * 文件类型
     */
    @TableField("file_type")
    private String fileType;

    /**
     * 文件内容（Markdown格式）
     */
    @TableField("content")
    private String content;

    /**
     * 版本号
     */
    @TableField("version")
    private Integer version;

    /**
     * 最后更新者
     */
    @TableField("last_updated_by")
    private String lastUpdatedBy;

    // ==================== 审计字段 ====================

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

    // ==================== 常量定义 ====================

    public static final String TYPE_TASK_PLAN = "task_plan";
    public static final String TYPE_NOTES = "notes";
    public static final String TYPE_CONTEXT = "context";

    public static final String FILE_TYPE_TASK_PLAN = TYPE_TASK_PLAN;
    public static final String FILE_TYPE_NOTES = TYPE_NOTES;
    public static final String FILE_TYPE_CONTEXT = TYPE_CONTEXT;

    public static final String UPDATER_SYSTEM = "system";
    public static final String UPDATER_ARCHITECT = "architect";
    public static final String UPDATER_CODER = "coder";
    public static final String UPDATER_COACH = "coach";
    public static final String UPDATER_USER = "user";

    // Manual Boilerplate
    public G3PlanningFileEntity(UUID id, UUID jobId, String fileType, String content, Integer version,
            String lastUpdatedBy, Instant createdAt, Instant updatedAt) {
        this.id = id;
        this.jobId = jobId;
        this.fileType = fileType;
        this.content = content;
        this.version = version;
        this.lastUpdatedBy = lastUpdatedBy;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    // Getters
    public UUID getId() {
        return id;
    }

    public UUID getJobId() {
        return jobId;
    }

    public String getFileType() {
        return fileType;
    }

    public String getContent() {
        return content;
    }

    public Integer getVersion() {
        return version;
    }

    public String getLastUpdatedBy() {
        return lastUpdatedBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    // Setters
    public void setId(UUID id) {
        this.id = id;
    }

    public void setJobId(UUID jobId) {
        this.jobId = jobId;
    }

    public void setFileType(String fileType) {
        this.fileType = fileType;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public void setVersion(Integer version) {
        this.version = version;
    }

    public void setLastUpdatedBy(String lastUpdatedBy) {
        this.lastUpdatedBy = lastUpdatedBy;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public static G3PlanningFileEntityBuilder builder() {
        return new G3PlanningFileEntityBuilder();
    }

    public static class G3PlanningFileEntityBuilder {
        private UUID id;
        private UUID jobId;
        private String fileType;
        private String content;
        private Integer version;
        private String lastUpdatedBy;
        private Instant createdAt;
        private Instant updatedAt;

        G3PlanningFileEntityBuilder() {
        }

        public G3PlanningFileEntityBuilder id(UUID id) {
            this.id = id;
            return this;
        }

        public G3PlanningFileEntityBuilder jobId(UUID jobId) {
            this.jobId = jobId;
            return this;
        }

        public G3PlanningFileEntityBuilder fileType(String fileType) {
            this.fileType = fileType;
            return this;
        }

        public G3PlanningFileEntityBuilder content(String content) {
            this.content = content;
            return this;
        }

        public G3PlanningFileEntityBuilder version(Integer version) {
            this.version = version;
            return this;
        }

        public G3PlanningFileEntityBuilder lastUpdatedBy(String lastUpdatedBy) {
            this.lastUpdatedBy = lastUpdatedBy;
            return this;
        }

        public G3PlanningFileEntityBuilder createdAt(Instant createdAt) {
            this.createdAt = createdAt;
            return this;
        }

        public G3PlanningFileEntityBuilder updatedAt(Instant updatedAt) {
            this.updatedAt = updatedAt;
            return this;
        }

        public G3PlanningFileEntity build() {
            return new G3PlanningFileEntity(id, jobId, fileType, content, version, lastUpdatedBy, createdAt, updatedAt);
        }
    }
}
