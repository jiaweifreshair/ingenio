package com.ingenio.backend.controller;

import com.ingenio.backend.common.Result;
import com.ingenio.backend.dto.billing.*;
import com.ingenio.backend.service.BillingService;
import com.ingenio.backend.service.PayOrderService;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * BillingController 单元测试
 * 仅测试不需要登录的接口（getPackages, alipayNotify）
 * 需要登录的接口通过集��测试覆盖
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class BillingControllerTest {

    @Mock
    private BillingService billingService;

    @Mock
    private PayOrderService payOrderService;

    private BillingController billingController;

    @BeforeEach
    void setUp() {
        billingController = new BillingController(billingService, payOrderService);
    }

    // ========== getPackages 测试（无需登录）==========

    @Test
    void getPackages_returnsAllPackages() {
        // Given
        List<CreditPackageDTO> packages = List.of(
                CreditPackageDTO.builder().code("PACK_10").name("基础套餐").credits(10).price(new BigDecimal("199.00")).build(),
                CreditPackageDTO.builder().code("PACK_30").name("标准套餐").credits(30).price(new BigDecimal("399.00")).build(),
                CreditPackageDTO.builder().code("PACK_80").name("专业套餐").credits(80).price(new BigDecimal("800.00")).build()
        );
        when(billingService.getPackages()).thenReturn(packages);

        // When
        Result<List<CreditPackageDTO>> result = billingController.getPackages();

        // Then
        assertTrue(result.getSuccess());
        assertEquals(3, result.getData().size());
        verify(billingService).getPackages();
    }

    @Test
    void getPackages_returnsCorrectPackageDetails() {
        // Given
        List<CreditPackageDTO> packages = List.of(
                CreditPackageDTO.builder()
                        .code("PACK_10")
                        .name("基础套餐")
                        .credits(10)
                        .price(new BigDecimal("199.00"))
                        .build()
        );
        when(billingService.getPackages()).thenReturn(packages);

        // When
        Result<List<CreditPackageDTO>> result = billingController.getPackages();

        // Then
        assertTrue(result.getSuccess());
        CreditPackageDTO pkg = result.getData().get(0);
        assertEquals("PACK_10", pkg.getCode());
        assertEquals("基础套餐", pkg.getName());
        assertEquals(10, pkg.getCredits());
        assertEquals(new BigDecimal("199.00"), pkg.getPrice());
    }

    // ========== alipayNotify 测试（无需登录）==========

    @Test
    void alipayNotify_success_delegatesToService() {
        // Given
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(payOrderService.handleAlipayNotify(request)).thenReturn("success");

        // When
        String result = billingController.alipayNotify(request);

        // Then
        assertEquals("success", result);
        verify(payOrderService).handleAlipayNotify(request);
    }

    @Test
    void alipayNotify_fail_delegatesToService() {
        // Given
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(payOrderService.handleAlipayNotify(request)).thenReturn("fail");

        // When
        String result = billingController.alipayNotify(request);

        // Then
        assertEquals("fail", result);
        verify(payOrderService).handleAlipayNotify(request);
    }
}
