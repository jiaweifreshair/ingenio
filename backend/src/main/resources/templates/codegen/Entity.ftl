<#-- 提取简单类型名的函数(java.math.BigDecimal -> BigDecimal, 但UUID保持完全限定) -->
<#function simpleTypeName fullTypeName>
  <#-- UUID特殊处理:保持完全限定名java.util.UUID -->
  <#if fullTypeName == "java.util.UUID">
    <#return fullTypeName>
  <#elseif fullTypeName?contains(".")>
    <#return fullTypeName?keep_after_last(".")>
  <#else>
    <#return fullTypeName>
  </#if>
</#function>
package ${packageName};

import jakarta.persistence.*;
import lombok.*;
import java.io.Serializable;

<#-- 导入所需的Java类型 -->
<#list fields as field>
<#if field.javaType == "java.util.UUID">
import java.util.UUID;
<#break>
</#if>
</#list>
<#-- OffsetDateTime: 检查fields + timestamps + softDelete -->
<#assign needsOffsetDateTime = false>
<#list fields as field>
<#if field.javaType == "java.time.OffsetDateTime">
<#assign needsOffsetDateTime = true>
<#break>
</#if>
</#list>
<#if timestamps || softDelete>
<#assign needsOffsetDateTime = true>
</#if>
<#if needsOffsetDateTime>
import java.time.OffsetDateTime;
</#if>
<#list fields as field>
<#if field.javaType == "java.time.LocalDate">
import java.time.LocalDate;
<#break>
</#if>
</#list>
<#list fields as field>
<#if field.javaType == "java.time.LocalTime">
import java.time.LocalTime;
<#break>
</#if>
</#list>
<#list fields as field>
<#if field.javaType == "java.math.BigDecimal">
import java.math.BigDecimal;
<#break>
</#if>
</#list>

/**
 * ${className} 实体类
 *
 * <p>${description!""}</p>
 *
 * <p>对应数据库表：${tableName}</p>
 *
 * @author ${author}
 * @since ${date}
 */
@Entity
@Table(name = "${tableName}")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ${className} implements Serializable {

    private static final long serialVersionUID = 1L;

<#-- 生成字段定义 -->
<#list fields as field>
    /**
     * ${field.description!""}
<#if field.columnName != field.name>
     * 数据库字段：${field.columnName}
</#if>
<#if field.primaryKey>
     * 主键字段
</#if>
<#if field.unique>
     * 唯一约束
</#if>
<#if field.foreignKey??>
     * 外键引用：${field.foreignKey}
</#if>
     */
<#if field.primaryKey>
    @Id
<#if field.javaType == "java.util.UUID">
    @GeneratedValue(strategy = GenerationType.UUID)
<#elseif field.javaType == "Long">
    @GeneratedValue(strategy = GenerationType.IDENTITY)
</#if>
</#if>
<#if field.columnName != field.name>
    @Column(name = "${field.columnName}"<#if field.length??>, length = ${field.length}</#if><#if field.precision??>, precision = ${field.precision}</#if><#if field.scale??>, scale = ${field.scale}</#if><#if !field.nullable>, nullable = false</#if><#if field.unique>, unique = true</#if>)
<#else>
    @Column<#if field.length?? || field.precision?? || !field.nullable || field.unique>(
<#if field.length??>length = ${field.length}<#if field.precision?? || !field.nullable || field.unique>, </#if></#if><#if field.precision??>precision = ${field.precision}<#if field.scale??>, scale = ${field.scale}</#if><#if !field.nullable || field.unique>, </#if></#if><#if !field.nullable>nullable = false<#if field.unique>, </#if></#if><#if field.unique>unique = true</#if>)</#if>
</#if>
    private ${simpleTypeName(field.javaType)} ${field.name};

</#list>
<#-- 软删除字段 -->
<#if softDelete>
    /**
     * 软删除标志
     * NULL: 未删除
     * 非NULL: 删除时间
     */
    @Column(name = "deleted_at")
    private OffsetDateTime deletedAt;

</#if>
<#-- 时间戳字段 -->
<#if timestamps>
    /**
     * 创建时间
     */
    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    /**
     * 更新时间
     */
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    /**
     * JPA生命周期回调：持久化前设置创建时间和更新时间
     */
    @PrePersist
    protected void onCreate() {
        OffsetDateTime now = OffsetDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    /**
     * JPA生命周期回调：更新前设置更新时间
     */
    @PreUpdate
    protected void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }
</#if>
}
