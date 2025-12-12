package com.ingenio.backend.codegen.ai.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ingenio.backend.codegen.ai.tool.AIOptimizerTool.*;
import com.ingenio.backend.codegen.schema.Entity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClient.CallResponseSpec;
import org.springframework.ai.chat.client.ChatClient.ChatClientRequestSpec;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * AIOptimizerTool 单元测试
 *
 * <p>测试覆盖范围：</p>
 * <ul>
 *     <li>完美代码（无优化点）</li>
 *     <li>性能问题代码（N+1查询、未使用索引、无缓存）</li>
 *     <li>安全问题代码（SQL注入、XSS、敏感数据泄露）</li>
 *     <li>可读性问题代码（命名不规范、缺少注释、方法过长）</li>
 *     <li>最佳实践问题（注解使用、设计模式）</li>
 *     <li>综合优化场景</li>
 *     <li>边界情况（空代码、超长代码、JSON解析失败）</li>
 *     <li>错误处理和fallback机制</li>
 * </ul>
 *
 * @author Justin
 * @since 2025-11-19
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AIOptimizerTool 单元测试")
class AIOptimizerToolTest {

    @Mock
    private ChatClient.Builder chatClientBuilder;

    @Mock
    private ChatClient chatClient;

    @Mock
    private ChatClientRequestSpec requestSpec;

    @Mock
    private CallResponseSpec callResponseSpec;

    private AIOptimizerTool aiOptimizerTool;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        // Mock ChatClient chain
        when(chatClientBuilder.build()).thenReturn(chatClient);
        when(chatClient.prompt()).thenReturn(requestSpec);
        when(requestSpec.user(anyString())).thenReturn(requestSpec);
        when(requestSpec.call()).thenReturn(callResponseSpec);

