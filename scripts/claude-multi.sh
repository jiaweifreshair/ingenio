#!/usr/bin/env bash
# Claude Code 多终端支持包装脚本
# 功能：
# 1. 复用当前仓库的 .claude 配置目录
# 2. 禁用 context_management 特性（避免未开通账号触发400错误）
# 3. 支持多个终端同时运行 Claude Code
#
# 使用方法：
#   ./scripts/claude-multi.sh [claude命令参数]
#
# 示例：
#   ./scripts/claude-multi.sh chat            # 启动交互式对话
#   ./scripts/claude-multi.sh --help          # 查看帮助
#
set -euo pipefail

# 颜色定义（用于美化输出）
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

# 错误处理函数
error() {
  echo -e "${RED}❌ 错误: $1${NC}" >&2
  exit 1
}

info() {
  echo -e "${GREEN}✓${NC} $1"
}

warn() {
  echo -e "${YELLOW}⚠${NC} $1"
}

# 获取项目根目录（脚本所在目录的上级目录）
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"

# 补丁文件路径
PATCH_FILE="$SCRIPT_DIR/disable-context-management.js"

# 检查补丁文件是否存在
if [[ ! -f "$PATCH_FILE" ]]; then
  error "补丁文件不存在: $PATCH_FILE"
fi

# 检查 claude 命令是否可用
if ! command -v claude &> /dev/null; then
  error "未找到 claude 命令，请先安装 Claude Code: npm install -g @anthropic-ai/claude-code"
fi

# 设置配置目录（使用项目根目录下的 .claude）
export CLAUDE_CONFIG_DIR="${CLAUDE_CONFIG_DIR:-$ROOT_DIR/.claude}"

# 确保配置目录存在
if [[ ! -d "$CLAUDE_CONFIG_DIR" ]]; then
  warn "配置目录不存在，将创建: $CLAUDE_CONFIG_DIR"
  mkdir -p "$CLAUDE_CONFIG_DIR"
fi

# 注入 Node.js 补丁（禁用 context_management）
if [[ -n "${NODE_OPTIONS:-}" ]]; then
  export NODE_OPTIONS="--require \"$PATCH_FILE\" $NODE_OPTIONS"
else
  export NODE_OPTIONS="--require \"$PATCH_FILE\""
fi

# 显示配置信息
info "项目根目录: $ROOT_DIR"
info "配置目录: $CLAUDE_CONFIG_DIR"
info "补丁已加载: disable-context-management.js"

# 执行 claude 命令（将所有参数传递给 claude）
echo ""
info "启动 Claude Code..."
echo ""

exec claude "$@"
