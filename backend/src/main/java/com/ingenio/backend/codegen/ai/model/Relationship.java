package com.ingenio.backend.codegen.ai.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 实体关系定义（V2.0 Phase 4.1）
 *
 * <p>描述实体之间的关联关系</p>
 *
 * <p>关系类型：</p>
 * <ul>
 *   <li>ONE_TO_ONE：一对一关系（用户-用户详情）</li>
 *   <li>ONE_TO_MANY：一对多关系（用户-订单，从User角度）</li>
 *   <li>MANY_TO_ONE：多对一关系（订单-用户，从Order角度）</li>
 *   <li>MANY_TO_MANY：多对多关系（用户-角色）</li>
 * </ul>
 *
 * <p>使用示例：</p>
 * <pre>{@code
 * Relationship userOrders = Relationship.builder()
 *     .sourceEntity("User")
 *     .targetEntity("Order")
 *     .type(RelationshipType.ONE_TO_MANY)
 *     .sourceField("id")
 *     .targetField("userId")
 *     .cascadeDelete(false)
 *     .build();
 * }</pre>
 *
 * @author Ingenio Code Generator
 * @since 2025-11-17 V2.0 Phase 4.1: AI需求理解服务
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Relationship {

    /**
     * 源实体名称（PascalCase）
     * 示例：User、Order、Product
     */
    private String sourceEntity;

    /**
     * 目标实体名称（PascalCase）
     * 示例：Order、Product、Category
     */
    private String targetEntity;

    /**
     * 关系类型
     * ONE_TO_ONE、ONE_TO_MANY、MANY_TO_MANY
     */
    private RelationshipType type;

    /**
     * 源实体关联字段
     * 示例：id、userId、productId
     */
    private String sourceField;

    /**
     * 目标实体关联字段
     * 示例：userId、orderId、categoryId
     */
    private String targetField;

    /**
     * 关系描述
     * 示例：一个用户可以有多个订单
     */
    private String description;

    /**
     * 是否可为空
     * true表示关系是可选的（OPTIONAL）
     * false表示关系是必需的（REQUIRED）
     */
    private Boolean nullable;

    /**
     * 是否级联删除
     * true表示删除源实体时自动删除关联的目标实体
     * 示例：删除订单时自动删除订单项
     */
    private Boolean cascadeDelete;

    /**
     * 中间表名称（仅MANY_TO_MANY时使用）
     * 示例：user_roles（用户角色关联表）
     */
    private String joinTableName;

    /**
     * Fetch策略
     * LAZY：延迟加载（默认）
     * EAGER：立即加载
     */
    private FetchType fetchType;

    /**
     * 实体关系类型枚举
     */
    public enum RelationshipType {
        /**
         * 一对一关系
         * 示例：User - UserProfile（用户与用户详情）
         */
        ONE_TO_ONE,

        /**
         * 一对多关系
         * 示例：User - Order（用户与订单，从User角度）
         */
        ONE_TO_MANY,

        /**
         * 多对一关系
         * 示例：Order - User（订单与用户，从Order角度）
         * 注：MANY_TO_ONE是ONE_TO_MANY的反向视角
         */
        MANY_TO_ONE,

        /**
         * 多对多关系
         * 示例：User - Role（用户与角色）
         */
        MANY_TO_MANY
    }

    /**
     * Fetch策略枚举
     */
    public enum FetchType {
        /**
         * 延迟加载：使用时才加载关联数据
         */
        LAZY,

        /**
         * 立即加载：查询主实体时立即加载关联数据
         */
        EAGER
    }
}
