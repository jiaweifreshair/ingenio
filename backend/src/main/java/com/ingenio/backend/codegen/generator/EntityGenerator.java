package com.ingenio.backend.codegen.generator;

import com.ingenio.backend.codegen.model.EntityTemplateModel;
import com.ingenio.backend.codegen.schema.Entity;
import com.ingenio.backend.codegen.template.TemplateEngine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Entity代码生成器（V2.0 Phase 3.2）
 *
 * <p>负责生成Spring Boot Entity类代码</p>
 *
 * <p>核心功能：</p>
 * <ul>
 *   <li>Entity代码生成：基于Phase 2的Entity schema生成JPA Entity类</li>
 *   <li>JPA注解：@Entity, @Table, @Id, @Column, @GeneratedValue</li>
 *   <li>Lombok注解：@Data, @Builder, @NoArgsConstructor, @AllArgsConstructor</li>
 *   <li>Supabase兼容：UUID主键、TIMESTAMPTZ时间戳、软删除支持</li>
 *   <li>审计字段：自动添加created_at, updated_at, deleted_at（可选）</li>
 * </ul>
 *
 * <p>使用示例：</p>
 * <pre>{@code
 * // 从Phase 2获取Entity schema
 * List<Entity> entities = databaseSchemaGenerator.generate(userRequirement).getEntities();
 *
 * // 生成Entity代码
 * for (Entity entity : entities) {
 *     String code = entityGenerator.generate(entity);
 *     // 写入文件或返回给用户
 * }
 * }</pre>
 *
 * @author Justin
 * @since 2025-11-17 V2.0 Phase 3.2: Entity代码生成模板
 */
@Slf4j
@Service
public class EntityGenerator {

    /**
     * 模板引擎（Phase 3.1）
     */
    private final TemplateEngine templateEngine;

    /**
     * Entity模板文件名
     */
    private static final String ENTITY_TEMPLATE = "Entity.ftl";

    @Autowired
    public EntityGenerator(TemplateEngine templateEngine) {
        this.templateEngine = templateEngine;
    }

    /**
     * 生成Entity类代码
     *
     * @param entity Entity schema对象（Phase 2输出）
     * @return 生成的Java代码字符串
     */
    public String generate(Entity entity) {
        log.info("[EntityGenerator] 开始生成Entity代码: tableName={}", entity.getName());

        // Step 1: 转换Entity schema为模板数据模型
        EntityTemplateModel model = EntityTemplateModel.fromEntity(entity);
        log.debug("[EntityGenerator] Entity schema已转换为模板数据模型: className={}",
                model.getClassName());

        // Step 2: 渲染模板
        String generatedCode = templateEngine.render(ENTITY_TEMPLATE, model.toMap());
        log.info("[EntityGenerator] ✅ Entity代码生成完成: className={}, 代码长度={} 字符",
                model.getClassName(), generatedCode.length());

        return generatedCode;
    }

    /**
     * 批量生成Entity代码
     *
     * @param entities Entity schema列表
     * @return Entity类名 → Java代码的映射
     */
    public java.util.Map<String, String> generateAll(java.util.List<Entity> entities) {
        log.info("[EntityGenerator] 开始批量生成Entity代码: 实体数量={}", entities.size());

        java.util.Map<String, String> result = new java.util.HashMap<>();
        for (Entity entity : entities) {
            String code = generate(entity);
            EntityTemplateModel model = EntityTemplateModel.fromEntity(entity);
            result.put(model.getClassName(), code);
        }

        log.info("[EntityGenerator] ✅ 批量生成完成: 生成了{}个Entity类", result.size());
        return result;
    }
}
