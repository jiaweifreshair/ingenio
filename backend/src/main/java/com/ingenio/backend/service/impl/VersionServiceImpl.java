package com.ingenio.backend.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.ingenio.backend.dto.CreateVersionRequest;
import com.ingenio.backend.dto.VersionCompareResult;
import com.ingenio.backend.dto.VersionDTO;
import com.ingenio.backend.entity.AppSpecEntity;
import com.ingenio.backend.mapper.AppSpecMapper;
import com.ingenio.backend.service.VersionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 版本管理服务实现
 * 提供版本创建、查询、对比等核心功能
 * 支持版本链管理和版本快照存储
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class VersionServiceImpl implements VersionService {

    private final AppSpecMapper appSpecMapper;

    /**
     * 创建新版本
     * 基于父版本创建新的版本记录，版本号自动递增
     *
     * @param request 创建版本请求，包含父版本ID、租户ID、用户ID
     * @return 新创建的版本实体
     * @throws RuntimeException 当父版本不存在时抛出异常
     */
    @Override
    @Transactional
    public AppSpecEntity createVersion(CreateVersionRequest request) {
        AppSpecEntity parentVersion = appSpecMapper.selectById(request.getParentVersionId());
        if (parentVersion == null) {
            throw new RuntimeException("父版本不存在");
        }

        AppSpecEntity newVersion = new AppSpecEntity();
        newVersion.setTenantId(request.getTenantId());
        newVersion.setCreatedByUserId(request.getUserId());
        newVersion.setParentVersionId(request.getParentVersionId());
        newVersion.setVersion(parentVersion.getVersion() + 1);
        newVersion.setSpecContent(parentVersion.getSpecContent());
        newVersion.setStatus("draft");

        appSpecMapper.insert(newVersion);
        log.info("创建新版本成功: {}", newVersion.getId());
        return newVersion;
    }

    @Override
    public List<VersionDTO> listVersions(UUID tenantId, UUID userId) {
        QueryWrapper<AppSpecEntity> wrapper = new QueryWrapper<>();
        wrapper.eq("tenant_id", tenantId)
               .eq("created_by_user_id", userId)
               .orderByDesc("created_at");

        List<AppSpecEntity> entities = appSpecMapper.selectList(wrapper);
        return entities.stream()
                .map(this::toDTO)
                .collect(Collectors.toList());
    }

    @Override
    public VersionDTO getVersion(UUID versionId) {
        AppSpecEntity entity = appSpecMapper.selectById(versionId);
        if (entity == null) {
            throw new RuntimeException("版本不存在");
        }
        return toDTO(entity);
    }

    @Override
    public VersionCompareResult compareVersions(UUID sourceVersionId, UUID targetVersionId) {
        AppSpecEntity source = appSpecMapper.selectById(sourceVersionId);
        AppSpecEntity target = appSpecMapper.selectById(targetVersionId);

        if (source == null || target == null) {
            throw new RuntimeException("版本不存在");
        }

        Map<String, Object> differences = new HashMap<>();
        differences.put("versionDiff", target.getVersion() - source.getVersion());
        differences.put("statusChanged", !Objects.equals(source.getStatus(), target.getStatus()));
        differences.put("styleChanged", !Objects.equals(source.getSelectedStyle(), target.getSelectedStyle()));
        differences.put("intentChanged", !Objects.equals(source.getIntentType(), target.getIntentType()));

        return VersionCompareResult.builder()
                .sourceVersion(toDTO(source))
                .targetVersion(toDTO(target))
                .differences(differences)
                .build();
    }

    private VersionDTO toDTO(AppSpecEntity entity) {
        return VersionDTO.builder()
                .id(entity.getId())
                .version(entity.getVersion())
                .parentVersionId(entity.getParentVersionId())
                .status(entity.getStatus())
                .qualityScore(entity.getQualityScore())
                .intentType(entity.getIntentType())
                .selectedStyle(entity.getSelectedStyle())
                .designConfirmed(entity.getDesignConfirmed())
                .createdAt(entity.getCreatedAt())
                .updatedAt(entity.getUpdatedAt())
                .build();
    }
}
