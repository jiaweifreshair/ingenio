package com.ingenio.backend.e2e;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ingenio.backend.deployment.DeploymentAutomationService;
import com.ingenio.backend.deployment.DeploymentAutomationService.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * 部署自动化E2E测试
 *
 * <p>测试覆盖V2.0部署自动化功能：</p>
 * <ul>
 *   <li>部署配置生成（Docker/Vercel/AWS ECS/Kubernetes）</li>
 *   <li>环境变量配置</li>
 *   <li>资源配置</li>
 *   <li>健康检查配置</li>
 *   <li>部署执行</li>
 *   <li>部署状态查询</li>
 *   <li>部署回滚</li>
 * </ul>
 *
 * @author Justin
 * @since 2025-11-30 E2E测试覆盖
 */
@DisplayName("部署自动化E2E测试")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class DeploymentE2ETest extends BaseE2ETest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private DeploymentAutomationService deploymentService;

    private static String testDeploymentId;

    // ==================== 1. 部署配置生成测试 ====================

    @Test
    @Order(1)
    @DisplayName("配置生成: Docker部署配置")
    void testGenerateConfig_Docker() throws Exception {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("appName", "test-app");
        request.put("environment", "DEVELOPMENT");
        request.put("target", "DOCKER");
        request.put("codeArtifact", Map.of(
                "frontend", Map.of("framework", "nextjs"),
                "backend", Map.of("framework", "spring-boot")
        ));

        mockMvc.perform(post("/v1/deployment/config")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.appName").value("test-app"))
                .andExpect(jsonPath("$.data.environment").value("DEVELOPMENT"))
                .andExpect(jsonPath("$.data.target").value("DOCKER"))
                .andExpect(jsonPath("$.data.platformConfig.dockerfile").exists())
                .andExpect(jsonPath("$.data.platformConfig.docker-compose").exists())
                .andExpect(jsonPath("$.data.environmentVariables").exists())
                .andExpect(jsonPath("$.data.resourceConfig").exists())
                .andExpect(jsonPath("$.data.healthCheckConfig").exists());
    }

    @Test
    @Order(2)
    @DisplayName("配置生成: Vercel部署配置")
    void testGenerateConfig_Vercel() throws Exception {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("appName", "frontend-app");
        request.put("environment", "PRODUCTION");
        request.put("target", "VERCEL");
        request.put("codeArtifact", Map.of("frontend", Map.of("framework", "nextjs")));

        mockMvc.perform(post("/v1/deployment/config")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.target").value("VERCEL"))
                .andExpect(jsonPath("$.data.platformConfig").exists());
    }

    @Test
    @Order(3)
    @DisplayName("配置生成: AWS ECS部署配置")
    void testGenerateConfig_AwsEcs() throws Exception {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("appName", "backend-service");
        request.put("environment", "STAGING");
        request.put("target", "AWS_ECS");
        request.put("codeArtifact", Map.of("backend", Map.of("framework", "spring-boot")));

        mockMvc.perform(post("/v1/deployment/config")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.target").value("AWS_ECS"))
                .andExpect(jsonPath("$.data.platformConfig['task-definition.json']").exists());
    }

    @Test
    @Order(4)
    @DisplayName("配置生成: Kubernetes部署配置")
    void testGenerateConfig_Kubernetes() throws Exception {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("appName", "microservice");
        request.put("environment", "PRODUCTION");
        request.put("target", "KUBERNETES");
        request.put("codeArtifact", Map.of("backend", Map.of("framework", "spring-boot")));

        mockMvc.perform(post("/v1/deployment/config")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.target").value("KUBERNETES"))
                .andExpect(jsonPath("$.data.platformConfig['deployment.yaml']").exists())
                .andExpect(jsonPath("$.data.platformConfig['service.yaml']").exists())
                .andExpect(jsonPath("$.data.platformConfig['ingress.yaml']").exists());
    }

    // ==================== 2. 配置详情验证测试 ====================

    @Test
    @Order(10)
    @DisplayName("配置详情: Docker配置内容验证")
    void testDockerConfig_Content() {
        Map<String, Object> codeArtifact = Map.of(
                "frontend", Map.of("framework", "nextjs"),
                "backend", Map.of("framework", "spring-boot")
        );

        DeploymentConfig config = deploymentService.generateConfig(
                "full-stack-app",
                Environment.DEVELOPMENT,
                DeploymentTarget.DOCKER,
                codeArtifact
        );

        assertThat(config).isNotNull();
        assertThat(config.getAppName()).isEqualTo("full-stack-app");

        // 验证Docker配置内容
        Map<String, Object> platformConfig = config.getPlatformConfig();
        assertThat(platformConfig).containsKeys("dockerfile", "docker-compose", "image_name", "ports", "volumes");

        // 验证Dockerfile包含多阶段构建
        String dockerfile = (String) platformConfig.get("dockerfile");
        assertThat(dockerfile).contains("FROM");
        assertThat(dockerfile).contains("WORKDIR");
        assertThat(dockerfile).contains("EXPOSE");

        // 验证docker-compose包含服务定义
        String dockerCompose = (String) platformConfig.get("docker-compose");
        assertThat(dockerCompose).contains("services:");
        assertThat(dockerCompose).contains("postgres:");
        assertThat(dockerCompose).contains("redis:");
    }

    @Test
    @Order(11)
    @DisplayName("配置详情: 环境变量验证")
    void testEnvironmentVariables() {
        DeploymentConfig config = deploymentService.generateConfig(
                "test-app",
                Environment.PRODUCTION,
                DeploymentTarget.DOCKER,
                Map.of()
        );

        Map<String, String> envVars = config.getEnvironmentVariables();

        assertThat(envVars).containsEntry("NODE_ENV", "production");
        assertThat(envVars).containsEntry("SPRING_PROFILES_ACTIVE", "production");
        assertThat(envVars).containsEntry("LOG_LEVEL", "INFO");
        assertThat(envVars).containsEntry("FEATURE_DEBUG_MODE", "false");
        assertThat(envVars).containsKey("DATABASE_URL");
        assertThat(envVars).containsKey("REDIS_URL");
    }

    @Test
    @Order(12)
    @DisplayName("配置详情: 资源配置按环境区分")
    void testResourceConfig_ByEnvironment() {
        // 开发环境资源配置
        DeploymentConfig devConfig = deploymentService.generateConfig(
                "app", Environment.DEVELOPMENT, DeploymentTarget.DOCKER, Map.of());
        assertThat(devConfig.getResourceConfig().get("replicas")).isEqualTo(1);
        assertThat(devConfig.getResourceConfig().get("memory")).isEqualTo("512Mi");

        // 测试环境资源配置
        DeploymentConfig testConfig = deploymentService.generateConfig(
                "app", Environment.TESTING, DeploymentTarget.DOCKER, Map.of());
        assertThat(testConfig.getResourceConfig().get("replicas")).isEqualTo(1);
        assertThat(testConfig.getResourceConfig().get("memory")).isEqualTo("1Gi");

        // 预发布环境资源配置
        DeploymentConfig stagingConfig = deploymentService.generateConfig(
                "app", Environment.STAGING, DeploymentTarget.DOCKER, Map.of());
        assertThat(stagingConfig.getResourceConfig().get("replicas")).isEqualTo(2);

        // 生产环境资源配置
        DeploymentConfig prodConfig = deploymentService.generateConfig(
                "app", Environment.PRODUCTION, DeploymentTarget.DOCKER, Map.of());
        assertThat(prodConfig.getResourceConfig().get("replicas")).isEqualTo(3);
        assertThat(prodConfig.getResourceConfig().get("memory")).isEqualTo("2Gi");
        assertThat(prodConfig.getResourceConfig()).containsKey("autoscaling");
    }

    @Test
    @Order(13)
    @DisplayName("配置详情: 健康检查配置")
    void testHealthCheckConfig() {
        DeploymentConfig config = deploymentService.generateConfig(
                "app", Environment.PRODUCTION, DeploymentTarget.DOCKER, Map.of());

        Map<String, Object> healthCheck = config.getHealthCheckConfig();
        assertThat(healthCheck).containsKeys("liveness", "readiness");

        @SuppressWarnings("unchecked")
        Map<String, Object> liveness = (Map<String, Object>) healthCheck.get("liveness");
        assertThat(liveness.get("path")).isEqualTo("/actuator/health/liveness");
        assertThat(liveness.get("initialDelaySeconds")).isEqualTo(30);

        @SuppressWarnings("unchecked")
        Map<String, Object> readiness = (Map<String, Object>) healthCheck.get("readiness");
        assertThat(readiness.get("path")).isEqualTo("/actuator/health/readiness");
        assertThat(readiness.get("initialDelaySeconds")).isEqualTo(5);
    }

    // ==================== 3. 部署执行测试 ====================

    @Test
    @Order(20)
    @DisplayName("部署执行: 发起部署请求")
    void testDeploy() throws Exception {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("appName", "deploy-test-app");
        request.put("environment", "DEVELOPMENT");
        request.put("target", "DOCKER");
        request.put("codeArtifact", Map.of("frontend", Map.of("framework", "react")));

        String response = mockMvc.perform(post("/v1/deployment/deploy")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.deploymentId").exists())
                .andExpect(jsonPath("$.data.status").value(anyOf(is("PENDING"), is("RUNNING"))))
                .andExpect(jsonPath("$.data.startedAt").exists())
                .andReturn()
                .getResponse()
                .getContentAsString();

        // 保存部署ID用于后续测试
        @SuppressWarnings("unchecked")
        Map<String, Object> result = objectMapper.readValue(response, Map.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) result.get("data");
        testDeploymentId = (String) data.get("deploymentId");
    }

    @Test
    @Order(21)
    @DisplayName("部署执行: 查询部署状态")
    void testGetDeploymentStatus() throws Exception {
        // 如果前置测试未创建deploymentId，先创建一个
        if (testDeploymentId == null) {
            createTestDeployment();
        }

        // 等待部署任务开始处理
        Thread.sleep(100);

        mockMvc.perform(get("/v1/deployment/status/{deploymentId}", testDeploymentId)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.deploymentId").value(testDeploymentId))
                .andExpect(jsonPath("$.data.status").value(anyOf(
                        is("PENDING"), is("RUNNING"), is("SUCCESS")
                )))
                .andExpect(jsonPath("$.data.logs").isArray());
    }

    @Test
    @Order(22)
    @DisplayName("部署执行: 等待部署完成")
    void testWaitForDeploymentComplete() throws Exception {
        // 如果前置测试未创建deploymentId，先创建一个
        if (testDeploymentId == null) {
            createTestDeployment();
        }

        // 等待异步部署完成（最多5秒）
        DeploymentTask task = null;
        for (int i = 0; i < 10; i++) {
            task = deploymentService.getDeploymentStatus(testDeploymentId);
            if (task != null && task.getStatus() == DeploymentStatus.SUCCESS) {
                break;
            }
            Thread.sleep(500);
        }

        assertThat(task).isNotNull();
        assertThat(task.getStatus()).isEqualTo(DeploymentStatus.SUCCESS);
        assertThat(task.getDeploymentUrl()).isNotNull();
        assertThat(task.getCompletedAt()).isNotNull();
        assertThat(task.getLogs()).isNotEmpty();
    }

    @Test
    @Order(23)
    @DisplayName("部署执行: 验证部署日志")
    void testDeploymentLogs() throws InterruptedException {
        // 如果前置测试未创建deploymentId，先创建一个
        if (testDeploymentId == null) {
            createTestDeployment();
        }

        // 等待部署完成
        waitForDeployment(testDeploymentId);

        DeploymentTask task = deploymentService.getDeploymentStatus(testDeploymentId);

        assertThat(task).isNotNull();
        assertThat(task.getLogs()).isNotEmpty();
        assertThat(task.getLogs()).anyMatch(log -> log.contains("开始部署"));
        assertThat(task.getLogs()).anyMatch(log -> log.contains("健康检查"));
        assertThat(task.getLogs()).anyMatch(log -> log.contains("部署成功"));
    }

    // ==================== 4. 部署回滚测试 ====================

    @Test
    @Order(30)
    @DisplayName("部署回滚: 执行回滚操作")
    void testRollback() throws Exception {
        // 如果前置测试未创建deploymentId，先创建一个
        if (testDeploymentId == null) {
            createTestDeployment();
        }

        // 等待部署完成后才能回滚
        waitForDeployment(testDeploymentId);

        mockMvc.perform(post("/v1/deployment/rollback/{deploymentId}", testDeploymentId)
                        .param("targetVersion", "v1.0.0")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.deploymentId").exists())
                .andExpect(jsonPath("$.data.rollbackFromId").value(testDeploymentId))
                .andExpect(jsonPath("$.data.rollbackTargetVersion").value("v1.0.0"));
    }

    @Test
    @Order(31)
    @DisplayName("部署回滚: 等待回滚完成")
    void testRollbackComplete() throws Exception {
        // 如果前置测试未创建deploymentId，先创建一个
        if (testDeploymentId == null) {
            createTestDeployment();
            waitForDeployment(testDeploymentId);
        }

        // 执行回滚
        DeploymentTask rollbackTask = deploymentService.rollback(testDeploymentId, "v0.9.0");
        String rollbackId = rollbackTask.getDeploymentId();

        // 等待回滚完成
        for (int i = 0; i < 10; i++) {
            rollbackTask = deploymentService.getDeploymentStatus(rollbackId);
            if (rollbackTask != null && rollbackTask.getStatus() == DeploymentStatus.SUCCESS) {
                break;
            }
            Thread.sleep(500);
        }

        assertThat(rollbackTask.getStatus()).isEqualTo(DeploymentStatus.SUCCESS);
        assertThat(rollbackTask.getRollbackFromId()).isEqualTo(testDeploymentId);
        assertThat(rollbackTask.getLogs()).anyMatch(log -> log.contains("回滚成功"));
    }

    // ==================== 5. 枚举值查询测试 ====================

    @Test
    @Order(40)
    @DisplayName("枚举查询: 获取支持的环境列表")
    void testGetEnvironments() throws Exception {
        mockMvc.perform(get("/v1/deployment/environments")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(4));
    }

    @Test
    @Order(41)
    @DisplayName("枚举查询: 获取支持的部署目标列表")
    void testGetTargets() throws Exception {
        mockMvc.perform(get("/v1/deployment/targets")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(7));
    }

    // ==================== 6. 边界条件测试 ====================

    @Test
    @Order(50)
    @DisplayName("边界条件: 查询不存在的部署")
    void testNonExistentDeployment() throws Exception {
        mockMvc.perform(get("/v1/deployment/status/{deploymentId}", "non-existent-id")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @Order(51)
    @DisplayName("边界条件: 回滚不存在的部署")
    void testRollbackNonExistentDeployment() throws Exception {
        mockMvc.perform(post("/v1/deployment/rollback/{deploymentId}", "fake-deployment-id")
                        .param("targetVersion", "v1.0.0")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(false));
    }

    // ==================== 7. 多平台配置对比测试 ====================

    @Test
    @Order(60)
    @DisplayName("多平台对比: 不同平台生成URL格式")
    void testDeploymentUrl_ByPlatform() {
        Map<String, Object> artifact = Map.of("frontend", Map.of());

        // Vercel URL
        DeploymentConfig vercelConfig = deploymentService.generateConfig(
                "my-app", Environment.PRODUCTION, DeploymentTarget.VERCEL, artifact);
        DeploymentTask vercelTask = deploymentService.deploy(vercelConfig);
        // 等待完成
        waitForDeployment(vercelTask.getDeploymentId());
        vercelTask = deploymentService.getDeploymentStatus(vercelTask.getDeploymentId());
        assertThat(vercelTask.getDeploymentUrl()).contains("vercel.app");

        // Kubernetes URL
        DeploymentConfig k8sConfig = deploymentService.generateConfig(
                "my-app", Environment.PRODUCTION, DeploymentTarget.KUBERNETES, artifact);
        DeploymentTask k8sTask = deploymentService.deploy(k8sConfig);
        waitForDeployment(k8sTask.getDeploymentId());
        k8sTask = deploymentService.getDeploymentStatus(k8sTask.getDeploymentId());
        assertThat(k8sTask.getDeploymentUrl()).contains("ingenio.dev");
    }

    @Test
    @Order(61)
    @DisplayName("多平台对比: Kubernetes配置完整性")
    void testKubernetesConfig_Completeness() {
        DeploymentConfig config = deploymentService.generateConfig(
                "k8s-app",
                Environment.PRODUCTION,
                DeploymentTarget.KUBERNETES,
                Map.of("backend", Map.of())
        );

        Map<String, Object> platformConfig = config.getPlatformConfig();

        // 验证Deployment YAML
        String deployment = (String) platformConfig.get("deployment.yaml");
        assertThat(deployment).contains("apiVersion: apps/v1");
        assertThat(deployment).contains("kind: Deployment");
        assertThat(deployment).contains("replicas: 3"); // 生产环境3副本
        assertThat(deployment).contains("livenessProbe:");
        assertThat(deployment).contains("readinessProbe:");

        // 验证Service YAML
        String service = (String) platformConfig.get("service.yaml");
        assertThat(service).contains("kind: Service");
        assertThat(service).contains("type: LoadBalancer");

        // 验证Ingress YAML
        String ingress = (String) platformConfig.get("ingress.yaml");
        assertThat(ingress).contains("kind: Ingress");
        assertThat(ingress).contains("tls:");
        assertThat(ingress).contains("cert-manager");
    }

    @Test
    @Order(62)
    @DisplayName("多平台对比: AWS ECS配置完整性")
    void testAwsEcsConfig_Completeness() {
        DeploymentConfig config = deploymentService.generateConfig(
                "ecs-app",
                Environment.PRODUCTION,
                DeploymentTarget.AWS_ECS,
                Map.of("backend", Map.of())
        );

        Map<String, Object> platformConfig = config.getPlatformConfig();

        // 验证任务定义
        @SuppressWarnings("unchecked")
        Map<String, Object> taskDef = (Map<String, Object>) platformConfig.get("task-definition.json");
        assertThat(taskDef.get("networkMode")).isEqualTo("awsvpc");
        assertThat(taskDef.get("requiresCompatibilities")).asList().contains("FARGATE");
        assertThat(taskDef.get("cpu")).isEqualTo("1024"); // 生产环境更多CPU
        assertThat(taskDef.get("memory")).isEqualTo("2048"); // 生产环境更多内存

        // 验证服务配置
        @SuppressWarnings("unchecked")
        Map<String, Object> serviceConfig = (Map<String, Object>) platformConfig.get("service-config.json");
        assertThat(serviceConfig.get("desiredCount")).isEqualTo(3); // 生产环境3实例
    }

    /**
     * 等待部署完成的辅助方法
     */
    private void waitForDeployment(String deploymentId) {
        for (int i = 0; i < 10; i++) {
            DeploymentTask task = deploymentService.getDeploymentStatus(deploymentId);
            if (task != null && (task.getStatus() == DeploymentStatus.SUCCESS ||
                    task.getStatus() == DeploymentStatus.FAILED)) {
                return;
            }
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    /**
     * 创建测试部署的辅助方法
     * 当testDeploymentId为空时调用
     */
    private void createTestDeployment() {
        Map<String, Object> artifact = Map.of("frontend", Map.of("framework", "react"));
        DeploymentConfig config = deploymentService.generateConfig(
                "test-deploy-app",
                Environment.DEVELOPMENT,
                DeploymentTarget.DOCKER,
                artifact
        );
        DeploymentTask task = deploymentService.deploy(config);
        testDeploymentId = task.getDeploymentId();
    }
}
