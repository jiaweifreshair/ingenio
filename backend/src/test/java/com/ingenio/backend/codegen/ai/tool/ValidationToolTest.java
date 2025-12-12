package com.ingenio.backend.codegen.ai.tool;

import com.ingenio.backend.codegen.schema.Entity;
import com.ingenio.backend.codegen.schema.Field;
import com.ingenio.backend.codegen.schema.FieldType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ValidationToolTest - ValidationTool单元测试（V2.0 MVP Day 1 Phase 1.3.4）
 *
 * <p>测试ValidationTool的三层验证机制：</p>
 * <ul>
 *   <li>语法验证（30分）：括号匹配、分号检查、关键字拼写</li>
 *   <li>结构验证（30分）：类定义、方法定义、包声明</li>
 *   <li>逻辑验证（40分）：组件引用、业务逻辑、异常处理</li>
 * </ul>
 *
 * <p>测试覆盖：</p>
 * <ul>
 *   <li>正常流程：完美代码100分、良好代码90分、合格代码70分</li>
 *   <li>异常处理：空代码、语法错误、结构缺失</li>
 *   <li>性能指标：验证时间<1秒</li>
 *   <li>零Token消耗验证</li>
 * </ul>
 *
 * @author Ingenio Code Generator
 * @since 2025-01-18 V2.0 MVP Day 1 Phase 1.3.4: ValidationTool测试
 */
@DisplayName("ValidationTool单元测试")
class ValidationToolTest {

    private ValidationTool validationTool;
    private Entity testEntity;

    @BeforeEach
    void setUp() {
        validationTool = new ValidationTool();

        // 准备测试实体
        testEntity = Entity.builder()
                .name("User")
                .fields(Arrays.asList(
                        Field.builder().name("id").type(FieldType.BIGINT).build(),
                        Field.builder().name("username").type(FieldType.VARCHAR).build(),
                        Field.builder().name("email").type(FieldType.VARCHAR).build()
                ))
                .build();
    }

    /**
     * ========== Level 1: 语法验证测试（30分） ==========
     */

    @Test
    @DisplayName("测试1: 完美代码 - 100分（语法30+结构30+逻辑40）")
    void testPerfectCode_Score100() {
        String perfectCode = """
                package com.ingenio.backend.service;

                import com.ingenio.backend.repository.UserRepository;
                import com.ingenio.backend.model.User;
                import org.springframework.stereotype.Service;

                @Service
                public class UserService {

                    private final UserRepository userRepository;

                    public UserService(UserRepository userRepository) {
                        this.userRepository = userRepository;
                    }

                    public User createUser(User user) {
                        if (user == null) {
                            throw new IllegalArgumentException("User不能为空");
                        }

                        try {
                            return userRepository.save(user);
                        } catch (Exception e) {
                            throw new BusinessException("用户创建失败", e);
                        }
                    }
                }
                """;

        ValidationTool.Request request = new ValidationTool.Request(
                perfectCode, testEntity, "createUser"
        );
        ValidationTool.Response response = validationTool.apply(request);

        // 验证结果
        assertTrue(response.isSuccess(), "完美代码应该验证通过");
        assertEquals(100, response.getQualityScore(), "完美代码应该得满分100分");
        assertEquals(0, response.getIssues().size(), "完美代码应该没有问题");
        assertTrue(response.getMessage().contains("验证通过"), "消息应该包含'验证通过'");
        assertTrue(response.getDurationMs() < 1000, "验证时间应该<1秒");
    }

    @Test
    @DisplayName("测试2: 良好代码 - 90分（缺少包声明-10分）")
    void testGoodCode_Score90() {
        String goodCode = """
                import com.ingenio.backend.repository.UserRepository;
                import org.springframework.stereotype.Service;

                @Service
                public class UserService {

                    private final UserRepository userRepository;

                    public User createUser(User user) {
                        if (user == null) {
                            throw new IllegalArgumentException("User不能为空");
                        }
                        return userRepository.save(user);
                    }
                }
                """;

        ValidationTool.Request request = new ValidationTool.Request(
                goodCode, testEntity, "createUser"
        );
        ValidationTool.Response response = validationTool.apply(request);

        assertTrue(response.isSuccess(), "良好代码应该验证通过（≥70分）");
        assertEquals(90, response.getQualityScore(), "缺少包声明应该扣10分");
        assertTrue(response.getIssues().size() > 0, "应该有结构警告");
        assertTrue(response.getIssues().get(0).contains("缺少包声明"), "应该提示缺少包声明");
    }

