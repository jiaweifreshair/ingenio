package com.ingenio.backend.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 模板分类元数据响应DTO
 *
 * 用于返回分类的基本信息和统计数据
 *
 * @author Claude
 * @since 2025-12-27
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CategoryMetaResponse {

    /**
     * 分类ID
     */
    private String id;

    /**
     * 分类显示名称
     */
    private String name;

    /**
     * 分类图标（Emoji或图标名称）
     */
    private String icon;

    /**
     * 该分类下的模板数量
     */
    private Integer count;
}
