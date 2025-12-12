package com.ingenio.backend.agent;

import com.ingenio.backend.agent.dto.ComplexityScore;
import com.ingenio.backend.agent.dto.RefinedRequirement;
import com.ingenio.backend.agent.dto.RequirementIntent;
import com.ingenio.backend.common.exception.BusinessException;

/**
 * 需求改写器接口 - P3增强功能
 * 根据意图和复杂度评估结果，对原始需求进行边界优化和细化
 * 使其更加明确、可执行，便于后续代码生成
 *
 * <p>核心价值：</p>
 * <ul>
 *   <li>弥合用户自然语言需求与可执行代码生成之间的鸿沟</li>
 *   <li>补充缺失的核心实体和功能定义</li>
 *   <li>拆分复杂需求为MVP + 扩展功能</li>
 *   <li>明确技术约束和实现边界</li>
 *   <li>优化模糊表述为可执行描述</li>
 * </ul>
 *
 * <p>改写策略矩阵：</p>
 * <table border="1">
 *   <tr>
 *     <th>复杂度等级</th>
 *     <th>改写策略</th>
 *     <th>示例</th>
 *   </tr>
 *   <tr>
 *     <td>SIMPLE</td>
 *     <td>补充细节</td>
 *     <td>"做一个待办事项" → "创建待办事项管理应用，包括任务CRUD、优先级标记、完成状态切换"</td>
 *   </tr>
 *   <tr>
 *     <td>MEDIUM</td>
 *     <td>拆分优先级</td>
 *     <td>"做一个电商平台" → "MVP: 商品浏览+购物车+订单; 未来: 支付集成+物流跟踪+优惠券"</td>
 *   </tr>
 *   <tr>
 *     <td>COMPLEX</td>
 *     <td>分期建议</td>
 *     <td>"做一个企业级项目管理" → "第一期: 项目/任务管理; 第二期: 多租户+权限; 第三期: 工作流引擎"</td>
 *   </tr>
 * </table>
 *
 * <p>基于意图的改写策略：</p>
 * <table border="1">
 *   <tr>
 *     <th>意图类型</th>
 *     <th>改写重点</th>
 *     <th>示例</th>
 *   </tr>
 *   <tr>
 *     <td>CLONE_EXISTING_WEBSITE</td>
 *     <td>提取核心功能，忽略复杂特性</td>
 *     <td>"仿照Twitter" → "核心功能：发布动态、关注、时间线、点赞评论；不包含：广告、直播、Spaces"</td>
 *   </tr>
 *   <tr>
 *     <td>DESIGN_FROM_SCRATCH</td>
 *     <td>明确实体关系和业务流程</td>
 *     <td>"设计博客系统" → "实体：用户、文章、分类、标签、评论；流程：创作→发布→浏览→互动"</td>
 *   </tr>
 *   <tr>
 *     <td>HYBRID_CLONE_AND_CUSTOMIZE</td>
 *     <td>区分参考部分和定制部分</td>
 *     <td>"参考Instagram但加电商" → "参考部分：图片社交；定制部分：商品展示+购物车"</td>
 *   </tr>
 * </table>
 *
 * <p>典型失败场景与改写示例：</p>
 * <ul>
 *   <li><strong>过于宽泛</strong>：<br>
 *     原始："做一个社交平台"<br>
 *     问题：缺少核心实体和功能定义<br>
 *     改写后："创建一个基于图片分享的社交平台，核心功能包括：用户注册登录、发布图片动态、关注好友、点赞评论。参考Instagram的核心交互流程。"
 *   </li>
 *   <li><strong>不切实际</strong>：<br>
 *     原始："做一个像微信的即时通讯，支持百万并发"<br>
 *     问题：技术复杂度超出单次生成能力<br>
 *     改写后："创建一个即时通讯MVP应用，支持1对1文字聊天、在线状态显示、消息历史记录。后续可扩展语音通话和群聊功能。"
 *   </li>
 *   <li><strong>缺少边界</strong>：<br>
 *     原始："做一个博客"<br>
 *     问题：没有明确CRUD操作和实体关系<br>
 *     改写后："创建一个个人博客系统，包括文章管理（发布、编辑、删除）、分类标签、评论功能、Markdown编辑器、代码高亮显示。"
 *   </li>
 *   <li><strong>混合意图不清</strong>：<br>
 *     原始："参考Twitter，但要有电商功能"<br>
 *     问题：多个领域混合，优先级不明<br>
 *     改写后："参考部分：Twitter式社交（发布动态、关注、时间线）；定制部分：电商功能（商品展示、购物车、订单管理）。优先实现社交功能，电商作为扩展。"
 *   </li>
 * </ul>
 *
 * <p>实现要求：</p>
 * <ul>
 *   <li>准确性：改写后需求与原始意图一致性≥90%</li>
 *   <li>响应时间：P95 < 3秒（与ComplexityEvaluator相同）</li>
 *   <li>Token消耗：< 2500 tokens/次（包含复杂度信息）</li>
 *   <li>可解释性：提供详细的改写理由和依据</li>
 * </ul>
 *
 * <p>预期产出（RefinedRequirement）：</p>
 * <pre>{@code
 * RefinedRequirement {
 *   refinedText: "创建一个电商平台MVP，核心功能包括：商品浏览、购物车、订单管理。",
 *   coreEntities: ["用户", "商品", "订单", "购物车"],
 *   mvpFeatures: [
 *     "用户注册登录",
 *     "商品列表浏览",
 *     "添加商品到购物车",
 *     "提交订单"
 *   ],
 *   futureFeatures: [
 *     "支付集成（支付宝/微信）",
 *     "物流跟踪",
 *     "优惠券系统",
 *     "用户评价"
 *   ],
 *   technicalConstraints: [
 *     "支付功能需要对接第三方服务，需要额外配置",
 *     "物流跟踪需要对接物流公司API"
 *   ],
 *   entityRelationships: [
 *     "用户-订单: 一对多",
 *     "订单-商品: 多对多（通过订单明细表）",
 *     "用户-购物车: 一对一"
 *   ],
 *   refiningReasoning: "原始需求'做一个电商平台'过于宽泛，已拆分为MVP核心功能和扩展功能。优先实现基础电商流程（浏览→购物车→订单），支付和物流作为第二期功能。",
 *   needsUserConfirmation: true,
 *   refineType: PRIORITY_SPLIT
 * }
 * }</pre>
 *
 * @author Ingenio Team
 * @version 2.0.2
 * @since 2025-11-20 P3 Phase 3.4
 * @see RefinedRequirement
 * @see ComplexityScore
 * @see RequirementIntent
 */
