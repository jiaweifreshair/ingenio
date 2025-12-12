<#-- Service接口模板：生成IXxxService接口（仅方法签名） -->
package ${packageName};

<#-- 导入所需的Java类型 -->
import com.ingenio.backend.dto.${createDTOName};
import com.ingenio.backend.dto.${updateDTOName};
import com.ingenio.backend.dto.${responseDTOName};
import com.baomidou.mybatisplus.core.metadata.IPage;

import java.util.Optional;
<#if primaryKeyType == "java.util.UUID">
import java.util.UUID;
</#if>

/**
 * ${interfaceName}
 *
 * <p>${description!""}</p>
 *
 * <p>核心功能：</p>
 * <ul>
 *   <li>创建${entityName}实体</li>
 *   <li>更新${entityName}实体</li>
 *   <li>删除${entityName}实体<#if softDelete>（软删除）<#else>（物理删除）</#if></li>
 *   <li>根据ID查询${entityName}实体</li>
 *   <li>分页查询${entityName}列表</li>
 * </ul>
 *
 * @author ${author}
 * @since ${date}
 */
public interface ${interfaceName} {

<#-- 生成方法签名 -->
<#list methods as method>
    /**
     * ${method.description}
     *
<#list method.parameters as param>
     * @param ${param.name} ${param.description}
</#list>
<#if method.returnType != "void">
     * @return ${method.returnType}
</#if>
     */
    ${method.returnType} ${method.name}(<#list method.parameters as param>${param.type} ${param.name}<#if param?has_next>, </#if></#list>);

</#list>
}
