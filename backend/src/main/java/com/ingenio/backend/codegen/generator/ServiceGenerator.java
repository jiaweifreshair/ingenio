package com.ingenio.backend.codegen.generator;

import com.ingenio.backend.codegen.model.ServiceTemplateModel;
import com.ingenio.backend.codegen.model.ServiceTemplateModel.ServiceType;
import com.ingenio.backend.codegen.schema.Entity;
import com.ingenio.backend.codegen.template.TemplateEngine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Service代码生成器（V2.0 Phase 3.4）
 *
 * <p>负责生成两种Service类：Service接口、ServiceImpl实现类</p>
 *
 * <p>核心功能：</p>
 * <ul>
 *   <li>Service接口生成：生成IXxxService接口（仅方法签名）</li>
 *   <li>ServiceImpl实现类生成：生成XxxServiceImpl类（完整实现）</li>
 *   <li>标准CRUD方法：create、update、delete、getById、list</li>
 *   <li>依赖注入：自动注入Mapper依赖</li>
 *   <li>事务管理：为create、update、delete方法添加@Transactional</li>
 *   <li>DTO转换：提供Entity ↔ DTO转换方法</li>
 * </ul>
 *
 * <p>使用示例：</p>
 * <pre>{@code
 * // 从Phase 2获取Entity schema
 * Entity entity = // ... from Phase 2
 *
 * // 生成Service接口和实现类
 * String interfaceCode = serviceGenerator.generateInterface(entity);
 * String implCode = serviceGenerator.generateImplementation(entity);
 * }</pre>
 *
 * @author Justin
 * @since 2025-11-17 V2.0 Phase 3.4: Service代码生成模板
 */
@Slf4j
@Service
public class ServiceGenerator {

    /**
     * 模板引擎（Phase 3.1）
     */
    private final TemplateEngine templateEngine;

    /**
     * Service模板文件名映射
     */
    private static final Map<ServiceType, String> TEMPLATE_MAP = Map.of(
            ServiceType.INTERFACE, "Service.ftl",
            ServiceType.IMPLEMENTATION, "ServiceImpl.ftl"
    );

    @Autowired
    public ServiceGenerator(TemplateEngine templateEngine) {
        this.templateEngine = templateEngine;
    }

    /**
     * 生成Service接口代码
     *
     * @param entity Entity schema对象（Phase 2输出）
     * @return 生成的Java接口代码字符串
     */
    public String generateInterface(Entity entity) {
        log.info("[ServiceGenerator] 开始生成Service接口: tableName={}", entity.getName());

        // Step 1: 转换Entity schema为Service模板数据模型（接口）
        ServiceTemplateModel model = ServiceTemplateModel.forInterface(entity);
        log.debug("[ServiceGenerator] Entity schema已转换为Service接口模板数据模型: interfaceName={}",
                model.getInterfaceName());

        // Step 2: 获取Service接口模板
        String templateName = TEMPLATE_MAP.get(ServiceType.INTERFACE);

        // Step 3: 渲染模板
        String generatedCode = templateEngine.render(templateName, model.toMap());
        log.info("[ServiceGenerator] ✅ Service接口代码生成完成: interfaceName={}, 代码长度={} 字符",
                model.getInterfaceName(), generatedCode.length());

        return generatedCode;
    }

    /**
     * 生成ServiceImpl实现类代码
     *
     * @param entity Entity schema对象（Phase 2输出）
     * @return 生成的Java实现类代码字符串
     */
    public String generateImplementation(Entity entity) {
        log.info("[ServiceGenerator] 开始生成ServiceImpl实现类: tableName={}", entity.getName());

        // Step 1: 转换Entity schema为Service模板数据模型（实现类）
        ServiceTemplateModel model = ServiceTemplateModel.forImplementation(entity);
        log.debug("[ServiceGenerator] Entity schema已转换为ServiceImpl模板数据模型: implementationName={}",
                model.getImplementationName());

        // Step 2: 获取ServiceImpl模板
        String templateName = TEMPLATE_MAP.get(ServiceType.IMPLEMENTATION);

        // Step 3: 渲染模板
        String generatedCode = templateEngine.render(templateName, model.toMap());
        log.info("[ServiceGenerator] ✅ ServiceImpl实现类代码生成完成: implementationName={}, 代码长度={} 字符",
                model.getImplementationName(), generatedCode.length());

        return generatedCode;
    }

    /**
     * 批量生成单个Entity的Service接口和实现类
     *
     * @param entity Entity schema对象
     * @return Service类名 → Java代码的映射
     */
    public Map<String, String> generateAll(Entity entity) {
        log.info("[ServiceGenerator] 开始批量生成Entity的Service: tableName={}", entity.getName());

        Map<String, String> result = new HashMap<>();

        // 生成Service接口
        String interfaceCode = generateInterface(entity);
        ServiceTemplateModel interfaceModel = ServiceTemplateModel.forInterface(entity);
        result.put(interfaceModel.getInterfaceName(), interfaceCode);

        // 生成ServiceImpl实现类
        String implCode = generateImplementation(entity);
        ServiceTemplateModel implModel = ServiceTemplateModel.forImplementation(entity);
        result.put(implModel.getImplementationName(), implCode);

        log.info("[ServiceGenerator] ✅ 批量生成完成: 生成了{}个Service类", result.size());
        return result;
    }

    /**
     * 批量生成多个Entity的Service接口和实现类
     *
     * @param entities Entity schema列表
     * @return Service类名 → Java代码的映射
     */
    public Map<String, String> generateAll(List<Entity> entities) {
        log.info("[ServiceGenerator] 开始批量生成多个Entity的Service: 实体数量={}", entities.size());

        Map<String, String> result = new HashMap<>();
        for (Entity entity : entities) {
            Map<String, String> entityServices = generateAll(entity);
            result.putAll(entityServices);
        }

        log.info("[ServiceGenerator] ✅ 批量生成完成: 共生成了{}个Service类", result.size());
        return result;
    }

    /**
     * 根据Service类型生成特定类型的Service
     *
     * @param entities Entity schema列表
     * @param serviceType Service类型（INTERFACE或IMPLEMENTATION）
     * @return Service类名 → Java代码的映射
     */
    public Map<String, String> generateByType(List<Entity> entities, ServiceType serviceType) {
        log.info("[ServiceGenerator] 开始生成指定类型的Service: 实体数量={}, serviceType={}",
                entities.size(), serviceType);

        Map<String, String> result = new HashMap<>();
        for (Entity entity : entities) {
            if (serviceType == ServiceType.INTERFACE) {
                String code = generateInterface(entity);
                ServiceTemplateModel model = ServiceTemplateModel.forInterface(entity);
                result.put(model.getInterfaceName(), code);
            } else {
                String code = generateImplementation(entity);
                ServiceTemplateModel model = ServiceTemplateModel.forImplementation(entity);
                result.put(model.getImplementationName(), code);
            }
        }

        log.info("[ServiceGenerator] ✅ 生成完成: 共生成了{}个{}类型的Service",
                result.size(), serviceType);
        return result;
    }
}
