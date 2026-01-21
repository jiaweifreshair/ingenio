# G3沙箱Maven环境修复文档

## 问题描述

**问题根源**：G3沙箱使用的Docker容器中没有预装Maven，导致Java后端代码编译失败。

**错误信息**：
```
/bin/sh: 1: mvn: not found
exitCode: 127
```

**影响范围**：
- 所有通过G3引擎生成的Java后端代码无法编译
- 验证流程在Phase 1（编译验证）阶段失败
- 红队修复Agent无法自动修复此类环境问题

## 解决方案

### 方案选择

经过评估，选择**在Docker镜像中预装Java和Maven**的方案：

| 方案 | 优点 | 缺点 | 选择 |
|-----|------|------|------|
| **预装Maven** | 一次构建，永久可用；启动快；无需每次安装 | 镜像体积增加约200MB | ✅ **已选择** |
| Maven Wrapper | 无需修改镜像 | 需要项目包含mvnw文件；首次运行慢 | ❌ |
| 动态安装 | 镜像小 | 每次创建沙箱都要安装；启动慢；网络依赖 | ❌ |

### 实施步骤

#### 1. 修改Open-Lovable的Dockerfile

**文件位置**：`/Users/apus/Documents/UGit/open-lovable-cn/Dockerfile`

**修改内容**：在Stage 3（运行时阶段）添加Java 17和Maven安装：

```dockerfile
# ============================================================================
# Stage 3: 运行时阶段（最终镜像）
# ============================================================================
FROM mcr.microsoft.com/playwright:v1.56.1-noble AS runner

WORKDIR /app

# 使用 Playwright 镜像内置的 pwuser (uid=1000, gid=1000)
# Playwright 镜像已包含此用户，无需创建新用户

# 安装 Java 17 和 Maven（用于G3沙箱编译Java后端代码）
# 注意：必须以root用户安装系统包
USER root
RUN apt-get update && \
    apt-get install -y --no-install-recommends \
    openjdk-17-jdk \
    maven && \
    apt-get clean && \
    rm -rf /var/lib/apt/lists/*

# 验证安装
RUN java -version && mvn -version

# 安装 pnpm（运行时需要）
RUN corepack enable && corepack prepare pnpm@latest --activate
```

#### 2. 重新构建Docker镜像

```bash
cd /Users/apus/Documents/UGit/open-lovable-cn

# 构建新镜像
docker build -t open-lovable-cn:latest .

# 验证Java和Maven已安装
docker run --rm open-lovable-cn:latest java -version
docker run --rm open-lovable-cn:latest mvn -version
```

#### 3. 更新docker-compose��置（如果使用）

如果Open-Lovable通过docker-compose部署，需要更新配置使用新镜像：

```yaml
services:
  open-lovable:
    image: open-lovable-cn:latest
    # ... 其他配置
```

#### 4. 重启Open-Lovable服务

```bash
# 停止旧容器
docker-compose down

# 启动新容器
docker-compose up -d

# 验证服务正常
curl http://localhost:3001/api/health
```

## 验证步骤

### 1. 本地验证

```bash
# 进入容器验证环境
docker exec -it <container_id> bash

# 验证Java版本
java -version
# 预期输出：openjdk version "17.x.x"

# 验证Maven版本
mvn -version
# 预期输出：Apache Maven 3.x.x

# 验证Maven可执行
which mvn
# 预期输出：/usr/bin/mvn
```

### 2. G3引擎集成测试

通过Ingenio后端触发G3任务，验证编译成功：

```bash
# 启动Ingenio后端
cd /Users/apus/Documents/UGit/Ingenio/backend
./scripts/start-backend.sh

# 触发G3任务（通过前端或API）
# 观察日志，确认编译阶段不再出现 "mvn: not found" 错误
```

### 3. 监控指标

- ✅ 沙箱创建成功率：100%
- ✅ Maven编译成功率：>95%（排除代码错误）
- ✅ Phase 1验证通过率：>90%

## 技术细节

### 镜像大小影响

- **基础镜像**：`mcr.microsoft.com/playwright:v1.56.1-noble` (~1.5GB)
- **添加Java 17**：+150MB
- **添加Maven**：+50MB
- **最终镜像**：~1.7GB

### 性能影响

- **镜像构建时间**：增加约2-3分钟
- **容器启动时间**：无明显影响（<1秒差异）
- **首次Maven编译**：需要下载依赖（约30-60秒）
- **后续编译**：使用本地缓存（约5-10秒）

### 安全考虑

- 使用官方OpenJDK 17（来自Ubuntu仓库）
- 使用官方Maven（来自Ubuntu仓库）
- 定期更新基础镜像以获取安全补丁
- 清理apt缓存减少攻击面

## 监控和告警

### 添加健康检查

在G3SandboxService中添加环境检查：

```java
// 在沙箱创建后验证Maven可用性
public void validateSandboxEnvironment(String sandboxId) {
    try {
        CommandResult result = executeCommand(sandboxId, "mvn -version");
        if (result.exitCode() != 0) {
            log.error("沙箱环境检查失败：Maven不可用");
            throw new RuntimeException("Sandbox environment check failed: Maven not available");
        }
        log.info("沙箱环境检查通过：Maven版本 {}", result.stdout());
    } catch (Exception e) {
        log.error("沙箱环境检查异常", e);
        throw e;
    }
}
```

### 告警规则

- **P0告警**：Maven编译失败率 > 10%（5分钟内）
- **P1告警**：沙箱创建失败率 > 5%（15分钟内）
- **P2告警**：编译时间 > 120秒（P95）

## 回滚方案

如果新镜像出现问题，可以快速回滚：

```bash
# 回滚到旧镜像
docker tag open-lovable-cn:previous open-lovable-cn:latest
docker-compose restart open-lovable

# 或使用特定版本
docker pull open-lovable-cn:v1.0.0
docker tag open-lovable-cn:v1.0.0 open-lovable-cn:latest
docker-compose restart open-lovable
```

## 后续优化

### 短期优化（1-2周）

1. **Maven本地仓库持久化**：将Maven本地仓库挂载到宿主机，避免重复下载依赖
2. **依赖预下载**：在镜像构建时预下载常用依赖（Spring Boot、MyBatis等）
3. **编译缓存**：使用Docker BuildKit缓存加速镜像构建

### 长期优化（1-3个月）

1. **多阶段构建优化**：分离构建环境和运行环境，减小最终镜像体积
2. **自定义基础镜像**：创建包含Java、Maven、Node.js的统一基础镜像
3. **镜像分层优化**：优化Dockerfile层级，提高缓存命中率

## 相关文档

- [G3引擎架构设计](./G3_ENGINE_ARCHITECTURE.md)
- [沙箱服务文档](./G3_SANDBOX_SERVICE.md)
- [Open-Lovable集成指南](./OPEN_LOVABLE_INTEGRATION.md)

## 变更历史

| 日期 | 版本 | 变更内容 | 作者 |
|-----|------|---------|------|
| 2026-01-19 | 1.0.0 | 初始版本：添加Java 17和Maven支持 | DevOps Team |

---

**维护者**：Ingenio DevOps Team
**最后更新**：2026-01-19
**状态**：✅ 已实施
