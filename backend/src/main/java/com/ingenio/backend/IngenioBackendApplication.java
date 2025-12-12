package com.ingenio.backend;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Ingenio Backend主应用类
 * AI驱动的自然语言编程平台
 *
 * 技术栈：
 * - Spring Boot 3.2.0
 * - Spring AI Alibaba 1.0.0-M1（DeepSeek集成）
 * - MyBatis-Plus 3.5.5
 * - SaToken 1.37.0（JWT认证）
 * - PostgreSQL 15.x
 * - Redis 7.x
 * - MinIO 8.5.7
 *
 * @author Ingenio Team
 * @version 0.1.0
 */
@SpringBootApplication
@MapperScan("com.ingenio.backend.mapper")
@EnableAsync
public class IngenioBackendApplication {

    public static void main(String[] args) {
        // 启用Java 21虚拟线程（Project Loom）
        System.setProperty("spring.threads.virtual.enabled", "true");

        SpringApplication.run(IngenioBackendApplication.class, args);

        System.out.println("""

            ====================================================
            🚀 Ingenio Backend 已启动成功！
            ====================================================
            📚 API文档: http://localhost:8080/api/swagger-ui.html
            🔍 健康检查: http://localhost:8080/api/actuator/health
            📊 Metrics: http://localhost:8080/api/actuator/metrics
            ====================================================
            """);
    }
}
