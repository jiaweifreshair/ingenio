# 本地部署指南

> **最后更新**: 2025-01-XX  
> **适用环境**: macOS / Linux  
> **目标**: 在本地开发环境编译并部署前后端服务

---

## 📋 前置要求

### 必需软件

1. **Java 17+**
   ```bash
   # 检查 Java 版本
   java -version
   # 应该显示 java version "17" 或更高
   ```

2. **Maven 3.6+**
   ```bash
   # 检查 Maven 版本
   mvn -version
   ```

3. **Node.js 18+**
   ```bash
   # 检查 Node.js 版本
   node -version
   # 应该显示 v18.x.x 或更高
   ```

4. **pnpm**
   ```bash
   # 安装 pnpm（如果未安装）
   npm install -g pnpm
   ```

5. **Docker & Docker Compose**
   ```bash
   # 检查 Docker
   docker --version
   docker-compose --version
   ```

### 可选软件

- **PostgreSQL 15+** (如果不想使用 Docker)
- **Redis 7+** (如果不想使用 Docker)
- **MinIO** (如果不想使用 Docker)

---

## 🚀 快速开始

### 方式一：使用一键部署脚本（推荐）

```bash
# 1. 克隆项目（如果还没有）
cd /Users/apus/Documents/UGit/Ingenio

# 2. 运行一键部署脚本
./scripts/deploy-local.sh
```

### 方式二：手动部署

按照以下步骤手动部署：

---

## 📦 步骤 1: 启动依赖服务

### 使用 Docker Compose（推荐）

```bash
# 启动 PostgreSQL、Redis、MinIO
docker-compose up -d postgres redis minio

# 检查服务状态
docker-compose ps

# 查看日志
docker-compose logs -f
```

### 验证服务

```bash
# 检查 PostgreSQL
docker exec -it ingenio-postgres psql -U postgres -d ingenio -c "SELECT version();"

# 检查 Redis
docker exec -it ingenio-redis redis-cli ping
# 应该返回: PONG

# 检查 MinIO
curl http://localhost:9000/minio/health/live
# 应该返回: OK
```

**MinIO 控制台**: http://localhost:9001
- 用户名: `minioadmin`
- 密码: `minioadmin`

---

## 🔧 步骤 2: 配置环境变量

### 后端环境变量

创建或编辑 `backend/.env` 文件：

```bash
cd backend
cat > .env << 'EOF'
# 数据库配置
DB_HOST=localhost
DB_PORT=5432
DB_NAME=ingenio_dev
DB_USER=postgres
DB_PASSWORD=postgres

# Redis 配置
REDIS_HOST=localhost
REDIS_PORT=6379
REDIS_PASSWORD=

# MinIO 配置
MINIO_ENDPOINT=http://localhost:9000
MINIO_ACCESS_KEY=minioadmin
MINIO_SECRET_KEY=minioadmin
MINIO_BUCKET=ingenio-dev

# JWT 配置
JWT_SECRET=ingenio-jwt-dev-secret-key-change-in-production

# AI API Keys（必需）
DEEPSEEK_API_KEY=sk-your-deepseek-api-key
DASHSCOPE_API_KEY=sk-your-dashscope-api-key

# 可选：邮件配置
MAIL_HOST=smtp.qq.com
MAIL_PORT=465
MAIL_USERNAME=your-email@qq.com
MAIL_PASSWORD=your-authorization-code

# 可选：微信登录配置
WECHAT_APPID=your-wechat-appid
WECHAT_SECRET=your-wechat-secret
EOF
```

### 前端环境变量

创建或编辑 `frontend/.env.local` 文件：

```bash
cd frontend
cat > .env.local << 'EOF'
# API 基础 URL
NEXT_PUBLIC_API_BASE_URL=http://localhost:8080/api

# WebSocket URL（如果需要）
NEXT_PUBLIC_WS_URL=ws://localhost:8080/ws
EOF
```

---

## 🏗️ 步骤 3: 初始化数据库

### 检查数据库迁移脚本

```bash
# 查看迁移脚本目录
ls -la backend/migrations/
```

如果迁移脚本存在，执行迁移：

```bash
# 方式一：使用 psql 直接执行
psql -U postgres -d ingenio_dev -f backend/migrations/001_create_users_table.sql
# ... 执行所有迁移脚本

# 方式二：使用后端迁移工具（如果配置了）
cd backend
mvn spring-boot:run -Dspring-boot.run.arguments="--spring.profiles.active=dev --migration.run=true"
```

### 创建数据库（如果不存在）

```bash
# 连接到 PostgreSQL
docker exec -it ingenio-postgres psql -U postgres

# 在 psql 中执行
CREATE DATABASE ingenio_dev;
CREATE USER ingenio_user WITH PASSWORD 'ingenio_password';
GRANT ALL PRIVILEGES ON DATABASE ingenio_dev TO ingenio_user;
\q
```

---

## 🔨 步骤 4: 编译后端

```bash
cd backend

# 清理并编译
mvn clean install -DskipTests

# 或者只编译不运行测试
mvn clean package -DskipTests

# 检查编译结果
ls -la target/*.jar
```

**编译输出**: `target/ingenio-backend-0.1.0-SNAPSHOT.jar`

