#!/bin/bash

# 任务3：前端接口联调一键验证脚本（真实后端）
#
# 做什么：
# - 启动/确认 Docker 依赖（Postgres/Redis/MinIO）
# - 启动 Spring Boot 后端（8080/api）
# - 运行 Playwright 用例：frontend/src/e2e/task3-auth-integration.spec.ts
# - 额外校验 trace_id 是否写入后端日志文件
#
# 为什么：
# - 避免“前端 Ready 但端口不可达 / 后端未启动 / traceId 未贯通”等问题反复人工排查
#
# 用法（仓库根目录执行）：
# - `./scripts/verify-task3-e2e.sh`
# - 可选环境变量：
#   - `E2E_USERNAME` / `E2E_PASSWORD`：默认 justin / qazOKM123
#   - `NEXT_PUBLIC_API_BASE_URL`：默认 http://127.0.0.1:8080/api

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
LOG_DIR="${ROOT_DIR}/logs"
BACKEND_DIR="${ROOT_DIR}/backend"
FRONTEND_DIR="${ROOT_DIR}/frontend"

mkdir -p "${LOG_DIR}"

E2E_USERNAME="${E2E_USERNAME:-justin}"
E2E_PASSWORD="${E2E_PASSWORD:-qazOKM123}"
NEXT_PUBLIC_API_BASE_URL="${NEXT_PUBLIC_API_BASE_URL:-http://127.0.0.1:8080/api}"

BACKEND_HEALTH_URL="http://127.0.0.1:8080/api/actuator/health"
BACKEND_LOGIN_URL="http://127.0.0.1:8080/api/v1/auth/login"

BACKEND_STDOUT_LOG="${LOG_DIR}/backend-task3.log"

echo "🚀 Task3 联调验证开始"
echo "  - 后端: ${NEXT_PUBLIC_API_BASE_URL}"
echo "  - 前端: http://127.0.0.1:3000"
echo "  - 账号: ${E2E_USERNAME}"

cleanup() {
  if [ -n "${BACKEND_PID:-}" ]; then
    kill "${BACKEND_PID}" >/dev/null 2>&1 || true
    wait "${BACKEND_PID}" >/dev/null 2>&1 || true
  fi

  # 兜底：spring-boot:run 在部分环境可能残留子进程，额外按端口清理
  if command -v lsof >/dev/null 2>&1; then
    local pids
    pids="$(lsof -nP -iTCP:8080 -sTCP:LISTEN -t 2>/dev/null || true)"
    if [ -n "${pids}" ]; then
      for pid in ${pids}; do
        local cmd
        cmd="$(ps -p "${pid}" -o command= 2>/dev/null || true)"
        if echo "${cmd}" | grep -Eqi 'com\\.ingenio\\.backend|ingenio-backend|IngenioBackendApplication'; then
          kill "${pid}" >/dev/null 2>&1 || true
        fi
      done
    fi
  fi
}
trap cleanup EXIT

echo "📦 确认 Docker 依赖服务..."
if command -v docker-compose >/dev/null 2>&1; then
  (cd "${ROOT_DIR}" && docker-compose up -d postgres redis minio)
else
  echo "⚠️ 未找到 docker-compose，跳过依赖启动（请确保 Postgres/Redis/MinIO 已就绪）"
fi

echo "🔨 启动后端（Spring Boot / dev profile）..."
rm -f "${BACKEND_STDOUT_LOG}"

# 避免 8080 端口被残留后端占用导致“健康检查误命中旧进程”
if command -v lsof >/dev/null 2>&1; then
  EXISTING_PIDS="$(lsof -nP -iTCP:8080 -sTCP:LISTEN -t 2>/dev/null || true)"
  if [ -n "${EXISTING_PIDS}" ]; then
    echo "⚠️ 检测到 8080 端口已被占用，尝试清理可能残留的后端进程..."
    for pid in ${EXISTING_PIDS}; do
      cmd="$(ps -p "${pid}" -o command= 2>/dev/null || true)"
      echo "  - pid=${pid} cmd=${cmd}"
      if echo "${cmd}" | grep -Eqi 'com\\.ingenio\\.backend|ingenio-backend|IngenioBackendApplication'; then
        kill "${pid}" >/dev/null 2>&1 || true
      else
        echo "❌ 8080 被未知进程占用（pid=${pid}），请先手动释放端口再重试。"
        exit 1
      fi
    done
    sleep 1
  fi
fi

if [ -f "${BACKEND_DIR}/.env" ]; then
  set -a
  # shellcheck disable=SC1090
  source "${BACKEND_DIR}/.env"
  set +a
fi

(cd "${BACKEND_DIR}" && mvn -q spring-boot:run -Dspring-boot.run.profiles=dev -Dspring-boot.run.fork=false) >"${BACKEND_STDOUT_LOG}" 2>&1 &
BACKEND_PID=$!

echo "⏳ 等待后端健康检查..."
for i in {1..180}; do
  if ! kill -0 "${BACKEND_PID}" >/dev/null 2>&1; then
    echo "❌ 后端进程已退出，请检查日志: ${BACKEND_STDOUT_LOG}"
    tail -200 "${BACKEND_STDOUT_LOG}" || true
    exit 1
  fi
  if curl -sf --max-time 2 "${BACKEND_HEALTH_URL}" >/dev/null 2>&1; then
    echo "✓ 后端已就绪"
    break
  fi
  sleep 1
done

echo "🎭 运行前端 Playwright 联调用例..."
(cd "${FRONTEND_DIR}" && \
  E2E_TASK3=1 \
  E2E_USERNAME="${E2E_USERNAME}" \
  E2E_PASSWORD="${E2E_PASSWORD}" \
  NEXT_PUBLIC_API_BASE_URL="${NEXT_PUBLIC_API_BASE_URL}" \
  pnpm e2e:chromium -- src/e2e/task3-auth-integration.spec.ts)

echo "🔎 校验 trace_id 是否写入后端日志..."
TRACE_ID="trace-task3-$(date +%s)-${RANDOM}"

curl -sS \
  -H "Content-Type: application/json" \
  -H "X-Trace-Id: ${TRACE_ID}" \
  -d "{\"usernameOrEmail\":\"${E2E_USERNAME}\",\"password\":\"${E2E_PASSWORD}\"}" \
  "${BACKEND_LOGIN_URL}" >/dev/null

sleep 1

BACKEND_FILE_LOG="${BACKEND_DIR}/logs/ingenio-backend.log"
if [ -f "${BACKEND_FILE_LOG}" ] && grep -F "${TRACE_ID}" "${BACKEND_FILE_LOG}" >/dev/null 2>&1; then
  echo "✓ trace_id 已写入后端文件日志: ${TRACE_ID}"
elif [ -f "${BACKEND_STDOUT_LOG}" ] && grep -F "${TRACE_ID}" "${BACKEND_STDOUT_LOG}" >/dev/null 2>&1; then
  echo "✓ trace_id 已写入后端标准输出日志: ${TRACE_ID}"
  echo "⚠️ 未命中文件日志，建议确认 backend/src/main/resources/application.yml 的 logging.pattern.file 已包含 [%X{traceId}]"
else
  echo "❌ 未在后端日志中找到 trace_id: ${TRACE_ID}"
  echo "   - 后端文件日志: ${BACKEND_FILE_LOG}"
  echo "   - 后端标准输出: ${BACKEND_STDOUT_LOG}"
  exit 1
fi

echo "✅ Task3 联调验证通过"
