package com.ingenio.backend.codegen.ai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ingenio.backend.codegen.ai.model.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * AI需求分析器（V2.0 Phase 4.1）
 *
 * <p>核心功能：</p>
 * <ul>
 *   <li>自然语言需求 → 结构化需求文档</li>
 *   <li>提取实体、字段、业务规则、关系、约束</li>
 *   <li>使用Qwen-Max模型进行AI分析</li>
 *   <li>Few-shot Learning + Chain of Thought推理</li>
 * </ul>
 *
 * <p>使用示例：</p>
 * <pre>{@code
 * String userInput = "我要做一个用户管理系统，包括用户注册、登录、密码重置功能。"
 *                   + "用户有用户名、邮箱、密码、年龄等字段。"
 *                   + "用户名和邮箱必须唯一，年龄必须大于等于18岁。";
 *
 * AnalyzedRequirement requirement = requirementAnalyzer.analyze(userInput);
 *
 * System.out.println("领域: " + requirement.getDomain());
 * System.out.println("实体数量: " + requirement.getEntities().size());
 * System.out.println("业务规则数量: " + requirement.getBusinessRules().size());
 * System.out.println("AI置信度: " + requirement.getConfidence());
 * }</pre>
 *
 * <p>质量标准：</p>
 * <ul>
 *   <li>准确率 ≥ 90%：AI分析的结构化需求准确率</li>
 *   <li>置信度 ≥ 0.85：高置信度需求可直接使用</li>
 *   <li>响应时间 < 5s：包含AI调用的总响应时间</li>
 * </ul>
 *
 * @author Ingenio Code Generator
 * @since 2025-11-17 V2.0 Phase 4.1: AI需求理解服务
 */
@Service
@Slf4j
public class RequirementAnalyzer {

    private final ChatModel chatModel;
    private final ObjectMapper objectMapper;

    public RequirementAnalyzer(ChatModel chatModel, ObjectMapper objectMapper) {
        this.chatModel = chatModel;
        this.objectMapper = objectMapper;
    }

    /**
     * 分析用户需求，生成结构化需求文档
     *
     * @param userRequirement 用户的自然语言需求
     * @return 结构化需求文档
     */
    public AnalyzedRequirement analyze(String userRequirement) {
        log.info("开始分析需求: userRequirement={}", userRequirement);
        long startTime = System.currentTimeMillis();

        try {
            // Step 1: 构建AI Prompt（Few-shot Learning）
            String prompt = buildAnalysisPrompt(userRequirement);
            log.debug("AI Prompt构建完成，长度: {}", prompt.length());

            // Step 2: 构建ChatClient并调用AI模型（Qwen-Max）
            ChatClient chatClient = ChatClient.builder(chatModel).build();
            String aiResponse = chatClient.prompt()
                    .user(prompt)
                    .call()
                    .content();
            log.debug("AI返回结果: {}", aiResponse);

            // Step 3: 解析AI返回的JSON
            AnalyzedRequirement requirement = parseAIResponse(aiResponse, userRequirement);
            log.info("需求分析完成，领域: {}, 实体数: {}, 业务规则数: {}, 置信度: {}, 耗时: {}ms",
                    requirement.getDomain(),
                    requirement.getEntities() != null ? requirement.getEntities().size() : 0,
                    requirement.getBusinessRules() != null ? requirement.getBusinessRules().size() : 0,
                    requirement.getConfidence(),
                    System.currentTimeMillis() - startTime);

            return requirement;

        } catch (Exception e) {
            log.error("需求分析失败: userRequirement={}", userRequirement, e);

            // 降级策略：返回基础的需求结构
            return AnalyzedRequirement.builder()
                    .domain("未知领域")
                    .description(userRequirement)
                    .source("用户输入")
                    .confidence(0.0)
                    .reasoning("AI分析失败，使用降级策略: " + e.getMessage())
                    .entities(new ArrayList<>())
                    .businessRules(new ArrayList<>())
                    .relationships(new ArrayList<>())
                    .constraints(new ArrayList<>())
                    .build();
        }
    }

