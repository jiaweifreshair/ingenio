package com.ingenio.backend.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ingenio.backend.common.exception.BusinessException;
import com.ingenio.backend.common.exception.ErrorCode;
import com.ingenio.backend.common.response.PageResult;
import com.ingenio.backend.common.response.Result;
import com.ingenio.backend.dto.request.CreateAppSpecRequest;
import com.ingenio.backend.dto.response.AppSpecResponse;
import com.ingenio.backend.entity.AppSpecEntity;
import com.ingenio.backend.service.AppSpecService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

/**
 * AppSpec管理Controller
 * 提供AppSpec的CRUD、版本管理和状态更新等功能
 *
 * @author Ingenio Team
 * @since 1.0.0
 */
@Slf4j
@RestController
@RequestMapping("/v1/appspecs")
@RequiredArgsConstructor
public class AppSpecController {

    private final AppSpecService appSpecService;

    /**
     * 创建AppSpec
     * 需要登录
     *
     * @param request 创建AppSpec请求参数
     * @return 创建成功的AppSpec信息
     */
    @SaCheckLogin
    @PostMapping
    public Result<AppSpecResponse> create(@Valid @RequestBody CreateAppSpecRequest request) {
        // 获取当前用户ID和租户ID
        String userIdStr = StpUtil.getLoginIdAsString();
        UUID userId = UUID.fromString(userIdStr);

        // 从Token中获取租户ID（实际项目中应从Token的自定义字段获取）
        // 这里简化处理，假设租户ID存储在Session中
        String tenantIdStr = (String) StpUtil.getSession().get("tenantId");
        UUID tenantId = tenantIdStr != null ? UUID.fromString(tenantIdStr) : userId; // 简化处理

        log.info("创建AppSpec: userId={}, tenantId={}", userId, tenantId);

        // 调用Service层创建AppSpec
        AppSpecEntity appSpec = appSpecService.createAppSpec(tenantId, userId, request.getSpecContent());

        // 转换为AppSpecResponse
        AppSpecResponse response = convertToAppSpecResponse(appSpec);

        log.info("创建AppSpec成功: appSpecId={}", appSpec.getId());
        return Result.success(response);
    }

    /**
     * 获取AppSpec详情
     * 需要登录，租户隔离
     *
     * @param id AppSpec ID
     * @return AppSpec详细信息
     */
    @SaCheckLogin
    @GetMapping("/{id}")
    public Result<AppSpecResponse> getById(@PathVariable UUID id) {
        // 获取当前用户ID和租户ID
        // V2.0修复：与PlanRoutingController保持一致的默认租户逻辑
        String userIdStr = StpUtil.getLoginIdAsString();
        var session = StpUtil.getSession(false);
        Object sessionTenantId = session != null ? session.get("tenantId") : null;
        UUID tenantId = sessionTenantId != null
            ? UUID.fromString(sessionTenantId.toString())
            : UUID.fromString("00000000-0000-0000-0000-000000000001"); // 默认租户ID

        log.info("查询AppSpec详情: id={}, tenantId={}, userId={}", id, tenantId, userIdStr);

        // 根据ID和租户ID查询（租户隔离）
        AppSpecEntity appSpec = appSpecService.getByIdAndTenantId(id, tenantId);
        if (appSpec == null) {
            log.warn("AppSpec不存在: id={}", id);
            throw new BusinessException(ErrorCode.APPSPEC_NOT_FOUND);
        }

        AppSpecResponse response = convertToAppSpecResponse(appSpec);
        return Result.success(response);
    }