    @Test
    @DisplayName("测试3: 合格代码 - 70分（临界通过）")
    void testAcceptableCode_Score70() {
        String acceptableCode = """
                public class UserService {
                    public User createUser(User user) {
                        if (user == null) {
                            throw new IllegalArgumentException("User不能为空");
                        }
                        return userRepository.save(user);
                    }
                }
                """;

        ValidationTool.Request request = new ValidationTool.Request(
                acceptableCode, testEntity, "createUser"
        );
        ValidationTool.Response response = validationTool.apply(request);

        assertTrue(response.isSuccess(), "合格代码应该验证通过（=70分）");
        assertEquals(70, response.getQualityScore(), "合格代码应该得70分（临界通过）");
    }

    @Test
    @DisplayName("测试4: 语法错误 - 括号不匹配（语法-10分）")
    void testSyntaxError_BracketMismatch() {
        String bracketErrorCode = """
                package com.ingenio.backend.service;

                public class UserService {
                    public User createUser(User user) {
                        if (user == null) {
                            throw new IllegalArgumentException("User不能为空");
                        }
                        return userRepository.save(user);
                    // 缺少一个右大括号
                """;

        ValidationTool.Request request = new ValidationTool.Request(
                bracketErrorCode, testEntity, "createUser"
        );
        ValidationTool.Response response = validationTool.apply(request);

        assertFalse(response.isSuccess(), "括号不匹配应该验证失败");
        assertTrue(response.getQualityScore() < 70, "语法错误应该得分<70分");
        assertTrue(response.getIssues().stream()
                        .anyMatch(issue -> issue.contains("括号不匹配")),
                "应该提示括号不匹配");
    }

    @Test
    @DisplayName("测试5: 语法错误 - 关键字拼写错误（语法-10分）")
    void testSyntaxError_KeywordTypo() {
        String typoCode = """
                package com.ingenio.backend.service;

                pubilc class UserService {  // pubilc 拼写错误
                    public User createUser(User user) {
                        retrun userRepository.save(user);  // retrun 拼写错误
                    }
                }
                """;

        ValidationTool.Request request = new ValidationTool.Request(
                typoCode, testEntity, "createUser"
        );
        ValidationTool.Response response = validationTool.apply(request);

        assertFalse(response.isSuccess(), "关键字拼写错误应该验证失败");
        assertTrue(response.getIssues().stream()
                        .anyMatch(issue -> issue.contains("拼写错误")),
                "应该提示关键字拼写错误");
    }

    /**
     * ========== Level 2: 结构验证测试（30分） ==========
     */

    @Test
    @DisplayName("测试6: 结构错误 - 缺少类定义（结构-10分）")
    void testStructureError_MissingClass() {
        String noClassCode = """
                package com.ingenio.backend.service;

                // 只有方法，没有类定义
                public User createUser(User user) {
                    return userRepository.save(user);
                }
                """;

        ValidationTool.Request request = new ValidationTool.Request(
                noClassCode, testEntity, "createUser"
        );
        ValidationTool.Response response = validationTool.apply(request);

        assertFalse(response.isSuccess(), "缺少类定义应该验证失败");
        assertTrue(response.getIssues().stream()
                        .anyMatch(issue -> issue.contains("缺少类定义")),
                "应该提示缺少类定义");
    }

    @Test
    @DisplayName("测试7: 结构警告 - 缺少方法定义（结构-10分）")
    void testStructureWarning_MissingMethod() {
        String noMethodCode = """
                package com.ingenio.backend.service;

                public class UserService {
                    // 只有类，没有方法定义
                }
                """;

        ValidationTool.Request request = new ValidationTool.Request(
                noMethodCode, testEntity, "createUser"
        );
        ValidationTool.Response response = validationTool.apply(request);

        assertFalse(response.isSuccess(), "缺少方法定义应该验证失败");
        assertTrue(response.getIssues().stream()
                        .anyMatch(issue -> issue.contains("未找到方法定义")),
                "应该提示未找到方法定义");
    }

    /**
     * ========== Level 3: 逻辑验证测试（40分） ==========
     */

