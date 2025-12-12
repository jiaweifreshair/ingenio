package com.ingenio.backend.agent.v2;

import com.ingenio.backend.agent.IExecuteAgent;
import com.ingenio.backend.agent.dto.PlanResult;
import com.ingenio.backend.codegen.generator.*;
import com.ingenio.backend.codegen.schema.Entity;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * ExecuteAgent V2.0实现 - 全栈多端代码生成器
 *
 * <p>职责：根据Plan阶段的设计方案，生成完整的全栈应用代码</p>
 *
 * <p>生成内容：</p>
 * <ul>
 *   <li>数据库：PostgreSQL Schema + Liquibase changelog</li>
 *   <li>后端：Spring Boot代码（Entity/Service/Controller）</li>
 *   <li>前端Web：React代码（基于OpenLovable-CN原型增强）</li>
 * </ul>
 *
 * <p>技术栈：</p>
 * <ul>
 *   <li>AI模型：Qwen-Max（数据建模）</li>
 *   <li>模板引擎：FreeMarker 2.3.32（数据层代码生成）</li>
 *   <li>代码生成策略：混合模式（模板60% + AI 40%）</li>
 * </ul>
 *
 * <p>V2.0实现状态：基础功能已实现</p>
 *
 * @author Justin
 * @since 2025-11-17 V2.0架构升级
 */
@Slf4j
@Component("executeAgentV2")
@RequiredArgsConstructor
public class ExecuteAgentV2FullStackImpl implements IExecuteAgent {

    /**
     * 数据库Schema生成器
     */
    private final DatabaseSchemaGenerator databaseSchemaGenerator;

    /**
     * Entity代码生成器
     */
    private final EntityGenerator entityGenerator;

    /**
     * DTO代码生成器
     */
    private final DTOGenerator dtoGenerator;

    /**
     * Service代码生成器
     */
    private final ServiceGenerator serviceGenerator;

    /**
     * Controller代码生成器
     */
    private final ControllerGenerator controllerGenerator;

    /**
     * 执行全栈代码生成
     *
     * <p>V2.0完整实现：</p>
     * <ol>
     *   <li>从PlanResult提取用户需求</li>
     *   <li>生成数据库Schema（PostgreSQL DDL）</li>
     *   <li>生成Spring Boot后端代码（Entity/DTO/Service/Controller）</li>
     *   <li>组装并返回所有生成结果</li>
     * </ol>
     *
     * @param planResult Plan阶段的输出结果
     * @return 生成的代码结果Map
     */
    @Override
    public Map<String, Object> execute(PlanResult planResult) {
        log.info("[ExecuteAgentV2] ========== 开始V2.0全栈代码生成 ==========");
        long startTime = System.currentTimeMillis();

        try {
            // Step 1: 提取用户需求描述
            String userRequirement = extractUserRequirement(planResult);
            log.info("[ExecuteAgentV2] Step 1/4: 用户需求提取完成，长度: {} 字符", userRequirement.length());

            // Step 2: 生成数据库Schema
            log.info("[ExecuteAgentV2] Step 2/4: 开始生成数据库Schema...");
            DatabaseSchemaGenerator.DatabaseSchemaResult schemaResult =
                databaseSchemaGenerator.generate(userRequirement);
            log.info("[ExecuteAgentV2] ✅ 数据库Schema生成完成: {} 个实体, {} 个关系",
                schemaResult.getEntityCount(), schemaResult.getRelationshipCount());

            // Step 3: 生成Spring Boot后端代码
            log.info("[ExecuteAgentV2] Step 3/4: 开始生成Spring Boot后端代码...");
            Map<String, Object> backendCode = generateSpringBootCode(schemaResult);
            log.info("[ExecuteAgentV2] ✅ Spring Boot代码生成完成");

            // Step 4: 组装返回结果
            log.info("[ExecuteAgentV2] Step 4/4: 组装生成结果...");
            Map<String, Object> result = assembleResult(planResult, schemaResult, backendCode, startTime);

            long elapsedTime = System.currentTimeMillis() - startTime;
            log.info("[ExecuteAgentV2] ========== V2.0全栈代码生成完成 ==========");
            log.info("[ExecuteAgentV2] 总耗时: {} ms", elapsedTime);

            return result;

        } catch (Exception e) {
            log.error("[ExecuteAgentV2] ❌ 代码生成失败: {}", e.getMessage(), e);
            return createErrorResult(e.getMessage(), startTime);
        }
    }

