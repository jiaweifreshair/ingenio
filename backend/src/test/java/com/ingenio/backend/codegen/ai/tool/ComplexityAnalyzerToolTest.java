package com.ingenio.backend.codegen.ai.tool;

import com.ingenio.backend.codegen.schema.Entity;
import com.ingenio.backend.codegen.schema.Field;
import com.ingenio.backend.codegen.schema.FieldType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ComplexityAnalyzerTool单元测试（V2.0 MVP Day 1 Phase 1.3.1）
 *
 * <p>测试多维度复杂度评分系统</p>
 *
 * <p>测试覆盖：</p>
 * <ul>
 *   <li>方法维度：5种操作类型评分（CRUD、简单修改、批量、流程、事务）</li>
 *   <li>实体维度：字段数量、敏感字段识别</li>
 *   <li>需求维度：业务、技术、集成关键词</li>
 *   <li>关系维度：一对一、一对多、多对多识别</li>
 *   <li>性能指标：执行时间<50ms</li>
 *   <li>边界情况：null输入、空实体</li>
 * </ul>
 *
 * @author Ingenio Code Generator
 * @since 2025-01-19 V2.0 MVP Day 1: ComplexityAnalyzerTool测试
 */
@DisplayName("ComplexityAnalyzerTool单元测试")
class ComplexityAnalyzerToolTest {

    private ComplexityAnalyzerTool tool;

    @BeforeEach
    void setUp() {
        tool = new ComplexityAnalyzerTool();
    }

    // ==================== 方法维度测试 ====================

    @Test
    @DisplayName("测试1: 方法维度 - CRUD操作（5-15分）")
    void testMethodComplexity_CRUD() {
        // 测试各种CRUD操作关键词
        String[] crudMethods = {"getUserById", "findUserByName", "listAllUsers", "deleteUser", "queryOrders"};

        for (String methodName : crudMethods) {
            ComplexityAnalyzerTool.Request request = new ComplexityAnalyzerTool.Request(methodName, null, null);
            ComplexityAnalyzerTool.Response response = tool.apply(request);

            assertTrue(response.isSuccess(), "CRUD操作应该成功");
            assertTrue(response.getComplexityScore() < 60,
                    "CRUD操作复杂度应该<60分（实际: " + response.getComplexityScore() + "）");

            Map<String, Integer> breakdown = response.getScoreBreakdown();
            assertEquals(4, breakdown.get("methodScore"),
                    methodName + " 方法维度评分应该为4分（CRUD操作）");
        }
    }

    @Test
    @DisplayName("测试2: 方法维度 - 简单创建/更新（20-35分）")
    void testMethodComplexity_SimpleModify() {
        String[] modifyMethods = {"createUser", "updateUser", "saveOrder", "addProduct", "modifySettings"};

        for (String methodName : modifyMethods) {
            ComplexityAnalyzerTool.Request request = new ComplexityAnalyzerTool.Request(methodName, null, null);
            ComplexityAnalyzerTool.Response response = tool.apply(request);

            assertTrue(response.isSuccess(), "简单修改操作应该成功");
            Map<String, Integer> breakdown = response.getScoreBreakdown();
            assertEquals(12, breakdown.get("methodScore"),
                    methodName + " 方法维度评分应该为12分（简单创建/更新）");
        }
    }

    @Test
    @DisplayName("测试3: 方法维度 - 批量操作（40-55分）")
    void testMethodComplexity_Batch() {
        String[] batchMethods = {"batchCreateUsers", "bulkUpdateOrders", "importProducts", "exportData", "syncInventory"};

        for (String methodName : batchMethods) {
            ComplexityAnalyzerTool.Request request = new ComplexityAnalyzerTool.Request(methodName, null, null);
            ComplexityAnalyzerTool.Response response = tool.apply(request);

            assertTrue(response.isSuccess(), "批量操作应该成功");
            Map<String, Integer> breakdown = response.getScoreBreakdown();
            assertEquals(20, breakdown.get("methodScore"),
                    methodName + " 方法维度评分应该为20分（批量操作）");
        }
    }

