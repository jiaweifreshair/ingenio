#!/bin/bash

# Ingenio数据库迁移脚本
# 用途：自动执行所有数据库迁移脚本

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

echo -e "${GREEN}=====================================================${NC}"
echo -e "${GREEN}🚀 Ingenio 数据库迁移工具${NC}"
echo -e "${GREEN}=====================================================${NC}"
echo ""
echo -e "${YELLOW}数据库配置:${NC}"
echo -e "  主机: ${DB_HOST}:${DB_PORT}"
echo -e "  数据库: ${DB_NAME}"
echo -e "  用户: ${DB_USER}"
echo ""

# 检查PostgreSQL是否可连接
echo -e "${YELLOW}检查数据库连接...${NC}"
if ! PGPASSWORD=$DB_PASSWORD psql -h $DB_HOST -p $DB_PORT -U $DB_USER -d postgres -c '\q' 2>/dev/null; then
    echo -e "${RED}❌ 无法连接到PostgreSQL服务器${NC}"
    exit 1
fi
echo -e "${GREEN}✅ 数据库连接成功${NC}"
echo ""

# 检查数据库是否存在，不存在则创建
echo -e "${YELLOW}检查数据库是否存在...${NC}"
if ! PGPASSWORD=$DB_PASSWORD psql -h $DB_HOST -p $DB_PORT -U $DB_USER -lqt | cut -d \| -f 1 | grep -qw $DB_NAME; then
    echo -e "${YELLOW}数据库不存在，正在创建...${NC}"
    PGPASSWORD=$DB_PASSWORD createdb -h $DB_HOST -p $DB_PORT -U $DB_USER $DB_NAME
    echo -e "${GREEN}✅ 数据库创建成功${NC}"
else
    echo -e "${GREEN}✅ 数据库已存在${NC}"
fi
echo ""

# 执行迁移脚本
echo -e "${YELLOW}执行迁移脚本...${NC}"
MIGRATION_DIR="../migrations"
MIGRATION_COUNT=0

for file in $MIGRATION_DIR/*.sql; do
    # 跳过回滚脚本
    if [[ $file =~ \.down\.sql$ ]]; then
        continue
    fi

    filename=$(basename "$file")
    echo -e "${YELLOW}执行: $filename${NC}"

    if PGPASSWORD=$DB_PASSWORD psql -h $DB_HOST -p $DB_PORT -U $DB_USER -d $DB_NAME -f "$file" > /dev/null 2>&1; then
        echo -e "${GREEN}✅ $filename 执行成功${NC}"
        ((MIGRATION_COUNT++))
    else
        echo -e "${RED}❌ $filename 执行失败${NC}"
        exit 1
    fi
done

echo ""
echo -e "${GREEN}=====================================================${NC}"
echo -e "${GREEN}🎉 迁移完成！共执行 $MIGRATION_COUNT 个迁移脚本${NC}"
echo -e "${GREEN}=====================================================${NC}"
echo ""
echo -e "${YELLOW}数据库表清单:${NC}"
PGPASSWORD=$DB_PASSWORD psql -h $DB_HOST -p $DB_PORT -U $DB_USER -d $DB_NAME -c "\dt"
echo ""
echo -e "${GREEN}✅ 数据库迁移成功完成${NC}"
