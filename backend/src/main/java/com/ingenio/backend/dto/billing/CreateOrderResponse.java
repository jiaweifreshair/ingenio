package com.ingenio.backend.dto.billing;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 创建订单响应 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateOrderResponse {

    /**
     * 订单号
     */
    private String orderNo;

    /**
     * 支付数据类型：URL/FORM/QR
     */
    private String payDataType;

    /**
     * 支付数据（二维码URL/跳转URL/表单）
     */
    private String payData;

    /**
     * 订单过期时间（ISO格式）
     */
    private String expireTime;
}
