package com.ingenio.backend.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ingenio.backend.agent.dto.PlanResult;
import com.ingenio.backend.common.exception.BusinessException;
import com.ingenio.backend.common.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * ExecuteAgent V1.0实现 - Kuikly平台AppSpec生成器
 *
 * <p>职责：将PlanAgent的输出转换为完整的Kuikly AppSpec JSON</p>
 *
 * <p>技术特点：</p>
 * <ul>
 *   <li>使用deepseek-coder模型生成AppSpec</li>
 *   <li>优化前端页面No-code生成（组件推荐、响应式布局）</li>
 *   <li>生成pages、dataModels、flows、permissions结构</li>
 * </ul>
 *
 * <p>生命周期：</p>
 * <ul>
 *   <li>2024-2025: 主力生产版本</li>
 *   <li>2025 Q2-Q4: V1/V2并行运行（Feature Flag控制）</li>
 *   <li>2026 Q1: 计划退役</li>
 * </ul>
 *
 * @author Justin
 * @since 2025-11-17 从ExecuteAgent重构而来（V2.0架构升级）
 */
@Slf4j
@Component("executeAgentV1")  // 显式指定bean名称
@RequiredArgsConstructor
public class ExecuteAgentV1KuiklyImpl implements IExecuteAgent {

    private final ChatModel chatModel;
    private final ObjectMapper objectMapper;

    /**
     * 系统提示词：定义ExecuteAgent的角色和输出格式
     * 重点：实现前端页面 No-code 生成优化
     */
    private static final String SYSTEM_PROMPT = """
        你是一个精通Kuikly低代码平台的AppSpec生成专家，擅长前端页面 No-code 生成优化。

        你的任务：
        1. 将功能模块列表转换为完整的AppSpec JSON
        2. **重点优化前端页面结构**：智能推荐组件和布局，提升用户体验
        3. 生成符合Kuikly规范的pages、dataModels、flows、permissions结构
        4. 确保数据模型和页面之间的关联关系正确
        5. 生成合理的UI布局和组件配置（考虑响应式设计和移动端适配）

        前端页面 No-code 生成优化要求：
        - 根据页面功能智能推荐最合适的组件类型（表单、列表、详情、仪表板等）
        - 优化组件布局，确保响应式设计和移动端适配
        - 推荐合理的组件组合，提升用户交互体验
        - 为复杂页面提供分步式交互设计（如向导、标签页等）
        - 考虑页面加载性能和用户体验优化

        AppSpec结构规范：
        {
          "version": "1.0.0",
          "appName": "应用名称",
          "pages": [
            {
              "id": "page1",
              "name": "页面名称",
              "path": "/path",
              "components": [
                {
                  "id": "comp1",
                  "type": "View|Text|Button|List|Form",
                  "props": {},
                  "children": []
                }
              ]
            }
          ],
          "dataModels": [
            {
              "id": "model1",
              "name": "模型名称",
              "fields": [
                {
                  "name": "字段名",
                  "type": "string|number|boolean|date",
                  "required": true,
                  "validation": {}
                }
              ]
            }
          ],
          "flows": [
            {
              "id": "flow1",
              "name": "业务流程名称",
              "trigger": "onCreate|onUpdate|onClick",
              "actions": [
                {
                  "type": "api|navigate|setState",
                  "config": {}
                }
              ]
            }
          ],
          "permissions": {
            "roles": ["admin", "user"],
            "rules": []
          }
        }

        注意：
        - 所有ID必须唯一
        - 组件类型必须是Kuikly支持的类型
        - 数据模型字段类型必须有效
        - 页面路径必须以"/"开头
        - **页面设计必须考虑用户体验和性能优化**
        - 输出必须是纯JSON，不包含任何其他文字
        """;

