package com.ingenio.backend.codegen.ai.generator;

import com.ingenio.backend.codegen.schema.Entity;
import com.ingenio.backend.codegen.schema.Field;
import com.ingenio.backend.codegen.schema.FieldType;
import com.ingenio.backend.codegen.template.TemplateEngine;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * BestPracticeApplier单元测试（V2.0 Phase 4.3）
 *
 * <p>轻量级单元测试，不依赖Spring上下文，提升测试速度</p>
 *
 * <p>测试内容：</p>
 * <ul>
 *   <li>代码结构分析：识别VALIDATION/CALCULATION/WORKFLOW/NOTIFICATION块</li>
 *   <li>CODE_QUALITY增强：验证异常处理包装、日志记录</li>
 *   <li>边界情况：空代码、无规则块</li>
 * </ul>
 *
 * @author Ingenio Code Generator
 * @since 2025-11-18 V2.0 Phase 4.3: 最佳实践应用器测试
 */
@Slf4j
class BestPracticeApplierTest {

    private BestPracticeApplier bestPracticeApplier;
    private TemplateEngine templateEngine;
    private Entity testEntity;

    @BeforeEach
    void setUp() {
        log.info("====== Phase 4.3单元测试：BestPracticeApplier ======");

        // 初始化TemplateEngine（无依赖，直接实例化）
        templateEngine = new TemplateEngine();

        // 初始化BestPracticeApplier
        bestPracticeApplier = new BestPracticeApplier(templateEngine);

        // 构建测试用Entity
        testEntity = Entity.builder()
                .name("Order")
                .description("订单实体")
                .fields(Arrays.asList(
                        Field.builder().name("id").type(FieldType.BIGINT).build(),
                        Field.builder().name("quantity").type(FieldType.INTEGER).build(),
                        Field.builder().name("total_price").type(FieldType.NUMERIC).precision(10).scale(2).build(),
                        Field.builder().name("status").type(FieldType.VARCHAR).length(50).build()
                ))
                .build();

        log.info("setUp完成: TemplateEngine初始化成功, Entity创建成功");
    }

    @Test
    void testApplyCodeQualityEnhancements() {
        log.info("====== 测试1: CODE_QUALITY最佳实践应用 ======");

        // 准备基础业务逻辑代码（来自BusinessLogicGenerator的输出）
        String baseCode = """
            // ========== VALIDATION规则（数据验证） ==========
            if (order.getQuantity() < 1) {
                throw new BusinessException(ErrorCode.INVALID_QUANTITY, "订单数量必须≥1");
            }

            // ========== CALCULATION规则（业务计算） ==========
            BigDecimal totalPrice = order.getUnitPrice().multiply(BigDecimal.valueOf(order.getQuantity()));
            order.setTotalPrice(totalPrice);
            """;

        // 应用最佳实践增强
        String enhancedCode = bestPracticeApplier.apply(baseCode, testEntity, "createOrder");

        // 验证1: 增强后的代码长度应该大于原始代码（添加了日志和异常处理）
        assertThat(enhancedCode.length())
                .as("增强后代码长度应该大于原始代码")
                .isGreaterThan(baseCode.length());

        // 验证2: 应该包含日志语句
        assertThat(enhancedCode)
                .as("应该包含日志语句")
                .contains("log.debug", "log.info");

        // 验证3: VALIDATION块应该包含try-catch包装
        assertThat(enhancedCode)
                .as("VALIDATION块应该包含try-catch包装")
                .contains("try {", "} catch (BusinessException e) {");

        // 验证4: 应该包含服务名称（OrderService）
        assertThat(enhancedCode)
                .as("应该包含服务名称OrderService")
                .contains("OrderService");

        log.info("✅ 测试1通过: CODE_QUALITY最佳实践应用成功");
        log.info("原始代码长度: {}, 增强后代码长度: {}", baseCode.length(), enhancedCode.length());
        log.debug("增强后代码:\n{}", enhancedCode);
    }

    @Test
    void testApplyWithEmptyCode() {
        log.info("====== 测试2: 处理空代码输入 ======");

        String emptyCode = "";
        String result = bestPracticeApplier.apply(emptyCode, testEntity, "createOrder");

        assertThat(result)
                .as("空代码输入应该返回原始空字符串")
                .isEqualTo(emptyCode);

        log.info("✅ 测试2通过: 空代码边界情况处理正确");
    }