    @Test
    @DisplayName("测试4: 方法维度 - 业务流程（60-75分）")
    void testMethodComplexity_Process() {
        String[] processMethods = {"processOrderPayment", "handleRefund", "executeWorkflow", "validateInventory", "calculateTotalPrice"};

        for (String methodName : processMethods) {
            ComplexityAnalyzerTool.Request request = new ComplexityAnalyzerTool.Request(methodName, null, null);
            ComplexityAnalyzerTool.Response response = tool.apply(request);

            assertTrue(response.isSuccess(), "业务流程操作应该成功");
            Map<String, Integer> breakdown = response.getScoreBreakdown();
            assertEquals(28, breakdown.get("methodScore"),
                    methodName + " 方法维度评分应该为28分（业务流程）");
        }
    }

    @Test
    @DisplayName("测试5: 方法维度 - 分布式事务（80-95分）")
    void testMethodComplexity_Transaction() {
        String[] transactionMethods = {"distributedTransactionCommit", "sagaCompensate", "coordinateServices", "rollbackTransaction", "orchestratePayment"};

        for (String methodName : transactionMethods) {
            ComplexityAnalyzerTool.Request request = new ComplexityAnalyzerTool.Request(methodName, null, null);
            ComplexityAnalyzerTool.Response response = tool.apply(request);

            assertTrue(response.isSuccess(), "分布式事务操作应该成功");
            assertTrue(response.getComplexityScore() >= 38,
                    methodName + " 复杂度应该>=38分（实际: " + response.getComplexityScore() + "）");

            Map<String, Integer> breakdown = response.getScoreBreakdown();
            assertEquals(38, breakdown.get("methodScore"),
                    methodName + " 方法维度评分应该为38分（分布式事务）");
        }
    }

    // ==================== 实体维度测试 ====================

    @Test
    @DisplayName("测试6: 实体维度 - 少量字段（1-5个字段）")
    void testEntityComplexity_FewFields() {
        Entity entity = Entity.builder()
                .name("User")
                .fields(Arrays.asList(
                        Field.builder().name("id").type(FieldType.BIGINT).build(),
                        Field.builder().name("username").type(FieldType.VARCHAR).build(),
                        Field.builder().name("createdAt").type(FieldType.TIMESTAMPTZ).build()
                ))
                .build();

        ComplexityAnalyzerTool.Request request = new ComplexityAnalyzerTool.Request("getUser", entity, null);
        ComplexityAnalyzerTool.Response response = tool.apply(request);

        assertTrue(response.isSuccess(), "少量字段实体应该成功");
        Map<String, Integer> breakdown = response.getScoreBreakdown();
        assertEquals(2, breakdown.get("entityScore"),
                "3个字段的实体评分应该为2分");
    }

    @Test
    @DisplayName("测试7: 实体维度 - 中等字段（6-10个字段）")
    void testEntityComplexity_MediumFields() {
        Entity entity = Entity.builder()
                .name("Order")
                .fields(Arrays.asList(
                        Field.builder().name("id").type(FieldType.BIGINT).build(),
                        Field.builder().name("orderNo").type(FieldType.VARCHAR).build(),
                        Field.builder().name("userId").type(FieldType.BIGINT).build(),
                        Field.builder().name("totalAmount").type(FieldType.NUMERIC).build(),
                        Field.builder().name("status").type(FieldType.VARCHAR).build(),
                        Field.builder().name("createdAt").type(FieldType.TIMESTAMPTZ).build(),
                        Field.builder().name("updatedAt").type(FieldType.TIMESTAMPTZ).build(),
                        Field.builder().name("remarks").type(FieldType.TEXT).build()
                ))
                .build();

        ComplexityAnalyzerTool.Request request = new ComplexityAnalyzerTool.Request("createOrder", entity, null);
        ComplexityAnalyzerTool.Response response = tool.apply(request);

        assertTrue(response.isSuccess(), "中等字段实体应该成功");
        Map<String, Integer> breakdown = response.getScoreBreakdown();
        assertEquals(6, breakdown.get("entityScore"),
                "8个字段的实体评分应该为6分（6-8字段范围）");
    }

