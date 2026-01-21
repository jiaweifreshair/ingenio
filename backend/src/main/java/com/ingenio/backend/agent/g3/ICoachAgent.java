package com.ingenio.backend.agent.g3;

import com.ingenio.backend.entity.g3.G3ArtifactEntity;
import com.ingenio.backend.entity.g3.G3JobEntity;
import com.ingenio.backend.entity.g3.G3LogEntry;
import com.ingenio.backend.entity.g3.G3SessionMemory;
import com.ingenio.backend.entity.g3.G3ValidationResultEntity;

import java.util.List;
import java.util.function.Consumer;

/**
 * 教练Agent接口
 * 负责分析编译/测试错误，并生成修复代码
 *
 * Coach是G3引擎"自修复"能力的核心：
 * 1. 接收Executor的编译错误输出
 * 2. 分析错误原因（语法错误、类型错误、依赖缺失等）
 * 3. 生成修复后的代码版本
 * 4. 将修复代码提交给Executor重新验证
 *
 * v2.1.0 增强：
 * - 支持 Session Memory，传递修复历史上下文
 * - 错误聚合分析，理解跨文件依赖关系
 */
public interface ICoachAgent extends IG3Agent {

    /**
     * 修复结果
     */
    record CoachResult(
            /**
             * 修复后的代码产物列表
             */
            List<G3ArtifactEntity> fixedArtifacts,

            /**
             * 修复是否成功
             */
            boolean success,

            /**
             * 修复分析报告
             */
            String analysisReport,

            /**
             * 错误信息（如果修复失败）
             */
            String errorMessage) {
        /**
         * 创建成功结果
         */
        public static CoachResult success(List<G3ArtifactEntity> fixedArtifacts, String analysisReport) {
            return new CoachResult(fixedArtifacts, true, analysisReport, null);
        }

        /**
         * 创建失败结果
         */
        public static CoachResult failure(String errorMessage) {
            return new CoachResult(List.of(), false, null, errorMessage);
        }

        /**
         * 创建无法修复结果
         */
        public static CoachResult cannotFix(String analysisReport) {
            return new CoachResult(List.of(), false, analysisReport, "无法自动修复此类错误");
        }
    }

    /**
     * 分析错误并生成修复代码（带 Session Memory）
     *
     * @param job               G3任务实体
     * @param errorArtifacts    有错误的代码产物
     * @param validationResults 验证结果（包含编译器输出）
     * @param sessionMemory     Session 记忆（包含修复历史）
     * @param logConsumer       日志回调
     * @return 修复结果
     */
    CoachResult fix(
            G3JobEntity job,
            List<G3ArtifactEntity> errorArtifacts,
            List<G3ValidationResultEntity> validationResults,
            G3SessionMemory sessionMemory,
            Consumer<G3LogEntry> logConsumer);

    /**
     * 分析错误并生成修复代码（兼容旧版，无 Session Memory）
     *
     * @param job               G3任务实体
     * @param errorArtifacts    有错误的代码产物
     * @param validationResults 验证结果（包含编译器输出）
     * @param logConsumer       日志回调
     * @return 修复结果
     * @deprecated 推荐使用带 sessionMemory 参数的版本
     */
    @Deprecated
    default CoachResult fix(
            G3JobEntity job,
            List<G3ArtifactEntity> errorArtifacts,
            List<G3ValidationResultEntity> validationResults,
            Consumer<G3LogEntry> logConsumer) {
        // 兼容旧调用：创建临时 Memory
        G3SessionMemory tempMemory = new G3SessionMemory(job.getId());
        return fix(job, errorArtifacts, validationResults, tempMemory, logConsumer);
    }

    /**
     * 分析单个产物的错误
     *
     * @param artifact       有错误的产物
     * @param compilerOutput 编译器输出
     * @return 错误分析报告
     */
    String analyzeError(G3ArtifactEntity artifact, String compilerOutput);

    /**
     * 判断错误是否可以自动修复
     *
     * @param compilerOutput 编译器输出
     * @return 是否可以自动修复
     */
    boolean canAutoFix(String compilerOutput);

    @Override
    default G3LogEntry.Role getRole() {
        return G3LogEntry.Role.COACH;
    }
}
