package com.ingenio.backend.entity.generated;

import com.baomidou.mybatisplus.annotation.*;
import com.ingenio.backend.config.UUIDv8TypeHandler;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName(value = "product_shots", autoResultMap = true)
public class ProductShotEntity {
    @TableId(value = "id", type = IdType.ASSIGN_UUID)
    @TableField(typeHandler = UUIDv8TypeHandler.class)
    private UUID id;

    @TableField(value = "user_id", typeHandler = UUIDv8TypeHandler.class)
    private UUID userId;

    @TableField("original_image_url")
    private String originalImageUrl;

    @TableField("mask_image_url")
    private String maskImageUrl;

    @TableField("result_image_url")
    private String resultImageUrl;

    @TableField("prompt")
    private String prompt;

    @TableField("status")
    private String status;

    @TableField(value = "created_at", fill = FieldFill.INSERT)
    private Instant createdAt;

    @TableField(value = "updated_at", fill = FieldFill.INSERT_UPDATE)
    private Instant updatedAt;
}
