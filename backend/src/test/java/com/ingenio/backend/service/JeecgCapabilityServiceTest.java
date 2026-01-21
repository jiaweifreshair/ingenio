package com.ingenio.backend.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ingenio.backend.entity.JeecgCapabilityEntity;
import com.ingenio.backend.mapper.JeecgCapabilityMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * JeecgCapabilityService 测试
 */
@ExtendWith(MockitoExtension.class)
class JeecgCapabilityServiceTest {

    @Mock
    private JeecgCapabilityMapper capabilityMapper;

    @InjectMocks
    private JeecgCapabilityService capabilityService;

    private JeecgCapabilityEntity testCapability;

    @BeforeEach
    void setUp() {
        testCapability = new JeecgCapabilityEntity();
        testCapability.setId(UUID.randomUUID());
        testCapability.setCode("auth");
        testCapability.setName("用户认证");
        testCapability.setCategory("infrastructure");
        testCapability.setIsActive(true);
        testCapability.setSortOrder(100);
    }

    @Test
    void testListAllActive() {
        // Given
        List<JeecgCapabilityEntity> capabilities = Arrays.asList(testCapability);
        when(capabilityMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(capabilities);

        // When
        List<JeecgCapabilityEntity> result = capabilityService.listAllActive();

        // Then
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("auth", result.get(0).getCode());
        verify(capabilityMapper, times(1)).selectList(any(LambdaQueryWrapper.class));
    }

    @Test
    void testGetByCode() {
        // Given
        when(capabilityMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(testCapability);

        // When
        Optional<JeecgCapabilityEntity> result = capabilityService.getByCode("auth");

        // Then
        assertTrue(result.isPresent());
        assertEquals("auth", result.get().getCode());
        assertEquals("用户认证", result.get().getName());
        verify(capabilityMapper, times(1)).selectOne(any(LambdaQueryWrapper.class));
    }

    @Test
    void testGetByCodeNotFound() {
        // Given
        when(capabilityMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);

        // When
        Optional<JeecgCapabilityEntity> result = capabilityService.getByCode("nonexistent");

        // Then
        assertFalse(result.isPresent());
        verify(capabilityMapper, times(1)).selectOne(any(LambdaQueryWrapper.class));
    }

    @Test
    void testListByCategory() {
        // Given
        List<JeecgCapabilityEntity> capabilities = Arrays.asList(testCapability);
        when(capabilityMapper.selectList(any(LambdaQueryWrapper.class))).thenReturn(capabilities);

        // When
        List<JeecgCapabilityEntity> result = capabilityService.listByCategory("infrastructure");

        // Then
        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("infrastructure", result.get(0).getCategory());
        verify(capabilityMapper, times(1)).selectList(any(LambdaQueryWrapper.class));
    }

    @Test
    void testCheckConflicts() {
        // Given
        JeecgCapabilityEntity conflictingCapability = new JeecgCapabilityEntity();
        conflictingCapability.setCode("payment_wechat");
        conflictingCapability.setConflicts(Arrays.asList("payment_alipay"));

        when(capabilityMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(conflictingCapability);

        // When
        List<String> conflicts = capabilityService.checkConflicts(
            Arrays.asList("payment_alipay", "auth"),
            "payment_wechat"
        );

        // Then
        assertNotNull(conflicts);
        assertEquals(1, conflicts.size());
        assertEquals("payment_alipay", conflicts.get(0));
        verify(capabilityMapper, times(1)).selectOne(any(LambdaQueryWrapper.class));
    }
}
