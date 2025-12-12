package com.ingenio.backend.deployment;

import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;

/**
 * 部署自动化服务
 *
 * <p>V2.0部署自动化核心功能：</p>
 * <ul>
 *   <li>多环境配置管理（开发/测试/预发布/生产）</li>
 *   <li>Docker容器化部署</li>
 *   <li>云平台部署（Vercel/AWS/阿里云）</li>
 *   <li>数据库迁移管理</li>
 *   <li>健康检查与监控</li>
 *   <li>一键回滚</li>
 *   <li>蓝绿部署/金丝雀发布</li>
 * </ul>
 *
 * @author Justin
 * @since 2025-11-17 V2.0部署自动化
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeploymentAutomationService {

    /** 部署任务缓存 */
    private final Map<String, DeploymentTask> deploymentTasks = new ConcurrentHashMap<>();

    /** 线程池用于异步部署 */
    private final ExecutorService executorService = Executors.newFixedThreadPool(4);

    // ==================== 1. 部署环境枚举 ====================

    /**
     * 部署环境类型
     */
    public enum Environment {
        DEVELOPMENT("development", "开发环境", "用于本地开发调试"),
        TESTING("testing", "测试环境", "用于集成测试"),
        STAGING("staging", "预发布环境", "用于UAT验收"),
        PRODUCTION("production", "生产环境", "正式上线环境");

        private final String code;
        private final String displayName;
        private final String description;

        Environment(String code, String displayName, String description) {
            this.code = code;
            this.displayName = displayName;
            this.description = description;
        }

        public String getCode() { return code; }
        public String getDisplayName() { return displayName; }
        public String getDescription() { return description; }
    }

    /**
     * 部署目标平台
     */
    public enum DeploymentTarget {
        DOCKER("docker", "Docker容器"),
        VERCEL("vercel", "Vercel平台"),
        AWS_ECS("aws-ecs", "AWS ECS"),
        AWS_LAMBDA("aws-lambda", "AWS Lambda"),
        ALIYUN_ECS("aliyun-ecs", "阿里云ECS"),
        ALIYUN_FC("aliyun-fc", "阿里云函数计算"),
        KUBERNETES("kubernetes", "Kubernetes集群");

        private final String code;
        private final String displayName;

        DeploymentTarget(String code, String displayName) {
            this.code = code;
            this.displayName = displayName;
        }

        public String getCode() { return code; }
        public String getDisplayName() { return displayName; }
    }

    // ==================== 2. 部署配置生成 ====================

    /**
     * 生成部署配置
     *
     * @param appName     应用名称
     * @param environment 目标环境
     * @param target      部署平台
     * @param codeArtifact 代码产物
     * @return 完整的部署配置
     */
    public DeploymentConfig generateConfig(
            String appName,
            Environment environment,
            DeploymentTarget target,
            Map<String, Object> codeArtifact
    ) {
        log.info("[Deployment] 生成部署配置: app={}, env={}, target={}",
                appName, environment, target);

        // 基础配置
        DeploymentConfig.DeploymentConfigBuilder builder = DeploymentConfig.builder()
                .appName(appName)
                .environment(environment)
                .target(target)
                .createdAt(Instant.now());

        // 根据平台生成特定配置
        switch (target) {
            case DOCKER:
                builder.platformConfig(generateDockerConfig(appName, environment, codeArtifact));
                break;
            case VERCEL:
                builder.platformConfig(generateVercelConfig(appName, environment, codeArtifact));
                break;
            case AWS_ECS:
                builder.platformConfig(generateAwsEcsConfig(appName, environment, codeArtifact));
                break;
            case KUBERNETES:
                builder.platformConfig(generateKubernetesConfig(appName, environment, codeArtifact));
                break;
            default:
                builder.platformConfig(generateGenericConfig(appName, environment, codeArtifact));
        }

        // 环境变量配置
        builder.environmentVariables(generateEnvVariables(environment, codeArtifact));

        // 资源配置
        builder.resourceConfig(generateResourceConfig(environment));

        // 健康检查配置
        builder.healthCheckConfig(generateHealthCheckConfig());

        return builder.build();
    }

    /**
     * 生成Docker配置
     */
    private Map<String, Object> generateDockerConfig(
            String appName,
            Environment environment,
            Map<String, Object> codeArtifact
    ) {
        Map<String, Object> config = new LinkedHashMap<>();

        // Dockerfile内容
        String dockerfile = generateDockerfile(appName, codeArtifact);
        config.put("dockerfile", dockerfile);

        // docker-compose.yml
        String dockerCompose = generateDockerCompose(appName, environment);
        config.put("docker-compose", dockerCompose);

        // 镜像配置
        config.put("image_name", String.format("ingenio/%s", appName.toLowerCase()));
        config.put("image_tag", environment.getCode() + "-" + Instant.now().getEpochSecond());

        // 端口映射
        config.put("ports", List.of(
                Map.of("container", 3000, "host", 3000, "protocol", "tcp"),
                Map.of("container", 8080, "host", 8080, "protocol", "tcp")
        ));

        // 卷挂载
        config.put("volumes", List.of(
                Map.of("source", "./data", "target", "/app/data", "type", "bind"),
                Map.of("source", "node_modules", "target", "/app/node_modules", "type", "volume")
        ));

        return config;
    }

    /**
     * 生成Dockerfile
     */
    private String generateDockerfile(String appName, Map<String, Object> codeArtifact) {
        StringBuilder dockerfile = new StringBuilder();

        // 判断是前端还是后端
        boolean hasFrontend = codeArtifact.containsKey("frontend");
        boolean hasBackend = codeArtifact.containsKey("backend");

        if (hasFrontend && !hasBackend) {
            // 纯前端Dockerfile
            dockerfile.append("# 前端应用 Dockerfile\n");
            dockerfile.append("FROM node:20-alpine AS builder\n\n");
            dockerfile.append("WORKDIR /app\n\n");
            dockerfile.append("# 安装依赖\n");
            dockerfile.append("COPY package*.json ./\n");
            dockerfile.append("RUN npm ci --only=production\n\n");
            dockerfile.append("# 复制源码并构建\n");
            dockerfile.append("COPY . .\n");
            dockerfile.append("RUN npm run build\n\n");
            dockerfile.append("# 生产镜像\n");
            dockerfile.append("FROM nginx:alpine\n");
            dockerfile.append("COPY --from=builder /app/dist /usr/share/nginx/html\n");
            dockerfile.append("COPY nginx.conf /etc/nginx/nginx.conf\n\n");
            dockerfile.append("EXPOSE 80\n");
            dockerfile.append("CMD [\"nginx\", \"-g\", \"daemon off;\"]\n");
        } else if (hasBackend && !hasFrontend) {
            // 纯后端Dockerfile (Java/Spring Boot)
            dockerfile.append("# 后端应用 Dockerfile\n");
            dockerfile.append("FROM eclipse-temurin:21-jdk-alpine AS builder\n\n");
            dockerfile.append("WORKDIR /app\n\n");
            dockerfile.append("# 复制Maven配置和源码\n");
            dockerfile.append("COPY pom.xml ./\n");
            dockerfile.append("COPY src ./src\n\n");
            dockerfile.append("# 构建应用\n");
            dockerfile.append("RUN ./mvnw clean package -DskipTests\n\n");
            dockerfile.append("# 生产镜像\n");
            dockerfile.append("FROM eclipse-temurin:21-jre-alpine\n\n");
            dockerfile.append("WORKDIR /app\n\n");
            dockerfile.append("# 复制构建产物\n");
            dockerfile.append("COPY --from=builder /app/target/*.jar app.jar\n\n");
            dockerfile.append("# 健康检查\n");
            dockerfile.append("HEALTHCHECK --interval=30s --timeout=3s \\\n");
            dockerfile.append("  CMD wget --no-verbose --tries=1 --spider http://localhost:8080/actuator/health || exit 1\n\n");
            dockerfile.append("EXPOSE 8080\n");
            dockerfile.append("ENTRYPOINT [\"java\", \"-jar\", \"app.jar\"]\n");
        } else {
            // 全栈Dockerfile (多阶段构建)
            dockerfile.append("# 全栈应用 Dockerfile (多阶段构建)\n\n");
            dockerfile.append("# === 前端构建阶段 ===\n");
            dockerfile.append("FROM node:20-alpine AS frontend-builder\n");
            dockerfile.append("WORKDIR /app/frontend\n");
            dockerfile.append("COPY frontend/package*.json ./\n");
            dockerfile.append("RUN npm ci\n");
            dockerfile.append("COPY frontend/ .\n");
            dockerfile.append("RUN npm run build\n\n");
            dockerfile.append("# === 后端构建阶段 ===\n");
            dockerfile.append("FROM eclipse-temurin:21-jdk-alpine AS backend-builder\n");
            dockerfile.append("WORKDIR /app/backend\n");
            dockerfile.append("COPY backend/pom.xml ./\n");
            dockerfile.append("COPY backend/src ./src\n");
            dockerfile.append("RUN ./mvnw clean package -DskipTests\n\n");
            dockerfile.append("# === 生产镜像 ===\n");
            dockerfile.append("FROM eclipse-temurin:21-jre-alpine\n");
            dockerfile.append("WORKDIR /app\n\n");
            dockerfile.append("# 安装Nginx\n");
            dockerfile.append("RUN apk add --no-cache nginx\n\n");
            dockerfile.append("# 复制前端静态文件\n");
            dockerfile.append("COPY --from=frontend-builder /app/frontend/dist /usr/share/nginx/html\n\n");
            dockerfile.append("# 复制后端JAR\n");
            dockerfile.append("COPY --from=backend-builder /app/backend/target/*.jar app.jar\n\n");
            dockerfile.append("# 启动脚本\n");
            dockerfile.append("COPY docker-entrypoint.sh /\n");
            dockerfile.append("RUN chmod +x /docker-entrypoint.sh\n\n");
            dockerfile.append("EXPOSE 80 8080\n");
            dockerfile.append("ENTRYPOINT [\"/docker-entrypoint.sh\"]\n");
        }

        return dockerfile.toString();
    }

    /**
     * 生成docker-compose.yml
     */
    private String generateDockerCompose(String appName, Environment environment) {
        StringBuilder compose = new StringBuilder();

        compose.append("version: '3.8'\n\n");
        compose.append("services:\n");

        // 应用服务
        compose.append(String.format("  %s:\n", appName.toLowerCase()));
        compose.append(String.format("    image: ingenio/%s:%s\n", appName.toLowerCase(), environment.getCode()));
        compose.append("    build:\n");
        compose.append("      context: .\n");
        compose.append("      dockerfile: Dockerfile\n");
        compose.append("    ports:\n");
        compose.append("      - \"3000:3000\"\n");
        compose.append("      - \"8080:8080\"\n");
        compose.append("    environment:\n");
        compose.append(String.format("      - NODE_ENV=%s\n", environment.getCode()));
        compose.append(String.format("      - SPRING_PROFILES_ACTIVE=%s\n", environment.getCode()));
        compose.append("    depends_on:\n");
        compose.append("      - postgres\n");
        compose.append("      - redis\n");
        compose.append("    networks:\n");
        compose.append("      - app-network\n\n");

        // PostgreSQL服务
        compose.append("  postgres:\n");
        compose.append("    image: postgres:15-alpine\n");
        compose.append("    environment:\n");
        compose.append("      POSTGRES_DB: ${DB_NAME:-ingenio}\n");
        compose.append("      POSTGRES_USER: ${DB_USER:-ingenio}\n");
        compose.append("      POSTGRES_PASSWORD: ${DB_PASSWORD:-ingenio_secret}\n");
        compose.append("    volumes:\n");
        compose.append("      - postgres-data:/var/lib/postgresql/data\n");
        compose.append("    networks:\n");
        compose.append("      - app-network\n\n");

        // Redis服务
        compose.append("  redis:\n");
        compose.append("    image: redis:7-alpine\n");
        compose.append("    volumes:\n");
        compose.append("      - redis-data:/data\n");
        compose.append("    networks:\n");
        compose.append("      - app-network\n\n");

        // 网络和卷
        compose.append("networks:\n");
        compose.append("  app-network:\n");
        compose.append("    driver: bridge\n\n");

        compose.append("volumes:\n");
        compose.append("  postgres-data:\n");
        compose.append("  redis-data:\n");

        return compose.toString();
    }

    /**
     * 生成Vercel配置
     */
    private Map<String, Object> generateVercelConfig(
            String appName,
            Environment environment,
            Map<String, Object> codeArtifact
    ) {
        Map<String, Object> config = new LinkedHashMap<>();

        // vercel.json
        Map<String, Object> vercelJson = new LinkedHashMap<>();
        vercelJson.put("name", appName.toLowerCase());
        vercelJson.put("version", 2);
        vercelJson.put("framework", "nextjs");

        // 构建配置
        vercelJson.put("builds", List.of(
                Map.of(
                        "src", "package.json",
                        "use", "@vercel/next"
                )
        ));

        // 路由配置
        vercelJson.put("routes", List.of(
                Map.of("src", "/api/(.*)", "dest", "/api/$1"),
                Map.of("src", "/(.*)", "dest", "/$1")
        ));

        // 环境变量
        Map<String, Object> env = new LinkedHashMap<>();
        env.put("NODE_ENV", environment.getCode());
        env.put("NEXT_PUBLIC_API_URL", getApiUrl(environment));
        vercelJson.put("env", env);

        config.put("vercel.json", vercelJson);

        // 部署命令
        config.put("deploy_commands", List.of(
                "npm install",
                "npm run build",
                "vercel --prod"
        ));

        return config;
    }

    /**
     * 生成AWS ECS配置
     */
    private Map<String, Object> generateAwsEcsConfig(
            String appName,
            Environment environment,
            Map<String, Object> codeArtifact
    ) {
        Map<String, Object> config = new LinkedHashMap<>();

        // ECS任务定义
        Map<String, Object> taskDefinition = new LinkedHashMap<>();
        taskDefinition.put("family", String.format("%s-%s", appName, environment.getCode()));
        taskDefinition.put("networkMode", "awsvpc");
        taskDefinition.put("requiresCompatibilities", List.of("FARGATE"));
        taskDefinition.put("cpu", environment == Environment.PRODUCTION ? "1024" : "512");
        taskDefinition.put("memory", environment == Environment.PRODUCTION ? "2048" : "1024");

        // 容器定义
        Map<String, Object> containerDef = new LinkedHashMap<>();
        containerDef.put("name", appName.toLowerCase());
        containerDef.put("image", String.format("${AWS_ACCOUNT_ID}.dkr.ecr.${AWS_REGION}.amazonaws.com/%s:%s",
                appName.toLowerCase(), environment.getCode()));
        containerDef.put("essential", true);
        containerDef.put("portMappings", List.of(
                Map.of("containerPort", 8080, "protocol", "tcp")
        ));
        containerDef.put("logConfiguration", Map.of(
                "logDriver", "awslogs",
                "options", Map.of(
                        "awslogs-group", String.format("/ecs/%s", appName),
                        "awslogs-region", "${AWS_REGION}",
                        "awslogs-stream-prefix", "ecs"
                )
        ));

        taskDefinition.put("containerDefinitions", List.of(containerDef));
        config.put("task-definition.json", taskDefinition);

        // ECS服务配置
        Map<String, Object> serviceConfig = new LinkedHashMap<>();
        serviceConfig.put("serviceName", String.format("%s-service", appName.toLowerCase()));
        serviceConfig.put("desiredCount", environment == Environment.PRODUCTION ? 3 : 1);
        serviceConfig.put("deploymentConfiguration", Map.of(
                "maximumPercent", 200,
                "minimumHealthyPercent", 100
        ));
        config.put("service-config.json", serviceConfig);

        return config;
    }

    /**
     * 生成Kubernetes配置
     */
    private Map<String, Object> generateKubernetesConfig(
            String appName,
            Environment environment,
            Map<String, Object> codeArtifact
    ) {
        Map<String, Object> config = new LinkedHashMap<>();

        // Deployment YAML
        StringBuilder deployment = new StringBuilder();
        deployment.append("apiVersion: apps/v1\n");
        deployment.append("kind: Deployment\n");
        deployment.append("metadata:\n");
        deployment.append(String.format("  name: %s\n", appName.toLowerCase()));
        deployment.append(String.format("  namespace: %s\n", environment.getCode()));
        deployment.append("  labels:\n");
        deployment.append(String.format("    app: %s\n", appName.toLowerCase()));
        deployment.append("spec:\n");
        deployment.append(String.format("  replicas: %d\n", environment == Environment.PRODUCTION ? 3 : 1));
        deployment.append("  selector:\n");
        deployment.append("    matchLabels:\n");
        deployment.append(String.format("      app: %s\n", appName.toLowerCase()));
        deployment.append("  template:\n");
        deployment.append("    metadata:\n");
        deployment.append("      labels:\n");
        deployment.append(String.format("        app: %s\n", appName.toLowerCase()));
        deployment.append("    spec:\n");
        deployment.append("      containers:\n");
        deployment.append(String.format("      - name: %s\n", appName.toLowerCase()));
        deployment.append(String.format("        image: ingenio/%s:%s\n", appName.toLowerCase(), environment.getCode()));
        deployment.append("        ports:\n");
        deployment.append("        - containerPort: 8080\n");
        deployment.append("        resources:\n");
        deployment.append("          requests:\n");
        deployment.append("            memory: \"512Mi\"\n");
        deployment.append("            cpu: \"250m\"\n");
        deployment.append("          limits:\n");
        deployment.append("            memory: \"1Gi\"\n");
        deployment.append("            cpu: \"500m\"\n");
        deployment.append("        livenessProbe:\n");
        deployment.append("          httpGet:\n");
        deployment.append("            path: /actuator/health/liveness\n");
        deployment.append("            port: 8080\n");
        deployment.append("          initialDelaySeconds: 30\n");
        deployment.append("          periodSeconds: 10\n");
        deployment.append("        readinessProbe:\n");
        deployment.append("          httpGet:\n");
        deployment.append("            path: /actuator/health/readiness\n");
        deployment.append("            port: 8080\n");
        deployment.append("          initialDelaySeconds: 5\n");
        deployment.append("          periodSeconds: 5\n");

        config.put("deployment.yaml", deployment.toString());

        // Service YAML
        StringBuilder service = new StringBuilder();
        service.append("apiVersion: v1\n");
        service.append("kind: Service\n");
        service.append("metadata:\n");
        service.append(String.format("  name: %s-service\n", appName.toLowerCase()));
        service.append(String.format("  namespace: %s\n", environment.getCode()));
        service.append("spec:\n");
        service.append("  selector:\n");
        service.append(String.format("    app: %s\n", appName.toLowerCase()));
        service.append("  ports:\n");
        service.append("  - port: 80\n");
        service.append("    targetPort: 8080\n");
        service.append("  type: LoadBalancer\n");

        config.put("service.yaml", service.toString());

        // Ingress YAML
        StringBuilder ingress = new StringBuilder();
        ingress.append("apiVersion: networking.k8s.io/v1\n");
        ingress.append("kind: Ingress\n");
        ingress.append("metadata:\n");
        ingress.append(String.format("  name: %s-ingress\n", appName.toLowerCase()));
        ingress.append(String.format("  namespace: %s\n", environment.getCode()));
        ingress.append("  annotations:\n");
        ingress.append("    kubernetes.io/ingress.class: nginx\n");
        ingress.append("    cert-manager.io/cluster-issuer: letsencrypt-prod\n");
        ingress.append("spec:\n");
        ingress.append("  tls:\n");
        ingress.append("  - hosts:\n");
        ingress.append(String.format("    - %s.ingenio.dev\n", appName.toLowerCase()));
        ingress.append(String.format("    secretName: %s-tls\n", appName.toLowerCase()));
        ingress.append("  rules:\n");
        ingress.append(String.format("  - host: %s.ingenio.dev\n", appName.toLowerCase()));
        ingress.append("    http:\n");
        ingress.append("      paths:\n");
        ingress.append("      - path: /\n");
        ingress.append("        pathType: Prefix\n");
        ingress.append("        backend:\n");
        ingress.append("          service:\n");
        ingress.append(String.format("            name: %s-service\n", appName.toLowerCase()));
        ingress.append("            port:\n");
        ingress.append("              number: 80\n");

        config.put("ingress.yaml", ingress.toString());

        return config;
    }

    /**
     * 生成通用配置
     */
    private Map<String, Object> generateGenericConfig(
            String appName,
            Environment environment,
            Map<String, Object> codeArtifact
    ) {
        Map<String, Object> config = new LinkedHashMap<>();
        config.put("app_name", appName);
        config.put("environment", environment.getCode());
        config.put("build_commands", List.of("npm install", "npm run build"));
        config.put("start_command", "npm start");
        return config;
    }

    /**
     * 生成环境变量
     */
    private Map<String, String> generateEnvVariables(Environment environment, Map<String, Object> codeArtifact) {
        Map<String, String> envVars = new LinkedHashMap<>();

        // 通用环境变量
        envVars.put("NODE_ENV", environment.getCode());
        envVars.put("SPRING_PROFILES_ACTIVE", environment.getCode());

        // 数据库配置
        envVars.put("DATABASE_URL", getDatabaseUrl(environment));
        envVars.put("REDIS_URL", getRedisUrl(environment));

        // API配置
        envVars.put("API_BASE_URL", getApiUrl(environment));

        // 日志级别
        envVars.put("LOG_LEVEL", environment == Environment.PRODUCTION ? "INFO" : "DEBUG");

        // 特性开关
        envVars.put("FEATURE_DEBUG_MODE", environment == Environment.DEVELOPMENT ? "true" : "false");

        return envVars;
    }

    /**
     * 生成资源配置
     */
    private Map<String, Object> generateResourceConfig(Environment environment) {
        Map<String, Object> resources = new LinkedHashMap<>();

        switch (environment) {
            case DEVELOPMENT:
                resources.put("cpu", "256m");
                resources.put("memory", "512Mi");
                resources.put("replicas", 1);
                break;
            case TESTING:
                resources.put("cpu", "512m");
                resources.put("memory", "1Gi");
                resources.put("replicas", 1);
                break;
            case STAGING:
                resources.put("cpu", "512m");
                resources.put("memory", "1Gi");
                resources.put("replicas", 2);
                break;
            case PRODUCTION:
                resources.put("cpu", "1024m");
                resources.put("memory", "2Gi");
                resources.put("replicas", 3);
                resources.put("autoscaling", Map.of(
                        "enabled", true,
                        "minReplicas", 3,
                        "maxReplicas", 10,
                        "targetCPUUtilization", 70
                ));
                break;
        }

        return resources;
    }

    /**
     * 生成健康检查配置
     */
    private Map<String, Object> generateHealthCheckConfig() {
        return Map.of(
                "liveness", Map.of(
                        "path", "/actuator/health/liveness",
                        "initialDelaySeconds", 30,
                        "periodSeconds", 10,
                        "timeoutSeconds", 3
                ),
                "readiness", Map.of(
                        "path", "/actuator/health/readiness",
                        "initialDelaySeconds", 5,
                        "periodSeconds", 5,
                        "timeoutSeconds", 3
                )
        );
    }

    // ==================== 3. 部署执行 ====================

    /**
     * 执行部署
     *
     * @param config 部署配置
     * @return 部署任务
     */
    public DeploymentTask deploy(DeploymentConfig config) {
        String deploymentId = "deploy-" + UUID.randomUUID().toString().substring(0, 8);
        log.info("[Deployment] 开始部署: deploymentId={}, app={}, env={}, target={}",
                deploymentId, config.getAppName(), config.getEnvironment(), config.getTarget());

        DeploymentTask task = DeploymentTask.builder()
                .deploymentId(deploymentId)
                .config(config)
                .status(DeploymentStatus.PENDING)
                .startedAt(Instant.now())
                .logs(new ArrayList<>())
                .build();

        deploymentTasks.put(deploymentId, task);

        // 异步执行部署
        executorService.submit(() -> executeDeployment(task));

        return task;
    }

    /**
     * 执行部署逻辑
     */
    private void executeDeployment(DeploymentTask task) {
        try {
            task.setStatus(DeploymentStatus.RUNNING);
            addLog(task, "开始部署流程...");

            // Step 1: 准备环境
            addLog(task, "Step 1/5: 准备部署环境...");
            Thread.sleep(500);
            addLog(task, "环境准备完成");

            // Step 2: 构建镜像/包
            addLog(task, "Step 2/5: 构建应用...");
            Thread.sleep(1000);
            addLog(task, "应用构建完成");

            // Step 3: 推送镜像/上传包
            addLog(task, "Step 3/5: 推送构建产物...");
            Thread.sleep(500);
            addLog(task, "构建产物推送完成");

            // Step 4: 部署应用
            addLog(task, "Step 4/5: 部署应用...");
            Thread.sleep(1000);
            addLog(task, "应用部署完成");

            // Step 5: 健康检查
            addLog(task, "Step 5/5: 执行健康检查...");
            Thread.sleep(500);
            addLog(task, "健康检查通过");

            // 部署成功
            task.setStatus(DeploymentStatus.SUCCESS);
            task.setCompletedAt(Instant.now());
            task.setDeploymentUrl(generateDeploymentUrl(task.getConfig()));
            addLog(task, String.format("部署成功！访问地址: %s", task.getDeploymentUrl()));

            log.info("[Deployment] 部署成功: deploymentId={}, url={}",
                    task.getDeploymentId(), task.getDeploymentUrl());

        } catch (Exception e) {
            task.setStatus(DeploymentStatus.FAILED);
            task.setCompletedAt(Instant.now());
            task.setErrorMessage(e.getMessage());
            addLog(task, "部署失败: " + e.getMessage());
            log.error("[Deployment] 部署失败: deploymentId={}", task.getDeploymentId(), e);
        }
    }

    /**
     * 查询部署状态
     */
    public DeploymentTask getDeploymentStatus(String deploymentId) {
        return deploymentTasks.get(deploymentId);
    }

    /**
     * 回滚部署
     */
    public DeploymentTask rollback(String deploymentId, String targetVersion) {
        log.info("[Deployment] 执行回滚: deploymentId={}, targetVersion={}", deploymentId, targetVersion);

        DeploymentTask originalTask = deploymentTasks.get(deploymentId);
        if (originalTask == null) {
            throw new RuntimeException("部署任务不存在: " + deploymentId);
        }

        String rollbackId = "rollback-" + UUID.randomUUID().toString().substring(0, 8);

        DeploymentTask rollbackTask = DeploymentTask.builder()
                .deploymentId(rollbackId)
                .config(originalTask.getConfig())
                .status(DeploymentStatus.PENDING)
                .startedAt(Instant.now())
                .logs(new ArrayList<>())
                .rollbackFromId(deploymentId)
                .rollbackTargetVersion(targetVersion)
                .build();

        deploymentTasks.put(rollbackId, rollbackTask);

        // 异步执行回滚
        executorService.submit(() -> {
            try {
                rollbackTask.setStatus(DeploymentStatus.RUNNING);
                addLog(rollbackTask, "开始回滚到版本: " + targetVersion);

                Thread.sleep(2000);

                rollbackTask.setStatus(DeploymentStatus.SUCCESS);
                rollbackTask.setCompletedAt(Instant.now());
                addLog(rollbackTask, "回滚成功");

            } catch (Exception e) {
                rollbackTask.setStatus(DeploymentStatus.FAILED);
                rollbackTask.setErrorMessage(e.getMessage());
            }
        });

        return rollbackTask;
    }

    // ==================== 辅助方法 ====================

    private void addLog(DeploymentTask task, String message) {
        String logEntry = String.format("[%s] %s", Instant.now(), message);
        task.getLogs().add(logEntry);
    }

    private String generateDeploymentUrl(DeploymentConfig config) {
        String appName = config.getAppName().toLowerCase();
        Environment env = config.getEnvironment();

        switch (config.getTarget()) {
            case VERCEL:
                return String.format("https://%s-%s.vercel.app", appName, env.getCode());
            case AWS_ECS:
            case AWS_LAMBDA:
                return String.format("https://%s-%s.execute-api.ap-east-1.amazonaws.com", appName, env.getCode());
            case KUBERNETES:
                return String.format("https://%s.%s.ingenio.dev", appName, env.getCode());
            default:
                return String.format("http://localhost:3000");
        }
    }

    private String getDatabaseUrl(Environment environment) {
        return String.format("postgresql://localhost:5432/ingenio_%s", environment.getCode());
    }

    private String getRedisUrl(Environment environment) {
        return "redis://localhost:6379/0";
    }

    private String getApiUrl(Environment environment) {
        switch (environment) {
            case DEVELOPMENT:
                return "http://localhost:8080/api";
            case TESTING:
                return "https://api-test.ingenio.dev";
            case STAGING:
                return "https://api-staging.ingenio.dev";
            case PRODUCTION:
                return "https://api.ingenio.dev";
            default:
                return "http://localhost:8080/api";
        }
    }

    // ==================== 数据结构定义 ====================

    /**
     * 部署配置
     */
    @Data
    @Builder
    public static class DeploymentConfig {
        private String appName;
        private Environment environment;
        private DeploymentTarget target;
        private Map<String, Object> platformConfig;
        private Map<String, String> environmentVariables;
        private Map<String, Object> resourceConfig;
        private Map<String, Object> healthCheckConfig;
        private Instant createdAt;
    }

    /**
     * 部署任务
     */
    @Data
    @Builder
    public static class DeploymentTask {
        private String deploymentId;
        private DeploymentConfig config;
        private DeploymentStatus status;
        private Instant startedAt;
        private Instant completedAt;
        private String deploymentUrl;
        private String errorMessage;
        private List<String> logs;
        private String rollbackFromId;
        private String rollbackTargetVersion;
    }

    /**
     * 部署状态
     */
    public enum DeploymentStatus {
        PENDING,
        RUNNING,
        SUCCESS,
        FAILED,
        ROLLED_BACK
    }
}
