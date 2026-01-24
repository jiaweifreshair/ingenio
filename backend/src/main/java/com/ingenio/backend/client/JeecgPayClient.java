package com.ingenio.backend.client;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * JeecgBoot 支付客户端
 * 调用 JeecgBoot 后端的支付宝支付接口
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JeecgPayClient {

    @Value("${jeecg.pay.base-url:http://localhost:18080}")
    private String jeecgBaseUrl;

    @Value("${jeecg.pay.notify-url:http://localhost:8080/api/v1/billing/notify/alipay}")
    private String notifyUrl;

    private final RestTemplate restTemplate;

    /**
     * 调用 JeecgBoot 获取支付宝支付参数
     *
     * @param orderNo    订单号
     * @param amount     金额
     * @param subject    商品描述
     * @param payChannel 支付渠道
     * @return 支付参数
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> getAlipayParams(String orderNo, BigDecimal amount, String subject, String payChannel) {
        String url = jeecgBaseUrl + "/sys/pay/api/unifiedParams";

        // 构建请求参数
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("tenantId", 0);
        requestBody.put("channelCode", payChannel);
        requestBody.put("orderNo", orderNo);
        requestBody.put("amount", amount.multiply(new BigDecimal("100")).intValue()); // 转换为分
        requestBody.put("currency", "CNY");
        requestBody.put("subject", subject);
        requestBody.put("notifyUrl", notifyUrl);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

        try {
            log.info("调用JeecgBoot支付接口: url={}, orderNo={}", url, orderNo);
            ResponseEntity<Map> response = restTemplate.postForEntity(url, entity, Map.class);

            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                throw new RuntimeException("获取支付参数失败");
            }

            Map<String, Object> body = response.getBody();
            Map<String, Object> payData = extractPayData(body);

            if (payData != null) {
                log.info("获取支付参数成功: orderNo={}", orderNo);
                return payData;
            }

            String message = extractMessage(body);
            Object code = body.get("code");
            log.error("JeecgBoot支付接口返回错误: code={}, message={}, body={}", code, message, body);
            throw new RuntimeException(message == null ? "获取支付参数失败" : "获取支付参数失败: " + message);
        } catch (RestClientException e) {
            log.error("调用JeecgBoot支付接口失败: {}", e.getMessage());
            throw new RuntimeException("支付服务暂不可用", e);
        }
    }

    /**
     * 解析 JeecgBoot 统一返回结构并抽取支付参数。
     *
     * 是什么：从 JeecgBoot `/sys/pay/api/unifiedParams` 的响应体中抽取 `payDataType/payData` 等字段。
     * 做什么：兼容不同 Result 包装风格（如 `code=200 + result`、`code=0 + data`、`success=true + result`）。
     * 为什么：JeecgBoot/自定义支付模块在不同版本或定制下返回字段可能不同，避免因解析差异导致下单失败（HTTP 500）。
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> extractPayData(Map<String, Object> body) {
        if (!isSuccessResponse(body)) {
            return null;
        }

        Object data = body.get("data");
        if (data instanceof Map) {
            return (Map<String, Object>) data;
        }

        Object result = body.get("result");
        if (result instanceof Map) {
            return (Map<String, Object>) result;
        }

        return null;
    }

    /**
     * 判断 JeecgBoot 返回是否表示成功。
     *
     * 是什么：对 JeecgBoot 的“成功标识”进行归一判断。
     * 做什么：优先识别 `success=true`，其次兼容 `code=0/200` 或 `code="0"/"200"`。
     * 为什么：JeecgBoot 常见 Result 结构不统一，直接写死某一个 code 会导致误判。
     */
    private boolean isSuccessResponse(Map<String, Object> body) {
        Object success = body.get("success");
        if (success instanceof Boolean) {
            return (Boolean) success;
        }

        Object code = body.get("code");
        if (code instanceof Number) {
            int codeValue = ((Number) code).intValue();
            return codeValue == 0 || codeValue == 200;
        }

        if (code instanceof String) {
            String codeValue = ((String) code).trim();
            return Objects.equals(codeValue, "0") || Objects.equals(codeValue, "200");
        }

        return false;
    }

    /**
     * 提取 JeecgBoot 返回中的错误消息字段。
     *
     * 是什么：抽取 `message/msg` 作为可展示的错误提示。
     * 做什么：优先取 message，其次取 msg；并忽略空字符串。
     * 为什么：不同返回体字段命名不同，需要统一成一个可读错误信息。
     */
    private String extractMessage(Map<String, Object> body) {
        Object message = body.get("message");
        if (message instanceof String && !((String) message).isBlank()) {
            return (String) message;
        }

        Object msg = body.get("msg");
        if (msg instanceof String && !((String) msg).isBlank()) {
            return (String) msg;
        }

        return null;
    }
}
