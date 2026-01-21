package com.ingenio.backend.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import com.ingenio.backend.config.UUIDv8TypeHandler;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * 生成版本管理实体
 */
@TableName(value = "generation_versions")
public class GenerationVersionEntity {

    @TableId(type = IdType.ASSIGN_UUID)
    @TableField(typeHandler = UUIDv8TypeHandler.class)
    private UUID id;

    @TableField(value = "tenant_id", typeHandler = UUIDv8TypeHandler.class)
    private UUID tenantId;

    @TableField(value = "user_id", typeHandler = UUIDv8TypeHandler.class)
    private UUID userId;

    @TableField(value = "task_id", typeHandler = UUIDv8TypeHandler.class)
    private UUID taskId;

    private Integer versionNumber;

    private String versionType;

    private String versionTag;

    private String description;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> changes;

    @TableField(typeHandler = JacksonTypeHandler.class)
    private Map<String, Object> snapshot;

    @TableField(value = "parent_version_id", typeHandler = UUIDv8TypeHandler.class)
    private UUID parentVersionId;

    private Boolean isDeployed;

    private Instant deployedAt;

    private Instant createdAt;

    // Getters
    public UUID getId() {
        return id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public UUID getUserId() {
        return userId;
    }

    public UUID getTaskId() {
        return taskId;
    }

    public Integer getVersionNumber() {
        return versionNumber;
    }

    public String getVersionType() {
        return versionType;
    }

    public String getVersionTag() {
        return versionTag;
    }

    public String getDescription() {
        return description;
    }

    public Map<String, Object> getChanges() {
        return changes;
    }

    public Map<String, Object> getSnapshot() {
        return snapshot;
    }

    public UUID getParentVersionId() {
        return parentVersionId;
    }

    public Boolean getIsDeployed() {
        return isDeployed;
    }

    public Instant getDeployedAt() {
        return deployedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    // Setters
    public void setId(UUID id) {
        this.id = id;
    }

    public void setTenantId(UUID tenantId) {
        this.tenantId = tenantId;
    }

    public void setUserId(UUID userId) {
        this.userId = userId;
    }

    public void setTaskId(UUID taskId) {
        this.taskId = taskId;
    }

    public void setVersionNumber(Integer versionNumber) {
        this.versionNumber = versionNumber;
    }

    public void setVersionType(String versionType) {
        this.versionType = versionType;
    }

    public void setVersionTag(String versionTag) {
        this.versionTag = versionTag;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public void setChanges(Map<String, Object> changes) {
        this.changes = changes;
    }

    public void setSnapshot(Map<String, Object> snapshot) {
        this.snapshot = snapshot;
    }

    public void setParentVersionId(UUID parentVersionId) {
        this.parentVersionId = parentVersionId;
    }

    public void setIsDeployed(Boolean isDeployed) {
        this.isDeployed = isDeployed;
    }

    public void setDeployedAt(Instant deployedAt) {
        this.deployedAt = deployedAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
