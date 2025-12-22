package com.ingenio.backend.controller;

import com.ingenio.backend.common.Result;
import com.ingenio.backend.dto.request.validation.*;
import com.ingenio.backend.dto.response.validation.FullValidationResponse;
import com.ingenio.backend.dto.response.validation.ValidationResponse;
import com.ingenio.backend.service.ValidationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * V2.0 验证API控制器
 *
 * 功能：
 * 1. POST /compile - 编译验证（TypeScript/Java）
 * 2. POST /test - 单元测试执行
 * 3. POST /coverage - 测试覆盖率计算
 * 4. POST /quality-gate - 质量门禁验证（覆盖率≥85%）
 * 5. POST /contract - API契约验证（OpenAPI）
 * 6. POST /schema - 数据库Schema验证
 * 7. POST /business-flow - 业务流程完整性验证
 * 8. POST /full - 三环集成验证（编译→测试→业务）
 *
 * @author Ingenio Team
 * @since 2.0.0 Phase 3
 */
@Slf4j
@RestController
@RequestMapping("/v2/validate")
@RequiredArgsConstructor
public class ValidationController {

    private final ValidationService validationService;

    /**
     * 编译验证
     *
     * 请求示例：
     * {
     *   "appSpecId": "550e8400-e29b-41d4-a716-446655440000",
     *   "code": "export function greet(name: string): string { return `Hello, ${name}!`; }",
     *   "language": "typescript"
     * }
     *
     * 响应示例：
     * {
     *   "code": 200,
     *   "success": true,
     *   "message": "success",
     *   "data": {
     *     "validationId": "...",
     *     "validationType": "compile",
     *     "passed": true,
     *     "status": "passed",
     *     "qualityScore": 100,
     *     "details": {
     *       "compileSuccess": true,
     *       "language": "typescript",
     *       "codeLength": 75
     *     },
     *     "errors": [],
     *     "warnings": [],
     *     "durationMs": 234,
     *     "completedAt": "2025-11-22T10:30:00Z"
     *   }
     * }
     *
     * @param request 编译验证请求
     * @return 验证响应
     */
    @PostMapping("/compile")
    public Result<ValidationResponse> validateCompile(@Valid @RequestBody CompileValidationRequest request) {
        log.info("收到编译验证请求 - appSpecId: {}, language: {}", request.getAppSpecId(), request.getLanguage());

        try {
            ValidationResponse response = validationService.validateCompile(request);
            return Result.success(response);
        } catch (IllegalArgumentException e) {
            log.warn("编译验证请求参数错误: {}", e.getMessage());
            return Result.error(400, e.getMessage());
        } catch (Exception e) {
            log.error("编译验证失败", e);
            return Result.error("编译验证失败: " + e.getMessage());
        }
    }

    /**
     * 单元测试验证
     *
     * 请求示例：
     * {
     *   "appSpecId": "550e8400-e29b-41d4-a716-446655440000",
     *   "testType": "unit",
     *   "testFiles": ["src/__tests__/utils.test.ts"],
     *   "generateCoverage": true
     * }
     *
     * 响应示例：
     * {
     *   "code": 200,
     *   "success": true,
     *   "message": "success",
     *   "data": {
     *     "validationId": "...",
     *     "validationType": "test",
     *     "passed": true,
     *     "status": "passed",
     *     "qualityScore": 90,
     *     "details": {
     *       "totalTests": 10,
     *       "passedTests": 9,
     *       "failedTests": 1,
     *       "testType": "unit"
     *     },
     *     "errors": [],
     *     "durationMs": 1234
     *   }
     * }
     *
     * @param request 测试验证请求
     * @return 验证响应
     */
    @PostMapping("/test")
    public Result<ValidationResponse> validateTest(@Valid @RequestBody TestValidationRequest request) {
        log.info("收到测试验证请求 - appSpecId: {}, testType: {}", request.getAppSpecId(), request.getTestType());

        try {
            ValidationResponse response = validationService.validateTest(request);
            return Result.success(response);
        } catch (Exception e) {
            log.error("测试验证失败", e);
            return Result.error("测试验证失败: " + e.getMessage());
        }
    }

