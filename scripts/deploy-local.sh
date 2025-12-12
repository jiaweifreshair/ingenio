#!/bin/bash

# Ingenio 本地部署脚本
# 功能：一键编译并部署前后端服务

set -e  # 遇到错误立即退出

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m' # No Color

# 项目根目录
PROJECT_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$PROJECT_ROOT"

echo -e "${BLUE}========================================${NC}"
echo -e "${BLUE}  Ingenio 本地部署脚本${NC}"
echo -e "${BLUE}========================================${NC}"
echo ""

# 检查必需工具
check_requirements() {
    echo -e "${YELLOW}📋 检查前置要求...${NC}"
    
    local missing_tools=()
    
    # 检查 Java
    if ! command -v java &> /dev/null; then
        missing_tools+=("Java 17+")
    else
        JAVA_VERSION=$(java -version 2>&1 | head -n 1 | cut -d'"' -f2 | sed '/^1\./s///' | cut -d'.' -f1)
        if [ "$JAVA_VERSION" -lt 17 ]; then
            echo -e "${RED}❌ Java 版本过低，需要 Java 17+，当前版本: $JAVA_VERSION${NC}"
            exit 1
        else
            echo -e "${GREEN}✅ Java $JAVA_VERSION${NC}"
        fi
    fi
    
    # 检查 Maven
    if ! command -v mvn &> /dev/null; then
        missing_tools+=("Maven")
    else
        echo -e "${GREEN}✅ Maven$(mvn -version | head -n 1 | cut -d' ' -f3)${NC}"
    fi
    
    # 检查 Node.js
    if ! command -v node &> /dev/null; then
        missing_tools+=("Node.js 18+")
    else
        NODE_VERSION=$(node -version)
        echo -e "${GREEN}✅ Node.js $NODE_VERSION${NC}"
    fi
    
    # 检查 pnpm
    if ! command -v pnpm &> /dev/null; then
        echo -e "${YELLOW}⚠️  pnpm 未安装，将自动安装...${NC}"
        npm install -g pnpm
    else
        echo -e "${GREEN}✅ pnpm$(pnpm -v)${NC}"
    fi
    
    # 检查 Docker
    if ! command -v docker &> /dev/null; then
        missing_tools+=("Docker")
    else
        echo -e "${GREEN}✅ Docker$(docker --version | cut -d' ' -f3 | cut -d',' -f1)${NC}"
    fi
    
    if [ ${#missing_tools[@]} -gt 0 ]; then
        echo -e "${RED}❌ 缺少必需工具: ${missing_tools[*]}${NC}"
        exit 1
    fi
    
    echo ""
}

# 启动 Docker 服务
start_docker_services() {
    echo -e "${YELLOW}🐳 启动 Docker 服务...${NC}"
    
    if ! docker ps &> /dev/null; then
        echo -e "${RED}❌ Docker 未运行，请先启动 Docker${NC}"
        exit 1
    fi
    
    # 启动 PostgreSQL、Redis、MinIO
    docker-compose up -d postgres redis minio
    
    # 等待服务就绪
    echo -e "${YELLOW}⏳ 等待 Docker 服务就绪...${NC}"
    sleep 5
    
    # 检查服务状态
    if docker ps | grep -q ingenio-postgres && \
       docker ps | grep -q ingenio-redis && \
       docker ps | grep -q ingenio-minio; then
        echo -e "${GREEN}✅ Docker 服务已启动${NC}"
    else
        echo -e "${RED}❌ Docker 服务启动失败${NC}"
        docker-compose ps
        exit 1
    fi
    
    echo ""
}

# 配置环境变量
setup_env() {
    echo -e "${YELLOW}⚙️  配置环境变量...${NC}"
    
    # 后端环境变量
    if [ ! -f "backend/.env" ]; then
        echo -e "${YELLOW}📝 创建后端 .env 文件...${NC}"
        cat > backend/.env << 'EOF'
# 数据库配置
DB_HOST=localhost
DB_PORT=5432
DB_NAME=ingenio_dev
DB_USER=postgres
DB_PASSWORD=ingenio_20251122

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

# AI API Keys（请替换为实际值）
DEEPSEEK_API_KEY=sk-4c11e155dc9cb7c35be13a88996fe5660e0115d318cf69975a34451772158372
DASHSCOPE_API_KEY=sk-20bc5252fe0f4aa4a437db35d913ac4e
EOF
        echo -e "${YELLOW}⚠️  请编辑 backend/.env 文件，填入实际的 API Keys${NC}"
    else
        echo -e "${GREEN}✅ 后端 .env 文件已存在${NC}"
    fi
    
    # 前端环境变量
    if [ ! -f "frontend/.env.local" ]; then
        echo -e "${YELLOW}📝 创建前端 .env.local 文件...${NC}"
        cat > frontend/.env.local << 'EOF'
# API 基础 URL
NEXT_PUBLIC_API_BASE_URL=http://localhost:8080/api

# WebSocket URL
NEXT_PUBLIC_WS_URL=ws://localhost:8080/ws
EOF
        echo -e "${GREEN}✅ 前端 .env.local 文件已创建${NC}"
    else
        echo -e "${GREEN}✅ 前端 .env.local 文件已存在${NC}"
    fi
    
    echo ""
}

# 初始化数据库
init_database() {
    echo -e "${YELLOW}🗄️  初始化数据库...${NC}"
    
    # 检查数据库是否存在
    if docker exec ingenio-postgres psql -U postgres -lqt | cut -d \| -f 1 | grep -qw ingenio_dev; then
        echo -e "${GREEN}✅ 数据库 ingenio_dev 已存在${NC}"
    else
        echo -e "${YELLOW}📝 创建数据库...${NC}"
        docker exec -i ingenio-postgres psql -U postgres << EOF
CREATE DATABASE ingenio_dev;
CREATE USER ingenio_user WITH PASSWORD 'ingenio_password';
GRANT ALL PRIVILEGES ON DATABASE ingenio_dev TO ingenio_user;
EOF
        echo -e "${GREEN}✅ 数据库已创建${NC}"
    fi
    
    # 执行迁移脚本（如果存在）
    if [ -d "database/migrations" ] && [ "$(ls -A database/migrations/*.sql 2>/dev/null)" ]; then
        echo -e "${YELLOW}📝 执行数据库迁移...${NC}"
        for migration in database/migrations/*.sql; do
            if [ -f "$migration" ]; then
                echo "执行: $(basename $migration)"
                docker exec -i ingenio-postgres psql -U postgres -d ingenio_dev < "$migration"
            fi
        done
        echo -e "${GREEN}✅ 数据库迁移完成${NC}"
    else
        echo -e "${YELLOW}⚠️  未找到数据库迁移脚本${NC}"
    fi
    
    echo ""
}

# 编译后端
build_backend() {
    echo -e "${YELLOW}🔨 编译后端服务...${NC}"
    
    cd backend
    
    # 清理并编译
    echo "执行: mvn clean install -DskipTests"
    mvn clean install -DskipTests
    
    if [ -f "target/ingenio-backend-0.1.0-SNAPSHOT.jar" ]; then
        echo -e "${GREEN}✅ 后端编译成功${NC}"
    else
        echo -e "${RED}❌ 后端编译失败${NC}"
        exit 1
    fi
    
    cd ..
    echo ""
}

# 安装前端依赖
install_frontend() {
    echo -e "${YELLOW}📦 安装前端依赖...${NC}"
    
    cd frontend
    
    # 安装依赖
    pnpm install
    
    echo -e "${GREEN}✅ 前端依赖安装完成${NC}"
    
    cd ..
    echo ""
}

# 主函数
main() {
    check_requirements
    start_docker_services
    setup_env
    init_database
    build_backend
    install_frontend
    
    echo -e "${GREEN}========================================${NC}"
    echo -e "${GREEN}✅ 部署准备完成！${NC}"
    echo -e "${GREEN}========================================${NC}"
    echo ""
    echo -e "${BLUE}下一步：${NC}"
    echo -e "1. 编辑 backend/.env 文件，填入实际的 API Keys"
    echo -e "2. 启动后端服务: ${YELLOW}./scripts/start-backend.sh${NC}"
    echo -e "3. 启动前端服务: ${YELLOW}./scripts/start-frontend.sh${NC}"
    echo ""
    echo -e "或使用一键启动: ${YELLOW}./scripts/start-all.sh${NC}"
    echo ""
    echo -e "${BLUE}服务地址：${NC}"
    echo -e "  后端 API: ${GREEN}http://localhost:8080/api${NC}"
    echo -e "  前端 Web: ${GREEN}http://localhost:3000${NC}"
    echo -e "  MinIO 控制台: ${GREEN}http://localhost:9001${NC}"
    echo ""
}

# 执行主函数
main

