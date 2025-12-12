<#-- ResponseDTO模板：响应DTO（包含所有字段，无验证注解） -->
<#-- 提取简单类型名的函数 -->
<#function simpleTypeName fullTypeName>
  <#if fullTypeName == "java.util.UUID">
    <#return fullTypeName>
  <#elseif fullTypeName?contains(".")>
    <#return fullTypeName?keep_after_last(".")>
  <#else>
    <#return fullTypeName>
  </#if>
</#function>
package ${packageName};

<#-- 导入所需的Java类型 -->
<#assign needsUUID = false>
<#assign needsBigDecimal = false>
<#assign needsOffsetDateTime = false>
<#assign needsLocalDate = false>
<#assign needsLocalTime = false>
<#list fields as field>
<#if field.javaType == "java.util.UUID">
<#assign needsUUID = true>
</#if>
<#if field.javaType == "java.math.BigDecimal">
<#assign needsBigDecimal = true>
</#if>
<#if field.javaType == "java.time.OffsetDateTime">
<#assign needsOffsetDateTime = true>
</#if>
<#if field.javaType == "java.time.LocalDate">
<#assign needsLocalDate = true>
</#if>
<#if field.javaType == "java.time.LocalTime">
<#assign needsLocalTime = true>
</#if>
</#list>

<#if needsUUID>
import java.util.UUID;
</#if>
<#if needsBigDecimal>
import java.math.BigDecimal;
</#if>
<#if needsOffsetDateTime>
import java.time.OffsetDateTime;
</#if>
<#if needsLocalDate>
import java.time.LocalDate;
</#if>
<#if needsLocalTime>
import java.time.LocalTime;
</#if>

import lombok.Data;

/**
 * ${className}
 *
 * <p>${description!""}</p>
 *
 * <p>用于返回${entityName}实体数据的响应体</p>
 *
 * @author ${author}
 * @since ${date}
 */
@Data
public class ${className} {

<#-- 生成字段定义（ResponseDTO不需要验证注解） -->
<#list fields as field>
    /**
     * ${field.description!""}
     */
    private ${simpleTypeName(field.javaType)} ${field.name};

</#list>
}