    /**
     * 从PlanResult提取用户需求描述
     *
     * <p>V2.0增强：支持提取设计规范（designSpec）</p>
     * <p>拼接模块信息和设计约束生成完整需求描述</p>
     *
     * @param planResult Plan阶段结果（可能包含designSpec）
     * @return 用户需求描述文本（包含设计约束）
     */
    private String extractUserRequirement(PlanResult planResult) {
        StringBuilder requirement = new StringBuilder();

        // 添加推理过程中的需求描述
        if (planResult.getReasoning() != null && !planResult.getReasoning().isEmpty()) {
            requirement.append(planResult.getReasoning()).append("\n\n");
        }

        // 添加模块信息
        if (planResult.getModules() != null && !planResult.getModules().isEmpty()) {
            requirement.append("功能模块需求：\n");
            for (PlanResult.FunctionModule module : planResult.getModules()) {
                requirement.append("- ").append(module.getName());
                if (module.getDescription() != null) {
                    requirement.append(": ").append(module.getDescription());
                }
                requirement.append("\n");

                // 添加数据模型信息
                if (module.getDataModels() != null && !module.getDataModels().isEmpty()) {
                    requirement.append("  数据模型: ");
                    requirement.append(String.join(", ", module.getDataModels()));
                    requirement.append("\n");
                }
            }
        }

        // 如果没有提取到任何内容，使用默认描述
        if (requirement.toString().trim().isEmpty()) {
            requirement.append("基础应用系统，需要用户管理、基础CRUD功能");
        }

        // V2.0新增：提取并添加设计规范（designSpec）
        if (planResult.getDesignSpec() != null && !planResult.getDesignSpec().isEmpty()) {
            Map<String, Object> designSpec = planResult.getDesignSpec();

            log.info("[ExecuteAgentV2] ✅ 检测到设计规范(designSpec)，开始提取设计约束...");

            requirement.append("\n\n========== 设计规范约束 ==========\n");

            // 提取颜色主题
            @SuppressWarnings("unchecked")
            Map<String, Object> colorTheme = (Map<String, Object>) designSpec.get("colorTheme");
            if (colorTheme != null) {
                requirement.append("\n颜色主题：\n");
                if (colorTheme.get("primary") != null) {
                    requirement.append("- 主色调：").append(colorTheme.get("primary")).append("\n");
                }
                if (colorTheme.get("secondary") != null) {
                    requirement.append("- 辅助色：").append(colorTheme.get("secondary")).append("\n");
                }
                if (colorTheme.get("background") != null) {
                    requirement.append("- 背景色：").append(colorTheme.get("background")).append("\n");
                }
                if (colorTheme.get("text") != null) {
                    requirement.append("- 文字色：").append(colorTheme.get("text")).append("\n");
                }
                log.info("[ExecuteAgentV2] 已提取颜色主题: primary={}, secondary={}",
                        colorTheme.get("primary"), colorTheme.get("secondary"));
            }

            // 提取字体排版
            @SuppressWarnings("unchecked")
            Map<String, Object> typography = (Map<String, Object>) designSpec.get("typography");
            if (typography != null) {
                requirement.append("\n字体排版：\n");
                if (typography.get("fontFamily") != null) {
                    requirement.append("- 字体：").append(typography.get("fontFamily")).append("\n");
                }
                if (typography.get("fontSize") != null) {
                    requirement.append("- 字号：").append(typography.get("fontSize")).append("\n");
                }
                if (typography.get("lineHeight") != null) {
                    requirement.append("- 行高：").append(typography.get("lineHeight")).append("\n");
                }
                log.info("[ExecuteAgentV2] 已提取字体排版: fontFamily={}", typography.get("fontFamily"));
            }

            // 提取布局配置
            @SuppressWarnings("unchecked")
            Map<String, Object> layout = (Map<String, Object>) designSpec.get("layout");
            if (layout != null) {
                requirement.append("\n布局配置：\n");
                if (layout.get("type") != null) {
                    requirement.append("- 布局类型：").append(layout.get("type")).append("\n");
                }
                if (layout.get("spacing") != null) {
                    requirement.append("- 间距：").append(layout.get("spacing")).append("\n");
                }
                if (layout.get("borderRadius") != null) {
                    requirement.append("- 圆角：").append(layout.get("borderRadius")).append("\n");
                }
                log.info("[ExecuteAgentV2] 已提取布局配置: type={}, spacing={}",
                        layout.get("type"), layout.get("spacing"));
            }

            // 提取组件列表
            @SuppressWarnings("unchecked")
            List<String> components = (List<String>) designSpec.get("components");
            if (components != null && !components.isEmpty()) {
                requirement.append("\n推荐组件：\n");
                for (String component : components) {
                    requirement.append("- ").append(component).append("\n");
                }
                log.info("[ExecuteAgentV2] 已提取组件列表: {} 个组件", components.size());
            }

            requirement.append("========================================\n");

            log.info("[ExecuteAgentV2] ✅ 设计规范提取完成，已添加到需求描述中");
        } else {
            log.info("[ExecuteAgentV2] ℹ️ PlanResult中未包含设计规范(designSpec)，使用默认设计");
        }

        return requirement.toString().trim();
    }

