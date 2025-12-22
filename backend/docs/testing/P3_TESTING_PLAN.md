# P3增强功能测试计划

> **项目**: Ingenio P3增强（意图识别 + 复杂度评估 + 需求改写）
> **创建日期**: 2025-11-20
> **状态**: Phase 3.1-3.4已完成，Phase 3.5测试计划已制定

---

## 📋 P3功能概述

P3增强功能实现了完整的需求优化Pipeline：

```
用户输入需求
    ↓
IntentClassifier (意图识别) ✅
    ↓
ComplexityEvaluator (复杂度评估) ✅ Phase 3.3
    ↓
RequirementRefiner (需求改写) ✅ Phase 3.4
    ↓
后续代码生成流程
```

**核心价值**：解决"完不成的需求"问题，将模糊、不切实际的需求转化为可执行的结构化需求。

---

## 🎯 测试目标

1. **验证P3 Pipeline完整性**：确保IntentClassifier → ComplexityEvaluator → RequirementRefiner流程正确
2. **验证需求改写有效性**：证明RequirementRefiner能够将"完不成"的需求转化为可执行需求
3. **验证改写策略准确性**：不同复杂度和意图类型触发正确的改写策略

---

## 🧪 测试策略

### Layer 1: 单元测试（目标覆盖率≥90%）

#### RequirementRefinerImpl核心方法测试
- [x] 设计完成
- [ ] 实施待定（暂缓，优先集成测试）

**测试点**：
- refine()方法的6种改写策略（DETAIL_ENHANCEMENT, PRIORITY_SPLIT, PHASED_DEVELOPMENT, COMPLEXITY_REDUCTION, BOUNDARY_CLARIFICATION, NO_REFINE_NEEDED）
- parseResponse()的JSON解析（标准格式、Markdown包裹格式、错误格式）
- 参数验证和异常处理
- Mock ChatModel响应

**延后原因**：
- Mock Spring AI ChatClient API较复杂
- 集成测试能提供更直接的价值
- 单元测试投入产出比较低

---

### Layer 2: 集成测试（优先级⭐⭐⭐）

#### P3 Pipeline完整流程测试

**测试文件**：`P3PipelineIntegrationTest.java`

**测试场景**：

| 测试场景 | 用户需求 | 期望意图 | 期望复杂度 | 期望改写策略 |
|---------|----------|---------|-----------|------------|
| 简单需求 | "做一个待办事项app" | DESIGN_FROM_SCRATCH | SIMPLE | DETAIL_ENHANCEMENT |
| 克隆网站 | "仿照小红书做内容社区" | CLONE_EXISTING_WEBSITE | MEDIUM | PRIORITY_SPLIT |
| 混合意图 | "参考美团做外卖平台+AI推荐" | HYBRID_CLONE_AND_CUSTOMIZE | MEDIUM | BOUNDARY_CLARIFICATION |
| 过于复杂 | "做企业级ERP系统（全模块）" | DESIGN_FROM_SCRATCH | COMPLEX | PHASED_DEVELOPMENT |

**验收标准**：
- ComplexityScore在各组件间正确传递
- 复杂度等级与改写策略映射正确
- 所有测试用例通过率100%

**实施方案**：
```java
@SpringBootTest
@ActiveProfiles("test")
class P3PipelineIntegrationTest {

    @Autowired
    private IntentClassifier intentClassifier;

    @Autowired
    private RequirementRefiner requirementRefiner;

    @Test
    void testSimpleRequirement_CompletePipeline() {
        // Step 1: 意图识别 + 复杂度评估
        IntentClassificationResult classifyResult = intentClassifier.classify("做一个待办事项app");

        // 验证意图和复杂度
        assertThat(classifyResult.getIntent()).isEqualTo(RequirementIntent.DESIGN_FROM_SCRATCH);
        assertThat(classifyResult.getComplexityScore().getLevel()).isEqualTo(ComplexityLevel.SIMPLE);

        // Step 2: 需求改写
        RefinedRequirement refined = requirementRefiner.refine(
            "做一个待办事项app",
            classifyResult.getIntent(),
            classifyResult.getComplexityScore()
        );

        // 验证改写策略
        assertThat(refined.getRefineType()).isEqualTo(RefinedRequirement.RefineType.DETAIL_ENHANCEMENT);
        assertThat(refined.getCoreEntities()).isNotEmpty();
        assertThat(refined.getMvpFeatures()).isNotEmpty();
    }
}
```

---

### Layer 3: E2E验证测试（优先级⭐⭐⭐⭐⭐）

#### 典型失败场景测试

**测试文件**：`RequirementRefinerE2ETest.java`

**典型失败场景**（来自RequirementRefiner JavaDoc）：

#### 场景1: 过于宽泛

| 输入 | 问题 | 期望改写效果 |
|-----|------|-------------|
| "做一个app" | 极度模糊，缺少业务领域 | 补充实体、功能、技术约束 |
| "创建网站" | 缺少功能说明 | 明确业务领域和核心功能 |

**验收标准**：
- 改写后文本长度增加≥300%
- 核心实体数量≥1
- MVP功能数量≥2

#### 场景2: 不切实际

| 输入 | 问题 | 期望改写效果 |
|-----|------|-------------|
| "做比淘宝更强大的电商平台" | 不切实际的目标 | 去除比较，设定合理MVP |
| "AI+区块链+元宇宙电商" | 技术栈超出能力 | 移除不切实际功能 |

**验收标准**：
- MVP功能数量≤5
- 移除或延后高级功能
- needsUserConfirmation=true

