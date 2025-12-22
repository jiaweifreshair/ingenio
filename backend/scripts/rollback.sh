#!/bin/bash

# Ingenio数据库回滚脚本
# 用途：回滚所有数据库迁移（按逆序执行.down.sql脚本）

set -e  # 遇到错误立即退出

# 颜色定义
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# 读取环境变量
if [ -f "../.env" ]; then
    export $(cat ../.env | grep -v '^#' | xargs)
fi

# 默认配置
DB_HOST=${DB_HOST:-localhost}
DB_PORT=${DB_PORT:-5432}
DB_NAME=${DB_NAME:-ingenio_dev}
DB_USER=${DB_USER:-postgres}

echo -e "${YELLOW}=====================================================${NC}"
echo -e "${YELLOW}⚠️  Ingenio 数据库回滚工具${NC}"
echo -e "${YELLOW}=====================================================${NC}"
echo ""
echo -e "${RED}警告: 此操作将删除所有数据库表！${NC}"
echo -e "${YELLOW}数据库配置:${NC}"
echo -e "  主机: ${DB_HOST}:${DB_PORT}"
echo -e "  数据库: ${DB_NAME}"
echo -e "  用户: ${DB_USER}"
echo ""

# 确认操作
read -p "确认要回滚所有迁移吗？(输入 'yes' 确认): " confirm
if [ "$confirm" != "yes" ]; then
    echo -e "${YELLOW}操作已取消${NC}"
    exit 0
fi

# 检查数据库连接
echo -e "${YELLOW}检查数据库连接...${NC}"
if ! PGPASSWORD=$DB_PASSWORD psql -h $DB_HOST -p $DB_PORT -U $DB_USER -d $DB_NAME -c '\q' 2>/dev/null; then
    echo -e "${RED}❌ 无法连接到数据库${NC}"
    exit 1
fi
echo -e "${GREEN}✅ 数据库连接成功${NC}"
echo ""

# 执行回滚脚本（逆序）
echo -e "${YELLOW}执行回滚脚本...${NC}"
MIGRATION_DIR="../migrations"
ROLLBACK_COUNT=0

# 按逆序执行.down.sql脚本
for file in $(ls -r $MIGRATION_DIR/*.down.sql); do
    filename=$(basename "$file")
    echo -e "${YELLOW}回滚: $filename${NC}"

    if PGPASSWORD=$DB_PASSWORD psql -h $DB_HOST -p $DB_PORT -U $DB_USER -d $DB_NAME -f "$file" > /dev/null 2>&1; then
        echo -e "${GREEN}✅ $filename 回滚成功${NC}"
        ((ROLLBACK_COUNT++))
    else
        echo -e "${RED}❌ $filename 回滚失败${NC}"
        exit 1
    fi
done

echo ""
echo -e "${GREEN}=====================================================${NC}"
echo -e "${GREEN}🎉 回滚完成！共回滚 $ROLLBACK_COUNT 个迁移${NC}"
echo -e "${GREEN}=====================================================${NC}"
echo ""
echo -e "${YELLOW}剩余数据库表:${NC}"
PGPASSWORD=$DB_PASSWORD psql -h $DB_HOST -p $DB_PORT -U $DB_USER -d $DB_NAME -c "\dt"
echo ""
echo -e "${GREEN}✅ 数据库回滚成功完成${NC}"
