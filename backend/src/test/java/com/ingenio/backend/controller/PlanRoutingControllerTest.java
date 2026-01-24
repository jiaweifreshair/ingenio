package com.ingenio.backend.controller;

import com.ingenio.backend.common.response.Result;
import com.ingenio.backend.dto.request.PlanRoutingRequest;
import com.ingenio.backend.entity.AppSpecEntity;
import com.ingenio.backend.mapper.UserMapper;
import com.ingenio.backend.service.AppSpecService;
import com.ingenio.backend.service.BillingService;
import com.ingenio.backend.service.OpenLovableService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * PlanRoutingController 单元测试
 *
 * 覆盖点：
 * - /v2/plan-routing/route 支持复用 appSpecId（需求修改不新建记录）
 * - /v2/plan-routing/{id}/update-requirement 支持在原型确认前持续更新需求
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PlanRoutingControllerTest {

    @Mock
    private AppSpecService appSpecService;

    @Mock
    private BillingService billingService;

    /**
     * OpenLovable 服务 Mock。
     *
     * 是什么：用于替代原型生成服务的测试替身。
     * 做什么：满足控制器构造依赖，避免真实外部调用。
     * 为什么：保证单测仅验证路由逻辑，不受外部服务影响。
     */
    @Mock
    private OpenLovableService openLovableService;

    @Mock
    private UserMapper userMapper;

    private PlanRoutingController controller;

    @BeforeEach
    void setUp() {
        controller = new PlanRoutingController(appSpecService, billingService, openLovableService, userMapper);
    }

    @Test
    void routeRequirement_createsNewAppSpec_whenAppSpecIdMissing() {
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID appSpecId = UUID.randomUUID();

        when(appSpecService.createAppSpec(eq(tenantId), eq(userId), any())).thenAnswer(invocation -> {
            Map<String, Object> specContent = invocation.getArgument(2);
            return AppSpecEntity.builder()
                    .id(appSpecId)
                    .tenantId(tenantId)
                    .createdByUserId(userId)
                    .specContent(specContent)
                    .metadata(new HashMap<>())
                    .build();
        });
        when(appSpecService.updateById(any())).thenReturn(true);

        PlanRoutingRequest req = PlanRoutingRequest.builder()
                .userRequirement("创建一个用于安全事故管理的企业后台系统，包含上报/审核/整改闭环/统计看板")
                .tenantId(tenantId)
                .userId(userId)
                .build();

        Result<PlanRoutingController.PlanRoutingResult> resp = controller.routeRequirement(req);

        assertTrue(resp.isSuccess());
        assertNotNull(resp.getData());
        assertEquals(appSpecId.toString(), resp.getData().getAppSpecId());
        verify(appSpecService, times(1)).createAppSpec(eq(tenantId), eq(userId), any());
        verify(appSpecService, times(1)).updateById(any(AppSpecEntity.class));
    }

    @Test
    void routeRequirement_reusesExistingAppSpec_whenAppSpecIdProvided() {
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID appSpecId = UUID.randomUUID();

        AppSpecEntity existing = AppSpecEntity.builder()
                .id(appSpecId)
                .tenantId(tenantId)
                .createdByUserId(userId)
                .specContent(new HashMap<>(Map.of("userRequirement", "旧需求")))
                .metadata(new HashMap<>())
                .build();

        when(appSpecService.getById(appSpecId)).thenReturn(existing);
        when(appSpecService.updateById(any())).thenReturn(true);

        PlanRoutingRequest req = PlanRoutingRequest.builder()
                .appSpecId(appSpecId)
                .userRequirement("新需求：增加整改闭环超时提醒与权限控制")
                .tenantId(tenantId)
                .userId(userId)
                .build();

        Result<PlanRoutingController.PlanRoutingResult> resp = controller.routeRequirement(req);

        assertTrue(resp.isSuccess());
        assertNotNull(resp.getData());
        assertEquals(appSpecId.toString(), resp.getData().getAppSpecId());
        verify(appSpecService, never()).createAppSpec(any(), any(), any());

        ArgumentCaptor<AppSpecEntity> captor = ArgumentCaptor.forClass(AppSpecEntity.class);
        verify(appSpecService).updateById(captor.capture());
        AppSpecEntity updated = captor.getValue();
        assertNotNull(updated.getSpecContent());
        assertEquals("新需求：增加整改闭环超时提醒与权限控制", updated.getSpecContent().get("userRequirement"));
    }

    @Test
    void updateRequirement_updatesSpecContentOnly() {
        UUID appSpecId = UUID.randomUUID();
        AppSpecEntity existing = AppSpecEntity.builder()
                .id(appSpecId)
                .tenantId(UUID.randomUUID())
                .createdByUserId(UUID.randomUUID())
                .specContent(new HashMap<>(Map.of("userRequirement", "旧需求", "stage", "planning")))
                .metadata(new HashMap<>())
                .build();

        when(appSpecService.getById(appSpecId)).thenReturn(existing);
        when(appSpecService.updateById(any())).thenReturn(true);

        PlanRoutingController.UpdateRequirementRequest req = new PlanRoutingController.UpdateRequirementRequest();
        req.setUserRequirement("更新后的需求（通过原型阶段Chat修改）");

        Result<Map<String, Object>> resp = controller.updateRequirement(appSpecId.toString(), req);

        assertTrue(resp.isSuccess());
        verify(appSpecService).updateById(any(AppSpecEntity.class));
        assertEquals("更新后的需求（通过原型阶段Chat修改）", existing.getSpecContent().get("userRequirement"));
    }
}
