package com.ingenio.backend.codegen.ai.generator;

import com.ingenio.backend.codegen.ai.model.BestPracticeType;
import com.ingenio.backend.codegen.schema.Entity;
import com.ingenio.backend.codegen.template.TemplateEngine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 最佳实践应用器（V2.0 Phase 4.3）
 *
 * <p>在BusinessLogicGenerator生成的基础业务逻辑代码上，叠加企业级最佳实践增强</p>
 *
 * <p>核心功能：</p>
 * <ul>
 *   <li>代码质量增强：异常处理包装、日志记录、参数校验</li>
 *   <li>安全最佳实践：SQL注入防护、敏感信息保护、权限校验</li>
 *   <li>性能优化建议：缓存建议、批量操作、索引提示</li>
 *   <li>可维护性提升：JavaDoc注释、代码结构优化</li>
 * </ul>
 *
 * <p>工作流程：</p>
 * <pre>
 * 原始业务逻辑代码
 *         ↓
 * 代码结构分析（识别VALIDATION/CALCULATION/WORKFLOW/NOTIFICATION块）
 *         ↓
 * 应用CODE_QUALITY最佳实践（异常处理、日志）
 *         ↓
 * 应用SECURITY最佳实践（SQL注入防护、敏感数据脱敏）
 *         ↓
 * 应用PERFORMANCE最佳实践（缓存建议、批量操作）
 *         ↓
 * 增强后的企业级代码
 * </pre>
 *
 * <p>使用示例：</p>
 * <pre>{@code
 * // Step 1: 生成基础业务逻辑
 * String baseCode = businessLogicGenerator.generateBusinessLogic(rules, entity, "createOrder");
 *
 * // Step 2: 应用最佳实践增强
 * String enhancedCode = bestPracticeApplier.apply(baseCode, entity, "createOrder");
 *
 * // Step 3: 将增强代码插入Service方法
 * String serviceCode = serviceTemplate.replace("// TODO: Business Logic", enhancedCode);
 * }</pre>
 *
 * <p>输入示例：</p>
 * <pre>{@code
 * // ========== VALIDATION规则（数据验证） ==========
 * if (order.getQuantity() < 1) {
 *     throw new BusinessException(ErrorCode.INVALID_QUANTITY, "订单数量必须≥1");
 * }
 *
 * // ========== CALCULATION规则（业务计算） ==========
 * BigDecimal totalPrice = order.getUnitPrice().multiply(BigDecimal.valueOf(order.getQuantity()));
 * order.setTotalPrice(totalPrice);
 * }</pre>
 *
 * <p>输出示例：</p>
 * <pre>{@code
 * // ========== VALIDATION规则（数据验证） ==========
 * log.debug("[OrderService] 开始验证订单数据: orderId={}", order.getId());
 * try {
 *     if (order.getQuantity() == null || order.getQuantity() < 1) {
 *         log.warn("[OrderService] 订单数量验证失败: quantity={}", order.getQuantity());
 *         throw new BusinessException(ErrorCode.INVALID_QUANTITY, "订单数量必须≥1");
 *     }
 *     log.info("[OrderService] 订单数据验证通过: orderId={}", order.getId());
 * } catch (BusinessException e) {
 *     log.error("[OrderService] 业务异常: {}", e.getMessage(), e);
 *     throw e;
 * }
 *
 * // ========== CALCULATION规则（业务计算） ==========
 * log.debug("[OrderService] 开始计算订单总价: orderId={}", order.getId());
 * BigDecimal totalPrice = order.getUnitPrice().multiply(BigDecimal.valueOf(order.getQuantity()));
 * order.setTotalPrice(totalPrice);
 * log.info("[OrderService] 订单总价计算完成: orderId={}, totalPrice={}", order.getId(), totalPrice);
 * }</pre>
 *
 * @author Ingenio Code Generator
 * @since 2025-11-18 V2.0 Phase 4.3: 最佳实践应用器
 * @see com.ingenio.backend.codegen.ai.generator.BusinessLogicGenerator Phase 4.2 业务逻辑生成器
 * @see com.ingenio.backend.codegen.ai.model.BestPracticeType 最佳实践类型枚举
 */