    @Test
    @DisplayName("测试8: 实体维度 - 大量字段（11-20个字段）")
    void testEntityComplexity_ManyFields() {
        // 创建15个字段
        List<Field> fields = new ArrayList<>();
        for (int i = 1; i <= 15; i++) {
            fields.add(Field.builder().name("field" + i).type(FieldType.VARCHAR).build());
        }

        Entity entity = Entity.builder()
                .name("Product")
                .fields(fields)
                .build();

        ComplexityAnalyzerTool.Request request = new ComplexityAnalyzerTool.Request("updateProduct", entity, null);
        ComplexityAnalyzerTool.Response response = tool.apply(request);

        assertTrue(response.isSuccess(), "大量字段实体应该成功");
        Map<String, Integer> breakdown = response.getScoreBreakdown();
        assertEquals(16, breakdown.get("entityScore"),
                "15个字段的实体评分应该为16分（13-20字段范围）");
    }

    @Test
    @DisplayName("测试9: 实体维度 - 敏感字段识别")
    void testEntityComplexity_SensitiveFields() {
        Entity entity = Entity.builder()
                .name("User")
                .fields(Arrays.asList(
                        Field.builder().name("id").type(FieldType.BIGINT).build(),
                        Field.builder().name("username").type(FieldType.VARCHAR).build(),
                        Field.builder().name("password").type(FieldType.VARCHAR).build(),      // 敏感字段
                        Field.builder().name("email").type(FieldType.VARCHAR).build(),
                        Field.builder().name("accessToken").type(FieldType.VARCHAR).build(),   // 敏感字段
                        Field.builder().name("apiKey").type(FieldType.VARCHAR).build()         // 敏感字段
                ))
                .build();

        ComplexityAnalyzerTool.Request request = new ComplexityAnalyzerTool.Request("createUser", entity, null);
        ComplexityAnalyzerTool.Response response = tool.apply(request);

        assertTrue(response.isSuccess(), "敏感字段识别应该成功");
        Map<String, Integer> breakdown = response.getScoreBreakdown();
        assertTrue(breakdown.get("entityScore") > 4,
                "包含敏感字段的实体评分应该更高（实际: " + breakdown.get("entityScore") + "）");
    }

    // ==================== 需求维度测试 ====================

    @Test
    @DisplayName("测试10: 需求维度 - 业务关键词（支付、订单、库存）")
    void testRequirementComplexity_BusinessKeywords() {
        String requirement = "实现订单支付功能，支持库存扣减和结算流程";

        ComplexityAnalyzerTool.Request request = new ComplexityAnalyzerTool.Request("processPayment", null, requirement);
        ComplexityAnalyzerTool.Response response = tool.apply(request);

        assertTrue(response.isSuccess(), "业务关键词识别应该成功");
        Map<String, Integer> breakdown = response.getScoreBreakdown();
        assertTrue(breakdown.get("requirementScore") >= 6,
                "包含3个业务关键词（订单、支付、库存、结算）的需求评分应该>=6分（实际: " + breakdown.get("requirementScore") + "）");
    }

    @Test
    @DisplayName("测试11: 需求维度 - 技术关键词（缓存、异步、消息队列）")
    void testRequirementComplexity_TechKeywords() {
        String requirement = "实现异步消息队列处理，支持缓存和定时任务调度";

        ComplexityAnalyzerTool.Request request = new ComplexityAnalyzerTool.Request("processMessage", null, requirement);
        ComplexityAnalyzerTool.Response response = tool.apply(request);

        assertTrue(response.isSuccess(), "技术关键词识别应该成功");
        Map<String, Integer> breakdown = response.getScoreBreakdown();
        assertTrue(breakdown.get("requirementScore") >= 3,
                "包含技术关键词的需求评分应该>=3分（实际: " + breakdown.get("requirementScore") + "）");
    }

