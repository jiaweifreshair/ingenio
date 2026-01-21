#!/bin/bash
# Ingenio 服务启动脚本
# 用途：一键启动所有依赖服务和后端服务

set -e  # 遇到错误立即退出

echo "================================================"
echo "  Ingenio 服务启动脚本"
echo "  时间: $(date +%Y-%m-%d\ %H:%M:%S)"
echo "================================================"
echo ""

# 颜色定义
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m' # No Color

# 项目根目录
PROJECT_ROOT="/Users/apus/Documents/UGit/Ingenio"
BACKEND_DIR="$PROJECT_ROOT/backend"
FRONTEND_DIR="$PROJECT_ROOT/frontend"

# 0. 加载环境变量（本地 .env，不入库的密钥/配置）
echo -e "${YELLOW}[0/6] 加载环境变量 (.env)...${NC}"
if [ -f "$PROJECT_ROOT/.env" ]; then
    set -a
    # shellcheck disable=SC1090
    source "$PROJECT_ROOT/.env"
    set +a
    echo -e "${GREEN}✓ 已加载: $PROJECT_ROOT/.env${NC}"
else
    echo -e "${YELLOW}⚠ 未找到: $PROJECT_ROOT/.env（如需AI能力，请配置QINIU_CLOUD_API_KEY/DEEPSEEK_API_KEY/DASHSCOPE_API_KEY等）${NC}"
fi

if [ -f "$BACKEND_DIR/.env" ]; then
    set -a
    # shellcheck disable=SC1090
    source "$BACKEND_DIR/.env"
    set +a
    echo -e "${GREEN}✓ 已加载: $BACKEND_DIR/.env${NC}"
fi

echo ""

# 1. 检查Docker是否运行
echo -e "${YELLOW}[1/6] 检查Docker状态...${NC}"
if ! docker info > /dev/null 2>&1; then
    echo -e "${RED}错误: Docker未运行，正在启动Docker Desktop...${NC}"
    open -a Docker
    echo "等待Docker启动（30秒）..."
    sleep 30
fi
echo -e "${GREEN}✓ Docker已运行${NC}"
echo ""

# 2. 启动依赖服务（PostgreSQL, Redis, MinIO）
echo -e "${YELLOW}[2/6] 启动依赖服务（PostgreSQL, Redis, MinIO）...${NC}"
cd "$PROJECT_ROOT"
docker compose up -d postgres redis minio

# 等待服务健康检查
echo "等待服务健康检查（15秒）..."
sleep 15

# 检查服务状态
echo -e "${GREEN}服务状态:${NC}"
docker compose ps

echo ""

# 3. 验证服务健康
echo -e "${YELLOW}[3/6] 验证服务健康...${NC}"

# 检查PostgreSQL
if docker exec ingenio-postgres pg_isready -U ingenio_user > /dev/null 2>&1; then
    echo -e "${GREEN}✓ PostgreSQL: 健康${NC}"
else
    echo -e "${RED}✗ PostgreSQL: 不健康${NC}"
    exit 1
fi

# 检查Redis
if docker exec ingenio-redis redis-cli ping > /dev/null 2>&1; then
    echo -e "${GREEN}✓ Redis: 健康${NC}"
else
    echo -e "${RED}✗ Redis: 不健康${NC}"
    exit 1
fi

# 检查MinIO（可能需要更长时间启动）
echo -e "${YELLOW}等待MinIO启动...${NC}"
for i in {1..10}; do
    if curl -sf http://localhost:9000/minio/health/live > /dev/null 2>&1; then
        echo -e "${GREEN}✓ MinIO: 健康${NC}"
        break
    fi
    if [ $i -eq 10 ]; then
        echo -e "${YELLOW}⚠ MinIO: 启动中（可能需要更多时间）${NC}"
    fi
    sleep 3
done

echo ""

# 4. 启动后端服务
echo -e "${YELLOW}[4/6] 启动Spring Boot后端服务...${NC}"
cd "$BACKEND_DIR"

# 清除代理环境变量，防止 localhost 连接被代理拦截导致 502 错误
echo -e "${YELLOW}配置网络环境 (清除代理设置)...${NC}"
unset ALL_PROXY
unset http_proxy
unset https_proxy
unset MINIO_ENDPOINT # 确保不使用环境变量中的 minio:9000
unset SPRING_PROFILES_ACTIVE # 确保不使用环境变量中的 docker 配置
export NO_PROXY=localhost,127.0.0.1,::1,.local

