# npx执行Chrome DevTools MCP - 速查表

## 🎯 一句话解释

**npx = 临时下载并运行npm包，无需全局安装**

```bash
npx chrome-devtools-mcp@latest
# ↓ 等价于
# 1. npm下载 chrome-devtools-mcp 到缓存
# 2. 运行该包
# 3. 不污染全局环境
```

---

## ⚡ 30秒快速配置

### Step 1: 确认Node.js版本
```bash
node --version
# 需要 ≥ 22.0.0
```

### Step 2: 添加配置
编辑 `~/.claude.json`:
```json
{
  "mcpServers": {
    "chrome-devtools": {
      "type": "stdio",
      "command": "npx",
      "args": ["chrome-devtools-mcp@latest"]
    }
  }
}
```

### Step 3: 预热缓存（可选但推荐）
```bash
npx chrome-devtools-mcp@latest --version
# 首次下载需30-60秒，后续启动只需0.3秒
```

### Step 4: 启动Claude
```bash
claude
```

---

## 🔍 工作原理（简化版）

```
用户启动Claude Code
         ↓
Claude读取配置发现chrome-devtools
         ↓
执行: npx chrome-devtools-mcp@latest
         ↓
npx检查缓存 (~/.npm/_npx/)
    ├─ 有缓存 → 直接运行 (0.3秒)
    └─ 无缓存 → 下载后运行 (2-6秒)
         ↓
启动MCP Server进程
         ↓
通过stdin/stdout与Claude通信
         ↓
准备就绪！可以使用 @chrome-devtools
```

---

## 📊 性能对比

| 场景 | 时间 | 优化方案 |
|-----|------|---------|
| **首次启动** | 2-6秒 | 预先执行缓存 |
| **后续启动** | 0.3秒 | 使用 `--prefer-offline` |
| **离线模式** | 0.5秒 | 设置 `NPX_PREFER_OFFLINE=true` |
| **本地安装** | 0.45秒 | `npm install chrome-devtools-mcp` |

---

## 🛠️ 常用命令

### 预热缓存
```bash
npx chrome-devtools-mcp@latest --version
```

### 查看npx缓存
```bash
ls -lh ~/.npm/_npx/
```

### 清理npx缓存
```bash
rm -rf ~/.npm/_npx/
```

### 查看包版本
```bash
npm view chrome-devtools-mcp version
```

### 锁定版本
```json
{
  "args": ["chrome-devtools-mcp@1.2.3"]  // 固定版本号
}
```

---

## 🚀 优化技巧

### 技巧1: 离线优先模式
```bash
# 方法A: 环境变量
export NPX_PREFER_OFFLINE=true
claude

# 方法B: 配置参数
{
  "args": ["--prefer-offline", "chrome-devtools-mcp@latest"]
}
```

### 技巧2: 使用淘宝镜像（中国大陆）
```bash
npm config set registry https://registry.npmmirror.com
```

### 技巧3: 本地安装（最快）
```bash
cd /Users/apus/Documents/UGit/Ingenio
npm install chrome-devtools-mcp

# npx会自动使用本地版本，启动速度 0.45秒
```

---

## ⚠️ 常见问题

### Q1: 首次启动很慢（30-60秒）？
**A**: 正常，首次需要下载~50MB的包

**解决**: 预先缓存
```bash
npx chrome-devtools-mcp@latest --version
```

### Q2: "command not found: npx"？
**A**: Node.js版本过低

**解决**: 升级Node.js
```bash
brew install node@22
```

### Q3: 网络超时？
**A**: npm registry被墙或网络不稳定

**解决**: 使用镜像
```bash
npm config set registry https://registry.npmmirror.com
```

### Q4: 想要完全离线使用？
**A**: 先下载后离线

**解决**:
```bash
# 1. 联网时预先下载
npx chrome-devtools-mcp@latest --version

# 2. 配置离线模式
{
  "args": ["--offline", "chrome-devtools-mcp@latest"]
}
```

---

## 🆚 npx vs 其他方式

| 方式 | 启动速度 | 磁盘 | 推荐度 |
|-----|---------|------|--------|
| **npx** | 0.3秒 | 50MB | ⭐⭐⭐⭐⭐ |
| 全局安装 | 0.1秒 | 200MB | ⭐⭐⭐ |
| 本地安装 | 0.45秒 | 200MB | ⭐⭐⭐⭐ |
| Docker | 15秒 | 5GB | ⭐⭐ |

**推荐**: npx（简单）或 本地安装（最快）

---

## 📁 文件位置

### npx缓存
```
~/.npm/_npx/
└── <hash>/
    └── node_modules/
        └── chrome-devtools-mcp/
```

### 本地安装
```
/Users/apus/Documents/UGit/Ingenio/node_modules/
└── chrome-devtools-mcp/
```

### 全局安装
```
/usr/local/lib/node_modules/
└── chrome-devtools-mcp/
```

---

## 🎓 完整文档

- **深度分析**: [ULTRATHINK_NPX_EXECUTION.md](./ULTRATHINK_NPX_EXECUTION.md)
- **快速启动**: [QUICK_START.md](./QUICK_START.md)
- **部署分析**: [MCP_DEPLOYMENT_ANALYSIS.md](./MCP_DEPLOYMENT_ANALYSIS.md)

---

## ✅ 最佳配置（复制即用）

```json
{
  "mcpServers": {
    "figma": {
      "type": "http",
      "url": "https://mcp.figma.com/mcp"
    },
    "calicat": {
      "type": "http",
      "url": "https://www.calicat.cn/mcp"
    },
    "chrome-devtools": {
      "type": "stdio",
      "command": "npx",
      "args": [
        "--yes",
        "--prefer-offline",
        "chrome-devtools-mcp@latest"
      ],
      "env": {}
    }
  }
}
```

**说明**:
- `--yes`: 跳过确认提示
- `--prefer-offline`: 优先使用缓存（离线可用）
- `@latest`: 自动使用最新版本

---

**总结**: npx让你无需安装即可使用chrome-devtools-mcp，首次下载后缓存到本地，后续启动只需0.3秒。这是最简单、最灵活的使用方式。

**Made with ❤️ by Claude Code**
