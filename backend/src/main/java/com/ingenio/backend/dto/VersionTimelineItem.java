package com.ingenio.backend.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * 版本时间线条目
 */
public class VersionTimelineItem {

    private UUID versionId;
    private Integer versionNumber;
    private String versionType;
    private String versionTypeDisplay;
    private Instant timestamp;
    private String summary;
    private String status;
    private Boolean canRollback = true;
    private UUID parentVersionId;

    public VersionTimelineItem() {
    }

    public VersionTimelineItem(UUID versionId, Integer versionNumber, String versionType, String versionTypeDisplay,
            Instant timestamp, String summary, String status, Boolean canRollback, UUID parentVersionId) {
        this.versionId = versionId;
        this.versionNumber = versionNumber;
        this.versionType = versionType;
        this.versionTypeDisplay = versionTypeDisplay;
        this.timestamp = timestamp;
        this.summary = summary;
        this.status = status;
        this.canRollback = canRollback;
        this.parentVersionId = parentVersionId;
    }

    public static VersionTimelineItemBuilder builder() {
        return new VersionTimelineItemBuilder();
    }

    public static class VersionTimelineItemBuilder {
        private UUID versionId;
        private Integer versionNumber;
        private String versionType;
        private String versionTypeDisplay;
        private Instant timestamp;
        private String summary;
        private String status;
        private Boolean canRollback = true;
        private UUID parentVersionId;

        public VersionTimelineItemBuilder versionId(UUID versionId) {
            this.versionId = versionId;
            return this;
        }

        // Alias for versionId to be compatible with potential Lombok-generated code
        public VersionTimelineItemBuilder id(UUID id) {
            this.versionId = id;
            return this;
        }

        public VersionTimelineItemBuilder versionNumber(Integer versionNumber) {
            this.versionNumber = versionNumber;
            return this;
        }

        public VersionTimelineItemBuilder versionType(String versionType) {
            this.versionType = versionType;
            return this;
        }

        public VersionTimelineItemBuilder versionTypeDisplay(String versionTypeDisplay) {
            this.versionTypeDisplay = versionTypeDisplay;
            return this;
        }

        public VersionTimelineItemBuilder timestamp(Instant timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        /**
         * 创建时间的别名设置方法，兼容旧调用写法并统一映射到 timestamp。
         */
        public VersionTimelineItemBuilder createdAt(Instant createdAt) {
            this.timestamp = createdAt;
            return this;
        }

        public VersionTimelineItemBuilder summary(String summary) {
            this.summary = summary;
            return this;
        }

        public VersionTimelineItemBuilder status(String status) {
            this.status = status;
            return this;
        }

        public VersionTimelineItemBuilder canRollback(Boolean canRollback) {
            this.canRollback = canRollback;
            return this;
        }

        public VersionTimelineItemBuilder parentVersionId(UUID parentVersionId) {
            this.parentVersionId = parentVersionId;
            return this;
        }

        public VersionTimelineItem build() {
            return new VersionTimelineItem(versionId, versionNumber, versionType, versionTypeDisplay, timestamp,
                    summary, status, canRollback, parentVersionId);
        }
    }

    // Getters and Setters
    public UUID getVersionId() {
        return versionId;
    }

    public void setVersionId(UUID versionId) {
        this.versionId = versionId;
    }

    public Integer getVersionNumber() {
        return versionNumber;
    }

    public void setVersionNumber(Integer versionNumber) {
        this.versionNumber = versionNumber;
    }

    public String getVersionType() {
        return versionType;
    }

    public void setVersionType(String versionType) {
        this.versionType = versionType;
    }

    public String getVersionTypeDisplay() {
        return versionTypeDisplay;
    }

    public void setVersionTypeDisplay(String versionTypeDisplay) {
        this.versionTypeDisplay = versionTypeDisplay;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Boolean getCanRollback() {
        return canRollback;
    }

    public void setCanRollback(Boolean canRollback) {
        this.canRollback = canRollback;
    }

    public UUID getParentVersionId() {
        return parentVersionId;
    }

    public void setParentVersionId(UUID parentVersionId) {
        this.parentVersionId = parentVersionId;
    }
}
