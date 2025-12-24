# Ingenio Git工作流

> **版本**: v1.0
> **最后更新**: 2025-11-09
> **维护人**: Ingenio Team

本文档定义了Ingenio项目的Git工作流程，包括分支模型、提交规范、合并策略等，确保团队协作高效有序。

---

## 目录

- [分支模型](#分支模型)
- [分支命名规范](#分支命名规范)
- [提交信息规范](#提交信息规范)
- [工作流程](#工作流程)
- [合并策略](#合并策略)
- [版本标签规范](#版本标签规范)
- [冲突解决](#冲突解决)
- [代码回滚](#代码回滚)
- [最佳实践](#最佳实践)
- [常用Git命令](#常用git命令)

---

## 分支模型

Ingenio使用简化的**GitFlow分支模型**，平衡了灵活性和规范性。

### 分支架构

```
main (生产环境，受保护)
  ↑
  │ merge (发布时)
  │
release/v1.0.0 (发布候选，临时分支)
  ↑
  │ merge (功能完成后)
  │
develop (开发集成分支，受保护)
  ↑
  │ merge (开发完成后)
  │
feature/add-oauth-login (功能分支)
```

### 主要分支

#### 1. main分支

**用途**: 生产环境代码，始终保持可发布状态

**保护规则**:
- 🔒 完全保护，仅维护者可合并
- 禁止直接push
- 必须通过Pull Request
- 必须经过代码审查
- 必须通过CI/CD检查

**命名**: `main`

**生命周期**: 永久

```bash
# main分支只接受来自release分支或hotfix分支的合并
git checkout main
git merge --no-ff release/v1.0.0
git tag -a v1.0.0 -m "Release version 1.0.0"
git push origin main --tags
```

#### 2. develop分支

**用途**: 开发集成分支，汇总所有功能开发

**保护规则**:
- 🔐 部分保护，核心贡献者可合并
- 禁止直接push（除紧急情况）
- 推荐通过Pull Request
- 需要代码审查
- 需要通过CI/CD检查

**命名**: `develop`

**生命周期**: 永久

```bash
# develop分支接受来自feature分支的合并
git checkout develop
git merge --no-ff feature/add-oauth-login
git push origin develop
```

### 临时分支

#### 1. 功能分支 (feature/*)

**用途**: 开发新功能或改进

**从哪里创建**: `develop`

**合并到哪里**: `develop`

**命名规范**: `feature/功能描述`

**生命周期**: 功能开发完成后删除

```bash
# 创建功能分支
git checkout develop
git pull origin develop
git checkout -b feature/add-oauth-login

# 开发...
git add .
git commit -m "feat: 添加OAuth登录功能"

# 合并到develop
git checkout develop
git pull origin develop
git merge --no-ff feature/add-oauth-login
git push origin develop

# 删除功能分支
git branch -d feature/add-oauth-login
git push origin --delete feature/add-oauth-login
```

#### 2. 修复分支 (fix/*)

**用途**: 修复非紧急Bug

**从哪里创建**: `develop`

**合并到哪里**: `develop`

**命名规范**: `fix/bug描述`

**生命周期**: Bug修复完成后删除

```bash
# 创建修复分支
git checkout develop
git pull origin develop
git checkout -b fix/login-session-timeout

# 修复...
git add .
git commit -m "fix: 修复登录会话超时问题"

# 合并到develop
git checkout develop
git pull origin develop
git merge --no-ff fix/login-session-timeout
git push origin develop

# 删除修复分支
git branch -d fix/login-session-timeout
```

#### 3. 发布分支 (release/*)

**用途**: 准备新版本发布，进行最后的测试和修复

**从哪里创建**: `develop`

**合并到哪里**: `main` 和 `develop`

**命名规范**: `release/v主版本号.次版本号.修订号`

**生命周期**: 发布完成后删除

```bash
# 创建发布分支
git checkout develop
git pull origin develop
git checkout -b release/v1.0.0

# 修改版本号
vim pom.xml  # 修改version为1.0.0
git commit -am "chore: 升级版本号到1.0.0"

# 最后的测试和Bug修复...
git commit -am "fix: 修复发布前发现的小问题"

# 合并到main并打标签
git checkout main
git pull origin main
git merge --no-ff release/v1.0.0
git tag -a v1.0.0 -m "Release version 1.0.0"
git push origin main --tags

# 合并回develop
git checkout develop
git pull origin develop
git merge --no-ff release/v1.0.0
git push origin develop

# 删除发布分支
git branch -d release/v1.0.0
git push origin --delete release/v1.0.0
```

#### 4. 热修复分支 (hotfix/*)

**用途**: 紧急修复生产环境的严重Bug

**从哪里创建**: `main`

**合并到哪里**: `main` 和 `develop`

**命名规范**: `hotfix/紧急问题描述`

**生命周期**: 修复完成后删除

```bash
# 创建热修复分支
git checkout main
git pull origin main
git checkout -b hotfix/fix-critical-security-issue

# 紧急修复...
git add .
git commit -m "fix: 修复严重安全漏洞CVE-2025-12345"

# 升级版本号（修订号+1）
vim pom.xml  # 1.0.0 → 1.0.1
git commit -am "chore: 升级版本号到1.0.1"

# 合并到main并打标签
git checkout main
git pull origin main
git merge --no-ff hotfix/fix-critical-security-issue
git tag -a v1.0.1 -m "Hotfix: 修复安全漏洞"
git push origin main --tags

# 合并回develop
git checkout develop
git pull origin develop
git merge --no-ff hotfix/fix-critical-security-issue
git push origin develop

# 删除热修复分支
git branch -d hotfix/fix-critical-security-issue
git push origin --delete hotfix/fix-critical-security-issue
```

---

## 分支命名规范

### 功能分支命名

```bash
feature/功能描述              # 新功能
feature/add-oauth-login       # 添加OAuth登录
feature/multi-model-support   # 支持多模型
feature/user-profile-page     # 用户个人主页
```

### 修复分支命名

```bash
fix/bug描述                   # Bug修复
fix/login-session-timeout     # 修复登录会话超时
fix/api-rate-limit-error      # 修复API限流错误
fix/database-connection-leak  # 修复数据库连接泄露
```

### 文档分支命名

```bash
docs/文档描述                 # 文档更新
docs/update-api-guide         # 更新API使用指南
docs/add-deployment-steps     # 添加部署步骤
docs/translate-to-english     # 翻译成英文
```

### 重构分支命名

```bash
refactor/重构描述             # 代码重构
refactor/simplify-auth-logic  # 简化认证逻辑
refactor/extract-util-methods # 提取工具方法
refactor/optimize-db-queries  # 优化数据库查询
```

### 测试分支命名

```bash
test/测试描述                 # 测试相关
test/add-user-service-tests   # 添加UserService测试
test/e2e-generation-flow      # E2E生成流程测试
test/improve-coverage         # 提升测试覆盖率
```

### 构建分支命名

```bash
chore/构建描述                # 构建/工具变更
chore/upgrade-spring-boot     # 升级Spring Boot
chore/update-dependencies     # 更新依赖
chore/configure-ci-pipeline   # 配置CI流程
```

### 性能分支命名

```bash
perf/性能描述                 # 性能优化
perf/optimize-ai-calls        # 优化AI调用性能
perf/reduce-memory-usage      # 减少内存占用
perf/cache-frequent-queries   # 缓存高频查询
```

---

## 提交信息规范

Ingenio遵循 [Conventional Commits](https://www.conventionalcommits.org/) 规范。

### 提交消息格式

```
<类型>[可选范围]: <描述>

[可选正文]

[可选脚注]
```

### 提交类型

| 类型 | 说明 | 示例 |
|-----|------|------|
| `feat` | 新增功能 | feat: 添加OAuth登录支持 |
| `fix` | 修复Bug | fix: 修复登录会话超时问题 |
| `docs` | 文档变更 | docs: 更新API使用指南 |
| `style` | 代码格式（不影响逻辑） | style: 格式化代码 |
| `refactor` | 重构代码 | refactor: 简化认证逻辑 |
| `perf` | 性能优化 | perf: 优化数据库查询性能 |
| `test` | 测试相关 | test: 添加UserService单元测试 |
| `chore` | 构建/工具变更 | chore: 升级Spring Boot到3.4.0 |
| `ci` | CI/CD变更 | ci: 添加GitHub Actions工作流 |
| `revert` | 回滚提交 | revert: 回滚feat: 添加OAuth登录 |

### 提交范围（可选）

```bash
feat(auth): 添加OAuth登录支持
fix(api): 修复分页参数验证错误
docs(readme): 更新安装步骤
refactor(service): 简化UserService逻辑
```

### 提交描述规则

1. **使用中文描述**（项目主要语言）
2. **使用动词开头**（添加、修复、更新、重构等）
3. **描述简洁明确**（不超过50个字符）
4. **不添加句号**
5. **首字母小写**（除非专有名词）

### 提交示例

#### 简单提交

```bash
git commit -m "feat: 添加用户头像上传功能"
git commit -m "fix: 修复分页参数验证错误"
git commit -m "docs: 完善开发环境搭建指南"
git commit -m "refactor: 优化数据库查询性能"
git commit -m "test: 添加UserService单元测试"
```

#### 详细提交（包含正文）

```bash
git commit -m "feat: 实现多模态输入支持

支持文本、语音、图片三种输入方式：
- 文本输入：直接传递自然语言需求
- 语音输入：集成DeepSeek语音转文字API
- 图片输入：使用DeepSeek视觉模型分析UI截图

相关Issue: #123
测试覆盖率: 89%"
```

#### 包含Breaking Change

```bash
git commit -m "feat!: 重构认证系统使用JWT

BREAKING CHANGE: 旧的Session认证方式已移除，
所有客户端需要升级到JWT认证。

迁移指南: docs/migration/v1-to-v2-auth.md
影响范围: 所有API调用需要携带Authorization头"
```

#### 回滚提交

```bash
git commit -m "revert: 回滚feat: 添加OAuth登录支持

This reverts commit 1234567890abcdef.

原因: OAuth登录功能存在安全问题，需要重新设计"
```

### 提交前检查

```bash
# 1. 查看修改
git status
git diff

# 2. 运行测试
mvn test

# 3. 代码规范检查
mvn checkstyle:check

# 4. 提交
git add .
git commit -m "feat: 添加新功能"
```

---

## 工作流程

### 日常开发流程

#### 1. 同步最新代码

```bash
# 切换到develop分支
git checkout develop

# 拉取最新代码
git pull origin develop

# 查看当前状态
git status
```

#### 2. 创建功能分支

```bash
# 创建并切换到功能分支
git checkout -b feature/add-oauth-login

# 或分两步
git branch feature/add-oauth-login
git checkout feature/add-oauth-login

# 验证当前分支
git branch
# * feature/add-oauth-login
#   develop
```

#### 3. 开发和提交

```bash
# 开发功能...

# 查看修改
git status
git diff

# 添加到暂存区
git add .

# 或添加指定文件
git add src/main/java/com/ingenio/backend/service/OAuthService.java
git add src/main/java/com/ingenio/backend/controller/OAuthController.java

# 提交
git commit -m "feat: 添加OAuth登录支持"

# 继续开发，多次提交...
git commit -m "feat: 添加OAuth回调处理"
git commit -m "test: 添加OAuth登录测试"
```

#### 4. 推送到远程

```bash
# 首次推送，创建远程分支
git push -u origin feature/add-oauth-login

# 后续推送
git push
```

#### 5. 同步develop最新代码

```bash
# 切换到develop分支
git checkout develop

# 拉取最新代码
git pull origin develop

# 切换回功能分支
git checkout feature/add-oauth-login

# 合并develop的最新代码
git merge develop

# 或使用rebase（保持提交历史线性）
git rebase develop

# 解决冲突（如有）...

# 推送到远程
git push origin feature/add-oauth-login
```

#### 6. 创建Pull Request

1. 访问GitHub仓库
2. 点击 **Compare & pull request** 按钮
3. 填写PR标题和描述（见[贡献指南](../../CONTRIBUTING.md)）
4. 指定审查者
5. 点击 **Create pull request**

#### 7. 代码审查和修改

```bash
# 根据审查意见修改代码...

# 提交修改
git add .
git commit -m "fix: 根据审查意见修复OAuth错误处理"

# 推送到远程（自动更新PR）
git push origin feature/add-oauth-login
```

#### 8. 合并到develop

审查通过后，维护者将合并PR：

```bash
# 维护者操作
git checkout develop
git pull origin develop
git merge --no-ff feature/add-oauth-login
git push origin develop

# 删除远程分支
git push origin --delete feature/add-oauth-login
```

#### 9. 删除本地分支

```bash
# 切换到develop分支
git checkout develop

# 拉取最新代码
git pull origin develop

# 删除本地功能分支
git branch -d feature/add-oauth-login

# 如果分支未合并，强制删除
git branch -D feature/add-oauth-login
```

### 发布流程

#### 1. 创建发布分支

```bash
# 从develop创建发布分支
git checkout develop
git pull origin develop
git checkout -b release/v1.0.0
```

#### 2. 更新版本号

```xml
<!-- pom.xml -->
<version>1.0.0</version>
```

```bash
git commit -am "chore: 升级版本号到1.0.0"
```

#### 3. 最后的测试和修复

```bash
# 运行完整测试套件
mvn clean test

# 运行E2E测试
mvn verify

# 修复发现的Bug
git commit -am "fix: 修复发布前发现的问题"
```

#### 4. 合并到main

```bash
# 合并到main
git checkout main
git pull origin main
git merge --no-ff release/v1.0.0

# 打标签
git tag -a v1.0.0 -m "Release version 1.0.0

主要变更:
- 添加OAuth登录功能
- 实现多模态输入支持
- 优化AI代理性能

完整变更日志: CHANGELOG.md"

# 推送到远程
git push origin main --tags
```

#### 5. 合并回develop

```bash
git checkout develop
git pull origin develop
git merge --no-ff release/v1.0.0
git push origin develop
```

#### 6. 删除发布分支

```bash
git branch -d release/v1.0.0
git push origin --delete release/v1.0.0
```

### 热修复流程

#### 1. 创建热修复分支

```bash
# 从main创建热修复分支
git checkout main
git pull origin main
git checkout -b hotfix/fix-critical-security-issue
```

#### 2. 紧急修复

```bash
# 修复问题...
git add .
git commit -m "fix: 修复严重安全漏洞CVE-2025-12345"

# 运行测试
mvn test

# 更新版本号（修订号+1）
vim pom.xml  # 1.0.0 → 1.0.1
git commit -am "chore: 升级版本号到1.0.1"
```

#### 3. 合并到main

```bash
git checkout main
git pull origin main
git merge --no-ff hotfix/fix-critical-security-issue
git tag -a v1.0.1 -m "Hotfix: 修复安全漏洞CVE-2025-12345"
git push origin main --tags
```

#### 4. 合并回develop

```bash
git checkout develop
git pull origin develop
git merge --no-ff hotfix/fix-critical-security-issue
git push origin develop
```

#### 5. 删除热修复分支

```bash
git branch -d hotfix/fix-critical-security-issue
git push origin --delete hotfix/fix-critical-security-issue
```

---

## 合并策略

### --no-ff合并（推荐）

**优点**: 保留分支历史，清晰可追溯

```bash
git merge --no-ff feature/add-oauth-login
```

**提交历史**:
```
*   Merge branch 'feature/add-oauth-login' into develop
|\
| * feat: 添加OAuth回调处理
| * feat: 添加OAuth登录支持
|/
* feat: 上一个功能
```

### Fast-forward合并（不推荐）

**缺点**: 丢失分支历史

```bash
git merge feature/add-oauth-login
```

**提交历史**:
```
* feat: 添加OAuth回调处理
* feat: 添加OAuth登录支持
* feat: 上一个功能
```

### Squash合并（特定场景）

**用途**: 将多个提交压缩为一个，适合小功能

```bash
git merge --squash feature/add-oauth-login
git commit -m "feat: 添加OAuth登录支持"
```

**适用场景**:
- 多次临时提交需要合并
- 提交历史过于琐碎
- 需要清理提交记录

### Rebase（保持线性历史）

**用途**: 保持提交历史线性，避免合并提交

```bash
git checkout feature/add-oauth-login
git rebase develop

# 解决冲突...
git rebase --continue

# 推送到远程（需要强制推送）
git push --force-with-lease origin feature/add-oauth-login
```

**注意**: 仅在功能分支上使用rebase，不要在公共分支上使用

---

## 版本标签规范

### 版本号格式

遵循 [Semantic Versioning](https://semver.org/) 规范：

```
主版本号.次版本号.修订号

例如: v1.2.3
```

### 版本号说明

| 部分 | 何时增加 | 示例 |
|-----|---------|------|
| **主版本号** | 不兼容的API变更 | v1.0.0 → v2.0.0 |
| **次版本号** | 向下兼容的新功能 | v1.0.0 → v1.1.0 |
| **修订号** | 向下兼容的Bug修复 | v1.0.0 → v1.0.1 |

### 创建标签

```bash
# 轻量标签（不推荐）
git tag v1.0.0

# 附注标签（推荐）
git tag -a v1.0.0 -m "Release version 1.0.0

主要变更:
- 添加OAuth登录功能
- 实现多模态输入支持
- 优化AI代理性能

完整变更日志: CHANGELOG.md"

# 推送标签到远程
git push origin v1.0.0

# 推送所有标签
git push origin --tags
```

### 查看标签

```bash
# 列出所有标签
git tag

# 列出特定模式的标签
git tag -l "v1.*"

# 查看标签详情
git show v1.0.0

# 查看标签对应的提交
git log v1.0.0
```

### 删除标签

```bash
# 删除本地标签
git tag -d v1.0.0

# 删除远程标签
git push origin --delete v1.0.0
```

### 检出标签

```bash
# 查看标签代码（分离HEAD状态）
git checkout v1.0.0

# 从标签创建分支
git checkout -b hotfix/from-v1.0.0 v1.0.0
```

---

## 冲突解决

### 合并冲突

```bash
# 合并时发生冲突
git merge develop

# 输出:
# Auto-merging src/main/java/com/ingenio/backend/service/UserService.java
# CONFLICT (content): Merge conflict in src/main/java/com/ingenio/backend/service/UserService.java
# Automatic merge failed; fix conflicts and then commit the result.

# 查看冲突文件
git status

# 手动编辑冲突文件
vim src/main/java/com/ingenio/backend/service/UserService.java

# 冲突标记示例:
<<<<<<< HEAD
// 你的修改
public void createUser(CreateUserRequest request) {
    validateEmail(request.getEmail());
    // ...
}
=======
// develop分支的修改
public void createUser(CreateUserRequest request) {
    validateUserRequest(request);
    // ...
}
>>>>>>> develop

# 解决冲突后，删除冲突标记，保留正确代码
public void createUser(CreateUserRequest request) {
    validateUserRequest(request);  # 保留develop的修改
    validateEmail(request.getEmail());  # 也保留你的修改
    // ...
}

# 标记冲突已解决
git add src/main/java/com/ingenio/backend/service/UserService.java

# 完成合并
git commit -m "Merge branch 'develop' into feature/add-oauth-login

解决冲突:
- UserService: 合并validateUserRequest和validateEmail逻辑"
```

### Rebase冲突

```bash
# Rebase时发生冲突
git rebase develop

# 输出:
# CONFLICT (content): Merge conflict in src/main/java/com/ingenio/backend/service/UserService.java
# error: could not apply 1234567... feat: 添加OAuth登录支持
# Resolve all conflicts manually, mark them as resolved with "git add/rm <conflicted_files>", then run "git rebase --continue".

# 解决冲突...
vim src/main/java/com/ingenio/backend/service/UserService.java

# 标记冲突已解决
git add src/main/java/com/ingenio/backend/service/UserService.java

# 继续rebase
git rebase --continue

# 如果放弃rebase
git rebase --abort
```

### 冲突预防

```bash
# 1. 经常同步develop分支
git checkout feature/add-oauth-login
git pull origin develop

# 2. 小步提交，频繁推送
git commit -m "feat: 完成OAuth配置"
git push origin feature/add-oauth-login

# 3. 代码审查及时合并
# 避免功能分支长时间存在
```

---

## 代码回滚

### 回滚未提交的修改

```bash
# 撤销工作区修改（未add）
git checkout -- src/main/java/com/ingenio/backend/service/UserService.java

# 撤销所有工作区修改
git checkout -- .

# 撤销暂存区修改（已add，未commit）
git reset HEAD src/main/java/com/ingenio/backend/service/UserService.java

# 撤销所有暂存区修改
git reset HEAD
```

### 回滚本地提交

```bash
# 查看提交历史
git log --oneline

# 输出:
# 1234567 feat: 添加OAuth登录支持
# 890abcd feat: 上一个功能
# ...

# 方式1: reset --soft（保留修改，撤销提交）
git reset --soft HEAD~1  # 撤销最近1次提交

# 方式2: reset --mixed（撤销提交和暂存，保留修改）
git reset HEAD~1  # 默认--mixed

# 方式3: reset --hard（完全删除提交和修改）⚠️危险
git reset --hard HEAD~1

# 撤销到指定提交
git reset --hard 890abcd
```

### 回滚远程提交

```bash
# 方式1: revert（推荐，创建新提交撤销旧提交）
git revert 1234567
git push origin feature/add-oauth-login

# 方式2: reset + force push（危险，慎用）
git reset --hard HEAD~1
git push --force origin feature/add-oauth-login  # 强制推送
```

### 回滚合并提交

```bash
# 回滚合并到main的提交
git checkout main
git revert -m 1 1234567  # -m 1表示保留main的修改
git push origin main
```

---

## 最佳实践

### 提交频率

- ✅ **小步提交**: 每完成一个小功能就提交
- ✅ **功能完整**: 每次提交是一个完整的功能点
- ❌ **大块提交**: 避免一次提交包含多个不相关的修改

```bash
# ✅ 推荐：小步提交
git commit -m "feat: 添加OAuth配置"
git commit -m "feat: 实现OAuth回调处理"
git commit -m "test: 添加OAuth测试"

# ❌ 不推荐：大块提交
git commit -m "feat: 完成OAuth功能，包括配置、回调、测试等"
```

### 分支管理

- ✅ **及时删除**: 合并后立即删除功能分支
- ✅ **保持同步**: 经常同步develop分支到功能分支
- ✅ **分支专注**: 一个分支只做一件事

```bash
# ✅ 推荐：功能分支专注单一功能
feature/add-oauth-login
feature/implement-rate-limiting

# ❌ 不推荐：功能分支包含多个不相关功能
feature/add-oauth-and-rate-limiting-and-user-avatar
```

### 代码审查

- ✅ **小PR**: 每个PR不超过500行代码
- ✅ **自我审查**: 提交PR前自己先审查一遍
- ✅ **及时响应**: 快速响应审查意见

### Commit消息

- ✅ **描述清晰**: 准确描述做了什么
- ✅ **包含原因**: 必要时说明为什么这样做
- ✅ **引用Issue**: 关联相关的Issue

```bash
# ✅ 推荐：清晰描述
git commit -m "feat: 添加OAuth登录支持

支持GitHub、Google、Microsoft三种OAuth提供商。

关联Issue: #123
测试覆盖率: 92%"

# ❌ 不推荐：模糊描述
git commit -m "update code"
git commit -m "fix bug"
```

### 避免的操作

- ❌ **直接push到main**: 永远不要直接推送到main分支
- ❌ **force push到公共分支**: 不要强制推送到develop或main
- ❌ **提交敏感信息**: 不要提交密码、API Key等

```bash
# ❌ 禁止操作
git push origin main  # 直接push到main
git push --force origin develop  # 强制push到develop

# ✅ 正确操作
git push origin feature/add-oauth-login  # push到功能分支
# 通过PR合并到develop或main
```

---

## 常用Git命令

### 基础操作

```bash
# 初始化仓库
git init

# 克隆仓库
git clone https://github.com/ingenio/ingenio.git

# 查看状态
git status

# 查看修改
git diff
git diff --staged  # 查看暂存区修改

# 添加到暂存区
git add .
git add src/main/java/com/ingenio/backend/service/UserService.java

# 提交
git commit -m "feat: 添加新功能"

# 推送到远程
git push origin feature/add-oauth-login

# 拉取远程代码
git pull origin develop
```

### 分支操作

```bash
# 查看分支
git branch
git branch -a  # 查看所有分支（包括远程）

# 创建分支
git branch feature/add-oauth-login

# 切换分支
git checkout feature/add-oauth-login

# 创建并切换分支
git checkout -b feature/add-oauth-login

# 删除分支
git branch -d feature/add-oauth-login  # 安全删除
git branch -D feature/add-oauth-login  # 强制删除

# 重命名分支
git branch -m old-name new-name

# 删除远程分支
git push origin --delete feature/add-oauth-login
```

### 远程操作

```bash
# 查看远程仓库
git remote -v

# 添加远程仓库
git remote add origin https://github.com/ingenio/ingenio.git

# 修改远程仓库URL
git remote set-url origin https://github.com/ingenio/ingenio.git

# 拉取远程分支
git fetch origin
git fetch origin develop

# 拉取并合并
git pull origin develop

# 推送到远程
git push origin feature/add-oauth-login
git push origin main --tags  # 推送标签
```

### 历史查看

```bash
# 查看提交历史
git log
git log --oneline  # 简洁模式
git log --graph --oneline --all  # 图形化显示

# 查看文件历史
git log -- src/main/java/com/ingenio/backend/service/UserService.java

# 查看指定作者的提交
git log --author="zhangsan"

# 查看指定日期范围的提交
git log --since="2025-01-01" --until="2025-12-31"

# 查看提交详情
git show 1234567
```

### 撤销和回滚

```bash
# 撤销工作区修改
git checkout -- file.txt

# 撤销暂存区修改
git reset HEAD file.txt

# 回滚提交
git reset --soft HEAD~1  # 保留修改
git reset --mixed HEAD~1  # 撤销暂存
git reset --hard HEAD~1  # 删除修改

# 回滚指定提交
git revert 1234567
```

### Stash操作

```bash
# 暂存当前修改
git stash

# 暂存并添加说明
git stash save "临时保存OAuth功能开发"

# 查看stash列表
git stash list

# 应用stash
git stash apply  # 应用最近的stash
git stash apply stash@{0}  # 应用指定stash

# 应用并删除stash
git stash pop

# 删除stash
git stash drop stash@{0}

# 清空所有stash
git stash clear
```

### 标签操作

```bash
# 创建标签
git tag v1.0.0
git tag -a v1.0.0 -m "Release version 1.0.0"

# 查看标签
git tag
git show v1.0.0

# 推送标签
git push origin v1.0.0
git push origin --tags

# 删除标签
git tag -d v1.0.0
git push origin --delete v1.0.0

# 检出标签
git checkout v1.0.0
git checkout -b branch-from-tag v1.0.0
```

### 高级操作

```bash
# Cherry-pick（挑选特定提交）
git cherry-pick 1234567

# Rebase（变基）
git rebase develop
git rebase -i HEAD~3  # 交互式rebase

# Squash（压缩提交）
git merge --squash feature/add-oauth-login

# Reflog（查看操作历史）
git reflog

# 清理无用分支
git remote prune origin

# 查看文件的每一行最后修改人
git blame file.txt
```

---

## Git配置

### 全局配置

```bash
# 配置用户信息
git config --global user.name "Your Name"
git config --global user.email "your.email@example.com"

# 配置默认编辑器
git config --global core.editor "vim"

# 配置默认分支名称
git config --global init.defaultBranch main

# 配置自动换行
git config --global core.autocrlf input  # macOS/Linux
git config --global core.autocrlf true   # Windows

# 配置颜色显示
git config --global color.ui auto

# 查看配置
git config --list
git config --global --list
```

### 别名配置

```bash
# 配置常用别名
git config --global alias.st status
git config --global alias.co checkout
git config --global alias.br branch
git config --global alias.ci commit
git config --global alias.unstage 'reset HEAD --'
git config --global alias.last 'log -1 HEAD'
git config --global alias.lg "log --graph --oneline --all"

# 使用别名
git st  # 等同于 git status
git co develop  # 等同于 git checkout develop
git lg  # 等同于 git log --graph --oneline --all
```

---

## 参考资料

- [Pro Git Book](https://git-scm.com/book/zh/v2)
- [GitFlow Workflow](https://www.atlassian.com/git/tutorials/comparing-workflows/gitflow-workflow)
- [Conventional Commits](https://www.conventionalcommits.org/)
- [Semantic Versioning](https://semver.org/)
- [GitHub Flow](https://docs.github.com/en/get-started/quickstart/github-flow)

---

**文档信息**

- 版本: v1.0
- 最后更新: 2025-11-09
- 维护人: Ingenio Team
- 反馈问题: https://github.com/ingenio/ingenio/issues