    @Test
    void testApplyWithNullCode() {
        log.info("====== 测试3: 处理null代码输入 ======");

        String result = bestPracticeApplier.apply(null, testEntity, "createOrder");

        assertThat(result)
                .as("null代码输入应该返回null")
                .isNull();

        log.info("✅ 测试3通过: null代码边界情况处理正确");
    }

    @Test
    void testApplyWithMultipleRuleBlocks() {
        log.info("====== 测试4: 处理多个规则块 ======");

        // 准备包含所有4种规则类型的代码
        String baseCode = """
            // ========== VALIDATION规则（数据验证） ==========
            if (order.getQuantity() < 1) {
                throw new BusinessException(ErrorCode.INVALID_QUANTITY, "订单数量必须≥1");
            }

            // ========== CALCULATION规则（业务计算） ==========
            BigDecimal totalPrice = order.getUnitPrice().multiply(BigDecimal.valueOf(order.getQuantity()));
            order.setTotalPrice(totalPrice);

            // ========== WORKFLOW规则（状态流转） ==========
            if ("PENDING".equals(order.getStatus())) {
                order.setStatus("CONFIRMED");
            }

            // ========== NOTIFICATION规则（消息通知） ==========
            emailService.send(order.getUserEmail(), "订单已确认", "订单号: " + order.getId());
            """;

        String enhancedCode = bestPracticeApplier.apply(baseCode, testEntity, "createOrder");

        // 验证所有规则类型都被识别和增强
        assertThat(enhancedCode)
                .as("应该识别所有4种规则类型")
                .contains("VALIDATION规则", "CALCULATION规则", "WORKFLOW规则", "NOTIFICATION规则");

        // 验证每种规则块都添加了日志
        assertThat(enhancedCode)
                .as("每种规则块都应该有开始日志")
                .contains(
                        "开始执行VALIDATION规则",
                        "开始执行CALCULATION规则",
                        "开始执行WORKFLOW规则",
                        "开始执行NOTIFICATION规则"
                );

        log.info("✅ 测试4通过: 多个规则块处理成功");
        log.debug("增强后代码:\n{}", enhancedCode);
    }

    @Test
    void testApplyWithCodeWithoutRuleBlocks() {
        log.info("====== 测试5: 处理无规则块的普通代码 ======");

        String plainCode = "System.out.println(\"Hello World\");";
        String result = bestPracticeApplier.apply(plainCode, testEntity, "createOrder");

        // 无规则块的代码应该保持不变（仅经过结构分析）
        assertThat(result)
                .as("无规则块的代码应该基本保持不变")
                .contains("Hello World");

        log.info("✅ 测试5通过: 无规则块代码处理正确");
    }

    @Test
    void testExceptionHandlingEnhancement() {
        log.info("====== 测试6: 异常处理增强验证 ======");

        String baseCode = """
            // ========== VALIDATION规则（数据验证） ==========
            if (order.getQuantity() < 1) {
                throw new BusinessException(ErrorCode.INVALID_QUANTITY, "订单数量必须≥1");
            }
            """;

        String enhancedCode = bestPracticeApplier.apply(baseCode, testEntity, "createOrder");

        // 验证异常处理结构完整性
        assertThat(enhancedCode)
                .as("应该包含完整的try-catch-throw结构")
                .contains("try {")
                .contains("} catch (BusinessException e) {")
                .contains("throw e;");

        // 验证异常日志记录
        assertThat(enhancedCode)
                .as("应该包含异常日志记录")
                .contains("log.error");

        log.info("✅ 测试6通过: 异常处理增强验证成功");
    }

    @Test
    void testLogEnhancement() {
        log.info("====== 测试7: 日志增强验证 ======");

        String baseCode = """
            // ========== CALCULATION规则（业务计算） ==========
            BigDecimal totalPrice = order.getUnitPrice().multiply(BigDecimal.valueOf(order.getQuantity()));
            order.setTotalPrice(totalPrice);
            """;

        String enhancedCode = bestPracticeApplier.apply(baseCode, testEntity, "createOrder");

        // 验证日志级别多样性
        assertThat(enhancedCode)
                .as("应该包含debug级别日志（开始）")
                .contains("log.debug");

        assertThat(enhancedCode)
                .as("应该包含info级别日志（完成）")
                .contains("log.info");

        // 验证日志参数化
        assertThat(enhancedCode)
                .as("日志应该使用参数化格式（含{}占位符）")
                .contains("{}");

        log.info("✅ 测试7通过: 日志增强验证成功");
    }

