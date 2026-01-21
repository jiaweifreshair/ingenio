package com.ingenio.backend.langchain4j.tool;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ingenio.backend.dto.request.repair.TriggerRepairRequest;
import com.ingenio.backend.dto.request.repair.TriggerRepairRequest.ErrorDetail;
import com.ingenio.backend.dto.response.repair.RepairResponse;
import com.ingenio.backend.service.RepairService;
import dev.langchain4j.agent.tool.Tool;

import java.util.List;
import java.util.UUID;

/**
 * 修复服务工具封装。
 *
 * 是什么：RepairService 的 LangChain4j 工具适配器。
 * 做什么：触发修复流程并返回修复响应。
 * 为什么：为 Agent 提供自动修复能力。
 */
public class RepairServiceTool {

    /**
     * 修复服务。
     *
     * 是什么：现有修复服务实例。
     * 做什么：触发修复流程。
     * 为什么：复用现有修复逻辑与记录能力。
     */
    private final RepairService repairService;

    /**
     * JSON 序列化器。
     *
     * 是什么：ObjectMapper 实例。
     * 做什么：解析错误详情并序列化输出。
     * 为什么：支持工具输入输出标准化。
     */
    private final ObjectMapper objectMapper;

    /**
     * 构造函数。
     *
     * 是什么：修复工具初始化入口。
     * 做什么：注入修复服务与 JSON 解析器。
     * 为什么：保证工具可解析输入并输出结果。
     *
     * @param repairService 修复服务
     * @param objectMapper JSON 解析器
     */
    public RepairServiceTool(RepairService repairService, ObjectMapper objectMapper) {
        this.repairService = repairService;
        this.objectMapper = objectMapper;
    }

    /**
     * 触发修复流程。
     *
     * 是什么：可被 Agent 调用的修复入口。
     * 做什么：构建 TriggerRepairRequest 并调用 RepairService。
     * 为什么：让 Agent 能以结构化方式触发修复。
     *
     * @param appSpecId       AppSpec ID
     * @param tenantId        租户ID
     * @param failureType     失败类型
     * @param errorDetailsJson 错误详情 JSON（数组）
     * @return 修复响应 JSON
     */
    @Tool("触发修复流程并返回修复响应")
    public String triggerRepair(String appSpecId, String tenantId, String failureType, String errorDetailsJson) {
        TriggerRepairRequest request = TriggerRepairRequest.builder()
                .appSpecId(parseUuid(appSpecId))
                .tenantId(parseUuid(tenantId))
                .failureType(failureType)
                .errorDetails(parseErrorDetails(errorDetailsJson))
                .build();
        RepairResponse response = repairService.triggerRepair(request);
        try {
            return objectMapper.writeValueAsString(response);
        } catch (JsonProcessingException e) {
            return "{\"success\":false,\"message\":\"修复响应序列化失败\"}";
        }
    }

    /**
     * 解析错误详情列表。
     *
     * 是什么：错误详情解析方法。
     * 做什么：将 JSON 数组解析为 ErrorDetail 列表。
     * 为什么：保证工具输入可被 RepairService 使用。
     *
     * @param raw JSON 字符串
     * @return 错误详情列表
     */
    private List<ErrorDetail> parseErrorDetails(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(raw, new TypeReference<List<ErrorDetail>>() {});
        } catch (Exception e) {
            return List.of();
        }
    }

    /**
     * 解析 UUID。
     *
     * 是什么：UUID 解析工具方法。
     * 做什么：将字符串解析为 UUID。
     * 为什么：避免工具调用输入格式错误导致异常。
     *
     * @param raw 原始字符串
     * @return UUID 或 null
     */
    private UUID parseUuid(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
