package com.ingenio.backend.agent.g3;

import com.ingenio.backend.entity.g3.G3ArtifactEntity;
import com.ingenio.backend.entity.g3.G3JobEntity;
import com.ingenio.backend.entity.g3.G3LogEntry;

import java.util.List;
import java.util.function.Consumer;

/**
 * G3 Agent 基础接口
 * 定义所有G3引擎Agent的通用行为
 *
 * G3引擎Agent角色：
 * - Architect: 架构师，生成OpenAPI契约和数据库Schema
 * - BackendCoder: 后端编码器，根据契约生成Spring Boot代码
 * - FrontendCoder: 前端编码器，根据契约生成React代码
 * - Coach: 修复教练，分析编译错误并生成修复代码
 */
public interface IG3Agent {

    /**
     * 获取Agent名称
     *
     * @return Agent名称（用于日志和追踪）
     */
    String getName();

    /**
     * 获取Agent角色
     *
     * @return Agent角色枚举
     */
    G3LogEntry.Role getRole();

    /**
     * 执行Agent任务
     *
     * @param job           G3任务实体
     * @param logConsumer   日志回调（用于实时输出日志到SSE）
     * @return 生成的产物列表
     * @throws G3AgentException 执行失败时抛出
     */
    List<G3ArtifactEntity> execute(G3JobEntity job, Consumer<G3LogEntry> logConsumer) throws G3AgentException;

    /**
     * 检查Agent是否可用
     *
     * @return 是否可用
     */
    default boolean isAvailable() {
        return true;
    }

    /**
     * 获取Agent描述
     *
     * @return Agent描述
     */
    default String getDescription() {
        return getName();
    }

    /**
     * G3 Agent 执行异常
     */
    class G3AgentException extends RuntimeException {
        private final String agentName;
        private final G3LogEntry.Role role;

        public G3AgentException(String agentName, G3LogEntry.Role role, String message) {
            super(message);
            this.agentName = agentName;
            this.role = role;
        }

        public G3AgentException(String agentName, G3LogEntry.Role role, String message, Throwable cause) {
            super(message, cause);
            this.agentName = agentName;
            this.role = role;
        }

        public String getAgentName() {
            return agentName;
        }

        public G3LogEntry.Role getRole() {
            return role;
        }
    }
}
