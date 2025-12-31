package com.ingenio.backend.controller.admin;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ingenio.backend.common.response.Result;
import com.ingenio.backend.entity.UserEntity;
import com.ingenio.backend.service.admin.AdminUserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

/**
 * Admin API - 用户管理控制器
 *
 * 用途：供 JeecgBoot 管理 Ingenio 的 C端用户
 *
 * 核心功能：
 * 1. 用户列表查询（分页、筛选）
 * 2. 用户详情查询
 * 3. 用户状态变更（冻结/解冻）
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
@RequestMapping({"/admin/app-users", "/api/admin/app-users"})
@RequiredArgsConstructor
@Tag(name = "Admin - 用户管理", description = "供 JeecgBoot 管理 C端用户")
public class AdminUserController {

    private final AdminUserService userService;

    /**
     * 分页查询用户列表
     *
     * 请求示例：
     * <pre>
     * GET /api/admin/app-users?tenantId=xxx&status=active&keyword=test&pageNum=1&pageSize=20
     * Authorization: Bearer {service-jwt}
     * </pre>
     *
     * @param tenantId 租户ID（可选）
     * @param status   用户状态（可选：active/inactive/suspended）
     * @param keyword  搜索关键词（用户名/邮箱模糊匹配，可选）
     * @param pageNum  页码（从1开始，默认1）
     * @param pageSize 每页大小（默认20，最大100）
     * @return 用户分页列表（脱敏后）
     */
    @GetMapping
    @Operation(
        summary = "分页查询用户列表",
        description = "支持按租户、状态、关键词筛选的用户列表查询"
    )
    public Result<Page<UserEntity>> listUsers(
            @Parameter(description = "租户ID")
            @RequestParam(required = false) String tenantId,

            @Parameter(description = "用户状态（active/inactive/suspended）")
            @RequestParam(required = false) String status,

            @Parameter(description = "搜索关键词（用户名/邮箱）")
            @RequestParam(required = false) String keyword,

            @Parameter(description = "页码（从1开始）")
            @RequestParam(defaultValue = "1") int pageNum,

            @Parameter(description = "每页大小（最大100）")
            @RequestParam(defaultValue = "20") int pageSize
    ) {
        log.info("查询用户列表: tenantId={}, status={}, keyword={}, pageNum={}, pageSize={}",
                tenantId, status, keyword, pageNum, pageSize);

        // 限制每页大小
        if (pageSize > 100) {
            pageSize = 100;
        }
        if (pageNum < 1) {
            pageNum = 1;
        }

        try {
            Page<UserEntity> result = userService.listUsers(tenantId, status, keyword, pageNum, pageSize);
            log.info("用户列表查询成功: total={}, current={}", result.getTotal(), result.getCurrent());
            return Result.success(result);
        } catch (Exception e) {
            log.error("用户列表查询失败: error={}", e.getMessage(), e);
            return Result.error("500", "用户列表查询失败: " + e.getMessage());
        }
    }

    /**
     * 查询用户详情
     *
     * @param userId 用户ID
     * @return 用户实体（脱敏后）
     */
    @GetMapping("/{userId}")
    @Operation(
        summary = "查询用户详情",
        description = "根据用户ID查询用户详细信息"
    )
    public Result<UserEntity> getUser(
            @Parameter(description = "用户ID")
            @PathVariable UUID userId
    ) {
        log.info("查询用户详情: userId={}", userId);

        try {
            UserEntity user = userService.getUser(userId);
            if (user == null) {
                log.warn("用户不存在: userId={}", userId);
                return Result.error("404", "用户不存在");
            }
            return Result.success(user);
        } catch (Exception e) {
            log.error("用户详情查询失败: userId={}, error={}", userId, e.getMessage(), e);
            return Result.error("500", "用户详情查询失败: " + e.getMessage());
        }
    }

    /**
     * 冻结用户
     *
     * @param userId  用户ID
     * @param request 请求体（包含操作人、原因）
     * @return 更新后的用户实体
     */
    @PostMapping("/{userId}/freeze")
    @Operation(
        summary = "冻结用户",
        description = "将用户状态设置为 suspended"
    )
    public Result<UserEntity> freezeUser(
            @Parameter(description = "用户ID")
            @PathVariable UUID userId,

            @RequestBody Map<String, String> request
    ) {
        String operator = request.getOrDefault("operator", "SYSTEM");
        String reason = request.get("reason");

        log.info("冻结用户: userId={}, operator={}, reason={}", userId, operator, reason);

        try {
            UserEntity user = userService.freezeUser(userId, operator, reason);
            return Result.success(user);
        } catch (IllegalArgumentException e) {
            log.warn("冻结用户失败: userId={}, error={}", userId, e.getMessage());
            return Result.error("400", e.getMessage());
        } catch (Exception e) {
            log.error("冻结用户失败: userId={}, error={}", userId, e.getMessage(), e);
            return Result.error("500", "冻结用户失败: " + e.getMessage());
        }
    }

    /**
     * 解冻用户
     *
     * @param userId  用户ID
     * @param request 请求体（包含操作人）
     * @return 更新后的用户实体
     */
    @PostMapping("/{userId}/unfreeze")
    @Operation(
        summary = "解冻用户",
        description = "将用户状态从 suspended 恢复为 active"
    )
    public Result<UserEntity> unfreezeUser(
            @Parameter(description = "用户ID")
            @PathVariable UUID userId,

            @RequestBody Map<String, String> request
    ) {
        String operator = request.getOrDefault("operator", "SYSTEM");

        log.info("解冻用户: userId={}, operator={}", userId, operator);

        try {
            UserEntity user = userService.unfreezeUser(userId, operator);
            return Result.success(user);
        } catch (IllegalArgumentException e) {
            log.warn("解冻用户失败: userId={}, error={}", userId, e.getMessage());
            return Result.error("400", e.getMessage());
        } catch (Exception e) {
            log.error("解冻用户失败: userId={}, error={}", userId, e.getMessage(), e);
            return Result.error("500", "解冻用户失败: " + e.getMessage());
        }
    }

    /**
     * 更新用户状态
     *
     * 通用的状态变更接口，支持 active/inactive/suspended
     *
     * @param userId  用户ID
     * @param request 请求体（包含状态、操作人）
     * @return 更新后的用户实体
     */
    @PatchMapping("/{userId}/status")
    @Operation(
        summary = "更新用户状态",
        description = "更新用户状态（active/inactive/suspended）"
    )
    public Result<UserEntity> updateUserStatus(
            @Parameter(description = "用户ID")
            @PathVariable UUID userId,

            @RequestBody Map<String, String> request
    ) {
        String status = request.get("status");
        String operator = request.getOrDefault("operator", "SYSTEM");

        if (status == null || status.isBlank()) {
            return Result.error("400", "status 不能为空");
        }

        log.info("更新用户状态: userId={}, status={}, operator={}", userId, status, operator);

        try {
            UserEntity user = userService.updateUserStatus(userId, status, operator);
            return Result.success(user);
        } catch (IllegalArgumentException e) {
            log.warn("更新用户状态失败: userId={}, error={}", userId, e.getMessage());
            return Result.error("400", e.getMessage());
        } catch (Exception e) {
            log.error("更新用户状态失败: userId={}, error={}", userId, e.getMessage(), e);
            return Result.error("500", "更新用户状态失败: " + e.getMessage());
        }
    }

    /**
     * 统计用户数量
     *
     * @param tenantId 租户ID（可选）
     * @param status   状态（可选）
     * @return 用户数量统计
     */
    @GetMapping("/count")
    @Operation(
        summary = "统计用户数量",
        description = "按租户、状态统计用户数量"
    )
    public Result<Map<String, Object>> countUsers(
            @Parameter(description = "租户ID")
            @RequestParam(required = false) String tenantId,

            @Parameter(description = "用户状态")
            @RequestParam(required = false) String status
    ) {
        log.info("统计用户数量: tenantId={}, status={}", tenantId, status);

        try {
            long count = userService.countUsers(tenantId, status);
            return Result.success(Map.of("count", count));
        } catch (Exception e) {
            log.error("统计用户数量失败: error={}", e.getMessage(), e);
            return Result.error("500", "统计用户数量失败: " + e.getMessage());
        }
    }

    /**
     * 获取用户状态列表
     *
     * @return 可用的用户状态列表
     */
    @GetMapping("/statuses")
    @Operation(
        summary = "获取用户状态列表",
        description = "返回系统支持的用户状态"
    )
    public Result<Map<String, String>> getStatuses() {
        return Result.success(Map.of(
                "active", "活跃",
                "inactive", "未激活",
                "suspended", "已冻结"
        ));
    }
}
