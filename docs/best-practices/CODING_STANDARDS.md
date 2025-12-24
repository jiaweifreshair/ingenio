# 代码规范指南

> **文档版本**: v1.0.0
> **创建日期**: 2025-11-11
> **作者**: Ingenio Team
> **状态**: 正式版

---

## 目录

- [1. Java代码规范](#1-java代码规范)
- [2. TypeScript代码规范](#2-typescript代码规范)
- [3. Kotlin代码规范](#3-kotlin代码规范)
- [4. 注释规范](#4-注释规范)
- [5. Git Commit规范](#5-git-commit规范)
- [6. Code Review清单](#6-code-review清单)

---

## 1. Java代码规范

### 1.1 基础规范（Google Java Style Guide）

**核心原则**：
- ✅ 遵循Google Java Style Guide
- ✅ 使用Checkstyle自动检查
- ✅ 使用Lombok减少样板代码
- ✅ 使用Spring Boot最佳实践

### 1.2 命名规范

#### 包名（Package）

```java
// ✅ 正确：全小写，单词之间无分隔符
package com.ingenio.backend.service;
package com.ingenio.backend.controller.ai;

// ❌ 错误：大写字母、下划线、驼峰
package com.Ingenio.Backend.Service;
package com.ingenio.backend_service;
package com.ingenio.backend.aiService;
```

#### 类名（Class）

```java
// ✅ 正确：大驼峰（PascalCase）
public class AICodeGenerator { }
public class VideoAnalysisService { }
public class GenerateAICodeRequest { }

// ❌ 错误：小驼峰、下划线、全大写
public class aiCodeGenerator { }
public class video_analysis_service { }
public class GENERATEAICODEQUEST { }
```

#### 方法名（Method）

```java
// ✅ 正确：小驼峰（camelCase），动词开头
public void generateCode() { }
public boolean isValid() { }
public String getUserName() { }
public List<User> findActiveUsers() { }

// ❌ 错误：大驼峰、下划线、名词开头
public void GenerateCode() { }
public void generate_code() { }
public void code() { }  // 不明确
```

#### 常量名（Constant）

```java
// ✅ 正确：全大写，下划线分隔
public static final int MAX_RETRY_COUNT = 3;
public static final String API_BASE_URL = "https://api.ingenio.dev";
public static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);

// ❌ 错误：小写、驼峰
public static final int maxRetryCount = 3;
public static final String ApiBaseUrl = "https://api.ingenio.dev";
```

#### 变量名（Variable）

```java
// ✅ 正确：小驼峰，有意义的名称
String userName = "John";
int totalCount = 100;
List<AICapability> selectedCapabilities = new ArrayList<>();

// ❌ 错误：单字母（除循环变量外）、无意义名称
String s = "John";
int n = 100;
List<AICapability> list1 = new ArrayList<>();
```

### 1.3 代码格式化

```java
// ✅ 正确：4空格缩进，大括号不换行
public class UserService {
    private final UserRepository userRepository;

    public User findById(String id) {
        if (id == null || id.isEmpty()) {
            throw new IllegalArgumentException("ID不能为空");
        }

        return userRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("用户不存在: " + id));
    }

    public List<User> findAll(Pageable pageable) {
        return userRepository.findAll(pageable)
                .getContent();
    }
}
```

### 1.4 异常处理

```java
// ✅ 正确：具体的异常类型，有意义的错误信息
public User createUser(CreateUserRequest request) {
    if (request.getEmail() == null) {
        throw new ValidationException("邮箱不能为空");
    }

    if (emailExists(request.getEmail())) {
        throw new DuplicateException("邮箱已存在: " + request.getEmail());
    }

    try {
        return userRepository.save(toEntity(request));
    } catch (DataAccessException e) {
        log.error("创建用户失败: email={}", request.getEmail(), e);
        throw new DatabaseException("创建用户失败", e);
    }
}

// ❌ 错误：捕获Exception、吞掉异常、无意义的错误信息
public User createUser(CreateUserRequest request) {
    try {
        return userRepository.save(toEntity(request));
    } catch (Exception e) {
        e.printStackTrace();  // 不要使用printStackTrace
        return null;  // 不要吞掉异常
    }
}
```

### 1.5 日志规范

```java
// ✅ 正确：使用SLF4J，参数化日志，合理的日志级别
@Slf4j
public class AICodeGeneratorServiceImpl {
    public GenerateResult generate(GenerateRequest request) {
        log.info("开始生成代码: userId={}, capabilities={}",
                 request.getUserId(), request.getCapabilities());

        long startTime = System.currentTimeMillis();
        try {
            GenerateResult result = doGenerate(request);
            long duration = System.currentTimeMillis() - startTime;

            log.info("代码生成成功: taskId={}, fileCount={}, duration={}ms",
                     result.getTaskId(), result.getFileCount(), duration);

            return result;
        } catch (Exception e) {
            log.error("代码生成失败: userId={}, error={}",
                      request.getUserId(), e.getMessage(), e);
            throw new GenerateException("代码生成失败", e);
        }
    }
}

// ❌ 错误：使用System.out、字符串拼接、错误的日志级别
public GenerateResult generate(GenerateRequest request) {
    System.out.println("开始生成: " + request);  // 不要使用System.out

    try {
        return doGenerate(request);
    } catch (Exception e) {
        log.info("生成失败", e);  // ERROR级别，不是INFO
    }
}
```

### 1.6 Lombok使用规范

```java
// ✅ 正确：合理使用Lombok注解
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GenerateAICodeRequest {
    @NotBlank(message = "包名不能为空")
    private String packageName;

    @NotBlank(message = "应用名称不能为空")
    private String appName;

    @NotEmpty(message = "至少选择一个AI能力")
    private List<String> capabilities;

    private String userRequirement;
}

// ❌ 错误：过度使用@Data，忽略不可变性
@Data  // Entity类不建议使用@Data
public class User {
    private String id;
    private String email;
    // ...
}
```

### 1.7 Stream API使用规范

```java
// ✅ 正确：简洁的Stream操作
List<String> activeUserEmails = users.stream()
        .filter(User::isActive)
        .map(User::getEmail)
        .distinct()
        .sorted()
        .collect(Collectors.toList());

// ✅ 正确：复杂操作拆分
Map<String, List<User>> usersByCity = users.stream()
        .filter(User::isActive)
        .collect(Collectors.groupingBy(
                User::getCity,
                Collectors.toList()
        ));

// ❌ 错误：过长的链式调用（>3个操作建议拆分）
List<String> result = users.stream().filter(u -> u.isActive()).map(u -> u.getEmail()).filter(e -> e.contains("@gmail.com")).distinct().sorted().limit(10).collect(Collectors.toList());
```

---

## 2. TypeScript代码规范

### 2.1 基础规范（Airbnb + 项目定制）

**核心原则**：
- ✅ 遵循Airbnb TypeScript Style Guide
- ✅ 启用strict模式
- ✅ 使用ESLint + Prettier
- ✅ 禁止使用any（除非有明确注释）

### 2.2 类型定义

```typescript
// ✅ 正确：明确的类型定义
interface AICapability {
  type: AICapabilityType;
  name: string;
  description: string;
  complexity: ComplexityLevel;
  estimatedCost: number;
  estimatedDays: number;
}

type AICapabilityType =
  | 'CHATBOT'
  | 'VIDEO_ANALYSIS'
  | 'KNOWLEDGE_GRAPH';

// ❌ 错误：使用any
interface AICapability {
  type: any;  // 不要使用any
  name: any;
  data: any;
}
```

### 2.3 React组件规范

```typescript
// ✅ 正确：函数式组件，Props接口，hooks规范
interface AICapabilityCardProps {
  capability: AICapability;
  isSelected: boolean;
  onToggle: (type: AICapabilityType) => void;
  disabled?: boolean;
}

export function AICapabilityCard({
  capability,
  isSelected,
  onToggle,
  disabled = false,
}: AICapabilityCardProps) {
  const handleClick = useCallback(() => {
    if (!disabled) {
      onToggle(capability.type);
    }
  }, [disabled, onToggle, capability.type]);

  return (
    <Card onClick={handleClick}>
      <h3>{capability.name}</h3>
      <p>{capability.description}</p>
    </Card>
  );
}

// ❌ 错误：类组件、内联函数、Props未定义类型
export function AICapabilityCard(props: any) {
  return (
    <Card onClick={() => props.onToggle(props.capability.type)}>
      <h3>{props.capability.name}</h3>
    </Card>
  );
}
```

### 2.4 Hooks使用规范

```typescript
// ✅ 正确：合理使用hooks，避免不必要的重渲染
export function AICapabilityPicker({
  selectedCapabilities,
  onSelectionChange,
}: AICapabilityPickerProps) {
  const [searchQuery, setSearchQuery] = useState('');
  const [debouncedQuery, setDebouncedQuery] = useState('');

  // 防抖
  useEffect(() => {
    const timer = setTimeout(() => {
      setDebouncedQuery(searchQuery);
    }, 300);
    return () => clearTimeout(timer);
  }, [searchQuery]);

  // 缓存计算结果
  const filteredCapabilities = useMemo(() => {
    return AI_CAPABILITIES.filter(cap =>
      cap.name.toLowerCase().includes(debouncedQuery.toLowerCase())
    );
  }, [debouncedQuery]);

  // 缓存回调函数
  const handleToggle = useCallback(
    (type: AICapabilityType) => {
      if (selectedCapabilities.includes(type)) {
        onSelectionChange(selectedCapabilities.filter(t => t !== type));
      } else {
        onSelectionChange([...selectedCapabilities, type]);
      }
    },
    [selectedCapabilities, onSelectionChange]
  );

  return (
    <div>
      <Input value={searchQuery} onChange={e => setSearchQuery(e.target.value)} />
      {filteredCapabilities.map(cap => (
        <AICapabilityCard
          key={cap.type}
          capability={cap}
          isSelected={selectedCapabilities.includes(cap.type)}
          onToggle={handleToggle}
        />
      ))}
    </div>
  );
}

// ❌ 错误：不必要的useEffect，未缓存计算，内联函数
export function AICapabilityPicker(props: any) {
  const [searchQuery, setSearchQuery] = useState('');

  useEffect(() => {
    console.log(searchQuery);  // 不必要的effect
  }, [searchQuery]);

  return (
    <div>
      <Input value={searchQuery} onChange={e => setSearchQuery(e.target.value)} />
      {AI_CAPABILITIES.filter(cap =>  // 每次渲染都过滤
        cap.name.includes(searchQuery)
      ).map(cap => (
        <AICapabilityCard
          key={cap.type}
          capability={cap}
          onToggle={() => props.onToggle(cap.type)}  // 每次渲染创建新函数
        />
      ))}
    </div>
  );
}
```

### 2.5 导入规范

```typescript
// ✅ 正确：分组导入，路径别名
// React相关
import { useState, useEffect, useMemo, useCallback } from 'react';

// 第三方库
import { motion, AnimatePresence } from 'framer-motion';
import { cn } from '@/lib/utils';

// 项目内部
import { AICapability, AICapabilityType } from '@/types/ai-capability';
import { AI_CAPABILITIES } from '@/data/ai-capabilities';
import { Card } from '@/components/ui/card';

// ❌ 错误：混乱的导入顺序，相对路径
import { Card } from '@/components/ui/card';
import { useState } from 'react';
import { AICapability } from '../../../types/ai-capability';
import { motion } from 'framer-motion';
```

---

## 3. Kotlin代码规范

### 3.1 基础规范（Kotlin官方规范）

**核心原则**：
- ✅ 遵循Kotlin官方编码规范
- ✅ 使用Ktlint自动检查
- ✅ 优先使用不可变数据结构
- ✅ 使用协程而非线程

### 3.2 数据类规范

```kotlin
// ✅ 正确：数据类，不可变属性
data class VideoAnalysisRequest(
    val videoUrl: String,
    val analysisType: AnalysisType,
    val frameInterval: Int = 30,
    val maxFrames: Int = 100
)

// ✅ 正确：密封类（Sealed Class）
sealed class AnalysisState {
    object Idle : AnalysisState()
    object Loading : AnalysisState()
    data class Success(val result: VideoAnalysisResult) : AnalysisState()
    data class Error(val message: String) : AnalysisState()
}

// ❌ 错误：可变属性，普通类
class VideoAnalysisRequest {
    var videoUrl: String = ""
    var analysisType: String = ""
}
```

### 3.3 协程使用规范

```kotlin
// ✅ 正确：使用协程，结构化并发
class VideoAnalysisService(private val apiKey: String) {
    suspend fun analyzeVideo(videoUrl: String): VideoAnalysisResult =
        withContext(Dispatchers.IO) {
            val response = httpClient.post(API_ENDPOINT) {
                header("Authorization", "Bearer $apiKey")
                setBody(VideoAnalysisRequest(videoUrl))
            }

            response.body<VideoAnalysisResult>()
        }

    suspend fun analyzeBatch(urls: List<String>): List<VideoAnalysisResult> =
        coroutineScope {
            urls.map { url ->
                async { analyzeVideo(url) }
            }.awaitAll()
        }
}

// ❌ 错误：使用Thread，阻塞调用
class VideoAnalysisService(private val apiKey: String) {
    fun analyzeVideo(videoUrl: String): VideoAnalysisResult {
        val thread = Thread {
            // 阻塞调用
        }
        thread.start()
        thread.join()
        // ...
    }
}
```

### 3.4 扩展函数规范

```kotlin
// ✅ 正确：有意义的扩展函数
fun String.isValidEmail(): Boolean {
    return matches(Regex("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$"))
}

fun List<AICapability>.totalCost(): Double {
    return sumOf { it.estimatedCost }
}

// ❌ 错误：过度使用扩展函数
fun String.s(): String = this  // 无意义
fun Int.add(other: Int): Int = this + other  // 已有运算符
```

---

## 4. 注释规范

### 4.1 JavaDoc规范

```java
/**
 * AI代码生成器
 * 基于KuiklyUI框架生成AI功能代码
 *
 * 功能：
 * - 读取AI代码模板（templates/ai/kuikly/）
 * - 替换占位符（{{PACKAGE_NAME}}, {{GENERATION_DATE}}, {{APP_NAME}}）
 * - 生成完整的KuiklyUI + AI集成代码
 * - 支持19种AI能力类型（11种基础 + 8种新增）
 *
 * 新增8种AI能力（2025-11-11）：
 * 1. VIDEO_ANALYSIS - 视频分析
 * 2. KNOWLEDGE_GRAPH - 知识图谱
 * 3. OCR_DOCUMENT - 智能文档识别
 * 4. REALTIME_STREAM - 实时流分析
 * 5. HYPER_PERSONALIZATION - 超个性化引擎
 * 6. PREDICTIVE_ANALYTICS - 预测分析
 * 7. MULTIMODAL_GENERATION - 多模态生成
 * 8. ANOMALY_DETECTION - 异常检测
 *
 * @author Ingenio Team
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AICodeGenerator {
    /**
     * 生成AI功能代码
     *
     * @param aiCapability AI能力需求
     * @param packageName 包名（如：com.example.myapp）
     * @param appName 应用名称（如：我的AI助手）
     * @return 生成的文件Map，key为文件路径，value为文件内容
     * @throws BusinessException 当生成失败时抛出
     */
    public Map<String, String> generateAICode(
        AICapabilityRequirement aiCapability,
        String packageName,
        String appName
    ) {
        // ...
    }
}
```

### 4.2 TSDoc规范

```typescript
/**
 * AI能力选择器组件（完整版）
 * 允许用户浏览和选择AI能力类型，支持智能推荐、筛选、搜索等功能
 *
 * 功能特性：
 * - 智能推荐算法（基于用户需求关键词匹配）
 * - 多维度筛选（分类、搜索、复杂度）
 * - 实时统计（总成本、总工期、复杂度分布）
 * - 响应式布局（4个断点：Mobile/Tablet/Desktop/Large）
 * - 完整的无障碍支持（ARIA标签、键盘导航）
 * - 流畅的微交互动画
 *
 * @author Ingenio Team
 * @since 1.0.0
 */
export function AICapabilityPicker({
  selectedCapabilities,
  onSelectionChange,
  userRequirement,
  maxSelection = 5,
  disabled = false,
}: AICapabilityPickerProps) {
  // ...
}
```

### 4.3 KDoc规范

```kotlin
/**
 * 视频分析AI服务
 * 基于阿里云通义千问Qwen-VL-Max实现
 *
 * 功能：
 * - analyzeVideo(): 分析视频内容，识别物体、场景、动作
 * - extractKeyFrames(): 提取视频关键帧
 * - generateSummary(): 生成视频摘要
 *
 * 技术实现：
 * - 使用Ktor客户端进行HTTP请求
 * - 支持Kotlin协程
 * - 完整的错误处理和重试机制
 *
 * Generated by Ingenio Platform
 * Date: 2025-11-11
 */
class VideoAnalysisService(
    private val apiKey: String,
    private val baseUrl: String = "https://dashscope.aliyuncs.com/api/v1"
) {
    /**
     * 分析视频内容
     *
     * @param videoUrl 视频URL或本地路径
     * @param prompt 分析提示词（可选）
     * @return 视频分析结果
     * @throws VideoAnalysisException 当分析失败时抛出
     */
    suspend fun analyzeVideo(
        videoUrl: String,
        prompt: String = "分析这个视频中的主要物体、场景和动作"
    ): VideoAnalysisResult {
        // ...
    }
}
```

---

## 5. Git Commit规范

### 5.1 Conventional Commits规范

**格式**：
```
<type>(<scope>): <subject>

<body>

<footer>
```

**Type类型**：
| 类型 | 说明 | 示例 |
|-----|------|------|
| `feat` | 新功能 | feat: 新增VIDEO_EDITING AI能力类型 |
| `fix` | Bug修复 | fix: 修复代码生成器空指针异常 |
| `perf` | 性能优化 | perf: 优化代码生成缓存策略 |
| `refactor` | 重构 | refactor: 重构AICodeGenerator类结构 |
| `docs` | 文档更新 | docs: 更新API文档 |
| `style` | 代码格式 | style: 格式化Java代码 |
| `test` | 测试相关 | test: 添加E2E测试用例 |
| `chore` | 构建/工具 | chore: 升级Spring Boot到3.4.0 |

### 5.2 Commit示例

```bash
# 示例1：新功能
feat: 实现VIDEO_EDITING（智能视频剪辑）AI能力类型

实现内容：
- 后端：VideoEditingService代码生成方法
- 前端：AI能力数据和类型定义
- 测试：单元测试和E2E测试覆盖率100%

技术实现：
- AI模型：Qwen-VL-Max + FFmpeg
- 复杂度：COMPLEX
- 预估成本：¥0.05/次

Generated with Claude Code
Co-Authored-By: Claude <noreply@anthropic.com>

# 示例2：Bug修复
fix: 修复代码生成器在处理特殊字符时的空指针异常

问题描述：
- 用户输入包含特殊字符（如$、{}）时，代码生成失败
- 错误信息：NullPointerException at AICodeGenerator.java:245

解决方案：
- 添加输入验证，过滤特殊字符
- 使用StringEscapeUtils.escapeJava()进行转义
- 添加单元测试覆盖边界情况

影响范围：
- 修复了影响5%用户的Bug
- 提升了代码生成成功率（95% → 99%）

Generated with Claude Code
Co-Authored-By: Claude <noreply@anthropic.com>
```

---

## 6. Code Review清单

### 6.1 功能性检查

- [ ] 代码实现了需求文档中的所有功能
- [ ] 所有边界条件都已处理
- [ ] 异常处理完整且合理
- [ ] 输入验证充分（防止SQL注入、XSS等）
- [ ] 并发安全（线程安全、数据竞争）

### 6.2 代码质量检查

- [ ] 命名规范（类、方法、变量）
- [ ] 代码格式化（缩进、空格、换行）
- [ ] 无Magic Number（常量已定义）
- [ ] 无明文敏感信息（密码、API密钥）
- [ ] 无重复代码（DRY原则）

### 6.3 性能检查

- [ ] 无N+1查询问题
- [ ] 数据库查询已添加索引
- [ ] 缓存策略合理
- [ ] 避免不必要的对象创建
- [ ] Stream操作效率高

### 6.4 安全性检查

- [ ] 无SQL注入风险
- [ ] 无XSS攻击风险
- [ ] 无CSRF攻击风险
- [ ] 敏感数据已加密
- [ ] 日志中无敏感信息

### 6.5 测试检查

- [ ] 单元测试覆盖率≥85%
- [ ] 所有测试用例通过
- [ ] 边界测试完整
- [ ] 异常情况测试充分
- [ ] E2E测试通过

### 6.6 文档检查

- [ ] JavaDoc/TSDoc/KDoc完整
- [ ] README更新
- [ ] API文档更新
- [ ] CHANGELOG更新
- [ ] 设计文档同步

---

**文档结束**
