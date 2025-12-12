# Claude Code 多终端支持脚本

## 📋 功能说明

这两个脚本用于支持 Claude Code 在多个终端同时运行，并解决未开通 `context_management` Beta 特性的账号遇到的 400 错误。

### claude-multi.sh
- **包装脚本**：包装 `claude` 命令，自动注入补丁
- **配置复用**：使用项目根目录下的 `.claude` 配置目录
- **多终端支持**：支持多个终端同时运行 Claude Code
- **友好提示**：提供彩色输出和错误提示

### disable-context-management.js
- **Node.js 补丁**：拦截 fetch 请求，移除 `context_management` 相关字段
- **自动过滤**：过滤 betas 数组中的 `context-management-2025-06-27`
- **透明注入**：通过 `NODE_OPTIONS` 环境变量自动加载

---

## 🚀 使用方法

### 方法1：直接运行脚本（推荐）

在项目根目录下运行：

```bash
# 启动交互式对话
./scripts/claude-multi.sh chat

# 查看帮助
./scripts/claude-multi.sh --help

# 执行命令
./scripts/claude-multi.sh "帮我优化代码"
```

### 方法2：使用快捷命令

在项目根目录创建软链接（一次性操作）：

```bash
ln -sf scripts/claude-multi.sh cm
```

然后可以使用更短的命令：

```bash
./cm chat                    # 启动交互式对话
./cm "帮我优化代码"           # 执行命令
```

### 方法3：添加到 PATH（全局使用）

在 `~/.bashrc` 或 `~/.zshrc` 中添加：

```bash
# Claude Code 多终端支持
alias claude-multi='/Users/apus/Documents/UGit/Ingenio/scripts/claude-multi.sh'
```

重新加载配置：

```bash
source ~/.bashrc  # 或 source ~/.zshrc
```

然后在任何目录都可以使用：

```bash
claude-multi chat
```

---

## 📁 文件结构

```
Ingenio/
├── scripts/
│   ├── claude-multi.sh                    # 包装脚本（主入口）
│   ├── disable-context-management.js      # Node.js 补丁
│   └── README.md                          # 本文档
├── .claude/                               # Claude Code 配置目录
└── cm -> scripts/claude-multi.sh          # 快捷方式（可选）
```

---

## 🔧 工作原理

1. **配置目录复用**
   - 设置 `CLAUDE_CONFIG_DIR` 环境变量指向项目的 `.claude` 目录
   - 多个终端共享同一配置，避免重复配置

2. **补丁注入**
   - 通过 `NODE_OPTIONS` 环境变量注入 `disable-context-management.js`
   - 拦截所有 fetch 请求，自动移除 `context_management` 字段

3. **透明执行**
   - 使用 `exec` 替换当前进程，保持原有命令行参数
   - 用户感知不到中间包装层的存在

---

## ✅ 验证安装

运行以下命令验证脚本是否正常工作：

```bash
# 检查脚本是否有执行权限
ls -l scripts/claude-multi.sh

# 测试脚本（应该显示配置信息和启动 Claude Code）
./scripts/claude-multi.sh --version
```

预期输出：

```
✓ 项目根目录: /Users/apus/Documents/UGit/Ingenio
✓ 配置目录: /Users/apus/Documents/UGit/Ingenio/.claude
✓ 补丁已加载: disable-context-management.js

✓ 启动 Claude Code...

Claude Code v1.x.x
```

---

## 🐛 故障排查

### 问题1：权限被拒绝

```bash
chmod +x scripts/claude-multi.sh
```

### 问题2：找不到 claude 命令

```bash
npm install -g @anthropic-ai/claude-code
```

### 问题3：补丁文件不存在

确保 `disable-context-management.js` 在 `scripts/` 目录下：

```bash
ls -l scripts/disable-context-management.js
```

### 问题4：配置目录不存在

脚本会自动创建，如果失败可手动创建：

```bash
mkdir -p .claude
```

---

## 📝 开发者笔记

### 修改补丁逻辑

编辑 `disable-context-management.js` 文件，取消注释调试信息：

```javascript
// 启用调试日志
console.log('[Patch] disable-context-management.js 已加载');
console.debug('[Patch] 已移除 context_management 字段');
```

### 自定义配置目录

通过环境变量覆盖默认配置目录：

```bash
CLAUDE_CONFIG_DIR=~/.claude-custom ./scripts/claude-multi.sh chat
```

### 禁用补丁（测试用）

如果需要测试原始 Claude Code 行为，直接运行 `claude` 命令：

```bash
claude chat  # 不使用补丁
```

---

## 🤝 贡献

欢迎提交问题和改进建议！

**作者**: Ingenio Team  
**最后更新**: 2025-01-13
