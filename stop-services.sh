#!/bin/bash
# Ingenio 服务停止脚本
# 用途：一键停止所有服务

set -e

echo "================================================"
echo "  Ingenio 服务停止脚本"
echo "  时间: $(date +%Y-%m-%d\ %H:%M:%S)"
echo "================================================"
echo ""

GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m'

PROJECT_ROOT="/Users/apus/Documents/UGit/Ingenio"

# 1. 停止Spring Boot后端
echo -e "${YELLOW}[1/2] 停止Spring Boot后端服务...${NC}"
# 仅停止监听 8080 的进程，避免误杀“连接远端 :8080”的客户端进程
if lsof -nP -iTCP:8080 -sTCP:LISTEN > /dev/null 2>&1; then
    PID=$(lsof -t -iTCP:8080 -sTCP:LISTEN)
    echo "发现后端进程 (PID: $PID)，正在停止..."
    kill $PID 2>/dev/null || true
    sleep 3

    # 检查是否成功停止
    if lsof -nP -iTCP:8080 -sTCP:LISTEN > /dev/null 2>&1; then
        echo -e "${RED}强制停止后端服务...${NC}"
        kill -9 $PID 2>/dev/null || true
    fi
    echo -e "${GREEN}✓ 后端服务已停止${NC}"
else
    echo -e "${YELLOW}后端服务未运行${NC}"
fi

echo ""

# 2. 停止Docker服务
echo -e "${YELLOW}[2/2] 停止Docker服务...${NC}"
cd "$PROJECT_ROOT"
docker compose down

echo -e "${GREEN}✓ Docker服务已停止${NC}"
echo ""

# 3. 显示最终状态
echo "================================================"
echo -e "${GREEN}✓ 所有服务已停止${NC}"
echo ""
echo "服务状态:"
echo "  - 后端 (8080): $(lsof -i:8080 > /dev/null 2>&1 && echo -e '${RED}运行中${NC}' || echo -e '${GREEN}已停止${NC}')"
echo "  - Docker服务: $(docker compose ps 2>/dev/null | grep -q 'Up' && echo -e '${RED}运行中${NC}' || echo -e '${GREEN}已停止${NC}')"
echo ""
echo "================================================"
