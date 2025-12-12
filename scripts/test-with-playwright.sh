#!/bin/bash

# 使用 Playwright MCP 进行自动化测试脚本

echo "🎭 使用 Playwright MCP 进行自动化测试..."

# 检查服务是否运行
check_service() {
    local url=$1
    local name=$2
    
    if curl -s -f "$url" > /dev/null 2>&1; then
        echo "✅ $name 服务运行正常: $url"
        return 0
    else
        echo "❌ $name 服务未运行: $url"
        return 1
    fi
}

# 检查后端服务
if ! check_service "http://localhost:8080/api/actuator/health" "后端"; then
    echo "⚠️  请先启动后端服务: ./scripts/start-backend.sh"
    exit 1
fi

# 检查前端服务
if ! check_service "http://localhost:3001" "前端"; then
    echo "⚠️  请先启动前端服务: ./scripts/start-frontend.sh"
    exit 1
fi

echo "✅ 所有服务运行正常，开始自动化测试..."

# 运行 Playwright 测试
cd frontend
pnpm run e2e

