# Ingenio Backend 综合部署指南

本文档提供Ingenio AI驱动自然语言编程平台后端服务的完整部署指南。

## 目录

- [1. 部署架构概览](#1-部署架构概览)
- [2. 环境要求](#2-环境要求)
- [3. 配置管理](#3-配置管理)
- [4. 数据库迁移](#4-数据库迁移)
- [5. 部署步骤](#5-部署步骤)
- [6. 监控和日志](#6-监控和日志)
- [7. 备份和恢复](#7-备份和恢复)
- [8. 故障排查](#8-故障排查)
- [9. 性能优化](#9-性能优化)
- [10. 安全加固](#10-安全加固)

---

## 1. 部署架构概览

### 1.1 系统架构图

```
┌─────────────────────────────────────────────────────────────────┐
│                         负载均衡层                               │
│                    Nginx / AWS ALB / K8s Ingress                │
└─────────────────────────┬───────────────────────────────────────┘
                          │ HTTPS
┌─────────────────────────┴───────────────────────────────────────┐
│                      应用服务层                                  │
│  ┌────────────┐  ┌────────────┐  ┌────────────┐               │
│  │ Backend-1  │  │ Backend-2  │  │ Backend-3  │  (扩展N个)   │
│  │ Spring Boot│  │ Spring Boot│  │ Spring Boot│               │
│  │   :8080    │  │   :8080    │  │   :8080    │               │
│  └─────┬──────┘  └─────┬──────┘  └─────┬──────┘               │
└────────┼───────────────┼───────────────┼────────────────────────┘
         │               │               │
         └───────────────┴───────────────┘
                         │
         ┌───────────────┼───────────────┐
         │               │               │
┌────────▼──────┐  ┌────▼──────┐  ┌────▼──────────┐
│  PostgreSQL   │  │   Redis   │  │    MinIO      │
│   主从复制     │  │  集群模式  │  │  对象存储      │
│   (Port 5432) │  │ (Port 6379)│  │ (Port 9000)   │
└───────────────┘  └───────────┘  └───────────────┘
```

### 1.2 核心组件

| 组件 | 版本要求 | 用途 | 高可用 |
|-----|---------|------|--------|
| **Java Runtime** | 17+ | 应用运行时 | N/A |
| **Spring Boot** | 3.4.0 | Web框架 | 多实例部署 |
| **PostgreSQL** | 15+ | 主数据库 | 主从/读写分离 |
| **Redis** | 7+ | 缓存/会话 | 哨兵/集群模式 |
| **MinIO** | 8.5.7+ | 对象存储 | 分布式集群 |
| **Spring AI** | 1.0.0-M7 | AI集成 | N/A |

### 1.3 端口规划

| 服务 | 端口 | 协议 | 说明 |
|-----|------|------|------|
| Backend API | 8080 | HTTP | 应用主端口 |
| PostgreSQL | 5432 | TCP | 数据库连接 |
| Redis | 6379 | TCP | 缓存连接 |
| MinIO API | 9000 | HTTP | 对象存储API |
| MinIO Console | 9001 | HTTP | MinIO管理控制台 |
| Actuator | 8080/actuator | HTTP | 监控端点 |

---

## 2. 环境要求

### 2.1 硬件要求

#### 开发环境
- **CPU**: 2核心+
- **内存**: 4GB+
- **磁盘**: 20GB+ (SSD推荐)
- **网络**: 10Mbps+

#### 生产环境（单节点）
- **CPU**: 4核心+ (8核心推荐)
- **内存**: 8GB+ (16GB推荐)
- **磁盘**: 100GB+ SSD
- **网络**: 100Mbps+

#### 生产环境（集群）
- **应用节点**: 3+ 实例，每个4核8GB
- **数据库节点**: 主从各8核16GB
- **Redis节点**: 3+ 实例，每个2核4GB
- **MinIO节点**: 4+ 实例，每个4核8GB

### 2.2 软件要求

#### 必需软件

| 软件 | 版本 | 安装方式 |
|-----|------|---------|
| **Java JDK** | 17+ | [AdoptOpenJDK](https://adoptium.net/) |
| **Maven** | 3.9+ | `brew install maven` / `apt install maven` |
| **PostgreSQL** | 15+ | [官方文档](https://www.postgresql.org/download/) |
| **Redis** | 7+ | `brew install redis` / `apt install redis` |
| **Git** | 2.30+ | `brew install git` / `apt install git` |

#### 可选软件

| 软件 | 用途 | 安装方式 |
|-----|------|---------|
| **MinIO** | 对象存储 | [官方文档](https://min.io/docs/minio/linux/index.html) |
| **Docker** | 容器化部署 | [Docker官网](https://docs.docker.com/get-docker/) |
| **Kubernetes** | 容器编排 | [K8s官网](https://kubernetes.io/docs/setup/) |

### 2.3 网络要求

- **出站访问**: DeepSeek API (api.deepseek.com:443)
- **入站访问**: 允许客户端访问8080端口
- **内网通信**: 应用服务器与数据库/Redis/MinIO之间网络畅通

### 2.4 操作系统支持

| 操作系统 | 版本 | 测试状态 |
|---------|------|---------|
| **Ubuntu** | 20.04/22.04 LTS | ✅ 完全支持 |
| **CentOS/RHEL** | 8/9 | ✅ 完全支持 |
| **macOS** | 12+ | ✅ 开发环境 |
| **Windows** | 10/11 + WSL2 | ⚠️ 仅开发环境 |

---

## 3. 配置管理

### 3.1 环境变量配置

#### 核心环境变量

创建 `.env` 文件（从 `.env.example` 复制）：

```bash
# 数据库配置
DB_HOST=localhost
DB_PORT=5432
DB_NAME=ingenio_prod
DB_USER=ingenio_user
DB_PASSWORD=<strong-password>

# Redis配置
REDIS_HOST=localhost
REDIS_PORT=6379
REDIS_PASSWORD=<redis-password>

# DeepSeek API
DEEPSEEK_API_KEY=sk-<your-deepseek-key>

# MinIO对象存储
MINIO_ENDPOINT=http://localhost:9000
MINIO_ACCESS_KEY=<minio-access-key>
MINIO_SECRET_KEY=<minio-secret-key>
MINIO_BUCKET=ingenio

# JWT安全密钥（生产环境必须修改）
JWT_SECRET=<generate-a-strong-secret-key-here>

# 服务配置
PORT=8080
PREVIEW_BASE_URL=https://preview.ingenio.com

# Spring Profile
SPRING_PROFILES_ACTIVE=prod
```

#### 生成安全密钥

```bash
# 生成JWT密钥（32字节随机字符串）
openssl rand -base64 32

# 生成MinIO Access Key
openssl rand -hex 20

# 生成MinIO Secret Key
openssl rand -base64 40
```

### 3.2 Spring配置文件层级

配置文件优先级（从高到低）：

1. **命令行参数**: `--spring.profiles.active=prod`
2. **环境变量**: `SPRING_PROFILES_ACTIVE=prod`
3. **application-{profile}.yml**: `application-prod.yml`
4. **application.yml**: 默认配置

### 3.3 配置文件详解

#### application.yml (基础配置)

```yaml
# 数据源连接池配置
spring:
  datasource:
    hikari:
      maximum-pool-size: 20      # 最大连接数
      minimum-idle: 5            # 最小空闲连接
      connection-timeout: 30000  # 连接超时(ms)
      idle-timeout: 600000       # 空闲超时(ms)
      max-lifetime: 1800000      # 连接最大存活时间(ms)
```

#### application-prod.yml (生产配置)

```yaml
spring:
  datasource:
    hikari:
      maximum-pool-size: 50      # 生产环境增加连接池
      minimum-idle: 10

logging:
  level:
    root: WARN                   # 生产环境降低日志级别
    com.ingenio.backend: INFO

ingenio:
  rate-limit:
    enabled: true
    requests-per-minute: 30      # 生产环境限流
    ai-calls-per-hour: 50
```

### 3.4 敏感信息管理

#### 方案1: 使用环境变量（推荐）

```bash
# 在服务器设置环境变量
export DB_PASSWORD=$(cat /secure/db_password.txt)
export JWT_SECRET=$(cat /secure/jwt_secret.txt)
export DEEPSEEK_API_KEY=$(cat /secure/deepseek_key.txt)
```

#### 方案2: 使用Kubernetes Secrets

```yaml
apiVersion: v1
kind: Secret
metadata:
  name: ingenio-secrets
type: Opaque
stringData:
  db-password: <base64-encoded-password>
  jwt-secret: <base64-encoded-secret>
  deepseek-api-key: <base64-encoded-key>
```

#### 方案3: 使用AWS Secrets Manager

```java
// 使用AWS SDK获取密钥
AWSSecretsManager client = AWSSecretsManagerClientBuilder.standard()
    .withRegion(Regions.US_WEST_2)
    .build();
```

---

## 4. 数据库迁移

### 4.1 创建数据库

#### PostgreSQL初始化

```bash
# 连接到PostgreSQL
psql -U postgres

# 创建数据库用户
CREATE USER ingenio_user WITH PASSWORD 'strong-password';

# 创建数据库
CREATE DATABASE ingenio_prod
  OWNER ingenio_user
  ENCODING 'UTF8'
  LC_COLLATE = 'en_US.UTF-8'
  LC_CTYPE = 'en_US.UTF-8';

# 授予权限
GRANT ALL PRIVILEGES ON DATABASE ingenio_prod TO ingenio_user;

# 退出
\q
```

### 4.2 执行迁移脚本

#### 迁移脚本目录结构

```
backend/migrations/
├── 001_create_users_table.sql
├── 002_create_app_specs_table.sql
├── 003_create_app_spec_versions_table.sql
├── 004_create_generated_code_table.sql
├── 005_create_projects_table.sql
├── 006_create_forks_table.sql
├── 007_create_social_interactions_table.sql
├── 008_create_magic_prompts_table.sql
└── rollback/
    ├── 001_create_users_table.down.sql
    └── ...
```

#### 自动化迁移脚本

创建 `migrate.sh`:

```bash
#!/bin/bash
set -e

# 配置
DB_HOST=${DB_HOST:-localhost}
DB_PORT=${DB_PORT:-5432}
DB_NAME=${DB_NAME:-ingenio_prod}
DB_USER=${DB_USER:-ingenio_user}

MIGRATION_DIR="./migrations"
MIGRATION_LOG_TABLE="schema_migrations"

echo "开始数据库迁移..."

# 创建迁移日志表
psql -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d "$DB_NAME" <<EOF
CREATE TABLE IF NOT EXISTS $MIGRATION_LOG_TABLE (
  id SERIAL PRIMARY KEY,
  version VARCHAR(50) NOT NULL UNIQUE,
  executed_at TIMESTAMP DEFAULT NOW(),
  script_name VARCHAR(255) NOT NULL
);
EOF

# 执行所有迁移脚本
for file in $MIGRATION_DIR/*.sql; do
  # 跳过回滚脚本
  if [[ $file =~ \.down\.sql$ ]]; then
    continue
  fi

  filename=$(basename "$file")
  version=$(echo "$filename" | cut -d'_' -f1)

  # 检查是否已执行
  already_executed=$(psql -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d "$DB_NAME" -tAc \
    "SELECT COUNT(*) FROM $MIGRATION_LOG_TABLE WHERE version = '$version'")

  if [ "$already_executed" -eq 0 ]; then
    echo "执行迁移: $filename"
    psql -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d "$DB_NAME" -f "$file"

    # 记录迁移
    psql -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d "$DB_NAME" -c \
      "INSERT INTO $MIGRATION_LOG_TABLE (version, script_name) VALUES ('$version', '$filename')"

    echo "✅ $filename 迁移成功"
  else
    echo "⏭️  $filename 已执行，跳过"
  fi
done

echo "数据库迁移完成！"
```

#### 执行迁移

```bash
# 赋予执行权限
chmod +x migrate.sh

# 执行迁移
./migrate.sh
```

### 4.3 回滚策略

#### 回滚脚本

创建 `rollback.sh`:

```bash
#!/bin/bash
set -e

DB_HOST=${DB_HOST:-localhost}
DB_PORT=${DB_PORT:-5432}
DB_NAME=${DB_NAME:-ingenio_prod}
DB_USER=${DB_USER:-ingenio_user}

ROLLBACK_DIR="./migrations/rollback"
TARGET_VERSION=${1:-}

if [ -z "$TARGET_VERSION" ]; then
  echo "用法: ./rollback.sh <target_version>"
  echo "示例: ./rollback.sh 005"
  exit 1
fi

echo "回滚到版本: $TARGET_VERSION"

# 获取当前版本
current_version=$(psql -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d "$DB_NAME" -tAc \
  "SELECT MAX(version) FROM schema_migrations")

echo "当前版本: $current_version"

# 依次回滚
for file in $(ls -r $ROLLBACK_DIR/*.down.sql); do
  filename=$(basename "$file")
  version=$(echo "$filename" | cut -d'_' -f1)

  if [ "$version" -gt "$TARGET_VERSION" ] && [ "$version" -le "$current_version" ]; then
    echo "回滚: $filename"
    psql -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d "$DB_NAME" -f "$file"

    # 删除迁移记录
    psql -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d "$DB_NAME" -c \
      "DELETE FROM schema_migrations WHERE version = '$version'"

    echo "✅ $filename 回滚成功"
  fi
done

echo "回滚完成！"
```

---

## 5. 部署步骤

### 5.1 本地手动部署

#### Step 1: 克隆代码

```bash
git clone https://github.com/ingenio/ingenio-backend.git
cd ingenio-backend
```

#### Step 2: 配置环境

```bash
# 复制环境变量模板
cp .env.example .env

# 编辑配置（使用vim/nano等）
vim .env
```

#### Step 3: 编译打包

```bash
# 清理并编译
mvn clean package -DskipTests

# 或者包含测试
mvn clean package
```

#### Step 4: 执行数据库迁移

```bash
# 创建数据库
createdb ingenio_prod -O ingenio_user

# 执行迁移
./migrate.sh
```

#### Step 5: 启动应用

```bash
# 方式1: 使用Maven运行
mvn spring-boot:run -Dspring-boot.run.profiles=prod

# 方式2: 运行JAR包
java -jar target/ingenio-backend-0.1.0-SNAPSHOT.jar \
  --spring.profiles.active=prod

# 方式3: 后台运行
nohup java -jar target/ingenio-backend-0.1.0-SNAPSHOT.jar \
  --spring.profiles.active=prod > logs/app.log 2>&1 &
```

#### Step 6: 验证部署

```bash
# 健康检查
curl http://localhost:8080/api/actuator/health

# 预期输出
{"status":"UP"}

# 查看日志
tail -f logs/ingenio-backend.log
```

### 5.2 使用Systemd服务管理

#### 创建服务文件

创建 `/etc/systemd/system/ingenio-backend.service`:

```ini
[Unit]
Description=Ingenio Backend Service
After=network.target postgresql.service redis.service

[Service]
Type=simple
User=ingenio
Group=ingenio
WorkingDirectory=/opt/ingenio/backend

# 环境变量
Environment="SPRING_PROFILES_ACTIVE=prod"
EnvironmentFile=/opt/ingenio/backend/.env

# 启动命令
ExecStart=/usr/bin/java \
  -Xms2g -Xmx4g \
  -XX:+UseG1GC \
  -XX:MaxGCPauseMillis=200 \
  -Dspring.profiles.active=prod \
  -jar /opt/ingenio/backend/ingenio-backend.jar

# 重启策略
Restart=on-failure
RestartSec=10s

# 日志
StandardOutput=journal
StandardError=journal
SyslogIdentifier=ingenio-backend

[Install]
WantedBy=multi-user.target
```

#### 服务管理命令

```bash
# 重载配置
sudo systemctl daemon-reload

# 启动服务
sudo systemctl start ingenio-backend

# 设置开机自启
sudo systemctl enable ingenio-backend

# 查看状态
sudo systemctl status ingenio-backend

# 查看日志
sudo journalctl -u ingenio-backend -f

# 停止服务
sudo systemctl stop ingenio-backend

# 重启服务
sudo systemctl restart ingenio-backend
```

### 5.3 蓝绿部署

#### 架构设计

```
Nginx
  ├── Blue环境 (当前生产) - :8080
  └── Green环境 (新版本)  - :8081
```

#### 部署流程

```bash
# 1. 部署Green环境（新版本）
PORT=8081 java -jar ingenio-backend-new.jar &

# 2. 验证Green环境
curl http://localhost:8081/api/actuator/health

# 3. 切换Nginx流量到Green
sudo vim /etc/nginx/sites-enabled/ingenio
# 修改 proxy_pass http://localhost:8081

# 4. 重载Nginx
sudo nginx -s reload

# 5. 停止Blue环境（旧版本）
# 等待5分钟确保无问题后执行
pkill -f ingenio-backend-old.jar
```

---

## 6. 监控和日志

### 6.1 健康检查端点

#### Actuator端点列表

| 端点 | URL | 用途 |
|-----|-----|------|
| 健康检查 | `/actuator/health` | 服务整体健康状态 |
| 详细健康 | `/actuator/health?details=true` | 包含数据库/Redis状态 |
| 指标 | `/actuator/metrics` | JVM和应用指标 |
| Prometheus | `/actuator/prometheus` | Prometheus格式指标 |
| 日志级别 | `/actuator/loggers` | 动态调整日志级别 |

#### 健康检查示例

```bash
# 基础健康检查
curl http://localhost:8080/api/actuator/health

# 响应示例
{
  "status": "UP",
  "components": {
    "db": {
      "status": "UP",
      "details": {
        "database": "PostgreSQL",
        "validationQuery": "isValid()"
      }
    },
    "redis": {
      "status": "UP",
      "details": {
        "version": "7.0.5"
      }
    },
    "diskSpace": {
      "status": "UP",
      "details": {
        "total": 500107862016,
        "free": 345678912345,
        "threshold": 10485760
      }
    }
  }
}
```

### 6.2 日志配置

#### 日志级别管理

```yaml
# application-prod.yml
logging:
  level:
    root: WARN
    com.ingenio.backend: INFO
    com.ingenio.backend.service: DEBUG  # 业务逻辑详细日志
    org.springframework.web: WARN
    com.baomidou.mybatisplus: WARN
```

#### 日志格式

```yaml
logging:
  pattern:
    console: "%d{yyyy-MM-dd HH:mm:ss} [%thread] %-5level %logger{36} - %msg%n"
    file: "%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level %logger{50} - %msg%n"
  file:
    name: logs/ingenio-backend.log
    max-size: 100MB
    max-history: 30
    total-size-cap: 3GB
```

#### 日志切割配置（Logback）

创建 `src/main/resources/logback-spring.xml`:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<configuration>
  <property name="LOG_PATH" value="logs"/>

  <!-- 控制台输出 -->
  <appender name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
    <encoder>
      <pattern>%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n</pattern>
    </encoder>
  </appender>

  <!-- 文件输出 - 按日期和大小切割 -->
  <appender name="FILE" class="ch.qos.logback.core.rolling.RollingFileAppender">
    <file>${LOG_PATH}/ingenio-backend.log</file>
    <rollingPolicy class="ch.qos.logback.core.rolling.SizeAndTimeBasedRollingPolicy">
      <fileNamePattern>${LOG_PATH}/ingenio-backend-%d{yyyy-MM-dd}.%i.log</fileNamePattern>
      <maxFileSize>100MB</maxFileSize>
      <maxHistory>30</maxHistory>
      <totalSizeCap>3GB</totalSizeCap>
    </rollingPolicy>
    <encoder>
      <pattern>%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level %logger{50} - %msg%n</pattern>
    </encoder>
  </appender>

  <!-- 错误日志单独输出 -->
  <appender name="ERROR_FILE" class="ch.qos.logback.core.rolling.RollingFileAppender">
    <file>${LOG_PATH}/error.log</file>
    <filter class="ch.qos.logback.classic.filter.ThresholdFilter">
      <level>ERROR</level>
    </filter>
    <rollingPolicy class="ch.qos.logback.core.rolling.SizeAndTimeBasedRollingPolicy">
      <fileNamePattern>${LOG_PATH}/error-%d{yyyy-MM-dd}.%i.log</fileNamePattern>
      <maxFileSize>50MB</maxFileSize>
      <maxHistory>60</maxHistory>
    </rollingPolicy>
    <encoder>
      <pattern>%d{yyyy-MM-dd HH:mm:ss.SSS} [%thread] %-5level %logger{50} - %msg%n</pattern>
    </encoder>
  </appender>

  <root level="INFO">
    <appender-ref ref="CONSOLE"/>
    <appender-ref ref="FILE"/>
    <appender-ref ref="ERROR_FILE"/>
  </root>
</configuration>
```

### 6.3 Prometheus集成

#### 配置Prometheus

创建 `prometheus.yml`:

```yaml
global:
  scrape_interval: 15s
  evaluation_interval: 15s

scrape_configs:
  - job_name: 'ingenio-backend'
    metrics_path: '/api/actuator/prometheus'
    static_configs:
      - targets: ['localhost:8080']
        labels:
          app: 'ingenio-backend'
          env: 'prod'
```

#### 启动Prometheus

```bash
# Docker方式
docker run -d \
  --name prometheus \
  -p 9090:9090 \
  -v $(pwd)/prometheus.yml:/etc/prometheus/prometheus.yml \
  prom/prometheus
```

### 6.4 Grafana仪表板

#### 关键指标仪表板

1. **JVM内存使用**: `jvm_memory_used_bytes`
2. **HTTP请求率**: `http_server_requests_seconds_count`
3. **数据库连接池**: `hikaricp_connections_active`
4. **Redis连接**: `lettuce_command_latency_seconds`
5. **GC停顿时间**: `jvm_gc_pause_seconds`

#### 导入Grafana模板

```bash
# 使用社区JVM模板
Dashboard ID: 4701
```

---

## 7. 备份和恢复

### 7.1 PostgreSQL备份

#### 自动化备份脚本

创建 `backup-postgres.sh`:

```bash
#!/bin/bash
set -e

# 配置
BACKUP_DIR="/backup/postgres"
DB_NAME="ingenio_prod"
DB_USER="ingenio_user"
RETENTION_DAYS=7

# 创建备份目录
mkdir -p "$BACKUP_DIR"

# 备份文件名（带时间戳）
TIMESTAMP=$(date +"%Y%m%d_%H%M%S")
BACKUP_FILE="$BACKUP_DIR/${DB_NAME}_${TIMESTAMP}.sql.gz"

echo "开始备份数据库: $DB_NAME"

# 执行备份（压缩）
pg_dump -U "$DB_USER" "$DB_NAME" | gzip > "$BACKUP_FILE"

echo "✅ 备份完成: $BACKUP_FILE"

# 计算备份文件大小
BACKUP_SIZE=$(du -h "$BACKUP_FILE" | cut -f1)
echo "备份文件大小: $BACKUP_SIZE"

# 清理旧备份
find "$BACKUP_DIR" -name "${DB_NAME}_*.sql.gz" -mtime +$RETENTION_DAYS -delete
echo "已清理 ${RETENTION_DAYS} 天前的旧备份"

# 可选: 上传到S3
# aws s3 cp "$BACKUP_FILE" s3://your-backup-bucket/postgres/
```

#### 定时备份（Cron）

```bash
# 编辑crontab
crontab -e

# 每天凌晨2点执行备份
0 2 * * * /opt/ingenio/scripts/backup-postgres.sh >> /var/log/ingenio-backup.log 2>&1
```

### 7.2 数据恢复

#### 完整恢复

```bash
# 解压并恢复
gunzip < backup_20250115_020000.sql.gz | psql -U ingenio_user -d ingenio_prod

# 或使用pg_restore（自定义格式备份）
pg_restore -U ingenio_user -d ingenio_prod backup_20250115_020000.dump
```

#### 时间点恢复（PITR）

需要启用PostgreSQL WAL归档：

```bash
# postgresql.conf
wal_level = replica
archive_mode = on
archive_command = 'cp %p /backup/wal_archive/%f'
```

### 7.3 Redis备份

#### RDB快照备份

```bash
# 手动触发快照
redis-cli BGSAVE

# 备份RDB文件
cp /var/lib/redis/dump.rdb /backup/redis/dump_$(date +%Y%m%d).rdb
```

#### AOF持久化

```bash
# redis.conf
appendonly yes
appendfsync everysec
```

### 7.4 MinIO备份

#### 镜像同步

```bash
# 使用mc工具同步到备份MinIO
mc mirror minio-prod/ingenio minio-backup/ingenio
```

---

## 8. 故障排查

### 8.1 常见问题诊断

#### 问题1: 应用无法启动

**症状**: JAR包启动后立即退出

**排查步骤**:

```bash
# 1. 查看日志
tail -f logs/ingenio-backend.log

# 2. 检查端口占用
lsof -i :8080

# 3. 检查Java版本
java -version  # 必须是Java 17+

# 4. 检查数据库连接
psql -h $DB_HOST -U $DB_USER -d $DB_NAME

# 5. 验证环境变量
env | grep -E "(DB_|REDIS_|DEEPSEEK_)"
```

**常见原因**:
- Java版本过低（< 17）
- 数据库连接失败
- 端口被占用
- 缺少必需环境变量

#### 问题2: 数据库连接超时

**症状**: `Connection timeout` 错误

**排查步骤**:

```bash
# 1. 测试数据库连通性
telnet $DB_HOST 5432

# 2. 检查连接池配置
# 查看application.yml中的hikari配置

# 3. 查看数据库活跃连接
psql -U postgres -c "SELECT count(*) FROM pg_stat_activity WHERE datname='ingenio_prod';"

# 4. 检查数据库日志
tail -f /var/log/postgresql/postgresql-15-main.log
```

**解决方案**:
- 增加连接池大小
- 检查防火墙规则
- 优化慢查询

#### 问题3: Redis连接失败

**症状**: `RedisConnectionException`

**排查步骤**:

```bash
# 1. 测试Redis连接
redis-cli -h $REDIS_HOST -p $REDIS_PORT -a $REDIS_PASSWORD ping

# 2. 检查Redis服务状态
systemctl status redis

# 3. 查看Redis日志
tail -f /var/log/redis/redis-server.log

# 4. 检查网络
telnet $REDIS_HOST 6379
```

#### 问题4: API响应慢

**症状**: 请求响应时间超过5秒

**排查步骤**:

```bash
# 1. 查看Actuator指标
curl http://localhost:8080/api/actuator/metrics/http.server.requests

# 2. 检查数据库慢查询
SELECT query, calls, total_time, mean_time
FROM pg_stat_statements
ORDER BY mean_time DESC
LIMIT 10;

# 3. 查看JVM GC情况
curl http://localhost:8080/api/actuator/metrics/jvm.gc.pause

# 4. 分析线程栈
jstack <pid> > thread_dump.txt
```

**优化建议**:
- 添加数据库索引
- 启用Redis缓存
- 增加JVM堆内存
- 使用连接池

### 8.2 日志分析

#### 关键错误日志模式

```bash
# 查找OutOfMemoryError
grep -r "OutOfMemoryError" logs/

# 查找数据库异常
grep -r "SQLException" logs/

# 查找超时错误
grep -r "TimeoutException" logs/

# 统计HTTP 500错误
grep "status=500" logs/ingenio-backend.log | wc -l
```

### 8.3 性能分析工具

#### JVM分析

```bash
# 堆内存dump
jmap -dump:live,format=b,file=heap_dump.hprof <pid>

# 分析堆dump（使用Eclipse MAT）
mat heap_dump.hprof

# 查看类加载统计
jstat -class <pid> 1000 10
```

#### 数据库分析

```bash
# PostgreSQL慢查询日志
# postgresql.conf
log_min_duration_statement = 1000  # 记录超过1秒的查询

# 查看表膨胀
SELECT schemaname, tablename,
       pg_size_pretty(pg_total_relation_size(schemaname||'.'||tablename)) AS size
FROM pg_tables
WHERE schemaname = 'public'
ORDER BY pg_total_relation_size(schemaname||'.'||tablename) DESC;
```

---

## 9. 性能优化

### 9.1 JVM调优

#### 生产环境JVM参数

```bash
java -jar ingenio-backend.jar \
  -Xms4g \                              # 初始堆内存4GB
  -Xmx4g \                              # 最大堆内存4GB
  -XX:+UseG1GC \                        # 使用G1垃圾收集器
  -XX:MaxGCPauseMillis=200 \            # GC停顿目标200ms
  -XX:ParallelGCThreads=8 \             # 并行GC线程数
  -XX:ConcGCThreads=2 \                 # 并发GC线程数
  -XX:InitiatingHeapOccupancyPercent=70 \ # G1触发阈值
  -XX:+HeapDumpOnOutOfMemoryError \     # OOM时生成堆dump
  -XX:HeapDumpPath=/var/dumps \         # 堆dump路径
  -XX:+PrintGCDetails \                 # 打印GC详情
  -XX:+PrintGCDateStamps \              # GC时间戳
  -Xloggc:/var/log/gc.log \             # GC日志路径
  -Dspring.profiles.active=prod
```

### 9.2 数据库优化

#### 索引优化

```sql
-- 为常用查询字段添加索引
CREATE INDEX idx_users_email ON users(email);
CREATE INDEX idx_projects_user_id ON projects(user_id);
CREATE INDEX idx_app_specs_tenant_id ON app_specs(tenant_id);

-- 复合索引
CREATE INDEX idx_app_spec_versions_app_version
ON app_spec_versions(app_id, version);

-- 分析索引使用情况
SELECT schemaname, tablename, indexname, idx_scan
FROM pg_stat_user_indexes
ORDER BY idx_scan ASC;
```

#### 连接池优化

```yaml
spring:
  datasource:
    hikari:
      maximum-pool-size: 50
      minimum-idle: 10
      connection-timeout: 30000
      idle-timeout: 600000
      max-lifetime: 1800000
      leak-detection-threshold: 60000  # 连接泄露检测
```

### 9.3 Redis缓存策略

#### 缓存配置

```java
@Configuration
@EnableCaching
public class CacheConfig {
    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory factory) {
        RedisCacheConfiguration config = RedisCacheConfiguration.defaultCacheConfig()
            .entryTtl(Duration.ofMinutes(30))  // 默认TTL 30分钟
            .disableCachingNullValues()
            .serializeValuesWith(RedisSerializationContext.SerializationPair
                .fromSerializer(new GenericJackson2JsonRedisSerializer()));

        return RedisCacheManager.builder(factory)
            .cacheDefaults(config)
            .build();
    }
}
```

#### 缓存注解使用

```java
@Service
public class ProjectService {

    @Cacheable(value = "projects", key = "#userId")
    public List<Project> getUserProjects(UUID userId) {
        return projectMapper.selectByUserId(userId);
    }

    @CacheEvict(value = "projects", key = "#project.userId")
    public void updateProject(Project project) {
        projectMapper.updateById(project);
    }
}
```

### 9.4 API限流

#### 配置限流规则

```yaml
ingenio:
  rate-limit:
    enabled: true
    requests-per-minute: 30
    ai-calls-per-hour: 50
```

---

## 10. 安全加固

### 10.1 网络安全

#### 防火墙配置（UFW）

```bash
# 允许SSH
sudo ufw allow 22/tcp

# 允许HTTPS
sudo ufw allow 443/tcp

# 仅允许内网访问应用端口
sudo ufw allow from 10.0.0.0/8 to any port 8080

# 仅允许内网访问数据库
sudo ufw allow from 10.0.0.0/8 to any port 5432

# 启用防火墙
sudo ufw enable
```

### 10.2 应用安全

#### HTTPS配置（Nginx反向代理）

```nginx
server {
    listen 443 ssl http2;
    server_name api.ingenio.com;

    ssl_certificate /etc/letsencrypt/live/api.ingenio.com/fullchain.pem;
    ssl_certificate_key /etc/letsencrypt/live/api.ingenio.com/privkey.pem;

    ssl_protocols TLSv1.2 TLSv1.3;
    ssl_ciphers HIGH:!aNULL:!MD5;
    ssl_prefer_server_ciphers on;

    location /api/ {
        proxy_pass http://localhost:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }
}
```

#### 安全响应头

```yaml
# application.yml
server:
  servlet:
    session:
      cookie:
        secure: true
        http-only: true
        same-site: strict
```

### 10.3 密钥轮换

#### 定期轮换JWT密钥

```bash
# 生成新密钥
NEW_JWT_SECRET=$(openssl rand -base64 32)

# 更新环境变量
export JWT_SECRET="$NEW_JWT_SECRET"

# 重启应用（零停机）
systemctl restart ingenio-backend
```

---

## 附录

### A. 部署检查清单

#### 部署前检查

- [ ] 所有环境变量已正确配置
- [ ] 数据库迁移脚本已测试
- [ ] 备份策略已就位
- [ ] 监控告警已配置
- [ ] 日志收集已配置
- [ ] 防火墙规则已配置
- [ ] HTTPS证书已安装
- [ ] 健康检查端点可访问

#### 部署后验证

- [ ] 应用健康检查通过
- [ ] 数据库连接正常
- [ ] Redis连接正常
- [ ] API响应时间 < 500ms
- [ ] 日志正常输出
- [ ] Prometheus指标采集正常
- [ ] 备份任务执行成功

### B. 参考文档

- [Spring Boot官方文档](https://spring.io/projects/spring-boot)
- [PostgreSQL性能优化](https://wiki.postgresql.org/wiki/Performance_Optimization)
- [Redis最佳实践](https://redis.io/docs/manual/patterns/)
- [Docker部署指南](./DOCKER_GUIDE.md)
- [Kubernetes部署指南](./KUBERNETES_GUIDE.md)

---

**文档版本**: v1.0
**最后更新**: 2025-01-15
**维护人**: Ingenio DevOps Team