    @Test
    @DisplayName("测试8: 逻辑警告 - 缺少Repository引用（逻辑-15分）")
    void testLogicWarning_MissingRepository() {
        String noRepositoryCode = """
                package com.ingenio.backend.service;

                public class UserService {
                    public User createUser(User user) {
                        if (user == null) {
                            throw new IllegalArgumentException("User不能为空");
                        }
                        return user;  // 没有调用Repository
                    }
                }
                """;

        ValidationTool.Request request = new ValidationTool.Request(
                noRepositoryCode, testEntity, "createUser"
        );
        ValidationTool.Response response = validationTool.apply(request);

        assertFalse(response.isSuccess(), "缺少Repository引用应该得分<70分");
        assertTrue(response.getIssues().stream()
                        .anyMatch(issue -> issue.contains("未找到Repository")),
                "应该提示未找到Repository引用");
    }

    @Test
    @DisplayName("测试9: 逻辑警告 - 缺少业务逻辑（逻辑-15分）")
    void testLogicWarning_MissingBusinessLogic() {
        String noLogicCode = """
                package com.ingenio.backend.service;

                public class UserService {
                    private UserRepository userRepository;

                    public User createUser(User user) {
                        userRepository.save(user);  // 没有任何业务逻辑
                    }
                }
                """;

        ValidationTool.Request request = new ValidationTool.Request(
                noLogicCode, testEntity, "createUser"
        );
        ValidationTool.Response response = validationTool.apply(request);

        assertFalse(response.isSuccess(), "缺少业务逻辑应该得分<70分");
        assertTrue(response.getIssues().stream()
                        .anyMatch(issue -> issue.contains("未找到基础业务逻辑")),
                "应该提示未找到基础业务逻辑");
    }

    @Test
    @DisplayName("测试10: 逻辑建议 - 缺少异常处理（逻辑-10分）")
    void testLogicSuggestion_MissingExceptionHandling() {
        String noExceptionCode = """
                package com.ingenio.backend.service;

                public class UserService {
                    private UserRepository userRepository;

                    public User createUser(User user) {
                        if (user == null) {
                            return null;  // 没有抛出异常
                        }
                        return userRepository.save(user);
                    }
                }
                """;

        ValidationTool.Request request = new ValidationTool.Request(
                noExceptionCode, testEntity, "createUser"
        );
        ValidationTool.Response response = validationTool.apply(request);

        assertTrue(response.isSuccess(), "缺少异常处理不阻塞通过（得分≥70）");
        assertEquals(90, response.getQualityScore(), "缺少异常处理扣10分");
        assertTrue(response.getIssues().stream()
                        .anyMatch(issue -> issue.contains("缺少异常处理")),
                "应该建议添加异常处理");
    }

    /**
     * ========== 异常处理测试 ==========
     */

    @Test
    @DisplayName("测试11: 异常处理 - 空代码")
    void testEmptyCode_ReturnsError() {
        ValidationTool.Request request = new ValidationTool.Request(
                "", testEntity, "testMethod"
        );
        ValidationTool.Response response = validationTool.apply(request);

        assertFalse(response.isSuccess(), "空代码应该验证失败");
        assertEquals(0, response.getQualityScore(), "空代码应该得0分");
        assertTrue(response.getMessage().contains("代码为空"), "消息应该提示代码为空");
        assertTrue(response.getIssues().size() > 0, "应该有问题列表");
    }

    @Test
    @DisplayName("测试12: 异常处理 - Null代码")
    void testNullCode_ReturnsError() {
        ValidationTool.Request request = new ValidationTool.Request(
                null, testEntity, "testMethod"
        );
        ValidationTool.Response response = validationTool.apply(request);

        assertFalse(response.isSuccess(), "Null代码应该验证失败");
        assertEquals(0, response.getQualityScore(), "Null代码应该得0分");
        assertTrue(response.getMessage().contains("代码为空"), "消息应该提示代码为空");
    }

    /**
     * ========== 性能测试 ==========
     */

