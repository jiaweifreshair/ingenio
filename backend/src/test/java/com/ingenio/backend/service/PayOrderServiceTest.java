package com.ingenio.backend.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ingenio.backend.client.JeecgPayClient;
import com.ingenio.backend.dto.billing.CreateOrderRequest;
import com.ingenio.backend.dto.billing.CreateOrderResponse;
import com.ingenio.backend.dto.billing.PayOrderDTO;
import com.ingenio.backend.entity.billing.PayOrderEntity;
import com.ingenio.backend.enums.PayOrderStatus;
import com.ingenio.backend.mapper.billing.PayOrderMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * PayOrderService 单元测试
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PayOrderServiceTest {

    @Mock
    private PayOrderMapper payOrderMapper;

    @Mock
    private BillingService billingService;

    @Mock
    private JeecgPayClient jeecgPayClient;

    private PayOrderService payOrderService;

    private UUID testUserId;

    @BeforeEach
    void setUp() {
        payOrderService = new PayOrderService(payOrderMapper, billingService, jeecgPayClient);
        testUserId = UUID.randomUUID();
    }

    // ========== createOrder 测试 ==========

    @Test
    void createOrder_validRequest_createsOrderAndReturnsPayData() {
        // Given
        CreateOrderRequest request = new CreateOrderRequest();
        request.setPackageCode("PACK_10");
        request.setPayChannel("alipay_pc");

        Map<String, Object> payResult = Map.of(
                "payDataType", "form",
                "payData", "<form>...</form>"
        );
        when(jeecgPayClient.getAlipayParams(anyString(), any(BigDecimal.class), anyString(), anyString()))
                .thenReturn(payResult);
        when(payOrderMapper.insert(any(PayOrderEntity.class))).thenReturn(1);
        when(payOrderMapper.updateById(any(PayOrderEntity.class))).thenReturn(1);

        // When
        CreateOrderResponse response = payOrderService.createOrder(testUserId, request);

        // Then
        assertNotNull(response);
        assertNotNull(response.getOrderNo());
        assertTrue(response.getOrderNo().startsWith("PAY"));
        assertEquals("form", response.getPayDataType());
        assertEquals("<form>...</form>", response.getPayData());

        // 验证订单创建
        ArgumentCaptor<PayOrderEntity> captor = ArgumentCaptor.forClass(PayOrderEntity.class);
        verify(payOrderMapper).insert(captor.capture());
        PayOrderEntity order = captor.getValue();
        assertEquals(testUserId, order.getUserId());
        assertEquals("PACK_10", order.getPackageCode());
        assertEquals(10, order.getCreditsAmount());
        assertEquals(PayOrderStatus.PENDING.getCode(), order.getStatus());
    }

    @Test
    void createOrder_invalidPackage_throwsException() {
        // Given
        CreateOrderRequest request = new CreateOrderRequest();
        request.setPackageCode("INVALID_PACK");
        request.setPayChannel("alipay_pc");

        // When & Then
        assertThrows(IllegalArgumentException.class, () ->
                payOrderService.createOrder(testUserId, request)
        );
    }

    // ========== getOrder 测试 ==========

    @Test
    void getOrder_existingOrder_returnsDTO() {
        // Given
        String orderNo = "PAY123456";
        PayOrderEntity entity = PayOrderEntity.builder()
                .id(UUID.randomUUID())
                .orderNo(orderNo)
                .userId(testUserId)
                .packageName("基础套餐")
                .creditsAmount(10)
                .amount(new BigDecimal("199.00"))
                .status(PayOrderStatus.PENDING.getCode())
                .createdAt(Instant.now())
                .build();
        when(payOrderMapper.selectOne(any())).thenReturn(entity);

        // When
        PayOrderDTO result = payOrderService.getOrder(testUserId, orderNo);

        // Then
        assertEquals(orderNo, result.getOrderNo());
        assertEquals("基础套餐", result.getPackageName());
        assertEquals(10, result.getCreditsAmount());
        assertEquals(PayOrderStatus.PENDING.getCode(), result.getStatus());
    }

    @Test
    void getOrder_nonExistingOrder_throwsException() {
        // Given
        when(payOrderMapper.selectOne(any())).thenReturn(null);

        // When & Then
        assertThrows(RuntimeException.class, () ->
                payOrderService.getOrder(testUserId, "NON_EXISTING")
        );
    }

    // ========== getOrders 测试 ==========

    @Test
    void getOrders_returnsPagedResults() {
        // Given
        PayOrderEntity order1 = PayOrderEntity.builder()
                .orderNo("PAY001")
                .packageName("基础套餐")
                .creditsAmount(10)
                .amount(new BigDecimal("199.00"))
                .status(PayOrderStatus.PAID.getCode())
                .createdAt(Instant.now())
                .build();
        PayOrderEntity order2 = PayOrderEntity.builder()
                .orderNo("PAY002")
                .packageName("标准套餐")
                .creditsAmount(30)
                .amount(new BigDecimal("399.00"))
                .status(PayOrderStatus.PENDING.getCode())
                .createdAt(Instant.now())
                .build();

        Page<PayOrderEntity> page = new Page<>(1, 10);
        page.setRecords(List.of(order1, order2));

        when(payOrderMapper.selectPage(any(), any())).thenReturn(page);

        // When
        List<PayOrderDTO> results = payOrderService.getOrders(testUserId, 1, 10);

        // Then
        assertEquals(2, results.size());
        assertEquals("PAY001", results.get(0).getOrderNo());
        assertEquals("PAY002", results.get(1).getOrderNo());
    }

    // ========== handleAlipayNotify 测试 ==========

    @Test
    void handleAlipayNotify_tradeSuccess_updatesOrderAndAddsCredits() {
        // Given
        String orderNo = "PAY123456";
        String tradeNo = "ALIPAY_TRADE_123";
        UUID orderId = UUID.randomUUID();

        PayOrderEntity order = PayOrderEntity.builder()
                .id(orderId)
                .orderNo(orderNo)
                .userId(testUserId)
                .packageName("基础套餐")
                .creditsAmount(10)
                .status(PayOrderStatus.PENDING.getCode())
                .build();

        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getParameter("out_trade_no")).thenReturn(orderNo);
        when(request.getParameter("trade_no")).thenReturn(tradeNo);
        when(request.getParameter("trade_status")).thenReturn("TRADE_SUCCESS");

        when(payOrderMapper.selectOne(any())).thenReturn(order);
        when(payOrderMapper.updateById(any(PayOrderEntity.class))).thenReturn(1);

        // When
        String result = payOrderService.handleAlipayNotify(request);

        // Then
        assertEquals("success", result);
        assertEquals(PayOrderStatus.PAID.getCode(), order.getStatus());
        assertEquals(tradeNo, order.getTransactionId());
        assertNotNull(order.getPayTime());

        // 验证充值调用
        verify(billingService).addCredits(testUserId, orderId, 10, "购买基础套餐");
    }

    @Test
    void handleAlipayNotify_orderNotFound_returnsFail() {
        // Given
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getParameter("out_trade_no")).thenReturn("NON_EXISTING");
        when(request.getParameter("trade_no")).thenReturn("TRADE_123");
        when(request.getParameter("trade_status")).thenReturn("TRADE_SUCCESS");

        when(payOrderMapper.selectOne(any())).thenReturn(null);

        // When
        String result = payOrderService.handleAlipayNotify(request);

        // Then
        assertEquals("fail", result);
        verify(billingService, never()).addCredits(any(), any(), anyInt(), anyString());
    }

    @Test
    void handleAlipayNotify_alreadyPaid_returnsSuccess() {
        // Given
        String orderNo = "PAY123456";
        PayOrderEntity order = PayOrderEntity.builder()
                .orderNo(orderNo)
                .userId(testUserId)
                .status(PayOrderStatus.PAID.getCode())
                .build();

        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getParameter("out_trade_no")).thenReturn(orderNo);
        when(request.getParameter("trade_no")).thenReturn("TRADE_123");
        when(request.getParameter("trade_status")).thenReturn("TRADE_SUCCESS");

        when(payOrderMapper.selectOne(any())).thenReturn(order);

        // When
        String result = payOrderService.handleAlipayNotify(request);

        // Then
        assertEquals("success", result);
        verify(payOrderMapper, never()).updateById(any(PayOrderEntity.class));
        verify(billingService, never()).addCredits(any(), any(), anyInt(), anyString());
    }

    @Test
    void handleAlipayNotify_tradeFinished_updatesOrderAndAddsCredits() {
        // Given
        String orderNo = "PAY123456";
        UUID orderId = UUID.randomUUID();

        PayOrderEntity order = PayOrderEntity.builder()
                .id(orderId)
                .orderNo(orderNo)
                .userId(testUserId)
                .packageName("标准套餐")
                .creditsAmount(30)
                .status(PayOrderStatus.PENDING.getCode())
                .build();

        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getParameter("out_trade_no")).thenReturn(orderNo);
        when(request.getParameter("trade_no")).thenReturn("TRADE_456");
        when(request.getParameter("trade_status")).thenReturn("TRADE_FINISHED");

        when(payOrderMapper.selectOne(any())).thenReturn(order);
        when(payOrderMapper.updateById(any(PayOrderEntity.class))).thenReturn(1);

        // When
        String result = payOrderService.handleAlipayNotify(request);

        // Then
        assertEquals("success", result);
        verify(billingService).addCredits(testUserId, orderId, 30, "购买标准套餐");
    }

    @Test
    void handleAlipayNotify_otherStatus_returnsFail() {
        // Given
        String orderNo = "PAY123456";
        PayOrderEntity order = PayOrderEntity.builder()
                .orderNo(orderNo)
                .userId(testUserId)
                .status(PayOrderStatus.PENDING.getCode())
                .build();

        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getParameter("out_trade_no")).thenReturn(orderNo);
        when(request.getParameter("trade_no")).thenReturn("TRADE_123");
        when(request.getParameter("trade_status")).thenReturn("WAIT_BUYER_PAY");

        when(payOrderMapper.selectOne(any())).thenReturn(order);

        // When
        String result = payOrderService.handleAlipayNotify(request);

        // Then
        assertEquals("fail", result);
        verify(billingService, never()).addCredits(any(), any(), anyInt(), anyString());
    }
}
