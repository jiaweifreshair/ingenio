package com.ingenio.backend.codegen.generator;

import com.ingenio.backend.codegen.model.ControllerTemplateModel;
import com.ingenio.backend.codegen.schema.Entity;
import com.ingenio.backend.codegen.template.TemplateEngine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Controller代码生成器（V2.0 Phase 3.5）
 *
 * <p>从Entity schema生成完整的Spring REST Controller代码</p>
 *
 * <p>核心功能：</p>
 * <ul>
 *   <li>生成标准的RESTful API Controller</li>
 *   <li>包含5个标准CRUD端点（create、getById、update、delete、list）</li>
 *   <li>集成Spring MVC、Sa-Token、Swagger/OpenAPI注解</li>
 *   <li>统一响应格式Result包装</li>
 *   <li>Bean Validation校验</li>
 *   <li>依赖注入Service层</li>
 * </ul>
 *
 * <p>使用示例：</p>
 * <pre>{@code
 * // 单个Entity生成
 * Entity userEntity = // ... from Phase 2
 * String controllerCode = controllerGenerator.generate(userEntity);
 *
 * // 批量生成
 * List<Entity> entities = // ... multiple entities
 * Map<String, String> allControllers = controllerGenerator.generateAll(entities);
 * // 返回: {"UserController" -> "完整代码", "OrderController" -> "完整代码"}
 * }</pre>
 *
 * <p>生成的Controller特性：</p>
 * <ul>
 *   <li>@RestController：Spring MVC REST控制器</li>
 *   <li>@RequestMapping：RESTful API基础路径（/api/v1/xxx）</li>
 *   <li>@RequiredArgsConstructor：Lombok依赖注入</li>
 *   <li>@Slf4j：日志记录</li>
 *   <li>@Tag：Swagger API文档标签</li>
 *   <li>@SaCheckLogin：Sa-Token认证保护</li>
 * </ul>
 *
 * @author Ingenio Code Generator
 * @since 2025-11-17 V2.0 Phase 3.5: Controller代码生成器
 */
@Slf4j
@Service
public class ControllerGenerator {

    /**
     * Freemarker模板引擎
     * 用于渲染Controller.ftl模板
     */
    private final TemplateEngine templateEngine;

    /**
     * 构造函数注入TemplateEngine
     *
     * @param templateEngine Freemarker模板引擎实例
     */
    @Autowired
    public ControllerGenerator(TemplateEngine templateEngine) {
        this.templateEngine = templateEngine;
        log.info("[ControllerGenerator] ✅ Controller代码生成器初始化完成");
    }

    /**
     * 从Entity schema生成单个Controller代码
     *
     * <p>生成流程：</p>
     * <ol>
     *   <li>将Entity schema转换为ControllerTemplateModel数据模型</li>
     *   <li>获取Controller.ftl模板名称</li>
     *   <li>使用TemplateEngine渲染模板生成代码</li>
     *   <li>返回生成的Controller代码字符串</li>
     * </ol>
     *
     * <p>生成的Controller包含：</p>
     * <ul>
     *   <li>完整的类声明和注解（@RestController、@RequestMapping等）</li>
     *   <li>Service依赖注入字段</li>
     *   <li>5个标准CRUD端点方法实现</li>
     *   <li>完整的Swagger/OpenAPI文档注解</li>
     *   <li>统一的Result响应格式</li>
     *   <li>完整的日志记录</li>
     * </ul>
     *
     * @param entity Entity schema对象（Phase 2输出）
     * @return 生成的Controller完整代码
     * @throws IllegalArgumentException 如果entity为null或缺少必需字段
     */
    public String generate(Entity entity) {
        // Step 1: 参数校验（必须在访问entity属性之前）
        validateEntity(entity);

        log.info("[ControllerGenerator] 开始生成Controller: tableName={}", entity.getName());

        // Step 2: 将Entity转换为ControllerTemplateModel
        ControllerTemplateModel model = ControllerTemplateModel.fromEntity(entity);
        log.debug("[ControllerGenerator] ControllerTemplateModel构建完成: className={}, baseUrl={}, endpoints={}",
                model.getClassName(), model.getBaseUrl(), model.getEndpoints().size());

        // Step 3: 获取模板名称
        String templateName = "Controller.ftl";

        // Step 4: 使用TemplateEngine渲染模板
        log.debug("[ControllerGenerator] 开始渲染模板: templateName={}", templateName);
        String generatedCode = templateEngine.render(templateName, model.toMap());

        // Step 5: 验证生成的代码
        validateGeneratedCode(generatedCode, model.getClassName());

        log.info("[ControllerGenerator] ✅ Controller代码生成完成: className={}, codeLength={}",
                model.getClassName(), generatedCode.length());

        return generatedCode;
    }