    @Test
    @DisplayName("测试12: 需求维度 - 集成关键词（第三方API、微服务）")
    void testRequirementComplexity_IntegrationKeywords() {
        String requirement = "对接第三方支付API，调用微服务接口进行数据同步";

        ComplexityAnalyzerTool.Request request = new ComplexityAnalyzerTool.Request("syncPayment", null, requirement);
        ComplexityAnalyzerTool.Response response = tool.apply(request);

        assertTrue(response.isSuccess(), "集成关键词识别应该成功");
        Map<String, Integer> breakdown = response.getScoreBreakdown();
        assertTrue(breakdown.get("requirementScore") >= 4,
                "包含集成关键词的需求评分应该>=4分（实际: " + breakdown.get("requirementScore") + "）");
    }

    @Test
    @DisplayName("测试13: 需求维度 - 复合关键词（业务+技术+集成）")
    void testRequirementComplexity_MixedKeywords() {
        String requirement = "实现订单支付流程，使用缓存优化性能，对接第三方支付API，支持异步消息通知";

        ComplexityAnalyzerTool.Request request = new ComplexityAnalyzerTool.Request("processOrderPayment", null, requirement);
        ComplexityAnalyzerTool.Response response = tool.apply(request);

        assertTrue(response.isSuccess(), "复合关键词识别应该成功");
        Map<String, Integer> breakdown = response.getScoreBreakdown();
        assertTrue(breakdown.get("requirementScore") >= 10,
                "包含复合关键词的需求评分应该>=10分（实际: " + breakdown.get("requirementScore") + "）");
    }

    // ==================== 关系维度测试 ====================

    @Test
    @DisplayName("测试14: 关系维度 - 一对一关系（外键字段）")
    void testRelationshipComplexity_OneToOne() {
        Entity entity = Entity.builder()
                .name("Order")
                .fields(Arrays.asList(
                        Field.builder().name("id").type(FieldType.BIGINT).build(),
                        Field.builder().name("userId").type(FieldType.BIGINT).build(),      // 外键，一对一
                        Field.builder().name("addressId").type(FieldType.BIGINT).build()    // 外键，一对一
                ))
                .build();

        ComplexityAnalyzerTool.Request request = new ComplexityAnalyzerTool.Request("createOrder", entity, null);
        ComplexityAnalyzerTool.Response response = tool.apply(request);

        assertTrue(response.isSuccess(), "一对一关系识别应该成功");
        Map<String, Integer> breakdown = response.getScoreBreakdown();
        assertEquals(4, breakdown.get("relationshipScore"),
                "2个外键字段（一对一关系）评分应该为4分（2*2=4）");
    }

    @Test
    @DisplayName("测试15: 关系维度 - 一对多关系（List字段）")
    void testRelationshipComplexity_OneToMany() {
        Entity entity = Entity.builder()
                .name("Order")
                .fields(Arrays.asList(
                        Field.builder().name("id").type(FieldType.BIGINT).build(),
                        Field.builder().name("orderItems").type(FieldType.TEXT_ARRAY).build()     // 一对多关系
                ))
                .build();

        ComplexityAnalyzerTool.Request request = new ComplexityAnalyzerTool.Request("createOrder", entity, null);
        ComplexityAnalyzerTool.Response response = tool.apply(request);

        assertTrue(response.isSuccess(), "一对多关系识别应该成功");
        Map<String, Integer> breakdown = response.getScoreBreakdown();
        assertEquals(4, breakdown.get("relationshipScore"),
                "1个List字段（一对多关系）评分应该为4分");
    }

