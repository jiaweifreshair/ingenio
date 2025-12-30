#!/bin/bash

# 启动前端服务脚本

set -e

cd "$(dirname "$0")/../frontend"

echo "🚀 启动秒构AI前端服务..."

# 检查 Node.js
if ! command -v node &> /dev/null; then
    echo "❌ 错误: 未找到 Node.js，请先安装 Node.js 18+"
    exit 1
fi

# 检查 pnpm
if ! command -v pnpm &> /dev/null; then
    echo "📦 安装 pnpm..."
    npm install -g pnpm
fi

# 检查依赖
if [ ! -d "node_modules" ]; then
    echo "📦 安装依赖..."
    pnpm install
fi

# 设置环境变量
export NEXT_PUBLIC_API_BASE_URL=${NEXT_PUBLIC_API_BASE_URL:-http://localhost:8080/api}
export PORT=${PORT:-3000}
# 说明：
# - 若仅监听 IPv4（例如 0.0.0.0 / 127.0.0.1），在部分环境中 localhost 可能优先解析到 IPv6（::1），
#   导致访问 http://localhost:${PORT} 出现连接拒绝。
# - 默认使用 IPv6 未指定地址 ::（Node 会同时接受 IPv6 与 IPv4-mapped），兼容 localhost 与 127.0.0.1。
# - 若你需要对外网卡暴露（局域网访问），可显式设置 NEXT_HOSTNAME=0.0.0.0。
export NEXT_HOSTNAME=${NEXT_HOSTNAME:-::}

echo "前端 API_BASE_URL: ${NEXT_PUBLIC_API_BASE_URL}"
echo "前端监听地址: ${NEXT_HOSTNAME}:${PORT}"
echo "访问地址建议："
echo "  - http://localhost:${PORT}"
echo "  - http://127.0.0.1:${PORT}（若 localhost 解析/IPv6 存在问题）"

# 预检：检查常见的“原生二进制依赖架构不匹配”问题（Apple Silicon / Rosetta 场景高发）
# NODE_PLATFORM=$(node -p "process.platform")
# NODE_ARCH=$(node -p "process.arch")
# if [ "${NODE_PLATFORM}" = "darwin" ] && [ -d "node_modules/.pnpm" ]; then
#     if ! ls "node_modules/.pnpm/@esbuild+darwin-${NODE_ARCH}@*" > /dev/null 2>&1; then
#         echo "❌ 检测到 esbuild 二进制依赖可能与当前 Node 架构(${NODE_ARCH})不匹配。"
#         echo "   建议执行：rm -rf node_modules && pnpm install"
#         exit 1
#     fi
#     if ! ls "node_modules/.pnpm/@next+swc-darwin-${NODE_ARCH}@*" > /dev/null 2>&1; then
#         echo "❌ 检测到 Next.js SWC 二进制依赖可能与当前 Node 架构(${NODE_ARCH})不匹配。"
#         echo "   建议执行：rm -rf node_modules && pnpm install"
#         exit 1
#     fi
# fi

# 启动开发服务器
echo "🔨 启动 Next.js 开发服务器..."
pnpm exec next dev -p "${PORT}" -H "${NEXT_HOSTNAME}"
