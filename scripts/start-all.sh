#!/bin/bash

# 启动所有服务脚本

cd "$(dirname "$0")/.."

echo "🚀 启动秒构AI所有服务..."

# 1. 启动 Docker 服务
echo "📦 启动 Docker 服务..."
docker-compose up -d postgres redis minio

# 等待服务就绪
echo "⏳ 等待 Docker 服务就绪..."
sleep 5

# 2. 启动后端服务（后台运行）
echo "🔨 启动后端服务..."
cd backend

# 加载环境变量
if [ -f .env ]; then
    echo "📄 加载环境变量 (.env)..."
    set -a
    source .env
    set +a
fi

nohup mvn spring-boot:run -Dspring-boot.run.profiles=dev > ../logs/backend.log 2>&1 &
BACKEND_PID=$!
echo "后端服务 PID: $BACKEND_PID"
echo $BACKEND_PID > ../logs/backend.pid

# 等待后端启动
echo "⏳ 等待后端服务启动..."
sleep 10

# 3. 启动前端服务（后台运行）
echo "🎨 启动前端服务..."
cd ../frontend
# 强制设置端口为 3000
export PORT=3000
nohup pnpm dev > ../logs/frontend.log 2>&1 &
FRONTEND_PID=$!
echo "前端服务 PID: $FRONTEND_PID"
echo $FRONTEND_PID > ../logs/frontend.pid

# 等待前端启动
echo "⏳ 等待前端服务启动..."
sleep 10

echo "✅ 所有服务已启动！"
echo "📊 后端服务: http://localhost:8080/api"
echo "🎨 前端服务: http://localhost:3000"
echo "📝 日志文件: ./logs/backend.log 和 ./logs/frontend.log"
echo ""
echo "停止服务: ./scripts/stop-all.sh"