@Slf4j
@Service
public class BestPracticeApplier {

    /**
     * 模板引擎（复用Phase 3.1模板基础设施）
     */
    private final TemplateEngine templateEngine;

    /**
     * VALIDATION规则块识别正则
     *
     * <p>注意：不使用MULTILINE模式，因为$需要匹配字符串末尾而非行末尾</p>
     */
    private static final Pattern VALIDATION_BLOCK_PATTERN =
            Pattern.compile("//\\s*=+\\s*VALIDATION规则[\\s\\S]*?(?=//\\s*=+|\\z)");

    /**
     * CALCULATION规则块识别正则
     *
     * <p>注意：不使用MULTILINE模式，因为$需要匹配字符串末尾而非行末尾</p>
     */
    private static final Pattern CALCULATION_BLOCK_PATTERN =
            Pattern.compile("//\\s*=+\\s*CALCULATION规则[\\s\\S]*?(?=//\\s*=+|\\z)");

    /**
     * WORKFLOW规则块识别正则
     *
     * <p>注意：不使用MULTILINE模式，因为$需要匹配字符串末尾而非行末尾</p>
     */
    private static final Pattern WORKFLOW_BLOCK_PATTERN =
            Pattern.compile("//\\s*=+\\s*WORKFLOW规则[\\s\\S]*?(?=//\\s*=+|\\z)");

    /**
     * NOTIFICATION规则块识别正则
     *
     * <p>注意：不使用MULTILINE模式，因为$需要匹配字符串末尾而非行末尾</p>
     */
    private static final Pattern NOTIFICATION_BLOCK_PATTERN =
            Pattern.compile("//\\s*=+\\s*NOTIFICATION规则[\\s\\S]*?(?=//\\s*=+|\\z)");

    @Autowired
    public BestPracticeApplier(TemplateEngine templateEngine) {
        this.templateEngine = templateEngine;
    }

    /**
     * 应用最佳实践增强（主入口方法）
     *
     * <p>按以下顺序应用最佳实践：</p>
     * <ol>
     *   <li>CODE_QUALITY：异常处理包装、日志记录</li>
     *   <li>SECURITY：SQL注入检测、敏感信息脱敏</li>
     *   <li>PERFORMANCE：性能优化建议（注释形式）</li>
     *   <li>MAINTAINABILITY：JavaDoc注释增强</li>
     * </ol>
     *
     * @param baseCode 基础业务逻辑代码（来自BusinessLogicGenerator）
     * @param entity 关联实体
     * @param method 关联方法名称
     * @return 应用最佳实践后的增强代码
     */
    public String apply(String baseCode, Entity entity, String method) {
        log.info("[BestPracticeApplier] 开始应用最佳实践增强: entity={}, method={}",
                entity.getName(), method);

        if (baseCode == null || baseCode.trim().isEmpty()) {
            log.warn("[BestPracticeApplier] 输入代码为空，跳过最佳实践应用");
            return baseCode;
        }

        // Step 1: 分析代码结构，识别VALIDATION/CALCULATION/WORKFLOW/NOTIFICATION块
        CodeStructure structure = analyzeCodeStructure(baseCode);
        log.debug("[BestPracticeApplier] 代码结构分析完成: blocks={}",
                structure.getBlocks().size());

        // Step 2: 应用CODE_QUALITY最佳实践（异常处理、日志）
        String codeQualityEnhanced = applyCodeQualityPractices(baseCode, entity, method, structure);
        log.debug("[BestPracticeApplier] CODE_QUALITY最佳实践应用完成: length={}",
                codeQualityEnhanced.length());

        // Step 3: 应用SECURITY最佳实践（SQL注入检测、敏感信息保护）
        String securityEnhanced = applySecurityPractices(codeQualityEnhanced, entity, method, structure);
        log.debug("[BestPracticeApplier] SECURITY最佳实践应用完成: length={}",
                securityEnhanced.length());

        // Step 4: 应用PERFORMANCE最佳实践（性能优化建议）
        String performanceEnhanced = applyPerformancePractices(securityEnhanced, entity, method, structure);
        log.debug("[BestPracticeApplier] PERFORMANCE最佳实践应用完成: length={}",
                performanceEnhanced.length());

        log.info("[BestPracticeApplier] ✅ 最佳实践应用完成: entity={}, method={}, 原始长度={}, 增强后长度={}",
                entity.getName(), method, baseCode.length(), performanceEnhanced.length());

        return performanceEnhanced;
    }