    @Test
    void testCodeIndentation() {
        log.info("====== 测试8: 代码缩进正确性验证 ======");

        String baseCode = """
            // ========== VALIDATION规则（数据验证） ==========
            if (order.getQuantity() < 1) {
                throw new BusinessException(ErrorCode.INVALID_QUANTITY, "订单数量必须≥1");
            }
            """;

        String enhancedCode = bestPracticeApplier.apply(baseCode, testEntity, "createOrder");

        // 验证try块内的代码有正确的缩进（4空格）
        assertThat(enhancedCode)
                .as("try块内代码应该有正确的缩进")
                .contains("    if (order.getQuantity()");

        log.info("✅ 测试8通过: 代码缩进正确性验证成功");
        log.debug("增强后代码:\n{}", enhancedCode);
    }

    // ========== Phase 4.3.2: SECURITY模块测试 ==========

    @Test
    void testApplySecurityPractices() {
        log.info("====== 测试9: SECURITY最佳实践应用 ======");

        // 构建包含敏感字段的实体
        Entity entityWithSensitiveFields = Entity.builder()
                .name("User")
                .description("用户实体")
                .fields(Arrays.asList(
                        Field.builder().name("id").type(FieldType.BIGINT).build(),
                        Field.builder().name("username").type(FieldType.VARCHAR).length(50).build(),
                        Field.builder().name("password").type(FieldType.VARCHAR).length(255).build(),
                        Field.builder().name("email").type(FieldType.VARCHAR).length(100).build(),
                        Field.builder().name("phone").type(FieldType.VARCHAR).length(20).build(),
                        Field.builder().name("id_card").type(FieldType.VARCHAR).length(18).build()
                ))
                .build();

        // 准备基础代码
        String baseCode = """
            User user = userRepository.findById(userId);
            log.info("查询到用户: {}", user);
            return user;
            """;

        // 应用最佳实践
        String enhancedCode = bestPracticeApplier.apply(baseCode, entityWithSensitiveFields, "getUserById");

        // 验证1: 应该包含安全提示注释头
        assertThat(enhancedCode)
                .as("应该包含SECURITY提示注释头")
                .contains("========== SECURITY提示：敏感字段脱敏 ==========");

        // 验证2: 应该识别出敏感字段
        assertThat(enhancedCode)
                .as("应该识别出password等敏感字段")
                .contains("检测到敏感字段：");

        // 验证3: 应该提供脱敏建议
        assertThat(enhancedCode)
                .as("应该提供DataMaskingUtil.mask()脱敏建议")
                .contains("DataMaskingUtil.mask()");

        log.info("✅ 测试9通过: SECURITY最佳实践应用成功");
        log.debug("增强后代码:\n{}", enhancedCode);
    }

    @Test
    void testSecurityWithNoSensitiveFields() {
        log.info("====== 测试10: SECURITY处理无敏感字段实体 ======");

        // 使用testEntity（Order实体，无敏感字段）
        String baseCode = "Order order = orderRepository.findById(orderId);\nreturn order;";
        String enhancedCode = bestPracticeApplier.apply(baseCode, testEntity, "getOrderById");

        // 验证：无敏感字段时不应添加安全提示
        assertThat(enhancedCode)
                .as("无敏感字段时不应添加SECURITY提示")
                .doesNotContain("========== SECURITY提示：敏感字段脱敏 ==========");

        log.info("✅ 测试10通过: 无敏感字段边界情况处理正确");
    }

