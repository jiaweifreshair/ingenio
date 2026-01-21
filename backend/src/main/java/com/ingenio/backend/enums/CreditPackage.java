package com.ingenio.backend.enums;

import java.math.BigDecimal;

/**
 * 套餐枚举
 * 定义可购买的生成次数套餐
 */
public enum CreditPackage {

    /**
     * 基础套餐：199元10次
     */
    PACK_10("PACK_10", "基础套餐", 10, new BigDecimal("199.00")),

    /**
     * 标准套餐：399元30次
     */
    PACK_30("PACK_30", "标准套餐", 30, new BigDecimal("399.00")),

    /**
     * 专业套餐：800元80次
     */
    PACK_80("PACK_80", "专业套餐", 80, new BigDecimal("800.00"));

    private final String code;
    private final String name;
    private final int credits;
    private final BigDecimal price;

    CreditPackage(String code, String name, int credits, BigDecimal price) {
        this.code = code;
        this.name = name;
        this.credits = credits;
        this.price = price;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public int getCredits() {
        return credits;
    }

    public BigDecimal getPrice() {
        return price;
    }

    /**
     * 根据编码获取套餐
     */
    public static CreditPackage fromCode(String code) {
        for (CreditPackage pkg : values()) {
            if (pkg.code.equals(code)) {
                return pkg;
            }
        }
        throw new IllegalArgumentException("未知的套餐编码: " + code);
    }
}
