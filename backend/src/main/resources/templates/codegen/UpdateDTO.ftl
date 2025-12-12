<#-- UpdateDTO模板：更新请求DTO（包含id，其他字段可选） -->
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

<#-- Bean Validation imports (UpdateDTO的验证要求较少) -->
<#assign needsValidation = false>
<#assign needsNotNull = false>
<#assign needsSize = false>
<#assign needsMin = false>
<#assign needsDigits = false>
<#assign needsEmail = false>
<#list fields as field>
<#if field.validationAnnotations?? && field.validationAnnotations?size gt 0>
<#assign needsValidation = true>
<#list field.validationAnnotations as annotation>
<#if annotation?contains("@NotNull") && field.primaryKey>
<#assign needsNotNull = true>
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

<#if needsNotNull>
import jakarta.validation.constraints.NotNull;
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

import lombok.Data;

/**
 * ${className}
 *
 * <p>${description!""}</p>
 *
 * <p>用于更新${entityName}实体的请求体</p>
 *
 * <p>注意：除id外，其他字段均为可选</p>
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
<#if field.primaryKey>
     * 必填字段
<#else>
     * 可选字段
</#if>
     */
<#-- UpdateDTO只对主键字段生成@NotNull注解 -->
<#if field.primaryKey && field.validationAnnotations??>
<#list field.validationAnnotations as annotation>
<#if annotation?contains("@NotNull")>
    ${annotation}
</#if>
</#list>
</#if>
<#-- 其他字段的格式验证注解（不包含@NotNull/@NotBlank） -->
<#if !field.primaryKey && field.validationAnnotations??>
<#list field.validationAnnotations as annotation>
<#if !annotation?contains("@NotNull") && !annotation?contains("@NotBlank")>
    ${annotation}
</#if>
</#list>
</#if>
    private ${simpleTypeName(field.javaType)} ${field.name};

</#list>
}
