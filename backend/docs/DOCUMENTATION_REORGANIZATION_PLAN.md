# INGENIO项目文档重组方案

> **执行时间**: 2025-11-09
> **目标**: 规范化文档结构，生成完整的项目文档，删除无用文件
> **原则**: 遵循specify agents理念，每种类型文档由专门的agent生成

---

## 1. 现状分析

### 1.1 现有文档清单

#### ✅ 良好文档（保留）
1. `/docs/architecture/ARCHITECTURE.md` - 架构设计文档（详细）
2. `/docs/implementation-roadmap.md` - 8周MVP实施路线图（完整）
3. `/docs/product-tech-design.md` - 产品技术设计
4. `/backend/README.md` - 后端基础文档
5. `/backend/docs/DATABASE_SCHEMA.md` - 数据库Schema文档
6. `/backend/docs/PHASE2_SUMMARY.md` - Phase 2总结
7. `/docs/supabase-migration-guide.md` - Supabase迁移指南

#### ⚠️ 位置不当（需移动）
1. `/backend/KUIKLYUI_RENDERER_DELIVERY.md` → `/backend/docs/kuiklyui/DELIVERY.md`
2. `/backend/KUIKLYUI_RENDERER_EXAMPLE.md` → `/backend/docs/kuiklyui/EXAMPLE.md`
3. `/backend/KUIKLYUI_RENDERER_TECHNICAL_DOC.md` → `/backend/docs/kuiklyui/TECHNICAL.md`
4. `/backend/CONTROLLER_API.md` → `/backend/docs/api/CONTROLLER_API.md`

#### ❌ 应删除（无用文件）
1. `/backend/backend.log` - 日志文件
2. `/backend/backend-new.log` - 日志文件
3. `/backend/pom.xml.bak2` - 备份文件

### 1.2 缺失的关键文档

#### 高优先级（P0）
1. **完整API参考文档** - 基于Phase 1.1-2.2实现
   - `/backend/docs/api/TIMEMACHINE_API.md` - 时光机版本快照API
   - `/backend/docs/api/SUPERDESIGN_API.md` - SuperDesign AI多方案API
   - `/backend/docs/api/VALIDATION_API.md` - 代码验证编排API
   - `/backend/docs/api/CODE_GENERATION_API.md` - Kotlin Multiplatform代码生成API

2. **E2E测试文档**
   - `/backend/docs/testing/E2E_TESTING_GUIDE.md` - E2E测试指南
   - `/backend/docs/testing/TEST_COVERAGE_REPORT.md` - 测试覆盖率报告

3. **部署文档**
   - `/docs/deployment/DEPLOYMENT_GUIDE.md` - 部署指南
   - `/docs/deployment/DOCKER_GUIDE.md` - Docker部署
   - `/docs/deployment/KUBERNETES_GUIDE.md` - Kubernetes部署

#### 中优先级（P1）
4. **开发者贡献指南**
   - `/CONTRIBUTING.md` - 贡献指南
   - `/CODE_OF_CONDUCT.md` - 行为准则

5. **变更日志**
   - `/CHANGELOG.md` - 项目变更历史

6. **开发指南**
   - `/docs/development/DEVELOPMENT_GUIDE.md` - 开发环境搭建
   - `/docs/development/CODING_STANDARDS.md` - 代码规范
   - `/docs/development/GIT_WORKFLOW.md` - Git工作流

---

## 2. 目标文档结构

### 2.1 根目录
```
Ingenio/
├── README.md                      # 项目总览（更新）
├── CONTRIBUTING.md                # 贡献指南（新增）
├── CODE_OF_CONDUCT.md             # 行为准则（新增）
├── CHANGELOG.md                   # 变更日志（新增）
├── LICENSE                        # 许可证
└── docs/                          # 文档目录
```

### 2.2 docs/目录结构
```
docs/
├── architecture/
│   └── ARCHITECTURE.md            # 架构文档（保留）
├── product/
│   ├── product-tech-design.md     # 产品设计（保留）
│   └── implementation-roadmap.md  # 实施路线图（保留）
├── deployment/
│   ├── DEPLOYMENT_GUIDE.md        # 部署指南（新增）
│   ├── DOCKER_GUIDE.md            # Docker指南（新增）
│   └── KUBERNETES_GUIDE.md        # K8s指南（新增）
├── development/
│   ├── DEVELOPMENT_GUIDE.md       # 开发指南（新增）
│   ├── CODING_STANDARDS.md        # 代码规范（新增）
│   └── GIT_WORKFLOW.md            # Git工作流（新增）
├── migration/
│   └── supabase-migration-guide.md # Supabase迁移（保留）
└── legacy/
    └── 20251224/
        ├── 05-FRONTEND_DESIGN_ANALYSIS.md # 旧文档（归档）
        ├── 06-KuiklyUI集成测试报告.md      # 旧文档（归档）
        └── 07-KuiklyUI集成验证计划.md      # 旧文档（归档）
```

