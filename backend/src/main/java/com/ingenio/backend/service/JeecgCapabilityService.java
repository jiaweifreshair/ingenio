package com.ingenio.backend.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ingenio.backend.entity.JeecgCapabilityEntity;
import com.ingenio.backend.mapper.JeecgCapabilityMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * JeecgBoot能力清单服务
 */
@Service
public class JeecgCapabilityService {

    private static final Logger log = LoggerFactory.getLogger(JeecgCapabilityService.class);

    @Autowired
    private JeecgCapabilityMapper capabilityMapper;

    public List<JeecgCapabilityEntity> listAllActive() {
        log.info("查询所有启用的JeecgBoot能力");

        LambdaQueryWrapper<JeecgCapabilityEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(JeecgCapabilityEntity::getIsActive, true)
                .orderByAsc(JeecgCapabilityEntity::getSortOrder)
                .orderByAsc(JeecgCapabilityEntity::getCode);

        List<JeecgCapabilityEntity> capabilities = capabilityMapper.selectList(wrapper);
        log.info("找到 {} 个启用的能力", capabilities.size());

        return capabilities;
    }

    public List<JeecgCapabilityEntity> listByCategory(String category) {
        log.info("按分类查询JeecgBoot能力: {}", category);

        LambdaQueryWrapper<JeecgCapabilityEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(JeecgCapabilityEntity::getCategory, category)
                .eq(JeecgCapabilityEntity::getIsActive, true)
                .orderByAsc(JeecgCapabilityEntity::getSortOrder);

        List<JeecgCapabilityEntity> capabilities = capabilityMapper.selectList(wrapper);
        log.info("分类 {} 下找到 {} 个能力", category, capabilities.size());

        return capabilities;
    }

    public Optional<JeecgCapabilityEntity> getByCode(String code) {
        log.info("按代码查询JeecgBoot能力: {}", code);

        LambdaQueryWrapper<JeecgCapabilityEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(JeecgCapabilityEntity::getCode, code)
                .eq(JeecgCapabilityEntity::getIsActive, true);

        JeecgCapabilityEntity capability = capabilityMapper.selectOne(wrapper);

        if (capability != null) {
            log.info("找到能力: {} - {}", code, capability.getName());
        } else {
            log.warn("未找到能力: {}", code);
        }

        return Optional.ofNullable(capability);
    }

    public Optional<JeecgCapabilityEntity> getById(UUID id) {
        log.info("按ID查询JeecgBoot能力: {}", id);

        JeecgCapabilityEntity capability = capabilityMapper.selectById(id);
        return Optional.ofNullable(capability);
    }

    public List<JeecgCapabilityEntity> listByCodes(List<String> codes) {
        log.info("批量查询JeecgBoot能力: {}", codes);

        if (codes == null || codes.isEmpty()) {
            return List.of();
        }

        LambdaQueryWrapper<JeecgCapabilityEntity> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(JeecgCapabilityEntity::getCode, codes)
                .eq(JeecgCapabilityEntity::getIsActive, true);

        List<JeecgCapabilityEntity> capabilities = capabilityMapper.selectList(wrapper);
        log.info("批量查询找到 {} 个能力", capabilities.size());

        return capabilities;
    }

    public List<JeecgCapabilityEntity> getWithDependencies(String code) {
        log.info("分析能力依赖关系: {}", code);

        Optional<JeecgCapabilityEntity> capabilityOpt = getByCode(code);
        if (capabilityOpt.isEmpty()) {
            log.warn("能力不存在: {}", code);
            return List.of();
        }

        JeecgCapabilityEntity capability = capabilityOpt.get();
        List<String> dependencies = capability.getDependencies();

        if (dependencies == null || dependencies.isEmpty()) {
            log.info("能力 {} 没有依赖", code);
            return List.of(capability);
        }

        List<JeecgCapabilityEntity> allCapabilities = new java.util.ArrayList<>();
        allCapabilities.add(capability);

        for (String depCode : dependencies) {
            List<JeecgCapabilityEntity> depCapabilities = getWithDependencies(depCode);
            for (JeecgCapabilityEntity dep : depCapabilities) {
                if (allCapabilities.stream().noneMatch(c -> c.getCode().equals(dep.getCode()))) {
                    allCapabilities.add(dep);
                }
            }
        }

        log.info("能力 {} 及其依赖共 {} 个", code, allCapabilities.size());
        return allCapabilities;
    }

    public List<String> checkConflicts(List<String> selectedCodes, String newCode) {
        log.info("检查能力冲突: 已选择 {}, 新增 {}", selectedCodes, newCode);

        Optional<JeecgCapabilityEntity> newCapabilityOpt = getByCode(newCode);
        if (newCapabilityOpt.isEmpty()) {
            return List.of();
        }

        JeecgCapabilityEntity newCapability = newCapabilityOpt.get();
        List<String> conflicts = newCapability.getConflicts();

        if (conflicts == null || conflicts.isEmpty()) {
            return List.of();
        }

        List<String> conflictingCodes = selectedCodes.stream()
                .filter(conflicts::contains)
                .toList();

        if (!conflictingCodes.isEmpty()) {
            log.warn("能力 {} 与已选择的 {} 冲突", newCode, conflictingCodes);
        }

        return conflictingCodes;
    }

    public java.util.Map<String, Long> getStatistics() {
        log.info("获取JeecgBoot能力统计信息");

        List<JeecgCapabilityEntity> all = listAllActive();

        return all.stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        JeecgCapabilityEntity::getCategory,
                        java.util.stream.Collectors.counting()));
    }
}
