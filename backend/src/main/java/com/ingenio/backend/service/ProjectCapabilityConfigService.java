package com.ingenio.backend.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ingenio.backend.entity.JeecgCapabilityEntity;
import com.ingenio.backend.entity.ProjectCapabilityConfigEntity;
import com.ingenio.backend.mapper.ProjectCapabilityConfigMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

/**
 * 项目能力配置服务
 *
 * G3引擎JeecgBoot能力集成 - 配置管理业务层
 *
 * 核心功能：
 * 1. 配置CRUD：为项目添加、修改、删除能力配置
 * 2. 配置加密：敏感字段自动加密存储
 * 3. 配置验证：验证配置的连通性和有效性
 * 4. 配置掩码：API返回时掩码敏感信息
 *
 * 使用场景：
 * - 用户在项目向导中配置JeecgBoot能力参数
 * - G3引擎生成代码时读取解密后的配置
 * - 管理后台查看配置状态（掩码显示）
 *
 * @author Claude
 * @since 2025-01-08 (G3引擎JeecgBoot能力集成)
 */
@Slf4j
@Service
public class ProjectCapabilityConfigService {

    @Autowired
    private ProjectCapabilityConfigMapper configMapper;

    @Autowired
    private JeecgCapabilityService capabilityService;

    @Autowired
    private CapabilityConfigEncryptService encryptService;

    /**
     * 获取项目的所有能力配置
     *
     * @param projectId 项目ID
     * @return 配置列表
     */
    public List<ProjectCapabilityConfigEntity> listByProject(UUID projectId) {
        log.info("查询项目能力配置: projectId={}", projectId);

        LambdaQueryWrapper<ProjectCapabilityConfigEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ProjectCapabilityConfigEntity::getProjectId, projectId)
               .orderByAsc(ProjectCapabilityConfigEntity::getCreatedAt);

        List<ProjectCapabilityConfigEntity> configs = configMapper.selectList(wrapper);
        log.info("项目 {} 共有 {} 个能力配置", projectId, configs.size());