### 2.3 backend/docs/目录结构
```
backend/docs/
├── api/                           # API参考文档
│   ├── TIMEMACHINE_API.md         # 时光机API（新增）
│   ├── SUPERDESIGN_API.md         # SuperDesign API（新增）
│   ├── VALIDATION_API.md          # 验证API（新增）
│   ├── CODE_GENERATION_API.md     # 代码生成API（新增）
│   └── CONTROLLER_API.md          # 控制器API（移动）
├── database/
│   └── DATABASE_SCHEMA.md         # 数据库Schema（保留）
├── testing/
│   ├── E2E_TESTING_GUIDE.md       # E2E测试指南（新增）
│   └── TEST_COVERAGE_REPORT.md    # 测试覆盖率（新增）
├── kuiklyui/                      # KuiklyUI文档
│   ├── TECHNICAL.md               # 技术文档（移动）
│   ├── EXAMPLE.md                 # 示例文档（移动）
│   └── DELIVERY.md                # 交付文档（移动）
└── phases/                        # Phase总结
    └── PHASE2_SUMMARY.md          # Phase 2（保留）
```

---

## 3. 执行计划

### Phase 1: 目录规整（30分钟）
1. 创建新目录结构
2. 移动现有文档到正确位置
3. 删除无用文件
4. 更新文档内部链接

### Phase 2: 生成新文档（2小时）
使用专门的agents生成以下文档：

#### 2.1 API文档（technical-writer agent）
- `TIMEMACHINE_API.md` - 基于`TimeMachineController.java`和`VersionSnapshotService.java`
- `SUPERDESIGN_API.md` - 基于`SuperDesignController.java`和`SuperDesignService.java`
- `VALIDATION_API.md` - 基于`ValidationOrchestrator.java`
- `CODE_GENERATION_API.md` - 基于`KotlinMultiplatformGenerator.java`

#### 2.2 测试文档（test-writer agent）
- `E2E_TESTING_GUIDE.md` - 基于`BaseE2ETest.java`、`TimeMachineE2ETest.java`、`SuperDesignE2ETest.java`
- `TEST_COVERAGE_REPORT.md` - 基于实际测试覆盖率

#### 2.3 部署文档（devops-automator agent）
- `DEPLOYMENT_GUIDE.md` - 综合部署指南
- `DOCKER_GUIDE.md` - Docker部署指南
- `KUBERNETES_GUIDE.md` - Kubernetes部署指南

#### 2.4 开发文档（technical-writer agent）
- `DEVELOPMENT_GUIDE.md` - 开发环境搭建
- `CODING_STANDARDS.md` - 代码规范
- `GIT_WORKFLOW.md` - Git工作流
- `CONTRIBUTING.md` - 贡献指南

#### 2.5 变更日志（changelog-generator agent）
- `CHANGELOG.md` - 基于Git提交历史

### Phase 3: 质量检查（30分钟）
1. 验证所有文档链接正确
2. 检查Markdown语法
3. 确认代码示例可运行
4. 验证目录结构符合规范

### Phase 4: 提交Git（15分钟）
```bash
git add .
git commit -m "docs: 重组项目文档结构，生成完整API和部署文档"
git push
```

---

## 4. 文档生成Agent映射

| 文档类型 | 专门Agent | 输入源 |
|---------|----------|-------|
| API参考文档 | technical-writer | Java源代码 |
| E2E测试文档 | test-writer | 测试代码 |
| 部署文档 | devops-automator | Dockerfile, K8s配置 |
| 开发指南 | technical-writer | 项目结构 |
| 变更日志 | 无（手动） | Git历史 |

---

## 5. 文档质量标准

### 5.1 API文档要求
- 包含完整的请求/响应示例
- 包含错误码说明
- 包含认证要求
- 包含速率限制说明
- 包含版本变更历史

### 5.2 部署文档要求
- 包含前置要求清单
- 包含详细步骤说明
- 包含常见问题解答
- 包含故障排查指南
- 包含监控和日志配置

### 5.3 开发文档要求
- 包含环境搭建步骤
- 包含依赖安装说明
- 包含本地运行命令
- 包含调试技巧
- 包含常见错误解决

---

## 6. 维护计划

### 6.1 文档更新触发条件
- 新增API端点 → 更新API文档
- 新增功能 → 更新CHANGELOG.md
- 修复Bug → 更新CHANGELOG.md
- 架构变更 → 更新ARCHITECTURE.md
- 部署变更 → 更新DEPLOYMENT_GUIDE.md

### 6.2 文档审查机制
- 每次PR必须更新相关文档
- 每月进行文档准确性审查
- 每季度进行文档完整性审查

---

**批准人**: Claude Code
**执行人**: Specialized Agents (technical-writer, devops-automator, test-writer)
**预计完成时间**: 3小时
