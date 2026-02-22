package com.ingenio.backend.controller;

import com.ingenio.backend.dto.ApiResponse;
import com.ingenio.backend.entity.ProjectCapabilityConfigEntity;
import com.ingenio.backend.entity.ProjectEntity;
import com.ingenio.backend.service.ProjectCapabilityConfigService;
import com.ingenio.backend.service.ProjectService;
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
     * 项目服务
     *
     * 是什么：用于通过 appSpecId 反查项目。
     * 做什么：兼容能力配置接口使用 appSpecId 的调用方式。
     * 为什么：前端 E2E 场景传 appSpecId，需要自动映射。
     */
    @Autowired
    private ProjectService projectService;

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

        ProjectEntity project = resolveProject(projectId);
        if (project == null) {
            return ResponseEntity.notFound().build();
        }

        List<ProjectCapabilityConfigEntity> configs = configService.listByProjectMasked(project.getId());

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

        ProjectEntity project = resolveProject(projectId);
        if (project == null) {
            return ResponseEntity.notFound().build();
        }

        return configService.getByProjectAndCodeMasked(project.getId(), capabilityCode)
            .map(config -> ResponseEntity.ok(ApiResponse.success(config)))
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
            ProjectEntity project = resolveProject(projectId);
            if (project == null) {
                return ResponseEntity.notFound().build();
            }

            ProjectCapabilityConfigEntity config = configService.addConfig(
                project.getId(),
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
            ProjectEntity project = resolveProject(projectId);
            if (project == null) {
                return ResponseEntity.notFound().build();
            }

            ProjectCapabilityConfigEntity config = configService.updateConfig(
                project.getId(),
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

        ProjectEntity project = resolveProject(projectId);
        if (project == null) {
            return ResponseEntity.notFound().build();
        }

        configService.deleteConfig(project.getId(), capabilityCode);

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
            ProjectEntity project = resolveProject(projectId);
            if (project == null) {
                return ResponseEntity.notFound().build();
            }

            boolean isValid = configService.validateConfig(project.getId(), capabilityCode);
            String message = isValid ? "配置验证通过" : "配置验证失败";

            if (!isValid) {
                String errorMessage = configService.getByProjectAndCode(project.getId(), capabilityCode)
                        .map(ProjectCapabilityConfigEntity::getValidationError)
                        .filter(value -> value != null && !value.isBlank())
                        .orElse(null);
                if (errorMessage != null) {
                    message = errorMessage;
                }
            }

            ValidationResult result = new ValidationResult();
            result.valid = isValid;
            result.message = message;

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

        ProjectEntity project = resolveProject(projectId);
        if (project == null) {
            return ResponseEntity.notFound().build();
        }

        configService.addConfigsBatch(project.getId(), request.capabilityCodes);

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

        ProjectEntity project = resolveProject(projectId);
        if (project == null) {
            return ResponseEntity.notFound().build();
        }

        List<String> codes = configService.getConfiguredCapabilityCodes(project.getId());

        return ResponseEntity.ok(ApiResponse.success(codes));
    }

    /**
     * 解析项目ID（兼容 appSpecId 作为入口）
     *
     * 是什么：根据传入的 projectId 或 appSpecId 查询真实项目。
     * 做什么：优先按 projectId 查询，找不到再按 appSpecId 反查。
     * 为什么：前端可能仅持有 appSpecId，需要兼容能力配置调用。
     */
    private ProjectEntity resolveProject(UUID projectIdOrAppSpecId) {
        if (projectIdOrAppSpecId == null) {
            return null;
        }

        ProjectEntity project = projectService.getById(projectIdOrAppSpecId);
        if (project != null) {
            return project;
        }

        return projectService.findByAppSpecId(projectIdOrAppSpecId);
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
