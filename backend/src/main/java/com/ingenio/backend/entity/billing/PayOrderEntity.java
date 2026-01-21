package com.ingenio.backend.entity.billing;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * 支付订单实体类
 * 记录所有支付订单
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("pay_orders")
public class PayOrderEntity {

    /**
     * 主键ID（UUID）
     */
    @TableId(value = "id", type = IdType.ASSIGN_UUID)
    private UUID id;

    /**
     * 业务订单号（唯一）
     */
    @TableField("order_no")
    private String orderNo;

    /**
     * 用户ID
     */
    @TableField("user_id")
    private UUID userId;

    /**
     * 套餐编码：PACK_10/PACK_30/PACK_80
     */
    @TableField("package_code")
    private String packageCode;

    /**
     * 套餐名称
     */
    @TableField("package_name")
    private String packageName;

    /**
     * 购买次数
     */
    @TableField("credits_amount")
    private Integer creditsAmount;

    /**
     * 订单金额（元）
     */
    @TableField("amount")
    private BigDecimal amount;

    /**
     * 货币类型
     */
    @TableField("currency")
    private String currency;

    /**
     * 支付渠道：ALIPAY_PC/ALIPAY_WAP
     */
    @TableField("pay_channel")
    private String payChannel;

    /**
     * 支付数据类型：URL/FORM/QR
     */
    @TableField("pay_data_type")
    private String payDataType;

    /**
     * 支付数据（二维码URL/表单等）
     */
    @TableField("pay_data")
    private String payData;

    /**
     * 第三方支付流水号
     */
    @TableField("transaction_id")
    private String transactionId;

    /**
     * 订单状态：PENDING/PAID/FAILED/CANCELLED/EXPIRED
     */
    @TableField("status")
    private String status;

    /**
     * 订单过期时间
     */
    @TableField("expire_time")
    private Instant expireTime;

    /**
     * 支付成功时间
     */
    @TableField("pay_time")
    private Instant payTime;

    /**
     * 创建时间
     */
    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private Instant createdAt;

    /**
     * 更新时间
     */
    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private Instant updatedAt;
}