---

## 🎨 步骤 5: 安装前端依赖

```bash
cd frontend

# 安装依赖
pnpm install

# 检查依赖安装
pnpm list --depth=0
```

---

## ▶️ 步骤 6: 启动服务

### 方式一：使用启动脚本

#### 启动后端

```bash
# 使用脚本启动
./scripts/start-backend.sh

# 或者手动启动
cd backend
export SPRING_PROFILES_ACTIVE=dev
mvn spring-boot:run
```

#### 启动前端

```bash
# 使用脚本启动
./scripts/start-frontend.sh

# 或者手动启动
cd frontend
pnpm dev
```

### 方式二：使用一键启动脚本

```bash
# 启动所有服务（包括 Docker 服务）
./scripts/start-all.sh
```

---

## ✅ 步骤 7: 验证部署

### 检查后端服务

```bash
# 健康检查
curl http://localhost:8080/api/actuator/health

# API 文档
open http://localhost:8080/api/swagger-ui.html

# 检查日志
tail -f logs/backend.log
# 或
tail -f logs/ingenio-backend.log
```

**后端服务地址**: http://localhost:8080/api

### 检查前端服务

```bash
# 访问前端
open http://localhost:3000

# 或使用 curl
curl http://localhost:3000
```

**前端服务地址**: http://localhost:3000

---

## 🐛 常见问题

### 1. 后端启动失败

#### 问题：数据库连接失败
```bash
# 检查 PostgreSQL 是否运行
docker ps | grep postgres

# 检查数据库连接
docker exec -it ingenio-postgres psql -U postgres -d ingenio_dev -c "SELECT 1;"
```

#### 问题：端口被占用
```bash
# 检查端口占用
lsof -i :8080

# 杀死占用进程
kill -9 <PID>
```

#### 问题：Maven 依赖下载失败
```bash
# 清理 Maven 缓存
mvn clean
rm -rf ~/.m2/repository/com/ingenio

# 重新下载依赖
mvn dependency:resolve
```

### 2. 前端启动失败

#### 问题：端口被占用
```bash
# 检查端口占用
lsof -i :3000

# 或修改端口
cd frontend
PORT=3001 pnpm dev
```

#### 问题：依赖安装失败
```bash
# 清理并重新安装
cd frontend
rm -rf node_modules pnpm-lock.yaml
pnpm install
```

### 3. Docker 服务问题

#### 问题：Docker 服务无法启动
```bash
# 检查 Docker 状态
docker ps

# 重启 Docker 服务
docker-compose down
docker-compose up -d
```

#### 问题：数据卷权限问题
```bash
# 检查数据卷
docker volume ls

# 清理数据卷（注意：会删除数据）
docker-compose down -v
```

---

## 📊 服务端口列表

| 服务 | 端口 | 说明 |
|------|------|------|
| 后端 API | 8080 | Spring Boot 服务 |
| 前端 Web | 3000 | Next.js 开发服务器 |
| PostgreSQL | 5432 | 数据库 |
| Redis | 6379 | 缓存 |
| MinIO API | 9000 | 对象存储 API |
| MinIO Console | 9001 | 对象存储控制台 |

---

## 🔄 停止服务

### 停止所有服务

```bash
# 使用停止脚本
./scripts/stop-all.sh

# 或手动停止
# 1. 停止前端（Ctrl+C 或）
pkill -f "next dev"

# 2. 停止后端（Ctrl+C 或）
pkill -f "spring-boot:run"

# 3. 停止 Docker 服务
docker-compose down
```

---

## 📝 开发模式

### 后端热重载

```bash
cd backend
mvn spring-boot:run -Dspring-boot.run.profiles=dev
# Spring Boot DevTools 会自动重载
```

### 前端热重载

```bash
cd frontend
pnpm dev
# Next.js 默认支持热重载
```

---

## 🧪 运行测试

### 后端测试

```bash
cd backend

# 运行所有测试
mvn test

# 运行特定测试
mvn test -Dtest=UserServiceTest

# 生成测试覆盖率报告
mvn test jacoco:report
```

### 前端测试

```bash
cd frontend

# 运行单元测试
pnpm test:unit

# 运行 E2E 测试
pnpm e2e

# TypeScript 类型检查
pnpm typecheck
```

---

## 📚 相关文档

- [部署指南](./DEPLOYMENT_GUIDE.md) - 生产环境部署
- [Docker 部署](./DOCKER_GUIDE.md) - Docker 部署指南
- [开发指南](../development/DEVELOPMENT_GUIDE.md) - 开发环境配置

---

## 🆘 获取帮助

如果遇到问题：

1. 查看日志文件：
   - 后端: `logs/backend.log` 或 `logs/ingenio-backend.log`
   - 前端: `logs/frontend.log`

2. 检查服务状态：
   ```bash
   # Docker 服务
   docker-compose ps
   
   # 后端进程
   ps aux | grep spring-boot
   
   # 前端进程
   ps aux | grep "next dev"
   ```

3. 查看项目 Issue: [GitHub Issues](https://github.com/yourusername/Ingenio/issues)

---

**最后更新**: 2025-01-XX  
**维护者**: Ingenio 团队





