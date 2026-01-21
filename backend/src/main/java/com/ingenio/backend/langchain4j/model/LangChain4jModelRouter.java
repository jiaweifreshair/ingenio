package com.ingenio.backend.langchain4j.model;

/**
 * LangChain4j 模型路由器接口。
 *
 * 是什么：模型选择的统一入口。
 * 做什么：根据任务类型与失败上下文选择模型。
 * 为什么：保证成功率优先并支持降级策略。
 */
public interface LangChain4jModelRouter {

    /**
     * 选择模型。
     *
     * 是什么：模型选择方法。
     * 做什么：返回本次调用应使用的提供商与模型。
     * 为什么：避免在业务逻辑中分散路由策略。
     *
     * @param taskType       任务类型
     * @param attempt        失败重试次数（从0开始）
     * @param failureContext 失败上下文
     * @return 模型选择结果
     */
    ModelSelection select(TaskType taskType, int attempt, FailureContext failureContext);

    /**
     * 任务类型。
     *
     * 是什么：模型路由类别。
     * 做什么：区分 codegen/repair/analysis 场景。
     * 为什么：不同任务对模型能力的偏好不同。
     */
    enum TaskType {
        CODEGEN,
        REPAIR,
        ANALYSIS
    }

    /**
     * 模型选择结果。
     *
     * 是什么：路由器输出的数据结构。
     * 做什么：提供 provider 与 model 信息。
     * 为什么：Agent 需要统一的模型信息以执行调用。
     *
     * @param provider 提供商标识
     * @param model    模型名称
     */
    record ModelSelection(String provider, String model) {
    }

    /**
     * 失败上下文。
     *
     * 是什么：失败信息容器。
     * 做什么：描述上一次模型调用的失败原因。
     * 为什么：为降级与熔断策略提供依据。
     *
     * @param lastProvider 上一次使用的提供商
     * @param lastModel    上一次使用的模型
     * @param reason       失败原因
     */
    record FailureContext(String lastProvider, String lastModel, String reason) {
    }
}
