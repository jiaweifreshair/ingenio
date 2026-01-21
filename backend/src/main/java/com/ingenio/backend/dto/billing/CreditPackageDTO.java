package com.ingenio.backend.dto.billing;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 套餐 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreditPackageDTO {

    /**
     * 套餐编码
     */
    private String code;

    /**
     * 套餐名称
     */
    private String name;

    /**
     * 生成次数
     */
    private Integer credits;

    /**
     * 价格（元）
     */
    private BigDecimal price;
}
