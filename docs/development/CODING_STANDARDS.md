# Ingenio 代码规范

> **版本**: v1.0
> **最后更新**: 2025-11-09
> **维护人**: Ingenio Team

本文档定义了Ingenio项目的代码规范和最佳实践，确保代码质量、可维护性和团队协作效率。

---

## 目录

- [总体原则](#总体原则)
- [Java代码规范](#java代码规范)
- [命名规范](#命名规范)
- [注释规范](#注释规范)
- [包结构规范](#包结构规范)
- [异常处理规范](#异常处理规范)
- [日志规范](#日志规范)
- [测试规范](#测试规范)
- [数据库规范](#数据库规范)
- [API设计规范](#api设计规范)
- [安全规范](#安全规范)
- [性能规范](#性能规范)
- [代码审查检查清单](#代码审查检查清单)

---

## 总体原则

### SOLID原则

| 原则 | 说明 | 示例 |
|-----|------|------|
| **S**ingle Responsibility | 单一职责：一个类只有一个变化原因 | UserService只负责用户业务逻辑 |
| **O**pen/Closed | 开闭原则：对扩展开放，对修改关闭 | 使用策略模式扩展AI模型 |
| **L**iskov Substitution | 里氏替换：子类型必须能替换基类型 | 所有Renderer实现可互换 |
| **I**nterface Segregation | 接口隔离：客户端不依赖不需要的接口 | 拆分大接口为小接口 |
| **D**ependency Inversion | 依赖倒置：依赖抽象而非实现 | 依赖IRenderer接口而非具体类 |

### DRY原则

**Don't Repeat Yourself** - 避免代码重复

```java
// ❌ 错误示例：重复代码
public class UserController {
    public Result create(UserRequest req) {
        if (req.getEmail() == null || req.getEmail().isEmpty()) {
            return Result.error("邮箱不能为空");
        }
        if (!req.getEmail().matches("^[A-Za-z0-9+_.-]+@(.+)$")) {
            return Result.error("邮箱格式错误");
        }
        // ...
    }

    public Result update(UserRequest req) {
        if (req.getEmail() == null || req.getEmail().isEmpty()) {
            return Result.error("邮箱不能为空");
        }
        if (!req.getEmail().matches("^[A-Za-z0-9+_.-]+@(.+)$")) {
            return Result.error("邮箱格式错误");
        }
        // ...
    }
}

// ✅ 正确示例：提取共用逻辑
public class UserValidator {
    public void validateEmail(String email) {
        if (email == null || email.isEmpty()) {
            throw new ValidationException("邮箱不能为空");
        }
        if (!email.matches("^[A-Za-z0-9+_.-]+@(.+)$")) {
            throw new ValidationException("邮箱格式错误");
        }
    }
}

public class UserController {
    private final UserValidator validator;

    public Result create(UserRequest req) {
        validator.validateEmail(req.getEmail());
        // ...
    }

    public Result update(UserRequest req) {
        validator.validateEmail(req.getEmail());
        // ...
    }
}
```

### KISS原则

**Keep It Simple, Stupid** - 保持简单

```java
// ❌ 错误示例：过度设计
public class ComplexFactory {
    private static final Map<String, Supplier<AbstractBuilder>> builderRegistry = new HashMap<>();

    static {
        builderRegistry.put("user", () -> new UserBuilderImpl());
        builderRegistry.put("project", () -> new ProjectBuilderImpl());
    }

    public static <T> T build(String type, Function<AbstractBuilder, T> builderFunction) {
        return builderFunction.apply(builderRegistry.get(type).get());
    }
}

// ✅ 正确示例：简单直接
public class UserFactory {
    public static UserEntity createUser(CreateUserRequest request) {
        return UserEntity.builder()
            .email(request.getEmail())
            .username(request.getUsername())
            .build();
    }
}
```

### YAGNI原则

**You Aren't Gonna Need It** - 不要过度设计未来可能用不到的功能

---

## Java代码规范

### 基础规范

Ingenio遵循 [Google Java Style Guide](https://google.github.io/styleguide/javaguide.html)。

#### 代码格式

```java
// 缩进：4个空格
public class Example {
    private String field;

    public void method() {
        if (condition) {
            // 代码块
        }
    }
}

// 行宽：120字符
// 配置IntelliJ: Settings > Editor > Code Style > Java > Wrapping and Braces > Hard wrap at: 120

// 导入顺序
import com.ingenio.backend.*;           // 项目包
import org.springframework.*;           // 第三方包
import java.util.*;                     // Java标准库
```

#### 大括号规范

```java
// ✅ 正确：K&R风格，左大括号不换行
if (condition) {
    doSomething();
} else {
    doOtherThing();
}

// ❌ 错误：左大括号换行
if (condition)
{
    doSomething();
}

// ✅ 单行可省略大括号（但不推荐）
if (condition) return;

// 🎯 推荐：始终使用大括号
if (condition) {
    return;
}
```

### Lombok使用规范

#### 推荐使用

```java
@Data                    // 生成getter/setter/toString/equals/hashCode
@Builder                 // 生成构建器模式
@NoArgsConstructor       // 生成无参构造器
@AllArgsConstructor      // 生成全参构造器
@Slf4j                   // 生成log字段
@RequiredArgsConstructor // 生成必需字段的构造器
```

#### 实体类示例

```java
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@TableName("users")
public class UserEntity {
    @TableId(type = IdType.AUTO)
    private UUID id;

    private String email;
    private String username;
    private String passwordHash;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    private Boolean deleted;
}
```

#### Service类示例

```java
@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {
    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;

    public UserEntity createUser(CreateUserRequest request) {
        log.info("创建用户: email={}", request.getEmail());

        UserEntity user = UserEntity.builder()
            .email(request.getEmail())
            .username(request.getUsername())
            .passwordHash(passwordEncoder.encode(request.getPassword()))
            .build();

        userMapper.insert(user);

        log.info("用户创建成功: id={}", user.getId());
        return user;
    }
}
```

### 空值处理

```java
// ❌ 错误：可能抛出NullPointerException
public String getUserEmail(UUID userId) {
    UserEntity user = userMapper.selectById(userId);
    return user.getEmail(); // user可能为null
}

// ✅ 正确：使用Optional
public Optional<String> getUserEmail(UUID userId) {
    return Optional.ofNullable(userMapper.selectById(userId))
        .map(UserEntity::getEmail);
}

// ✅ 正确：明确检查null
public String getUserEmail(UUID userId) {
    UserEntity user = userMapper.selectById(userId);
    if (user == null) {
        throw new BusinessException(ErrorCode.USER_NOT_FOUND);
    }
    return user.getEmail();
}

// 🎯 推荐：使用@NonNull注解
public String getUserEmail(@NonNull UUID userId) {
    UserEntity user = userMapper.selectById(userId);
    Objects.requireNonNull(user, "用户不存在");
    return user.getEmail();
}
```

### Stream API使用

```java
// ✅ 推荐：使用Stream API简化集合操作
List<String> emails = users.stream()
    .filter(user -> !user.getDeleted())
    .map(UserEntity::getEmail)
    .distinct()
    .sorted()
    .collect(Collectors.toList());

// ✅ 使用parallelStream提升性能（大数据集）
List<String> emails = users.parallelStream()
    .map(this::processUser)
    .collect(Collectors.toList());

// ⚠️ 注意：避免在Stream中修改外部状态
List<String> result = new ArrayList<>();
users.stream().forEach(user -> result.add(user.getEmail())); // ❌ 不推荐

List<String> result = users.stream()
    .map(UserEntity::getEmail)
    .collect(Collectors.toList()); // ✅ 推荐
```

---

## 命名规范

### 类命名

| 类型 | 规范 | 示例 |
|-----|------|------|
| 实体类 | XxxEntity | UserEntity, ProjectEntity |
| DTO | XxxRequest, XxxResponse | CreateUserRequest, UserResponse |
| Controller | XxxController | UserController, AuthController |
| Service | XxxService | UserService, CodeGenerationService |
| Repository/Mapper | XxxMapper | UserMapper, ProjectMapper |
| 配置类 | XxxConfig | MinioConfig, RedisConfig |
| 异常类 | XxxException | BusinessException, ValidationException |
| 工具类 | XxxUtil | ZipUtil, FileUploadUtil |
| 常量类 | XxxConstants | UUIDv8Constants, ErrorCode |

### 方法命名

| 操作 | 前缀 | 示例 |
|-----|------|------|
| 获取单个对象 | get, find | getUser, findById |
| 获取多个对象 | list, query | listUsers, queryByCondition |
| 新增 | create, add, insert | createUser, addProject |
| 修改 | update, modify | updateUser, modifyStatus |
| 删除 | delete, remove | deleteUser, removeById |
| 保存（新增或修改） | save | saveUser |
| 统计 | count | countUsers |
| 判断 | is, has, can | isValid, hasPermission, canAccess |
| 转换 | to, from, convert | toDTO, fromEntity, convertToJson |
| 构建 | build | buildUserEntity |
| 验证 | validate | validateEmail |

### 变量命名

```java
// 常量：全大写，下划线分隔
public static final int MAX_RETRY_COUNT = 3;
public static final String DEFAULT_ENCODING = "UTF-8";

// 静态变量：驼峰命名
private static AtomicLong idGenerator = new AtomicLong(0);

// 成员变量：驼峰命名
private UserMapper userMapper;
private PasswordEncoder passwordEncoder;

// 局部变量：驼峰命名，简洁明确
String email = user.getEmail();
List<ProjectEntity> projects = projectMapper.selectAll();

// 循环变量：有意义的名称
for (UserEntity user : users) {  // ✅ 推荐
    // ...
}

for (int i = 0; i < users.size(); i++) {  // ✅ 可接受
    UserEntity user = users.get(i);
}

// 避免无意义的名称
for (UserEntity u : users) {  // ❌ 不推荐
    // ...
}
```

### 包命名

```
com.ingenio.backend
├── config          # 配置类
├── controller      # 控制器
├── dto             # 数据传输对象
│   ├── request     # 请求DTO
│   └── response    # 响应DTO
├── entity          # 实体类
├── mapper          # MyBatis Mapper
├── service         # 业务逻辑
│   └── impl        # Service实现
├── agent           # AI Agent
├── renderer        # 代码生成器
├── common          # 通用组件
│   ├── exception   # 异常类
│   ├── response    # 统一响应
│   └── util        # 工具类
└── IngenioBackendApplication.java
```

---

## 注释规范

### JavaDoc规范

**所有public类和方法必须添加JavaDoc注释。**

#### 类注释

```java
/**
 * 用户服务
 *
 * 提供用户相关的业务逻辑，包括用户注册、登录、信息管理等功能。
 *
 * @author Ingenio Team
 * @since 1.0.0
 */
@Service
@RequiredArgsConstructor
public class UserService {
    // ...
}
```

#### 方法注释

```java
/**
 * 创建用户
 *
 * 根据请求参数创建新用户，包括邮箱验证、密码加密、数据持久化等步骤。
 *
 * @param request 创建用户请求，包含邮箱、用户名、密码等信息
 * @return 创建成功的用户实体，包含自动生成的ID和时间戳
 * @throws BusinessException 当邮箱已存在或验证失败时抛出
 */
public UserEntity createUser(CreateUserRequest request) {
    // ...
}
```

#### 参数和返回值注释

```java
/**
 * 分页查询用户
 *
 * @param page 页码，从1开始
 * @param size 每页大小，范围1-100
 * @param keyword 搜索关键词，支持邮箱和用户名模糊匹配，可为null
 * @return 分页结果，包含用户列表和总数
 */
public PageResult<UserEntity> listUsers(int page, int size, String keyword) {
    // ...
}
```

### 行内注释

```java
public void processUser(UserEntity user) {
    // 1. 验证用户数据
    validateUser(user);

    // 2. 加密密码
    String encryptedPassword = passwordEncoder.encode(user.getPassword());
    user.setPasswordHash(encryptedPassword);

    // 3. 保存到数据库
    userMapper.insert(user);

    // 4. 发送欢迎邮件（异步）
    CompletableFuture.runAsync(() -> emailService.sendWelcomeEmail(user.getEmail()));
}

// ⚠️ 避免无意义的注释
int count = 0; // 初始化count为0  ❌ 不推荐（显而易见）
int retryCount = 0; // 重试次数，最大3次  ✅ 推荐（补充上下文）
```

### TODO注释

```java
// TODO: 实现OAuth登录功能 (@author zhangsan, deadline: 2025-12-31)
public void oauthLogin(String provider, String token) {
    throw new UnsupportedOperationException("OAuth登录功能待实现");
}

// FIXME: 修复并发情况下的数据竞争问题 (@author lisi, priority: high)
public void updateCounter(String key, int delta) {
    // 临时解决方案
    synchronized (this) {
        int current = getCounter(key);
        setCounter(key, current + delta);
    }
}
```

---

## 包结构规范

### 标准包结构

```
backend/src/main/java/com/ingenio/backend/
├── config/                           # 配置类
│   ├── MyBatisPlusConfig.java        # MyBatis-Plus配置
│   ├── RedisConfig.java              # Redis配置
│   ├── MinioConfig.java              # MinIO配置
│   ├── AsyncConfig.java              # 异步任务配置
│   └── WebConfig.java                # Web配置（CORS等）
│
├── controller/                       # 控制器
│   ├── AuthController.java           # 认证相关
│   ├── UserController.java           # 用户管理
│   ├── ProjectController.java        # 项目管理
│   └── MultimodalInputController.java # 多模态输入
│
├── dto/                              # 数据传输对象
│   ├── request/                      # 请求DTO
│   │   ├── CreateUserRequest.java
│   │   ├── LoginRequest.java
│   │   └── GenerateFullRequest.java
│   └── response/                     # 响应DTO
│       ├── UserResponse.java
│       ├── LoginResponse.java
│       └── GenerateFullResponse.java
│
├── entity/                           # 实体类
│   ├── UserEntity.java
│   ├── ProjectEntity.java
│   ├── AppSpecEntity.java
│   └── GenerationTaskEntity.java
│
├── mapper/                           # MyBatis Mapper
│   ├── UserMapper.java
│   ├── ProjectMapper.java
│   └── GenerationTaskMapper.java
│
├── service/                          # 业务逻辑
│   ├── UserService.java
│   ├── AuthService.java
│   ├── CodeGenerationService.java
│   └── impl/                         # Service实现（如需要）
│       └── UserServiceImpl.java
│
├── agent/                            # AI Agent
│   ├── PlanAgent.java
│   ├── ExecuteAgent.java
│   ├── ValidateAgent.java
│   └── dto/                          # Agent专用DTO
│       ├── PlanResult.java
│       └── ValidateResult.java
│
├── renderer/                         # 代码生成器
│   ├── IRenderer.java
│   └── KuiklyUIRenderer.java
│
├── common/                           # 通用组件
│   ├── exception/                    # 异常类
│   │   ├── BusinessException.java
│   │   ├── ValidationException.java
│   │   └── GlobalExceptionHandler.java
│   ├── response/                     # 统一响应
│   │   ├── Result.java
│   │   └── PageResult.java
│   └── util/                         # 工具类
│       ├── ZipUtil.java
│       ├── UUIDv8Generator.java
│       └── FileUploadUtil.java
│
└── IngenioBackendApplication.java    # 主启动类
```

### 模块化原则

- **按功能模块划分**：相关的类放在同一个包下
- **清晰的依赖关系**：Controller → Service → Mapper/Repository
- **避免循环依赖**：使用事件驱动或依赖倒置解决
- **分层隔离**：不同层级的类放在不同包

---

## 异常处理规范

### 异常分类

```java
// 业务异常（可预期，需要返回给前端）
public class BusinessException extends RuntimeException {
    private final ErrorCode errorCode;
    private final String message;

    public BusinessException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
        this.message = errorCode.getMessage();
    }

    public BusinessException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
        this.message = message;
    }
}

// 系统异常（不可预期，记录日志，返回通用错误）
public class SystemException extends RuntimeException {
    public SystemException(String message, Throwable cause) {
        super(message, cause);
    }
}
```

### 错误码定义

```java
@Getter
@AllArgsConstructor
public enum ErrorCode {
    // 通用错误 (1xxx)
    SUCCESS(1000, "操作成功"),
    INVALID_PARAMETER(1001, "参数错误"),
    INTERNAL_ERROR(1999, "系统内部错误"),

    // 用户相关 (2xxx)
    USER_NOT_FOUND(2001, "用户不存在"),
    USER_ALREADY_EXISTS(2002, "用户已存在"),
    INVALID_CREDENTIALS(2003, "用户名或密码错误"),

    // 认证相关 (3xxx)
    UNAUTHORIZED(3001, "未授权"),
    TOKEN_EXPIRED(3002, "Token已过期"),
    FORBIDDEN(3003, "无权限访问"),

    // 业务相关 (4xxx)
    PROJECT_NOT_FOUND(4001, "项目不存在"),
    GENERATION_FAILED(4002, "代码生成失败"),
    AI_SERVICE_ERROR(4003, "AI服务调用失败");

    private final int code;
    private final String message;
}
```

### 异常处理最佳实践

```java
@Service
@RequiredArgsConstructor
public class UserService {

    // ✅ 正确：抛出业务异常
    public UserEntity getUserById(UUID id) {
        return userMapper.selectById(id)
            .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
    }

    // ✅ 正确：捕获并转换异常
    public void sendEmail(String email, String content) {
        try {
            emailClient.send(email, content);
        } catch (EmailException e) {
            log.error("邮件发送失败: email={}", email, e);
            throw new BusinessException(ErrorCode.EMAIL_SEND_FAILED, e.getMessage());
        }
    }

    // ✅ 正确：记录日志后重新抛出
    public void callAIService(String prompt) {
        try {
            aiClient.chat(prompt);
        } catch (Exception e) {
            log.error("AI服务调用失败: prompt={}", prompt, e);
            throw new BusinessException(ErrorCode.AI_SERVICE_ERROR, "AI服务暂时不可用");
        }
    }

    // ❌ 错误：吞没异常
    public void processUser(UserEntity user) {
        try {
            // 业务逻辑
        } catch (Exception e) {
            // 什么都不做
        }
    }

    // ❌ 错误：捕获所有异常
    public void someMethod() {
        try {
            // 业务逻辑
        } catch (Throwable t) {  // 不要捕获Throwable
            // ...
        }
    }
}
```

### 全局异常处理

```java
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 业务异常处理
     */
    @ExceptionHandler(BusinessException.class)
    public Result<Void> handleBusinessException(BusinessException e) {
        log.warn("业务异常: code={}, message={}", e.getErrorCode().getCode(), e.getMessage());
        return Result.error(e.getErrorCode().getCode(), e.getMessage());
    }

    /**
     * 参数验证异常
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<Void> handleValidationException(MethodArgumentNotValidException e) {
        String errorMessage = e.getBindingResult().getFieldErrors().stream()
            .map(error -> error.getField() + ": " + error.getDefaultMessage())
            .collect(Collectors.joining(", "));

        log.warn("参数验证失败: {}", errorMessage);
        return Result.error(ErrorCode.INVALID_PARAMETER.getCode(), errorMessage);
    }

    /**
     * 系统异常处理
     */
    @ExceptionHandler(Exception.class)
    public Result<Void> handleException(Exception e) {
        log.error("系统异常", e);
        return Result.error(ErrorCode.INTERNAL_ERROR.getCode(), "系统内部错误");
    }
}
```

---

## 日志规范

### 日志级别

| 级别 | 使用场景 | 示例 |
|-----|---------|------|
| **ERROR** | 系统错误，需要立即处理 | 数据库连接失败、第三方API调用失败 |
| **WARN** | 警告信息，可能导致问题 | 参数验证失败、业务异常 |
| **INFO** | 重要业务流程 | 用户登录、订单创建、代码生成完成 |
| **DEBUG** | 调试信息 | 方法参数、SQL语句、AI请求/响应 |
| **TRACE** | 详细追踪信息 | 每一步骤的执行细节 |

### 日志格式

```java
@Slf4j
@Service
public class UserService {

    // ✅ 正确：结构化日志，包含关键信息
    public UserEntity createUser(CreateUserRequest request) {
        log.info("创建用户开始: email={}, username={}", request.getEmail(), request.getUsername());

        try {
            UserEntity user = buildUser(request);
            userMapper.insert(user);

            log.info("创建用户成功: id={}, email={}", user.getId(), user.getEmail());
            return user;
        } catch (Exception e) {
            log.error("创建用户失败: email={}", request.getEmail(), e);
            throw new BusinessException(ErrorCode.USER_CREATE_FAILED);
        }
    }

    // ✅ 正确：DEBUG级别记录详细参数
    public void updateUser(UUID id, UpdateUserRequest request) {
        log.debug("更新用户: id={}, request={}", id, request);
        // ...
    }

    // ❌ 错误：字符串拼接（性能差）
    log.info("创建用户: " + user.getEmail());  // 不推荐

    // ✅ 正确：使用占位符
    log.info("创建用户: email={}", user.getEmail());  // 推荐

    // ❌ 错误：日志级别不当
    log.error("用户登录: email={}", email);  // 应使用INFO

    // ❌ 错误：记录敏感信息
    log.info("用户登录: password={}", password);  // 禁止记录密码
}
```

### 敏感信息脱敏

```java
@Slf4j
@Service
public class AuthService {

    public void login(LoginRequest request) {
        // ❌ 错误：记录明文密码
        log.info("用户登录: email={}, password={}", request.getEmail(), request.getPassword());

        // ✅ 正确：不记录密码
        log.info("用户登录: email={}", request.getEmail());

        // ✅ 正确：脱敏处理
        log.debug("登录请求: email={}, passwordHash={}",
            request.getEmail(),
            DigestUtils.md5Hex(request.getPassword()));
    }

    // 邮箱脱敏：test@example.com → t***@example.com
    private String maskEmail(String email) {
        if (email == null || !email.contains("@")) return email;
        String[] parts = email.split("@");
        String username = parts[0];
        if (username.length() <= 1) return email;
        return username.charAt(0) + "***@" + parts[1];
    }

    // 手机号脱敏：13812345678 → 138****5678
    private String maskPhone(String phone) {
        if (phone == null || phone.length() < 11) return phone;
        return phone.substring(0, 3) + "****" + phone.substring(7);
    }
}
```

### 性能监控日志

```java
@Slf4j
@Service
public class CodeGenerationService {

    public GenerateFullResponse generate(GenerateFullRequest request) {
        long startTime = System.currentTimeMillis();

        try {
            // 业务逻辑
            GenerateFullResponse response = doGenerate(request);

            long duration = System.currentTimeMillis() - startTime;
            log.info("代码生成完成: taskId={}, duration={}ms", response.getTaskId(), duration);

            // 性能警告
            if (duration > 10000) {
                log.warn("代码生成耗时过长: taskId={}, duration={}ms", response.getTaskId(), duration);
            }

            return response;
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            log.error("代码生成失败: requirement={}, duration={}ms", request.getRequirement(), duration, e);
            throw e;
        }
    }
}
```

---

## 测试规范

### 测试分类

| 测试类型 | 目的 | 工具 | 覆盖率要求 |
|---------|------|------|-----------|
| 单元测试 | 测试单个方法或类 | JUnit 5 + Mockito | ≥ 85% |
| 集成测试 | 测试模块间协作 | Spring Boot Test + TestContainers | ≥ 70% |
| E2E测试 | 测试完整业务流程 | REST Assured + TestContainers | 核心流程100% |

### 单元测试规范

```java
@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserMapper userMapper;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UserService userService;

    /**
     * 测试方法命名：should_ExpectedBehavior_When_Condition
     */
    @Test
    void should_CreateUserSuccessfully_When_ValidRequest() {
        // Given（准备测试数据）
        CreateUserRequest request = CreateUserRequest.builder()
            .email("test@example.com")
            .username("testuser")
            .password("Test123456")
            .build();

        when(passwordEncoder.encode(anyString())).thenReturn("encrypted_password");
        when(userMapper.insert(any())).thenReturn(1);

        // When（执行测试方法）
        UserEntity result = userService.createUser(request);

        // Then（验证结果）
        assertNotNull(result);
        assertEquals("test@example.com", result.getEmail());
        assertEquals("testuser", result.getUsername());

        // 验证方法调用
        verify(passwordEncoder, times(1)).encode("Test123456");
        verify(userMapper, times(1)).insert(any(UserEntity.class));
    }

    @Test
    void should_ThrowException_When_EmailAlreadyExists() {
        // Given
        CreateUserRequest request = CreateUserRequest.builder()
            .email("existing@example.com")
            .username("testuser")
            .password("Test123456")
            .build();

        when(userMapper.selectByEmail(anyString()))
            .thenReturn(Optional.of(new UserEntity()));

        // When & Then
        assertThrows(BusinessException.class, () -> {
            userService.createUser(request);
        });
    }

    /**
     * 测试边界条件
     */
    @Test
    void should_HandleNullParameters_When_InvalidInput() {
        assertThrows(NullPointerException.class, () -> {
            userService.createUser(null);
        });
    }
}
```

### 集成测试规范

```java
@SpringBootTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class UserServiceIntegrationTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15")
        .withDatabaseName("testdb")
        .withUsername("test")
        .withPassword("test");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @Autowired
    private UserService userService;

    @Autowired
    private UserMapper userMapper;

    @Test
    void should_CreateAndRetrieveUser_When_ValidData() {
        // Given
        CreateUserRequest request = CreateUserRequest.builder()
            .email("integration@test.com")
            .username("integrationuser")
            .password("Test123456")
            .build();

        // When
        UserEntity createdUser = userService.createUser(request);
        UserEntity retrievedUser = userMapper.selectById(createdUser.getId());

        // Then
        assertNotNull(retrievedUser);
        assertEquals(createdUser.getId(), retrievedUser.getId());
        assertEquals("integration@test.com", retrievedUser.getEmail());
    }
}
```

### E2E测试规范

```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
class GenerationFlowE2ETest {

    @LocalServerPort
    private int port;

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15");

    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7-alpine")
        .withExposedPorts(6379);

    @Test
    void should_CompleteGenerationFlow_When_ValidRequirement() {
        // 1. 用户注册
        String token = given()
            .port(port)
            .contentType(ContentType.JSON)
            .body(Map.of(
                "email", "e2e@test.com",
                "username", "e2euser",
                "password", "Test123456"
            ))
            .when()
            .post("/api/auth/register")
            .then()
            .statusCode(200)
            .extract()
            .path("data.token");

        // 2. 提交需求
        String taskId = given()
            .port(port)
            .header("Authorization", "Bearer " + token)
            .contentType(ContentType.JSON)
            .body(Map.of("requirement", "创建图书管理系统"))
            .when()
            .post("/api/generate/full")
            .then()
            .statusCode(200)
            .extract()
            .path("data.taskId");

        // 3. 轮询任务状态
        await().atMost(Duration.ofSeconds(30))
            .pollInterval(Duration.ofSeconds(2))
            .until(() -> {
                String status = given()
                    .port(port)
                    .header("Authorization", "Bearer " + token)
                    .when()
                    .get("/api/tasks/" + taskId + "/status")
                    .then()
                    .statusCode(200)
                    .extract()
                    .path("data.status");

                return "COMPLETED".equals(status);
            });

        // 4. 下载生成代码
        byte[] codeZip = given()
            .port(port)
            .header("Authorization", "Bearer " + token)
            .when()
            .get("/api/tasks/" + taskId + "/download")
            .then()
            .statusCode(200)
            .contentType("application/zip")
            .extract()
            .asByteArray();

        assertThat(codeZip).isNotEmpty();
    }
}
```

### 测试覆盖率要求

```bash
# 运行测试并生成覆盖率报告
mvn clean test jacoco:report

# 打开报告
open target/site/jacoco/index.html

# 覆盖率要求
# - 整体覆盖率 ≥ 85%
# - 核心业务逻辑 ≥ 90%
# - 工具类 ≥ 80%
# - 配置类可较低
```

---

## 数据库规范

### 表命名规范

- 使用小写字母和下划线
- 表名使用复数形式
- 示例：`users`, `projects`, `app_specs`

### 字段命名规范

```sql
-- 主键：id (UUID类型)
CREATE TABLE users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    -- 业务字段：小写+下划线
    email VARCHAR(255) NOT NULL,
    username VARCHAR(100) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,

    -- 时间戳字段：固定命名
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,

    -- 软删除字段
    deleted BOOLEAN NOT NULL DEFAULT FALSE,

    -- 外键：xxx_id
    tenant_id UUID NOT NULL,

    -- 索引命名：idx_表名_字段名
    CONSTRAINT idx_users_email UNIQUE (email),
    CONSTRAINT idx_users_username UNIQUE (username)
);

-- 索引命名
CREATE INDEX idx_users_tenant_id ON users(tenant_id);
CREATE INDEX idx_users_created_at ON users(created_at);
```

### 迁移脚本规范

```sql
-- 文件命名：序号_描述.sql
-- 示例：001_create_users_table.sql

-- 每个迁移脚本包含：
-- 1. 注释说明
-- 2. 正向迁移（创建或修改）
-- 3. 回滚脚本（对应的.down.sql文件）

-- 001_create_users_table.sql
-- 创建用户表
-- 作者: Ingenio Team
-- 日期: 2025-11-09

CREATE TABLE users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email VARCHAR(255) NOT NULL,
    username VARCHAR(100) NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,

    CONSTRAINT idx_users_email UNIQUE (email),
    CONSTRAINT idx_users_username UNIQUE (username)
);

CREATE INDEX idx_users_created_at ON users(created_at);

-- 001_create_users_table.down.sql
-- 回滚：删除用户表

DROP TABLE IF EXISTS users;
```

### SQL查询优化

```java
// ❌ 错误：N+1查询
List<User> users = userMapper.selectAll();
for (User user : users) {
    List<Project> projects = projectMapper.selectByUserId(user.getId()); // N次查询
}

// ✅ 正确：使用JOIN或批量查询
List<UserWithProjects> users = userMapper.selectUsersWithProjects();

// 或使用MyBatis-Plus批量查询
List<UUID> userIds = users.stream().map(User::getId).toList();
List<Project> allProjects = projectMapper.selectBatchIds(userIds);
Map<UUID, List<Project>> projectsByUser = allProjects.stream()
    .collect(Collectors.groupingBy(Project::getUserId));
```

---

## API设计规范

### RESTful API规范

| 操作 | HTTP方法 | 路径示例 | 说明 |
|-----|---------|---------|------|
| 列表查询 | GET | `/api/users` | 查询所有用户 |
| 分页查询 | GET | `/api/users?page=1&size=20` | 分页查询用户 |
| 单个查询 | GET | `/api/users/{id}` | 查询指定用户 |
| 创建 | POST | `/api/users` | 创建用户 |
| 更新（全量） | PUT | `/api/users/{id}` | 更新用户（全部字段） |
| 更新（部分） | PATCH | `/api/users/{id}` | 更新用户（部分字段） |
| 删除 | DELETE | `/api/users/{id}` | 删除用户 |

### 统一响应格式

```java
@Data
@Builder
public class Result<T> {
    private int code;       // 业务代码
    private String message; // 提示信息
    private T data;         // 响应数据
    private Long timestamp; // 时间戳

    public static <T> Result<T> success(T data) {
        return Result.<T>builder()
            .code(ErrorCode.SUCCESS.getCode())
            .message(ErrorCode.SUCCESS.getMessage())
            .data(data)
            .timestamp(System.currentTimeMillis())
            .build();
    }

    public static <T> Result<T> error(int code, String message) {
        return Result.<T>builder()
            .code(code)
            .message(message)
            .timestamp(System.currentTimeMillis())
            .build();
    }
}

// 成功响应示例
{
    "code": 1000,
    "message": "操作成功",
    "data": {
        "id": "550e8400-e29b-41d4-a716-446655440000",
        "email": "test@example.com",
        "username": "testuser"
    },
    "timestamp": 1699488000000
}

// 错误响应示例
{
    "code": 2001,
    "message": "用户不存在",
    "data": null,
    "timestamp": 1699488000000
}
```

### 分页响应格式

```java
@Data
@Builder
public class PageResult<T> {
    private List<T> records;    // 数据列表
    private long total;         // 总记录数
    private long current;       // 当前页码
    private long size;          // 每页大小
    private long pages;         // 总页数

    public static <T> PageResult<T> of(IPage<T> page) {
        return PageResult.<T>builder()
            .records(page.getRecords())
            .total(page.getTotal())
            .current(page.getCurrent())
            .size(page.getSize())
            .pages(page.getPages())
            .build();
    }
}

// 分页响应示例
{
    "code": 1000,
    "message": "操作成功",
    "data": {
        "records": [...],
        "total": 100,
        "current": 1,
        "size": 20,
        "pages": 5
    },
    "timestamp": 1699488000000
}
```

### 参数验证

```java
@Data
public class CreateUserRequest {

    @NotBlank(message = "邮箱不能为空")
    @Email(message = "邮箱格式错误")
    private String email;

    @NotBlank(message = "用户名不能为空")
    @Size(min = 3, max = 20, message = "用户名长度必须在3-20之间")
    @Pattern(regexp = "^[a-zA-Z0-9_]+$", message = "用户名只能包含字母、数字和下划线")
    private String username;

    @NotBlank(message = "密码不能为空")
    @Size(min = 8, max = 32, message = "密码长度必须在8-32之间")
    @Pattern(
        regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).+$",
        message = "密码必须包含大小写字母和数字"
    )
    private String password;
}

// Controller中使用@Valid触发验证
@PostMapping("/users")
public Result<UserResponse> create(@Valid @RequestBody CreateUserRequest request) {
    UserEntity user = userService.createUser(request);
    return Result.success(UserResponse.from(user));
}
```

---

## 安全规范

### SQL注入防护

```java
// ❌ 错误：字符串拼接，存在SQL注入风险
String sql = "SELECT * FROM users WHERE email = '" + email + "'";
jdbcTemplate.query(sql, ...);

// ✅ 正确：使用参数化查询
String sql = "SELECT * FROM users WHERE email = ?";
jdbcTemplate.query(sql, new Object[]{email}, ...);

// ✅ 推荐：使用MyBatis-Plus
userMapper.selectOne(
    new LambdaQueryWrapper<UserEntity>()
        .eq(UserEntity::getEmail, email)
);
```

### XSS防护

```java
// 前端输入自动转义（Spring Boot默认启用）
// 后端输出时使用@JsonRawValue需谨慎

// ✅ 推荐：使用HTML转义工具
import org.springframework.web.util.HtmlUtils;

String userInput = request.getParameter("content");
String sanitized = HtmlUtils.htmlEscape(userInput);
```

### 认证授权

```java
// 使用SaToken进行认证
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class UserController {

    // 需要登录
    @GetMapping("/profile")
    public Result<UserResponse> getProfile() {
        StpUtil.checkLogin();  // 检查登录状态
        UUID userId = UUID.fromString(StpUtil.getLoginIdAsString());
        // ...
    }

    // 需要特定权限
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable UUID id) {
        StpUtil.checkPermission("user:delete");  // 检查权限
        // ...
    }

    // 需要特定角色
    @GetMapping("/admin/stats")
    public Result<Map<String, Object>> getStats() {
        StpUtil.checkRole("admin");  // 检查角色
        // ...
    }
}
```

### 敏感信息保护

```java
// ❌ 错误：密码明文存储
user.setPassword(request.getPassword());

// ✅ 正确：密码加密存储
String encodedPassword = passwordEncoder.encode(request.getPassword());
user.setPasswordHash(encodedPassword);

// ❌ 错误：凭证硬编码
String credential = "hardcoded-sensitive-value";

// ✅ 正确：从环境变量读取
@Value("${deepseek.api-key}")
private String apiKey;

// ❌ 错误：敏感信息记录到日志
log.info("用户登录: password={}", password);

// ✅ 正确：不记录敏感信息
log.info("用户登录: email={}", email);
```

---

## 性能规范

### 数据库查询优化

```java
// ✅ 使用索引
@TableName("users")
public class UserEntity {
    @TableId
    private UUID id;

    @TableField(value = "email")
    private String email;  // 确保email字段有索引
}

// ✅ 批量操作
List<UserEntity> users = buildUsers(requests);
userMapper.insertBatch(users);  // 批量插入

// ✅ 分页查询
Page<UserEntity> page = new Page<>(current, size);
IPage<UserEntity> result = userMapper.selectPage(page, queryWrapper);

// ✅ 只查询需要的字段
List<UserEntity> users = userMapper.selectList(
    new LambdaQueryWrapper<UserEntity>()
        .select(UserEntity::getId, UserEntity::getEmail, UserEntity::getUsername)
);
```

### 缓存使用

```java
@Service
@RequiredArgsConstructor
public class UserService {

    private final UserMapper userMapper;
    private final RedisTemplate<String, Object> redisTemplate;

    private static final String USER_CACHE_KEY = "user:";
    private static final long USER_CACHE_TTL = 3600; // 1小时

    public UserEntity getUserById(UUID id) {
        String cacheKey = USER_CACHE_KEY + id;

        // 1. 尝试从缓存获取
        UserEntity cached = (UserEntity) redisTemplate.opsForValue().get(cacheKey);
        if (cached != null) {
            return cached;
        }

        // 2. 从数据库查询
        UserEntity user = userMapper.selectById(id);
        if (user == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }

        // 3. 写入缓存
        redisTemplate.opsForValue().set(cacheKey, user, USER_CACHE_TTL, TimeUnit.SECONDS);

        return user;
    }

    public void updateUser(UUID id, UpdateUserRequest request) {
        // 更新数据库
        userMapper.updateById(buildUser(id, request));

        // 删除缓存
        redisTemplate.delete(USER_CACHE_KEY + id);
    }
}
```

### 异步处理

```java
@Service
@RequiredArgsConstructor
public class EmailService {

    // ✅ 使用@Async异步发送邮件
    @Async
    public CompletableFuture<Void> sendWelcomeEmail(String email) {
        try {
            emailClient.send(email, "欢迎注册Ingenio！");
            return CompletableFuture.completedFuture(null);
        } catch (Exception e) {
            log.error("邮件发送失败: email={}", email, e);
            return CompletableFuture.failedFuture(e);
        }
    }
}

@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean(name = "taskExecutor")
    public Executor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(5);
        executor.setMaxPoolSize(10);
        executor.setQueueCapacity(100);
        executor.setThreadNamePrefix("async-");
        executor.initialize();
        return executor;
    }
}
```

---

## 代码审查检查清单

### 功能检查

- [ ] 功能实现符合需求
- [ ] 边界条件处理正确
- [ ] 错误处理完善
- [ ] 业务逻辑正确

### 代码质量

- [ ] 遵循命名规范
- [ ] 代码结构清晰
- [ ] 无重复代码
- [ ] 注释完整准确

### 安全检查

- [ ] 无SQL注入风险
- [ ] 无XSS漏洞
- [ ] 敏感信息已加密
- [ ] 权限验证完善

### 性能检查

- [ ] 无N+1查询问题
- [ ] 使用了合适的索引
- [ ] 缓存使用合理
- [ ] 异步处理得当

### 测试检查

- [ ] 单元测试完整
- [ ] 测试覆盖率达标
- [ ] 测试用例合理
- [ ] 测试通过

---

## 参考资料

- [Google Java Style Guide](https://google.github.io/styleguide/javaguide.html)
- [Effective Java (第3版)](https://www.oracle.com/java/technologies/effective-java.html)
- [Clean Code](https://www.amazon.com/Clean-Code-Handbook-Software-Craftsmanship/dp/0132350882)
- [Spring Boot Best Practices](https://docs.spring.io/spring-boot/docs/current/reference/html/)
- [MyBatis-Plus Documentation](https://baomidou.com/)

---

**文档信息**

- 版本: v1.0
- 最后更新: 2025-11-09
- 维护人: Ingenio Team
- 反馈问题: https://github.com/ingenio/ingenio/issues
