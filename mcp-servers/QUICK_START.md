# MCP Server快速启动指南（无需Docker）

**⚡ 5分钟完成配置**

---

## 🎯 核心答案

### Q: 需要Docker吗？
**A: ❌ 完全不需要！**

- ✅ **Figma MCP**: HTTP远程服务，无需安装
- ✅ **Calicat MCP**: HTTP远程服务，无需安装
- ✅ **Chrome DevTools MCP**: 使用npx，无需Docker

---

## 🚀 3步完成配置

### Step 1: 确认Node.js版本

```bash
node --version
# 需要 ≥ 22.0.0

# 如果版本过低，升级：
brew install node@22
```

### Step 2: 配置MCP服务器

**方式A: 编辑全局配置（推荐）**

```bash
# 打开配置文件
code ~/.claude.json

# 或使用vim
vim ~/.claude.json
```

**方式B: 编辑项目配置**

```bash
# 打开项目配置
code /Users/apus/Documents/UGit/Ingenio/.claude/settings.json
```

**添加以下配置**:

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
      "args": ["chrome-devtools-mcp@latest"],
      "env": {}
    }
  }
}
```

**注意**: Figma和Calicat已经添加到配置中了，只需添加Chrome DevTools即可。

### Step 3: 重启Claude Code

```bash
# 如果Claude正在运行，先退出（Ctrl+D 或 /exit）

# 重新启动
claude
```

---

## ✅ 验证安装

### 检查MCP服务器状态

```bash
# 在Claude中运行
/mcp
```

**预期输出**:
```
MCP Servers:
✅ figma (HTTP)
✅ calicat (HTTP)
✅ chrome-devtools (stdio)
```

### 测试各个服务器

#### 测试Figma MCP
```
你: @figma 你能做什么？
Claude: [介绍Figma MCP的功能...]
```

#### 测试Chrome DevTools MCP
```
你: @chrome-devtools 打开 https://example.com
Claude: [执行Chrome操作...]
```

#### 测试Calicat MCP
```
你: @calicat 你能做什么？
Claude: [介绍Calicat MCP的功能...]
```

---

## 🎓 使用示例

### 示例1: 使用Chrome DevTools截图

```
你: @chrome-devtools 打开 https://anthropic.com 并截图首页

Claude会：
1. 启动Chrome浏览器
2. 导航到指定URL
3. 等待页面加载
4. 截图并返回
```

### 示例2: 查看Figma设计

```
你: @figma 查看我的最近设计文件

Claude会：
1. 连接到Figma API
2. 获取你的设计文件列表
3. 显示文件信息
```

### 示例3: 组合使用多个MCP

```
你: 使用@figma获取设计规范，然后用@chrome-devtools在浏览器中验证实现效果

Claude会：
1. 从Figma获取设计规范
2. 使用Chrome DevTools检查网页实现
3. 对比并给出反馈
```

---

## 🔧 常见问题

### Q1: Chrome DevTools首次启动很慢？

**A**: 正常现象，npx首次运行需要下载包（~50MB），约需30-60秒

**解决方案**: 预先缓存
```bash
npx chrome-devtools-mcp@latest --version
```

### Q2: 提示"npx: command not found"？

**A**: Node.js未安装或版本过低

**解决方案**:
```bash
# 检查Node.js
node --version

# 安装Node.js 22+
brew install node@22
```

### Q3: HTTP MCP无法连接？

**A**: 网络问题或防火墙

**解决方案**:
```bash
# 测试连接
curl -I https://mcp.figma.com/mcp
curl -I https://www.calicat.cn/mcp

# 如需代理
export HTTPS_PROXY=http://127.0.0.1:7890
```

### Q4: 我还是想用Docker怎么办？

**A**: 可以，但不推荐

**Docker方式**:
```bash
cd /Users/apus/Documents/UGit/Ingenio/mcp-servers
./start.sh build
./start.sh start

# 修改配置使用Docker
{
  "mcpServers": {
    "chrome-devtools": {
      "type": "stdio",
      "command": "docker",
      "args": ["exec", "-i", "chrome-devtools-mcp", "npx", "chrome-devtools-mcp"]
    }
  }
}
```

---

## 📊 资源消耗对比

### 推荐方式（HTTP + npx）

```
内存: ~350-450MB
磁盘: ~50MB
启动: 2-3秒
维护: 零维护
```

### Docker方式（不推荐）

```
内存: ~1.2-2GB
磁盘: ~2.5-4GB
启动: 15-30秒
维护: 需要管理容器
```

**节省资源**: 推荐方式比Docker节省 **80%内存** 和 **98%磁盘**

---

## 🎯 下一步

### 立即可做

1. ✅ 验证3个MCP服务器都正常工作
2. ✅ 尝试上述使用示例
3. ✅ 阅读MCP文档了解更多功能

### 可选优化

1. 配置快捷命令/别名
2. 探索更多MCP服务器：https://github.com/modelcontextprotocol/servers
3. 学习MCP协议：https://github.com/modelcontextprotocol/specification

---

## 📚 参考文档

- **详细分析**: [MCP_DEPLOYMENT_ANALYSIS.md](./MCP_DEPLOYMENT_ANALYSIS.md)
- **完整文档**: [README.md](./README.md)
- **安装总结**: [INSTALLATION_SUMMARY.md](./INSTALLATION_SUMMARY.md)
- **推荐配置**: [RECOMMENDED_CONFIG.json](./RECOMMENDED_CONFIG.json)

---

## ❓ 还有问题？

### 查看MCP状态

```bash
# 在Claude中
/mcp
```

### 查看详细日志

```bash
# 启动Claude时开启调试模式
ANTHROPIC_LOG=debug claude
```

### 社区支持

- GitHub Issues: https://github.com/anthropics/claude-code/issues
- Discord: https://discord.gg/anthropic

---

**总结**:
- ❌ **不需要Docker**
- ✅ **HTTP方式**（Figma、Calicat）- 已完成
- ✅ **npx方式**（Chrome DevTools）- 只需添加配置
- ⚡ **5分钟完成** - 重启即可使用

**Made with ❤️ by Claude Code**
