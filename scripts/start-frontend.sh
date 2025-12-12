#!/bin/bash

# 启动前端服务脚本

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
export NEXT_PUBLIC_API_BASE_URL=http://localhost:8080/api
export PORT=3001

# 启动开发服务器
echo "🔨 启动 Next.js 开发服务器..."
pnpm dev

