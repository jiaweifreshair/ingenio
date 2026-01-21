package com.ingenio.backend.service;

import com.ingenio.backend.dto.request.validation.*;
import com.ingenio.backend.dto.response.validation.FullValidationResponse;
import com.ingenio.backend.dto.response.validation.ValidationResponse;
import com.ingenio.backend.entity.AppSpecEntity;
import com.ingenio.backend.entity.ValidationResultEntity;
import com.ingenio.backend.mapper.AppSpecMapper;
import com.ingenio.backend.mapper.ValidationResultMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * V2.0 验证服务（编排器模式 - Phase 1-6重构版）
 *
 * 架构设计：
 * - 编排器模式：ValidationService仅负责任务调度、结果聚合、状态管理
 * - 具体验证委托给专业验证器：CompilationValidator、TestExecutor、CoverageCalculator
 * - 数据格式统一：通过Adapter层实现不同系统间的数据格式转换（G3ValidationAdapter）
 *
 * 核心功能：
 * 1. 编译验证（Phase 1）：委托CompilationValidator执行真实编译（TypeScript/Java/Kotlin）
 * 2. 测试验证（Phase 2）：委托TestExecutor执行单元测试和E2E测试
 * 3. 覆盖率验证（Phase 3）：委托CoverageCalculator解析Istanbul/JaCoCo覆盖率报告
 * 4. 质量门禁验证：检查编译、测试、覆盖率是否满足质量标准（覆盖率≥85%）
 * 5. 业务验证（基础版）：API契约、数据库Schema、业务流程基本格式验证
 * 6. 三环集成验证（Phase 4）：编译→测试→覆盖率的串行/并行验证链路
 * 7. 外部结果保存（Phase 5）：允许G3等外部系统保存验证结果到统一存储
 *
 * 重构历程：
 * - Phase 1: 集成CompilationValidator，删除Mock编译实现
 * - Phase 2: 集成TestExecutor，删除Mock测试实现
 * - Phase 3: 集成CoverageCalculator，删除硬编码覆盖率值
 * - Phase 4: 重构validateFull三环验证，实现线程池优雅关闭
 * - Phase 5: G3引擎集成，通过G3ValidationAdapter实现数据格式统一
 * - Phase 6: 代码清理、JavaDoc文档补充、性能优化
 *
 * @author Ingenio Team
 * @since 2.0.0 Phase 1-6
 */
@Service
public class ValidationService {

    private static final Logger log = LoggerFactory.getLogger(ValidationService.class);

    private final ValidationResultMapper validationResultMapper;
    private final AppSpecMapper appSpecMapper;

    // Phase 1: 编译验证集成依赖
    private final CompilationValidator compilationValidator;
    private final com.ingenio.backend.service.adapter.ValidationRequestAdapter validationRequestAdapter;
    private final com.ingenio.backend.service.adapter.ValidationResultAdapter validationResultAdapter;

    // Phase 2: 测试执行集成依赖
    private final TestExecutor testExecutor;

    // Phase 3: 覆盖率计算集成依赖
    private final CoverageCalculator coverageCalculator;

    // Phase 4: 线程池用于并行验证（Spring管理，自动优雅关闭）
    @Autowired
    @Qualifier("validationExecutor")
    private Executor validationExecutor;

    public ValidationService(ValidationResultMapper validationResultMapper, AppSpecMapper appSpecMapper,
            CompilationValidator compilationValidator,
            com.ingenio.backend.service.adapter.ValidationRequestAdapter validationRequestAdapter,
            com.ingenio.backend.service.adapter.ValidationResultAdapter validationResultAdapter,
            TestExecutor testExecutor, CoverageCalculator coverageCalculator) {
        this.validationResultMapper = validationResultMapper;
        this.appSpecMapper = appSpecMapper;
        this.compilationValidator = compilationValidator;
        this.validationRequestAdapter = validationRequestAdapter;
        this.validationResultAdapter = validationResultAdapter;
        this.testExecutor = testExecutor;
        this.coverageCalculator = coverageCalculator;
    }

    /**
     * 编译验证（Phase 1重构版：委托CompilationValidator）
     *
     * 执行流程：
     * 1. 适配器转换请求：CompileValidationRequest → CodeGenerationResult
     * 2. 委托CompilationValidator执行真实编译
     * 3. 适配器转换结果：CompilationResult → ValidationResponse
     * 4. 保存验证记录到数据库
     *
     * @param request 编译验证请求
     * @return 验证响应
     */
    @Transactional(rollbackFor = Exception.class)
    public ValidationResponse validateCompile(CompileValidationRequest request) {
        log.info("开始编译验证（Phase 1重构版） - appSpecId: {}, language: {}",
                request.getAppSpecId(), request.getLanguage());

        Instant startTime = Instant.now();

        try {
            // Step 1: 适配器转换请求（CompileValidationRequest → CodeGenerationResult）
            log.debug("Step 1/4: 适配器转换请求");
            com.ingenio.backend.dto.CodeGenerationResult codeResult = validationRequestAdapter
                    .toCodeGenerationResult(request);

            // Step 2: 委托CompilationValidator执行真实编译
            log.debug("Step 2/4: 委托CompilationValidator执行真实编译");
            com.ingenio.backend.dto.CompilationResult compilationResult = compilationValidator.compile(codeResult);

            // Step 3: 适配器转换结果（CompilationResult → ValidationResponse）
            log.debug("Step 3/4: 适配器转换结果");
            ValidationResponse response = validationResultAdapter.toValidationResponse(
                    compilationResult,
                    request.getAppSpecId());

            // Step 4: 保存验证记录到数据库
            log.debug("Step 4/4: 保存验证记录到数据库");
            UUID tenantId = getTenantIdFromAppSpec(request.getAppSpecId());
            saveValidationResult(
                    request.getAppSpecId(),
                    tenantId,
                    response,
                    startTime,
                    ValidationResultEntity.ValidationType.COMPILE);

            log.info("编译验证完成 - success: {}, errors: {}, warnings: {}, qualityScore: {}, durationMs: {}",
                    response.getPassed(),
                    response.getErrors().size(),
                    response.getWarnings().size(),
                    response.getQualityScore(),
                    response.getDurationMs());

            return response;

        } catch (Exception e) {
            log.error("编译验证失败", e);
            throw new RuntimeException("编译验证失败: " + e.getMessage(), e);
        }
    }

