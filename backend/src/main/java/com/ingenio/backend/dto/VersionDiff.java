package com.ingenio.backend.dto;

import com.ingenio.backend.entity.GenerationVersionEntity;
import java.util.Map;

/**
 * 版本差异对比结果
 */
public class VersionDiff {

    private GenerationVersionEntity version1;
    private GenerationVersionEntity version2;
    private Map<String, Object> differences;
    private Integer changeCount;
    private String changeSummary;
    private Boolean hasMajorChanges = false;

    public VersionDiff() {
    }

    public VersionDiff(GenerationVersionEntity version1, GenerationVersionEntity version2,
            Map<String, Object> differences, Integer changeCount, String changeSummary, Boolean hasMajorChanges) {
        this.version1 = version1;
        this.version2 = version2;
        this.differences = differences;
        this.changeCount = changeCount;
        this.changeSummary = changeSummary;
        this.hasMajorChanges = hasMajorChanges;
    }

    public static VersionDiffBuilder builder() {
        return new VersionDiffBuilder();
    }

    public static class VersionDiffBuilder {
        private GenerationVersionEntity version1;
        private GenerationVersionEntity version2;
        private Map<String, Object> differences;
        private Integer changeCount;
        private String changeSummary;
        private Boolean hasMajorChanges = false;

        public VersionDiffBuilder version1(GenerationVersionEntity version1) {
            this.version1 = version1;
            return this;
        }

        public VersionDiffBuilder version2(GenerationVersionEntity version2) {
            this.version2 = version2;
            return this;
        }

        public VersionDiffBuilder differences(Map<String, Object> differences) {
            this.differences = differences;
            return this;
        }

        public VersionDiffBuilder changeCount(Integer changeCount) {
            this.changeCount = changeCount;
            return this;
        }

        public VersionDiffBuilder changeSummary(String changeSummary) {
            this.changeSummary = changeSummary;
            return this;
        }

        public VersionDiffBuilder hasMajorChanges(Boolean hasMajorChanges) {
            this.hasMajorChanges = hasMajorChanges;
            return this;
        }

        public VersionDiff build() {
            return new VersionDiff(version1, version2, differences, changeCount, changeSummary, hasMajorChanges);
        }
    }

    // Getters and Setters
    public GenerationVersionEntity getVersion1() {
        return version1;
    }

    public void setVersion1(GenerationVersionEntity version1) {
        this.version1 = version1;
    }

    public GenerationVersionEntity getVersion2() {
        return version2;
    }

    public void setVersion2(GenerationVersionEntity version2) {
        this.version2 = version2;
    }

    public Map<String, Object> getDifferences() {
        return differences;
    }

    public void setDifferences(Map<String, Object> differences) {
        this.differences = differences;
    }

    public Integer getChangeCount() {
        return changeCount;
    }

    public void setChangeCount(Integer changeCount) {
        this.changeCount = changeCount;
    }

    public String getChangeSummary() {
        return changeSummary;
    }

    public void setChangeSummary(String changeSummary) {
        this.changeSummary = changeSummary;
    }

    public Boolean getHasMajorChanges() {
        return hasMajorChanges;
    }

    public void setHasMajorChanges(Boolean hasMajorChanges) {
        this.hasMajorChanges = hasMajorChanges;
    }
}
