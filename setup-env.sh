#!/bin/bash
# Ingenio 环境变量快速配置脚本

set -e

echo "╔════════════════════════════════════════════════════════════════╗"
echo "║          Ingenio 环境变量配置向导                              ║"
echo "╚════════════════════════════════════════════════════════════════╝"
echo ""

# 检查 .env.example 是否存在
if [ ! -f "backend/.env.example" ]; then
    echo "❌ 错误: backend/.env.example 文件不存在"
    echo "请确保在项目根目录运行此脚本"
    exit 1
fi

# 检查是否已存在 .env 文件
if [ -f "backend/.env" ]; then
    echo "⚠️  警告: backend/.env 文件已存在"
    read -p "是否覆盖现有配置？(y/N): " -n 1 -r
    echo
    if [[ ! $REPLY =~ ^[Yy]$ ]]; then
        echo "已取消配置"
        exit 0
    fi
    mv backend/.env backend/.env.backup
    echo "✓ 已备份现有配置到 backend/.env.backup"
fi

# 复制模板文件
cp backend/.env.example backend/.env
echo "✓ 已创建 backend/.env 文件"
echo ""

echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "  请手动编辑 backend/.env 文件，填入真实的配置值"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""
echo "必须配置的环境变量："
echo "  1. DB_PASSWORD          - 数据库密码"
echo "  2. QINIU_AI_API_KEY     - 七牛云API密钥（关键）"
echo "  3. JWT_SECRET_KEY       - JWT签名密钥（至少32字符）"
echo ""
echo "可选配置（使用默认值）："
echo "  • MINIO_ACCESS_KEY      - MinIO访问密钥"
echo "  • MINIO_SECRET_KEY      - MinIO密钥"
echo "  • MAIL_PASSWORD         - 邮箱授权码"
echo ""
echo "详细配置指南："
echo "  → backend/ENVIRONMENT_SETUP.md"
echo ""
echo "快速编辑命令："
echo "  vim backend/.env"
echo "  或"
echo "  code backend/.env"
echo ""

# 提示生成JWT密钥
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "  生成强随机JWT密钥（推荐）："
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""
if command -v openssl &> /dev/null; then
    JWT_KEY=$(openssl rand -base64 32)
    echo "已生成JWT密钥："
    echo "  $JWT_KEY"
    echo ""
    echo "复制上面的密钥到 backend/.env 中的 JWT_SECRET_KEY="
else
    echo "未安装 openssl，请手动生成32字符以上的随机密钥"
fi
echo ""

# 验证 .gitignore
if git check-ignore -q backend/.env; then
    echo "✅ 验证通过: backend/.env 已被 .gitignore 排除"
else
    echo "⚠️  警告: backend/.env 可能未被 .gitignore 排除"
    echo "请检查 .gitignore 文件"
fi
echo ""

echo "配置完成后，请运行："
echo "  cd backend && mvn spring-boot:run"
echo ""
