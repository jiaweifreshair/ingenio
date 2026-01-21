package com.ingenio.backend.dto.billing;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 创建订单请求 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateOrderRequest {

    /**
     * 套餐编码：PACK_10/PACK_30/PACK_80
     */
    @NotBlank(message = "套餐编码不能为空")
    private String packageCode;

    /**
     * 支付渠道：ALIPAY_PC/ALIPAY_WAP
     */
    @NotBlank(message = "支付渠道不能为空")
    private String payChannel;
}