    /**
     * 保存验证结果到数据库
     *
     * @param appSpecId      应用规格ID
     * @param tenantId       租户ID（用于多租户隔离）
     * @param response       验证响应
     * @param startTime      开始时间
     * @param validationType 验证类型（compile/test/coverage等）
     */
    private void saveValidationResult(
            UUID appSpecId,
            UUID tenantId,
            ValidationResponse response,
            Instant startTime,
            ValidationResultEntity.ValidationType validationType) {
        ValidationResultEntity entity = ValidationResultEntity.builder()
                .id(response.getValidationId())
                .appSpecId(appSpecId)
                .tenantId(tenantId)
                .validationType(validationType.getValue())
                .status(response.getPassed() ? ValidationResultEntity.Status.PASSED.getValue()
                        : ValidationResultEntity.Status.FAILED.getValue())
                .isPassed(response.getPassed())
                .validationDetails(response.getDetails())
                .errorMessages(response.getErrors())
                .warningMessages(response.getWarnings())
                .qualityScore(response.getQualityScore())
                .startedAt(startTime)
                .completedAt(response.getCompletedAt())
                .durationMs(response.getDurationMs())
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        validationResultMapper.insert(entity);

        log.debug("验证结果已保存: validationId={}, tenantId={}, validationType={}, passed={}",
                entity.getId(), entity.getTenantId(), entity.getValidationType(), entity.getIsPassed());
    }

    /**
     * 从 AppSpec 中获取租户ID
     *
     * @param appSpecId 应用规格ID
     * @return 租户ID（如果找不到AppSpec则返回null）
     */
    private UUID getTenantIdFromAppSpec(UUID appSpecId) {
        if (appSpecId == null) {
            log.warn("appSpecId 为空，无法获取 tenantId");
            return null;
        }
        try {
            AppSpecEntity appSpec = appSpecMapper.selectById(appSpecId);
            if (appSpec != null) {
                return appSpec.getTenantId();
            }
            log.warn("未找到 AppSpec: appSpecId={}", appSpecId);
            return null;
        } catch (Exception e) {
            log.error("获取 AppSpec tenantId 失败: appSpecId={}", appSpecId, e);
            return null;
        }
    }