    /**
     * 生成Spring Boot后端代码
     *
     * <p>根据数据库Schema生成完整的后端代码：</p>
     * <ul>
     *   <li>Entity类（JPA实体）</li>
     *   <li>DTO类（数据传输对象）</li>
     *   <li>Service类（业务逻辑层）</li>
     *   <li>Controller类（REST API层）</li>
     * </ul>
     *
     * @param schemaResult 数据库Schema生成结果
     * @return Spring Boot代码Map
     */
    private Map<String, Object> generateSpringBootCode(
            DatabaseSchemaGenerator.DatabaseSchemaResult schemaResult) {

        Map<String, Object> backendCode = new LinkedHashMap<>();
        List<Entity> entities = schemaResult.getEntities();

        // 生成各层代码
        Map<String, String> entityCodes = new LinkedHashMap<>();
        Map<String, String> dtoCodes = new LinkedHashMap<>();
        Map<String, String> serviceCodes = new LinkedHashMap<>();
        Map<String, String> controllerCodes = new LinkedHashMap<>();

        for (Entity entity : entities) {
            String entityName = entity.getName();
            log.debug("[ExecuteAgentV2] 生成 {} 的后端代码...", entityName);

            try {
                // Entity生成（单参数方法）
                String entityCode = entityGenerator.generate(entity);
                entityCodes.put(entityName + ".java", entityCode);

                // DTO生成（使用generateAll生成全部3种DTO：Create/Update/Response）
                Map<String, String> entityDTOs = dtoGenerator.generateAll(entity);
                dtoCodes.putAll(entityDTOs);

                // Service生成（使用generateAll生成接口和实现类）
                Map<String, String> entityServices = serviceGenerator.generateAll(entity);
                serviceCodes.putAll(entityServices);

                // Controller生成
                String controllerCode = controllerGenerator.generate(entity);
                controllerCodes.put(entityName + "Controller.java", controllerCode);

            } catch (Exception e) {
                log.warn("[ExecuteAgentV2] 生成 {} 代码时发生警告: {}", entityName, e.getMessage());
                // 继续处理其他实体
            }
        }

        backendCode.put("entities", entityCodes);
        backendCode.put("dtos", dtoCodes);
        backendCode.put("services", serviceCodes);
        backendCode.put("controllers", controllerCodes);
        backendCode.put("entityCount", entityCodes.size());

        log.info("[ExecuteAgentV2] 后端代码生成统计: Entity={}, DTO={}, Service={}, Controller={}",
            entityCodes.size(), dtoCodes.size(), serviceCodes.size(), controllerCodes.size());

        return backendCode;
    }

    /**
     * 组装最终返回结果
     *
     * @param planResult Plan阶段结果
     * @param schemaResult Schema生成结果
     * @param backendCode 后端代码
     * @param startTime 开始时间
     * @return 完整的生成结果Map
     */
    private Map<String, Object> assembleResult(
            PlanResult planResult,
            DatabaseSchemaGenerator.DatabaseSchemaResult schemaResult,
            Map<String, Object> backendCode,
            long startTime) {

        Map<String, Object> result = new LinkedHashMap<>();

        // 基础信息
        result.put("version", "V2");
        result.put("success", true);
        result.put("platforms", Arrays.asList("Web", "PostgreSQL", "Spring Boot"));

        // 数据库层
        Map<String, Object> database = new LinkedHashMap<>();
        database.put("entityCount", schemaResult.getEntityCount());
        database.put("relationshipCount", schemaResult.getRelationshipCount());
        database.put("migrationSQL", schemaResult.getMigrationSQL());
        database.put("rollbackSQL", schemaResult.getRollbackSQL());
        database.put("migrationFilePath", schemaResult.getMigrationFilePath());
        database.put("rollbackFilePath", schemaResult.getRollbackFilePath());

        // 实体详情
        List<Map<String, Object>> entityDetails = new ArrayList<>();
        for (Entity entity : schemaResult.getEntities()) {
            Map<String, Object> entityInfo = new LinkedHashMap<>();
            entityInfo.put("name", entity.getName());
            entityInfo.put("description", entity.getDescription());
            entityInfo.put("fieldCount", entity.getFields() != null ? entity.getFields().size() : 0);
            entityDetails.add(entityInfo);
        }
        database.put("entities", entityDetails);
        result.put("database", database);

        // 后端层
        result.put("backend", backendCode);

        // 前端层（占位，后续扩展）
        Map<String, Object> frontend = new LinkedHashMap<>();
        frontend.put("web", Map.of(
            "status", "pending",
            "message", "前端代码将在后续阶段生成"
        ));
        result.put("frontend", frontend);

        // 元数据
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("elapsedTimeMs", System.currentTimeMillis() - startTime);
        metadata.put("generatedAt", java.time.LocalDateTime.now().toString());
        metadata.put("planComplexityScore", planResult.getComplexityScore());
        metadata.put("estimatedHours", planResult.getEstimatedHours());
        result.put("metadata", metadata);

        return result;
    }

    /**
     * 创建错误结果
     *
     * @param errorMessage 错误消息
     * @param startTime 开始时间
     * @return 错误结果Map
     */
    private Map<String, Object> createErrorResult(String errorMessage, long startTime) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("version", "V2");
        result.put("success", false);
        result.put("error", errorMessage);
        result.put("elapsedTimeMs", System.currentTimeMillis() - startTime);
        return result;
    }

    @Override
    public String getVersion() {
        return "V2";
    }

    @Override
    public String getDescription() {
        return "V2.0 - 全栈多端代码生成器（PostgreSQL + Spring Boot + React）";
    }
}
