package com.ingenio.backend.agent;

import java.util.Map;

/**
 * ValidateAgent接口定义
 *
 * <p>职责：验证Execute阶段生成的代码质量、编译通过性、运行时正确性</p>
 *
 * <p>版本演进：</p>
 * <ul>
 *   <li>V1.0: 验证Kuikly AppSpec JSON的结构完整性和语义正确性</li>
 *   <li>V2.0: 三层验证策略（Local 2min + E2B 5min + GitHub Actions 15min），支持多平台编译验证</li>
 * </ul>
 *
 * <p>调用时机：必须在Execute Agent完成代码生成后立即调用</p>
 *
 * @author Justin
 * @since 2025-11-17 V2.0架构升级
 */
public interface IValidateAgent {

    /**
     * 验证生成的代码
     *
     * @param executeResult Execute阶段的输出结果（生成的代码）
     * @return 验证结果
     *
     * <p>V1.0返回格式：</p>
     * <pre>{@code
     * {
     *   "success": true,
     *   "score": 92.5,
     *   "validations": {
     *     "ruleValidation": {...},  // 规则验证结果（60%权重）
     *     "aiValidation": {...}     // AI语义验证（40%权重）
     *   },
     *   "version": "V1"
     * }
     * }</pre>
     *
     * <p>V2.0返回格式：</p>
     * <pre>{@code
     * {
     *   "success": true,
     *   "tier1": {  // Local验证（2分钟内，同步）
     *     "typescript": {...},
     *     "eslint": {...},
     *     "maven": {...},
     *     "allPassed": true
     *   },
     *   "tier2": {  // E2B云端编译（5分钟内，同步）
     *     "webBuild": {...},
     *     "backendBuild": {...},
     *     "allPassed": true
     *   },
     *   "tier3": {  // GitHub Actions多平台（15分钟内，异步）
     *     "asyncJobId": "gh-actions-run-12345",
     *     "status": "PENDING",
     *     "platforms": ["Android", "iOS", "WeChat"]
     *   },
     *   "version": "V2"
     * }
     * }</pre>
     */
    Map<String, Object> validate(Map<String, Object> executeResult);

    /**
     * 获取Agent版本标识
     *
     * @return "V1" 或 "V2"
     */
    String getVersion();

    /**
     * 获取Agent描述信息
     *
     * @return 版本描述（如"V1.0 - AppSpec验证器" 或 "V2.0 - 多平台三层验证器"）
     */
    String getDescription();

    /**
     * 查询异步验证任务状态（V2.0专用）
     *
     * <p>用于查询GitHub Actions异步任务的执行进度和结果</p>
     *
     * @param asyncJobId 异步任务ID
     * @return 任务状态和结果
     *
     * <pre>{@code
     * {
     *   "asyncJobId": "gh-actions-run-12345",
     *   "status": "COMPLETED|RUNNING|FAILED",
     *   "progress": 85,
     *   "results": {
     *     "android": {...},
     *     "ios": {...},
     *     "wechat": {...}
     *   }
     * }
     * }</pre>
     */
    default Map<String, Object> queryAsyncValidation(String asyncJobId) {
        throw new UnsupportedOperationException("This method is only supported in V2.0");
    }
}
