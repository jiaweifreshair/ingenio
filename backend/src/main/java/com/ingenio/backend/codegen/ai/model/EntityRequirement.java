package com.ingenio.backend.codegen.ai.model;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 实体需求定义（V2.0 Phase 4.1）
 *
 * <p>描述一个业务实体的完整需求</p>
 *
 * <p>核心信息：</p>
 * <ul>
 *   <li>实体名称和描述</li>
 *   <li>字段列表（含类型、约束）</li>
 *   <li>需要的业务方法列表</li>
 *   <li>关联关系</li>
 * </ul>
 *
 * <p>使用示例：</p>
 * <pre>{@code
 * EntityRequirement userEntity = EntityRequirement.builder()
 *     .name("User")
 *     .description("系统用户实体")
 *     .fields(List.of(
 *         new FieldRequirement("username", "String", "用户名", true, true),
 *         new FieldRequirement("email", "String", "邮箱", true, true),
 *         new FieldRequirement("age", "Integer", "年龄", false, false)
 *     ))
 *     .businessMethods(List.of("register", "login", "resetPassword"))
 *     .build();
 * }</pre>
 *
 * @author Ingenio Code Generator
 * @since 2025-11-17 V2.0 Phase 4.1: AI需求理解服务
 */
@Data
@Builder
public class EntityRequirement {

    /**
     * 实体名称（PascalCase）
     * 示例：User、Order、Product
     */
    private String name;

    /**
     * 实体描述
     * 示例：系统用户实体，管理用户的基本信息和认证
     */
    private String description;

    /**
     * 数据库表名（snake_case）
     * 示例：users、orders、products
     */
    private String tableName;

    /**
     * 字段列表
     * 包含实体的所有属性字段
     */
    private List<FieldRequirement> fields;

    /**
     * 需要生成的业务方法列表
     * 示例：["register", "login", "resetPassword", "updateProfile"]
     */
    private List<String> businessMethods;

    /**
     * 是否需要软删除
     * true表示删除时只标记deleted_at，不物理删除
     */
    private Boolean softDelete;

    /**
     * 是否需要审计字段
     * true表示自动添加created_at、updated_at、created_by、updated_by
     */
    private Boolean auditFields;

    /**
     * 是否需要分页查询
     * true表示生成分页查询方法
     */
    private Boolean pagination;

    /**
     * 实体优先级
     * HIGH: 核心实体，需要完整的CRUD和业务方法
     * MEDIUM: 重要实体，需要基础CRUD
     * LOW: 辅助实体，仅需基础功能
     */
    private EntityPriority priority;

    /**
     * 实体优先级枚举
     */
    public enum EntityPriority {
        HIGH,
        MEDIUM,
        LOW
    }
}