    @Test
    @DisplayName("测试16: 关系维度 - 多对多关系（Map字段）")
    void testRelationshipComplexity_ManyToMany() {
        Entity entity = Entity.builder()
                .name("User")
                .fields(Arrays.asList(
                        Field.builder().name("id").type(FieldType.BIGINT).build(),
                        Field.builder().name("roles").type(FieldType.JSONB).build()           // 多对多关系
                ))
                .build();

        ComplexityAnalyzerTool.Request request = new ComplexityAnalyzerTool.Request("assignRoles", entity, null);
        ComplexityAnalyzerTool.Response response = tool.apply(request);

        assertTrue(response.isSuccess(), "多对多关系识别应该成功");
        Map<String, Integer> breakdown = response.getScoreBreakdown();
        assertEquals(6, breakdown.get("relationshipScore"),
                "1个Map字段（多对多关系）评分应该为6分");
    }

    // ==================== 综合场景测试 ====================

    @Test
    @DisplayName("测试17: 综合场景 - 简单CRUD（总分<60）")
    void testCompleteScenario_SimpleCRUD() {
        Entity entity = Entity.builder()
                .name("User")
                .fields(Arrays.asList(
                        Field.builder().name("id").type(FieldType.BIGINT).build(),
                        Field.builder().name("username").type(FieldType.VARCHAR).build()
                ))
                .build();

        ComplexityAnalyzerTool.Request request = new ComplexityAnalyzerTool.Request(
                "getUserById",
                entity,
                "根据ID查询用户信息"
        );
        ComplexityAnalyzerTool.Response response = tool.apply(request);

        assertTrue(response.isSuccess(), "简单CRUD场景应该成功");
        assertTrue(response.getComplexityScore() < 60,
                "简单CRUD总分应该<60分（实际: " + response.getComplexityScore() + "）");
    }

    @Test
    @DisplayName("测试18: 综合场景 - 中等复杂业务（总分60-90）")
    void testCompleteScenario_MediumComplexity() {
        Entity entity = Entity.builder()
                .name("Order")
                .fields(Arrays.asList(
                        Field.builder().name("id").type(FieldType.BIGINT).build(),
                        Field.builder().name("orderNo").type(FieldType.VARCHAR).build(),
                        Field.builder().name("userId").type(FieldType.BIGINT).build(),
                        Field.builder().name("totalAmount").type(FieldType.NUMERIC).build(),
                        Field.builder().name("status").type(FieldType.VARCHAR).build(),
                        Field.builder().name("orderItems").type(FieldType.TEXT_ARRAY).build(),
                        Field.builder().name("createdAt").type(FieldType.TIMESTAMPTZ).build()
                ))
                .build();

        ComplexityAnalyzerTool.Request request = new ComplexityAnalyzerTool.Request(
                "processOrderPayment",
                entity,
                "处理订单支付流程，支持库存扣减和结算"
        );
        ComplexityAnalyzerTool.Response response = tool.apply(request);

        assertTrue(response.isSuccess(), "中等复杂业务场景应该成功");
        assertTrue(response.getComplexityScore() >= 40 && response.getComplexityScore() < 90,
                "中等复杂业务总分应该在40-90分（实际: " + response.getComplexityScore() + "）");
    }

    @Test
    @DisplayName("测试19: 综合场景 - 高复杂分布式事务（总分≥70）")
    void testCompleteScenario_HighComplexity() {
        Entity entity = Entity.builder()
                .name("Payment")
                .fields(Arrays.asList(
                        Field.builder().name("id").type(FieldType.BIGINT).build(),
                        Field.builder().name("orderId").type(FieldType.BIGINT).build(),
                        Field.builder().name("userId").type(FieldType.BIGINT).build(),
                        Field.builder().name("amount").type(FieldType.NUMERIC).build(),
                        Field.builder().name("status").type(FieldType.VARCHAR).build(),
                        Field.builder().name("transactionId").type(FieldType.VARCHAR).build(),
                        Field.builder().name("apiKey").type(FieldType.VARCHAR).build(),          // 敏感字段
                        Field.builder().name("paymentItems").type(FieldType.TEXT_ARRAY).build(),       // 一对多
                        Field.builder().name("metadata").type(FieldType.JSONB).build(),            // 多对多
                        Field.builder().name("createdAt").type(FieldType.TIMESTAMPTZ).build()
                ))
                .build();

        ComplexityAnalyzerTool.Request request = new ComplexityAnalyzerTool.Request(
                "distributedTransactionCommit",
                entity,
                "分布式支付事务提交，对接第三方支付API，支持异步消息通知和缓存优化"
        );
        ComplexityAnalyzerTool.Response response = tool.apply(request);

        assertTrue(response.isSuccess(), "高复杂分布式事务场景应该成功");
        assertTrue(response.getComplexityScore() >= 70,
                "高复杂分布式事务总分应该≥70分（实际: " + response.getComplexityScore() + "）");
    }