    /**
     * 构建AI分析Prompt（Few-shot Learning + Chain of Thought）
     *
     * @param userRequirement 用户需求
     * @return AI Prompt
     */
    private String buildAnalysisPrompt(String userRequirement) {
        return """
你是一个专业的软件需求分析师和架构师。请分析以下用户需求，提取结构化信息。

# 用户需求
%s

# 分析任务
请按照以下步骤分析需求，并返回JSON格式的结构化需求：

## Step 1: 识别业务领域
- 分析需求属于哪个业务领域（如：用户管理、订单管理、商品管理）
- 提取核心业务概念

## Step 2: 提取实体（Entities）
- 识别核心业务实体（如：User、Order、Product）
- 为每个实体提取字段信息：
  - 字段名称（camelCase）
  - 字段类型（String、Integer、Long、UUID、Boolean、LocalDateTime）
  - 字段描述
  - 是否必填（required）
  - 是否唯一（unique）
  - 默认值（defaultValue）
  - 长度限制（minLength、maxLength）
  - 正则表达式（pattern）
- 识别需要的业务方法（如：register、login、createOrder）

## Step 3: 提取业务规则（BusinessRules）⚠️ 🚨 **超级重要：必须完整提取所有业务规则！**

### 🎯 关键识别模板（必须严格遵守）

**1️⃣ VALIDATION规则** - 关键词：`必须`、`不能`、`只能`、`校验`、`验证`、`格式`、`范围`、`长度`、`正则`
   - **识别模板**：
     - "X必须Y" → VALIDATION规则（如"密码必须8位以上"）
     - "X不能为空" → VALIDATION规则
     - "X必须符合格式" → VALIDATION规则
     - "X范围A-B" → VALIDATION规则（如"年龄18-25岁"）
   - **隐含规则**：邮箱格式、手机号11位、ID非空、外键有效性

**2️⃣ CALCULATION规则** - 关键词：`计算`、`=`、`*`、`+`、`总额`、`小计`、`自动`、`根据...计算`
   - **识别模板**：
     - "X = Y ± Z" → CALCULATION规则（如"总额=原价-折扣"）
     - "每消费N元获得M积分" → CALCULATION规则
     - "达到X自动升级/降级" → CALCULATION规则
     - "根据X计算Y" → CALCULATION规则
   - **示例映射**：
     - "每消费1元获得1积分" → {"name": "calculatePointsPerYuan", "type": "CALCULATION", "logic": "消费金额 * 1"}
     - "生日当月双倍积分" → {"name": "calculateBirthdayBonus", "type": "CALCULATION", "logic": "if(isBirthdayMonth) points *= 2"}
     - "达到1000积分升级银卡" → {"name": "autoUpgradeToSilver", "type": "CALCULATION", "logic": "if(points >= 1000) level = SILVER"}
     - "折扣后价格" → {"name": "calculateDiscountedPrice", "type": "CALCULATION", "logic": "原价 * 折扣率"}

**3️⃣ WORKFLOW规则** - 关键词：`→`、`状态`、`流程`、`审批`、`步骤`、`进入下一步`、`流转`
   - **识别模板**：
     - "状态A→状态B→状态C" → 至少3个WORKFLOW规则（每个转换一个）
     - "如果X则进入Y步骤" → WORKFLOW规则
     - "批准/拒绝" → 2个WORKFLOW规则（批准分支+拒绝分支）
   - **示例映射**：
     - "待支付→已支付→已发货" → 提取2个规则：
       1. {"name": "paymentWorkflow", "type": "WORKFLOW", "logic": "待支付 → 已支付"}
       2. {"name": "shippingWorkflow", "type": "WORKFLOW", "logic": "已支付 → 已发货"}
     - "直属领导审批：批准→下一步，拒绝→结束" → 提取2个规则：
       1. {"name": "supervisorApprove", "type": "WORKFLOW", "logic": "待审批 → 部门经理审批（批准）"}
       2. {"name": "supervisorReject", "type": "WORKFLOW", "logic": "待审批 → 已拒绝（拒绝）"}
     - "天数>3需经理审批" → {"name": "managerApprovalRequired", "type": "WORKFLOW", "logic": "if(days > 3) 需要部门经理审批"}

**4️⃣ NOTIFICATION规则** - 关键词：`通知`、`发送`、`邮件`、`短信`、`提醒`、`推送`
   - **识别模板**：
     - "X时发送Y" → NOTIFICATION规则
     - "通知Z" → NOTIFICATION规则
   - **隐含规则**：注册成功邮件、密码重置邮件、订单确认通知

**5️⃣ CONSTRAINT规则** - 关键词：`唯一`、`不能为负`、`>0`、`外键`、`默认值`
   - **识别模板**：
     - "X唯一" → CONSTRAINT规则（type: UNIQUE）
     - "X不能为负/X≥0" → CONSTRAINT规则（type: CHECK）
     - "关联Y表" → CONSTRAINT规则（type: FOREIGN_KEY）

---

### 📋 强制提取要求：
✅ **每个包含数学运算符的描述都是CALCULATION规则**（+、-、*、/、=、%）
✅ **每个状态转换箭头（→）至少生成1个WORKFLOW规则**
✅ **每个if条件判断都可能是WORKFLOW或CALCULATION规则**
✅ **每个"达到X自动Y"都是CALCULATION规则**
✅ **每个审批步骤至少生成2个WORKFLOW规则**（批准+拒绝）

---

### ⚠️ 常见错误：
❌ **错误1**：只提取显式规则，遗漏隐含规则
❌ **错误2**：将多个状态转换合并为1个WORKFLOW规则（应该拆分）
❌ **错误3**：遗漏计算逻辑中的中间步骤
❌ **错误4**：忽略条件分支（if-then-else至少2个规则）

## Step 4: 识别实体关系（Relationships）
- ONE_TO_ONE：一对一关系（用户-用户详情）
- ONE_TO_MANY：一对多关系（用户-订单）
- MANY_TO_MANY：多对多关系（用户-角色）

## Step 5: 识别约束条件（Constraints）
- UNIQUE：唯一性约束（邮箱、用户名）
- NOT_NULL：非空约束（必填字段）
- CHECK：检查约束（年龄范围、枚举值）
- FOREIGN_KEY：外键约束（关联表）
- PRIMARY_KEY：主键约束
- DEFAULT：默认值约束

## Step 6: 评估置信度
- 根据需求的清晰度和完整性，给出AI分析的置信度（0-1）
- 0.9以上表示高置信度，可直接使用
- 0.7-0.9表示中等置信度，建议人工确认
- 低于0.7需要人工补充需求

# Few-shot Learning示例

## 示例1: 用户管理系统
**用户输入**: "我要做一个用户管理系统，包括用户注册、登录、密码重置功能。用户有用户名、邮箱、密码、年龄等字段。用户名和邮箱必须唯一，年龄必须大于等于18岁。"

**AI输出**:
```json
{
  "domain": "用户管理",
  "description": "管理系统用户的注册、登录、权限等功能",
  "entities": [
    {
      "name": "User",
      "description": "系统用户实体",
      "tableName": "users",
      "fields": [
        {
          "name": "id",
          "type": "UUID",
          "description": "用户ID",
          "required": true,
          "unique": true
        },
        {
          "name": "username",
          "type": "String",
          "description": "用户名",
          "required": true,
          "unique": true,
          "minLength": 3,
          "maxLength": 50
        },
        {
          "name": "email",
          "type": "String",
          "description": "邮箱",
          "required": true,
          "unique": true,
          "pattern": "^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\\\.[a-zA-Z]{2,}$"
        },
        {
          "name": "password",
          "type": "String",
          "description": "加密后的密码",
          "required": true,
          "minLength": 8
        },
        {
          "name": "age",
          "type": "Integer",
          "description": "年龄",
          "required": false
        }
      ],
      "businessMethods": ["register", "login", "resetPassword", "updateProfile"],
      "softDelete": true,
      "auditFields": true,
      "pagination": true,
      "priority": "HIGH"
    }
  ],
  "businessRules": [
    {
      "name": "validateAge",
      "description": "验证用户年龄必须≥18岁",
      "type": "VALIDATION",
      "entity": "User",
      "method": "register",
      "logic": "检查age字段，如果小于18则抛出BusinessException",
      "priority": 10
    },
    {
      "name": "validateEmailFormat",
      "description": "验证邮箱格式是否合法",
      "type": "VALIDATION",
      "entity": "User",
      "method": "register",
      "logic": "检查邮箱是否匹配正则表达式",
      "priority": 9
    },
    {
      "name": "sendWelcomeEmail",
      "description": "注册成功后发送欢迎邮件",
      "type": "NOTIFICATION",
      "entity": "User",
      "method": "register",
      "logic": "注册成功后异步发送欢迎邮件",
      "priority": 5
    }
  ],
  "relationships": [],
  "constraints": [
    {
      "name": "uk_user_username",
      "type": "UNIQUE",
      "entity": "User",
      "field": "username",
      "description": "用户名必须唯一",
      "errorMessage": "用户名已被注册",
      "enforcedAtDatabase": true
    },
    {
      "name": "uk_user_email",
      "type": "UNIQUE",
      "entity": "User",
      "field": "email",
      "description": "邮箱必须唯一",
      "errorMessage": "邮箱已被注册",
      "enforcedAtDatabase": true
    },
    {
      "name": "ck_user_age",
      "type": "CHECK",
      "entity": "User",
      "field": "age",
      "expression": "age >= 18",
      "description": "年龄必须大于等于18岁",
      "errorMessage": "用户年龄必须≥18岁",
      "enforcedAtDatabase": true
    }
  ],
  "confidence": 0.95,
  "reasoning": "需求清晰完整，明确指定了实体、字段、业务规则和约束条件"
}
```

## 示例2: 订单管理系统
**用户输入**: "我需要一个订单系统，用户可以创建订单、查看订单、取消订单。订单包含订单号、用户ID、订单状态（待支付、已支付、已发货、已完成、已取消）、订单金额、创建时间等字段。订单状态的流转规则是：待支付→已支付→已发货→已完成，任何状态都可以取消。"

**AI输出**:
```json
{
  "domain": "订单管理",
  "description": "管理用户订单的创建、状态流转、取消等功能",
  "entities": [
    {
      "name": "Order",
      "description": "订单实体",
      "tableName": "orders",
      "fields": [
        {
          "name": "id",
          "type": "UUID",
          "description": "订单ID",
          "required": true,
          "unique": true
        },
        {
          "name": "orderNo",
          "type": "String",
          "description": "订单号",
          "required": true,
          "unique": true
        },
        {
          "name": "userId",
          "type": "UUID",
          "description": "用户ID",
          "required": true
        },
        {
          "name": "status",
          "type": "String",
          "description": "订单状态",
          "required": true,
          "defaultValue": "PENDING"
        },
        {
          "name": "amount",
          "type": "Long",
          "description": "订单金额（分）",
          "required": true
        },
        {
          "name": "createdAt",
          "type": "LocalDateTime",
          "description": "创建时间",
          "required": true
        }
      ],
      "businessMethods": ["createOrder", "viewOrder", "cancelOrder", "updateStatus"],
      "softDelete": false,
      "auditFields": true,
      "pagination": true,
      "priority": "HIGH"
    }
  ],
  "businessRules": [
    {
      "name": "orderStatusFlow",
      "description": "订单状态流转规则",
      "type": "WORKFLOW",
      "entity": "Order",
      "method": "updateStatus",
      "logic": "待支付→已支付→已发货→已完成，任何状态都可以取消",
      "priority": 10
    },
    {
      "name": "calculateOrderAmount",
      "description": "计算订单总金额",
      "type": "CALCULATION",
      "entity": "Order",
      "method": "createOrder",
      "logic": "根据订单项计算总金额",
      "priority": 8
    },
    {
      "name": "sendOrderNotification",
      "description": "订单状态变更通知",
      "type": "NOTIFICATION",
      "entity": "Order",
      "method": "updateStatus",
      "logic": "状态变更时发送通知给用户",
      "priority": 5
    }
  ],
  "relationships": [
    {
      "sourceEntity": "User",
      "targetEntity": "Order",
      "type": "ONE_TO_MANY",
      "sourceField": "id",
      "targetField": "userId",
      "description": "一个用户可以有多个订单",
      "nullable": false,
      "cascadeDelete": false,
      "fetchType": "LAZY"
    }
  ],
  "constraints": [
    {
      "name": "uk_order_no",
      "type": "UNIQUE",
      "entity": "Order",
      "field": "orderNo",
      "description": "订单号必须唯一",
      "errorMessage": "订单号已存在",
      "enforcedAtDatabase": true
    },
    {
      "name": "fk_order_user",
      "type": "FOREIGN_KEY",
      "entity": "Order",
      "field": "userId",
      "referencedTable": "users",
      "referencedField": "id",
      "onDelete": "RESTRICT",
      "onUpdate": "CASCADE",
      "description": "订单必须关联有效的用户",
      "errorMessage": "用户不存在",
      "enforcedAtDatabase": true
    },
    {
      "name": "ck_order_status",
      "type": "CHECK",
      "entity": "Order",
      "field": "status",
      "expression": "status IN ('PENDING', 'PAID', 'SHIPPED', 'COMPLETED', 'CANCELLED')",
      "description": "订单状态必须是有效值",
      "errorMessage": "无效的订单状态",
      "enforcedAtDatabase": true
    }
  ],
  "confidence": 0.92,
  "reasoning": "需求完整，明确定义了订单实体、状态流转规则和关联关系"
}
```

# 输出格式要求
请严格按照上述JSON格式输出，确保：
1. 所有字段类型正确（String、Integer、Long、UUID、Boolean、LocalDateTime）
2. 枚举值准确（EntityPriority、BusinessRuleType、RelationshipType、ConstraintType）
3. 置信度在0-1之间
4. reasoning字段详细说明分析过程和依据
5. 返回纯JSON，不要包含任何markdown标记（如```json）

现在请分析上述用户需求并返回JSON。
""".replace("%s", userRequirement);
    }

