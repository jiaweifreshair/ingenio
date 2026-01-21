package com.ingenio.backend.enums;

/**
 * 支付订单状态枚举
 */
public enum PayOrderStatus {

    /**
     * 待支付
     */
    PENDING("PENDING", "待支付"),

    /**
     * 已支付
     */
    PAID("PAID", "已支付"),

    /**
     * 支付失败
     */
    FAILED("FAILED", "支付失败"),

    /**
     * 已取消
     */
    CANCELLED("CANCELLED", "已取消"),

    /**
     * 已过期
     */
    EXPIRED("EXPIRED", "已过期");

    private final String code;
    private final String name;

    PayOrderStatus(String code, String name) {
        this.code = code;
        this.name = name;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public static PayOrderStatus fromCode(String code) {
        for (PayOrderStatus status : values()) {
            if (status.code.equals(code)) {
                return status;
            }
        }
        throw new IllegalArgumentException("未知的订单状态: " + code);
    }
}
