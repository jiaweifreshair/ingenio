package com.ingenio.backend.codegen.ai.tool;

import com.ingenio.backend.codegen.schema.Entity;
import com.ingenio.backend.codegen.template.TemplateEngine;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * ServiceClassGeneratorTool - 完整Service类生成工具（V2.0 MVP Day 2 Phase 2.3.7）
 *
 * <p>核心功能：将业务逻辑代码片段组装成完整的Java Service类</p>
 *
 * <p>解决问题：</p>
 * <ul>
 *   <li>架构缺失：之前缺少将代码片段组装成完整类的组件</li>
 *   <li>评分过低：代码片段无法获得ValidationTool的高分（35/100）</li>
 *   <li>目标评分：生成完整Service类，目标≥80/100</li>
 * </ul>
 *
 * <p>使用的FreeMarker模板：</p>
 * <ul>
 *   <li>ai/ServiceClass.ftl - 完整Service类模板</li>
 * </ul>
 *
 * <p>评分目标分解：</p>
 * <ul>
 *   <li>Syntax (30分)：完整Java语法结构 → 30/30</li>
 *   <li>Structure (30分)：package、imports、@Service、@Slf4j → 30/30</li>
 *   <li>Logic (40分)：Repository注入、业务逻辑、异常处理 → 20-40/40</li>
 *   <li>总分目标：80-100/100</li>
 * </ul>
 *
 * @author Ingenio Code Generator
 * @since 2025-11-19 V2.0 MVP Day 2 Phase 2.3.7
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ServiceClassGeneratorTool implements Function<ServiceClassGeneratorTool.Request, ServiceClassGeneratorTool.Response> {

    /**
     * FreeMarker模板引擎
     */
    private final TemplateEngine templateEngine;

    /**
     * Service类模板路径（相对于/templates/codegen/）
     */
    private static final String SERVICE_CLASS_TEMPLATE = "ai/ServiceClass.ftl";

    /**
     * 生成完整的Service类代码
     *
     * <p>将业务逻辑代码片段包装成完整的Java Service类，包含：</p>
     * <ul>
     *   <li>Package声明</li>
     *   <li>所有必要的imports</li>
     *   <li>@Service、@Slf4j、@RequiredArgsConstructor注解</li>
     *   <li>Repository注入</li>
     *   <li>完整方法实现（含日志、异常处理）</li>
     *   <li>辅助方法（findById、getById、deleteById、validateInput）</li>
     * </ul>
     *
     * @param request 生成请求，包含业务逻辑代码和实体信息
     * @return 生成响应，包含完整的Service类代码
     */
    @Override
    public Response apply(Request request) {
        log.info("[ServiceClassGeneratorTool] 开始生成完整Service类: entity={}, method={}",
                request.entity.getName(), request.methodName);

        long startTime = System.currentTimeMillis();

        try {
            // Step 1: 准备模板参数
            Map<String, Object> templateParams = buildTemplateParams(request);

            // Step 2: 使用FreeMarker渲染模板
            String completeServiceClass = templateEngine.render(SERVICE_CLASS_TEMPLATE, templateParams);

            long duration = System.currentTimeMillis() - startTime;

            log.info("[ServiceClassGeneratorTool] ✅ Service类生成完成: " +
                            "耗时={}ms, 代码长度={} 字符",
                    duration, completeServiceClass.length());

            return Response.builder()
                    .success(true)
                    .completeCode(completeServiceClass)
                    .message("Service类生成成功")
                    .duration(duration)
                    .build();

        } catch (Exception e) {
            log.error("[ServiceClassGeneratorTool] ❌ Service类生成失败: {}", e.getMessage(), e);

            return Response.builder()
                    .success(false)
                    .completeCode(null)
                    .message("Service类生成失败: " + e.getMessage())
                    .duration(System.currentTimeMillis() - startTime)
                    .build();
        }
    }

    /**
     * 构建模板参数Map
     *
     * @param request 生成请求
     * @return 模板参数Map
     */
    private Map<String, Object> buildTemplateParams(Request request) {
        Map<String, Object> params = new HashMap<>();

        // 基础信息
        String entityName = capitalizeFirstLetter(request.entity.getName());
        String entityVarName = uncapitalizeFirstLetter(entityName);

        // 包名配置
        params.put("packageName", "com.ingenio.backend.service");
        params.put("entityPackage", "com.ingenio.backend.entity");
        params.put("repositoryPackage", "com.ingenio.backend.repository");

        // 实体和服务信息
        params.put("entityName", entityName);
        params.put("entityVarName", entityVarName);
        params.put("serviceName", entityName + "Service");
        params.put("methodName", request.methodName);
        params.put("methodDescription", request.methodDescription != null ?
                request.methodDescription : request.methodName);
        params.put("entityDescription", request.entity.getDescription() != null ?
                request.entity.getDescription() : entityName);

        // 业务逻辑代码（核心内容）
        params.put("businessLogic", request.businessLogicCode);

        // 生成日期
        params.put("generatedDate", LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE));

        // 功能标志（用于条件渲染）
        params.put("hasValidation", detectFeature(request.businessLogicCode, "VALIDATION", "throw", "Exception", "validate"));
        params.put("hasCalculation", detectFeature(request.businessLogicCode, "CALCULATION", "BigDecimal", "calculate", "sum"));
        params.put("hasWorkflow", detectFeature(request.businessLogicCode, "WORKFLOW", "status", "state", "transition"));
        params.put("hasNotification", detectFeature(request.businessLogicCode, "NOTIFICATION", "notify", "send", "email"));

        return params;
    }

    /**
     * 检测业务逻辑代码是否包含特定功能
     *
     * @param code 业务逻辑代码
     * @param keywords 关键词列表
     * @return 是否包含该功能
     */
    private boolean detectFeature(String code, String... keywords) {
        if (code == null || code.isEmpty()) {
            return false;
        }
        String upperCode = code.toUpperCase();
        for (String keyword : keywords) {
            if (upperCode.contains(keyword.toUpperCase())) {
                return true;
            }
        }
        return false;
    }

    /**
     * 首字母大写
     */
    private String capitalizeFirstLetter(String str) {
        if (str == null || str.isEmpty()) {
            return str;
        }
        // 处理下划线命名（如 user_account -> UserAccount）
        if (str.contains("_")) {
            String[] parts = str.split("_");
            StringBuilder sb = new StringBuilder();
            for (String part : parts) {
                if (!part.isEmpty()) {
                    sb.append(Character.toUpperCase(part.charAt(0)));
                    if (part.length() > 1) {
                        sb.append(part.substring(1).toLowerCase());
                    }
                }
            }
            return sb.toString();
        }
        return Character.toUpperCase(str.charAt(0)) + str.substring(1);
    }

    /**
     * 首字母小写
     */
    private String uncapitalizeFirstLetter(String str) {
        if (str == null || str.isEmpty()) {
            return str;
        }
        return Character.toLowerCase(str.charAt(0)) + str.substring(1);
    }

    /**
     * 生成请求参数
     */
    @Data
    @Builder
    public static class Request {
        /**
         * 业务逻辑代码片段（来自BestPracticeApplierTool）
         */
        private String businessLogicCode;

        /**
         * 实体定义
         */
        private Entity entity;

        /**
         * 方法名称
         */
        private String methodName;

        /**
         * 方法描述（可选）
         */
        private String methodDescription;
    }

    /**
     * 生成响应结果
     */
    @Data
    @Builder
    public static class Response {
        /**
         * 是否成功
         */
        private boolean success;

        /**
         * 完整的Service类代码
         */
        private String completeCode;

        /**
         * 处理消息
         */
        private String message;

        /**
         * 处理耗时（毫秒）
         */
        private long duration;
    }
}
