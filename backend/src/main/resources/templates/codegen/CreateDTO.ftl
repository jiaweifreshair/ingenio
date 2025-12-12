<#-- CreateDTO模板：创建请求DTO（排除id、时间戳、软删除字段） -->
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

<#-- Bean Validation imports -->
<#assign needsValidation = false>
<#assign needsNotNull = false>
<#assign needsNotBlank = false>
<#assign needsSize = false>
<#assign needsMin = false>
<#assign needsDigits = false>
<#assign needsEmail = false>
<#list fields as field>
<#if field.validationAnnotations?? && field.validationAnnotations?size gt 0>
<#assign needsValidation = true>
<#list field.validationAnnotations as annotation>
<#if annotation?contains("@NotNull")>
<#assign needsNotNull = true>
</#if>
<#if annotation?contains("@NotBlank")>
<#assign needsNotBlank = true>
</#if>
<#if annotation?contains("@Size")>
<#assign needsSize = true>
</#if>
<#if annotation?contains("@Min")>
<#assign needsMin = true>
</#if>
<#if annotation?contains("@Digits")>
<#assign needsDigits = true>
</#if>
<#if annotation?contains("@Email")>
<#assign needsEmail = true>
</#if>
</#list>
</#if>
</#list>

<#if needsValidation>
<#if needsNotNull>
import jakarta.validation.constraints.NotNull;
</#if>
<#if needsNotBlank>
import jakarta.validation.constraints.NotBlank;
</#if>
<#if needsSize>
import jakarta.validation.constraints.Size;
</#if>
<#if needsMin>
import jakarta.validation.constraints.Min;
</#if>
<#if needsDigits>
import jakarta.validation.constraints.Digits;
</#if>
<#if needsEmail>
import jakarta.validation.constraints.Email;
</#if>
</#if>

import lombok.Data;

/**
 * ${className}
 *
 * <p>${description!""}</p>
 *
 * <p>用于创建${entityName}实体的请求体</p>
 *
 * @author ${author}
 * @since ${date}
 */
@Data
public class ${className} {

<#-- 生成字段定义 -->
<#list fields as field>
    /**
     * ${field.description!""}
     */
<#-- 生成Bean Validation注解 -->
<#if field.validationAnnotations?? && field.validationAnnotations?size gt 0>
<#list field.validationAnnotations as annotation>
    ${annotation}
</#list>
</#if>
    private ${simpleTypeName(field.javaType)} ${field.name};

</#list>
}
