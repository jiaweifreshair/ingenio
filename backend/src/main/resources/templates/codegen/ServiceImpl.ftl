<#-- ServiceImpl实现类模板：生成XxxServiceImpl类（完整实现） -->
package ${packageName};

<#-- 导入所需的Java类型 -->
import com.ingenio.backend.dto.${createDTOName};
import com.ingenio.backend.dto.${updateDTOName};
import com.ingenio.backend.dto.${responseDTOName};
import com.ingenio.backend.entity.${entityName}Entity;
import com.ingenio.backend.mapper.${mapperName};
import com.ingenio.backend.service.${interfaceName};
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
<#if primaryKeyType == "java.util.UUID">
import java.util.UUID;
</#if>

/**
 * ${implementationName}
 *
 * <p>${description!""}</p>
 *
 * <p>核心功能：</p>
 * <ul>
 *   <li>创建${entityName}实体（事务保护）</li>
 *   <li>更新${entityName}实体（事务保护）</li>
 *   <li>删除${entityName}实体（事务保护）<#if softDelete>（软删除）<#else>（物理删除）</#if></li>
 *   <li>根据ID查询${entityName}实体</li>
 *   <li>分页查询${entityName}列表</li>
 * </ul>
 *
 * <p>技术实现：</p>
 * <ul>
 *   <li>使用MyBatis-Plus进行数据库操作</li>
 *   <li>使用@Transactional保证事务一致性</li>
 *   <li>使用Lombok简化代码</li>
 *   <li>使用Slf4j记录操作日志</li>
 * </ul>
 *
 * @author ${author}
 * @since ${date}
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ${implementationName} implements ${interfaceName} {

    /**
     * ${entityName}数据访问对象
     */
    private final ${mapperName} ${mapperName?uncap_first};

<#-- 生成方法实现 -->
<#list methods as method>
    <#if method.transactional>
    @Transactional(rollbackFor = Exception.class)
    </#if>
    @Override
    public ${method.returnType} ${method.name}(<#list method.parameters as param>${param.type} ${param.name}<#if param?has_next>, </#if></#list>) {
        log.info("[${implementationName}] ${method.description} - <#list method.parameters as param>${param.name}={}<#if param?has_next>, </#if></#list>"<#list method.parameters as param>, ${param.name}</#list>);

<#if method.implementationSteps??>
<#list method.implementationSteps as step>
        ${step}
</#list>
</#if>
    }

</#list>
    /**
     * DTO转Entity
     *
     * @param createDTO 创建请求DTO
     * @return ${entityName}Entity
     */
    private ${entityName}Entity convertToEntity(${createDTOName} createDTO) {
        ${entityName}Entity entity = new ${entityName}Entity();
        // TODO: 根据实际字段映射，使用BeanUtils.copyProperties()或手动设置
        // 示例：entity.setFieldName(createDTO.getFieldName());
        return entity;
    }

    /**
     * 更新Entity字段（仅更新非null字段）
     *
     * @param entity 现有实体
     * @param updateDTO 更新请求DTO
     */
    private void updateEntityFields(${entityName}Entity entity, ${updateDTOName} updateDTO) {
        // TODO: 根据实际字段映射，仅更新非null字段
        // 示例：
        // if (updateDTO.getFieldName() != null) {
        //     entity.setFieldName(updateDTO.getFieldName());
        // }
    }

    /**
     * Entity转ResponseDTO
     *
     * @param entity 实体对象
     * @return ${responseDTOName}
     */
    private ${responseDTOName} convertToResponseDTO(${entityName}Entity entity) {
        ${responseDTOName} responseDTO = new ${responseDTOName}();
        // TODO: 根据实际字段映射，使用BeanUtils.copyProperties()或手动设置
        // 示例：responseDTO.setFieldName(entity.getFieldName());
        return responseDTO;
    }
}