    /**
     * 解析AI返回的JSON响应
     *
     * @param aiResponse AI返回的原始响应
     * @param userRequirement 原始用户需求
     * @return 结构化需求对象
     */
    private AnalyzedRequirement parseAIResponse(String aiResponse, String userRequirement) {
        try {
            // 清理AI返回的响应（移除可能的markdown标记）
            String cleanedResponse = cleanAIResponse(aiResponse);

            // 解析JSON
            AnalyzedRequirement requirement = objectMapper.readValue(cleanedResponse, AnalyzedRequirement.class);

            // 设置需求来源
            if (requirement.getSource() == null) {
                requirement.setSource("用户输入");
            }

            // 确保列表不为null
            if (requirement.getEntities() == null) {
                requirement.setEntities(new ArrayList<>());
            }
            if (requirement.getBusinessRules() == null) {
                requirement.setBusinessRules(new ArrayList<>());
            }
            if (requirement.getRelationships() == null) {
                requirement.setRelationships(new ArrayList<>());
            }
            if (requirement.getConstraints() == null) {
                requirement.setConstraints(new ArrayList<>());
            }

            return requirement;

        } catch (Exception e) {
            log.error("解析AI响应失败: aiResponse={}", aiResponse, e);
            throw new RuntimeException("AI响应解析失败: " + e.getMessage(), e);
        }
    }

    /**
     * 清理AI返回的响应（移除markdown标记等）
     *
     * @param aiResponse AI返回的原始响应
     * @return 清理后的JSON字符串
     */
    private String cleanAIResponse(String aiResponse) {
        // 移除markdown代码块标记
        String cleaned = aiResponse.trim();

        if (cleaned.startsWith("```json")) {
            cleaned = cleaned.substring(7);
        } else if (cleaned.startsWith("```")) {
            cleaned = cleaned.substring(3);
        }

        if (cleaned.endsWith("```")) {
            cleaned = cleaned.substring(0, cleaned.length() - 3);
        }

        return cleaned.trim();
    }
}
