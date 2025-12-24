# MCP服务器安装总结

**完成时间**: 2025-11-15
**项目**: Ingenio

---

## ✅ 已完成的任务

### 1. Figma MCP Server（项目级）
- **安装状态**: ✅ 成功
- **配置位置**: `/Users/apus/.claude.json` (项目级: /Users/apus/Documents/UGit/Ingenio)
- **传输方式**: HTTP
- **URL**: https://mcp.figma.com/mcp
- **用途**: Figma设计工具集成，用于访问Figma文件和设计资源

**配置内容**:
```json
{
  "mcpServers": {
    "figma": {
      "type": "http",
      "url": "https://mcp.figma.com/mcp"
    }
  }
}
```

### 2. Calicat MCP Server（用户级）
- **安装状态**: ✅ 成功
- **配置位置**: `/Users/apus/.claude.json` (用户级全局配置)
- **传输方式**: HTTP
- **URL**: https://www.calicat.cn/mcp
- **用途**: Calicat服务集成

**配置内容**:
```json
{
  "mcpServers": {
    "calicat": {
      "type": "http",
      "url": "https://www.calicat.cn/mcp"
    }
  }
}
```

### 3. Chrome DevTools MCP Server（Docker部署）
- **包名**: `chrome-devtools-mcp` (正确包名，不是@modelcontextprotocol/server-chrome-devtools)
- **安装方式**: Docker容器部署
- **Docker配置**: `/Users/apus/Documents/UGit/Ingenio/mcp-servers/`
- **用途**: 让AI Agent控制和检查Chrome浏览器

**Docker文件清单**:
- ✅ `Dockerfile` - Chrome DevTools MCP镜像定义
- ✅ `docker-compose.yml` - 容器编排配置
- ✅ `README.md` - 详细使用文档
- ✅ `start.sh` - 启动管理脚本（已添加执行权限）
- ✅ `claude-config-example.json` - Claude配置示例

---

## 📋 配置验证

### 项目级MCP服务器（Ingenio项目）
```bash
# 查看项目级MCP配置
grep -A 5 '"mcpServers"' /Users/apus/.claude.json | grep -A 3 'Ingenio'
```

**结果**: Figma MCP已成功添加到项目配置

### 用户级MCP服务器（全局）
```bash
# 查看用户级MCP配置
grep -A 5 '"mcpServers":' /Users/apus/.claude.json | tail -10
```

**结果**: Calicat MCP已成功添加到全局配置

---

## 🚀 使用方法

### 方式1: 使用npx直接运行Chrome DevTools MCP（推荐）

**无需全局安装，直接运行**:
```bash
npx chrome-devtools-mcp@latest
```

**Claude配置**:
编辑 `~/.claude.json` 或项目级 `.claude/settings.json`:
```json
{
  "mcpServers": {
    "chrome-devtools": {
      "command": "npx",
      "args": ["chrome-devtools-mcp@latest"]
    }
  }
}
```

### 方式2: 使用Docker运行Chrome DevTools MCP

**启动Docker服务**:
```bash
cd /Users/apus/Documents/UGit/Ingenio/mcp-servers
./start.sh start
```

**查看服务状态**:
```bash
./start.sh status
```

**查看日志**:
```bash
./start.sh logs
```

**Claude配置**:
```json
{
  "mcpServers": {
    "chrome-devtools": {
      "command": "docker",
      "args": [
        "exec",
        "-i",
        "chrome-devtools-mcp",
        "npx",
        "chrome-devtools-mcp"
      ]
    }
  }
}
```

---

## 🧪 测试MCP服务器

### 测试Figma MCP
```bash
# 在Claude Code中测试
claude

# 然后在对话中：
你: @figma 帮我查看Figma中的设计文件
```

### 测试Calicat MCP
```bash
# 在Claude Code中测试
claude

# 然后在对话中：
你: @calicat 帮我使用Calicat服务
```

### 测试Chrome DevTools MCP
```bash
# 方式1: 使用npx
npx chrome-devtools-mcp@latest

# 方式2: 使用Docker
cd /Users/apus/Documents/UGit/Ingenio/mcp-servers
./start.sh test
```

---

## 📖 可用的管理命令

### start.sh脚本命令

```bash
# 构建Docker镜像
./start.sh build

# 启动MCP服务器
./start.sh start

# 停止MCP服务器
./start.sh stop

# 重启MCP服务器
./start.sh restart

# 查看服务状态
./start.sh status

# 查看日志
./start.sh logs [服务名]

# 进入容器shell
./start.sh shell [服务名]

# 测试Chrome DevTools MCP
./start.sh test

# 清理所有数据（谨慎使用！）
./start.sh clean

# 显示帮助信息
./start.sh help
```

---

## 🐛 故障排查

### 问题1: Chrome DevTools MCP无法启动

**症状**: 容器启动失败或日志显示Chrome无法启动

**解决方案**:
```bash
# 重新构建镜像
cd /Users/apus/Documents/UGit/Ingenio/mcp-servers
./start.sh stop
docker-compose build --no-cache chrome-devtools-mcp
./start.sh start
```

### 问题2: npm全局安装权限问题

**症状**: EACCES错误

**解决方案**: 使用npx无需全局安装
```bash
# 不需要全局安装，直接使用npx
npx chrome-devtools-mcp@latest
```

### 问题3: MCP服务器未在Claude中显示

**症状**: @-mention时看不到MCP服务器

**解决方案**:
1. 重启Claude Code
2. 检查配置文件格式是否正确
3. 运行 `claude mcp list` 查看已加载的MCP服务器

---

## 📚 相关文档

- [Chrome DevTools MCP官方仓库](https://github.com/ChromeDevTools/chrome-devtools-mcp)
- [MCP协议规范](https://github.com/modelcontextprotocol/specification)
- [Claude Code MCP文档](https://docs.claude.com/en/docs/claude-code/mcp)
- [项目MCP README](/Users/apus/Documents/UGit/Ingenio/mcp-servers/README.md)

---

## 🎯 下一步建议

### 立即可做
1. ✅ 测试每个MCP服务器的基本功能
2. ✅ 将Docker配置提交到版本控制（.gitignore已配置好）
3. ✅ 创建团队MCP使用文档

### 可选优化
1. 配置Nginx反向代理（如需要）
2. 设置MCP服务器监控和告警
3. 探索更多MCP服务器（https://github.com/modelcontextprotocol/servers）
4. 创建自定义MCP服务器（针对项目特定需求）

---

## 🔐 安全注意事项

1. **不要在生产环境直接使用**: 此配置主要用于开发和测试
2. **限制网络访问**: 使用防火墙规则限制容器网络访问
3. **定期更新镜像**: 运行`docker-compose pull`更新基础镜像
4. **监控资源使用**: 防止资源滥用
5. **备份数据卷**: 定期备份重要的Chrome用户数据

---

**总结**: 所有MCP服务器已成功配置！Figma和Calicat通过HTTP直接连接，Chrome DevTools MCP提供了两种使用方式（npx和Docker），推荐使用npx方式以简化部署。

**Made with ❤️ by Claude Code**
