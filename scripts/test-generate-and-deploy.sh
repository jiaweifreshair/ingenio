#!/bin/bash

# 自动化生成并部署测试脚本
# 用途：验证全栈代码生成、构建和部署流程

set -e

# 配置
API_BASE="http://localhost:8080/api/v1"
OUTPUT_DIR="deployments"
TIMESTAMP=$(date +%Y%m%d%H%M%S)
DEPLOY_DIR="$OUTPUT_DIR/app_$TIMESTAMP"

# 颜色
GREEN='\033[0;32m'
RED='\033[0;31m'
NC='\033[0m'

echo -e "${GREEN}🚀 开始自动化生成与部署流程...${NC}"

# 1. 认证 (注册/登录)
echo -e "${GREEN}Step 0: 获取认证Token...${NC}"
EMAIL="justin@ingenio.com"
PASSWORD="qazOKM123"
USERNAME="justin"

# 尝试登录
echo "尝试登录用户: $USERNAME"
LOGIN_RES=$(curl -s -X POST "$API_BASE/auth/login" \
  -H "Content-Type: application/json" \
  -d "{
    \"usernameOrEmail\": \"$USERNAME\",
    \"password\": \"$PASSWORD\"
  }")
TOKEN=$(echo $LOGIN_RES | grep -o '"token":"[^"]*"' | cut -d'"' -f4)

# 如果登录失败（Token为空），尝试注册
if [ -z "$TOKEN" ] || [ "$TOKEN" == "null" ]; then
    echo "登录失败，尝试注册用户..."
    REG_RES=$(curl -s -X POST "$API_BASE/auth/register" \
      -H "Content-Type: application/json" \
      -d "{
        \"email\": \"$EMAIL\",
        \"password\": \"$PASSWORD\",
        \"username\": \"$USERNAME\"
      }")
    TOKEN=$(echo $REG_RES | grep -o '"token":"[^"]*"' | cut -d'"' -f4)
    
    if [ -z "$TOKEN" ] || [ "$TOKEN" == "null" ]; then
        echo -e "${RED}❌ 认证失败 (登录和注册均失败)${NC}"
        echo "登录响应: $LOGIN_RES"
        echo "注册响应: $REG_RES"
        exit 1
    fi
fi

echo "✅ 获取Token成功: ${TOKEN:0:10}..."

# 2. 发起生成请求
echo -e "${GREEN}Step 1: 发起生成请求...${NC}"
REQUIREMENT="创建一个简单的待办事项管理系统(Todo List)，包含任务名称、描述、截止日期、是否完成状态。支持增删改查。"

RESPONSE=$(curl -s -X POST "$API_BASE/generate/async" \
  -H "Content-Type: application/json" \
  -H "Authorization: $TOKEN" \
  -d "{
    \"userRequirement\": \"$REQUIREMENT\",
    \"platform\": \"WEB\",
    \"generatePreview\": true,
    \"packageName\": \"com.ingenio.generated.todo\",
    \"appName\": \"TodoApp\"
  }")

# 提取TaskId (假设返回格式: {"code":200, "data":"task-uuid", ...})
TASK_ID=$(echo $RESPONSE | grep -o '"data":"[^"]*"' | cut -d'"' -f4)

if [ -z "$TASK_ID" ]; then
    echo -e "${RED}❌ 生成请求失败: $RESPONSE${NC}"
    exit 1
fi

echo "✅ 任务已创建，TaskId: $TASK_ID"

# 3. 轮询任务状态
echo -e "${GREEN}Step 2: 等待生成完成...${NC}"
STATUS="executing"
DOWNLOAD_URL=""

while [ "$STATUS" != "completed" ] && [ "$STATUS" != "failed" ]; do
    sleep 2
    STATUS_RES=$(curl -s "$API_BASE/generate/status/$TASK_ID" -H "Authorization: $TOKEN")
    STATUS=$(echo $STATUS_RES | grep -o '"status":"[^"]*"' | cut -d'"' -f4)
    echo "当前状态: $STATUS"
    
    if [ "$STATUS" == "completed" ]; then
        DOWNLOAD_URL=$(echo $STATUS_RES | grep -o '"downloadUrl":"[^"]*"' | cut -d'"' -f4)
    fi
done

if [ "$STATUS" == "failed" ]; then
    echo -e "${RED}❌ 生成失败${NC}"
    exit 1
fi

echo -e "${GREEN}✅ 生成完成，下载地址: $DOWNLOAD_URL${NC}"

# 4. 下载并解压
echo -e "${GREEN}Step 3: 下载并解压代码...${NC}"
mkdir -p "$DEPLOY_DIR"
# 注意：如果是MinIO本地地址(localhost:9000)，可能需要处理Docker网络问题
# 这里假设直接访问localhost:9000是通的
curl -s -o "$DEPLOY_DIR/app.zip" "$DOWNLOAD_URL"
unzip -q "$DEPLOY_DIR/app.zip" -d "$DEPLOY_DIR/source"

echo "代码已解压至: $DEPLOY_DIR/source"

# 5. 编译后端
echo -e "${GREEN}Step 4: 编译后端服务...${NC}"
cd "$DEPLOY_DIR/source/backend"
# 赋予mvnw执行权限（如果生成了mvnw），否则使用系统mvn
mvn clean package -DskipTests
BACKEND_JAR=$(find target -name "*.jar" | head -n 1)

# 启动后端 (后台运行)
echo -e "${GREEN}Step 5: 启动后端服务 (Port 8081)...${NC}"
nohup java -jar -Dserver.port=8081 "$BACKEND_JAR" > ../backend.log 2>&1 &
BACKEND_PID=$!
echo "Backend PID: $BACKEND_PID"

# 等待后端启动
echo "等待后端启动..."
sleep 15 # 简单等待，生产环境应使用健康检查

# 6. 编译前端
echo -e "${GREEN}Step 6: 编译前端应用...${NC}"
cd "../frontend"
# 使用pnpm或npm
npm install
npm run build

# 启动前端 (后台运行)
echo -e "${GREEN}Step 7: 启动前端应用 (Port 3001)...${NC}"
# 设置API地址指向刚才启动的后端
export NEXT_PUBLIC_API_URL="http://localhost:8081/api/v1"
nohup npm start -- -p 3001 > ../frontend.log 2>&1 &
FRONTEND_PID=$!
echo "Frontend PID: $FRONTEND_PID"

echo -e "${GREEN}✅ 部署完成!${NC}"
echo -e "后端地址: http://localhost:8081"
echo -e "前端地址: http://localhost:3001"
echo -e "日志文件: $DEPLOY_DIR/backend.log, $DEPLOY_DIR/frontend.log"
echo -e "停止服务命令: kill $BACKEND_PID $FRONTEND_PID"