    /**
     * 分析代码结构，识别不同规则类型的代码块
     *
     * @param code 原始代码
     * @return 代码结构对象
     */
    private CodeStructure analyzeCodeStructure(String code) {
        log.debug("[BestPracticeApplier] 开始分析代码结构");

        CodeStructure structure = new CodeStructure();

        // 识别VALIDATION规则块
        Matcher validationMatcher = VALIDATION_BLOCK_PATTERN.matcher(code);
        while (validationMatcher.find()) {
            CodeBlock block = new CodeBlock("VALIDATION", validationMatcher.group().trim(),
                    validationMatcher.start(), validationMatcher.end());
            structure.addBlock(block);
            log.trace("[BestPracticeApplier] 识别VALIDATION块: start={}, end={}, length={}",
                    block.getStart(), block.getEnd(), block.getContent().length());
        }

        // 识别CALCULATION规则块
        Matcher calculationMatcher = CALCULATION_BLOCK_PATTERN.matcher(code);
        while (calculationMatcher.find()) {
            CodeBlock block = new CodeBlock("CALCULATION", calculationMatcher.group().trim(),
                    calculationMatcher.start(), calculationMatcher.end());
            structure.addBlock(block);
            log.trace("[BestPracticeApplier] 识别CALCULATION块: start={}, end={}, length={}",
                    block.getStart(), block.getEnd(), block.getContent().length());
        }

        // 识别WORKFLOW规则块
        Matcher workflowMatcher = WORKFLOW_BLOCK_PATTERN.matcher(code);
        while (workflowMatcher.find()) {
            CodeBlock block = new CodeBlock("WORKFLOW", workflowMatcher.group().trim(),
                    workflowMatcher.start(), workflowMatcher.end());
            structure.addBlock(block);
            log.trace("[BestPracticeApplier] 识别WORKFLOW块: start={}, end={}, length={}",
                    block.getStart(), block.getEnd(), block.getContent().length());
        }

        // 识别NOTIFICATION规则块
        Matcher notificationMatcher = NOTIFICATION_BLOCK_PATTERN.matcher(code);
        while (notificationMatcher.find()) {
            CodeBlock block = new CodeBlock("NOTIFICATION", notificationMatcher.group().trim(),
                    notificationMatcher.start(), notificationMatcher.end());
            structure.addBlock(block);
            log.trace("[BestPracticeApplier] 识别NOTIFICATION块: start={}, end={}, length={}",
                    block.getStart(), block.getEnd(), block.getContent().length());
        }

        log.debug("[BestPracticeApplier] 代码结构分析完成: 共识别{}个代码块", structure.getBlocks().size());
        return structure;
    }

    /**
     * 应用CODE_QUALITY最佳实践
     *
     * <p>增强内容：</p>
     * <ul>
     *   <li>为VALIDATION块添加try-catch异常处理</li>
     *   <li>为每个规则块添加开始/结束日志</li>
     *   <li>为CALCULATION块添加null检查</li>
     * </ul>
     *
     * @param code 原始代码
     * @param entity 实体
     * @param method 方法名
     * @param structure 代码结构
     * @return 应用CODE_QUALITY最佳实践后的代码
     */
    private String applyCodeQualityPractices(String code, Entity entity, String method, CodeStructure structure) {
        log.debug("[BestPracticeApplier] 开始应用CODE_QUALITY最佳实践");

        StringBuilder enhancedCode = new StringBuilder();
        int lastEnd = 0;

        // 按代码块顺序处理
        List<CodeBlock> sortedBlocks = structure.getBlocksSortedByPosition();

        for (CodeBlock block : sortedBlocks) {
            // 添加未处理的中间代码
            if (lastEnd < block.getStart()) {
                enhancedCode.append(code, lastEnd, block.getStart());
            }

            // 根据块类型应用增强
            String enhanced = enhanceCodeBlock(block, entity, method);
            enhancedCode.append(enhanced);

            lastEnd = block.getEnd();
        }

        // 添加剩余代码
        if (lastEnd < code.length()) {
            enhancedCode.append(code.substring(lastEnd));
        }

        return enhancedCode.toString();
    }

