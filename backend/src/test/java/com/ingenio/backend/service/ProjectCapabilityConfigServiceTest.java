package com.ingenio.backend.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ingenio.backend.entity.JeecgCapabilityEntity;
import com.ingenio.backend.entity.ProjectCapabilityConfigEntity;
import com.ingenio.backend.mapper.ProjectCapabilityConfigMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * ProjectCapabilityConfigService 测试
 */
@ExtendWith(MockitoExtension.class)
class ProjectCapabilityConfigServiceTest {

    @Mock
    private ProjectCapabilityConfigMapper configMapper;

    @Mock
    private JeecgCapabilityService capabilityService;

    @Mock
    private CapabilityConfigEncryptService encryptService;

    @InjectMocks
    private ProjectCapabilityConfigService configService;

    private UUID testProjectId;
    private ProjectCapabilityConfigEntity testConfig;
    private JeecgCapabilityEntity testCapability;

    @BeforeEach
    void setUp() {
        testProjectId = UUID.randomUUID();

        testCapability = new JeecgCapabilityEntity();
        testCapability.setId(UUID.randomUUID());
        testCapability.setCode("auth");
        testCapability.setName("用户认证");
        testCapability.setConfigTemplate(Map.of(
            "apiKey", Map.of("type", "string", "encrypted", true),
            "apiSecret", Map.of("type", "string", "encrypted", true)
        ));

        testConfig = new ProjectCapabilityConfigEntity();
        testConfig.setId(UUID.randomUUID());
        testConfig.setProjectId(testProjectId);
        testConfig.setCapabilityId(testCapability.getId());
        testConfig.setCapabilityCode("auth");
        testConfig.setConfigValues(Map.of(
            "apiKey", "test-key",
            "apiSecret", "test-secret"
        ));
        testConfig.setStatus("pending");
    }

    @Test
    void testAddConfig() {
        // Given
        when(capabilityService.getByCode("auth")).thenReturn(Optional.of(testCapability));
        when(encryptService.encryptSensitiveFields(any(), any())).thenReturn(testConfig.getConfigValues());
        when(configMapper.insert(any(ProjectCapabilityConfigEntity.class))).thenReturn(1);

        // When
        ProjectCapabilityConfigEntity result = configService.addConfig(
            testProjectId,
            "auth",
            Map.of("apiKey", "test-key", "apiSecret", "test-secret")
        );

        // Then
        assertNotNull(result);
        assertEquals(testProjectId, result.getProjectId());
        assertEquals("auth", result.getCapabilityCode());
        verify(capabilityService, times(1)).getByCode("auth");
        verify(encryptService, times(1)).encryptSensitiveFields(any(), any());
        verify(configMapper, times(1)).insert(any(ProjectCapabilityConfigEntity.class));
    }

    @Test
    void testAddConfigCapabilityNotFound() {
        // Given
        when(capabilityService.getByCode("nonexistent")).thenReturn(Optional.empty());

        // When & Then
        assertThrows(IllegalArgumentException.class, () -> {
            configService.addConfig(testProjectId, "nonexistent", Map.of());
        });

        verify(capabilityService, times(1)).getByCode("nonexistent");
        verify(configMapper, never()).insert(any(ProjectCapabilityConfigEntity.class));
    }

    @Test
    void testListByProject() {
        // Given
        List<ProjectCapabilityConfigEntity> configs = Arrays.asList(testConfig);
        when(configMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(configs);

        // When
        List<ProjectCapabilityConfigEntity> result = configService.listByProject(testProjectId);

        // Then
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals(testProjectId, result.get(0).getProjectId());
        verify(configMapper, times(1)).selectList(any(LambdaQueryWrapper.class));
    }

    @Test
    void testUpdateConfig() {
        // Given
        Map<String, Object> newConfigValues = Map.of("apiKey", "new-key");
        when(configMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(testConfig);
        when(capabilityService.getByCode("auth")).thenReturn(Optional.of(testCapability));
        when(encryptService.encryptSensitiveFields(any(), any())).thenReturn(newConfigValues);
        when(configMapper.updateById(any(ProjectCapabilityConfigEntity.class))).thenReturn(1);

        // When
        ProjectCapabilityConfigEntity result = configService.updateConfig(
            testProjectId,
            "auth",
            newConfigValues
        );

        // Then
        assertNotNull(result);
        verify(configMapper, times(1)).selectOne(any(LambdaQueryWrapper.class));
        verify(encryptService, times(1)).encryptSensitiveFields(any(), any());
        verify(configMapper, times(1)).updateById(any(ProjectCapabilityConfigEntity.class));
    }

    @Test
    void testDeleteConfig() {
        // Given
        when(configMapper.delete(any(LambdaQueryWrapper.class))).thenReturn(1);

        // When
        configService.deleteConfig(testProjectId, "auth");

        // Then
        verify(configMapper, times(1)).delete(any(LambdaQueryWrapper.class));
    }

    @Test
    void testGetConfiguredCapabilityCodes() {
        // Given
        List<ProjectCapabilityConfigEntity> configs = Arrays.asList(testConfig);
        when(configMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(configs);

        // When
        List<String> result = configService.getConfiguredCapabilityCodes(testProjectId);

        // Then
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("auth", result.get(0));
        verify(configMapper, times(1)).selectList(any(LambdaQueryWrapper.class));
    }

    @Test
    void testGetByProjectAndCodeMasked() {
        // Given
        Map<String, Object> maskedValues = Map.of(
            "apiKey", "******",
            "apiSecret", "******"
        );
        when(configMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(testConfig);
        when(capabilityService.getByCode("auth")).thenReturn(Optional.of(testCapability));
        when(encryptService.maskSensitiveFields(any(), any())).thenReturn(maskedValues);

        // When
        Optional<ProjectCapabilityConfigEntity> result = configService.getByProjectAndCodeMasked(
            testProjectId,
            "auth"
        );

        // Then
        assertTrue(result.isPresent());
        assertEquals(maskedValues, result.get().getConfigValues());
        verify(encryptService, times(1)).maskSensitiveFields(any(), any());
    }

    @Test
    void testValidateConfigUsesBasicValidation() {
        // Given
        when(configMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(testConfig);
        when(capabilityService.getByCode("auth")).thenReturn(Optional.of(testCapability));
        when(configMapper.updateById(any(ProjectCapabilityConfigEntity.class))).thenReturn(1);

        // When
        boolean result = configService.validateConfig(testProjectId, "auth");

        // Then
        assertTrue(result);
        assertEquals(ProjectCapabilityConfigEntity.STATUS_VALIDATED, testConfig.getStatus());
        assertNull(testConfig.getValidationError());
        verify(capabilityService, times(1)).getByCode("auth");
    }
}
