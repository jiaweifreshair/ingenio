package com.ingenio.backend.service;

import com.ingenio.backend.dto.CreateVersionRequest;
import com.ingenio.backend.dto.VersionCompareResult;
import com.ingenio.backend.dto.VersionDTO;
import com.ingenio.backend.entity.AppSpecEntity;

import java.util.List;
import java.util.UUID;

/**
 * 版本管理服务
 */
public interface VersionService {

    /**
     * 创建新版本（从现有版本派生）
     */
    AppSpecEntity createVersion(CreateVersionRequest request);

    /**
     * 查询项目的版本列表
     */
    List<VersionDTO> listVersions(UUID tenantId, UUID userId);

    /**
     * 查询版本详情
     */
    VersionDTO getVersion(UUID versionId);

    /**
     * 版本对比
     */
    VersionCompareResult compareVersions(UUID sourceVersionId, UUID targetVersionId);
}
