package com.ingenio.backend.dto.response;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.UUID;

/**
 * 设计确认响应DTO
 *
 * V2.0 Plan阶段关键API返回值：
 * 用户确认设计后，返回结构化信息，指示是否可以进入Execute阶段
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DesignConfirmResponse {

    /**
     * 操作是否成功
     */
    private boolean success;

    /**
     * AppSpec ID
     */
    private UUID appSpecId;

    /**
     * 是否可以进入Execute阶段
     * true: 所有前置条件满足，可以开始代码生成
     * false: 存在阻塞条件，需要用户处理
     */
    private boolean canProceedToExecute;

    /**
     * 设计确认时间
     */
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", timezone = "UTC")
    private Instant designConfirmedAt;

    /**
     * 操作消息
     */
    private String message;

    /**
     * 下一步操作提示
     */
    private String nextAction;
}