#### 场景3: 缺少边界

| 输入 | 问题 | 期望改写效果 |
|-----|------|-------------|
| "仿照淘宝做电商" | 未明确参考范围 | 拆分MVP和扩展功能 |
| "参考小红书做社区" | 边界不清 | 明确核心功能和技术约束 |

**验收标准**：
- mvpFeatures与futureFeatures明确拆分
- technicalConstraints已定义
- 改写策略为PRIORITY_SPLIT或BOUNDARY_CLARIFICATION

#### 场景4: 混合意图不清

| 输入 | 问题 | 期望改写效果 |
|-----|------|-------------|
| "参考美团但要完全不同" | 矛盾需求 | 澄清参考和定制边界 |
| "参考Airbnb+美团+小红书" | 多个参考对象 | 明确核心参考，拆分阶段 |

**验收标准**：
- 明确参考和定制部分
- entityRelationships已定义
- 改写策略为BOUNDARY_CLARIFICATION

---

## 📊 度量指标

### 改写效果度量

| 指标 | 定义 | 目标值 | 测试方法 |
|-----|------|-------|---------|
| **改写成功率** | 需求改写不抛出异常的比例 | ≥90% | 批量测试10个典型失败需求 |
| **信息增益** | 改写后需求文本长度增加比例 | ≥200% | (改写后长度-原长度)/原长度×100% |
| **可执行性** | 改写后需求包含明确实体/功能/约束 | 100% | 验证coreEntities、mvpFeatures、technicalConstraints非空 |
| **用户确认率** | MEDIUM/COMPLEX需要用户确认的比例 | ≥80% | needsUserConfirmation=true的比例 |

### 策略准确性度量

| 复杂度 | 意图类型 | 期望策略 | 准确率目标 |
|-------|----------|---------|-----------|
| SIMPLE | DESIGN | DETAIL_ENHANCEMENT | ≥90% |
| MEDIUM | CLONE | PRIORITY_SPLIT | ≥85% |
| COMPLEX | DESIGN | PHASED_DEVELOPMENT | ≥80% |
| COMPLEX | HYBRID | COMPLEXITY_REDUCTION | ≥80% |

---

## 🔄 实施roadmap

### Phase 3.5.1: 集成测试实施（优先级：高）

**时间估算**：2-3小时

**产出物**：
- P3PipelineIntegrationTest.java完整实现
- 至少5个典型场景测试通过
- ComplexityScore数据流验证通过

**执行方式**：真实AI API调用，无Mock

### Phase 3.5.2: E2E验证测试（优先级：最高）

**时间估算**：3-4小时

**产出物**：
- RequirementRefinerE2ETest.java完整实现
- 10个典型失败场景测试
- 改写效果度量报告

**执行方式**：真实AI API调用，验证完整Pipeline

### Phase 3.5.3: 度量分析和优化（优先级：中）

**时间估算**：1-2小时

**产出物**：
- 改写成功率报告
- 信息增益统计
- 策略准确性分析
- 优化建议

---

## ✅ 质量门禁

### 测试通过标准

**P3 Phase 3.5完成条件**：
- [ ] 集成测试覆盖5个以上典型场景
- [ ] 所有集成测试通过率100%
- [ ] E2E测试覆盖4类失败场景
- [ ] 改写成功率≥90%
- [ ] 信息增益≥200%

**阻塞标准**（禁止合并代码）：
- 🚫 改写成功率<90%
- 🚫 集成测试通过率<100%
- 🚫 MEDIUM/COMPLEX需求用户确认率<80%

---

## 📝 测试执行记录

### 已完成

- ✅ P3 Phase 3.1: ComplexityEvaluator架构设计
- ✅ P3 Phase 3.2: ComplexityEvaluator核心逻辑实现
- ✅ P3 Phase 3.3: IntentClassifier集成ComplexityEvaluator
- ✅ P3 Phase 3.4: RequirementRefiner核心逻辑实现
- ✅ P3 Phase 3.5: 测试计划制定

### 待执行

- ⏳ P3 Phase 3.5.1: 集成测试实施
- ⏳ P3 Phase 3.5.2: E2E验证测试
- ⏳ P3 Phase 3.5.3: 度量分析和优化

---

## 🎓 关键经验

### 测试策略调整原因

1. **优先级调整**：从Mock单元测试转向集成和E2E测试
   - **原因**：Mock Spring AI ChatClient API过于复杂
   - **收益**：集成测试能更直接验证实际效果

2. **真实API调用**：使用真实Qwen AI API而非Mock
   - **原因**：验证真实AI模型的改写能力
   - **注意**：需要API Key和网络连接

3. **度量导向**：关注改写成功率、信息增益等量化指标
   - **原因**：证明RequirementRefiner的实际价值
   - **应用**：数据驱动的优化决策

---

## 📚 参考资料

- [RequirementRefiner JavaDoc](../src/main/java/com/ingenio/backend/agent/RequirementRefiner.java)
- [ComplexityEvaluator JavaDoc](../src/main/java/com/ingenio/backend/agent/ComplexityEvaluator.java)
- [IntentClassifier JavaDoc](../src/main/java/com/ingenio/backend/agent/IntentClassifier.java)
- [P3 UltraThink分析文档](/tmp/NEW_3AGENT_ARCHITECTURE_ULTRATHINK.md)

---

**Made with ❤️ by Justin**

> 本测试计划采用实用主义方法，优先集成和E2E测试，确保P3功能的实际效果验证。