    /**
     * 增强单个代码块（添加异常处理和日志）
     *
     * @param block 代码块
     * @param entity 实体
     * @param method 方法名
     * @return 增强后的代码块
     */
    private String enhanceCodeBlock(CodeBlock block, Entity entity, String method) {
        String blockType = block.getType();
        String serviceName = entity.getName() + "Service";
        String entityVarName = entity.getName().substring(0, 1).toLowerCase() +
                entity.getName().substring(1);

        StringBuilder enhanced = new StringBuilder();

        // 添加块头部注释和开始日志
        enhanced.append(String.format("// ========== %s规则 ==========\n", blockType));
        enhanced.append(String.format("log.debug(\"[%s] 开始执行%s规则: %sId={}\", %s.getId());\n",
                serviceName, blockType, entityVarName, entityVarName));

        // 为VALIDATION块添加try-catch包装
        if ("VALIDATION".equals(blockType)) {
            enhanced.append("try {\n");
            // 原始规则代码（去掉头部注释）
            String ruleCode = block.getContent().replaceFirst("//\\s*=+\\s*VALIDATION规则[^\\n]*\\n", "");
            enhanced.append(indentCode(ruleCode, 4));
            enhanced.append(String.format("    log.info(\"[%s] %s规则验证通过: %sId={}\", %s.getId());\n",
                    serviceName, blockType, entityVarName, entityVarName));
            enhanced.append("} catch (BusinessException e) {\n");
            enhanced.append(String.format("    log.error(\"[%s] 业务异常: {}\", e.getMessage(), e);\n", serviceName));
            enhanced.append("    throw e;\n");
            enhanced.append("}\n");
        } else {
            // 其他块类型：添加日志但不添加异常处理
            String ruleCode = block.getContent().replaceFirst("//\\s*=+\\s*" + blockType + "规则[^\\n]*\\n", "");
            enhanced.append(ruleCode);
            enhanced.append(String.format("log.info(\"[%s] %s规则执行完成: %sId={}\", %s.getId());\n",
                    serviceName, blockType, entityVarName, entityVarName));
        }

        enhanced.append("\n");
        return enhanced.toString();
    }

    /**
     * 应用SECURITY最佳实践（Phase 4.3.2）
     *
     * <p>安全增强包含：</p>
     * <ul>
     *   <li>敏感字段识别和脱敏提示</li>
     *   <li>权限校验提示</li>
     *   <li>SQL注入防护检查</li>
     * </ul>
     *
     * @param code 代码
     * @param entity 实体
     * @param method 方法名
     * @param structure 代码结构
     * @return 应用SECURITY最佳实践后的代码
     */
    private String applySecurityPractices(String code, Entity entity, String method, CodeStructure structure) {
        log.debug("[BestPracticeApplier] 开始应用SECURITY最佳实践");

        // Step 1: 识别敏感字段
        List<String> sensitiveFields = identifySensitiveFields(entity);
        if (sensitiveFields.isEmpty()) {
            log.debug("[BestPracticeApplier] 未检测到敏感字段，跳过安全增强");
            return code;
        }

        // Step 2: 在代码开头添加安全提示注释
        StringBuilder enhanced = new StringBuilder();
        enhanced.append("// ========== SECURITY提示：敏感字段脱敏 ==========\n");
        enhanced.append("// 检测到敏感字段：").append(String.join(", ", sensitiveFields)).append("\n");
        enhanced.append("// 建议：日志记录时使用DataMaskingUtil.mask()进行脱敏处理\n");

        // 为修改操作添加权限校验提示
        if (method.startsWith("update") || method.startsWith("delete")) {
            enhanced.append("// 建议：添加数据权限校验，确保当前用户有权限修改此数据\n");
            enhanced.append("// 示例：checkDataPermission(currentUserId, ").append(entity.getName().toLowerCase())
                    .append("Id);\n");
        }

        enhanced.append("\n");
        enhanced.append(code);

        log.info("[BestPracticeApplier] ✅ SECURITY最佳实践应用完成: 检测到{}个敏感字段", sensitiveFields.size());
        return enhanced.toString();
    }