        aiOptimizerTool = new AIOptimizerTool(chatClientBuilder);
        objectMapper = new ObjectMapper();
    }

    // ==================== 测试用例1：完美代码（无优化点） ====================

    @Test
    @DisplayName("测试1：完美代码 - 无优化建议")
    void testPerfectCode_NoOptimizations() {
        // 准备测试数据
        String perfectCode = """
                package com.ingenio.backend.service;

                import com.ingenio.backend.repository.UserRepository;
                import com.ingenio.backend.model.User;
                import org.springframework.stereotype.Service;
                import org.springframework.cache.annotation.Cacheable;
                import org.springframework.transaction.annotation.Transactional;

                @Service
                public class UserService {

                    private final UserRepository userRepository;

                    public UserService(UserRepository userRepository) {
                        this.userRepository = userRepository;
                    }

                    @Cacheable("users")
                    public User findById(Long id) {
                        return userRepository.findById(id)
                            .orElseThrow(() -> new NotFoundException("User not found: " + id));
                    }

                    @Transactional
                    public User createUser(User user) {
                        validateUser(user);
                        return userRepository.save(user);
                    }

                    private void validateUser(User user) {
                        if (user == null) {
                            throw new IllegalArgumentException("User cannot be null");
                        }
                        if (user.getEmail() == null || !user.getEmail().matches("^[A-Za-z0-9+_.-]+@(.+)$")) {
                            throw new IllegalArgumentException("Invalid email format");
                        }
                    }
                }
                """;

        Request request = Request.builder()
                .generatedCode(perfectCode)
                .methodName("UserService")
                .scope(OptimizationScope.ALL)
                .build();

        // Mock AI 返回无优化建议
        String aiResponse = """
                {
                  "hasOptimizations": false,
                  "optimizations": [],
                  "summary": "代码质量优秀，已遵循Spring Boot最佳实践，包括依赖注入、缓存注解、事务管理、输入验证等。无需优化。"
                }
                """;

        when(callResponseSpec.content()).thenReturn(aiResponse);

        // 执行测试
        Response response = aiOptimizerTool.apply(request);

        // 验证结果
        assertNotNull(response);
        assertFalse(response.getHasOptimizations(), "完美代码不应有优化建议");
        assertEquals(0, response.getOptimizationCount());
        assertTrue(response.getSummary().contains("无需优化") || response.getSummary().contains("质量优秀"));
        assertNotNull(response.getDurationMs());
        assertTrue(response.getDurationMs() >= 0);
        assertNull(response.getErrorMessage());
    }

    // ==================== 测试用例2：性能问题 - N+1查询 ====================

    @Test
    @DisplayName("测试2：性能问题 - N+1查询漏洞")
    void testPerformanceIssue_NPlusOneQuery() {
        String badCode = """
                public List<UserDTO> getAllUsersWithOrders() {
                    List<User> users = userRepository.findAll();
                    return users.stream()
                        .map(user -> {
                            List<Order> orders = orderRepository.findByUserId(user.getId());
                            return new UserDTO(user, orders);
                        })
                        .collect(Collectors.toList());
                }
                """;

        Request request = Request.builder()
                .generatedCode(badCode)
                .scope(OptimizationScope.PERFORMANCE)
                .build();

        // Mock AI 返回N+1查询优化建议
        String aiResponse = """
                {
                  "hasOptimizations": true,
                  "optimizations": [
                    {
                      "category": "PERFORMANCE",
                      "severity": "HIGH",
                      "location": "getAllUsersWithOrders方法",
                      "issue": "存在N+1查询问题：对每个用户单独查询订单，导致1+N次数据库查询",
                      "suggestion": "使用JOIN FETCH一次性查询用户和订单，或使用EntityGraph注解",
                      "example": "使用JOIN FETCH优化查询"
                    }
                  ],
                  "summary": "检测到严重的N+1查询性能问题，建议使用JOIN FETCH或EntityGraph优化"
                }
                """;

        when(callResponseSpec.content()).thenReturn(aiResponse);

        // 执行测试
        Response response = aiOptimizerTool.apply(request);

        // 验证结果
        assertNotNull(response);
        assertTrue(response.getHasOptimizations());
        assertEquals(1, response.getOptimizationCount());

        Optimization opt = response.getOptimizations().get(0);
        assertEquals(OptimizationCategory.PERFORMANCE, opt.getCategory());
        assertEquals(Severity.HIGH, opt.getSeverity());
        assertTrue(opt.getIssue().contains("N+1") || opt.getIssue().contains("查询"));
        assertTrue(opt.getSuggestion().contains("JOIN") || opt.getSuggestion().contains("EntityGraph"));
        assertNotNull(opt.getExample());
    }

    // ==================== 测试用例3：性能问题 - 未使用缓存 ====================

    @Test
    @DisplayName("测试3：性能问题 - 未使用缓存导致重复查询")
    void testPerformanceIssue_MissingCache() {
        String badCode = """
                public User getUserById(Long id) {
                    return userRepository.findById(id)
                        .orElseThrow(() -> new NotFoundException("User not found"));
                }
                """;

        Request request = Request.builder()
                .generatedCode(badCode)
                .scope(OptimizationScope.PERFORMANCE)
                .build();

        String aiResponse = """
                {
                  "hasOptimizations": true,
                  "optimizations": [
                    {
                      "category": "PERFORMANCE",
                      "severity": "MEDIUM",
                      "location": "getUserById方法",
                      "issue": "频繁查询的用户数据未使用缓存，每次请求都访问数据库",
                      "suggestion": "添加@Cacheable注解启用Spring Cache，减少数据库访问",
                      "example": "添加@Cacheable注解启用Spring缓存"
                    }
                  ],
                  "summary": "建议为频繁查询的数据添加缓存层"
                }
                """;

        when(callResponseSpec.content()).thenReturn(aiResponse);

        Response response = aiOptimizerTool.apply(request);

        assertNotNull(response);
        assertTrue(response.getHasOptimizations());
        assertEquals(1, response.getOptimizationCount());

        Optimization opt = response.getOptimizations().get(0);
        assertEquals(OptimizationCategory.PERFORMANCE, opt.getCategory());
        assertEquals(Severity.MEDIUM, opt.getSeverity());
        assertTrue(opt.getSuggestion().contains("缓存") || opt.getSuggestion().contains("Cacheable"));
    }

    // ==================== 测试用例4：安全问题 - SQL注入风险 ====================

    @Test
    @DisplayName("测试4：安全问题 - SQL注入漏洞")
    void testSecurityIssue_SQLInjection() {
        String badCode = """
                public List<User> searchUsers(String keyword) {
                    String sql = "SELECT * FROM users WHERE name LIKE '%" + keyword + "%'";
                    return jdbcTemplate.query(sql, userRowMapper);
                }
                """;

        Request request = Request.builder()
                .generatedCode(badCode)
                .scope(OptimizationScope.SECURITY)
                .build();

        String aiResponse = """
                {
                  "hasOptimizations": true,
                  "optimizations": [
                    {
                      "category": "SECURITY",
                      "severity": "HIGH",
                      "location": "searchUsers方法",
                      "issue": "存在严重的SQL注入漏洞：直接拼接用户输入到SQL语句中",
                      "suggestion": "使用参数化查询或PreparedStatement防止SQL注入",
                      "example": "String sql = \\"SELECT * FROM users WHERE name LIKE ?\\";\\njdbcTemplate.query(sql, userRowMapper, \\"%%\\" + keyword + \\"%%\\");"
                    }
                  ],
                  "summary": "检测到高危SQL注入漏洞，必须立即修复"
                }
                """;

        when(callResponseSpec.content()).thenReturn(aiResponse);

        Response response = aiOptimizerTool.apply(request);

        assertNotNull(response);
        assertTrue(response.getHasOptimizations());
        assertEquals(1, response.getOptimizationCount());

        Optimization opt = response.getOptimizations().get(0);
        assertEquals(OptimizationCategory.SECURITY, opt.getCategory());
        assertEquals(Severity.HIGH, opt.getSeverity());
        assertTrue(opt.getIssue().contains("SQL注入") || opt.getIssue().contains("注入"));
        assertTrue(opt.getSuggestion().contains("参数化") || opt.getSuggestion().contains("PreparedStatement"));
    }

    // ==================== 测试用例5：安全问题 - 敏感数据泄露 ====================

    @Test
    @DisplayName("测试5：安全问题 - 敏感信息日志泄露")
    void testSecurityIssue_SensitiveDataLogging() {
        String badCode = """
                public void login(String username, String password) {
                    log.info("User login attempt: username={}, password={}", username, password);
                    // ... authentication logic
                }
                """;

        Request request = Request.builder()
                .generatedCode(badCode)
                .scope(OptimizationScope.SECURITY)
                .build();

        String aiResponse = """
                {
                  "hasOptimizations": true,
                  "optimizations": [
                    {
                      "category": "SECURITY",
                      "severity": "HIGH",
                      "location": "login方法",
                      "issue": "日志中记录明文密码，存在敏感信息泄露风险",
                      "suggestion": "删除密码日志记录，仅记录用户名和登录结果",
                      "example": "log.info(\\"User login attempt: username={}\\", username);"
                    }
                  ],
                  "summary": "检测到敏感信息泄露风险，禁止在日志中记录密码等敏感数据"
                }
                """;

        when(callResponseSpec.content()).thenReturn(aiResponse);

        Response response = aiOptimizerTool.apply(request);

        assertNotNull(response);
        assertTrue(response.getHasOptimizations());

        Optimization opt = response.getOptimizations().get(0);
        assertEquals(OptimizationCategory.SECURITY, opt.getCategory());
        assertEquals(Severity.HIGH, opt.getSeverity());
        assertTrue(opt.getIssue().contains("敏感") || opt.getIssue().contains("密码"));
    }

    // ==================== 测试用例6：可读性问题 - 命名不规范 ====================

    @Test
    @DisplayName("测试6：可读性问题 - 变量命名不规范")
    void testReadabilityIssue_PoorNaming() {
        String badCode = """
                public void process(User u) {
                    String s = u.getName();
                    int a = u.getAge();
                    List<Order> l = u.getOrders();
                    // ... business logic
                }
                """;

        Request request = Request.builder()
                .generatedCode(badCode)
                .scope(OptimizationScope.READABILITY)
                .build();

        String aiResponse = """
                {
                  "hasOptimizations": true,
                  "optimizations": [
                    {
                      "category": "READABILITY",
                      "severity": "MEDIUM",
                      "location": "process方法",
                      "issue": "变量命名过于简短且无意义（s, a, l），降低代码可读性",
                      "suggestion": "使用有意义的变量名描述业务含义",
                      "example": "String userName = user.getName();\\nint userAge = user.getAge();\\nList<Order> userOrders = user.getOrders();"
                    }
                  ],
                  "summary": "变量命名需要改进，使用有业务含义的名称"
                }
                """;

        when(callResponseSpec.content()).thenReturn(aiResponse);

        Response response = aiOptimizerTool.apply(request);

        assertNotNull(response);
        assertTrue(response.getHasOptimizations());

        Optimization opt = response.getOptimizations().get(0);
        assertEquals(OptimizationCategory.READABILITY, opt.getCategory());
        assertEquals(Severity.MEDIUM, opt.getSeverity());
        assertTrue(opt.getIssue().contains("命名") || opt.getIssue().contains("变量"));
    }

    // ==================== 测试用例7：可读性问题 - 方法过长 ====================

    @Test
    @DisplayName("测试7：可读性问题 - 方法过长违反单一职责")
    void testReadabilityIssue_LongMethod() {
        String badCode = """
                public void processOrder(Order order) {
                    // 1. 验证订单 (10行代码)
                    // 2. 计算价格 (15行代码)
                    // 3. 扣减库存 (10行代码)
                    // 4. 创建支付 (12行代码)
                    // 5. 发送通知 (8行代码)
                    // 总计55行代码在一个方法中
                }
                """;

        Request request = Request.builder()
                .generatedCode(badCode)
                .scope(OptimizationScope.READABILITY)
                .build();

        String aiResponse = """
                {
                  "hasOptimizations": true,
                  "optimizations": [
                    {
                      "category": "READABILITY",
                      "severity": "MEDIUM",
                      "location": "processOrder方法",
                      "issue": "方法过长（55行），承担多个职责，违反单一职责原则",
                      "suggestion": "拆分为多个私有方法：validateOrder(), calculatePrice(), reduceStock(), createPayment(), sendNotification()",
                      "example": "public void processOrder(Order order) {\\n  validateOrder(order);\\n  calculatePrice(order);\\n  reduceStock(order);\\n  createPayment(order);\\n  sendNotification(order);\\n}"
                    }
                  ],
                  "summary": "建议拆分长方法，提升可读性和可维护性"
                }
                """;

        when(callResponseSpec.content()).thenReturn(aiResponse);

        Response response = aiOptimizerTool.apply(request);

        assertNotNull(response);
        assertTrue(response.getHasOptimizations());

        Optimization opt = response.getOptimizations().get(0);
        assertEquals(OptimizationCategory.READABILITY, opt.getCategory());
        assertTrue(opt.getIssue().contains("方法") || opt.getIssue().contains("职责"));
    }

    // ==================== 测试用例8：最佳实践 - Spring注解使用 ====================

    @Test
    @DisplayName("测试8：最佳实践 - 未使用Spring依赖注入")
    void testBestPracticeIssue_MissingDependencyInjection() {
        String badCode = """
                @Service
                public class OrderService {
                    private UserRepository userRepository = new UserRepositoryImpl();

                    public void createOrder(Order order) {
                        // ...
                    }
                }
                """;

        Request request = Request.builder()
                .generatedCode(badCode)
                .scope(OptimizationScope.BEST_PRACTICE)
                .build();

        String aiResponse = """
                {
                  "hasOptimizations": true,
                  "optimizations": [
                    {
                      "category": "BEST_PRACTICE",
                      "severity": "MEDIUM",
                      "location": "OrderService类",
                      "issue": "使用new关键字手动创建依赖对象，未使用Spring依赖注入",
                      "suggestion": "使用构造器注入或@Autowired注解实现依赖注入",
                      "example": "private final UserRepository userRepository;\\n\\npublic OrderService(UserRepository userRepository) {\\n  this.userRepository = userRepository;\\n}"
                    }
                  ],
                  "summary": "建议使用Spring依赖注入管理Bean依赖关系"
                }
                """;

        when(callResponseSpec.content()).thenReturn(aiResponse);

        Response response = aiOptimizerTool.apply(request);

        assertNotNull(response);
        assertTrue(response.getHasOptimizations());

        Optimization opt = response.getOptimizations().get(0);
        assertEquals(OptimizationCategory.BEST_PRACTICE, opt.getCategory());
        assertTrue(opt.getSuggestion().contains("依赖注入") || opt.getSuggestion().contains("注入"));
    }

    // ==================== 测试用例9：综合优化场景 ====================

    @Test
    @DisplayName("测试9：综合优化 - 多维度问题")
    void testComprehensiveOptimization_MultipleIssues() {
        String badCode = """
                public class UserService {
                    public List<User> getAll(String kw) {
                        String sql = "SELECT * FROM users WHERE name LIKE '%" + kw + "%'";
                        List<User> l = jdbcTemplate.query(sql, userRowMapper);
                        for (User u : l) {
                            List<Order> o = orderRepo.findByUserId(u.getId());
                            u.setOrders(o);
                        }
                        return l;
                    }
                }
                """;

        Request request = Request.builder()
                .generatedCode(badCode)
                .scope(OptimizationScope.ALL)
                .build();

        String aiResponse = """
                {
                  "hasOptimizations": true,
                  "optimizations": [
                    {
                      "category": "SECURITY",
                      "severity": "HIGH",
                      "location": "getAll方法",
                      "issue": "SQL注入漏洞",
                      "suggestion": "使用参数化查询",
                      "example": "jdbcTemplate.query(\\"SELECT * FROM users WHERE name LIKE ?\\", userRowMapper, \\"%%\\" + kw + \\"%%\\");"
                    },
                    {
                      "category": "PERFORMANCE",
                      "severity": "HIGH",
                      "location": "getAll方法",
                      "issue": "N+1查询问题",
                      "suggestion": "使用JOIN查询",
                      "example": "使用@EntityGraph或手动JOIN查询"
                    },
                    {
                      "category": "READABILITY",
                      "severity": "MEDIUM",
                      "location": "getAll方法",
                      "issue": "变量命名不规范（l, u, o, kw）",
                      "suggestion": "使用有意义的变量名",
                      "example": "List<User> users = ...; for (User user : users) { ... }"
                    }
                  ],
                  "summary": "检测到3个问题：1个安全漏洞（HIGH），1个性能问题（HIGH），1个可读性问题（MEDIUM）"
                }
                """;

        when(callResponseSpec.content()).thenReturn(aiResponse);

        Response response = aiOptimizerTool.apply(request);

        assertNotNull(response);
        assertTrue(response.getHasOptimizations());
        assertEquals(3, response.getOptimizationCount());

        // 验证包含不同类型的优化建议
        List<Optimization> optimizations = response.getOptimizations();
        boolean hasSecurity = optimizations.stream().anyMatch(o -> o.getCategory() == OptimizationCategory.SECURITY);
        boolean hasPerformance = optimizations.stream().anyMatch(o -> o.getCategory() == OptimizationCategory.PERFORMANCE);
        boolean hasReadability = optimizations.stream().anyMatch(o -> o.getCategory() == OptimizationCategory.READABILITY);

        assertTrue(hasSecurity, "应包含安全优化建议");
        assertTrue(hasPerformance, "应包含性能优化建议");
        assertTrue(hasReadability, "应包含可读性优化建议");
    }

    // ==================== 测试用例10：边界情况 - Markdown代码块解析 ====================

    @Test
    @DisplayName("测试10：边界情况 - AI返回Markdown代码块格式")
    void testEdgeCase_MarkdownCodeBlock() {
        String code = "public void test() {}";

        Request request = Request.builder()
                .generatedCode(code)
                .scope(OptimizationScope.ALL)
                .build();

        // AI返回带markdown代码块的响应
        String aiResponse = """
                ```json
                {
                  "hasOptimizations": false,
                  "optimizations": [],
                  "summary": "代码简洁"
                }
                ```
                """;

        when(callResponseSpec.content()).thenReturn(aiResponse);

        Response response = aiOptimizerTool.apply(request);

        // 应该成功解析markdown代码块中的JSON
        assertNotNull(response);
        assertFalse(response.getHasOptimizations());
        assertNull(response.getErrorMessage());
    }

    // ==================== 测试用例11：边界情况 - 空代码输入 ====================

    @Test
    @DisplayName("测试11：边界情况 - 空代码输入")
    void testEdgeCase_EmptyCode() {
        Request request = Request.builder()
                .generatedCode("")
                .scope(OptimizationScope.ALL)
                .build();

        String aiResponse = """
                {
                  "hasOptimizations": false,
                  "optimizations": [],
                  "summary": "代码为空，无法分析"
                }
                """;

        when(callResponseSpec.content()).thenReturn(aiResponse);

        Response response = aiOptimizerTool.apply(request);

        assertNotNull(response);
        assertFalse(response.getHasOptimizations());
        assertEquals(0, response.getOptimizationCount());
    }

    // ==================== 测试用例12：错误处理 - AI调用失败 ====================

    @Test
    @DisplayName("测试12：错误处理 - AI调用异常")
    void testErrorHandling_AICallException() {
        String code = "public void test() {}";

        Request request = Request.builder()
                .generatedCode(code)
                .scope(OptimizationScope.ALL)
                .build();

        // Mock AI调用抛出异常
        when(callResponseSpec.content()).thenThrow(new RuntimeException("AI服务不可用"));

        // 执行测试
        Response response = aiOptimizerTool.apply(request);

        // 验证fallback机制
        assertNotNull(response);
        assertFalse(response.getHasOptimizations());
        assertEquals(0, response.getOptimizationCount());
        assertEquals("优化分析失败", response.getSummary());
        assertNotNull(response.getErrorMessage());
        assertTrue(response.getErrorMessage().contains("AI服务不可用"));
        assertNotNull(response.getDurationMs());
    }

    // ==================== 测试用例13：错误处理 - JSON解析失败 ====================

    @Test
    @DisplayName("测试13：错误处理 - AI返回无效JSON")
    void testErrorHandling_InvalidJSON() {
        String code = "public void test() {}";

        Request request = Request.builder()
                .generatedCode(code)
                .scope(OptimizationScope.ALL)
                .build();

        // Mock AI返回无效JSON
        String invalidJson = "这不是一个有效的JSON响应";

        when(callResponseSpec.content()).thenReturn(invalidJson);

        // 执行测试
        Response response = aiOptimizerTool.apply(request);

        // 验证错误处理
        assertNotNull(response);
        assertFalse(response.getHasOptimizations());
        assertEquals(0, response.getOptimizationCount());
        assertNotNull(response.getErrorMessage());
    }

    // ==================== 测试用例14：上下文增强 - Entity信息 ====================

    @Test
    @DisplayName("测试14：上下文增强 - 传入Entity增强分析")
    void testContextEnhancement_WithEntity() {
        String code = "public User createUser(User user) { return userRepository.save(user); }";

        Entity entity = Entity.builder()
                .name("User")
                .description("用户实体")
                .build();

        Request request = Request.builder()
                .generatedCode(code)
                .entity(entity)
                .methodName("createUser")
                .scope(OptimizationScope.ALL)
                .build();

        String aiResponse = """
                {
                  "hasOptimizations": true,
                  "optimizations": [
                    {
                      "category": "BEST_PRACTICE",
                      "severity": "MEDIUM",
                      "location": "createUser方法",
                      "issue": "缺少输入验证和事务管理",
                      "suggestion": "添加@Transactional注解和输入验证",
                      "example": "@Transactional\\npublic User createUser(User user) {\\n  validateUser(user);\\n  return userRepository.save(user);\\n}"
                    }
                  ],
                  "summary": "针对User实体的createUser方法，建议添加事务和验证"
                }
                """;

        when(callResponseSpec.content()).thenReturn(aiResponse);

        Response response = aiOptimizerTool.apply(request);

        assertNotNull(response);
        assertTrue(response.getHasOptimizations());
        assertEquals(1, response.getOptimizationCount());
    }

    // ==================== 测试用例15：优化范围过滤 - 仅性能优化 ====================

    @Test
    @DisplayName("测试15：优化范围过滤 - 仅关注性能优化")
    void testOptimizationScope_PerformanceOnly() {
        String code = """
                public List<User> getAllUsers() {
                    List<User> users = userRepository.findAll();
                    for (User u : users) {
                        u.setOrders(orderRepository.findByUserId(u.getId()));
                    }
                    return users;
                }
                """;

        Request request = Request.builder()
                .generatedCode(code)
                .scope(OptimizationScope.PERFORMANCE)  // 仅关注性能
                .build();

        String aiResponse = """
                {
                  "hasOptimizations": true,
                  "optimizations": [
                    {
                      "category": "PERFORMANCE",
                      "severity": "HIGH",
                      "location": "getAllUsers方法",
                      "issue": "N+1查询问题",
                      "suggestion": "使用JOIN FETCH优化",
                      "example": "@Query(\\"SELECT u FROM User u LEFT JOIN FETCH u.orders\\")"
                    }
                  ],
                  "summary": "仅性能优化：检测到N+1查询问题"
                }
                """;

        when(callResponseSpec.content()).thenReturn(aiResponse);

        Response response = aiOptimizerTool.apply(request);

        assertNotNull(response);
        assertTrue(response.getHasOptimizations());
        assertEquals(1, response.getOptimizationCount());

        // 验证只返回性能相关优化
        Optimization opt = response.getOptimizations().get(0);
        assertEquals(OptimizationCategory.PERFORMANCE, opt.getCategory());
    }

    // ==================== 测试用例16：响应时间验证 ====================

    @Test
    @DisplayName("测试16：性能指标 - 验证响应时间记录")
    void testPerformanceMetrics_DurationTracking() {
        String code = "public void test() {}";

        Request request = Request.builder()
                .generatedCode(code)
                .scope(OptimizationScope.ALL)
                .build();

        String aiResponse = """
                {
                  "hasOptimizations": false,
                  "optimizations": [],
                  "summary": "无优化建议"
                }
                """;

        when(callResponseSpec.content()).thenReturn(aiResponse);

        long startTime = System.currentTimeMillis();
        Response response = aiOptimizerTool.apply(request);
        long endTime = System.currentTimeMillis();

        // 验证durationMs字段
        assertNotNull(response.getDurationMs());
        assertTrue(response.getDurationMs() >= 0);
        assertTrue(response.getDurationMs() <= (endTime - startTime) + 100); // 允许100ms误差
    }
}
