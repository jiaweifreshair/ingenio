package com.ingenio.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 版本对比结果
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VersionCompareResult {

    private VersionDTO sourceVersion;
    private VersionDTO targetVersion;
    private Map<String, Object> differences;
}
