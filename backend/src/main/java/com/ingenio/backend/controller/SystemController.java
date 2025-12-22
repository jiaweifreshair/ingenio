package com.ingenio.backend.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.info.BuildProperties;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 系统信息控制器
 *
 * 提供系统版本、构建信息、运行状态等元数据API
 * 主要用于：
 * 1. E2E测试前验证服务版本
 * 2. CI/CD流程中确认部署成功
 * 3. 生产环境故障排查
 *
 * @author Claude
 * @since 2025-11-16 (Phase X.3 技术债务)
 */
@Slf4j
@RestController
@RequestMapping("/v1/system")
public class SystemController {

    private static final DateTimeFormatter FORMATTER =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());

    private final Instant serverStartTime;
    private final Optional<BuildProperties> buildProperties;

    @Value("${spring.application.name:ingenio-backend}")
    private String applicationName;

    @Value("${spring.profiles.active:default}")
    private String activeProfile;

    public SystemController(Optional<BuildProperties> buildProperties) {
        this.buildProperties = buildProperties;
        this.serverStartTime = Instant.now();
    }

    /**
     * 获取服务版本和构建信息
     *
     * 返回字段说明：
     * - version: 应用版本号（来自pom.xml <version>）
     * - buildTime: 构建时间（class文件编译时间）
     * - startTime: 服务启动时间
     * - uptime: 服务运行时长（秒）
     * - applicationName: 应用名称
     * - profile: 当前激活的配置文件（local/dev/prod）
     * - gitCommit: Git提交哈希（如果可用）
     * - javaVersion: Java版本
     * - osName: 操作系统名称
     *
     * 使用示例：
     * ```bash
     * # E2E测试前检查服务版本
     * curl http://localhost:8080/api/v1/system/version
     *
     * # 检查构建时间是否晚于代码编译时间
     * BUILD_TIME=$(curl -s http://localhost:8080/api/v1/system/version | jq -r '.buildTime')
     * ```
     *
     * @return 系统版本信息
     */
    @GetMapping("/version")
    public Map<String, Object> getVersion() {
        log.debug("查询系统版本信息");

        Map<String, Object> versionInfo = new HashMap<>();

        // 基础信息
        versionInfo.put("applicationName", applicationName);
        versionInfo.put("profile", activeProfile);

        // BuildProperties来自spring-boot-maven-plugin自动生成（target/classes/META-INF/build-info.properties）
        if (buildProperties.isPresent()) {
            BuildProperties props = buildProperties.get();
            versionInfo.put("version", props.getVersion());
            versionInfo.put("buildTime", FORMATTER.format(props.getTime()));
            versionInfo.put("buildTimeEpoch", props.getTime().toEpochMilli());

            // Git信息（需要配置git-commit-id-plugin）
            if (props.get("git.commit.id") != null) {
                versionInfo.put("gitCommit", props.get("git.commit.id"));
            }
        } else {
            // 开发环境可能没有BuildProperties，使用默认值
            versionInfo.put("version", "2.0.0-SNAPSHOT");
            versionInfo.put("buildTime", "Not available (dev mode)");
            log.warn("BuildProperties不可用，可能是开发环境直接运行");
        }

        // 服务运行信息
        versionInfo.put("startTime", FORMATTER.format(serverStartTime));
        versionInfo.put("startTimeEpoch", serverStartTime.toEpochMilli());
        long uptimeSeconds = (System.currentTimeMillis() - serverStartTime.toEpochMilli()) / 1000;
        versionInfo.put("uptimeSeconds", uptimeSeconds);
        versionInfo.put("uptimeFormatted", formatUptime(uptimeSeconds));

        // 系统环境信息
        versionInfo.put("javaVersion", System.getProperty("java.version"));
        versionInfo.put("javaVendor", System.getProperty("java.vendor"));
        versionInfo.put("osName", System.getProperty("os.name"));
        versionInfo.put("osVersion", System.getProperty("os.version"));
        versionInfo.put("osArch", System.getProperty("os.arch"));

        return versionInfo;
    }

    /**
     * 健康检查端点
     *
     * 简单的健康检查，返回服务状态
     * 用于负载均衡器、监控系统的探活
     *
     * @return 健康状态
     */
    @GetMapping("/health")
    public Map<String, Object> health() {
        Map<String, Object> health = new HashMap<>();
        health.put("status", "UP");
        health.put("timestamp", FORMATTER.format(Instant.now()));
        health.put("uptime", formatUptime((System.currentTimeMillis() - serverStartTime.toEpochMilli()) / 1000));
        return health;
    }

    /**
     * 格式化运行时长
     *
     * @param uptimeSeconds 运行秒数
     * @return 格式化字符串，如 "2d 3h 45m 12s"
     */
    private String formatUptime(long uptimeSeconds) {
        long days = uptimeSeconds / 86400;
        long hours = (uptimeSeconds % 86400) / 3600;
        long minutes = (uptimeSeconds % 3600) / 60;
        long seconds = uptimeSeconds % 60;

        if (days > 0) {
            return String.format("%dd %dh %dm %ds", days, hours, minutes, seconds);
        } else if (hours > 0) {
            return String.format("%dh %dm %ds", hours, minutes, seconds);
        } else if (minutes > 0) {
            return String.format("%dm %ds", minutes, seconds);
        } else {
            return String.format("%ds", seconds);
        }
    }
}