    /**
     * 分页查询AppSpec列表
     * 查询当前用户创建的AppSpec列表
     *
     * @param current 当前页码
     * @param size 每页数量
     * @return 分页结果
     */
    @SaCheckLogin
    @GetMapping
    public Result<PageResult<AppSpecResponse>> list(
        @RequestParam(defaultValue = "1") Long current,
        @RequestParam(defaultValue = "10") Long size
    ) {
        // 获取当前用户ID和租户ID
        String userIdStr = StpUtil.getLoginIdAsString();
        UUID userId = UUID.fromString(userIdStr);
        String tenantIdStr = (String) StpUtil.getSession().get("tenantId");
        UUID tenantId = tenantIdStr != null ? UUID.fromString(tenantIdStr) : userId;

        log.info("分页查询AppSpec列表: userId={}, tenantId={}, current={}, size={}",
            userId, tenantId, current, size);

        // 分页查询
        Page<AppSpecEntity> page = appSpecService.pageByUser(tenantId, userId, current, size);

        // 转换为PageResult
        PageResult<AppSpecResponse> pageResult = PageResult.<AppSpecResponse>builder()
            .records(page.getRecords().stream()
                .map(this::convertToAppSpecResponse)
                .toList())
            .total(page.getTotal())
            .size(page.getSize())
            .current(page.getCurrent())
            .pages(page.getPages())
            .hasNext(page.hasNext())
            .hasPrevious(page.hasPrevious())
            .build();

        return Result.success(pageResult);
    }

    /**
     * 更新AppSpec状态
     * 需要登录
     *
     * @param id AppSpec ID
     * @param statusRequest 状态更新请求（包含status字段）
     * @return 操作结果
     */
    @SaCheckLogin
    @PutMapping("/{id}/status")
    public Result<Void> updateStatus(
        @PathVariable UUID id,
        @RequestBody Map<String, String> statusRequest
    ) {
        String status = statusRequest.get("status");
        if (status == null || status.isBlank()) {
            throw new BusinessException(ErrorCode.PARAM_ERROR.getCode(), "状态不能为空");
        }

        log.info("更新AppSpec状态: id={}, status={}", id, status);

        // 调用Service层更新状态
        appSpecService.updateStatus(id, status);

        log.info("更新AppSpec状态成功: id={}", id);
        return Result.success();
    }

    /**
     * 删除AppSpec（软删除）
     * 需要登录，租户隔离
     *
     * @param id AppSpec ID
     * @return 操作结果
     */
    @SaCheckLogin
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable UUID id) {
        // 获取租户ID
        String userIdStr = StpUtil.getLoginIdAsString();
        String tenantIdStr = (String) StpUtil.getSession().get("tenantId");
        UUID tenantId = tenantIdStr != null ? UUID.fromString(tenantIdStr) : UUID.fromString(userIdStr);

        log.info("删除AppSpec: id={}, tenantId={}", id, tenantId);

        // 调用Service层软删除
        appSpecService.softDelete(id, tenantId);

        log.info("删除AppSpec成功: id={}", id);
        return Result.success("删除成功", null);
    }

    /**
     * 创建AppSpec新版本
     * 基于现有版本创建新版本
     *
     * @param id 父版本ID
     * @param request 新版本的AppSpec内容
     * @return 新版本AppSpec信息
     */
    @SaCheckLogin
    @PostMapping("/{id}/versions")
    public Result<AppSpecResponse> createVersion(
        @PathVariable UUID id,
        @Valid @RequestBody CreateAppSpecRequest request
    ) {
        log.info("创建AppSpec新版本: parentId={}", id);

        // 调用Service层创建新版本
        AppSpecEntity newVersion = appSpecService.createVersion(id, request.getSpecContent());

        AppSpecResponse response = convertToAppSpecResponse(newVersion);

        log.info("创建AppSpec新版本成功: newVersionId={}, parentId={}", newVersion.getId(), id);
        return Result.success(response);
    }

    /**
     * 将AppSpecEntity转换为AppSpecResponse
     *
     * @param appSpec AppSpec实体
     * @return AppSpec响应DTO
     */
    private AppSpecResponse convertToAppSpecResponse(AppSpecEntity appSpec) {
        return AppSpecResponse.builder()
            .id(appSpec.getId())
            .tenantId(appSpec.getTenantId())
            .createdByUserId(appSpec.getCreatedByUserId())
            .specContent(appSpec.getSpecContent())
            .version(appSpec.getVersion())
            .parentVersionId(appSpec.getParentVersionId())
            .status(appSpec.getStatus())
            .qualityScore(appSpec.getQualityScore())
            .createdAt(appSpec.getCreatedAt())
            .updatedAt(appSpec.getUpdatedAt())
            .metadata(appSpec.getMetadata())
            .build();
    }
}