    @Test
    void testSecurityPermissionCheckForUpdateMethods() {
        log.info("====== 测试11: SECURITY修改操作权限校验提示 ======");

        // 构建包含敏感字段的实体
        Entity entityWithSensitiveFields = Entity.builder()
                .name("User")
                .description("用户实体")
                .fields(Arrays.asList(
                        Field.builder().name("id").type(FieldType.BIGINT).build(),
                        Field.builder().name("password").type(FieldType.VARCHAR).length(255).build()
                ))
                .build();

        String baseCode = "userRepository.updateById(userId, user);";

        // 应用最佳实践（updateUser方法）
        String enhancedCode = bestPracticeApplier.apply(baseCode, entityWithSensitiveFields, "updateUser");

        // 验证：update方法应该包含权限校验提示
        assertThat(enhancedCode)
                .as("update方法应该包含数据权限校验提示")
                .contains("建议：添加数据权限校验")
                .contains("checkDataPermission");

        log.info("✅ 测试11通过: 修改操作权限校验提示正确");
    }

    // ========== Phase 4.3.3: PERFORMANCE模块测试 ==========

    @Test
    void testApplyPerformancePracticesForQuery() {
        log.info("====== 测试12: PERFORMANCE查询方法缓存建议 ======");

        String baseCode = "Order order = orderRepository.findById(orderId);\nreturn order;";
        String enhancedCode = bestPracticeApplier.apply(baseCode, testEntity, "getOrderById");

        // 验证1: 查询方法应该包含缓存建议
        assertThat(enhancedCode)
                .as("查询方法应该包含PERFORMANCE缓存提示")
                .contains("========== PERFORMANCE提示：缓存优化 ==========");

        // 验证2: 应该提供Redis缓存示例
        assertThat(enhancedCode)
                .as("应该提供Redis缓存示例")
                .contains("@Cacheable");

        // 验证3: 应该提供缓存过期时间建议
        assertThat(enhancedCode)
                .as("应该提供缓存过期时间建议")
                .contains("缓存过期时间建议");

        log.info("✅ 测试12通过: 查询方法缓存建议正确");
        log.debug("增强后代码:\n{}", enhancedCode);
    }

    @Test
    void testApplyPerformancePracticesForBatch() {
        log.info("====== 测试13: PERFORMANCE批量操作建议 ======");

        String baseCode = "orderRepository.saveBatch(orderList);";
        String enhancedCode = bestPracticeApplier.apply(baseCode, testEntity, "batchInsertOrders");

        // 验证：批量方法应该包含批量操作优化建议
        assertThat(enhancedCode)
                .as("批量方法应该包含批量操作优化提示")
                .contains("========== PERFORMANCE提示：批量操作优化 ==========")
                .contains("saveBatch()")
                .contains("批量大小建议");

        log.info("✅ 测试13通过: 批量操作建议正确");
    }

    @Test
    void testApplyPerformancePracticesForUpdate() {
        log.info("====== 测试14: PERFORMANCE更新操作索引建议 ======");

        String baseCode = "orderRepository.updateById(orderId, order);";
        String enhancedCode = bestPracticeApplier.apply(baseCode, testEntity, "updateOrder");

        // 验证：update方法应该包含索引优化建议
        assertThat(enhancedCode)
                .as("update方法应该包含索引优化提示")
                .contains("========== PERFORMANCE提示：索引优化 ==========")
                .contains("推荐索引字段");

        log.info("✅ 测试14通过: 更新操作索引建议正确");
    }

    @Test
    void testApplyPerformancePracticesForList() {
        log.info("====== 测试15: PERFORMANCE列表查询分页建议 ======");

        String baseCode = "List<Order> orders = orderRepository.queryAll();";
        String enhancedCode = bestPracticeApplier.apply(baseCode, testEntity, "listOrders");

        // 验证：list方法应该包含分页建议
        assertThat(enhancedCode)
                .as("list方法应该包含分页查询提示")
                .contains("========== PERFORMANCE提示：分页查询 ==========")
                .contains("必须使用分页")
                .contains("推荐分页大小");

        log.info("✅ 测试15通过: 列表查询分页建议正确");
    }

    @Test
    void testApplyPerformancePracticesWithNoHints() {
        log.info("====== 测试16: PERFORMANCE无优化建议的普通方法 ======");

        String baseCode = "System.out.println(\"Hello\");";
        String enhancedCode = bestPracticeApplier.apply(baseCode, testEntity, "printMessage");

        // 验证：非查询/批量/修改方法不应添加性能提示
        assertThat(enhancedCode)
                .as("普通方法不应添加PERFORMANCE提示")
                .doesNotContain("========== PERFORMANCE提示");

        log.info("✅ 测试16通过: 无优化建议的边界情况处理正确");
    }
}