        return configs;
    }

    /**
     * 获取项目的所有能力配置（掩码敏感信息）
     *
     * @param projectId 项目ID
     * @return 掩码后的配置列表
     */
    public List<ProjectCapabilityConfigEntity> listByProjectMasked(UUID projectId) {
        List<ProjectCapabilityConfigEntity> configs = listByProject(projectId);

        // 对每个配置进行掩码处理
        for (ProjectCapabilityConfigEntity config : configs) {
            maskConfigValues(config);
        }

        return configs;
    }

    /**
     * 按能力代码获取项目配置
     *
     * @param projectId 项目ID
     * @param capabilityCode 能力代码
     * @return 配置实体
     */
    public Optional<ProjectCapabilityConfigEntity> getByProjectAndCode(UUID projectId, String capabilityCode) {
        log.info("查询项目能力配置: projectId={}, code={}", projectId, capabilityCode);

        LambdaQueryWrapper<ProjectCapabilityConfigEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ProjectCapabilityConfigEntity::getProjectId, projectId)
               .eq(ProjectCapabilityConfigEntity::getCapabilityCode, capabilityCode);

        ProjectCapabilityConfigEntity config = configMapper.selectOne(wrapper);
        return Optional.ofNullable(config);
    }

    /**
     * 按能力代码获取项目配置（掩码敏感字段）
     *
     * 是什么：返回单条配置的掩码版本。
     * 做什么：对敏感字段进行掩码处理后返回给API。
     * 为什么：避免API返回真实密钥信息。
     *
     * @param projectId 项目ID
     * @param capabilityCode 能力代码
     * @return 掩码后的配置实体
     */
    public Optional<ProjectCapabilityConfigEntity> getByProjectAndCodeMasked(UUID projectId, String capabilityCode) {
        Optional<ProjectCapabilityConfigEntity> configOpt = getByProjectAndCode(projectId, capabilityCode);
        configOpt.ifPresent(this::maskConfigValues);
        return configOpt;
    }

    /**
     * 添加项目能力配置
     *
     * @param projectId 项目ID
     * @param capabilityCode 能力代码
     * @param configValues 配置值
     * @return 创建的配置实体
     */
    @Transactional
    public ProjectCapabilityConfigEntity addConfig(UUID projectId, String capabilityCode, Map<String, Object> configValues) {
        log.info("添加项目能力配置: projectId={}, code={}", projectId, capabilityCode);

        // 检查能力是否存在
        Optional<JeecgCapabilityEntity> capabilityOpt = capabilityService.getByCode(capabilityCode);
        if (capabilityOpt.isEmpty()) {
            throw new IllegalArgumentException("能力不存在: " + capabilityCode);
        }

        JeecgCapabilityEntity capability = capabilityOpt.get();

        // 检查是否已存在
        Optional<ProjectCapabilityConfigEntity> existingOpt = getByProjectAndCode(projectId, capabilityCode);
        if (existingOpt.isPresent()) {
            throw new IllegalArgumentException("项目已配置该能力: " + capabilityCode);
        }

        // 加密敏感字段
        Map<String, Object> encryptedValues = encryptService.encryptSensitiveFields(
            configValues,
            capability.getConfigTemplate()
        );

        // 创建配置实体
        ProjectCapabilityConfigEntity config = ProjectCapabilityConfigEntity.builder()
            .projectId(projectId)
            .capabilityId(capability.getId())
            .capabilityCode(capabilityCode)
            .configValues(encryptedValues)
            .status(ProjectCapabilityConfigEntity.STATUS_PENDING)
            .createdAt(Instant.now())
            .updatedAt(Instant.now())
            .build();

        configMapper.insert(config);
        log.info("能力配置创建成功: id={}", config.getId());

        return config;
    }

    /**
     * 更新项目能力配置
     *
     * @param projectId 项目ID
     * @param capabilityCode 能力代码
     * @param configValues 新的配置值
     * @return 更新后的配置实体
     */
    @Transactional
    public ProjectCapabilityConfigEntity updateConfig(UUID projectId, String capabilityCode, Map<String, Object> configValues) {
        log.info("更新项目能力配置: projectId={}, code={}", projectId, capabilityCode);

        // 获取现有配置
        Optional<ProjectCapabilityConfigEntity> existingOpt = getByProjectAndCode(projectId, capabilityCode);
        if (existingOpt.isEmpty()) {
            throw new IllegalArgumentException("配置不存在: " + capabilityCode);
        }

        ProjectCapabilityConfigEntity config = existingOpt.get();

        // 获取能力模板
        Optional<JeecgCapabilityEntity> capabilityOpt = capabilityService.getByCode(capabilityCode);
        if (capabilityOpt.isEmpty()) {
            throw new IllegalArgumentException("能力不存在: " + capabilityCode);
        }

        JeecgCapabilityEntity capability = capabilityOpt.get();

        // 加密敏感字段
        Map<String, Object> encryptedValues = encryptService.encryptSensitiveFields(
            configValues,
            capability.getConfigTemplate()
        );

        // 更新配置
        config.setConfigValues(encryptedValues);
        config.setStatus(ProjectCapabilityConfigEntity.STATUS_PENDING);
        config.setValidationError(null);
        config.setValidatedAt(null);
        config.setUpdatedAt(Instant.now());

        configMapper.updateById(config);
        log.info("能力配置更新成功: id={}", config.getId());

        return config;
    }

    /**
     * 删除项目能力配置
     *
     * @param projectId 项目ID
     * @param capabilityCode 能力代码
     */
    @Transactional
    public void deleteConfig(UUID projectId, String capabilityCode) {
        log.info("删除项目能力配置: projectId={}, code={}", projectId, capabilityCode);

        LambdaQueryWrapper<ProjectCapabilityConfigEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ProjectCapabilityConfigEntity::getProjectId, projectId)
               .eq(ProjectCapabilityConfigEntity::getCapabilityCode, capabilityCode);

        int deleted = configMapper.delete(wrapper);
        log.info("删除了 {} 条配置记录", deleted);
    }

    /**
     * 获取解密后的配置值
     *
     * 供G3引擎内部使用，不应直接暴露给API
     *
     * @param projectId 项目ID
     * @param capabilityCode 能力代码
     * @return 解密后的配置值
     */
    public Map<String, Object> getDecryptedConfig(UUID projectId, String capabilityCode) {
        log.info("获取解密配置: projectId={}, code={}", projectId, capabilityCode);

        Optional<ProjectCapabilityConfigEntity> configOpt = getByProjectAndCode(projectId, capabilityCode);
        if (configOpt.isEmpty()) {
            log.warn("配置不存在: projectId={}, code={}", projectId, capabilityCode);
            return Map.of();
        }

        ProjectCapabilityConfigEntity config = configOpt.get();
        return encryptService.decryptSensitiveFields(config.getConfigValues());
    }

    /**
     * 获取项目所有能力的解密配置
     *
     * 供G3引擎生成代码时使用
     *
     * @param projectId 项目ID
     * @return 能力代码 -> 解密配置值 的映射
     */
    public Map<String, Map<String, Object>> getAllDecryptedConfigs(UUID projectId) {
        log.info("获取项目所有解密配置: projectId={}", projectId);

        List<ProjectCapabilityConfigEntity> configs = listByProject(projectId);
        Map<String, Map<String, Object>> result = new HashMap<>();

        for (ProjectCapabilityConfigEntity config : configs) {
            Map<String, Object> decrypted = encryptService.decryptSensitiveFields(config.getConfigValues());
            result.put(config.getCapabilityCode(), decrypted);
        }

        log.info("获取了 {} 个能力的解密配置", result.size());
        return result;
    }

    /**
     * 验证配置
     *
     * 检验配置的连通性和有效性
     *
     * @param projectId 项目ID
     * @param capabilityCode 能力代码
     * @return 验证结果：true表示验证通过
     */
    @Transactional
    public boolean validateConfig(UUID projectId, String capabilityCode) {
        log.info("验证能力配置: projectId={}, code={}", projectId, capabilityCode);

        Optional<ProjectCapabilityConfigEntity> configOpt = getByProjectAndCode(projectId, capabilityCode);
        if (configOpt.isEmpty()) {
            throw new IllegalArgumentException("配置不存在: " + capabilityCode);
        }

        ProjectCapabilityConfigEntity config = configOpt.get();

        try {
            boolean isValid = validateBasicConfig(config);
            String errorMessage = null;
            if (!isValid) {
                errorMessage = "配置校验失败：缺少必填字段";
            }

            if (isValid) {
                config.setStatus(ProjectCapabilityConfigEntity.STATUS_VALIDATED);
                config.setValidationError(null);
            } else {
                config.setStatus(ProjectCapabilityConfigEntity.STATUS_FAILED);
                config.setValidationError(errorMessage);
            }

            config.setValidatedAt(Instant.now());
            config.setUpdatedAt(Instant.now());
            configMapper.updateById(config);

            log.info("配置验证结果: projectId={}, code={}, status={}",
                projectId, capabilityCode, config.getStatus());

            return isValid;

        } catch (Exception e) {
            log.error("配置验证异常: projectId={}, code={}", projectId, capabilityCode, e);

            config.setStatus(ProjectCapabilityConfigEntity.STATUS_FAILED);
            config.setValidationError("验证异常: " + e.getMessage());
            config.setValidatedAt(Instant.now());
            config.setUpdatedAt(Instant.now());
            configMapper.updateById(config);

            return false;
        }
    }

    /**
     * 基本配置校验
     *
     * @param config 配置实体
     * @return 是否通过基本校验
     */
    private boolean validateBasicConfig(ProjectCapabilityConfigEntity config) {
        Map<String, Object> values = config.getConfigValues();
        if (values == null || values.isEmpty()) {
            return false;
        }

        // 获取能力模板
        Optional<JeecgCapabilityEntity> capabilityOpt = capabilityService.getByCode(config.getCapabilityCode());
        if (capabilityOpt.isEmpty()) {
            return false;
        }

        JeecgCapabilityEntity capability = capabilityOpt.get();
        Map<String, Object> template = capability.getConfigTemplate();

        if (template == null || template.isEmpty()) {
            return true; // 没有配置模板，直接通过
        }

        // 检查必填字段
        for (Map.Entry<String, Object> entry : template.entrySet()) {
            String fieldName = entry.getKey();
            Object fieldConfig = entry.getValue();

            if (fieldConfig instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> fieldConfigMap = (Map<String, Object>) fieldConfig;
                Boolean required = (Boolean) fieldConfigMap.get("required");

                if (Boolean.TRUE.equals(required)) {
                    Object value = values.get(fieldName);
                    if (value == null || (value instanceof String && ((String) value).isEmpty())) {
                        log.warn("必填字段缺失: {}", fieldName);
                        return false;
                    }
                }
            }
        }

        return true;
    }

    /**
     * 对配置值进行掩码处理
     *
     * @param config 配置实体
     */
    private void maskConfigValues(ProjectCapabilityConfigEntity config) {
        if (config.getConfigValues() == null) {
            return;
        }

        Optional<JeecgCapabilityEntity> capabilityOpt = capabilityService.getByCode(config.getCapabilityCode());
        if (capabilityOpt.isEmpty()) {
            return;
        }

        JeecgCapabilityEntity capability = capabilityOpt.get();
        Map<String, Object> maskedValues = encryptService.maskSensitiveFields(
            config.getConfigValues(),
            capability.getConfigTemplate()
        );
        config.setConfigValues(maskedValues);
    }

    /**
     * 获取项目已配置的能力代码列表
     *
     * @param projectId 项目ID
     * @return 能力代码列表
     */
    public List<String> getConfiguredCapabilityCodes(UUID projectId) {
        List<ProjectCapabilityConfigEntity> configs = listByProject(projectId);
        return configs.stream()
            .map(ProjectCapabilityConfigEntity::getCapabilityCode)
            .toList();
    }

    /**
     * 批量添加能力配置
     *
     * @param projectId 项目ID
     * @param capabilityCodes 能力代码列表
     */
    @Transactional
    public void addConfigsBatch(UUID projectId, List<String> capabilityCodes) {
        log.info("批量添加能力配置: projectId={}, codes={}", projectId, capabilityCodes);

        for (String code : capabilityCodes) {
            try {
                addConfig(projectId, code, new HashMap<>());
            } catch (IllegalArgumentException e) {
                log.warn("批量添加跳过: {}", e.getMessage());
            }
        }
    }

    /**
     * 删除项目所有能力配置
     *
     * @param projectId 项目ID
     */
    @Transactional
    public void deleteAllByProject(UUID projectId) {
        log.info("删除项目所有能力配置: projectId={}", projectId);

        LambdaQueryWrapper<ProjectCapabilityConfigEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ProjectCapabilityConfigEntity::getProjectId, projectId);

        int deleted = configMapper.delete(wrapper);
        log.info("删除了 {} 条配置记录", deleted);
    }
}
