package com.ingenio.backend.dto.billing;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 支付订单 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PayOrderDTO {

    /**
     * 订单号
     */
    private String orderNo;

    /**
     * 套餐名称
     */
    private String packageName;

    /**
     * 购买次数
     */
    private Integer creditsAmount;

    /**
     * 订单金额（元）
     */
    private BigDecimal amount;

    /**
     * 订单状态
     */
    private String status;

    /**
     * 创建时间
     */
    private String createdAt;

    /**
     * 支付时间
     */
    private String payTime;
}
