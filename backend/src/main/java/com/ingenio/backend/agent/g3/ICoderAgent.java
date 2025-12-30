package com.ingenio.backend.agent.g3;

import com.ingenio.backend.entity.g3.G3ArtifactEntity;
import com.ingenio.backend.entity.g3.G3JobEntity;
import com.ingenio.backend.entity.g3.G3LogEntry;

import java.util.List;
import java.util.function.Consumer;

/**
 * 编码器Agent接口
 * 负责根据契约生成代码文件
 *
 * 编码器必须严格遵循Architect生成的契约（OpenAPI + DB Schema）
 * 生成的代码将被提交到沙箱进行编译验证
 */
public interface ICoderAgent extends IG3Agent {

    /**
     * 编码结果
     */
    record CoderResult(
            /**
             * 生成的代码产物列表
             */
            List<G3ArtifactEntity> artifacts,

            /**
             * 生成是否成功
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
        public static CoderResult success(List<G3ArtifactEntity> artifacts) {
            return new CoderResult(artifacts, true, null);
        }

        /**
         * 创建失败结果
         */
        public static CoderResult failure(String errorMessage) {
            return new CoderResult(List.of(), false, errorMessage);
        }
    }

    /**
     * 生成代码
     *
     * @param job           G3任务实体（包含契约信息）
     * @param generationRound 当前生成轮次（0=首次，>0=修复后重新生成）
     * @param logConsumer   日志回调
     * @return 编码结果
     */
    CoderResult generate(G3JobEntity job, int generationRound, Consumer<G3LogEntry> logConsumer);

    /**
     * 获取编码器目标类型
     *
     * @return 目标类型（backend/frontend）
     */
    String getTargetType();

    /**
     * 获取编码器目标语言
     *
     * @return 目标语言（java/typescript等）
     */
    String getTargetLanguage();

    @Override
    default G3LogEntry.Role getRole() {
        return G3LogEntry.Role.PLAYER;
    }
}