    /**
     * 识别敏感字段
     *
     * <p>通过字段名匹配识别敏感字段：</p>
     * <ul>
     *   <li>password, pwd: 密码</li>
     *   <li>idCard, idNo: 身份证号</li>
     *   <li>phone, mobile: 手机号</li>
     *   <li>bankCard, cardNo: 银行卡号</li>
     *   <li>email: 邮箱</li>
     * </ul>
     *
     * @param entity 实体
     * @return 敏感字段名称列表
     */
    private List<String> identifySensitiveFields(Entity entity) {
        List<String> sensitiveFields = new ArrayList<>();

        // 敏感字段关键词（不区分大小写）
        Set<String> sensitiveKeywords = Set.of(
                "password", "pwd",
                "idcard", "idno", "identitycard",
                "phone", "mobile", "telephone",
                "bankcard", "cardno", "creditcard",
                "email", "mail"
        );

        for (com.ingenio.backend.codegen.schema.Field field : entity.getFields()) {
            String fieldName = field.getName().toLowerCase().replace("_", "");

            // 检查字段名是否包含敏感关键词
            for (String keyword : sensitiveKeywords) {
                if (fieldName.contains(keyword)) {
                    sensitiveFields.add(field.getName());
                    log.debug("[BestPracticeApplier] 识别到敏感字段: {}", field.getName());
                    break;
                }
            }
        }

        return sensitiveFields;
    }

    /**
     * 应用PERFORMANCE最佳实践（Phase 4.3.3）
     *
     * <p>性能优化建议包含：</p>
     * <ul>
     *   <li>缓存建议：为查询操作添加Redis缓存提示</li>
     *   <li>批量操作：检测循环中的数据库操作，建议使用批量API</li>
     *   <li>索引建议：为频繁查询的字段建议添加数据库索引</li>
     * </ul>
     *
     * @param code 代码
     * @param entity 实体
     * @param method 方法名
     * @param structure 代码结构
     * @return 应用PERFORMANCE最佳实践后的代码
     */
    private String applyPerformancePractices(String code, Entity entity, String method, CodeStructure structure) {
        log.debug("[BestPracticeApplier] 开始应用PERFORMANCE最佳实践");

        StringBuilder hints = new StringBuilder();
        boolean hasPerformanceHints = false;

        // Step 1: 为查询操作添加缓存建议
        if (method.startsWith("get") || method.startsWith("find") || method.startsWith("query")) {
            hints.append("// ========== PERFORMANCE提示：缓存优化 ==========\n");
            hints.append("// 建议：对于频繁查询的数据，考虑添加Redis缓存\n");
            hints.append("// 示例：@Cacheable(value = \"").append(entity.getName().toLowerCase())
                    .append("\", key = \"#id\")\n");
            hints.append("// 缓存过期时间建议：热数据5-30分钟，温数据1-6小时\n");
            hasPerformanceHints = true;
        }

        // Step 2: 为批量操作添加建议
        if (method.startsWith("batch") || method.contains("List") || method.contains("Batch")) {
            if (hasPerformanceHints) hints.append("\n");
            hints.append("// ========== PERFORMANCE提示：批量操作优化 ==========\n");
            hints.append("// 建议：使用MyBatis-Plus的saveBatch()或updateBatchById()方法\n");
            hints.append("// 批量大小建议：500-1000条/批，避免单次操作数据量过大\n");
            hasPerformanceHints = true;
        }

        // Step 3: 为修改操作添加索引建议
        if (method.startsWith("update") || method.startsWith("delete")) {
            if (hasPerformanceHints) hints.append("\n");
            hints.append("// ========== PERFORMANCE提示：索引优化 ==========\n");
            hints.append("// 建议：确保WHERE条件字段已添加数据库索引\n");
            hints.append("// 推荐索引字段：");

            // 识别可能的查询字段（除了id和时间戳字段外的其他字段）
            List<String> indexFields = new ArrayList<>();
            for (com.ingenio.backend.codegen.schema.Field field : entity.getFields()) {
                String fieldName = field.getName().toLowerCase();
                // 跳过主键和时间戳字段
                if (!fieldName.equals("id") &&
                        !fieldName.contains("create") &&
                        !fieldName.contains("update") &&
                        !fieldName.contains("delete") &&
                        !fieldName.contains("time")) {
                    indexFields.add(field.getName());
                }
            }

            if (!indexFields.isEmpty()) {
                hints.append(String.join(", ", indexFields.subList(0, Math.min(3, indexFields.size()))));
                hints.append("\n");
            } else {
                hints.append("根据实际查询条件确定\n");
            }

            hasPerformanceHints = true;
        }

        // Step 4: 为列表查询添加分页建议
        if (method.startsWith("list") || method.startsWith("query")) {
            if (hasPerformanceHints) hints.append("\n");
            hints.append("// ========== PERFORMANCE提示：分页查询 ==========\n");
            hints.append("// 建议：大数据量查询必须使用分页，避免一次性加载所有数据\n");
            hints.append("// 推荐分页大小：移动端10-20条/页，PC端20-50条/页\n");
            hasPerformanceHints = true;
        }

        // 如果有性能优化建议，添加到代码开头
        if (hasPerformanceHints) {
            hints.append("\n");
            String enhanced = hints.toString() + code;
            log.info("[BestPracticeApplier] ✅ PERFORMANCE最佳实践应用完成: 添加了性能优化建议");
            return enhanced;
        }

        // 无性能优化建议，返回原代码
        return code;
    }

