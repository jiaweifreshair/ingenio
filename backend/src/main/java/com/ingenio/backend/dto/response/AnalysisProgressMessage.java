package com.ingenio.backend.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * 分析进度消息DTO
 *
 * 用于SSE流式推送需求分析的实时进度，包含5个步骤：
 * 1. 需求解析（20%）
 * 2. 实体建模（40%）
 * 3. 关系识别（60%）
 * 4. 技术选型（80%）
 * 5. 复杂度评估（100%）
 *
 * @author Ingenio Team
 * @since 1.0.0
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnalysisProgressMessage {

    /**
     * 步骤编号（1-5）
     */
    private Integer step;

    /**
     * 步骤名称
     */
    private String stepName;

    /**
     * 步骤描述（简短说明）
     */
    private String description;

    /**
     * 详细日志信息（用于终端风格展示）
     */
    private String detail;

    /**
     * 步骤状态
     * - pending: 待处理
     * - running: 进行中
     * - completed: 已完成
     * - failed: 失败
     */
    private StepStatus status;

    /**
     * 进度百分比（0-100）
     */
    private Integer progress;

    /**
     * 步骤结果（可选，completed时返回）
     */
    private Object result;

    /**
     * 错误信息（可选，failed时返回）
     */
    private String error;

    /**
     * 时间戳
     */
    private Instant timestamp;

    /**
     * 步骤状态枚举
     */
    public enum StepStatus {
        PENDING("待处理"),
        RUNNING("进行中"),
        COMPLETED("已完成"),
        FAILED("失败");

        private final String displayName;

        StepStatus(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    /**
     * 便捷工厂方法：创建开始消息
     */
    public static AnalysisProgressMessage start(Integer step, String stepName, String description) {
        return AnalysisProgressMessage.builder()
                .step(step)
                .stepName(stepName)
                .description(description)
                .status(StepStatus.RUNNING)
                .progress((step - 1) * 20)
                .timestamp(Instant.now())
                .build();
    }

    /**
     * 便捷工厂方法：创建完成消息
     */
    public static AnalysisProgressMessage complete(Integer step, String stepName, String description, Object result) {
        return AnalysisProgressMessage.builder()
                .step(step)
                .stepName(stepName)
                .description(description)
                .status(StepStatus.COMPLETED)
                .progress(step * 20)
                .result(result)
                .timestamp(Instant.now())
                .build();
    }

    /**
     * 便捷工厂方法：创建失败消息
     */
    public static AnalysisProgressMessage fail(Integer step, String stepName, String description, String error) {
        return AnalysisProgressMessage.builder()
                .step(step)
                .stepName(stepName)
                .description(description)
                .status(StepStatus.FAILED)
                .progress((step - 1) * 20)
                .error(error)
                .timestamp(Instant.now())
                .build();
    }
}