    /**
     * 测试覆盖率计算
     *
     * 请求示例：
     * {
     *   "appSpecId": "550e8400-e29b-41d4-a716-446655440000"
     * }
     *
     * 响应示例：
     * {
     *   "code": 200,
     *   "success": true,
     *   "message": "success",
     *   "data": {
     *     "validationId": "...",
     *     "validationType": "coverage",
     *     "passed": true,
     *     "status": "passed",
     *     "qualityScore": 88,
     *     "details": {
     *       "lineCoverage": 88.5,
     *       "branchCoverage": 85.2,
     *       "functionCoverage": 90.0,
     *       "statementCoverage": 87.5
     *     },
     *     "errors": [],
     *     "durationMs": 567
     *   }
     * }
     *
     * @param request 包含appSpecId的请求体
     * @return 验证响应
     */
    @PostMapping("/coverage")
    public Result<ValidationResponse> validateCoverage(@RequestBody Map<String, String> request) {
        UUID appSpecId = UUID.fromString(request.get("appSpecId"));
        log.info("收到覆盖率验证请求 - appSpecId: {}", appSpecId);

        try {
            ValidationResponse response = validationService.validateCoverage(appSpecId);
            return Result.success(response);
        } catch (Exception e) {
            log.error("覆盖率验证失败", e);
            return Result.error("覆盖率验证失败: " + e.getMessage());
        }
    }

    /**
     * 质量门禁验证
     *
     * 请求示例：
     * {
     *   "appSpecId": "550e8400-e29b-41d4-a716-446655440000",
     *   "metrics": {
     *     "coverage": 90,
     *     "complexity": 8
     *   },
     *   "coverageThreshold": 85,
     *   "complexityThreshold": 10
     * }
     *
     * 响应示例：
     * {
     *   "code": 200,
     *   "success": true,
     *   "message": "success",
     *   "data": {
     *     "validationId": "...",
     *     "validationType": "quality_gate",
     *     "passed": true,
     *     "status": "passed",
     *     "qualityScore": 90,
     *     "details": {
     *       "coverage": 90,
     *       "complexity": 8,
     *       "coverageThreshold": 85,
     *       "complexityThreshold": 10,
     *       "coveragePassed": true,
     *       "complexityPassed": true
     *     },
     *     "errors": [],
     *     "durationMs": 123
     *   }
     * }
     *
     * @param request 质量门禁请求
     * @return 验证响应
     */
    @PostMapping("/quality-gate")
    public Result<ValidationResponse> validateQualityGate(@Valid @RequestBody QualityGateRequest request) {
        log.info("收到质量门禁验证请求 - appSpecId: {}", request.getAppSpecId());

        try {
            ValidationResponse response = validationService.validateQualityGate(request);
            return Result.success(response);
        } catch (Exception e) {
            log.error("质量门禁验证失败", e);
            return Result.error("质量门禁验证失败: " + e.getMessage());
        }
    }

    /**
     * API契约验证
     *
     * 请求示例：
     * {
     *   "appSpecId": "550e8400-e29b-41d4-a716-446655440000",
     *   "openApiSpec": {
     *     "openapi": "3.0.0",
     *     "info": { "title": "Test API", "version": "1.0.0" },
     *     "paths": {
     *       "/users": {
     *         "get": { "responses": { "200": { "description": "Success" } } }
     *       }
     *     }
     *   }
     * }
     *
     * @param request 包含appSpecId和openApiSpec的请求体
     * @return 验证响应
     */
    @PostMapping("/contract")
    public Result<ValidationResponse> validateContract(@RequestBody Map<String, Object> request) {
        UUID appSpecId = UUID.fromString((String) request.get("appSpecId"));
        @SuppressWarnings("unchecked")
        Map<String, Object> openApiSpec = (Map<String, Object>) request.get("openApiSpec");

        log.info("收到API契约验证请求 - appSpecId: {}", appSpecId);

        try {
            ValidationResponse response = validationService.validateContract(appSpecId, openApiSpec);
            return Result.success(response);
        } catch (Exception e) {
            log.error("API契约验证失败", e);
            return Result.error("API契约验证失败: " + e.getMessage());
        }
    }