public interface RequirementRefiner {

    /**
     * 改写需求，使其更加明确和可执行
     *
     * <p>改写流程：</p>
     * <ol>
     *   <li>分析原始需求：提取关键信息、识别模糊点</li>
     *   <li>结合意图和复杂度：确定改写策略（补充细节/拆分优先级/分期建议）</li>
     *   <li>AI辅助改写：调用ChatModel生成结构化需求</li>
     *   <li>后处理验证：确保改写结果符合标准，生成警告信息</li>
     * </ol>
     *
     * <p>改写策略选择逻辑：</p>
     * <ul>
     *   <li>SIMPLE复杂度 → 补充细节策略（DETAIL_ENHANCEMENT）</li>
     *   <li>MEDIUM复杂度 → 拆分优先级策略（PRIORITY_SPLIT）</li>
     *   <li>COMPLEX复杂度 → 分期建议策略（PHASED_DEVELOPMENT）</li>
     *   <li>不切实际需求 → 降低复杂度策略（COMPLEXITY_REDUCTION）</li>
     *   <li>HYBRID意图 → 明确边界策略（BOUNDARY_CLARIFICATION）</li>
     * </ul>
     *
     * @param originalRequirement 原始用户需求（自然语言描述）
     * @param intent 意图类型（CLONE/DESIGN/HYBRID）
     * @param complexityScore 复杂度评估结果
     * @return 改写后的结构化需求
     * @throws BusinessException 当改写失败时抛出
     * @throws IllegalArgumentException 当参数为空时抛出
     *
     * <p>使用示例：</p>
     * <pre>{@code
     * @Autowired
     * private RequirementRefiner requirementRefiner;
     *
     * public void processRequirement(String userInput) {
     *     // 步骤1: 意图识别
     *     IntentClassificationResult intentResult = intentClassifier.classify(userInput);
     *
     *     // 步骤2: 复杂度评估（已包含在intentResult中）
     *     ComplexityScore complexityScore = intentResult.getComplexityScore();
     *
     *     // 步骤3: 需求改写
     *     RefinedRequirement refined = requirementRefiner.refine(
     *         userInput,
     *         intentResult.getIntent(),
     *         complexityScore
     *     );
     *
     *     // 步骤4: 决策是否需要用户确认
     *     if (refined.needsUserConfirmation()) {
     *         log.warn("重大改写，需要用户确认：{}", refined.getRefiningReasoning());
     *         // 向前端返回改写结果，等待用户确认
     *         requestUserConfirmation(refined);
     *     } else {
     *         log.info("轻微优化，无需确认：{}", refined.getFormattedReport());
     *         // 直接进入代码生成流程
     *         proceedToCodeGeneration(refined);
     *     }
     * }
     * }</pre>
     */
    RefinedRequirement refine(
            String originalRequirement,
            RequirementIntent intent,
            ComplexityScore complexityScore
    ) throws BusinessException;

