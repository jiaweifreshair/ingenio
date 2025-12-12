#!/bin/bash

# 检查所有服务状态的脚本

echo "🔍 检查秒构AI服务状态..."
echo ""

# 检查 Docker 服务
echo "📦 Docker 服务:"
docker-compose ps | grep -E "postgres|redis|minio" | awk '{print "  " $1 ": " $7}'
echo ""

# 检查后端服务
echo "🔨 后端服务:"
if curl -s -f http://localhost:8080/api/actuator/health > /dev/null 2>&1; then
    echo "  ✅ 运行正常 (http://localhost:8080/api)"
    curl -s http://localhost:8080/api/actuator/health | jq -r '.status // "UP"' 2>/dev/null || echo "  UP"
else
    echo "  ❌ 未运行"
fi
echo ""

# 检查前端服务
echo "🎨 前端服务:"
if curl -s -f http://localhost:3001 > /dev/null 2>&1; then
    echo "  ✅ 运行正常 (http://localhost:3001)"
else
    echo "  ❌ 未运行"
fi
echo ""

# 检查进程
echo "📊 运行进程:"
ps aux | grep -E "spring-boot|next dev" | grep -v grep | awk '{print "  PID: " $2 " - " $11 " " $12 " " $13}'
echo ""

echo "✅ 检查完成！"

