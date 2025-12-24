# MCP服务器Docker部署指南

本目录包含了Ingenio项目使用的Model Context Protocol (MCP) 服务器的Docker配置。

## 📋 已配置的MCP服务器

### 1. Chrome DevTools MCP Server
- **用途**: 让AI Agent控制和检查Chrome浏览器
- **仓库**: https://github.com/ChromeDevTools/chrome-devtools-mcp
- **npm包**: chrome-devtools-mcp
- **部署方式**: Docker容器

### 2. Figma MCP Server
- **用途**: 集成Figma设计工具
- **URL**: https://mcp.figma.com/mcp
- **配置方式**: HTTP传输
- **配置位置**: `/Users/apus/.claude.json` (项目级)

### 3. Calicat MCP Server
- **用途**: Calicat服务集成
- **URL**: https://www.calicat.cn/mcp
- **配置方式**: HTTP传输
- **配置位置**: `/Users/apus/.claude.json` (用户级)

---

## 🚀 快速开始

### 前置要求
- Docker 20.10+
- Docker Compose 2.0+
- 至少2GB可用内存
- 至少5GB可用磁盘空间

### 启动服务

```bash
# 进入mcp-servers目录
cd /Users/apus/Documents/UGit/Ingenio/mcp-servers

# 构建并启动所有服务
docker-compose up -d

# 查看日志
docker-compose logs -f chrome-devtools-mcp

# 查看服务状态
docker-compose ps
```

### 停止服务

```bash
# 停止所有服务
docker-compose down

# 停止并删除数据卷（慎用！）
docker-compose down -v
```

---

## 📖 Chrome DevTools MCP使用方法

### 在Claude Code中使用

1. **确保Docker服务运行**：
```bash
docker-compose ps
# 应该看到chrome-devtools-mcp服务状态为Up
```

2. **配置Claude Code**：
编辑 `~/.claude.json` 添加：
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

3. **使用示例**：
```
你: 使用Chrome DevTools打开https://example.com并截图
Claude: [调用chrome-devtools MCP进行操作]
```

### 使用npx直接运行（无需Docker）

如果你不想使用Docker，可以直接使用npx运行：

```bash
# 无需全局安装，直接运行
npx chrome-devtools-mcp@latest
```

然后在Claude配置中使用：
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

---

## 🔧 高级配置

### 环境变量

在`docker-compose.yml`中可以配置以下环境变量：

| 变量 | 默认值 | 说明 |
|-----|-------|------|
| `NODE_ENV` | production | Node.js运行环境 |
| `CHROME_PATH` | /usr/bin/google-chrome-stable | Chrome可执行文件路径 |
| `DEBUG` | - | 调试日志开关 |

### 资源限制

默认配置：
- CPU限制: 2核心
- 内存限制: 2GB
- 共享内存: 2GB

可以在`docker-compose.yml`的`deploy.resources`部分调整。

### 数据持久化

Docker卷挂载：
- `chrome-user-data`: Chrome用户数据（浏览历史、cookies等）
- `chrome-downloads`: Chrome下载文件目录

清理数据：
```bash
docker volume rm mcp-servers_chrome-user-data
docker volume rm mcp-servers_chrome-downloads
```

---

## 🐛 故障排查

### 1. 容器无法启动

**症状**: `docker-compose up`失败

**检查步骤**:
```bash
# 查看详细日志
docker-compose logs chrome-devtools-mcp

# 检查容器状态
docker-compose ps

# 重新构建镜像
docker-compose build --no-cache chrome-devtools-mcp
```

### 2. Chrome启动失败

**症状**: 日志显示Chrome无法启动

**解决方案**:
```bash
# 增加共享内存大小
# 编辑docker-compose.yml，增加shm_size到4gb

# 或者使用--disable-dev-shm-usage标志
# 编辑Dockerfile，修改CMD为：
CMD ["npx", "chrome-devtools-mcp", "--", "--disable-dev-shm-usage"]
```

### 3. 内存不足

**症状**: 容器被OOM Killer杀死

**解决方案**:
```bash
# 增加内存限制
# 编辑docker-compose.yml的deploy.resources.limits.memory
```

### 4. 网络连接问题

**症状**: 无法访问外部网站

**检查步骤**:
```bash
# 检查容器网络
docker network inspect mcp-servers_mcp-network

# 测试网络连接
docker exec chrome-devtools-mcp ping -c 3 google.com
```

---

## 📊 监控和日志

### 实时日志

```bash
# 查看所有服务日志
docker-compose logs -f

# 仅查看Chrome DevTools MCP日志
docker-compose logs -f chrome-devtools-mcp

# 查看最近100行日志
docker-compose logs --tail=100 chrome-devtools-mcp
```

### 资源使用情况

```bash
# 查看容器资源使用
docker stats chrome-devtools-mcp

# 查看磁盘使用
docker system df
```

---

## 🔐 安全最佳实践

1. **不要在生产环境直接使用**: 此配置主要用于开发和测试
2. **限制网络访问**: 使用防火墙规则限制容器网络访问
3. **定期更新镜像**: 定期运行`docker-compose pull`更新基础镜像
4. **监控资源使用**: 防止资源滥用
5. **备份数据卷**: 定期备份重要的Chrome用户数据

---

## 📚 参考资源

- [Chrome DevTools MCP官方文档](https://github.com/ChromeDevTools/chrome-devtools-mcp)
- [Model Context Protocol规范](https://github.com/modelcontextprotocol/specification)
- [Docker Compose文档](https://docs.docker.com/compose/)
- [Claude Code MCP集成指南](https://docs.claude.com/en/docs/claude-code/mcp)

---

## 🤝 贡献

如果你发现问题或有改进建议，请提交Issue或Pull Request。

---

**最后更新**: 2025-11-15
**维护者**: Ingenio团队
