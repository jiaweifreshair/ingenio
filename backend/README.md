# Ingenio Backend

AI驱动的自然语言编程平台 - 后端服务

## 技术栈

- **Java 17+** - 运行时最低要求（以 `pom.xml` 为准）
- **Spring Boot 3.4.0** - Web框架（以 `pom.xml` 为准）
- **Spring AI 1.0.0-M5** - AI集成（以 `pom.xml` 为准）
- **MyBatis-Plus 3.5.8** - ORM框架（以 `pom.xml` 为准）
- **SaToken 1.37.0** - JWT认证
- **PostgreSQL 15.x** - 主数据库
- **Redis 7.x** - 缓存和会话
- **MinIO 8.5.7** - 对象存储

> 目录说明：`backend/` 同时包含 **Java Spring Boot**（`pom.xml` + `src/main/java`）与 **NestJS（TypeScript）**（`package.json` + `src/*.ts`）两套实现；当前默认脚本（`start-services.sh`）启动的是 Spring Boot。

## 项目结构

```
backend/
├── src/
│   ├── main/
│   │   ├── java/com/ingenio/backend/
│   │   │   ├── config/          # 配置类
│   │   │   ├── controller/      # 控制器
│   │   │   ├── dto/             # 数据传输对象
│   │   │   ├── entity/          # 实体类
│   │   │   ├── mapper/          # MyBatis Mapper
│   │   │   ├── service/         # 业务逻辑
│   │   │   └── IngenioBackendApplication.java
│   │   └── resources/
│   │       ├── application.yml       # 主配置
│   │       ├── application-dev.yml   # 开发环境配置
│   │       └── application-prod.yml  # 生产环境配置
│   └── test/                    # 测试代码
├── migrations/                  # 数据库迁移脚本
├── docs/                        # 文档
├── pom.xml                      # Maven配置
└── README.md
```

## 快速开始

### 前置要求

- Java 21+
- Maven 3.9+
- PostgreSQL 15+
- Redis 7+
- MinIO（可选）
- DeepSeek API Key

### 安装依赖

```bash
mvn clean install
```

### 配置环境变量

```bash
# 复制环境变量模板
cp .env.example .env

# 编辑.env文件，填写实际配置
vim .env
```

### 执行数据库迁移

```bash
# 创建数据库
createdb ingenio_dev

# 执行迁移脚本
psql -U postgres -d ingenio_dev -f migrations/001_create_users_table.sql
psql -U postgres -d ingenio_dev -f migrations/002_create_app_specs_table.sql
psql -U postgres -d ingenio_dev -f migrations/003_create_app_spec_versions_table.sql
psql -U postgres -d ingenio_dev -f migrations/004_create_generated_code_table.sql
psql -U postgres -d ingenio_dev -f migrations/005_create_projects_table.sql
psql -U postgres -d ingenio_dev -f migrations/006_create_forks_table.sql
psql -U postgres -d ingenio_dev -f migrations/007_create_social_interactions_table.sql
psql -U postgres -d ingenio_dev -f migrations/008_create_magic_prompts_table.sql
```

或使用一键脚本：

```bash
#!/bin/bash
# migrate.sh
for file in migrations/*.sql; do
  if [[ ! $file =~ \.down\.sql$ ]]; then
    echo "执行迁移: $file"
    psql -U postgres -d ingenio_dev -f "$file"
  fi
done
```

### 启动服务

```bash
# 开发模式
mvn spring-boot:run -Dspring-boot.run.profiles=dev

# 或构建后运行
mvn clean package
java -jar target/ingenio-backend-0.1.0-SNAPSHOT.jar --spring.profiles.active=dev
```

### 访问服务

- API服务：http://localhost:8080/api
- Swagger文档：http://localhost:8080/api/swagger-ui.html
- 健康检查：http://localhost:8080/api/actuator/health

## 开发指南

### 数据库操作

使用MyBatis-Plus进行数据库操作：

```java
@Service
public class ProjectService {
    @Autowired
    private ProjectMapper projectMapper;

    public List<ProjectEntity> getUserProjects(UUID userId) {
        return projectMapper.selectList(
            new LambdaQueryWrapper<ProjectEntity>()
                .eq(ProjectEntity::getUserId, userId)
                .orderByDesc(ProjectEntity::getCreatedAt)
        );
    }
}
```

### AI Agent调用

```java
@Service
public class PlanAgentService {
    @Autowired
    private ChatClient chatClient;

    public PlanAgentOutput plan(String requirement) {
        Prompt prompt = new Prompt(buildSystemPrompt(), requirement);
        ChatResponse response = chatClient.call(prompt);
        return parseResponse(response);
    }
}
```

### 代码生成

```java
@Service
public class CodeGenService {
    @Autowired
    private KuiklyUIRenderer renderer;

    public GeneratedCode generate(AppSpec appSpec) {
        RenderOptions options = RenderOptions.builder()
            .framework("taro")
            .h5Support(true)
            .build();
        return renderer.render(appSpec, options);
    }
}
```

## 测试

### 单元测试

```bash
mvn test
```

### 集成测试

```bash
mvn verify
```

### 测试覆盖率

```bash
mvn clean test jacoco:report
```

查看覆盖率报告：`target/site/jacoco/index.html`

## 代码规范

### 运行代码检查

```bash
mvn checkstyle:check
```

### 格式化代码

```bash
mvn spotless:apply
```

## Docker部署

### 构建镜像

```bash
docker build -t ingenio-backend:latest .
```

### 运行容器

```bash
docker-compose up -d
```

## API文档

启动服务后访问：http://localhost:8080/api/swagger-ui.html

## 监控和日志

### 健康检查

```bash
curl http://localhost:8080/api/actuator/health
```

### Metrics

```bash
curl http://localhost:8080/api/actuator/metrics
```

### 日志文件

日志文件位于：`logs/ingenio-backend.log`

## 常见问题

### Q: 如何更改数据库连接？

编辑`src/main/resources/application-dev.yml`或设置环境变量：

```yaml
spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/ingenio_dev
    username: postgres
    password: postgres
```

### Q: 如何配置DeepSeek API？

设置环境变量`DEEPSEEK_API_KEY`或在`application.yml`中配置：

```yaml
spring:
  ai:
    alibaba:
      api-key: sk-your-deepseek-api-key
```

### Q: 如何启用虚拟线程？

Java 21默认启用虚拟线程，在`IngenioBackendApplication.java`中配置：

```java
System.setProperty("spring.threads.virtual.enabled", "true");
```

## 贡献指南

1. Fork本仓库
2. 创建特性分支 (`git checkout -b feature/amazing-feature`)
3. 提交更改 (`git commit -m 'feat: 添加新功能'`)
4. 推送到分支 (`git push origin feature/amazing-feature`)
5. 创建Pull Request

## 许可证

MIT License

## 联系方式

- 项目仓库：https://github.com/ingenio/ingenio-backend
- Issue跟踪：https://github.com/ingenio/ingenio-backend/issues

---

**Ingenio Team** - AI驱动的自然语言编程平台
