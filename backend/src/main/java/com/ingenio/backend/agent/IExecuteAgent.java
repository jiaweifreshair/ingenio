package com.ingenio.backend.agent;

import com.ingenio.backend.agent.dto.PlanResult;

import java.util.Map;

/**
 * ExecuteAgent接口定义
 *
 * <p>职责：根据Plan阶段的设计方案，生成可部署的应用代码</p>
 *
 * <p>版本演进：</p>
 * <ul>
 *   <li>V1.0: 生成Kuikly平台AppSpec JSON配置</li>
 *   <li>V2.0: 生成完整全栈代码（PostgreSQL Schema + Spring Boot后端 + 多端前端）</li>
 * </ul>
 *
 * <p>调用时机：必须在Plan Agent完成且用户确认设计后调用</p>
 *
 * @author Justin
 * @since 2025-11-17 V2.0架构升级
 */
public interface IExecuteAgent {

    /**
     * 执行代码生成
     *
     * @param planResult Plan阶段的输出结果（包含设计方案、原型代码、需求分析）
     * @return 生成的代码结果
     *
     * <p>V1.0返回格式：</p>
     * <pre>{@code
     * {
     *   "appSpec": {...},  // Kuikly平台配置JSON
     *   "version": "V1",
     *   "platform": "Kuikly"
     * }
     * }</pre>
     *
     * <p>V2.0返回格式：</p>
     * <pre>{@code
     * {
     *   "database": {
     *     "entities": [...],
     *     "liquibaseChangelog": "..."
     *   },
     *   "backend": {
     *     "springBootCode": {...},
     *     "projectStructure": {...}
     *   },
     *   "frontend": {
     *     "web": {...},
     *     "android": {...},
     *     "ios": {...},
     *     "wechat": {...}
     *   },
     *   "version": "V2",
     *   "platforms": ["Web", "Android", "iOS", "WeChat"]
     * }
     * }</pre>
     */
    Map<String, Object> execute(PlanResult planResult);

    /**
     * 获取Agent版本标识
     *
     * @return "V1" 或 "V2"
     */
    String getVersion();

    /**
     * 获取Agent描述信息
     *
     * @return 版本描述（如"V1.0 - Kuikly平台生成器" 或 "V2.0 - 全栈多端生成器"）
     */
    String getDescription();
}
