package com.ingenio.backend.controller;

import com.ingenio.backend.common.Result;
import com.ingenio.backend.dto.request.repair.*;
import com.ingenio.backend.dto.response.repair.RepairResponse;
import com.ingenio.backend.service.RepairService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * V2.0 AI自动修复API控制器
 *
 * 功能：
 * 1. POST /trigger - 触发AI修复流程
 * 2. POST /suggest - 生成修复建议
 * 3. POST /iterate - 迭代修复（最多3次）
 * 4. POST /escalate - 升级到人工介入
 * 5. POST /type-error - 类型错误修复
 * 6. POST /dependency - 依赖自动安装
 * 7. POST /business-logic - 业务逻辑修复
 * 8. POST /auto - 自动修复并重新验证
 * 9. GET /history - 查询修复历史
 *
 * @author Ingenio Team
 * @since 2.0.0 Phase 4
 */
@RestController
@RequestMapping("/v2/repair")
public class RepairController {

    private static final Logger log = LoggerFactory.getLogger(RepairController.class);

    private final RepairService repairService;

    public RepairController(RepairService repairService) {
        this.repairService = repairService;
    }

    /**
     * 触发AI修复流程
     *
     * 请求示例：
     * {
     * "appSpecId": "550e8400-e29b-41d4-a716-446655440000",
     * "tenantId": "00000000-0000-0000-0000-000000000001",
     * "failureType": "compile",
     * "errorDetails": [
     * { "line": 5, "message": "Type string is not assignable to type number" }
     * ]
     * }
     *
     * @param request 触发修复请求
     * @return 修复响应
     */
    @PostMapping("/trigger")
    public Result<RepairResponse> triggerRepair(@Valid @RequestBody TriggerRepairRequest request) {
        log.info("收到触发修复请求 - appSpecId: {}, failureType: {}",
                request.getAppSpecId(), request.getFailureType());

        try {
            RepairResponse response = repairService.triggerRepair(request);
            return Result.success(response);
        } catch (Exception e) {
            log.error("触发修复失败", e);
            return Result.error("触发修复失败: " + e.getMessage());
        }
    }

