package com.ingenio.backend.codegen.ai.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 字段需求定义（V2.0 Phase 4.1）
 *
 * <p>描述实体的一个字段及其约束</p>
 *
 * @author Ingenio Code Generator
 * @since 2025-11-17 V2.0 Phase 4.1
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FieldRequirement {
    private String name;           // 字段名：username
    private String type;           // 类型：String、Integer、UUID
    private String description;    // 描述：用户名
    private Boolean required;      // 是否必填
    private Boolean unique;        // 是否唯一
    private String defaultValue;   // 默认值
    private Integer minLength;     // 最小长度
    private Integer maxLength;     // 最大长度
    private String pattern;        // 正则表达式
}
