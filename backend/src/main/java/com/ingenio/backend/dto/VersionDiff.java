package com.ingenio.backend.dto;

import com.ingenio.backend.entity.GenerationVersionEntity;
import lombok.Builder;
import lombok.Data;

import java.util.Map;

/**
 * 版本差异对比结果
 *
 * 用于时光机功能的版本对比
 */
@Data
@Builder
public class VersionDiff {

    /**
     * 版本1
     */
    private GenerationVersionEntity version1;

    /**
     * 版本2
     */
    private GenerationVersionEntity version2;

    /**
     * 差异详情
     * 使用JSON Diff格式，记录所有字段的变更
     */
    private Map<String, Object> differences;

    /**
     * 变更数量
     */
    private Integer changeCount;

    /**
     * 变更摘要
     * 人类可读的变更说明
     */
    private String changeSummary;

    /**
     * 是否有重大变更
     * true: 代码逻辑变更、Schema变更
     * false: 仅注释、格式调整
     */
    @Builder.Default
    private Boolean hasMajorChanges = false;
}
