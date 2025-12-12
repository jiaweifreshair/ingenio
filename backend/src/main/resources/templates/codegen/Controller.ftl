<#-- Controller模板：生成XxxController类（Spring REST Controller） -->
package ${packageName};

<#-- 导入所需的Java类型 -->
import com.ingenio.backend.dto.${createDTOName};
import com.ingenio.backend.dto.${updateDTOName};
import com.ingenio.backend.dto.${responseDTOName};
import com.ingenio.backend.service.${serviceInterfaceName};
import com.ingenio.backend.common.Result;
import com.ingenio.backend.common.PageResult;

<#-- Spring MVC注解 -->
import org.springframework.web.bind.annotation.*;

<#-- Bean Validation -->
import jakarta.validation.Valid;

<#-- Lombok注解 -->
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

<#-- Sa-Token认证 -->
import cn.dev33.satoken.annotation.SaCheckLogin;

<#-- Swagger/OpenAPI注解 -->
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

<#-- 根据主键类型导入对应的Java类型 -->
<#if needsUUID>
import java.util.UUID;
</#if>

/**
 * ${className}
 *
 * <p>${description!""}</p>
 *
 * <p>RESTful API端点：</p>
 * <ul>
 *   <li>POST ${baseUrl} - 创建${entityName}</li>
 *   <li>GET ${baseUrl}/{id} - 查询${entityName}详情</li>
 *   <li>PUT ${baseUrl}/{id} - 更新${entityName}</li>
 *   <li>DELETE ${baseUrl}/{id} - 删除${entityName}</li>
 *   <li>GET ${baseUrl} - 分页查询${entityName}列表</li>
 * </ul>
 *
 * <p>技术特性：</p>
 * <ul>
 *   <li>统一响应格式：Result&lt;T&gt;包装</li>
 *   <li>认证保护：@SaCheckLogin确保用户已登录</li>
 *   <li>参数校验：@Valid自动校验请求参数</li>
 *   <li>API文档：Swagger/OpenAPI自动生成</li>
 * </ul>
 *
 * @author ${author}
 * @since ${date}
 */
@Slf4j
@RestController
@RequestMapping("${baseUrl}")
@RequiredArgsConstructor
@Tag(name = "${apiTagName}", description = "${apiTagDescription}")
public class ${className} {

    /**
     * ${entityName}业务逻辑服务
     */
    private final ${serviceInterfaceName} ${serviceFieldName};

<#-- 生成5个标准CRUD端点方法 -->
<#list endpoints as endpoint>
    <#-- @SaCheckLogin认证注解 -->
    <#if endpoint.requireLogin>
    @SaCheckLogin
    </#if>
    <#-- HTTP方法注解 -->
    <#if endpoint.httpMethod == "POST">
    @PostMapping<#if endpoint.path != "">("${endpoint.path}")</#if>
    <#elseif endpoint.httpMethod == "GET">
    @GetMapping<#if endpoint.path != "">("${endpoint.path}")</#if>
    <#elseif endpoint.httpMethod == "PUT">
    @PutMapping<#if endpoint.path != "">("${endpoint.path}")</#if>
    <#elseif endpoint.httpMethod == "DELETE">
    @DeleteMapping<#if endpoint.path != "">("${endpoint.path}")</#if>
    </#if>
    <#-- Swagger Operation注解 -->
    @Operation(summary = "${endpoint.operationSummary}", description = "${endpoint.operationDescription}")
    public ${endpoint.returnType} ${endpoint.name}(
<#-- 方法参数列表 -->
<#list endpoint.parameters as param>
            <#-- 参数注解 -->
            <#if param.location == "path">
            @Parameter(description = "${param.description}", required = ${param.required?c})
            @PathVariable<#-- 路径参数 --><#--
            --><#elseif param.location == "query">
            @Parameter(description = "${param.description}", required = ${param.required?c})
            @RequestParam<#if !param.required>(required = false)</#if><#-- 查询参数 --><#--
            --><#elseif param.location == "body">
            <#if param.needValidation>@Valid </#if>@RequestBody<#-- 请求体 --><#--
            --></#if> ${param.type} ${param.name}<#if param?has_next>,
<#else>) {
</#if>
</#list>
        <#-- 方法实现 -->
        log.info("[${className}.${endpoint.name}] ${endpoint.description} - <#list endpoint.parameters as param>${param.name}={}<#if param?has_next>, </#if></#list>"<#list endpoint.parameters as param>, ${param.name}</#list>);

        <#-- 根据方法类型生成不同的实现代码 -->
        <#if endpoint.name == "create">
        // 调用Service创建实体
        ${responseDTOName} response = ${serviceFieldName}.create(request);
        log.info("[${className}.${endpoint.name}] ✅ ${entityName}创建成功: id={}", response.getId());
        return Result.success("创建成功", response);

        <#elseif endpoint.name == "getById">
        // 调用Service查询实体
        ${responseDTOName} response = ${serviceFieldName}.getById(${primaryKeyFieldName})
                .orElseThrow(() -> new RuntimeException("${entityName}不存在: id=" + ${primaryKeyFieldName}));
        log.info("[${className}.${endpoint.name}] ✅ ${entityName}查询成功");
        return Result.success(response);

        <#elseif endpoint.name == "update">
        // 调用Service更新实体
        ${responseDTOName} response = ${serviceFieldName}.update(request);
        log.info("[${className}.${endpoint.name}] ✅ ${entityName}更新成功: id={}", ${primaryKeyFieldName});
        return Result.success("更新成功", response);

        <#elseif endpoint.name == "delete">
        // 调用Service删除实体
        ${serviceFieldName}.delete(${primaryKeyFieldName});
        log.info("[${className}.${endpoint.name}] ✅ ${entityName}删除成功: id={}", ${primaryKeyFieldName});
        return Result.success("删除成功", null);

        <#elseif endpoint.name == "list">
        // 调用Service分页查询
        com.baomidou.mybatisplus.core.metadata.IPage<${responseDTOName}> page = ${serviceFieldName}.list(
                current != null ? current : 1L,
                size != null ? size : 10L
        );

        // 转换为PageResult统一响应格式
        PageResult<${responseDTOName}> pageResult = PageResult.<${responseDTOName}>builder()
                .records(page.getRecords())
                .total(page.getTotal())
                .current(page.getCurrent())
                .size(page.getSize())
                .pages(page.getPages())
                .build();

        log.info("[${className}.${endpoint.name}] ✅ ${entityName}列表查询成功: total={}, current={}", page.getTotal(), page.getCurrent());
        return Result.success(pageResult);
        </#if>
    }

</#list>
}
