package com.ingenio.backend.dto;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

/**
 * 版本时间线条目
 *
 * 用于时光机UI展示版本历史
 */
@Data
@Builder
public class VersionTimelineItem {

    /**
     * 版本ID
     */
    private UUID versionId;

    /**
     * 版本号
     */
    private Integer versionNumber;

    /**
     * 版本类型
     */
    private String versionType;

    /**
     * 版本类型显示名称
     */
    private String versionTypeDisplay;

    /**
     * 时间戳
     */
    private Instant timestamp;

    /**
     * 摘要说明
     * 例如: "生成3个实体，覆盖率87%"
     */
    private String summary;

    /**
     * 状态
     * success/failed/in_progress
     */
    private String status;

    /**
     * 是否可回滚
     */
    @Builder.Default
    private Boolean canRollback = true;

    /**
     * 父版本ID
     */
    private UUID parentVersionId;
}
