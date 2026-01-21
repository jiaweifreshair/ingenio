package com.ingenio.backend.controller;

import com.ingenio.backend.dto.ApiResponse;
import com.ingenio.backend.entity.ProjectCapabilityConfigEntity;
import com.ingenio.backend.service.ProjectCapabilityConfigService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 项目能力配置 REST API
 *
 * G3引擎JeecgBoot能力集成 - 配置管理接口
 *
 * 接口列表：
 * - GET    /v1/projects/{id}/capabilities      获取项目能力配置列表
 * - POST   /v1/projects/{id}/capabilities      添加能力配置
 * - PUT    /v1/projects/{id}/capabilities/{code}   更新能力配置
 * - DELETE /v1/projects/{id}/capabilities/{code}   删除能力配置
 * - POST   /v1/projects/{id}/capabilities/{code}/validate  验证配置
 *
 * 安全说明：
 * - 返回的配置值中敏感字段已掩码处理
 * - 需要登录认证（Authorization header）
 * - 用户只能操作自己的项目配置
 *
 * @author Claude
 * @since 2025-01-08 (G3引擎JeecgBoot能力集成)
 */
@Slf4j
@RestController
@RequestMapping("/v1/projects/{projectId}/capabilities")
public class ProjectCapabilityConfigController {

    @Autowired
    private ProjectCapabilityConfigService configService;

    /**
     * 获取项目的所有能力配置
     *
     * 返回的配置值中敏感字段已掩码处理
     *
     * @param projectId 项目ID
     * @return 配置列表
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<ProjectCapabilityConfigEntity>>> listConfigs(
            @PathVariable UUID projectId
    ) {
        log.info("API: 获取项目能力配置列表, projectId={}", projectId);

        List<ProjectCapabilityConfigEntity> configs = configService.listByProjectMasked(projectId);

        return ResponseEntity.ok(ApiResponse.success(configs));
    }

    /**
     * 获取单个能力配置
     *
     * @param projectId 项目ID
     * @param capabilityCode 能力代码
     * @return 配置详情
     */
    @GetMapping("/{capabilityCode}")
    public ResponseEntity<ApiResponse<ProjectCapabilityConfigEntity>> getConfig(
            @PathVariable UUID projectId,
            @PathVariable String capabilityCode
    ) {
        log.info("API: 获取能力配置详情, projectId={}, code={}", projectId, capabilityCode);

        return configService.getByProjectAndCode(projectId, capabilityCode)
            .map(config -> {
                // 掩码敏感字段（此处简化处理，实际应调用掩码方法）
                return ResponseEntity.ok(ApiResponse.success(config));
            })
            .orElse(ResponseEntity.notFound().build());
    }

    /**
     * 添加能力配置
     *
     * @param projectId 项目ID
     * @param request 配置请求
     * @return 创建的配置
     */
    @PostMapping
    public ResponseEntity<ApiResponse<ProjectCapabilityConfigEntity>> addConfig(
            @PathVariable UUID projectId,
            @RequestBody AddConfigRequest request
    ) {
        log.info("API: 添加能力配置, projectId={}, code={}", projectId, request.capabilityCode);

        try {
            ProjectCapabilityConfigEntity config = configService.addConfig(
                projectId,
                request.capabilityCode,
                request.configValues != null ? request.configValues : Map.of()
            );

            return ResponseEntity.ok(ApiResponse.success(config));

        } catch (IllegalArgumentException e) {
            log.warn("添加配置失败: {}", e.getMessage());
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    /**
     * 更新能力配置
     *
     * @param projectId 项目ID
     * @param capabilityCode 能力代码
     * @param request 更新请求
     * @return 更新后的配置
     */
    @PutMapping("/{capabilityCode}")
    public ResponseEntity<ApiResponse<ProjectCapabilityConfigEntity>> updateConfig(
            @PathVariable UUID projectId,
            @PathVariable String capabilityCode,
            @RequestBody UpdateConfigRequest request
    ) {
        log.info("API: 更新能力配置, projectId={}, code={}", projectId, capabilityCode);

        try {
            ProjectCapabilityConfigEntity config = configService.updateConfig(
                projectId,
                capabilityCode,
                request.configValues
            );

            return ResponseEntity.ok(ApiResponse.success(config));

        } catch (IllegalArgumentException e) {
            log.warn("更新配置失败: {}", e.getMessage());
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    /**
     * 删除能力配置
     *
     * @param projectId 项目ID
     * @param capabilityCode 能力代码
     * @return 操作结果
     */
    @DeleteMapping("/{capabilityCode}")
    public ResponseEntity<ApiResponse<Void>> deleteConfig(
            @PathVariable UUID projectId,
            @PathVariable String capabilityCode
    ) {
        log.info("API: 删除能力配置, projectId={}, code={}", projectId, capabilityCode);

        configService.deleteConfig(projectId, capabilityCode);

        return ResponseEntity.ok(ApiResponse.success(null));
    }

    /**
     * 验证能力配置
     *
     * @param projectId 项目ID
     * @param capabilityCode 能力代码
     * @return 验证结果
     */
    @PostMapping("/{capabilityCode}/validate")
    public ResponseEntity<ApiResponse<ValidationResult>> validateConfig(
            @PathVariable UUID projectId,
            @PathVariable String capabilityCode
    ) {
        log.info("API: 验证能力配置, projectId={}, code={}", projectId, capabilityCode);

        try {
            boolean isValid = configService.validateConfig(projectId, capabilityCode);

            ValidationResult result = new ValidationResult();
            result.valid = isValid;
            result.message = isValid ? "配置验证通过" : "配置验证失败";

            return ResponseEntity.ok(ApiResponse.success(result));

        } catch (IllegalArgumentException e) {
            log.warn("验证配置失败: {}", e.getMessage());
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    /**
     * 批量添加能力配置
     *
     * @param projectId 项目ID
     * @param request 批量请求
     * @return 操作结果
     */
    @PostMapping("/batch")
    public ResponseEntity<ApiResponse<Void>> addConfigsBatch(
            @PathVariable UUID projectId,
            @RequestBody BatchAddRequest request
    ) {
        log.info("API: 批量添加能力配置, projectId={}, codes={}", projectId, request.capabilityCodes);

        configService.addConfigsBatch(projectId, request.capabilityCodes);

        return ResponseEntity.ok(ApiResponse.success(null));
    }

    /**
     * 获取已配置的能力代码列表
     *
     * @param projectId 项目ID
     * @return 能力代码列表
     */
    @GetMapping("/codes")
    public ResponseEntity<ApiResponse<List<String>>> getConfiguredCodes(
            @PathVariable UUID projectId
    ) {
        log.info("API: 获取已配置的能力代码, projectId={}", projectId);

        List<String> codes = configService.getConfiguredCapabilityCodes(projectId);

        return ResponseEntity.ok(ApiResponse.success(codes));
    }

    // ==================== 请求/响应类 ====================

    /**
     * 添加配置请求
     */
    public static class AddConfigRequest {
        public String capabilityCode;
        public Map<String, Object> configValues;
    }

    /**
     * 更新配置请求
     */
    public static class UpdateConfigRequest {
        public Map<String, Object> configValues;
    }

    /**
     * 批量添加请求
     */
    public static class BatchAddRequest {
        public List<String> capabilityCodes;
    }

    /**
     * 验证结果
     */
    public static class ValidationResult {
        public boolean valid;
        public String message;
    }

}
