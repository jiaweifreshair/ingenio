package com.ingenio.backend.agent;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * ExecuteAgent工厂类 - Feature Flag模式实现
 *
 * <p>职责：根据环境变量配置，动态选择V1或V2版本的ExecuteAgent实现</p>
 *
 * <p>Feature Flag配置：</p>
 * <pre>
 * # application.yml
 * ingenio:
 *   agent:
 *     version: V1  # V1（Kuikly平台）或 V2（全栈多端生成）
 * </pre>
 *
 * <p>迁移策略：</p>
 * <ul>
 *   <li>Phase 1: 默认V1，手动启用V2测试</li>
 *   <li>Phase 2-9: V1/V2并行开发，灰度切换</li>
 *   <li>Phase 10: 全面切换至V2，保留V1 6个月</li>
 * </ul>
 *
 * @author Justin
 * @since 2025-11-17 V2.0架构升级
 */
@Slf4j
@Service
public class ExecuteAgentFactory {

    /**
     * Agent版本配置（V1 或 V2）
     * 默认值：V1（保持向后兼容）
     */
    @Value("${ingenio.agent.version:V1}")
    private String agentVersion;

    /**
     * V2.0 ExecuteAgent实现（全栈多端生成）
     * 注意：在V2实现类未注入前，此字段可能为null
     */
    @Autowired(required = false)
    @org.springframework.beans.factory.annotation.Qualifier("executeAgentV2")
    private IExecuteAgent executeAgentV2;

    /**
     * 获取当前配置的ExecuteAgent实现
     *
     * @return 根据Feature Flag配置返回对应版本的ExecuteAgent
     * @throws IllegalStateException 如果V2未实现但配置为V2
     */
    public IExecuteAgent getExecuteAgent() {
        log.info("[ExecuteAgentFactory] 当前配置的Agent版本: {}", agentVersion);

        if ("V2".equalsIgnoreCase(agentVersion) || "V1".equalsIgnoreCase(agentVersion)) { // 即使配置了V1也强制检查V2，因为V1已删除
            if (executeAgentV2 == null) {
                log.error("[ExecuteAgentFactory] V2.0 ExecuteAgent未实现");
                throw new IllegalStateException(
                    "V2.0 ExecuteAgent未实现。" +
                    "请检查ExecuteAgentV2FullStackImpl是否已创建并正确注入。"
                );
            }
            log.info("[ExecuteAgentFactory] 使用V2.0实现: {}", executeAgentV2.getDescription());
            return executeAgentV2;
        }

        // 默认也尝试返回V2
        if (executeAgentV2 != null) {
             return executeAgentV2;
        }

        throw new IllegalStateException("无任何可用的ExecuteAgent实现");
    }

    /**
     * 获取当前配置的版本号
     *
     * @return "V1" 或 "V2"
     */
    public String getCurrentVersion() {
        return agentVersion;
    }

    /**
     * 检查V2是否可用
     *
     * @return true if V2已实现并可用
     */
    public boolean isV2Available() {
        return executeAgentV2 != null;
    }

    /**
     * 获取所有可用的ExecuteAgent版本信息
     *
     * @return 版本信息字符串
     */
    public String getAvailableVersions() {
        StringBuilder sb = new StringBuilder();
        sb.append("可用版本：");

        if (executeAgentV2 != null) {
            sb.append("V2(").append(executeAgentV2.getDescription()).append(")");
        }

        sb.append(" | 当前使用: ").append(agentVersion);
        return sb.toString();
    }
}
