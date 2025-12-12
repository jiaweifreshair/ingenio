package com.ingenio.backend.e2e;

import com.ingenio.backend.config.TestSaTokenConfig;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * E2E测试基类
 *
 * 功能：
 * 1. 使用TestContainers启动真实PostgreSQL数据库
 * 2. 配置Spring Boot测试环境
 * 3. 提供MockMvc进行HTTP请求测试
 * 4. 零Mock策略：使用真实数据库和服务
 * 5. 禁用SaToken认证，允许测试请求通过
 *
 * 所有E2E测试类继承此基类
 *
 * 容器生命周期：
 * - 使用Singleton模式确保所有测试类共享同一个PostgreSQL容器
 * - 容器在第一个测试类执行前启动
 * - 容器在所有测试类执行完毕后停止
 * - 这避免了多个测试类之间的容器冲突问题
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestSaTokenConfig.class)  // 导入SaToken测试配置，禁用认证检查
public abstract class BaseE2ETest {

    /**
     * PostgreSQL TestContainer (Singleton)
     * 使用PostgreSQL 14，与生产环境版本一致
     *
     * 数据库初始化：
     * - 自动执行 schema.sql 初始化所有表结构
     * - schema.sql 包含16个migration脚本的合并版本
     *
     * Singleton模式：
     * - 在第一次访问时启动容器
     * - 所有测试类共享同一个容器实例
     * - JVM退出时自动停止容器
     */
    protected static PostgreSQLContainer<?> postgresContainer;

    static {
        postgresContainer = new PostgreSQLContainer<>("postgres:14-alpine")
                .withDatabaseName("ingenio_test")
                .withUsername("test_user")
                .withPassword("test_password")
                .withInitScript("schema.sql"); // 执行数据库初始化脚本
        postgresContainer.start();
    }

    /**
     * 动态配置数据源属性
     * 从TestContainer获取动态端口和连接信息
     *
     * stringtype=unspecified: 允许PostgreSQL自动推断字符串类型
     * 这对于JSONB字段至关重要，否则MyBatis-Plus的JacksonTypeHandler会失败
     */
    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        // 添加stringtype=unspecified支持JSONB类型
        String jdbcUrl = postgresContainer.getJdbcUrl() + "&stringtype=unspecified";
        registry.add("spring.datasource.url", () -> jdbcUrl);
        registry.add("spring.datasource.username", postgresContainer::getUsername);
        registry.add("spring.datasource.password", postgresContainer::getPassword);
    }

    /**
     * MockMvc用于HTTP请求测试
     */
    @Autowired
    protected MockMvc mockMvc;

    /**
     * 每个测试前的初始化
     * 子类可以重写此方法进行额外初始化
     */
    @BeforeEach
    public void setUp() {
        // 基础初始化（如清理测试数据等）
        // 子类可以重写并调用super.setUp()
    }
}
