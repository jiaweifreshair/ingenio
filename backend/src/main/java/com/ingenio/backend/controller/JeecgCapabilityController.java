package com.ingenio.backend.controller;

import com.ingenio.backend.dto.ApiResponse;
import com.ingenio.backend.entity.JeecgCapabilityEntity;
import com.ingenio.backend.service.JeecgCapabilityService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * JeecgBoot能力清单控制器
 */
@RestController
@RequestMapping("/v1/jeecg/capabilities")
@Tag(name = "JeecgBoot能力管理", description = "JeecgBoot平台能力清单管理接口")
public class JeecgCapabilityController {

    private static final Logger log = LoggerFactory.getLogger(JeecgCapabilityController.class);

    @Autowired
    private JeecgCapabilityService capabilityService;

    @GetMapping
    @Operation(summary = "获取能力列表", description = "获取所有启用的JeecgBoot能力，按排序权重升序")
    public ResponseEntity<ApiResponse<List<JeecgCapabilityEntity>>> listAll() {
        log.info("API: 获取所有JeecgBoot能力列表");

        List<JeecgCapabilityEntity> capabilities = capabilityService.listAllActive();
        return ResponseEntity.ok(ApiResponse.success(capabilities));
    }

    @GetMapping("/{code}")
    @Operation(summary = "获取能力详情", description = "根据能力代码获取单个能力的详细信息")
    public ResponseEntity<ApiResponse<JeecgCapabilityEntity>> getByCode(
            @Parameter(description = "能力代码，如：auth, payment_alipay") @PathVariable String code) {
        log.info("API: 获取JeecgBoot能力详情, code={}", code);

        Optional<JeecgCapabilityEntity> capabilityOpt = capabilityService.getByCode(code);

        if (capabilityOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(ApiResponse.success(capabilityOpt.get()));
    }

    @GetMapping("/category/{category}")
    @Operation(summary = "按分类获取能力", description = "根据分类筛选能力：infrastructure/business/third_party")
    public ResponseEntity<ApiResponse<List<JeecgCapabilityEntity>>> listByCategory(
            @Parameter(description = "能力分类：infrastructure(基础设施)/business(业务能力)/third_party(第三方集成)") @PathVariable String category) {
        log.info("API: 按分类获取JeecgBoot能力, category={}", category);

        List<JeecgCapabilityEntity> capabilities = capabilityService.listByCategory(category);
        return ResponseEntity.ok(ApiResponse.success(capabilities));
    }

    @GetMapping("/{code}/dependencies")
    @Operation(summary = "获取能力依赖", description = "获取指定能力及其所有依赖的能力列表")
    public ResponseEntity<ApiResponse<List<JeecgCapabilityEntity>>> getWithDependencies(
            @Parameter(description = "能力代码") @PathVariable String code) {
        log.info("API: 获取JeecgBoot能力依赖, code={}", code);

        List<JeecgCapabilityEntity> capabilities = capabilityService.getWithDependencies(code);

        if (capabilities.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(ApiResponse.success(capabilities));
    }

    @PostMapping("/check-conflicts")
    @Operation(summary = "检查能力冲突", description = "检查新选择的能力是否与已选择的能力存在冲突")
    public ResponseEntity<ApiResponse<ConflictCheckResult>> checkConflicts(
            @RequestBody ConflictCheckRequest request) {
        log.info("API: 检查JeecgBoot能力冲突, selectedCodes={}, newCode={}",
                request.selectedCodes, request.newCode);

        List<String> conflicts = capabilityService.checkConflicts(
                request.selectedCodes,
                request.newCode);

        ConflictCheckResult result = new ConflictCheckResult();
        result.hasConflict = !conflicts.isEmpty();
        result.conflictingCodes = conflicts;

        return ResponseEntity.ok(ApiResponse.success(result));
    }

    @GetMapping("/statistics")
    @Operation(summary = "获取能力统计", description = "获取各分类的能力数量统计")
    public ResponseEntity<ApiResponse<Map<String, Long>>> getStatistics() {
        log.info("API: 获取JeecgBoot能力统计");

        Map<String, Long> statistics = capabilityService.getStatistics();
        return ResponseEntity.ok(ApiResponse.success(statistics));
    }

    @PostMapping("/batch")
    @Operation(summary = "批量获取能力", description = "根据能力代码列表批量获取能力详情")
    public ResponseEntity<ApiResponse<List<JeecgCapabilityEntity>>> listByCodes(
            @RequestBody List<String> codes) {
        log.info("API: 批量获取JeecgBoot能力, codes={}", codes);

        List<JeecgCapabilityEntity> capabilities = capabilityService.listByCodes(codes);
        return ResponseEntity.ok(ApiResponse.success(capabilities));
    }

    public static class ConflictCheckRequest {
        public List<String> selectedCodes;
        public String newCode;
    }

    public static class ConflictCheckResult {
        public boolean hasConflict;
        public List<String> conflictingCodes;
    }
}