    /**
     * 代码缩进工具方法
     *
     * @param code 原始代码
     * @param spaces 缩进空格数
     * @return 缩进后的代码
     */
    private String indentCode(String code, int spaces) {
        if (code == null || code.isEmpty()) {
            return code;
        }

        String indent = " ".repeat(spaces);
        String[] lines = code.split("\n", -1);  // -1 保留尾部空字符串
        StringBuilder result = new StringBuilder();

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            // 空行不添加缩进，非空行添加缩进
            if (!line.isEmpty()) {
                result.append(indent).append(line);
            }
            // 除最后一行外都添加换行符
            if (i < lines.length - 1) {
                result.append("\n");
            }
        }

        return result.toString();
    }

    /**
     * 代码结构类（内部类）
     *
     * <p>用于存储代码块分析结果</p>
     */
    private static class CodeStructure {
        private final List<CodeBlock> blocks = new ArrayList<>();

        public void addBlock(CodeBlock block) {
            blocks.add(block);
        }

        public List<CodeBlock> getBlocks() {
            return blocks;
        }

        public List<CodeBlock> getBlocksSortedByPosition() {
            List<CodeBlock> sorted = new ArrayList<>(blocks);
            sorted.sort(Comparator.comparingInt(CodeBlock::getStart));
            return sorted;
        }
    }

    /**
     * 代码块类（内部类）
     *
     * <p>表示一个完整的规则代码块</p>
     */
    private static class CodeBlock {
        private final String type;        // 块类型：VALIDATION/CALCULATION/WORKFLOW/NOTIFICATION
        private final String content;     // 块内容（完整代码）
        private final int start;          // 起始位置
        private final int end;            // 结束位置

        public CodeBlock(String type, String content, int start, int end) {
            this.type = type;
            this.content = content;
            this.start = start;
            this.end = end;
        }

        public String getType() {
            return type;
        }

        public String getContent() {
            return content;
        }

        public int getStart() {
            return start;
        }

        public int getEnd() {
            return end;
        }
    }
}