    /**
     * 生成修复建议
     *
     * 请求示例：
     * {
     * "appSpecId": "550e8400-e29b-41d4-a716-446655440000",
     * "failedTests": [
     * {
     * "testName": "should return user by id",
     * "error": "Expected 200 but received 404",
     * "stackTrace": "at UserService.test.ts:25"
     * }
     * ]
     * }
     *
     * @param request 包含appSpecId和failedTests的请求体
     * @return 修复响应
     */
    @PostMapping("/suggest")
    public Result<RepairResponse> generateSuggestions(@RequestBody Map<String, Object> request) {
        UUID appSpecId = UUID.fromString((String) request.get("appSpecId"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> failedTests = (List<Map<String, Object>>) request.get("failedTests");

        log.info("收到生成修复建议请求 - appSpecId: {}, failedTestCount: {}",
                appSpecId, failedTests != null ? failedTests.size() : 0);

        try {
            RepairResponse response = repairService.generateSuggestions(appSpecId, failedTests);
            return Result.success(response);
        } catch (Exception e) {
            log.error("生成修复建议失败", e);
            return Result.error("生成修复建议失败: " + e.getMessage());
        }
    }

    /**
     * 迭代修复
     *
     * 请求示例：
     * {
     * "appSpecId": "550e8400-e29b-41d4-a716-446655440000",
     * "tenantId": "00000000-0000-0000-0000-000000000001",
     * "repairId": "660e8400-e29b-41d4-a716-446655440000",
     * "maxIterations": 3,
     * "autoEscalate": true
     * }
     *
     * @param request 迭代修复请求
     * @return 修复响应
     */
    @PostMapping("/iterate")
    public Result<RepairResponse> iterateRepair(@Valid @RequestBody IterateRepairRequest request) {
        log.info("收到迭代修复请求 - repairId: {}, maxIterations: {}",
                request.getRepairId(), request.getMaxIterations());

        try {
            RepairResponse response = repairService.iterateRepair(request);
            return Result.success(response);
        } catch (IllegalArgumentException | IllegalStateException e) {
            log.warn("迭代修复参数错误: {}", e.getMessage());
            return Result.error(400, e.getMessage());
        } catch (Exception e) {
            log.error("迭代修复失败", e);
            return Result.error("迭代修复失败: " + e.getMessage());
        }
    }

    /**
     * 升级到人工介入
     *
     * 请求示例：
     * {
     * "appSpecId": "550e8400-e29b-41d4-a716-446655440000",
     * "repairId": "660e8400-e29b-41d4-a716-446655440000",
     * "failedIterations": 3,
     * "lastError": "无法自动修复的复杂错误"
     * }
     *
     * @param request 包含repairId和lastError的请求体
     * @return 修复响应
     */
    @PostMapping("/escalate")
    public Result<RepairResponse> escalateToHuman(@RequestBody Map<String, Object> request) {
        UUID repairId = UUID.fromString((String) request.get("repairId"));
        String lastError = (String) request.get("lastError");

        log.info("收到升级人工介入请求 - repairId: {}", repairId);

        try {
            RepairResponse response = repairService.escalateToHuman(repairId, lastError);
            return Result.success(response);
        } catch (Exception e) {
            log.error("升级人工介入失败", e);
            return Result.error("升级人工介入失败: " + e.getMessage());
        }
    }

    /**
     * 类型错误修复
     *
     * 请求示例：
     * {
     * "appSpecId": "550e8400-e29b-41d4-a716-446655440000",
     * "errorCode": "TS2322",
     * "context": {
     * "expectedType": "number",
     * "actualType": "string",
     * "variableName": "count"
     * }
     * }
     *
     * @param request 包含appSpecId、errorCode和context的请求体
     * @return 修复响应
     */
    @PostMapping("/type-error")
    public Result<RepairResponse> fixTypeError(@RequestBody Map<String, Object> request) {
        UUID appSpecId = UUID.fromString((String) request.get("appSpecId"));
        String errorCode = (String) request.get("errorCode");
        @SuppressWarnings("unchecked")
        Map<String, Object> context = (Map<String, Object>) request.get("context");

        log.info("收到类型错误修复请求 - appSpecId: {}, errorCode: {}", appSpecId, errorCode);

        try {
            RepairResponse response = repairService.fixTypeError(appSpecId, errorCode, context);
            return Result.success(response);
        } catch (Exception e) {
            log.error("类型错误修复失败", e);
            return Result.error("类型错误修复失败: " + e.getMessage());
        }
    }

    /**
     * 依赖自动安装
     *
     * 请求示例：
     * {
     * "appSpecId": "550e8400-e29b-41d4-a716-446655440000",
     * "missingDependency": "lodash",
     * "importStatement": "import _ from 'lodash';"
     * }
     *
     * @param request 包含appSpecId和missingDependency的请求体
     * @return 修复响应
     */
    @PostMapping("/dependency")
    public Result<RepairResponse> installDependency(@RequestBody Map<String, Object> request) {
        UUID appSpecId = UUID.fromString((String) request.get("appSpecId"));
        String missingDependency = (String) request.get("missingDependency");

        log.info("收到依赖安装请求 - appSpecId: {}, dependency: {}", appSpecId, missingDependency);

        try {
            RepairResponse response = repairService.installDependency(appSpecId, missingDependency);
            return Result.success(response);
        } catch (Exception e) {
            log.error("依赖安装失败", e);
            return Result.error("依赖安装失败: " + e.getMessage());
        }
    }

    /**
     * 业务逻辑修复
     *
     * 请求示例：
     * {
     * "appSpecId": "550e8400-e29b-41d4-a716-446655440000",
     * "errorDescription": "购物车总价计算不包含折扣",
     * "expectedBehavior": "总价 = 商品价格 * 数量 - 折扣",
     * "actualBehavior": "总价 = 商品价格 * 数量"
     * }
     *
     * @param request 包含appSpecId、errorDescription和expectedBehavior的请求体
     * @return 修复响应
     */
    @PostMapping("/business-logic")
    public Result<RepairResponse> fixBusinessLogic(@RequestBody Map<String, Object> request) {
        UUID appSpecId = UUID.fromString((String) request.get("appSpecId"));
        String errorDescription = (String) request.get("errorDescription");
        String expectedBehavior = (String) request.get("expectedBehavior");

        log.info("收到业务逻辑修复请求 - appSpecId: {}", appSpecId);

        try {
            RepairResponse response = repairService.fixBusinessLogic(appSpecId, errorDescription, expectedBehavior);
            return Result.success(response);
        } catch (Exception e) {
            log.error("业务逻辑修复失败", e);
            return Result.error("业务逻辑修复失败: " + e.getMessage());
        }
    }

    /**
     * 自动修复并重新验证
     *
     * 请求示例：
     * {
     * "appSpecId": "550e8400-e29b-41d4-a716-446655440000",
     * "tenantId": "00000000-0000-0000-0000-000000000001",
     * "maxIterations": 3,
     * "autoEscalate": true,
     * "autoDeploy": false
     * }
     *
     * @param request 自动修复请求
     * @return 修复响应
     */
    @PostMapping("/auto")
    public Result<RepairResponse> autoRepair(@Valid @RequestBody AutoRepairRequest request) {
        log.info("收到自动修复请求 - appSpecId: {}, maxIterations: {}",
                request.getAppSpecId(), request.getMaxIterations());

        try {
            RepairResponse response = repairService.autoRepairAndValidate(request);
            return Result.success(response);
        } catch (Exception e) {
            log.error("自动修复失败", e);
            return Result.error("自动修复失败: " + e.getMessage());
        }
    }

    /**
     * 查询修复历史
     *
     * @param appSpecId AppSpec ID
     * @return 修复记录列表
     */
    @GetMapping("/history")
    public Result<Map<String, Object>> getRepairHistory(@RequestParam String appSpecId) {
        UUID uuid = UUID.fromString(appSpecId);
        log.info("收到查询修复历史请求 - appSpecId: {}", uuid);

        try {
            List<RepairResponse> history = repairService.getRepairHistory(uuid);

            Map<String, Object> response = Map.of(
                    "appSpecId", uuid,
                    "total", history.size(),
                    "history", history);

            return Result.success(response);
        } catch (Exception e) {
            log.error("查询修复历史失败", e);
            return Result.error("查询修复历史失败: " + e.getMessage());
        }
    }
}
