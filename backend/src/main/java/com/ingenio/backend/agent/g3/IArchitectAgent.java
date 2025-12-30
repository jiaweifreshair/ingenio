package com.ingenio.backend.agent.g3;

import com.ingenio.backend.entity.g3.G3JobEntity;
import com.ingenio.backend.entity.g3.G3LogEntry;

import java.util.function.Consumer;

/**
 * 架构师Agent接口
 * 负责根据需求生成：
 * 1. OpenAPI 3.0 契约文档（YAML格式）
 * 2. PostgreSQL 数据库Schema（DDL SQL）
 *
 * 契约生成后将被"锁定"，后续Coder Agent必须严格遵循契约生成代码
 */
public interface IArchitectAgent extends IG3Agent {

    /**
     * 架构设计结果
     */
    record ArchitectResult(
            /**
             * OpenAPI 3.0 契约文档（YAML格式）
             */
            String contractYaml,

            /**
             * PostgreSQL DDL SQL
             */
            String dbSchemaSql,

            /**
             * 设计是否成功
             */
            boolean success,

            /**
             * 错误信息（如果失败）
             */
            String errorMessage
    ) {
        /**
         * 创建成功结果
         */
        public static ArchitectResult success(String contractYaml, String dbSchemaSql) {
            return new ArchitectResult(contractYaml, dbSchemaSql, true, null);
        }

        /**
         * 创建失败结果
         */
        public static ArchitectResult failure(String errorMessage) {
            return new ArchitectResult(null, null, false, errorMessage);
        }
    }

    /**
     * 执行架构设计
     *
     * @param job         G3任务实体（包含需求文本）
     * @param logConsumer 日志回调
     * @return 架构设计结果
     */
    ArchitectResult design(G3JobEntity job, Consumer<G3LogEntry> logConsumer);

    /**
     * 验证OpenAPI契约格式
     *
     * @param contractYaml OpenAPI YAML内容
     * @return 是否有效
     */
    boolean validateContract(String contractYaml);

    /**
     * 验证SQL Schema格式
     *
     * @param schemaSql SQL DDL内容
     * @return 是否有效
     */
    boolean validateSchema(String schemaSql);

    @Override
    default G3LogEntry.Role getRole() {
        return G3LogEntry.Role.ARCHITECT;
    }
}