    /**
     * 为单个Entity生成Controller代码（返回Map格式）
     *
     * <p>与{@link #generate(Entity)}的区别：</p>
     * <ul>
     *   <li>generate()：直接返回代码字符串</li>
     *   <li>generateAll()：返回Map格式，key为类名，value为代码</li>
     * </ul>
     *
     * <p>适用场景：</p>
     * <ul>
     *   <li>需要统一批量处理多个生成结果</li>
     *   <li>需要按类名索引生成的代码</li>
     *   <li>便于后续文件写入和管理</li>
     * </ul>
     *
     * @param entity Entity schema对象
     * @return Map结构：{类名 → 代码}，示例：{"UserController" → "完整代码"}
     */
    public Map<String, String> generateAll(Entity entity) {
        log.info("[ControllerGenerator] 开始生成单个Entity的Controller（Map格式）: tableName={}", entity.getName());

        Map<String, String> result = new HashMap<>();

        // 生成Controller代码
        String controllerCode = generate(entity);

        // 构建类名
        ControllerTemplateModel model = ControllerTemplateModel.fromEntity(entity);
        String className = model.getClassName();

        // 存入Map
        result.put(className, controllerCode);

        log.info("[ControllerGenerator] ✅ 单个Entity的Controller生成完成（Map格式）: className={}", className);

        return result;
    }

    /**
     * 批量生成多个Entity的Controller代码
     *
     * <p>批量生成策略：</p>
     * <ul>
     *   <li>遍历所有Entity逐个生成</li>
     *   <li>单个Entity失败不影响其他Entity</li>
     *   <li>记录失败的Entity和原因</li>
     *   <li>返回所有成功生成的Controller代码</li>
     * </ul>
     *
     * <p>使用场景：</p>
     * <ul>
     *   <li>项目初始化时一次性生成所有Controller</li>
     *   <li>数据库Schema变更后批量更新Controller</li>
     *   <li>代码重构时批量重新生成</li>
     * </ul>
     *
     * @param entities 多个Entity schema对象列表
     * @return Map结构：{类名 → 代码}，包含所有Entity的Controller代码
     *         示例：{"UserController" → "代码", "OrderController" → "代码"}
     */
    public Map<String, String> generateAll(List<Entity> entities) {
        log.info("[ControllerGenerator] 开始批量生成Controller: entityCount={}", entities.size());

        Map<String, String> result = new HashMap<>();
        int successCount = 0;
        int failureCount = 0;

        // 遍历所有Entity逐个生成
        for (Entity entity : entities) {
            try {
                // 生成单个Entity的Controller
                Map<String, String> entityResult = generateAll(entity);
                result.putAll(entityResult);

                successCount++;
                log.debug("[ControllerGenerator] Entity生成成功: tableName={}", entity.getName());

            } catch (Exception e) {
                failureCount++;
                log.error("[ControllerGenerator] ❌ Entity生成失败: tableName={}, error={}",
                        entity.getName(), e.getMessage(), e);
            }
        }

        log.info("[ControllerGenerator] ✅ 批量生成完成: totalCount={}, successCount={}, failureCount={}, generatedClasses={}",
                entities.size(), successCount, failureCount, result.size());

        return result;
    }

    // ==================== 私有辅助方法 ====================

    /**
     * 校验Entity是否合法
     *
     * @param entity 待校验的Entity
     * @throws IllegalArgumentException 如果Entity为null或缺少必需字段
     */
    private void validateEntity(Entity entity) {
        if (entity == null) {
            throw new IllegalArgumentException("Entity不能为null");
        }

        if (entity.getName() == null || entity.getName().trim().isEmpty()) {
            throw new IllegalArgumentException("Entity name不能为空");
        }

        if (entity.getFields() == null || entity.getFields().isEmpty()) {
            throw new IllegalArgumentException("Entity fields不能为空");
        }

        // 验证至少有一个主键字段
        boolean hasPrimaryKey = entity.getFields().stream()
                .anyMatch(com.ingenio.backend.codegen.schema.Field::isPrimaryKey);
        if (!hasPrimaryKey) {
            log.warn("[ControllerGenerator] ⚠️ Entity缺少主键字段，将使用默认UUID主键: tableName={}", entity.getName());
        }
    }

    /**
     * 校验生成的代码是否合法
     *
     * @param generatedCode 生成的代码
     * @param className 类名（用于日志）
     * @throws IllegalStateException 如果生成的代码为空或不包含关键内容
     */
    private void validateGeneratedCode(String generatedCode, String className) {
        if (generatedCode == null || generatedCode.trim().isEmpty()) {
            throw new IllegalStateException("生成的Controller代码为空: className=" + className);
        }

        // 验证代码包含关键元素
        if (!generatedCode.contains("@RestController")) {
            throw new IllegalStateException("生成的Controller代码缺少@RestController注解: className=" + className);
        }

        if (!generatedCode.contains("public class " + className)) {
            throw new IllegalStateException("生成的Controller代码缺少类声明: className=" + className);
        }

        log.debug("[ControllerGenerator] ✅ 代码验证通过: className={}", className);
    }
}
