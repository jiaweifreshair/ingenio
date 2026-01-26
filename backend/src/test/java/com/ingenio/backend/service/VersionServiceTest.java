package com.ingenio.backend.service;

import com.ingenio.backend.dto.CreateVersionRequest;
import com.ingenio.backend.dto.VersionCompareResult;
import com.ingenio.backend.dto.VersionDTO;
import com.ingenio.backend.entity.AppSpecEntity;
import com.ingenio.backend.mapper.AppSpecMapper;
import com.ingenio.backend.service.impl.VersionServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class VersionServiceTest {

    @Mock
    private AppSpecMapper appSpecMapper;

    @InjectMocks
    private VersionServiceImpl versionService;

    private UUID testTenantId;
    private UUID testUserId;
    private UUID testVersionId;
    private AppSpecEntity testAppSpec;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        testTenantId = UUID.randomUUID();
        testUserId = UUID.randomUUID();
        testVersionId = UUID.randomUUID();

        testAppSpec = new AppSpecEntity();
        testAppSpec.setId(testVersionId);
        testAppSpec.setTenantId(testTenantId);
        testAppSpec.setCreatedByUserId(testUserId);
        testAppSpec.setVersion(1);
        testAppSpec.setStatus("draft");
    }

    @Test
    void testCreateVersion_Success() {
        when(appSpecMapper.selectById(testVersionId)).thenReturn(testAppSpec);
        when(appSpecMapper.insert(any(AppSpecEntity.class))).thenReturn(1);

        CreateVersionRequest request = CreateVersionRequest.builder()
                .parentVersionId(testVersionId)
                .tenantId(testTenantId)
                .userId(testUserId)
                .build();

        AppSpecEntity newVersion = versionService.createVersion(request);

        assertNotNull(newVersion);
        assertEquals(2, newVersion.getVersion());
        assertEquals(testVersionId, newVersion.getParentVersionId());
        assertEquals("draft", newVersion.getStatus());
        verify(appSpecMapper).insert(any(AppSpecEntity.class));
    }

    @Test
    void testCreateVersion_ParentNotFound() {
        when(appSpecMapper.selectById(testVersionId)).thenReturn(null);

        CreateVersionRequest request = CreateVersionRequest.builder()
                .parentVersionId(testVersionId)
                .tenantId(testTenantId)
                .userId(testUserId)
                .build();

        assertThrows(RuntimeException.class, () -> versionService.createVersion(request));
    }

    @Test
    void testListVersions_Success() {
        AppSpecEntity version1 = new AppSpecEntity();
        version1.setId(UUID.randomUUID());
        version1.setVersion(1);
        version1.setStatus("draft");

        AppSpecEntity version2 = new AppSpecEntity();
        version2.setId(UUID.randomUUID());
        version2.setVersion(2);
        version2.setStatus("validated");

        when(appSpecMapper.selectList(any())).thenReturn(Arrays.asList(version1, version2));

        List<VersionDTO> versions = versionService.listVersions(testTenantId, testUserId);

        assertEquals(2, versions.size());
        assertEquals(1, versions.get(0).getVersion());
        assertEquals(2, versions.get(1).getVersion());
    }

    @Test
    void testGetVersion_Success() {
        when(appSpecMapper.selectById(testVersionId)).thenReturn(testAppSpec);

        VersionDTO version = versionService.getVersion(testVersionId);

        assertNotNull(version);
        assertEquals(testVersionId, version.getId());
        assertEquals(1, version.getVersion());
        assertEquals("draft", version.getStatus());
    }

    @Test
    void testGetVersion_NotFound() {
        when(appSpecMapper.selectById(testVersionId)).thenReturn(null);

        assertThrows(RuntimeException.class, () -> versionService.getVersion(testVersionId));
    }

    @Test
    void testCompareVersions_Success() {
        UUID sourceId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();

        AppSpecEntity source = new AppSpecEntity();
        source.setId(sourceId);
        source.setVersion(1);
        source.setStatus("draft");
        source.setSelectedStyle("modern_minimal");

        AppSpecEntity target = new AppSpecEntity();
        target.setId(targetId);
        target.setVersion(2);
        target.setStatus("validated");
        target.setSelectedStyle("vibrant_fashion");

        when(appSpecMapper.selectById(sourceId)).thenReturn(source);
        when(appSpecMapper.selectById(targetId)).thenReturn(target);

        VersionCompareResult result = versionService.compareVersions(sourceId, targetId);

        assertNotNull(result);
        assertEquals(1, result.getSourceVersion().getVersion());
        assertEquals(2, result.getTargetVersion().getVersion());
        assertEquals(1, result.getDifferences().get("versionDiff"));
        assertTrue((Boolean) result.getDifferences().get("statusChanged"));
        assertTrue((Boolean) result.getDifferences().get("styleChanged"));
    }
}
