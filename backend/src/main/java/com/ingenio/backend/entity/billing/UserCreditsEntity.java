package com.ingenio.backend.entity.billing;

import com.baomidou.mybatisplus.annotation.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * 用户余额实体类
 * 记录用户的生成次数余额
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("user_credits")
public class UserCreditsEntity {

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
     * 总购买次数
     */
    @TableField("total_credits")
    private Integer totalCredits;

    /**
     * 已使用次数
     */
    @TableField("used_credits")
    private Integer usedCredits;

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

    /**
     * 获取剩余次数
     */
    public Integer getRemainingCredits() {
        return (totalCredits != null ? totalCredits : 0) - (usedCredits != null ? usedCredits : 0);
    }
}