    /**
     * 批量改写多个需求
     *
     * <p>用于批量分析和优化场景</p>
     *
     * @param requirements 原始需求列表
     * @param intents 对应的意图列表
     * @param complexityScores 对应的复杂度评估结果列表
     * @return 改写后的需求列表
     * @throws BusinessException 当改写失败时抛出
     * @throws IllegalArgumentException 当参数长度不一致时抛出
     */
    default java.util.List<RefinedRequirement> refineBatch(
            java.util.List<String> requirements,
            java.util.List<RequirementIntent> intents,
            java.util.List<ComplexityScore> complexityScores
    ) throws BusinessException {
        if (requirements == null || intents == null || complexityScores == null) {
            throw new IllegalArgumentException("参数不能为空");
        }

        if (requirements.size() != intents.size() || requirements.size() != complexityScores.size()) {
            throw new IllegalArgumentException("参数列表长度必须一致");
        }

        java.util.List<RefinedRequirement> results = new java.util.ArrayList<>();
        for (int i = 0; i < requirements.size(); i++) {
            results.add(refine(requirements.get(i), intents.get(i), complexityScores.get(i)));
        }

        return results;
    }

    /**
     * 快速验证改写是否必要
     *
     * <p>判断标准：</p>
     * <ul>
     *   <li>需求长度 < 20字 → 可能过于简短，需要改写</li>
     *   <li>复杂度 = COMPLEX → 需要拆分，必须改写</li>
     *   <li>包含模糊词汇（"类似"、"差不多"、"大概"）→ 需要明确，建议改写</li>
     *   <li>缺少核心实体关键词 → 需要补充，建议改写</li>
     * </ul>
     *
     * @param originalRequirement 原始需求
     * @param complexityScore 复杂度评估结果
     * @return true 如果需要改写
     */
    default boolean needsRefine(String originalRequirement, ComplexityScore complexityScore) {
        if (originalRequirement == null || originalRequirement.trim().isEmpty()) {
            return false;
        }

        // 规则1: 需求过于简短
        if (originalRequirement.length() < 20) {
            return true;
        }

        // 规则2: 复杂度为COMPLEX，必须拆分
        if (complexityScore != null
                && complexityScore.getLevel() == com.ingenio.backend.agent.dto.ComplexityLevel.COMPLEX) {
            return true;
        }

        // 规则3: 包含模糊词汇
        String[] vagueWords = {"类似", "差不多", "大概", "之类的", "等等", "做一个"};
        for (String word : vagueWords) {
            if (originalRequirement.contains(word)) {
                return true;
            }
        }

        // 规则4: 缺少核心实体关键词（简单启发式检查）
        if (complexityScore != null && complexityScore.getExtractedEntities() != null
                && complexityScore.getExtractedEntities().isEmpty()) {
            return true;
        }

        return false;
    }
}
