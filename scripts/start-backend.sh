#!/bin/bash

# 启动后端服务脚本

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT_DIR/backend"

echo "🚀 启动秒构AI后端服务..."

# 加载环境变量（仅根目录 .env）
if [ -f "$ROOT_DIR/.env" ]; then
    echo "📄 加载环境变量 (.env)..."
    # 使用 set -a 自动导出变量，处理包含空格的值
    set -a
    source "$ROOT_DIR/.env"
    set +a
fi

# 检查 Java 版本
if ! command -v java &> /dev/null; then
    echo "❌ 错误: 未找到 Java，请先安装 Java 17+"
    exit 1
fi

JAVA_VERSION=$(java -version 2>&1 | head -n 1 | cut -d'"' -f2 | sed '/^1\./s///' | cut -d'.' -f1)
if [ "$JAVA_VERSION" -lt 17 ]; then
    echo "❌ 错误: Java 版本过低，需要 Java 17+，当前版本: $JAVA_VERSION"
    exit 1
fi

# 检查 Maven
if ! command -v mvn &> /dev/null; then
    echo "❌ 错误: 未找到 Maven，请先安装 Maven"
    exit 1
fi

# 检查数据库连接
echo "📊 检查数据库连接..."
echo "DB_HOST: ${DB_HOST:-localhost}"
echo "DB_PORT: ${DB_PORT:-5432}"
echo "DB_NAME: ${DB_NAME:-ingenio_dev}"

if ! docker ps | grep -q ingenio-postgres; then
    echo "⚠️  警告: PostgreSQL 容器未运行，请先启动: docker-compose up -d postgres"
fi

# 设置环境变量（宿主机运行覆盖为 localhost/ingenio_dev）
export SPRING_PROFILES_ACTIVE=dev
export DB_HOST=localhost
export DB_PORT="${DB_PORT:-5432}"
export DB_NAME=ingenio_dev
export DB_USER="${DB_USER:-postgres}"
export DB_PASSWORD="${DB_PASSWORD:-ingenio_20251122}"
export REDIS_HOST=localhost
export REDIS_PORT="${REDIS_PORT:-6379}"
export MINIO_ENDPOINT=http://localhost:9000
export MINIO_ACCESS_KEY="${MINIO_ACCESS_KEY:-minioadmin}"
export MINIO_SECRET_KEY="${MINIO_SECRET_KEY:-minioadmin}"
export MINIO_BUCKET_NAME="${MINIO_BUCKET_NAME:-ingenio-code}"

# 检查 API Key
if [ -z "$SPRING_AI_OPENAI_API_KEY" ] && [ -z "$QINIU_CLOUD_API_KEY" ] && [ -z "$DEEPSEEK_API_KEY" ]; then
    echo "⚠️  警告: 未检测到有效的 API Key (SPRING_AI_OPENAI_API_KEY / QINIU_CLOUD_API_KEY)"
    echo "    系统将使用默认占位符，这可能导致 AI 功能无法使用 (401 Invalid API Key)"
    export SPRING_AI_OPENAI_API_KEY=sk-placeholder
else
    echo "✅ 检测到 API Key 配置"
fi

# 启动服务
echo "🔨 编译并启动后端服务..."
# 禁用Java代理，避免SOCKS代理干扰数据库连接
mvn spring-boot:run \
  -Dspring-boot.run.profiles=dev \
  -Dspring-boot.run.jvmArguments="-Djava.net.preferIPv4Stack=true -Dhttp.proxyHost= -Dhttp.proxyPort= -Dhttps.proxyHost= -Dhttps.proxyPort= -DsocksProxyHost= -DsocksProxyPort="
