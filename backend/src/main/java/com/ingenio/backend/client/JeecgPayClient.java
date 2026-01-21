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

            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                Map<String, Object> body = response.getBody();
                Integer code = (Integer) body.get("code");

                if (code != null && code == 0) {
                    Map<String, Object> data = (Map<String, Object>) body.get("data");
                    if (data != null) {
                        log.info("获取支付参数成功: orderNo={}", orderNo);
                        return data;
                    }
                }

                String msg = (String) body.get("msg");
                log.error("JeecgBoot支付接口返回错误: code={}, msg={}", code, msg);
                throw new RuntimeException("获取支付参数失败: " + msg);
            }

            throw new RuntimeException("获取支付参数失败");
        } catch (RestClientException e) {
            log.error("调用JeecgBoot支付接口失败: {}", e.getMessage());
            throw new RuntimeException("支付服务暂不可用", e);
        }
    }
}
