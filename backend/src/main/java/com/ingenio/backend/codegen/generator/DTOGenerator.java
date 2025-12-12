package com.ingenio.backend.codegen.generator;

import com.ingenio.backend.codegen.model.DTOTemplateModel;
import com.ingenio.backend.codegen.model.DTOTemplateModel.DTOType;
import com.ingenio.backend.codegen.schema.Entity;
import com.ingenio.backend.codegen.template.TemplateEngine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * DTO代码生成器（V2.0 Phase 3.3）
 *
 * <p>负责生成三种DTO类：CreateDTO、UpdateDTO、ResponseDTO</p>
 *
 * <p>核心功能：</p>
 * <ul>
 *   <li>CreateDTO生成：创建请求DTO（排除id、时间戳、软删除字段）</li>
 *   <li>UpdateDTO生成：更新请求DTO（包含id，其他字段可选）</li>
 *   <li>ResponseDTO生成：响应DTO（包含所有字段）</li>
 *   <li>Bean Validation：自动添加@NotNull、@NotBlank、@Size、@Email等注解</li>
 *   <li>中文友好：所有验证错误信息使用中文</li>
 * </ul>
 *
 * <p>使用示例：</p>
 * <pre>{@code
 * // 从Phase 2获取Entity schema
 * Entity entity = // ... from Phase 2
 *
 * // 生成三种DTO
 * String createDTO = dtoGenerator.generate(entity, DTOType.CREATE);
 * String updateDTO = dtoGenerator.generate(entity, DTOType.UPDATE);
 * String responseDTO = dtoGenerator.generate(entity, DTOType.RESPONSE);
 * }</pre>
 *
 * @author Justin
 * @since 2025-11-17 V2.0 Phase 3.3: DTO代码生成模板
 */
@Slf4j
@Service
public class DTOGenerator {

    /**
     * 模板引擎（Phase 3.1）
     */
    private final TemplateEngine templateEngine;

    /**
     * DTO模板文件名映射
     */
    private static final Map<DTOType, String> TEMPLATE_MAP = Map.of(
            DTOType.CREATE, "CreateDTO.ftl",
            DTOType.UPDATE, "UpdateDTO.ftl",
            DTOType.RESPONSE, "ResponseDTO.ftl"
    );

    @Autowired
    public DTOGenerator(TemplateEngine templateEngine) {
        this.templateEngine = templateEngine;
    }

    /**
     * 生成DTO类代码
     *
     * @param entity Entity schema对象（Phase 2输出）
     * @param dtoType DTO类型
     * @return 生成的Java代码字符串
     */
    public String generate(Entity entity, DTOType dtoType) {
        log.info("[DTOGenerator] 开始生成DTO代码: tableName={}, dtoType={}",
                entity.getName(), dtoType);

        // Step 1: 转换Entity schema为DTO模板数据模型
        DTOTemplateModel model = DTOTemplateModel.fromEntity(entity, dtoType);
        log.debug("[DTOGenerator] Entity schema已转换为DTO模板数据模型: className={}",
                model.getClassName());

        // Step 2: 获取对应的模板文件名
        String templateName = TEMPLATE_MAP.get(dtoType);
        if (templateName == null) {
            throw new IllegalArgumentException("不支持的DTO类型: " + dtoType);
        }

        // Step 3: 渲染模板
        String generatedCode = templateEngine.render(templateName, model.toMap());
        log.info("[DTOGenerator] ✅ DTO代码生成完成: className={}, 代码长度={} 字符",
                model.getClassName(), generatedCode.length());

        return generatedCode;
    }

    /**
     * 批量生成单个Entity的所有DTO类型
     *
     * @param entity Entity schema对象
     * @return DTO类名 → Java代码的映射
     */
    public Map<String, String> generateAll(Entity entity) {
        log.info("[DTOGenerator] 开始批量生成Entity的所有DTO: tableName={}", entity.getName());

        Map<String, String> result = new HashMap<>();
        for (DTOType dtoType : DTOType.values()) {
            String code = generate(entity, dtoType);
            DTOTemplateModel model = DTOTemplateModel.fromEntity(entity, dtoType);
            result.put(model.getClassName(), code);
        }

        log.info("[DTOGenerator] ✅ 批量生成完成: 生成了{}个DTO类", result.size());
        return result;
    }

    /**
     * 批量生成多个Entity的所有DTO类型
     *
     * @param entities Entity schema列表
     * @return DTO类名 → Java代码的映射
     */
    public Map<String, String> generateAll(List<Entity> entities) {
        log.info("[DTOGenerator] 开始批量生成多个Entity的所有DTO: 实体数量={}", entities.size());

        Map<String, String> result = new HashMap<>();
        for (Entity entity : entities) {
            Map<String, String> entityDTOs = generateAll(entity);
            result.putAll(entityDTOs);
        }

        log.info("[DTOGenerator] ✅ 批量生成完成: 共生成了{}个DTO类", result.size());
        return result;
    }

    /**
     * 根据DTO类型生成特定类型的DTO
     *
     * @param entities Entity schema列表
     * @param dtoType DTO类型
     * @return DTO类名 → Java代码的映射
     */
    public Map<String, String> generateByType(List<Entity> entities, DTOType dtoType) {
        log.info("[DTOGenerator] 开始生成指定类型的DTO: 实体数量={}, dtoType={}",
                entities.size(), dtoType);

        Map<String, String> result = new HashMap<>();
        for (Entity entity : entities) {
            String code = generate(entity, dtoType);
            DTOTemplateModel model = DTOTemplateModel.fromEntity(entity, dtoType);
            result.put(model.getClassName(), code);
        }

        log.info("[DTOGenerator] ✅ 生成完成: 共生成了{}个{}类型的DTO",
                result.size(), dtoType);
        return result;
    }
}