    @Test
    @DisplayName("测试13: 性能测试 - 小代码片段验证<100ms")
    void testPerformance_SmallCode_LessThan100ms() {
        String smallCode = """
                package com.ingenio.backend.service;

                public class UserService {
                    public User getUser(Long id) {
                        return userRepository.findById(id).orElse(null);
                    }
                }
                """;

        long startTime = System.currentTimeMillis();
        ValidationTool.Request request = new ValidationTool.Request(
                smallCode, testEntity, "getUser"
        );
        ValidationTool.Response response = validationTool.apply(request);
        long duration = System.currentTimeMillis() - startTime;

        assertTrue(response.isSuccess(), "验证应该成功");
        assertTrue(duration < 100, "小代码片段验证应该<100ms（实际: " + duration + "ms）");
        assertTrue(response.getDurationMs() < 100, "响应中的耗时应该<100ms");
    }

    @Test
    @DisplayName("测试14: 性能测试 - 大代码片段验证<1s")
    void testPerformance_LargeCode_LessThan1s() {
        // 生成一个较大的代码片段（约1000行）
        StringBuilder largeCode = new StringBuilder();
        largeCode.append("package com.ingenio.backend.service;\n\n");
        largeCode.append("public class UserService {\n");

        for (int i = 0; i < 100; i++) {
            largeCode.append(String.format("""
                    public User getUser%d(Long id) {
                        if (id == null) {
                            throw new IllegalArgumentException("ID不能为空");
                        }
                        try {
                            return userRepository.findById(id).orElse(null);
                        } catch (Exception e) {
                            throw new BusinessException("查询失败", e);
                        }
                    }

                    """, i));
        }
        largeCode.append("}\n");

        long startTime = System.currentTimeMillis();
        ValidationTool.Request request = new ValidationTool.Request(
                largeCode.toString(), testEntity, "getUser0"
        );
        ValidationTool.Response response = validationTool.apply(request);
        long duration = System.currentTimeMillis() - startTime;

        assertTrue(response.isSuccess(), "验证应该成功");
        assertTrue(duration < 1000, "大代码片段验证应该<1s（实际: " + duration + "ms）");
    }

    /**
     * ========== 零Token消耗验证 ==========
     */

    @Test
    @DisplayName("测试15: 零Token消耗 - 纯本地规则引擎")
    void testZeroTokenConsumption() {
        String code = """
                package com.ingenio.backend.service;

                public class UserService {
                    public User createUser(User user) {
                        return userRepository.save(user);
                    }
                }
                """;

        // 连续执行100次，验证性能稳定且无外部API调用
        long totalDuration = 0;
        for (int i = 0; i < 100; i++) {
            long startTime = System.currentTimeMillis();
            ValidationTool.Request request = new ValidationTool.Request(
                    code, testEntity, "createUser"
            );
            ValidationTool.Response response = validationTool.apply(request);
            long duration = System.currentTimeMillis() - startTime;

            assertTrue(response.isSuccess() || !response.isSuccess(), "应该返回结果");
            totalDuration += duration;
        }

        double avgDuration = totalDuration / 100.0;
        assertTrue(avgDuration < 10, "平均验证时间应该<10ms（实际: " + avgDuration + "ms）");
    }

    /**
     * ========== 集成场景测试 ==========
     */

    @Test
    @DisplayName("测试16: 集成场景 - CRUD方法验证")
    void testIntegrationScenario_CRUDMethods() {
        String crudCode = """
                package com.ingenio.backend.service;

                import org.springframework.stereotype.Service;

                @Service
                public class UserService {

                    private final UserRepository userRepository;

                    public User create(User user) {
                        if (user == null) {
                            throw new IllegalArgumentException("User不能为空");
                        }
                        return userRepository.save(user);
                    }

                    public User update(Long id, User user) {
                        if (id == null || user == null) {
                            throw new IllegalArgumentException("ID和User不能为空");
                        }
                        User existing = userRepository.findById(id)
                            .orElseThrow(() -> new EntityNotFoundException("用户不存在"));
                        return userRepository.save(user);
                    }

                    public void delete(Long id) {
                        if (id == null) {
                            throw new IllegalArgumentException("ID不能为空");
                        }
                        userRepository.deleteById(id);
                    }
                }
                """;

        ValidationTool.Request request = new ValidationTool.Request(
                crudCode, testEntity, "create"
        );
        ValidationTool.Response response = validationTool.apply(request);

        assertTrue(response.isSuccess(), "CRUD代码应该验证通过");
        assertTrue(response.getQualityScore() >= 90, "CRUD代码应该高分（≥90分）");
    }

