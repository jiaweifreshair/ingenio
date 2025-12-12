#!/bin/bash

# 停止所有服务脚本

cd "$(dirname "$0")/.."

echo "🛑 停止秒构AI所有服务..."

# 停止后端服务
if [ -f logs/backend.pid ]; then
    BACKEND_PID=$(cat logs/backend.pid)
    if ps -p $BACKEND_PID > /dev/null 2>&1; then
        echo "停止后端服务 (PID: $BACKEND_PID)..."
        kill $BACKEND_PID
        rm logs/backend.pid
    fi
fi

# 停止前端服务
if [ -f logs/frontend.pid ]; then
    FRONTEND_PID=$(cat logs/frontend.pid)
    if ps -p $FRONTEND_PID > /dev/null 2>&1; then
        echo "停止前端服务 (PID: $FRONTEND_PID)..."
        kill $FRONTEND_PID
        rm logs/frontend.pid
    fi
fi

# 停止 Docker 服务（可选）
read -p "是否停止 Docker 服务? (y/N): " -n 1 -r
echo
if [[ $REPLY =~ ^[Yy]$ ]]; then
    echo "停止 Docker 服务..."
    docker-compose down
fi

echo "✅ 所有服务已停止！"

