# 环境变量配置指南

## 快速开始

### 1. 复制环境变量模板

```bash
cp .env.example .env
```

### 2. 编辑 `.env` 文件，填入真实值

```bash
vim .env  # 或使用你喜欢的编辑器
```

### 3. 确保 `.env` 已被 `.gitignore` 排除

```bash
git status  # 确认 .env 不在待提交列表中
```

## 环境变量说明

### 数据库配置

```bash
DB_HOST=localhost           # PostgreSQL主机地址
DB_PORT=5432               # PostgreSQL端口
DB_NAME=ingenio_dev        # 数据库名称
DB_USER=postgresql         # 数据库用户名
DB_PASSWORD=your-password  # 数据库密码 ⚠️ 敏感信息
```

**获取方式**：
- 本地开发：安装PostgreSQL后自行设置
- 生产环境：从DBA或运维团队获取

### Redis配置

```bash
REDIS_HOST=localhost       # Redis主机地址
REDIS_PORT=6379           # Redis端口
REDIS_PASSWORD=           # Redis密码（留空表示无密码）
```

### 七牛云AI服务

```bash
QINIU_AI_API_KEY=sk-xxxxx  # 七牛云AI API密钥 ⚠️ 敏感信息
```

**获取方式**：
1. 登录七牛云控制台：https://portal.qiniu.com
2. 进入"密钥管理"
3. 创建或复制现有API密钥

⚠️ **重要提示**：
- 不要将API密钥提交到Git仓库
- 定期轮换密钥
- 限制密钥权限范围

### MinIO对象存储

```bash
MINIO_ENDPOINT=http://127.0.0.1:9000  # MinIO服务地址
MINIO_ACCESS_KEY=minioadmin           # MinIO访问密钥
MINIO_SECRET_KEY=minioadmin           # MinIO密钥 ⚠️ 敏感信息
```

**本地开发**：
```bash
# 使用Docker启动MinIO
docker run -p 9000:9000 -p 9001:9001 \
  -e "MINIO_ROOT_USER=minioadmin" \
  -e "MINIO_ROOT_PASSWORD=minioadmin" \
  minio/minio server /data --console-address ":9001"
```

### JWT密钥

```bash
JWT_SECRET_KEY=your-32-characters-secret  # JWT签名密钥 ⚠️ 敏感信息
```

**生成方法**：
```bash
# 生成强随机密钥（推荐）
openssl rand -base64 32

# 或使用Python
python3 -c "import secrets; print(secrets.token_urlsafe(32))"
```

⚠️ **安全要求**：
- 至少32个字符
- 使用随机生成的强密钥
- 生产环境和开发环境使用不同的密钥

### 邮件服务

```bash
MAIL_HOST=smtp.qq.com              # SMTP服务器地址
MAIL_PORT=465                      # SMTP端口（465为SSL）
MAIL_USERNAME=your-email@qq.com    # 邮箱账号
MAIL_PASSWORD=authorization-code   # 授权码 ⚠️ 敏感信息
```

**QQ邮箱授权码获取**：
1. 登录QQ邮箱网页版
2. 设置 → 账户 → POP3/IMAP/SMTP/Exchange/CardDAV/CalDAV服务
3. 开启"POP3/SMTP服务"
4. 生成授权码（不是QQ密码！）

## 不同环境的配置

### 开发环境（Local）

```bash
# .env
DB_HOST=localhost
DB_PASSWORD=dev_password
QINIU_AI_API_KEY=sk-dev-key
JWT_SECRET_KEY=dev-secret-key-32-characters
```

Spring配置文件：`application-local.yml`（已配置好环境变量引用）

### 测试环境（Test）

创建 `.env.test` 文件：
```bash
DB_HOST=test-db.example.com
DB_PASSWORD=test_password
QINIU_AI_API_KEY=sk-test-key
JWT_SECRET_KEY=test-secret-key-32-characters
```

### 生产环境（Production）

⚠️ **生产环境不使用 `.env` 文件！**

推荐使用：
- **Kubernetes Secrets**
- **AWS Secrets Manager**
- **Azure Key Vault**
- **HashiCorp Vault**

或通过环境变量注入：
```bash
export DB_PASSWORD="production-strong-password"
export QINIU_AI_API_KEY="sk-prod-key-xxxxx"
export JWT_SECRET_KEY="production-jwt-secret-64-characters-random"
```

## Spring Boot 如何加载环境变量

Spring Boot按以下优先级加载配置：

1. **命令行参数**（最高优先级）
2. **操作系统环境变量**
3. **`.env` 文件**（通过spring-dotenv加载）
4. **application.yml / application-{profile}.yml**
5. **默认值**（最低优先级）

配置示例：
```yaml
# application-local.yml
spring:
  datasource:
    password: ${DB_PASSWORD:default-password}
```

- `DB_PASSWORD`：环境变量名
- `default-password`：找不到环境变量时的默认值

## 安全检查清单

提交代码前，请确认：

- [ ] `.env` 文件已添加到 `.gitignore`
- [ ] 运行 `git status` 确认 `.env` 不在待提交列表
- [ ] 配置文件中没有硬编码的密码/密钥
- [ ] 所有敏感信息都使用环境变量引用
- [ ] `.env.example` 中只包含占位符，无真实值

检查命令：
```bash
# 检查是否有敏感信息泄露
git grep -i "password\|secret\|api_key" -- "*.yml" "*.yaml" "*.properties" | grep -v "PASSWORD\|SECRET\|API_KEY"

# 确认 .env 被忽略
git check-ignore -v .env backend/.env
```

## 故障排查

### 问题1：应用启动时提示找不到环境变量

**解决**：
1. 确认 `.env` 文件存在于 `backend/` 目录
2. 确认环境变量名称正确（区分大小写）
3. 重启IDE或终端以加载新的环境变量

### 问题2：Spring Boot无法读取 `.env` 文件

**解决**：
确认 `pom.xml` 包含 `spring-dotenv` 依赖：
```xml
<dependency>
    <groupId>me.paulschwarz</groupId>
    <artifactId>spring-dotenv</artifactId>
    <version>4.0.0</version>
</dependency>
```

### 问题3：Git提示 `.env` 将被提交

**解决**：
```bash
# 从暂存区移除
git rm --cached .env

# 确认 .gitignore 包含 .env
echo ".env" >> .gitignore

# 重新提交
git add .gitignore
git commit -m "chore: 添加 .env 到 .gitignore"
```

## 团队协作规范

1. **永远不要**将 `.env` 文件提交到Git
2. **始终维护** `.env.example` 文件的最新状态
3. **新增环境变量时**，同步更新 `.env.example` 和本文档
4. **密钥轮换时**，通知所有团队成员更新本地配置
5. **Code Review时**，检查是否有硬编码的敏感信息

## 相关文档

- [Spring Boot Externalized Configuration](https://docs.spring.io/spring-boot/docs/current/reference/html/features.html#features.external-config)
- [12-Factor App: Config](https://12factor.net/config)
- [OWASP: Secrets Management](https://owasp.org/www-community/vulnerabilities/Sensitive_Data_Exposure)
