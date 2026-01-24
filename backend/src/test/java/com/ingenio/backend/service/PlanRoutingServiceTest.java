package com.ingenio.backend.service;

import com.ingenio.backend.common.response.Result;
import com.ingenio.backend.controller.PlanRoutingController;
import com.ingenio.backend.mapper.UserMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * PlanRoutingServiceTest.
 *
 * 是什么：PlanRouting 相关流程的最小单元测试入口。
 * 做什么：验证关键 API 的基础行为（AppSpec 不存在时返回 404）。
 * 为什么：保证 V2 路由核心接口具备可预期的错误响应。
 */
class PlanRoutingServiceTest {

    /**
     * 验证选择风格时 AppSpec 不存在的返回值。
     *
     * 是什么：选择风格异常场景测试。
     * 做什么：模拟 AppSpec 缺失，断言返回 404。
     * 为什么：避免前端收到 500，确保错误可识别。
     */
    @Test
    @DisplayName("selectStyle: AppSpec 不存在时返回 404")
    void shouldReturn404WhenSelectStyleMissingAppSpec() {
        AppSpecService appSpecService = Mockito.mock(AppSpecService.class);
        BillingService billingService = Mockito.mock(BillingService.class);
        OpenLovableService openLovableService = Mockito.mock(OpenLovableService.class);
        UserMapper userMapper = Mockito.mock(UserMapper.class);

        PlanRoutingController controller = new PlanRoutingController(
                appSpecService,
                billingService,
                openLovableService,
                userMapper);

        Mockito.when(appSpecService.getById(Mockito.any())).thenReturn(null);

        Result<PlanRoutingController.PlanRoutingResult> result = controller.selectStyle(
                "123e4567-e89b-12d3-a456-426614174000",
                "modern_minimal");

        assertEquals("404", result.getCode());
        assertFalse(result.isSuccess());
    }
}
