package com.ingenio.backend.client;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * JeecgPayClient 单元测试
 * 覆盖：兼容 JeecgBoot 不同 Result 返回结构的解析逻辑
 */
@ExtendWith(MockitoExtension.class)
class JeecgPayClientTest {

    @Mock
    private RestTemplate restTemplate;

    private JeecgPayClient jeecgPayClient;

    @BeforeEach
    void setUp() {
        jeecgPayClient = new JeecgPayClient(restTemplate);

        // 使用反射注入 @Value 字段，避免引入 Spring 容器
        ReflectionTestUtils.setField(jeecgPayClient, "jeecgBaseUrl", "http://localhost:18080");
        ReflectionTestUtils.setField(jeecgPayClient, "notifyUrl", "http://localhost:8080/api/v1/billing/notify/alipay");
    }

    @Test
    void getAlipayParams_code200_result_returnsPayData() {
        // Given
        Map<String, Object> body = Map.of(
                "code", 200,
                "success", true,
                "result", Map.of(
                        "payDataType", "URL",
                        "payData", "https://example.com/pay"
                )
        );

        when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(Map.class)))
                .thenReturn(ResponseEntity.ok(body));

        // When
        Map<String, Object> payData = jeecgPayClient.getAlipayParams(
                "ORDER_1",
                new BigDecimal("199.00"),
                "基础套餐",
                "ALIPAY_PC"
        );

        // Then
        assertNotNull(payData);
        assertEquals("URL", payData.get("payDataType"));
        assertEquals("https://example.com/pay", payData.get("payData"));
    }

    @Test
    void getAlipayParams_code0_data_returnsPayData() {
        // Given
        Map<String, Object> body = Map.of(
                "code", 0,
                "data", Map.of(
                        "payDataType", "FORM",
                        "payData", "<form>...</form>"
                )
        );

        when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(Map.class)))
                .thenReturn(ResponseEntity.ok(body));

        // When
        Map<String, Object> payData = jeecgPayClient.getAlipayParams(
                "ORDER_2",
                new BigDecimal("399.00"),
                "标准套餐",
                "ALIPAY_PC"
        );

        // Then
        assertNotNull(payData);
        assertEquals("FORM", payData.get("payDataType"));
        assertEquals("<form>...</form>", payData.get("payData"));
    }

    @Test
    void getAlipayParams_errorResponse_throwsExceptionWithMessage() {
        // Given
        Map<String, Object> body = Map.of(
                "code", 500,
                "message", "支付渠道未配置"
        );

        when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(Map.class)))
                .thenReturn(ResponseEntity.ok(body));

        // When & Then
        RuntimeException ex = assertThrows(RuntimeException.class, () -> jeecgPayClient.getAlipayParams(
                "ORDER_3",
                new BigDecimal("199.00"),
                "基础套餐",
                "ALIPAY_PC"
        ));
        assertTrue(ex.getMessage().contains("支付渠道未配置"));
    }
}