# 检查是否已有后端进程运行（仅判断监听端口，避免误判“访问远端 :8080 的连接”）
if lsof -nP -iTCP:8080 -sTCP:LISTEN > /dev/null 2>&1; then
    echo -e "${YELLOW}⚠ 端口8080已被占用（LISTEN），跳过后端启动${NC}"
else
    # 清理旧日志
    rm -f /tmp/backend.log

    # 后台启动Spring Boot
    nohup mvn spring-boot:run -Dspring-boot.run.profiles=local \
        -Dhttp.proxyHost= -Dhttp.proxyPort= \
        -Dhttps.proxyHost= -Dhttps.proxyPort= \
        -DsocksProxyHost= -DsocksProxyPort= \
        > /tmp/backend.log 2>&1 &
    BACKEND_PID=$!

    echo "后端服务启动中（PID: $BACKEND_PID）..."
    echo "等待启动（60秒）..."

    # 等待启动完成
    for i in {1..60}; do
        if curl -sf http://localhost:8080/api/actuator/health > /dev/null 2>&1; then
            echo -e "${GREEN}✓ 后端服务启动成功！${NC}"
            break
        fi
        if [ $i -eq 60 ]; then
            echo -e "${RED}✗ 后端服务启动超时，请检查日志: /tmp/backend.log${NC}"
            tail -50 /tmp/backend.log
            exit 1
        fi
        sleep 1
    done
fi

echo ""

# 5. 检查OpenLovable-CN服务
echo -e "${YELLOW}[5/6] 检查OpenLovable-CN服务...${NC}"
OPENLOVABLE_BASE_URL=${OPENLOVABLE_BASE_URL:-http://localhost:3001}
OPENLOVABLE_HEALTH_URL="${OPENLOVABLE_BASE_URL}/api/health"
if curl -sf --max-time 5 "$OPENLOVABLE_HEALTH_URL" > /dev/null 2>&1; then
    echo -e "${GREEN}✓ OpenLovable: 运行中 (${OPENLOVABLE_BASE_URL})${NC}"
else
    echo -e "${YELLOW}⚠ 未检测到OpenLovable服务 (${OPENLOVABLE_BASE_URL})${NC}"
    echo -e "  → 请在另一个终端启动OpenLovable-CN，确保原型预览可以在5-10秒内生成"
    echo -e "  → 可参考 docs/TECHNICAL_DOCUMENTATION.md#8.1 或设置 OPENLOVABLE_BASE_URL 环境变量"
fi
echo ""

# 6. 服务状态摘要
echo -e "${YELLOW}[6/6] 服务状态摘要${NC}"
echo "================================================"

# 后端健康检查
echo -e "\n${GREEN}后端服务 (Spring Boot):${NC}"
curl -s http://localhost:8080/api/actuator/health | python3 -c "
import sys, json
try:
    data = json.load(sys.stdin)
    print(f'  状态: {data[\"status\"]}')
    for name, comp in data.get('components', {}).items():
        status = comp.get('status', 'UNKNOWN')
        print(f'  - {name}: {status}')
except:
    print('  状态: 无法获取')
" || echo "  状态: 无法获取"

echo ""
echo -e "${GREEN}依赖服务 (Docker):${NC}"
echo "  - PostgreSQL: localhost:5432"
echo "  - Redis: localhost:6379"
echo "  - MinIO: localhost:9000 (控制台: localhost:9001)"

echo ""
echo -e "${GREEN}API端点:${NC}"
echo "  - 健康检查: http://localhost:8080/api/actuator/health"
echo "  - Swagger UI: http://localhost:8080/api/swagger-ui.html"
echo "  - API文档: http://localhost:8080/api/api-docs"

echo ""
echo -e "${GREEN}日志文件:${NC}"
echo "  - 后端日志: /tmp/backend.log"
echo "  - 查看日志: tail -f /tmp/backend.log"

echo ""
echo "================================================"
echo -e "${GREEN}✓ 所有服务已启动！${NC}"
echo ""
echo -e "${YELLOW}下一步操作:${NC}"
echo "  1. 启动前端: cd $FRONTEND_DIR && pnpm dev"
echo "  2. 访问前端: http://localhost:3000"
echo "  3. 查看API文档: http://localhost:8080/api/swagger-ui.html"
echo ""
echo "================================================"