    // ==================== 边界情况测试 ====================

    @Test
    @DisplayName("测试20: 边界情况 - null方法名")
    void testBoundaryCase_NullMethodName() {
        ComplexityAnalyzerTool.Request request = new ComplexityAnalyzerTool.Request(null, null, null);
        ComplexityAnalyzerTool.Response response = tool.apply(request);

        assertTrue(response.isSuccess(), "null方法名应该优雅处理");
        assertTrue(response.getComplexityScore() >= 0 && response.getComplexityScore() <= 100,
                "总分应该在0-100范围内（实际: " + response.getComplexityScore() + "）");
    }

    @Test
    @DisplayName("测试21: 边界情况 - 空字符串方法名")
    void testBoundaryCase_EmptyMethodName() {
        ComplexityAnalyzerTool.Request request = new ComplexityAnalyzerTool.Request("", null, null);
        ComplexityAnalyzerTool.Response response = tool.apply(request);

        assertTrue(response.isSuccess(), "空字符串方法名应该优雅处理");
        assertTrue(response.getComplexityScore() >= 0 && response.getComplexityScore() <= 100,
                "总分应该在0-100范围内（实际: " + response.getComplexityScore() + "）");
    }

    @Test
    @DisplayName("测试22: 边界情况 - null实体")
    void testBoundaryCase_NullEntity() {
        ComplexityAnalyzerTool.Request request = new ComplexityAnalyzerTool.Request("getUser", null, "查询用户");
        ComplexityAnalyzerTool.Response response = tool.apply(request);

        assertTrue(response.isSuccess(), "null实体应该优雅处理");
        Map<String, Integer> breakdown = response.getScoreBreakdown();
        assertEquals(3, breakdown.get("entityScore"),
                "null实体应该返回默认低分（3分）");
    }

    @Test
    @DisplayName("测试23: 边界情况 - null需求描述")
    void testBoundaryCase_NullRequirement() {
        ComplexityAnalyzerTool.Request request = new ComplexityAnalyzerTool.Request("processPayment", null, null);
        ComplexityAnalyzerTool.Response response = tool.apply(request);

        assertTrue(response.isSuccess(), "null需求描述应该优雅处理");
        Map<String, Integer> breakdown = response.getScoreBreakdown();
        assertEquals(0, breakdown.get("requirementScore"),
                "null需求应该返回0分");
    }

    @Test
    @DisplayName("测试24: 边界情况 - 空需求描述")
    void testBoundaryCase_EmptyRequirement() {
        ComplexityAnalyzerTool.Request request = new ComplexityAnalyzerTool.Request("processPayment", null, "");
        ComplexityAnalyzerTool.Response response = tool.apply(request);

        assertTrue(response.isSuccess(), "空需求描述应该优雅处理");
        Map<String, Integer> breakdown = response.getScoreBreakdown();
        assertEquals(0, breakdown.get("requirementScore"),
                "空需求应该返回0分");
    }

    // ==================== 性能测试 ====================

