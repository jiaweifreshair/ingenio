#!/bin/bash

# 启动所有服务脚本

set -e

cd "$(dirname "$0")/.."

echo "🚀 启动秒构AI所有服务..."

# 0. 加载根目录环境变量（本地 .env，不入库）
if [ -f .env ]; then
    echo "📄 加载环境变量 (./.env)..."
    set -a
    # shellcheck disable=SC1091
    source .env
    set +a
fi

# 0.1 清除代理环境变量，避免本机 localhost 请求被代理拦截（常见于 Clash/Surge 等）
unset ALL_PROXY
unset all_proxy
unset HTTP_PROXY
unset HTTPS_PROXY
unset http_proxy
unset https_proxy
export NO_PROXY=${NO_PROXY:-localhost,127.0.0.1,::1,.local}
export no_proxy=${no_proxy:-localhost,127.0.0.1,::1,.local}

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
# 强制设置端口为 3000（可通过环境变量覆盖）
export PORT=${PORT:-3000}
# 默认使用 :: 以兼容 localhost(IPv6) 与 127.0.0.1(IPv4)
export NEXT_HOSTNAME=${NEXT_HOSTNAME:-::}
export NEXT_PUBLIC_API_BASE_URL=${NEXT_PUBLIC_API_BASE_URL:-http://localhost:8080/api}

nohup pnpm exec next dev -p "${PORT}" -H "${NEXT_HOSTNAME}" > ../logs/frontend.log 2>&1 &
FRONTEND_PID=$!
echo "前端服务 PID: $FRONTEND_PID"
echo $FRONTEND_PID > ../logs/frontend.pid

# 等待前端启动，并进行可访问性校验（避免“日志显示Ready但实际端口未监听”的误判）
echo "⏳ 等待前端服务启动..."
for i in {1..30}; do
    if ! kill -0 "$FRONTEND_PID" > /dev/null 2>&1; then
        echo "❌ 前端进程已退出，请检查日志: ./logs/frontend.log"
        tail -50 ../logs/frontend.log || true
        exit 1
    fi
    if curl -sf --noproxy '*' --max-time 2 "http://localhost:${PORT}/" > /dev/null 2>&1 || curl -sf --noproxy '*' --max-time 2 "http://127.0.0.1:${PORT}/" > /dev/null 2>&1; then
        echo "✓ 前端服务启动成功"
        break
    fi
    if [ "$i" -eq 30 ]; then
        echo "❌ 前端服务启动超时或不可访问，请检查日志: ./logs/frontend.log"
        tail -50 ../logs/frontend.log || true
        exit 1
    fi
    sleep 1
done

echo "✅ 所有服务已启动！"
echo "📊 后端服务: http://localhost:8080/api"
echo "🎨 前端服务: http://localhost:3000"
echo "📝 日志文件: ./logs/backend.log 和 ./logs/frontend.log"
echo ""
echo "停止服务: ./scripts/stop-all.sh"
