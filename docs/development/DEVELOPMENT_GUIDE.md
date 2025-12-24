# Ingenio 开发环境搭建指南

> **版本**: v1.0
> **最后更新**: 2025-11-09
> **维护人**: Ingenio Team

本指南将帮助开发者快速搭建Ingenio本地开发环境，涵盖前置要求、环境配置、数据库初始化、服务启动等完整流程。

---

## 目录

- [前置要求](#前置要求)
- [IDE配置](#ide配置)
- [环境变量配置](#环境变量配置)
- [数据库初始化](#数据库初始化)
- [Redis配置](#redis配置)
- [MinIO配置](#minio配置)
- [本地运行步骤](#本地运行步骤)
- [常用开发命令](#常用开发命令)
- [调试技巧](#调试技巧)
- [热重载配置](#热重载配置)
- [常见问题排查](#常见问题排查)

---

## 前置要求

### 必需软件

| 软件 | 版本 | 用途 | 安装验证 |
|-----|------|------|---------|
| **Java JDK** | 17+ | 后端运行时 | `java -version` |
| **Maven** | 3.9+ | 依赖管理和构建 | `mvn -version` |
| **PostgreSQL** | 15+ | 主数据库 | `psql --version` |
| **Redis** | 7+ | 缓存和会话 | `redis-cli --version` |
| **MinIO** | Latest | 对象存储 | 访问 http://localhost:9001 |
| **Git** | 2.x+ | 版本控制 | `git --version` |
| **Node.js** | 18+ | 前端工具链（可选） | `node --version` |

### 可选软件

| 软件 | 用途 |
|-----|------|
| **Docker Desktop** | 容器化部署和本地服务 |
| **IntelliJ IDEA** | Java IDE（推荐） |
| **VSCode** | 轻量级编辑器 |
| **Postman/Insomnia** | API测试工具 |
| **DBeaver/pgAdmin** | 数据库管理工具 |

---

## IDE配置

### IntelliJ IDEA（推荐）

#### 1. 安装必需插件

打开 `Settings/Preferences > Plugins`，安装以下插件：

- **Lombok** - 简化Java代码
- **MyBatisX** - MyBatis-Plus增强
- **Rainbow Brackets** - 彩虹括号
- **GitToolBox** - Git增强
- **SonarLint** - 代码质量检查

#### 2. 导入项目

```bash
# 克隆项目
git clone https://github.com/ingenio/ingenio.git
cd ingenio

# 使用IntelliJ IDEA打开项目
# File > Open > 选择 backend/pom.xml
```

#### 3. Maven配置

`Settings > Build, Execution, Deployment > Build Tools > Maven`

```
Maven home: /usr/local/maven (或Maven安装路径)
User settings file: ~/.m2/settings.xml
Local repository: ~/.m2/repository

✓ Always update snapshots
✓ Import Maven projects automatically
```

#### 4. Java编译器配置

`Settings > Build, Execution, Deployment > Compiler > Java Compiler`

```
Target bytecode version: 17
Per-module bytecode version: 17
Additional command line parameters: -parameters
```

#### 5. 代码风格配置

`Settings > Editor > Code Style > Java`

```
导入: google_checks.xml (项目根目录提供)

快捷键:
- Ctrl/Cmd + Alt + L: 格式化代码
- Ctrl/Cmd + Alt + O: 优化导入
```

#### 6. Lombok配置

`Settings > Build, Execution, Deployment > Compiler > Annotation Processors`

```
✓ Enable annotation processing
```

---

### VSCode配置

#### 1. 安装扩展

```bash
# 核心扩展
- Extension Pack for Java (Microsoft)
- Spring Boot Extension Pack (VMware)
- Lombok Annotations Support for VS Code

# 可选扩展
- GitLens
- Thunder Client (API测试)
- Database Client (数据库管理)
```

#### 2. 配置文件 `.vscode/settings.json`

```json
{
  "java.configuration.updateBuildConfiguration": "automatic",
  "java.compile.nullAnalysis.mode": "automatic",
  "java.format.settings.url": "google_checks.xml",
  "files.exclude": {
    "**/.git": true,
    "**/.DS_Store": true,
    "**/target": true
  },
  "java.jdt.ls.vmargs": "-XX:+UseG1GC -Xmx2G"
}
```

---

## 环境变量配置

### 1. 创建环境变量文件

```bash
# 后端目录
cd backend

# 复制环境变量模板
cp .env.example .env

# 编辑环境变量
vim .env
```

### 2. 环境变量说明

创建 `backend/.env` 文件并填写以下配置：

```bash
# ===== 数据库配置 =====
DB_HOST=localhost
DB_PORT=5432
DB_NAME=ingenio_dev
DB_USER=postgres
DB_PASSWORD=your_postgres_password

# ===== Redis配置 =====
REDIS_HOST=localhost
REDIS_PORT=6379
REDIS_PASSWORD=

# ===== MinIO配置（对象存储） =====
MINIO_ENDPOINT=http://localhost:9000
MINIO_ACCESS_KEY=minioadmin
MINIO_SECRET_KEY=minioadmin
MINIO_BUCKET=ingenio

# ===== DeepSeek AI配置 =====
DEEPSEEK_API_KEY=sk-your-deepseek-api-key

# ===== JWT配置 =====
JWT_SECRET=ingenio-jwt-secret-change-in-production-2025

# ===== 服务配置 =====
PORT=8080
PREVIEW_BASE_URL=http://localhost:3000

# ===== 应用配置 =====
SPRING_PROFILES_ACTIVE=dev
```

### 3. 环境变量加载

IntelliJ IDEA中配置环境变量：

```
Run > Edit Configurations > Spring Boot > IngenioBackendApplication
Environment Variables: 点击编辑图标，添加.env文件中的变量
```

或使用EnvFile插件自动加载：

```bash
# 安装EnvFile插件
Settings > Plugins > 搜索 "EnvFile" > Install

# 配置
Run > Edit Configurations > EnvFile
✓ Enable EnvFile
添加 .env 文件路径
```

---

## 数据库初始化

### 1. 安装PostgreSQL

**macOS (Homebrew):**
```bash
brew install postgresql@15
brew services start postgresql@15
```

**Ubuntu/Debian:**
```bash
sudo apt update
sudo apt install postgresql-15 postgresql-contrib
sudo systemctl start postgresql
sudo systemctl enable postgresql
```

**Windows:**
下载安装包: https://www.postgresql.org/download/windows/

### 2. 创建数据库用户

```bash
# 切换到postgres用户
sudo -u postgres psql

# 创建数据库用户（如果不存在）
CREATE USER ingenio_user WITH PASSWORD 'your_password';

# 授予创建数据库权限
ALTER USER ingenio_user CREATEDB;

# 退出
\q
```

### 3. 创建开发数据库

```bash
# 使用postgres用户创建数据库
createdb -U postgres ingenio_dev

# 或使用psql命令
psql -U postgres -c "CREATE DATABASE ingenio_dev OWNER ingenio_user;"

# 验证数据库创建
psql -U postgres -l | grep ingenio
```

### 4. 执行数据库迁移

Ingenio使用SQL迁移脚本管理数据库结构。

#### 方式一：手动执行（推荐用于首次）

```bash
cd backend/migrations

# 按顺序执行迁移脚本
psql -U postgres -d ingenio_dev -f 001_create_users_table.sql
psql -U postgres -d ingenio_dev -f 002_create_app_specs_table.sql
psql -U postgres -d ingenio_dev -f 003_create_app_spec_versions_table.sql
psql -U postgres -d ingenio_dev -f 004_create_generated_code_table.sql
psql -U postgres -d ingenio_dev -f 005_create_projects_table.sql
psql -U postgres -d ingenio_dev -f 006_create_forks_table.sql
psql -U postgres -d ingenio_dev -f 007_create_social_interactions_table.sql
psql -U postgres -d ingenio_dev -f 008_create_magic_prompts_table.sql
```

#### 方式二：使用迁移脚本（推荐用于日常）

```bash
cd backend/migrations

# 赋予执行权限
chmod +x migrate.sh

# 执行迁移
./migrate.sh

# 输出示例：
# 执行迁移: migrations/001_create_users_table.sql
# CREATE TABLE
# 执行迁移: migrations/002_create_app_specs_table.sql
# CREATE TABLE
# ...
```

#### 方式三：Docker一键初始化

```bash
# 使用Docker Compose启动数据库并自动迁移
docker-compose up -d postgres

# 等待数据库启动（约5秒）
sleep 5

# 执行迁移
docker-compose exec postgres psql -U postgres -d ingenio_dev -f /migrations/migrate_all.sql
```

### 5. 验证数据库结构

```bash
# 连接数据库
psql -U postgres -d ingenio_dev

# 查看所有表
\dt

# 应该看到如下表：
#  Schema |          Name           | Type  |  Owner
# --------+-------------------------+-------+----------
#  public | users                   | table | postgres
#  public | app_specs               | table | postgres
#  public | app_spec_versions       | table | postgres
#  public | generated_code          | table | postgres
#  public | projects                | table | postgres
#  public | forks                   | table | postgres
#  public | social_interactions     | table | postgres
#  public | magic_prompts           | table | postgres

# 查看表结构示例
\d users

# 退出
\q
```

### 6. 数据库回滚（如果需要）

```bash
cd backend/migrations

# 执行回滚脚本（按倒序）
psql -U postgres -d ingenio_dev -f 008_create_magic_prompts_table.down.sql
psql -U postgres -d ingenio_dev -f 007_create_social_interactions_table.down.sql
# ... 依此类推
```

---

## Redis配置

### 1. 安装Redis

**macOS (Homebrew):**
```bash
brew install redis
brew services start redis
```

**Ubuntu/Debian:**
```bash
sudo apt update
sudo apt install redis-server
sudo systemctl start redis-server
sudo systemctl enable redis-server
```

**Docker方式（推荐）:**
```bash
docker run -d \
  --name ingenio-redis \
  -p 6379:6379 \
  redis:7-alpine \
  redis-server --appendonly yes
```

### 2. 验证Redis连接

```bash
# 连接Redis
redis-cli

# 测试命令
127.0.0.1:6379> PING
PONG

# 设置测试键
127.0.0.1:6379> SET test "ingenio"
OK

# 获取测试键
127.0.0.1:6379> GET test
"ingenio"

# 删除测试键
127.0.0.1:6379> DEL test
(integer) 1

# 退出
127.0.0.1:6379> EXIT
```

### 3. Redis性能测试

```bash
redis-benchmark -q -n 10000

# 输出示例：
# PING_INLINE: 100000.00 requests per second
# PING_MBULK: 100000.00 requests per second
# SET: 90909.09 requests per second
# GET: 100000.00 requests per second
```

---

## MinIO配置

MinIO是AWS S3兼容的对象存储服务，用于存储用户上传的文件（图片、语音、文档等）。

### 1. 安装MinIO

**Docker方式（推荐）:**
```bash
docker run -d \
  --name ingenio-minio \
  -p 9000:9000 \
  -p 9001:9001 \
  -e MINIO_ROOT_USER=minioadmin \
  -e MINIO_ROOT_PASSWORD=minioadmin \
  -v minio-data:/data \
  quay.io/minio/minio server /data --console-address ":9001"
```

**macOS (Homebrew):**
```bash
brew install minio/stable/minio
minio server /Users/Shared/minio
```

**手动下载:**
```bash
# Linux
wget https://dl.min.io/server/minio/release/linux-amd64/minio
chmod +x minio
./minio server /data

# macOS
wget https://dl.min.io/server/minio/release/darwin-amd64/minio
chmod +x minio
./minio server /data
```

### 2. 访问MinIO控制台

打开浏览器访问: http://localhost:9001

```
用户名: minioadmin
密码: minioadmin
```

### 3. 创建Bucket

在MinIO控制台中：

1. 点击左侧 **Buckets** 菜单
2. 点击 **Create Bucket** 按钮
3. 输入Bucket名称: `ingenio`
4. 点击 **Create Bucket**

或使用MinIO Client (mc)命令行：

```bash
# 安装mc
brew install minio/stable/mc

# 配置别名
mc alias set local http://localhost:9000 minioadmin minioadmin

# 创建bucket
mc mb local/ingenio

# 设置公共访问策略（仅开发环境）
mc anonymous set download local/ingenio

# 验证
mc ls local/
```

### 4. 验证MinIO集成

启动后端服务后，MinIO会自动创建bucket（如果配置了`minio.auto-create-bucket: true`）。

---

## 本地运行步骤

### 方式一：IntelliJ IDEA运行（推荐）

1. **打开项目**
   - `File > Open > 选择 backend/pom.xml`

2. **配置运行配置**
   - `Run > Edit Configurations`
   - 点击 `+` > `Spring Boot`
   - Name: `IngenioBackendApplication`
   - Main class: `com.ingenio.backend.IngenioBackendApplication`
   - VM options: `-Dspring.profiles.active=dev`
   - Environment variables: 加载 `.env` 文件

3. **启动应用**
   - 点击绿色运行按钮或按 `Shift + F10`

4. **查看日志**
   ```
   2025-11-09 12:00:00 [main] INFO  c.i.b.IngenioBackendApplication - Starting IngenioBackendApplication
   2025-11-09 12:00:01 [main] INFO  c.i.b.IngenioBackendApplication - The following 1 profile is active: "dev"
   2025-11-09 12:00:05 [main] INFO  o.s.b.w.e.t.TomcatWebServer - Tomcat started on port(s): 8080 (http)
   2025-11-09 12:00:05 [main] INFO  c.i.b.IngenioBackendApplication - Started IngenioBackendApplication in 5.123 seconds
   ```

### 方式二：Maven命令行运行

```bash
cd backend

# 开发模式启动（使用dev配置）
mvn spring-boot:run -Dspring-boot.run.profiles=dev

# 或先编译再运行
mvn clean package -DskipTests
java -jar target/ingenio-backend-0.1.0-SNAPSHOT.jar --spring.profiles.active=dev
```

### 方式三：Docker Compose一键启动

```bash
# 启动所有服务（PostgreSQL + Redis + MinIO + Backend）
docker-compose up -d

# 查看日志
docker-compose logs -f backend

# 停止所有服务
docker-compose down

# 停止并删除数据
docker-compose down -v
```

### 4. 验证服务启动

**健康检查:**
```bash
curl http://localhost:8080/api/actuator/health

# 响应:
{
  "status": "UP",
  "components": {
    "db": { "status": "UP" },
    "diskSpace": { "status": "UP" },
    "ping": { "status": "UP" },
    "redis": { "status": "UP" }
  }
}
```

**Swagger API文档:**
打开浏览器访问: http://localhost:8080/api/swagger-ui.html

**测试API:**
```bash
# 注册用户
curl -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{
    "email": "test@ingenio.dev",
    "password": "Test123456",
    "username": "testuser"
  }'

# 登录
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "email": "test@ingenio.dev",
    "password": "Test123456"
  }'
```

---

## 常用开发命令

### Maven命令

```bash
# 编译项目
mvn compile

# 运行测试
mvn test

# 跳过测试编译
mvn clean package -DskipTests

# 查看依赖树
mvn dependency:tree

# 下载源码
mvn dependency:sources

# 清理构建
mvn clean

# 安装到本地仓库
mvn install

# 代码规范检查
mvn checkstyle:check

# 代码覆盖率报告
mvn clean test jacoco:report
# 报告位置: target/site/jacoco/index.html
```

### 数据库命令

```bash
# 连接数据库
psql -U postgres -d ingenio_dev

# 常用SQL命令
\dt                          # 列出所有表
\d table_name                # 查看表结构
\du                          # 列出所有用户
\l                           # 列出所有数据库
\q                           # 退出

# 备份数据库
pg_dump -U postgres ingenio_dev > backup_$(date +%Y%m%d).sql

# 恢复数据库
psql -U postgres ingenio_dev < backup_20251109.sql

# 查看表行数
SELECT COUNT(*) FROM users;

# 清空表数据（保留结构）
TRUNCATE TABLE users RESTART IDENTITY CASCADE;
```

### Redis命令

```bash
# 连接Redis
redis-cli

# 常用命令
KEYS *                       # 查看所有键
GET key_name                 # 获取键值
SET key_name value           # 设置键值
DEL key_name                 # 删除键
FLUSHDB                      # 清空当前数据库
FLUSHALL                     # 清空所有数据库
INFO                         # 查看Redis信息
MONITOR                      # 实时监控Redis命令
```

### Docker命令

```bash
# 查看运行中的容器
docker ps

# 查看所有容器
docker ps -a

# 查看容器日志
docker logs -f ingenio-backend

# 进入容器
docker exec -it ingenio-backend bash

# 重启容器
docker restart ingenio-backend

# 停止容器
docker stop ingenio-backend

# 删除容器
docker rm ingenio-backend

# 删除镜像
docker rmi ingenio-backend:latest
```

---

## 调试技巧

### 1. IntelliJ IDEA远程调试

**配置远程调试:**
```bash
# 启动应用时添加调试参数
java -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=*:5005 \
  -jar target/ingenio-backend-0.1.0-SNAPSHOT.jar
```

**IntelliJ配置:**
1. `Run > Edit Configurations`
2. 点击 `+` > `Remote JVM Debug`
3. Name: `Remote Debug`
4. Host: `localhost`
5. Port: `5005`
6. 点击 `Apply`

**启动调试:**
1. 在代码中设置断点
2. 点击 `Debug 'Remote Debug'`

### 2. 日志级别动态调整

**application.yml配置:**
```yaml
logging:
  level:
    com.ingenio.backend: DEBUG
    org.springframework.ai: DEBUG
```

**运行时动态调整（通过Actuator）:**
```bash
# 查看日志级别
curl http://localhost:8080/api/actuator/loggers/com.ingenio.backend

# 调整日志级别为TRACE
curl -X POST http://localhost:8080/api/actuator/loggers/com.ingenio.backend \
  -H "Content-Type: application/json" \
  -d '{"configuredLevel": "TRACE"}'

# 恢复日志级别为INFO
curl -X POST http://localhost:8080/api/actuator/loggers/com.ingenio.backend \
  -H "Content-Type: application/json" \
  -d '{"configuredLevel": "INFO"}'
```

### 3. 数据库查询调试

**MyBatis-Plus SQL日志:**
```yaml
mybatis-plus:
  configuration:
    log-impl: org.apache.ibatis.logging.stdout.StdOutImpl
```

**查看执行的SQL:**
```
2025-11-09 12:00:00 [http-nio-8080-exec-1] DEBUG c.i.b.m.UserMapper.selectById - ==>  Preparing: SELECT * FROM users WHERE id = ?
2025-11-09 12:00:00 [http-nio-8080-exec-1] DEBUG c.i.b.m.UserMapper.selectById - ==> Parameters: 550e8400-e29b-41d4-a716-446655440000
2025-11-09 12:00:00 [http-nio-8080-exec-1] DEBUG c.i.b.m.UserMapper.selectById - <==      Total: 1
```

### 4. AI Agent调试

**查看AI请求和响应:**
```java
@Slf4j
@Service
public class PlanAgent {

    public PlanResult plan(String requirement) {
        log.debug("PlanAgent输入: {}", requirement);

        // 调用AI
        ChatResponse response = chatClient.call(prompt);

        log.debug("PlanAgent原始响应: {}", response.getResult().getOutput().getContent());

        // 解析响应
        PlanResult result = parseResponse(response);

        log.debug("PlanAgent结构化结果: {}", result);

        return result;
    }
}
```

---

## 热重载配置

### Spring Boot DevTools

**添加依赖（已包含）:**
```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-devtools</artifactId>
    <scope>runtime</scope>
    <optional>true</optional>
</dependency>
```

**IntelliJ配置:**
1. `Settings > Build, Execution, Deployment > Compiler`
   - ✓ Build project automatically

2. `Settings > Advanced Settings`
   - ✓ Allow auto-make to start even if developed application is currently running

**触发热重载:**
- 修改Java文件后按 `Ctrl/Cmd + F9` (Build Project)
- 或等待IDE自动编译（约2秒后）

**热重载范围:**
- ✅ 业务逻辑代码
- ✅ 配置文件（application.yml）
- ❌ 依赖变更（需重启）
- ❌ 静态资源（需刷新浏览器）

---

## 常见问题排查

### 1. 数据库连接失败

**错误信息:**
```
org.postgresql.util.PSQLException: Connection refused. Check that the hostname and port are correct
```

**排查步骤:**
```bash
# 1. 检查PostgreSQL是否运行
ps aux | grep postgres

# 2. 检查PostgreSQL监听端口
lsof -i :5432

# 3. 测试数据库连接
psql -U postgres -h localhost -d ingenio_dev

# 4. 检查环境变量
echo $DB_HOST
echo $DB_PORT
echo $DB_NAME

# 5. 查看PostgreSQL日志
tail -f /usr/local/var/log/postgres.log  # macOS
tail -f /var/log/postgresql/postgresql-15-main.log  # Ubuntu
```

**解决方案:**
- 启动PostgreSQL: `brew services start postgresql@15` (macOS)
- 检查 `backend/.env` 配置是否正确
- 确认防火墙未阻止5432端口

### 2. Redis连接失败

**错误信息:**
```
io.lettuce.core.RedisConnectionException: Unable to connect to localhost:6379
```

**排查步骤:**
```bash
# 1. 检查Redis是否运行
redis-cli PING

# 2. 检查Redis监听端口
lsof -i :6379

# 3. 查看Redis日志
tail -f /usr/local/var/log/redis.log  # macOS
tail -f /var/log/redis/redis-server.log  # Ubuntu
```

**解决方案:**
- 启动Redis: `brew services start redis` (macOS)
- 或使用Docker: `docker start ingenio-redis`

### 3. MinIO连接失败

**错误信息:**
```
io.minio.errors.ErrorResponseException: The Access Key Id you provided does not exist in our records
```

**排查步骤:**
```bash
# 1. 检查MinIO是否运行
curl http://localhost:9000/minio/health/live

# 2. 检查环境变量
echo $MINIO_ENDPOINT
echo $MINIO_ACCESS_KEY
echo $MINIO_SECRET_KEY

# 3. 访问MinIO控制台
open http://localhost:9001
```

**解决方案:**
- 启动MinIO: `docker start ingenio-minio`
- 确认访问密钥配置正确
- 检查网络连接

### 4. Maven依赖下载失败

**错误信息:**
```
Failed to read artifact descriptor for com.alibaba.cloud.ai:spring-ai-alibaba-starter-dashscope
```

**排查步骤:**
```bash
# 1. 清理Maven缓存
mvn dependency:purge-local-repository

# 2. 强制更新
mvn clean install -U

# 3. 检查Maven仓库配置
cat ~/.m2/settings.xml
```

**解决方案:**
- 使用阿里云Maven镜像（编辑 `~/.m2/settings.xml`）:
```xml
<mirror>
    <id>aliyun</id>
    <name>Aliyun Maven</name>
    <url>https://maven.aliyun.com/repository/public</url>
    <mirrorOf>central</mirrorOf>
</mirror>
```

### 5. DeepSeek API调用失败

**错误信息:**
```
org.springframework.ai.retry.NonTransientAiException: 401 Unauthorized
```

**排查步骤:**
```bash
# 1. 检查API Key
echo $DEEPSEEK_API_KEY

# 2. 测试API连接
curl https://api.deepseek.com/v1/chat/completions \
  -H "Authorization: Bearer $DEEPSEEK_API_KEY" \
  -H "Content-Type: application/json" \
  -d '{
    "model": "deepseek-chat",
    "messages": [{"role": "user", "content": "Hello"}]
  }'
```

**解决方案:**
- 检查API Key是否正确
- 确认API Key未过期
- 检查网络是否可访问DeepSeek API

### 6. 端口冲突

**错误信息:**
```
org.apache.catalina.LifecycleException: Failed to start component [Connector[HTTP/1.1-8080]]
```

**排查步骤:**
```bash
# 查看8080端口占用
lsof -i :8080

# 杀死占用进程
kill -9 <PID>

# 或修改端口
export PORT=8081
```

### 7. Lombok不生效

**错误信息:**
```
java: cannot find symbol
  symbol:   method builder()
  location: class com.ingenio.backend.entity.UserEntity
```

**解决方案:**
1. IntelliJ安装Lombok插件
2. 启用注解处理器:
   - `Settings > Build, Execution, Deployment > Compiler > Annotation Processors`
   - ✓ Enable annotation processing
3. 重新构建项目: `Build > Rebuild Project`

### 8. 测试失败

**错误信息:**
```
java.lang.IllegalStateException: Failed to load ApplicationContext
```

**排查步骤:**
```bash
# 1. 查看详细错误日志
mvn test -X

# 2. 单独运行失败的测试
mvn test -Dtest=UserServiceTest

# 3. 检查测试配置
cat src/test/resources/application-test.yml
```

**解决方案:**
- 使用TestContainers进行集成测试
- 确保测试环境独立于开发环境
- 检查测试数据初始化

---

## 性能优化建议

### JVM参数优化

```bash
java -jar target/ingenio-backend-0.1.0-SNAPSHOT.jar \
  -Xms512m \
  -Xmx2g \
  -XX:+UseG1GC \
  -XX:MaxGCPauseMillis=200 \
  -XX:+HeapDumpOnOutOfMemoryError \
  -XX:HeapDumpPath=logs/heap_dump.hprof \
  -Dspring.profiles.active=dev
```

### 数据库连接池优化

```yaml
spring:
  datasource:
    hikari:
      maximum-pool-size: 20
      minimum-idle: 5
      connection-timeout: 30000
      idle-timeout: 600000
      max-lifetime: 1800000
```

### Redis连接池优化

```yaml
spring:
  redis:
    lettuce:
      pool:
        max-active: 20
        max-idle: 10
        min-idle: 5
        max-wait: 3000ms
```

---

## 下一步

- 阅读 [代码规范文档](./CODING_STANDARDS.md)
- 阅读 [Git工作流文档](./GIT_WORKFLOW.md)
- 阅读 [贡献指南](../CONTRIBUTING.md)
- 查看 [API文档](http://localhost:8080/api/swagger-ui.html)
- 加入 [开发者社区](https://discord.gg/ingenio)

---

**维护信息**

- 文档版本: v1.0
- 最后更新: 2025-11-09
- 维护人: Ingenio Team
- 反馈问题: https://github.com/ingenio/ingenio/issues