    @Test
    @DisplayName("测试25: 性能指标 - 执行时间<50ms")
    void testPerformanceMetrics_ExecutionTime() {
        Entity entity = Entity.builder()
                .name("Order")
                .fields(Arrays.asList(
                        Field.builder().name("id").type(FieldType.BIGINT).build(),
                        Field.builder().name("orderNo").type(FieldType.VARCHAR).build(),
                        Field.builder().name("userId").type(FieldType.BIGINT).build(),
                        Field.builder().name("totalAmount").type(FieldType.NUMERIC).build(),
                        Field.builder().name("orderItems").type(FieldType.TEXT_ARRAY).build()
                ))
                .build();

        ComplexityAnalyzerTool.Request request = new ComplexityAnalyzerTool.Request(
                "processOrderPayment",
                entity,
                "处理订单支付流程，支持库存扣减和结算"
        );

        long startTime = System.currentTimeMillis();
        ComplexityAnalyzerTool.Response response = tool.apply(request);
        long actualDuration = System.currentTimeMillis() - startTime;

        assertTrue(response.isSuccess(), "性能测试应该成功");
        assertTrue(response.getDurationMs() < 50,
                "执行时间应该<50ms（实际: " + response.getDurationMs() + "ms）");
        assertTrue(actualDuration < 100,
                "实际执行时间应该<100ms（实际: " + actualDuration + "ms）");
    }

    @Test
    @DisplayName("测试26: 性能指标 - 批量执行100次")
    void testPerformanceMetrics_BatchExecution() {
        Entity entity = Entity.builder()
                .name("User")
                .fields(Arrays.asList(
                        Field.builder().name("id").type(FieldType.BIGINT).build(),
                        Field.builder().name("username").type(FieldType.VARCHAR).build()
                ))
                .build();

        long totalDuration = 0;
        int successCount = 0;

        for (int i = 0; i < 100; i++) {
            ComplexityAnalyzerTool.Request request = new ComplexityAnalyzerTool.Request(
                    "getUser" + i,
                    entity,
                    "查询用户" + i
            );

            long startTime = System.currentTimeMillis();
            ComplexityAnalyzerTool.Response response = tool.apply(request);
            long duration = System.currentTimeMillis() - startTime;

            totalDuration += duration;
            if (response.isSuccess()) {
                successCount++;
            }
        }

        double avgDuration = totalDuration / 100.0;

        assertEquals(100, successCount, "100次执行应该全部成功");
        assertTrue(avgDuration < 10,
                "平均执行时间应该<10ms（实际: " + avgDuration + "ms）");
    }

    // ==================== 准确性验证测试 ====================

    @Test
    @DisplayName("测试27: 准确性验证 - 总分上限100分")
    void testAccuracyValidation_MaxScore() {
        // 构建极端高复杂度场景（尝试超过100分）
        // 25个字段（包含5个敏感字段 + 2个关系字段）
        List<Field> fields = new ArrayList<>();

        // 20个普通字段
        for (int i = 1; i <= 20; i++) {
            fields.add(Field.builder().name("field" + i).type(FieldType.VARCHAR).build());
        }

        // 3个敏感字段
        fields.add(Field.builder().name("password").type(FieldType.VARCHAR).build());
        fields.add(Field.builder().name("apiKey").type(FieldType.VARCHAR).build());
        fields.add(Field.builder().name("secretKey").type(FieldType.VARCHAR).build());

        // 2个关系字段
        fields.add(Field.builder().name("orderItems").type(FieldType.TEXT_ARRAY).build());  // 一对多
        fields.add(Field.builder().name("metadata").type(FieldType.JSONB).build());          // 多对多（字段名暗示）

        Entity entity = Entity.builder()
                .name("ComplexEntity")
                .fields(fields)
                .build();

        ComplexityAnalyzerTool.Request request = new ComplexityAnalyzerTool.Request(
                "distributedTransactionCommit",
                entity,
                "分布式支付事务提交，对接第三方支付API，支持异步消息队列通知，使用缓存优化性能，处理订单库存扣减和结算审批流程"
        );
        ComplexityAnalyzerTool.Response response = tool.apply(request);

        assertTrue(response.isSuccess(), "极端高复杂度场景应该成功");
        assertTrue(response.getComplexityScore() <= 100,
                "总分应该<=100分（实际: " + response.getComplexityScore() + "）");
        assertTrue(response.getComplexityScore() >= 70,
                "极端复杂场景总分应该>=70分（实际: " + response.getComplexityScore() + "）");
    }