    /**
     * 测试验证（Phase 2重构版：委托TestExecutor）
     *
     * 执行流程：
     * 1. 适配器转换TestValidationRequest → CodeGenerationResult
     * 2. 委托TestExecutor根据testType执行真实测试（unit/integration/e2e）
     * 3. 适配器转换TestResult → ValidationResponse
     * 4. 保存验证记录到数据库
     *
     * @param request 测试验证请求
     * @return 验证响应
     */
    @Transactional(rollbackFor = Exception.class)
    public ValidationResponse validateTest(TestValidationRequest request) {
        log.info("开始测试验证（Phase 2重构版） - appSpecId: {}, testType: {}",
                request.getAppSpecId(), request.getTestType());

        Instant startTime = Instant.now();

        try {
            // Step 1: 适配器转换测试请求
            com.ingenio.backend.dto.CodeGenerationResult codeResult = validationRequestAdapter
                    .toCodeGenerationResultForTest(request);
            log.debug("适配器转换完成 - projectType: {}, projectRoot: {}",
                    codeResult.getProjectType(), codeResult.getProjectRoot());

            // Step 2: 委托TestExecutor根据testType执行真实测试
            com.ingenio.backend.dto.TestResult testResult = switch (request.getTestType().toLowerCase()) {
                case "unit" -> {
                    log.info("执行单元测试 - projectType: {}", codeResult.getProjectType());
                    yield testExecutor.runUnitTests(codeResult);
                }
                case "integration" -> {
                    log.info("执行集成测试 - projectType: {}", codeResult.getProjectType());
                    // 集成测试复用单元测试执行器（或后续可扩展独立的集成测试执行器）
                    yield testExecutor.runUnitTests(codeResult);
                }
                case "e2e" -> {
                    log.info("执行E2E测试 - projectType: {}", codeResult.getProjectType());
                    yield testExecutor.runE2ETests(codeResult);
                }
                default -> throw new IllegalArgumentException(
                        "不支持的测试类型: " + request.getTestType() +
                                "，支持的类型: unit, integration, e2e");
            };

            log.debug("测试执行完成 - allPassed: {}, totalTests: {}, coverage: {}",
                    testResult.getAllPassed(), testResult.getTotalTests(), testResult.getCoverage());

            // Step 3: 适配器转换测试结果
            ValidationResponse response = validationResultAdapter.toValidationResponseFromTestResult(
                    testResult, request.getAppSpecId());

            // Step 4: 保存验证记录到数据库
            UUID tenantId = getTenantIdFromAppSpec(request.getAppSpecId());
            saveValidationResult(
                    request.getAppSpecId(),
                    tenantId,
                    response,
                    startTime,
                    ValidationResultEntity.ValidationType.TEST);

            log.info("测试验证完成（Phase 2重构版） - passed: {}/{}, coverage: {}, qualityScore: {}, durationMs: {}",
                    testResult.getPassedTests(), testResult.getTotalTests(),
                    testResult.getCoverage() != null ? String.format("%.2f%%", testResult.getCoverage() * 100) : "N/A",
                    response.getQualityScore(), response.getDurationMs());

            return response;

        } catch (IllegalArgumentException e) {
            log.error("测试验证参数错误: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("测试验证失败", e);
            throw new RuntimeException("测试验证失败: " + e.getMessage(), e);
        }
    }

    /**
     * 测试覆盖率验证（Phase 3重构版 - 调用CoverageCalculator）
     *
     * @param appSpecId   AppSpec ID
     * @param projectRoot 项目根目录
     * @param projectType 项目类型（nextjs/spring-boot/kmp）
     * @return 验证响应
     */
    @Transactional(rollbackFor = Exception.class)
    public ValidationResponse validateCoverage(UUID appSpecId, String projectRoot, String projectType) {
        log.info("开始覆盖率验证（Phase 3重构版） - appSpecId: {}, projectRoot: {}, projectType: {}",
                appSpecId, projectRoot, projectType);

        Instant startTime = Instant.now();

        try {
            // Step 1: 委托CoverageCalculator计算真实覆盖率
            com.ingenio.backend.dto.CoverageResult coverageResult = coverageCalculator.calculate(projectRoot,
                    projectType);

            log.info("覆盖率计算完成 - tool: {}, overall: {:.2f}%, meetsQualityGate: {}",
                    coverageResult.getTool(),
                    coverageResult.getOverallCoverage() * 100,
                    coverageResult.getMeetsQualityGate());

            // Step 2: 适配器转换覆盖率结果
            ValidationResponse response = validationResultAdapter.toValidationResponseFromCoverageResult(
                    coverageResult, appSpecId);

            // Step 3: 保存验证记录到数据库
            UUID tenantId = getTenantIdFromAppSpec(appSpecId);
            saveValidationResult(
                    appSpecId,
                    tenantId,
                    response,
                    startTime,
                    ValidationResultEntity.ValidationType.COVERAGE);

            log.info("覆盖率验证完成 - overall: {:.2f}%, line: {:.2f}%, branch: {:.2f}%, qualityScore: {}, durationMs: {}",
                    coverageResult.getOverallCoverage() * 100,
                    coverageResult.getLineCoverage() * 100,
                    coverageResult.getBranchCoverage() * 100,
                    response.getQualityScore(),
                    response.getDurationMs());

            return response;

        } catch (Exception e) {
            log.error("覆盖率验证失败", e);
            throw new RuntimeException("覆盖率验证失败: " + e.getMessage(), e);
        }
    }

    /**
     * 保存外部验证结果（Phase 5: G3引擎集成）
     *
     * 用途：
     * - 允许G3SandboxService等外部系统将验证结果保存到统一的验证结果表
     * - G3在E2B沙箱编译完成后，可以调用此方法保存结果
     * - 实现验证结果的统一存储和查询
     *
     * 设计理念：
     * - G3保持使用E2B沙箱进行编译（不调用validateCompile避免重复编译）
     * - 通过G3ValidationAdapter将CompileResult转换为ValidationResponse
     * - 调用此方法将结果保存到validation_results表
     * - 实现G3和ValidationService的数据格式统一
     *
     * @param appSpecId      应用规格ID
     * @param tenantId       租户ID（用于多租户隔离）
     * @param response       验证响应（由外部系统生成，如G3ValidationAdapter转换的结果）
     * @param validationType 验证类型（compile/test/coverage等）
     */
    @Transactional(rollbackFor = Exception.class)
    public void saveExternalValidationResult(
            UUID appSpecId,
            UUID tenantId,
            ValidationResponse response,
            ValidationResultEntity.ValidationType validationType) {

        log.info("保存外部验证结果（Phase 5） - appSpecId: {}, tenantId: {}, validationType: {}, passed: {}",
                appSpecId, tenantId, validationType, response.getPassed());

        // 计算startTime（从completedAt和durationMs反推）
        Instant startTime = response.getCompletedAt() != null
                ? response.getCompletedAt().minusMillis(response.getDurationMs())
                : Instant.now().minusMillis(response.getDurationMs());

        // 调用私有方法保存结果
        saveValidationResult(appSpecId, tenantId, response, startTime, validationType);

        log.info("外部验证结果已保存 - validationId={}, appSpecId={}, tenantId={}, validationType={}",
                response.getValidationId(), appSpecId, tenantId, validationType);
    }

    /**
     * 质量门禁验证（质量标准检查）
     *
     * 验证规则：
     * 1. 代码覆盖率检查：≥ coverageThreshold（默认85%）
     * 2. 圈复杂度检查：≤ complexityThreshold（默认10）
     * 3. 代码重复率检查：≤ 5%
     *
     * 通过标准（必须全部满足）：
     * - coveragePassed = true
     * - complexityPassed = true
     * - duplicationPassed = true
     *
     * 质量评分计算（0-100）：
     * - 全部通过：100分
     * - 部分通过：根据通过项数量计算（通过数/总数 * 100）
     *
     * @param request 质量门禁请求（包含metrics、coverageThreshold、complexityThreshold）
     * @return 验证响应（包含通过状态、质量评分、详细检查结果）
     */
    @Transactional(rollbackFor = Exception.class)
    public ValidationResponse validateQualityGate(QualityGateRequest request) {
        log.info("开始质量门禁验证 - appSpecId: {}", request.getAppSpecId());

        Instant startTime = Instant.now();

        try {
            Map<String, Integer> metrics = request.getMetrics();
            Integer coverage = metrics.getOrDefault("coverage", 0);
            Integer complexity = metrics.getOrDefault("complexity", 0);
            Integer duplication = metrics.getOrDefault("duplication", 0);

            // 质量门禁规则
            boolean coveragePassed = coverage >= request.getCoverageThreshold();
            boolean complexityPassed = complexity <= request.getComplexityThreshold();
            boolean duplicationPassed = duplication <= 5; // 代码重复率≤5%

            boolean allPassed = coveragePassed && complexityPassed && duplicationPassed;

            List<String> errors = new ArrayList<>();
            if (!coveragePassed) {
                errors.add(String.format("覆盖率不达标: %d%% < %d%%", coverage, request.getCoverageThreshold()));
            }
            if (!complexityPassed) {
                errors.add(String.format("圈复杂度过高: %d > %d", complexity, request.getComplexityThreshold()));
            }
            if (!duplicationPassed) {
                errors.add(String.format("代码重复率过高: %d%% > 5%%", duplication));
            }

            Map<String, Object> details = new HashMap<>();
            details.put("coverage", coverage);
            details.put("complexity", complexity);
            details.put("duplication", duplication);
            details.put("coverageThreshold", request.getCoverageThreshold());
            details.put("complexityThreshold", request.getComplexityThreshold());
            details.put("coveragePassed", coveragePassed);
            details.put("complexityPassed", complexityPassed);
            details.put("duplicationPassed", duplicationPassed);

            Instant endTime = Instant.now();
            long durationMs = endTime.toEpochMilli() - startTime.toEpochMilli();
            UUID tenantId = getTenantIdFromAppSpec(request.getAppSpecId());

            ValidationResultEntity entity = ValidationResultEntity.builder()
                    .id(UUID.randomUUID())
                    .appSpecId(request.getAppSpecId())
                    .tenantId(tenantId)
                    .validationType(ValidationResultEntity.ValidationType.QUALITY_GATE.getValue())
                    .status(allPassed ? ValidationResultEntity.Status.PASSED.getValue()
                            : ValidationResultEntity.Status.FAILED.getValue())
                    .isPassed(allPassed)
                    .validationDetails(details)
                    .errorMessages(errors)
                    .warningMessages(new ArrayList<>())
                    .qualityScore(coverage) // 使用覆盖率作为质量评分
                    .startedAt(startTime)
                    .completedAt(endTime)
                    .durationMs(durationMs)
                    .createdAt(Instant.now())
                    .updatedAt(Instant.now())
                    .build();

            validationResultMapper.insert(entity);

            log.info("质量门禁验证完成 - passed: {}, coverage: {}%, complexity: {}, tenantId: {}, durationMs: {}",
                    allPassed, coverage, complexity, tenantId, durationMs);

            return ValidationResponse.builder()
                    .validationId(entity.getId())
                    .validationType("quality_gate")
                    .passed(allPassed)
                    .status(entity.getStatus())
                    .qualityScore(entity.getQualityScore())
                    .details(details)
                    .errors(errors)
                    .warnings(new ArrayList<>())
                    .durationMs(durationMs)
                    .completedAt(endTime)
                    .build();

        } catch (Exception e) {
            log.error("质量门禁验证失败", e);
            throw new RuntimeException("质量门禁验证失败: " + e.getMessage(), e);
        }
    }

    /**
     * API契约验证（基础版本）
     *
     * 当前实现范围：
     * - 验证OpenAPI规范基本格式（包含openapi版本和paths定义）
     * - 统计API端点数量
     * - 保存验证结果到数据库
     *
     * 未来扩展方向：
     * - 集成Swagger Parser进行深度规范验证
     * - 验证请求/响应Schema完整性
     * - 检查API路径命名规范
     * - 验证HTTP方法使用合理性
     *
     * @param appSpecId   AppSpec ID
     * @param openApiSpec OpenAPI规范
     * @return 验证响应
     */
    @Transactional(rollbackFor = Exception.class)
    public ValidationResponse validateContract(UUID appSpecId, Map<String, Object> openApiSpec) {
        log.info("开始API契约验证 - appSpecId: {}", appSpecId);

        Instant startTime = Instant.now();

        try {
            // 基础OpenAPI规范格式验证
            boolean isValid = openApiSpec != null
                    && openApiSpec.containsKey("openapi")
                    && openApiSpec.containsKey("paths");

            List<String> errors = new ArrayList<>();
            if (!isValid) {
                errors.add("OpenAPI规范格式不正确");
            }

            Map<String, Object> details = new HashMap<>();
            details.put("openApiVersion", openApiSpec.get("openapi"));
            details.put("pathCount", openApiSpec.containsKey("paths")
                    ? ((Map<?, ?>) openApiSpec.get("paths")).size()
                    : 0);

            Instant endTime = Instant.now();
            long durationMs = endTime.toEpochMilli() - startTime.toEpochMilli();
            UUID tenantId = getTenantIdFromAppSpec(appSpecId);

            ValidationResultEntity entity = ValidationResultEntity.builder()
                    .id(UUID.randomUUID())
                    .appSpecId(appSpecId)
                    .tenantId(tenantId)
                    .validationType(ValidationResultEntity.ValidationType.CONTRACT.getValue())
                    .status(isValid ? ValidationResultEntity.Status.PASSED.getValue()
                            : ValidationResultEntity.Status.FAILED.getValue())
                    .isPassed(isValid)
                    .validationDetails(details)
                    .errorMessages(errors)
                    .warningMessages(new ArrayList<>())
                    .qualityScore(isValid ? 100 : 0)
                    .startedAt(startTime)
                    .completedAt(endTime)
                    .durationMs(durationMs)
                    .createdAt(Instant.now())
                    .updatedAt(Instant.now())
                    .build();

            validationResultMapper.insert(entity);

            log.info("API契约验证完成 - valid: {}, tenantId: {}, durationMs: {}", isValid, tenantId, durationMs);

            return ValidationResponse.builder()
                    .validationId(entity.getId())
                    .validationType("contract")
                    .passed(isValid)
                    .status(entity.getStatus())
                    .qualityScore(entity.getQualityScore())
                    .details(details)
                    .errors(errors)
                    .warnings(new ArrayList<>())
                    .durationMs(durationMs)
                    .completedAt(endTime)
                    .build();

        } catch (Exception e) {
            log.error("API契约验证失败", e);
            throw new RuntimeException("API契约验证失败: " + e.getMessage(), e);
        }
    }

    /**
     * 数据库Schema验证（基础版本）
     *
     * 当前实现范围：
     * - 验证Schema定义非空
     * - 统计数据库表数量
     * - 保存验证结果到数据库
     *
     * 未来扩展方向：
     * - 验证表结构完整性（字段类型、约束、索引）
     * - 检查主外键关系一致性
     * - 验证Schema命名规范（表名、字段名）
     * - 检查数据库迁移脚本SQL语法
     * - 验证Schema与API契约的匹配度
     *
     * @param appSpecId AppSpec ID
     * @param schema    Schema定义
     * @return 验证响应
     */
    @Transactional(rollbackFor = Exception.class)
    public ValidationResponse validateSchema(UUID appSpecId, Map<String, Object> schema) {
        log.info("开始Schema验证 - appSpecId: {}", appSpecId);

        Instant startTime = Instant.now();

        try {
            // 基础Schema格式验证
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> tables = (List<Map<String, Object>>) schema.get("tables");
            boolean isValid = tables != null && !tables.isEmpty();

            List<String> errors = new ArrayList<>();
            if (!isValid) {
                errors.add("Schema定义为空");
            }

            Map<String, Object> details = new HashMap<>();
            details.put("tableCount", tables != null ? tables.size() : 0);

            Instant endTime = Instant.now();
            long durationMs = endTime.toEpochMilli() - startTime.toEpochMilli();
            UUID tenantId = getTenantIdFromAppSpec(appSpecId);

            ValidationResultEntity entity = ValidationResultEntity.builder()
                    .id(UUID.randomUUID())
                    .appSpecId(appSpecId)
                    .tenantId(tenantId)
                    .validationType(ValidationResultEntity.ValidationType.SCHEMA.getValue())
                    .status(isValid ? ValidationResultEntity.Status.PASSED.getValue()
                            : ValidationResultEntity.Status.FAILED.getValue())
                    .isPassed(isValid)
                    .validationDetails(details)
                    .errorMessages(errors)
                    .warningMessages(new ArrayList<>())
                    .qualityScore(isValid ? 100 : 0)
                    .startedAt(startTime)
                    .completedAt(endTime)
                    .durationMs(durationMs)
                    .createdAt(Instant.now())
                    .updatedAt(Instant.now())
                    .build();

            validationResultMapper.insert(entity);

            log.info("Schema验证完成 - valid: {}, tables: {}, tenantId: {}, durationMs: {}", isValid,
                    details.get("tableCount"), tenantId, durationMs);

            return ValidationResponse.builder()
                    .validationId(entity.getId())
                    .validationType("schema")
                    .passed(isValid)
                    .status(entity.getStatus())
                    .qualityScore(entity.getQualityScore())
                    .details(details)
                    .errors(errors)
                    .warnings(new ArrayList<>())
                    .durationMs(durationMs)
                    .completedAt(endTime)
                    .build();

        } catch (Exception e) {
            log.error("Schema验证失败", e);
            throw new RuntimeException("Schema验证失败: " + e.getMessage(), e);
        }
    }

    /**
     * 业务流程验证（基础版本）
     *
     * 当前实现范围：
     * - 验证业务流程定义非空
     * - 统计业务流程数量
     * - 保存验证结果到数据库
     *
     * 未来扩展方向：
     * - 验证业务流程逻辑完整性（开始节点、结束节点、路径可达性）
     * - 检查业务规则一致性
     * - 验证用户故事覆盖度
     * - 检查业务流程与API契约的匹配度
     * - 验证权限和角色定义合理性
     *
     * @param appSpecId AppSpec ID
     * @param flows     业务流程列表
     * @return 验证响应
     */
    @Transactional(rollbackFor = Exception.class)
    public ValidationResponse validateBusinessFlow(UUID appSpecId, List<Map<String, Object>> flows) {
        log.info("开始业务流程验证 - appSpecId: {}", appSpecId);

        Instant startTime = Instant.now();

        try {
            // 基础业务流程格式验证
            boolean isValid = flows != null && !flows.isEmpty();

            List<String> errors = new ArrayList<>();
            if (!isValid) {
                errors.add("业务流程定义为空");
            }

            Map<String, Object> details = new HashMap<>();
            details.put("flowCount", flows != null ? flows.size() : 0);

            Instant endTime = Instant.now();
            long durationMs = endTime.toEpochMilli() - startTime.toEpochMilli();
            UUID tenantId = getTenantIdFromAppSpec(appSpecId);

            ValidationResultEntity entity = ValidationResultEntity.builder()
                    .id(UUID.randomUUID())
                    .appSpecId(appSpecId)
                    .tenantId(tenantId)
                    .validationType(ValidationResultEntity.ValidationType.BUSINESS_FLOW.getValue())
                    .status(isValid ? ValidationResultEntity.Status.PASSED.getValue()
                            : ValidationResultEntity.Status.FAILED.getValue())
                    .isPassed(isValid)
                    .validationDetails(details)
                    .errorMessages(errors)
                    .warningMessages(new ArrayList<>())
                    .qualityScore(isValid ? 100 : 0)
                    .startedAt(startTime)
                    .completedAt(endTime)
                    .durationMs(durationMs)
                    .createdAt(Instant.now())
                    .updatedAt(Instant.now())
                    .build();

            validationResultMapper.insert(entity);

            log.info("业务流程验证完成 - valid: {}, flows: {}, tenantId: {}, durationMs: {}", isValid, details.get("flowCount"),
                    tenantId, durationMs);

            return ValidationResponse.builder()
                    .validationId(entity.getId())
                    .validationType("business_flow")
                    .passed(isValid)
                    .status(entity.getStatus())
                    .qualityScore(entity.getQualityScore())
                    .details(details)
                    .errors(errors)
                    .warnings(new ArrayList<>())
                    .durationMs(durationMs)
                    .completedAt(endTime)
                    .build();

        } catch (Exception e) {
            log.error("业务流程验证失败", e);
            throw new RuntimeException("业务流程验证失败: " + e.getMessage(), e);
        }
    }

    /**
     * 三环集成验证（Phase 4重构版 - 支持串行/并行模式）
     *
     * 验证流程：
     * 1. 根据request.parallel选择执行模式（串行/并行）
     * 2. 依次或并行执行各个阶段的验证（compile/test/coverage/business）
     * 3. 聚合所有阶段的验证结果
     * 4. 计算整体状态和质量评分
     *
     * 支持的验证阶段：
     * - compile: 编译验证（委托CompilationValidator）
     * - test: 测试验证（委托TestExecutor）
     * - coverage: 覆盖率验证（委托CoverageCalculator）
     * - business: 业务验证（基础版 - API契约/Schema/业务流程）
     *
     * 执行模式：
     * - parallel=true: 并行验证（使用validationExecutor线程池，速度快但资源消耗高）
     * - parallel=false: 串行验证（支持failFast，某阶段失败立即返回）
     *
     * failFast机制（仅串行模式）：
     * - failFast=true: 某阶段失败立即终止后续验证
     * - failFast=false: 执行所有阶段后返回完整结果
     *
     * 整体评分计算：
     * - 所有阶段质量评分的平均值（0-100）
     * - 所有阶段通过则overallStatus="passed"，否则"failed"
     *
     * @param request 全量验证请求（包含appSpecId、stages、parallel、failFast等参数）
     * @return 全量验证响应（包含各阶段结果、整体状态、总评分、总耗时）
     */
    @Transactional(rollbackFor = Exception.class)
    public FullValidationResponse validateFull(FullValidationRequest request) {
        log.info("开始三环集成验证 - appSpecId: {}, stages: {}, parallel: {}",
                request.getAppSpecId(), request.getStages(), request.getParallel());

        Instant startTime = Instant.now();
        Map<String, FullValidationResponse.StageResult> stageResults = new HashMap<>();

        try {
            if (Boolean.TRUE.equals(request.getParallel())) {
                // 并行验证
                stageResults = validateStagesInParallel(request);
            } else {
                // 串行验证（支持failFast）
                stageResults = validateStagesInSequence(request);
            }

            // 计算整体状态和评分
            boolean allPassed = stageResults.values().stream()
                    .allMatch(result -> Boolean.TRUE.equals(result.getPassed()));
            String overallStatus = allPassed ? "passed" : "failed";

            int overallScore = (int) stageResults.values().stream()
                    .filter(result -> result.getDetails() != null)
                    .mapToInt(result -> {
                        Object scoreObj = result.getDetails().get("qualityScore");
                        return scoreObj != null ? (Integer) scoreObj : 0;
                    })
                    .average()
                    .orElse(0);

            Instant endTime = Instant.now();
            long totalDurationMs = endTime.toEpochMilli() - startTime.toEpochMilli();

            log.info("三环集成验证完成 - status: {}, score: {}, durationMs: {}",
                    overallStatus, overallScore, totalDurationMs);

            return FullValidationResponse.builder()
                    .validationTaskId(UUID.randomUUID())
                    .stages(stageResults)
                    .overallStatus(overallStatus)
                    .overallScore(overallScore)
                    .totalDurationMs(totalDurationMs)
                    .completedAt(endTime)
                    .build();

        } catch (Exception e) {
            log.error("三环集成验证失败", e);
            throw new RuntimeException("三环集成验证失败: " + e.getMessage(), e);
        }
    }

    /**
     * 串行验证（支持failFast快速失败）
     *
     * 执行流程：
     * 1. 按顺序执行各个阶段验证（for循环）
     * 2. 记录每个阶段的执行时间和结果
     * 3. 如果failFast=true且某阶段失败，立即终止后续验证
     * 4. 返回所有已执行阶段的结果Map（key=stage名称，value=StageResult）
     *
     * failFast机制：
     * - failFast=true: 验证失败后break，不执行后续阶段
     * - failFast=false: 继续执行所有阶段，收集完整结果
     *
     * @param request 全量验证请求
     * @return 阶段结果Map（LinkedHashMap保持顺序）
     */
    private Map<String, FullValidationResponse.StageResult> validateStagesInSequence(FullValidationRequest request) {
        Map<String, FullValidationResponse.StageResult> results = new LinkedHashMap<>();

        for (String stage : request.getStages()) {
            Instant stageStart = Instant.now();

            try {
                ValidationResponse validationResponse = executeStageValidation(stage, request);

                Instant stageEnd = Instant.now();
                long stageDuration = stageEnd.toEpochMilli() - stageStart.toEpochMilli();

                results.put(stage, FullValidationResponse.StageResult.builder()
                        .stage(stage)
                        .status(validationResponse.getPassed() ? "passed" : "failed")
                        .passed(validationResponse.getPassed())
                        .errorMessage(validationResponse.getErrors().isEmpty() ? null
                                : String.join("; ", validationResponse.getErrors()))
                        .details(Map.of(
                                "validationId", validationResponse.getValidationId(),
                                "qualityScore", validationResponse.getQualityScore(),
                                "details", validationResponse.getDetails()))
                        .durationMs(stageDuration)
                        .build());

                // 如果failFast且当前阶段失败，跳过后续阶段
                if (Boolean.TRUE.equals(request.getFailFast()) && !validationResponse.getPassed()) {
                    log.warn("阶段{}失败，跳过后续验证 - failFast=true", stage);

                    // 标记剩余阶段为skipped
                    int currentIndex = request.getStages().indexOf(stage);
                    for (int i = currentIndex + 1; i < request.getStages().size(); i++) {
                        String skippedStage = request.getStages().get(i);
                        results.put(skippedStage, FullValidationResponse.StageResult.builder()
                                .stage(skippedStage)
                                .status("skipped")
                                .passed(false)
                                .errorMessage("前置阶段验证失败")
                                .details(new HashMap<>())
                                .durationMs(0L)
                                .build());
                    }
                    break;
                }

            } catch (Exception e) {
                log.error("阶段{}验证异常", stage, e);
                results.put(stage, FullValidationResponse.StageResult.builder()
                        .stage(stage)
                        .status("failed")
                        .passed(false)
                        .errorMessage("验证异常: " + e.getMessage())
                        .details(new HashMap<>())
                        .durationMs(0L)
                        .build());

                if (Boolean.TRUE.equals(request.getFailFast())) {
                    break;
                }
            }
        }

        return results;
    }

    /**
     * 并行验证（使用CompletableFuture和validationExecutor线程池）
     *
     * 执行流程：
     * 1. 为每个阶段创建CompletableFuture异步任务
     * 2. 提交到validationExecutor线程池并行执行
     * 3. 等待所有任务完成（future.get()阻塞等待）
     * 4. 收集所有阶段的验证结果
     *
     * 并行特性：
     * - 所有阶段同时启动，互不阻塞
     * - 不支持failFast（必须等待所有阶段完成）
     * - 总耗时约等于最慢阶段的耗时
     *
     * 异常处理：
     * - 单个阶段异常不影响其他阶段执行
     * - 异常阶段返回failed状态和错误信息
     *
     * 线程池配置：
     * - 使用Spring管理的validationExecutor（自动优雅关闭）
     * - 配置见ValidationConfig类
     *
     * @param request 全量验证请求
     * @return 阶段结果Map（HashMap无序）
     */
    private Map<String, FullValidationResponse.StageResult> validateStagesInParallel(FullValidationRequest request) {
        Map<String, CompletableFuture<FullValidationResponse.StageResult>> futures = new HashMap<>();

        for (String stage : request.getStages()) {
            futures.put(stage, CompletableFuture.supplyAsync(() -> {
                Instant stageStart = Instant.now();

                try {
                    ValidationResponse validationResponse = executeStageValidation(stage, request);

                    Instant stageEnd = Instant.now();
                    long stageDuration = stageEnd.toEpochMilli() - stageStart.toEpochMilli();

                    return FullValidationResponse.StageResult.builder()
                            .stage(stage)
                            .status(validationResponse.getPassed() ? "passed" : "failed")
                            .passed(validationResponse.getPassed())
                            .errorMessage(validationResponse.getErrors().isEmpty() ? null
                                    : String.join("; ", validationResponse.getErrors()))
                            .details(Map.of(
                                    "validationId", validationResponse.getValidationId(),
                                    "qualityScore", validationResponse.getQualityScore(),
                                    "details", validationResponse.getDetails()))
                            .durationMs(stageDuration)
                            .build();

                } catch (Exception e) {
                    log.error("阶段{}并行验证异常", stage, e);
                    return FullValidationResponse.StageResult.builder()
                            .stage(stage)
                            .status("failed")
                            .passed(false)
                            .errorMessage("验证异常: " + e.getMessage())
                            .details(new HashMap<>())
                            .durationMs(0L)
                            .build();
                }
            }, validationExecutor));
        }

        // 等待所有验证完成
        Map<String, FullValidationResponse.StageResult> results = new HashMap<>();
        futures.forEach((stage, future) -> {
            try {
                results.put(stage, future.get());
            } catch (Exception e) {
                log.error("获取阶段{}验证结果失败", stage, e);
                results.put(stage, FullValidationResponse.StageResult.builder()
                        .stage(stage)
                        .status("failed")
                        .passed(false)
                        .errorMessage("获取结果失败: " + e.getMessage())
                        .details(new HashMap<>())
                        .durationMs(0L)
                        .build());
            }
        });

        return results;
    }

    /**
     * 执行单个阶段的验证（阶段路由器）
     *
     * 功能：
     * - 根据stage参数路由到对应的验证方法
     * - 从FullValidationRequest中提取各阶段所需的参数
     * - 调用Phase 1-3重构后的validate方法（委托专业验证器）
     *
     * 支持的阶段及参数要求：
     * 1. compile: 需要code和language参数
     * - 委托CompilationValidator执行真实编译
     * - CompilationValidator自动创建临时项目目录
     *
     * 2. test: 需要testFiles参数（可选）
     * - 委托TestExecutor执行单元测试
     * - generateCoverage默认为true
     *
     * 3. coverage: 需要projectRoot和projectType参数
     * - 委托CoverageCalculator解析覆盖率报告
     * - 支持Istanbul（nextjs）和JaCoCo（spring-boot）
     *
     * 4. business: 无强制参数要求
     * - 当前为基础版实现，仅验证基本格式
     * - 未来需扩展FullValidationRequest添加openApiSpec、schema、flows字段
     *
     * @param stage   验证阶段（compile/test/coverage/business）
     * @param request 全量验证请求（包含各阶段所需的参数）
     * @return 验证响应（包含通过状态、质量评分、错误信息）
     * @throws IllegalArgumentException 当阶段不支持或必需参数缺失时抛出
     */
    private ValidationResponse executeStageValidation(String stage, FullValidationRequest request) {
        log.info("执行验证阶段: {}", stage);

        switch (stage) {
            case "compile":
                // Phase 1重构后的编译验证（委托CompilationValidator）
                // CompilationValidator会自动创建临时项目目录，无需传递projectRoot
                if (request.getCode() != null && request.getLanguage() != null) {
                    CompileValidationRequest compileRequest = CompileValidationRequest.builder()
                            .appSpecId(request.getAppSpecId())
                            .code(request.getCode())
                            .language(request.getLanguage())
                            .build();
                    return validateCompile(compileRequest);
                } else {
                    throw new IllegalArgumentException("compile阶段需要提供code和language参数");
                }

            case "test":
                // Phase 2重构后的测试验证（委托TestExecutor）
                TestValidationRequest testRequest = TestValidationRequest.builder()
                        .appSpecId(request.getAppSpecId())
                        .testType("unit")
                        .testFiles(request.getTestFiles() != null ? request.getTestFiles() : new ArrayList<>())
                        .generateCoverage(true)
                        .build();
                return validateTest(testRequest);

            case "coverage":
                // Phase 3新增：覆盖率验证（委托CoverageCalculator）
                if (request.getProjectRoot() == null || request.getProjectType() == null) {
                    throw new IllegalArgumentException("coverage阶段需要提供projectRoot和projectType参数");
                }
                return validateCoverage(request.getAppSpecId(), request.getProjectRoot(), request.getProjectType());

            case "business":
                // 业务验证链路（基础版本）
                // 当前实现：仅验证API契约基本格式
                // 未来扩展：需要在FullValidationRequest中添加以下字段后实现完整链路
                // - Map<String, Object> openApiSpec (API契约规范)
                // - Map<String, Object> schema (数据库Schema定义)
                // - List<Map<String, Object>> flows (业务流程定义)
                // 完整链路：contract验证 → schema验证 → business_flow验证 → 聚合结果
                log.warn("business验证当前为简化实现，仅验证基本格式。完整实现需扩展FullValidationRequest");
                ValidationResponse contractResult = validateContract(request.getAppSpecId(), new HashMap<>());
                return contractResult;

            default:
                throw new IllegalArgumentException("不支持的验证阶段: " + stage);
        }
    }
}
