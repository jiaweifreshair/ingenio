package com.ingenio.backend.controller.admin;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ingenio.backend.common.response.Result;
import com.ingenio.backend.entity.AuditLogEntity;
import com.ingenio.backend.service.admin.AdminAuditLogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

/**
 * Admin API - 审计日志控制器
 *
 * 用途：供 JeecgBoot 查询 Ingenio 的操作审计日志
 *
 * 安全说明：
 * - 所有接口受 AdminServiceJwtFilter 保护
 * - 必须携带有效的服务间 JWT Token
 *
 * @author Claude
 * @since 2025-12-31 (JeecgBoot 集成)
 */
@Slf4j
@RestController
@RequestMapping({"/admin/audit-logs", "/api/admin/audit-logs"})
@RequiredArgsConstructor
@Tag(name = "Admin - 审计日志", description = "供 JeecgBoot 查询审计日志")
public class AdminAuditLogController {

    private final AdminAuditLogService auditLogService;

    /**
     * 分页查询审计日志
     *
     * 请求示例：
     * <pre>
     * GET /api/admin/audit-logs?tenantId=xxx&action=TEMPLATE_CREATE&pageNum=1&pageSize=20
     * Authorization: Bearer {service-jwt}
     * </pre>
     *
     * @param tenantId     租户ID（可选）
     * @param actorId      操作人ID（可选）
     * @param action       操作动作（可选）
     * @param resourceType 资源类型（可选）
     * @param startTime    开始时间（可选，ISO格式）
     * @param endTime      结束时间（可选，ISO格式）
     * @param pageNum      页码（从1开始，默认1）
     * @param pageSize     每页大小（默认20，最大100）
     * @return 分页审计日志
     */
    @GetMapping
    @Operation(
        summary = "分页查询审计日志",
        description = "支持多条件筛选的审计日志查询"
    )
    public Result<Page<AuditLogEntity>> queryLogs(
            @Parameter(description = "租户ID")
            @RequestParam(required = false) String tenantId,

            @Parameter(description = "操作人ID")
            @RequestParam(required = false) String actorId,

            @Parameter(description = "操作动作（如 TEMPLATE_CREATE, USER_LOGIN）")
            @RequestParam(required = false) String action,

            @Parameter(description = "资源类型（如 TEMPLATE, USER）")
            @RequestParam(required = false) String resourceType,

            @Parameter(description = "开始时间（ISO格式：2025-01-01T00:00:00）")
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,

            @Parameter(description = "结束时间（ISO格式：2025-12-31T23:59:59）")
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime,

            @Parameter(description = "页码（从1开始）")
            @RequestParam(defaultValue = "1") int pageNum,

            @Parameter(description = "每页大小（最大100）")
            @RequestParam(defaultValue = "20") int pageSize
    ) {
        log.info("查询审计日志: tenantId={}, actorId={}, action={}, resourceType={}, pageNum={}, pageSize={}",
                tenantId, actorId, action, resourceType, pageNum, pageSize);

        // 限制每页大小
        if (pageSize > 100) {
            pageSize = 100;
        }
        if (pageNum < 1) {
            pageNum = 1;
        }

        // 转换时间
        Instant startInstant = startTime != null ? startTime.toInstant(ZoneOffset.UTC) : null;
        Instant endInstant = endTime != null ? endTime.toInstant(ZoneOffset.UTC) : null;

        try {
            Page<AuditLogEntity> result = auditLogService.queryLogs(
                    tenantId, actorId, action, resourceType,
                    startInstant, endInstant, pageNum, pageSize
            );
            log.info("审计日志查询成功: total={}, current={}", result.getTotal(), result.getCurrent());
            return Result.success(result);
        } catch (Exception e) {
            log.error("审计日志查询失败: error={}", e.getMessage(), e);
            return Result.error("500", "审计日志查询失败: " + e.getMessage());
        }
    }

    /**
     * 根据链路ID查询日志
     *
     * 用于追踪单个请求的完整操作链路
     *
     * @param traceId 链路追踪ID
     * @return 该链路下的所有日志（按时间正序）
     */
    @GetMapping("/trace/{traceId}")
    @Operation(
        summary = "按链路ID查询日志",
        description = "追踪单个请求的完整操作链路"
    )
    public Result<List<AuditLogEntity>> queryByTraceId(
            @Parameter(description = "链路追踪ID")
            @PathVariable String traceId
    ) {
        log.info("按链路ID查询审计日志: traceId={}", traceId);

        try {
            List<AuditLogEntity> logs = auditLogService.queryByTraceId(traceId);
            log.info("链路日志查询成功: traceId={}, count={}", traceId, logs.size());
            return Result.success(logs);
        } catch (Exception e) {
            log.error("链路日志查询失败: traceId={}, error={}", traceId, e.getMessage(), e);
            return Result.error("500", "链路日志查询失败: " + e.getMessage());
        }
    }

    /**
     * 获取操作类型列表
     *
     * 返回系统中所有的操作类型，供前端筛选使用
     *
     * @return 操作类型列表
     */
    @GetMapping("/actions")
    @Operation(
        summary = "获取操作类型列表",
        description = "返回系统中所有的操作类型"
    )
    public Result<List<String>> getActionTypes() {
        // 预定义的操作类型
        List<String> actions = List.of(
                // 用户相关
                "USER_LOGIN",
                "USER_LOGOUT",
                "USER_REGISTER",
                "USER_UPDATE",
                "USER_DELETE",
                // 模板相关
                "TEMPLATE_CREATE",
                "TEMPLATE_UPDATE",
                "TEMPLATE_DELETE",
                "TEMPLATE_PUBLISH",
                "TEMPLATE_UNPUBLISH",
                // Prompt相关
                "PROMPT_CREATE",
                "PROMPT_UPDATE",
                "PROMPT_DELETE",
                // 应用相关
                "APP_SPEC_CREATE",
                "APP_SPEC_UPDATE",
                "APP_SPEC_DELETE",
                // 代码生成相关
                "GENERATION_START",
                "GENERATION_COMPLETE",
                "GENERATION_FAIL",
                // 管理操作
                "ACCOUNT_FREEZE",
                "ACCOUNT_UNFREEZE",
                "CONFIG_UPDATE",
                "KEY_ROTATE"
        );
        return Result.success(actions);
    }

    /**
     * 获取资源类型列表
     *
     * @return 资源类型列表
     */
    @GetMapping("/resource-types")
    @Operation(
        summary = "获取资源类型列表",
        description = "返回系统中所有的资源类型"
    )
    public Result<List<String>> getResourceTypes() {
        List<String> resourceTypes = List.of(
                "USER",
                "TEMPLATE",
                "PROMPT",
                "APP_SPEC",
                "PROJECT",
                "GENERATION_TASK",
                "CONFIG",
                "AI_PROVIDER"
        );
        return Result.success(resourceTypes);
    }
}
