# Ingenio Backend Docker 部署指南

本文档详细介绍如何使用Docker和Docker Compose部署Ingenio后端服务。

## 目录

- [1. Dockerfile解析](#1-dockerfile解析)
- [2. Docker Compose配置](#2-docker-compose配置)
- [3. 镜像构建](#3-镜像构建)
- [4. 容器运行](#4-容器运行)
- [5. 网络配置](#5-网络配置)
- [6. 存储管理](#6-存储管理)
- [7. 日志收集](#7-日志收集)
- [8. 性能优化](#8-性能优化)
- [9. 故障排查](#9-故障排查)
- [10. 生产实践](#10-生产实践)

---

## 1. Dockerfile解析

### 1.1 多阶段构建架构

当前Dockerfile采用**两阶段构建**策略，优化镜像大小和安全性：

```dockerfile
# Stage 1: 构建阶段
FROM maven:3.9-eclipse-temurin-17-alpine AS builder

# Stage 2: 运行阶段
FROM eclipse-temurin:17-jre-alpine
```

### 1.2 Stage 1: 构建阶段详解

#### 基础镜像选择

```dockerfile
FROM maven:3.9-eclipse-temurin-17-alpine AS builder
```

**镜像组成**:
- **Maven 3.9**: 构建工具
- **Eclipse Temurin 17**: OpenJDK发行版
- **Alpine Linux**: 轻量级Linux发行版（~5MB）

#### 构建流程

```dockerfile
# 设置工作目录
WORKDIR /build

# 复制pom.xml和源代码
COPY pom.xml .
COPY src ./src

# 下载依赖并构建（跳过测试以加快构建速度）
RUN mvn clean package -DskipTests
```

**关键点**:
1. **分层复制**: 先复制`pom.xml`，利用Docker层缓存机制
2. **跳过测试**: `-DskipTests`加快构建速度
3. **工作目录**: 所有构建产物在`/build`目录

#### 优化建议

```dockerfile
# 优化版本 - 利用Maven依赖缓存
FROM maven:3.9-eclipse-temurin-17-alpine AS builder
WORKDIR /build

# 仅复制pom.xml并下载依赖（独立缓存层）
COPY pom.xml .
RUN mvn dependency:go-offline -B

# 复制源代码并构建
COPY src ./src
RUN mvn clean package -DskipTests -o
```

**优势**:
- 依赖下载独立为一层，修改代码不会重新下载依赖
- `-o`（离线模式）加快构建速度

### 1.3 Stage 2: 运行阶段详解

#### 基础镜像选择

```dockerfile
FROM eclipse-temurin:17-jre-alpine
```

**为何选择JRE而非JDK**:
- JRE镜像大小：~150MB
- JDK镜像大小：~320MB
- 运行时不需要编译工具，JRE即可

#### 系统依赖安装

```dockerfile
# 安装必要的工具
RUN apk add --no-cache curl bash
```

**工具用途**:
- **curl**: 健康检查
- **bash**: Shell脚本支持

#### 安全加固

```dockerfile
# 创建应用用户
RUN addgroup -S ingenio && adduser -S ingenio -G ingenio

# 修改所有权
RUN chown -R ingenio:ingenio /app

# 切换到应用用户
USER ingenio
```

**安全原则**:
- ✅ 不使用root用户运行应用
- ✅ 限制文件系统访问权限
- ✅ 遵循最小权限原则

#### 应用部署

```dockerfile
# 设置工作目录
WORKDIR /app

# 从构建阶段复制jar包
COPY --from=builder /build/target/*.jar app.jar

# 暴露端口
EXPOSE 8080
```

#### 健康检查

```dockerfile
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
  CMD curl -f http://localhost:8080/actuator/health || exit 1
```

**参数说明**:
- `--interval=30s`: 每30秒检查一次
- `--timeout=10s`: 单次检查超时10秒
- `--start-period=60s`: 启动容忍期60秒
- `--retries=3`: 连续3次失败标记为不健康

#### 启动命令

```dockerfile
ENTRYPOINT ["java", \
  "-Djava.security.egd=file:/dev/./urandom", \
  "-Dspring.profiles.active=docker", \
  "-jar", \
  "app.jar"]
```

**参数解析**:
- `-Djava.security.egd=file:/dev/./urandom`: 使用非阻塞随机数生成器
- `-Dspring.profiles.active=docker`: 激活docker配置文件

### 1.4 完整优化版Dockerfile

```dockerfile
# ===================================
# Stage 1: 构建阶段
# ===================================
FROM maven:3.9-eclipse-temurin-17-alpine AS builder

# 设置工作目录
WORKDIR /build

# 仅复制依赖文件并下载依赖（利用Docker层缓存）
COPY pom.xml .
RUN mvn dependency:go-offline -B

# 复制源代码
COPY src ./src

# 编译打包（离线模式）
RUN mvn clean package -DskipTests -o

# ===================================
# Stage 2: 运行阶段
# ===================================
FROM eclipse-temurin:17-jre-alpine

# 安装运行时依赖
RUN apk add --no-cache \
    curl \
    bash \
    tzdata && \
    cp /usr/share/zoneinfo/Asia/Shanghai /etc/localtime && \
    echo "Asia/Shanghai" > /etc/timezone && \
    apk del tzdata

# 创建应用用户和目录
RUN addgroup -S ingenio && \
    adduser -S ingenio -G ingenio && \
    mkdir -p /app/logs && \
    chown -R ingenio:ingenio /app

# 切换工作目录和用户
WORKDIR /app
USER ingenio

# 从构建阶段复制JAR包
COPY --from=builder --chown=ingenio:ingenio /build/target/*.jar app.jar

# 暴露端口
EXPOSE 8080

# 健康检查
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
  CMD curl -f http://localhost:8080/actuator/health || exit 1

# JVM参数优化
ENV JAVA_OPTS="-Xms512m -Xmx2g -XX:+UseG1GC -XX:MaxGCPauseMillis=200"

# 启动命令
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -Djava.security.egd=file:/dev/./urandom -Dspring.profiles.active=docker -jar app.jar"]
```

---

## 2. Docker Compose配置

### 2.1 完整的docker-compose.yml

```yaml
version: '3.8'

services:
  # ===================================
  # PostgreSQL数据库
  # ===================================
  postgres:
    image: postgres:15-alpine
    container_name: ingenio-postgres
    restart: unless-stopped
    environment:
      POSTGRES_DB: ingenio_prod
      POSTGRES_USER: ingenio_user
      POSTGRES_PASSWORD: ${DB_PASSWORD}
      PGDATA: /var/lib/postgresql/data/pgdata
    volumes:
      - postgres_data:/var/lib/postgresql/data
      - ./migrations:/docker-entrypoint-initdb.d:ro
    ports:
      - "5432:5432"
    networks:
      - ingenio-network
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U ingenio_user -d ingenio_prod"]
      interval: 10s
      timeout: 5s
      retries: 5

  # ===================================
  # Redis缓存
  # ===================================
  redis:
    image: redis:7-alpine
    container_name: ingenio-redis
    restart: unless-stopped
    command: redis-server --requirepass ${REDIS_PASSWORD} --appendonly yes
    volumes:
      - redis_data:/data
    ports:
      - "6379:6379"
    networks:
      - ingenio-network
    healthcheck:
      test: ["CMD", "redis-cli", "--raw", "incr", "ping"]
      interval: 10s
      timeout: 3s
      retries: 5

  # ===================================
  # MinIO对象存储
  # ===================================
  minio:
    image: minio/minio:latest
    container_name: ingenio-minio
    restart: unless-stopped
    environment:
      MINIO_ROOT_USER: ${MINIO_ACCESS_KEY}
      MINIO_ROOT_PASSWORD: ${MINIO_SECRET_KEY}
    volumes:
      - minio_data:/data
    ports:
      - "9000:9000"
      - "9001:9001"
    networks:
      - ingenio-network
    command: server /data --console-address ":9001"
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:9000/minio/health/live"]
      interval: 30s
      timeout: 20s
      retries: 3

  # ===================================
  # Ingenio Backend应用
  # ===================================
  backend:
    build:
      context: .
      dockerfile: Dockerfile
    image: ingenio-backend:latest
    container_name: ingenio-backend
    restart: unless-stopped
    depends_on:
      postgres:
        condition: service_healthy
      redis:
        condition: service_healthy
      minio:
        condition: service_healthy
    environment:
      # Spring配置
      SPRING_PROFILES_ACTIVE: docker

      # 数据库配置
      DB_HOST: postgres
      DB_PORT: 5432
      DB_NAME: ingenio_prod
      DB_USER: ingenio_user
      DB_PASSWORD: ${DB_PASSWORD}

      # Redis配置
      REDIS_HOST: redis
      REDIS_PORT: 6379
      REDIS_PASSWORD: ${REDIS_PASSWORD}

      # MinIO配置
      MINIO_ENDPOINT: http://minio:9000
      MINIO_ACCESS_KEY: ${MINIO_ACCESS_KEY}
      MINIO_SECRET_KEY: ${MINIO_SECRET_KEY}
      MINIO_BUCKET: ingenio

      # JWT配置
      JWT_SECRET: ${JWT_SECRET}

      # DeepSeek API
      DEEPSEEK_API_KEY: ${DEEPSEEK_API_KEY}

      # JVM参数
      JAVA_OPTS: "-Xms1g -Xmx2g -XX:+UseG1GC"
    ports:
      - "8080:8080"
    volumes:
      - backend_logs:/app/logs
    networks:
      - ingenio-network
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8080/actuator/health"]
      interval: 30s
      timeout: 10s
      retries: 3
      start_period: 60s

# ===================================
# 网络定义
# ===================================
networks:
  ingenio-network:
    driver: bridge
    ipam:
      config:
        - subnet: 172.20.0.0/16

# ===================================
# 数据卷定义
# ===================================
volumes:
  postgres_data:
    driver: local
  redis_data:
    driver: local
  minio_data:
    driver: local
  backend_logs:
    driver: local
```

### 2.2 环境变量配置

创建 `.env` 文件：

```bash
# 数据库密码
DB_PASSWORD=strong-postgres-password

# Redis密码
REDIS_PASSWORD=strong-redis-password

# MinIO密钥
MINIO_ACCESS_KEY=minio-access-key
MINIO_SECRET_KEY=minio-secret-key-at-least-8-chars

# JWT密钥
JWT_SECRET=your-jwt-secret-key-change-in-production

# DeepSeek API Key
DEEPSEEK_API_KEY=sk-your-deepseek-api-key
```

### 2.3 应用配置文件

创建 `src/main/resources/application-docker.yml`:

```yaml
spring:
  datasource:
    url: jdbc:postgresql://${DB_HOST}:${DB_PORT}/${DB_NAME}
    username: ${DB_USER}
    password: ${DB_PASSWORD}
    hikari:
      maximum-pool-size: 30
      minimum-idle: 5

  redis:
    host: ${REDIS_HOST}
    port: ${REDIS_PORT}
    password: ${REDIS_PASSWORD}

  ai:
    openai:
      api-key: ${DEEPSEEK_API_KEY}
      base-url: https://api.deepseek.com

minio:
  endpoint: ${MINIO_ENDPOINT}
  access-key: ${MINIO_ACCESS_KEY}
  secret-key: ${MINIO_SECRET_KEY}

logging:
  level:
    root: INFO
    com.ingenio.backend: DEBUG
```

---

## 3. 镜像构建

### 3.1 本地构建

#### 基础构建

```bash
# 构建镜像
docker build -t ingenio-backend:latest .

# 构建时指定参数
docker build \
  --build-arg MAVEN_OPTS="-Xmx1024m" \
  --tag ingenio-backend:v1.0.0 \
  --file Dockerfile \
  .
```

#### 查看构建历史

```bash
# 查看镜像层
docker history ingenio-backend:latest

# 查看镜像详情
docker inspect ingenio-backend:latest
```

### 3.2 多平台构建

```bash
# 创建buildx构建器
docker buildx create --name multiplatform --use

# 构建多平台镜像
docker buildx build \
  --platform linux/amd64,linux/arm64 \
  --tag ingenio-backend:latest \
  --push \
  .
```

### 3.3 构建优化

#### 使用.dockerignore

创建 `.dockerignore`:

```
# Maven构建产物
target/
*.jar
*.war

# IDE配置
.idea/
.vscode/
*.iml

# Git
.git/
.gitignore

# 日志和临时文件
logs/
*.log
*.tmp

# 测试报告
test-results/
coverage/

# Docker相关
Dockerfile*
docker-compose*.yml
.dockerignore

# 文档
docs/
README.md
```

#### 缓存优化策略

```bash
# 使用BuildKit缓存
DOCKER_BUILDKIT=1 docker build \
  --cache-from ingenio-backend:latest \
  --tag ingenio-backend:v1.0.1 \
  .

# 使用内联缓存
docker build \
  --build-arg BUILDKIT_INLINE_CACHE=1 \
  --tag ingenio-backend:latest \
  .
```

### 3.4 推送到镜像仓库

#### Docker Hub

```bash
# 登录Docker Hub
docker login

# 标记镜像
docker tag ingenio-backend:latest ingenio/ingenio-backend:v1.0.0

# 推送镜像
docker push ingenio/ingenio-backend:v1.0.0
```

#### 私有镜像仓库

```bash
# 标记镜像
docker tag ingenio-backend:latest registry.company.com/ingenio/backend:v1.0.0

# 推送镜像
docker push registry.company.com/ingenio/backend:v1.0.0
```

#### AWS ECR

```bash
# 登录ECR
aws ecr get-login-password --region us-west-2 | \
  docker login --username AWS --password-stdin 123456789012.dkr.ecr.us-west-2.amazonaws.com

# 标记并推送
docker tag ingenio-backend:latest 123456789012.dkr.ecr.us-west-2.amazonaws.com/ingenio-backend:v1.0.0
docker push 123456789012.dkr.ecr.us-west-2.amazonaws.com/ingenio-backend:v1.0.0
```

---

## 4. 容器运行

### 4.1 使用Docker Compose

#### 启动所有服务

```bash
# 后台启动
docker-compose up -d

# 查看日志
docker-compose logs -f

# 仅启动特定服务
docker-compose up -d postgres redis
```

#### 服务管理命令

```bash
# 停止服务
docker-compose stop

# 停止并删除容器
docker-compose down

# 重启服务
docker-compose restart backend

# 查看服务状态
docker-compose ps

# 查看资源使用
docker-compose top
```

### 4.2 单容器运行

#### 启动Backend容器

```bash
docker run -d \
  --name ingenio-backend \
  --restart unless-stopped \
  -p 8080:8080 \
  -e SPRING_PROFILES_ACTIVE=prod \
  -e DB_HOST=host.docker.internal \
  -e DB_PORT=5432 \
  -e DB_NAME=ingenio_prod \
  -e DB_USER=ingenio_user \
  -e DB_PASSWORD=password \
  -e REDIS_HOST=host.docker.internal \
  -e REDIS_PORT=6379 \
  -e DEEPSEEK_API_KEY=sk-xxx \
  -v /var/logs/ingenio:/app/logs \
  ingenio-backend:latest
```

### 4.3 容器生命周期管理

#### 查看容器状态

```bash
# 查看运行中的容器
docker ps

# 查看所有容器
docker ps -a

# 查看容器详情
docker inspect ingenio-backend

# 查看容器资源使用
docker stats ingenio-backend
```

#### 容器操作

```bash
# 启动容器
docker start ingenio-backend

# 停止容器
docker stop ingenio-backend

# 重启容器
docker restart ingenio-backend

# 删除容器
docker rm ingenio-backend

# 强制删除运行中的容器
docker rm -f ingenio-backend
```

#### 进入容器调试

```bash
# 进入容器Shell
docker exec -it ingenio-backend sh

# 执行单条命令
docker exec ingenio-backend curl http://localhost:8080/actuator/health

# 查看容器日志
docker logs -f ingenio-backend

# 查看最近100行日志
docker logs --tail 100 ingenio-backend
```

---

## 5. 网络配置

### 5.1 Docker网络模式

#### Bridge网络（默认）

```yaml
networks:
  ingenio-network:
    driver: bridge
    ipam:
      config:
        - subnet: 172.20.0.0/16
          gateway: 172.20.0.1
```

#### Host网络（性能最佳）

```yaml
services:
  backend:
    network_mode: host
```

**注意**: Host模式下端口映射失效，直接使用宿主机端口。

### 5.2 服务间通信

#### 通过服务名访问

```yaml
# Backend访问PostgreSQL
spring:
  datasource:
    url: jdbc:postgresql://postgres:5432/ingenio_prod
```

Docker Compose会自动解析服务名为容器IP。

#### 自定义DNS

```yaml
services:
  backend:
    dns:
      - 8.8.8.8
      - 8.8.4.4
    extra_hosts:
      - "api.example.com:192.168.1.100"
```

### 5.3 端口映射

#### 基础映射

```yaml
ports:
  - "8080:8080"       # 宿主机:容器
  - "127.0.0.1:8080:8080"  # 仅本地访问
```

#### 动态端口

```yaml
ports:
  - "8080"  # Docker自动分配宿主机端口
```

查看映射端口:
```bash
docker port ingenio-backend
```

---

## 6. 存储管理

### 6.1 数据卷类型

#### Named Volume（推荐）

```yaml
volumes:
  postgres_data:
    driver: local
    driver_opts:
      type: none
      device: /mnt/data/postgres
      o: bind
```

#### Bind Mount

```yaml
services:
  backend:
    volumes:
      - ./logs:/app/logs
      - ./config:/app/config:ro  # 只读
```

#### tmpfs（临时内存存储）

```yaml
services:
  backend:
    tmpfs:
      - /tmp
      - /app/temp:size=100M
```

### 6.2 数据备份

#### 备份PostgreSQL数据卷

```bash
# 创建备份
docker run --rm \
  -v ingenio_postgres_data:/data \
  -v $(pwd):/backup \
  alpine tar czf /backup/postgres_backup_$(date +%Y%m%d).tar.gz /data

# 恢复备份
docker run --rm \
  -v ingenio_postgres_data:/data \
  -v $(pwd):/backup \
  alpine sh -c "cd /data && tar xzf /backup/postgres_backup_20250115.tar.gz --strip 1"
```

#### 备份应用日志

```bash
# 定时备份日志
docker run --rm \
  -v ingenio_backend_logs:/logs \
  -v /backup:/backup \
  alpine tar czf /backup/logs_$(date +%Y%m%d).tar.gz /logs
```

### 6.3 清理和维护

#### 清理未使用的卷

```bash
# 查看所有卷
docker volume ls

# 删除未使用的卷
docker volume prune

# 删除特定卷
docker volume rm ingenio_postgres_data
```

#### 磁盘空间管理

```bash
# 查看Docker磁盘使用
docker system df

# 清理所有未使用资源
docker system prune -a --volumes

# 仅清理停止的容器和未使用的镜像
docker system prune
```

---

## 7. 日志收集

### 7.1 日志驱动配置

#### JSON日志驱动（默认）

```yaml
services:
  backend:
    logging:
      driver: json-file
      options:
        max-size: "100m"
        max-file: "10"
        labels: "app,environment"
```

#### Syslog驱动

```yaml
services:
  backend:
    logging:
      driver: syslog
      options:
        syslog-address: "tcp://192.168.1.100:514"
        tag: "ingenio-backend"
```

#### Fluentd驱动

```yaml
services:
  backend:
    logging:
      driver: fluentd
      options:
        fluentd-address: localhost:24224
        tag: ingenio.backend
```

### 7.2 集中日志管理

#### ELK Stack集成

添加到docker-compose.yml:

```yaml
services:
  elasticsearch:
    image: elasticsearch:8.10.0
    environment:
      - discovery.type=single-node
      - xpack.security.enabled=false
    volumes:
      - es_data:/usr/share/elasticsearch/data
    ports:
      - "9200:9200"

  logstash:
    image: logstash:8.10.0
    volumes:
      - ./logstash.conf:/usr/share/logstash/pipeline/logstash.conf
    depends_on:
      - elasticsearch

  kibana:
    image: kibana:8.10.0
    ports:
      - "5601:5601"
    depends_on:
      - elasticsearch

  backend:
    logging:
      driver: gelf
      options:
        gelf-address: "udp://localhost:12201"
        tag: "ingenio-backend"
```

### 7.3 日志查看命令

```bash
# 实时日志
docker-compose logs -f backend

# 最近100行日志
docker-compose logs --tail 100 backend

# 带时间戳的日志
docker-compose logs -t backend

# 从特定时间开始的日志
docker logs --since 2025-01-15T10:00:00 ingenio-backend

# 查看容器内日志文件
docker exec ingenio-backend tail -f /app/logs/ingenio-backend.log
```

---

## 8. 性能优化

### 8.1 JVM内存调优

#### 设置堆内存

```yaml
services:
  backend:
    environment:
      JAVA_OPTS: >-
        -Xms2g
        -Xmx4g
        -XX:+UseG1GC
        -XX:MaxGCPauseMillis=200
        -XX:ParallelGCThreads=4
```

#### 容器内存限制

```yaml
services:
  backend:
    deploy:
      resources:
        limits:
          memory: 4G
          cpus: '2'
        reservations:
          memory: 2G
          cpus: '1'
```

### 8.2 网络性能优化

#### 启用HTTP/2

```yaml
server:
  http2:
    enabled: true
```

#### 连接池优化

```yaml
spring:
  datasource:
    hikari:
      maximum-pool-size: 50
      minimum-idle: 10
```

### 8.3 镜像优化

#### 减小镜像大小

```dockerfile
# 使用alpine基础镜像
FROM eclipse-temurin:17-jre-alpine

# 移除不必要的文件
RUN rm -rf /var/cache/apk/*

# 使用多阶段构建
FROM builder AS final
```

#### 层缓存优化

```dockerfile
# 先复制依赖文件
COPY pom.xml .
RUN mvn dependency:go-offline

# 再复制源代码
COPY src ./src
RUN mvn package
```

### 8.4 数据库性能

#### PostgreSQL参数调优

```yaml
services:
  postgres:
    command:
      - postgres
      - -c
      - shared_buffers=256MB
      - -c
      - max_connections=200
      - -c
      - effective_cache_size=1GB
```

#### 使用连接池

```yaml
spring:
  datasource:
    hikari:
      maximum-pool-size: 50
      connection-timeout: 30000
      idle-timeout: 600000
```

---

## 9. 故障排查

### 9.1 容器无法启动

#### 症状

```bash
docker-compose up -d
# backend容器立即退出
```

#### 排查步骤

```bash
# 1. 查看容器状态
docker-compose ps

# 2. 查看容器日志
docker-compose logs backend

# 3. 查看退出代码
docker inspect --format='{{.State.ExitCode}}' ingenio-backend

# 4. 尝试交互式启动
docker-compose run --rm backend sh
```

#### 常见原因

- 环境变量缺失
- 端口被占用
- 数据库连接失败
- 内存不足

### 9.2 健康检查失败

#### 症状

```bash
docker ps
# STATUS显示 "unhealthy"
```

#### 排查步骤

```bash
# 1. 查看健康检查日志
docker inspect --format='{{json .State.Health}}' ingenio-backend | jq

# 2. 手动执行健康检查命令
docker exec ingenio-backend curl -f http://localhost:8080/actuator/health

# 3. 延长启动时间
# 修改docker-compose.yml
healthcheck:
  start_period: 120s  # 延长到2分钟
```

### 9.3 网络连接问题

#### 症状

Backend无法连接PostgreSQL/Redis

#### 排查步骤

```bash
# 1. 检查网络
docker network ls
docker network inspect ingenio_ingenio-network

# 2. Ping测试
docker exec ingenio-backend ping postgres

# 3. 端口测试
docker exec ingenio-backend nc -zv postgres 5432

# 4. DNS测试
docker exec ingenio-backend nslookup postgres
```

### 9.4 性能问题

#### 症状

容器响应慢，CPU/内存占用高

#### 排查步骤

```bash
# 1. 查看资源使用
docker stats ingenio-backend

# 2. 查看容器进程
docker top ingenio-backend

# 3. 进入容器查看JVM状态
docker exec ingenio-backend jstat -gc 1

# 4. 导出堆dump
docker exec ingenio-backend jmap -dump:live,format=b,file=/tmp/heap.hprof 1
docker cp ingenio-backend:/tmp/heap.hprof ./heap.hprof
```

---

## 10. 生产实践

### 10.1 滚动更新

#### 使用Docker Stack（Swarm模式）

```yaml
version: '3.8'

services:
  backend:
    deploy:
      replicas: 3
      update_config:
        parallelism: 1
        delay: 10s
        order: start-first
      rollback_config:
        parallelism: 1
        delay: 5s
```

#### 部署命令

```bash
# 初始部署
docker stack deploy -c docker-compose.yml ingenio

# 更新服务
docker service update --image ingenio-backend:v1.0.1 ingenio_backend

# 回滚
docker service rollback ingenio_backend
```

### 10.2 高可用部署

#### 多副本配置

```yaml
services:
  backend:
    deploy:
      replicas: 3
      placement:
        constraints:
          - node.role == worker
        preferences:
          - spread: node.labels.zone
```

#### 负载均衡

```yaml
services:
  nginx:
    image: nginx:alpine
    ports:
      - "80:80"
    volumes:
      - ./nginx.conf:/etc/nginx/nginx.conf:ro
    depends_on:
      - backend
```

nginx.conf:
```nginx
upstream backend {
    least_conn;
    server backend_1:8080;
    server backend_2:8080;
    server backend_3:8080;
}

server {
    listen 80;
    location / {
        proxy_pass http://backend;
    }
}
```

### 10.3 监控告警

#### Prometheus集成

```yaml
services:
  prometheus:
    image: prom/prometheus
    volumes:
      - ./prometheus.yml:/etc/prometheus/prometheus.yml
      - prometheus_data:/prometheus
    ports:
      - "9090:9090"

  grafana:
    image: grafana/grafana
    ports:
      - "3000:3000"
    volumes:
      - grafana_data:/var/lib/grafana
```

### 10.4 安全最佳实践

#### 使用Docker Secrets

```yaml
secrets:
  db_password:
    external: true
  jwt_secret:
    external: true

services:
  backend:
    secrets:
      - db_password
      - jwt_secret
    environment:
      DB_PASSWORD_FILE: /run/secrets/db_password
      JWT_SECRET_FILE: /run/secrets/jwt_secret
```

#### 镜像扫描

```bash
# 使用Trivy扫描镜像
trivy image ingenio-backend:latest

# 使用Docker Scout
docker scout cves ingenio-backend:latest
```

---

## 附录

### A. Docker Compose命令速查

| 命令 | 说明 |
|-----|------|
| `docker-compose up -d` | 后台启动服务 |
| `docker-compose down` | 停止并删除容器 |
| `docker-compose ps` | 查看服务状态 |
| `docker-compose logs -f` | 查看实时日志 |
| `docker-compose exec backend sh` | 进入backend容器 |
| `docker-compose restart backend` | 重启backend服务 |
| `docker-compose pull` | 拉取最新镜像 |
| `docker-compose build` | 构建镜像 |

### B. 常用诊断命令

```bash
# 查看所有容器
docker ps -a

# 查看镜像
docker images

# 查看网络
docker network ls

# 查看卷
docker volume ls

# 查看资源使用
docker stats

# 查看系统信息
docker system info

# 清理资源
docker system prune -a
```

### C. 参考资源

- [Docker官方文档](https://docs.docker.com/)
- [Docker Compose文档](https://docs.docker.com/compose/)
- [Spring Boot Docker指南](https://spring.io/guides/topicals/spring-boot-docker/)
- [Kubernetes部署指南](./KUBERNETES_GUIDE.md)

---

**文档版本**: v1.0
**最后更新**: 2025-01-15
**维护人**: Ingenio DevOps Team
