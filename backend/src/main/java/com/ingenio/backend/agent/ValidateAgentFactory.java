package com.ingenio.backend.agent;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * ValidateAgent工厂类 - Feature Flag模式实现
 *
 * <p>职责：根据环境变量配置，动态选择V1或V2版本的ValidateAgent实现</p>
 *
 * <p>Feature Flag配置：</p>
 * <pre>
 * # application.yml
 * ingenio:
 *   agent:
 *     version: V1  # V1（AppSpec验证）或 V2（多端编译验证）
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
public class ValidateAgentFactory {

    /**
     * Agent版本配置（V1 或 V2）
     * 默认值：V1（保持向后兼容）
     */
    @Value("${ingenio.agent.version:V1}")
    private String agentVersion;

    /**
     * V1.0 ValidateAgent实现（AppSpec JSON验证）
     */
    @Autowired
    @org.springframework.beans.factory.annotation.Qualifier("validateAgentV1")
    private IValidateAgent validateAgentV1;

    /**
     * V2.0 ValidateAgent实现（多端编译验证）
     * 注意：在V2实现类未注入前，此字段可能为null
     */
    @Autowired(required = false)
    @org.springframework.beans.factory.annotation.Qualifier("validateAgentV2")
    private IValidateAgent validateAgentV2;

    /**
     * 获取当前配置的ValidateAgent实现
     *
     * @return 根据Feature Flag配置返回对应版本的ValidateAgent
     * @throws IllegalStateException 如果V2未实现但配置为V2
     */
    public IValidateAgent getValidateAgent() {
        log.info("[ValidateAgentFactory] 当前配置的Agent版本: {}", agentVersion);

        if ("V2".equalsIgnoreCase(agentVersion)) {
            if (validateAgentV2 == null) {
                log.error("[ValidateAgentFactory] V2.0 ValidateAgent未实现，但配置为V2模式");
                throw new IllegalStateException(
                    "V2.0 ValidateAgent未实现。" +
                    "请检查ValidateAgentV2FullStackImpl是否已创建并正确注入。" +
                    "或者将配置改为 ingenio.agent.version=V1"
                );
            }
            log.info("[ValidateAgentFactory] 使用V2.0实现: {}", validateAgentV2.getDescription());
            return validateAgentV2;
        }

        // 默认使用V1
        if (validateAgentV1 == null) {
            log.error("[ValidateAgentFactory] V1.0 ValidateAgent未注入");
            throw new IllegalStateException("V1.0 ValidateAgent未注入，系统无法正常运行");
        }

        log.info("[ValidateAgentFactory] 使用V1.0实现: {}", validateAgentV1.getDescription());
        return validateAgentV1;
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
        return validateAgentV2 != null;
    }

    /**
     * 获取所有可用的ValidateAgent版本信息
     *
     * @return 版本信息字符串
     */
    public String getAvailableVersions() {
        StringBuilder sb = new StringBuilder();
        sb.append("可用版本：");

        if (validateAgentV1 != null) {
            sb.append("V1(").append(validateAgentV1.getDescription()).append(")");
        }

        if (validateAgentV2 != null) {
            if (validateAgentV1 != null) {
                sb.append(", ");
            }
            sb.append("V2(").append(validateAgentV2.getDescription()).append(")");
        }

        sb.append(" | 当前使用: ").append(agentVersion);
        return sb.toString();
    }
}