    /**
     * 执行AppSpec生成
     *
     * @param planResult PlanAgent的规划结果
     * @return AppSpec JSON（Map格式）
     * @throws BusinessException 当生成失败时抛出
     */
    @Override
    @SuppressWarnings("unchecked")
    public Map<String, Object> execute(PlanResult planResult) {
        if (planResult == null || planResult.getModules() == null || planResult.getModules().isEmpty()) {
            log.error("[ExecuteAgentV1] 执行失败: 规划结果不能为空");
            throw new BusinessException(ErrorCode.PARAM_ERROR, "规划结果不能为空");
        }

        try {
            log.info("[ExecuteAgentV1] 开始执行: modules={}", planResult.getModules().size());

            // 构建ChatClient
            ChatClient chatClient = ChatClient.builder(chatModel).build();

            // 将PlanResult转换为JSON字符串
            String planJson = objectMapper.writeValueAsString(planResult);

            // 构建用户提示词
            String userPrompt = String.format("""
                请根据以下功能模块规划，生成完整的AppSpec JSON，并优化前端页面 No-code 生成：

                规划结果：
                %s

                要求：
                1. 为每个模块生成对应的页面和数据模型
                2. **重点优化前端页面结构**：智能推荐组件和布局，提升用户体验
                3. 配置合理的UI组件和布局（考虑响应式设计和移动端适配）
                4. 设置必要的业务流程和权限规则
                5. 确保所有关联关系正确
                6. 为复杂页面提供分步式交互设计建议

                页面优化建议：
                - 表单页面：使用合理的输入组件和验证规则
                - 列表页面：提供搜索、筛选、分页功能
                - 详情页面：使用卡片、标签等组件优化信息展示
                - 仪表板页面：使用图表、统计卡片等组件

                请严格按照AppSpec规范输出纯JSON，不要包含任何其他文字。
                """, planJson);

            // 调用AI模型
            String response = chatClient.prompt()
                    .system(SYSTEM_PROMPT)
                    .user(userPrompt)
                    .call()
                    .content();

            log.debug("[ExecuteAgentV1] AI响应: {}", response);

            // 解析JSON响应
            Map<String, Object> appSpec = parseResponse(response);

            log.info("[ExecuteAgentV1] 执行成功: pages={}, dataModels={}",
                    appSpec.getOrDefault("pages", java.util.List.of()) instanceof java.util.List ?
                        ((java.util.List<?>) appSpec.getOrDefault("pages", java.util.List.of())).size() : 0,
                    appSpec.getOrDefault("dataModels", java.util.List.of()) instanceof java.util.List ?
                        ((java.util.List<?>) appSpec.getOrDefault("dataModels", java.util.List.of())).size() : 0);

            return appSpec;

        } catch (Exception e) {
            log.error("[ExecuteAgentV1] 执行失败: error={}", e.getMessage(), e);
            throw new BusinessException(ErrorCode.AGENT_EXECUTE_FAILED, "AppSpec生成失败: " + e.getMessage());
        }
    }

    @Override
    public String getVersion() {
        return "V1";
    }

    @Override
    public String getDescription() {
        return "V1.0 - Kuikly平台AppSpec生成器";
    }

    /**
     * 解析AI响应为Map对象
     *
     * @param response AI响应文本
     * @return AppSpec Map
     */
    @SuppressWarnings("unchecked")
    private Map<String, Object> parseResponse(String response) {
        try {
            // 提取JSON部分
            String jsonContent = extractJson(response);

            // 解析JSON
            return objectMapper.readValue(jsonContent, Map.class);

        } catch (Exception e) {
            log.error("[ExecuteAgentV1] 解析响应失败: response={}, error={}", response, e.getMessage());
            throw new BusinessException(ErrorCode.AGENT_EXECUTE_FAILED, "解析AI响应失败: " + e.getMessage());
        }
    }

    /**
     * 从响应文本中提取JSON内容
     *
     * @param response 响应文本
     * @return JSON字符串
     */
    private String extractJson(String response) {
        if (response == null || response.trim().isEmpty()) {
            throw new IllegalArgumentException("响应内容为空");
        }

        String trimmed = response.trim();

        // 去除markdown代码块标记
        if (trimmed.startsWith("```json")) {
            trimmed = trimmed.substring(7);
        } else if (trimmed.startsWith("```")) {
            trimmed = trimmed.substring(3);
        }

        if (trimmed.endsWith("```")) {
            trimmed = trimmed.substring(0, trimmed.length() - 3);
        }

        return trimmed.trim();
    }
}