    /**
     * 数据库Schema验证
     *
     * 请求示例：
     * {
     *   "appSpecId": "550e8400-e29b-41d4-a716-446655440000",
     *   "schema": {
     *     "tables": [
     *       { "name": "users", "columns": ["id", "email", "password"] },
     *       { "name": "posts", "columns": ["id", "title", "content", "user_id"] }
     *     ]
     *   }
     * }
     *
     * @param request 包含appSpecId和schema的请求体
     * @return 验证响应
     */
    @PostMapping("/schema")
    public Result<ValidationResponse> validateSchema(@RequestBody Map<String, Object> request) {
        UUID appSpecId = UUID.fromString((String) request.get("appSpecId"));
        @SuppressWarnings("unchecked")
        Map<String, Object> schema = (Map<String, Object>) request.get("schema");

        log.info("收到Schema验证请求 - appSpecId: {}", appSpecId);

        try {
            ValidationResponse response = validationService.validateSchema(appSpecId, schema);
            return Result.success(response);
        } catch (Exception e) {
            log.error("Schema验证失败", e);
            return Result.error("Schema验证失败: " + e.getMessage());
        }
    }

    /**
     * 业务流程验证
     *
     * 请求示例：
     * {
     *   "appSpecId": "550e8400-e29b-41d4-a716-446655440000",
     *   "flows": [
     *     {
     *       "name": "用户注册流程",
     *       "steps": ["输入邮箱", "设置密码", "发送验证邮件", "验证成功"]
     *     }
     *   ]
     * }
     *
     * @param request 包含appSpecId和flows的请求体
     * @return 验证响应
     */
    @PostMapping("/business-flow")
    public Result<ValidationResponse> validateBusinessFlow(@RequestBody Map<String, Object> request) {
        UUID appSpecId = UUID.fromString((String) request.get("appSpecId"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> flows = (List<Map<String, Object>>) request.get("flows");

        log.info("收到业务流程验证请求 - appSpecId: {}", appSpecId);

        try {
            ValidationResponse response = validationService.validateBusinessFlow(appSpecId, flows);
            return Result.success(response);
        } catch (Exception e) {
            log.error("业务流程验证失败", e);
            return Result.error("业务流程验证失败: " + e.getMessage());
        }
    }

    /**
     * 三环集成验证
     *
     * 请求示例：
     * {
     *   "appSpecId": "550e8400-e29b-41d4-a716-446655440000",
     *   "tenantId": "00000000-0000-0000-0000-000000000001",
     *   "stages": ["compile", "test", "business"],
     *   "parallel": false,
     *   "failFast": true
     * }
     *
     * 响应示例：
     * {
     *   "code": 200,
     *   "success": true,
     *   "message": "success",
     *   "data": {
     *     "validationTaskId": "...",
     *     "stages": {
     *       "compile": {
     *         "stage": "compile",
     *         "status": "passed",
     *         "passed": true,
     *         "details": { ... },
     *         "durationMs": 234
     *       },
     *       "test": {
     *         "stage": "test",
     *         "status": "failed",
     *         "passed": false,
     *         "errorMessage": "测试失败: 1个测试用例未通过",
     *         "details": { ... },
     *         "durationMs": 1234
     *       },
     *       "business": {
     *         "stage": "business",
     *         "status": "skipped",
     *         "passed": false,
     *         "errorMessage": "前置阶段验证失败",
     *         "durationMs": 0
     *       }
     *     },
     *     "overallStatus": "failed",
     *     "overallScore": 67,
     *     "totalDurationMs": 1468,
     *     "completedAt": "2025-11-22T10:30:00Z"
     *   }
     * }
     *
     * @param request 全量验证请求
     * @return 全量验证响应
     */
    @PostMapping("/full")
    public Result<FullValidationResponse> validateFull(@Valid @RequestBody FullValidationRequest request) {
        log.info("收到三环集成验证请求 - appSpecId: {}, stages: {}", request.getAppSpecId(), request.getStages());

        try {
            FullValidationResponse response = validationService.validateFull(request);
            return Result.success(response);
        } catch (Exception e) {
            log.error("三环集成验证失败", e);
            return Result.error("三环集成验证失败: " + e.getMessage());
        }
    }
}
