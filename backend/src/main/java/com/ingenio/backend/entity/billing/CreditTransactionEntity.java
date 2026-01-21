package com.ingenio.backend.entity.billing;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * 余额变动记录实体类
 * 记录每次余额变动
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("credit_transactions")
public class CreditTransactionEntity {

    /**
     * 主键ID（UUID）
     */
    @TableId(value = "id", type = IdType.ASSIGN_UUID)
    private UUID id;

    /**
     * 用户ID
     */
    @TableField("user_id")
    private UUID userId;

    /**
     * 关联订单ID（充值时有值）
     */
    @TableField("order_id")
    private UUID orderId;

    /**
     * 变动类型：PURCHASE/CONSUME/REFUND/GIFT
     */
    @TableField("type")
    private String type;

    /**
     * 变动数量（正数增加，负数减少）
     */
    @TableField("credits_change")
    private Integer creditsChange;

    /**
     * 变动前余额
     */
    @TableField("credits_before")
    private Integer creditsBefore;

    /**
     * 变动后余额
     */
    @TableField("credits_after")
    private Integer creditsAfter;

    /**
     * 变动描述
     */
    @TableField("description")
    private String description;

    /**
     * 关联业务ID（如appSpecId）
     */
    @TableField("reference_id")
    private String referenceId;

    /**
     * 创建时间
     */
    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private Instant createdAt;
}