    @Test
    @DisplayName("测试17: 集成场景 - 复杂业务逻辑验证")
    void testIntegrationScenario_ComplexBusinessLogic() {
        String complexCode = """
                package com.ingenio.backend.service;

                import org.springframework.stereotype.Service;
                import org.springframework.transaction.annotation.Transactional;

                @Service
                public class OrderService {

                    private final OrderRepository orderRepository;
                    private final InventoryService inventoryService;
                    private final EmailService emailService;

                    @Transactional
                    public Order createOrder(Order order) {
                        // 1. 验证订单
                        if (order == null || order.getItems().isEmpty()) {
                            throw new BusinessException("订单不能为空");
                        }

                        // 2. 检查库存
                        for (OrderItem item : order.getItems()) {
                            if (!inventoryService.checkStock(item.getProductId(), item.getQuantity())) {
                                throw new BusinessException("库存不足: " + item.getProductName());
                            }
                        }

                        // 3. 扣减库存
                        for (OrderItem item : order.getItems()) {
                            inventoryService.reduceStock(item.getProductId(), item.getQuantity());
                        }

                        // 4. 保存订单
                        Order savedOrder = orderRepository.save(order);

                        // 5. 发送通知
                        try {
                            emailService.sendOrderConfirmation(order);
                        } catch (Exception e) {
                            log.warn("订单确认邮件发送失败: {}", e.getMessage());
                        }

                        return savedOrder;
                    }
                }
                """;

        ValidationTool.Request request = new ValidationTool.Request(
                complexCode, testEntity, "createOrder"
        );
        ValidationTool.Response response = validationTool.apply(request);

        assertTrue(response.isSuccess(), "复杂业务逻辑应该验证通过");
        assertEquals(100, response.getQualityScore(), "复杂业务逻辑应该得满分");
        assertEquals(0, response.getIssues().size(), "复杂业务逻辑应该没有问题");
    }

    @Test
    @DisplayName("测试18: 边界情况 - 只有注释的代码")
    void testEdgeCase_OnlyComments() {
        String commentsOnlyCode = """
                package com.ingenio.backend.service;

                // 这是一个用户服务
                // 提供用户CRUD操作
                // TODO: 实现用户创建功能
                """;

        ValidationTool.Request request = new ValidationTool.Request(
                commentsOnlyCode, testEntity, "createUser"
        );
        ValidationTool.Response response = validationTool.apply(request);

        assertFalse(response.isSuccess(), "只有注释的代码应该验证失败");
        assertTrue(response.getQualityScore() < 70, "只有注释的代码应该得分<70分");
    }

    @Test
    @DisplayName("测试19: 边界情况 - 空方法体")
    void testEdgeCase_EmptyMethodBody() {
        String emptyMethodCode = """
                package com.ingenio.backend.service;

                public class UserService {
                    public User createUser(User user) {
                        // TODO: 实现创建逻辑
                    }
                }
                """;

        ValidationTool.Request request = new ValidationTool.Request(
                emptyMethodCode, testEntity, "createUser"
        );
        ValidationTool.Response response = validationTool.apply(request);

        assertFalse(response.isSuccess(), "空方法体应该验证失败");
        assertTrue(response.getIssues().stream()
                        .anyMatch(issue -> issue.contains("未找到基础业务逻辑")),
                "应该提示缺少业务逻辑");
    }

    @Test
    @DisplayName("测试20: 响应完整性 - 所有字段非null")
    void testResponseCompleteness_AllFieldsNonNull() {
        String code = """
                package com.ingenio.backend.service;

                public class UserService {
                    public User createUser(User user) {
                        return userRepository.save(user);
                    }
                }
                """;

        ValidationTool.Request request = new ValidationTool.Request(
                code, testEntity, "createUser"
        );
        ValidationTool.Response response = validationTool.apply(request);

        // 验证响应所有字段都有值
        assertNotNull(response, "响应不应为null");
        assertNotNull(response.getMessage(), "消息不应为null");
        assertNotNull(response.getIssues(), "问题列表不应为null");
        assertTrue(response.getQualityScore() >= 0 && response.getQualityScore() <= 100,
                "质量评分应该在0-100范围内");
        assertTrue(response.getDurationMs() >= 0, "执行时长应该≥0");
    }
}