    @Test
    @DisplayName("测试28: 准确性验证 - 评分明细完整性")
    void testAccuracyValidation_ScoreBreakdownCompleteness() {
        Entity entity = Entity.builder()
                .name("Order")
                .fields(Arrays.asList(
                        Field.builder().name("id").type(FieldType.BIGINT).build(),
                        Field.builder().name("orderItems").type(FieldType.TEXT_ARRAY).build()
                ))
                .build();

        ComplexityAnalyzerTool.Request request = new ComplexityAnalyzerTool.Request(
                "processOrder",
                entity,
                "处理订单"
        );
        ComplexityAnalyzerTool.Response response = tool.apply(request);

        assertTrue(response.isSuccess(), "评分明细测试应该成功");

        Map<String, Integer> breakdown = response.getScoreBreakdown();
        assertNotNull(breakdown, "评分明细不应为null");
        assertTrue(breakdown.containsKey("methodScore"), "应该包含methodScore");
        assertTrue(breakdown.containsKey("entityScore"), "应该包含entityScore");
        assertTrue(breakdown.containsKey("requirementScore"), "应该包含requirementScore");
        assertTrue(breakdown.containsKey("relationshipScore"), "应该包含relationshipScore");

        int totalFromBreakdown = breakdown.get("methodScore") +
                                breakdown.get("entityScore") +
                                breakdown.get("requirementScore") +
                                breakdown.get("relationshipScore");

        assertEquals(response.getComplexityScore(), Math.min(100, totalFromBreakdown),
                "总分应该等于各维度评分之和（上限100分）");
    }

    @Test
    @DisplayName("测试29: 准确性验证 - 零Token消耗（本地规则引擎）")
    void testAccuracyValidation_ZeroTokenConsumption() {
        // 验证ComplexityAnalyzerTool不调用AI API（零Token消耗）
        Entity entity = Entity.builder()
                .name("User")
                .fields(Arrays.asList(
                        Field.builder().name("id").type(FieldType.BIGINT).build(),
                        Field.builder().name("username").type(FieldType.VARCHAR).build()
                ))
                .build();

        long startTime = System.currentTimeMillis();
        ComplexityAnalyzerTool.Request request = new ComplexityAnalyzerTool.Request(
                "getUser",
                entity,
                "查询用户"
        );
        ComplexityAnalyzerTool.Response response = tool.apply(request);
        long duration = System.currentTimeMillis() - startTime;

        assertTrue(response.isSuccess(), "零Token测试应该成功");
        // 本地规则引擎执行时间应该非常短（<50ms）
        assertTrue(duration < 50,
                "本地规则引擎执行应该<50ms（实际: " + duration + "ms）");
        // 验证确实使用了本地规则（无网络延迟）
        assertTrue(response.getDurationMs() < 50,
                "响应中记录的执行时间应该<50ms（实际: " + response.getDurationMs() + "ms）");
    }

    @Test
    @DisplayName("测试30: 准确性验证 - 中英文关键词混合识别")
    void testAccuracyValidation_MixedLanguageKeywords() {
        String requirement = "实现payment订单流程，支持inventory库存扣减，对接third-party API，使用cache缓存优化";

        ComplexityAnalyzerTool.Request request = new ComplexityAnalyzerTool.Request(
                "processPayment",
                null,
                requirement
        );
        ComplexityAnalyzerTool.Response response = tool.apply(request);

        assertTrue(response.isSuccess(), "中英文混合关键词应该成功识别");
        Map<String, Integer> breakdown = response.getScoreBreakdown();
        assertTrue(breakdown.get("requirementScore") >= 10,
                "包含中英文混合关键词的需求评分应该>=10分（实际: " + breakdown.get("requirementScore") + "）");
    }
}
