package com.ingenio.backend.controller.admin;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ingenio.backend.common.response.Result;
import com.ingenio.backend.dto.request.admin.PromptSyncRequest;
import com.ingenio.backend.entity.MagicPromptEntity;
import com.ingenio.backend.service.admin.AdminPromptService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Admin API - Prompt同步控制器
 *
 * 用途：供 JeecgBoot 管理 Ingenio 的 Prompt 资产
 *
 * 核心功能：
 * 1. Prompt 同步（发布/下架/更新）
 * 2. Prompt 列表查询
 * 3. Prompt 详情查询
 * 4. Prompt 删除
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
@RequestMapping({"/admin/prompts", "/api/admin/prompts"})
@RequiredArgsConstructor
@Tag(name = "Admin - Prompt同步", description = "供 JeecgBoot 管理 Prompt 资产")
public class AdminPromptController {

    private final AdminPromptService promptService;

    /**
     * 同步单个 Prompt
     *
     * 请求示例：
     * <pre>
     * POST /api/admin/prompts/sync
     * Authorization: Bearer {service-jwt}
     * Content-Type: application/json
     *
     * {
     *   "action": "publish",
     *   "prompt": {
     *     "title": "数学学习助手",
     *     "promptTemplate": "创建一个{subject}学习工具...",
     *     "category": "education",
     *     "ageGroup": "middle_school"
     *   },
     *   "operator": "admin-user-id"
     * }
     * </pre>
     *
     * @param request 同步请求
     * @return 同步后的 Prompt
     */
    @PostMapping("/sync")
    @Operation(
        summary = "同步单个 Prompt",
        description = "支持发布(publish)、下架(unpublish)、更新(update)操作"
    )
    public Result<MagicPromptEntity> syncPrompt(
            @Valid @RequestBody PromptSyncRequest request
    ) {
        log.info("同步 Prompt: action={}, title={}", request.getAction(), request.getPrompt().getTitle());

        try {
            MagicPromptEntity result = promptService.syncPrompt(request);
            return Result.success(result);
        } catch (IllegalArgumentException e) {
            log.warn("Prompt同步参数错误: {}", e.getMessage());
            return Result.error("400", e.getMessage());
        } catch (Exception e) {
            log.error("Prompt同步失败: {}", e.getMessage(), e);
            return Result.error("500", "Prompt同步失败: " + e.getMessage());
        }
    }

    /**
     * 批量同步 Prompt
     *
     * @param requests 批量同步请求
     * @return 同步结果列表
     */
    @PostMapping("/sync/batch")
    @Operation(
        summary = "批量同步 Prompt",
        description = "批量处理多个 Prompt 的同步操作"
    )
    public Result<Map<String, Object>> batchSyncPrompts(
            @Valid @RequestBody List<PromptSyncRequest> requests
    ) {
        log.info("批量同步 Prompt: count={}", requests.size());

        int successCount = 0;
        int failCount = 0;

        for (PromptSyncRequest request : requests) {
            try {
                promptService.syncPrompt(request);
                successCount++;
            } catch (Exception e) {
                log.warn("批量同步单个 Prompt 失败: title={}, error={}",
                        request.getPrompt().getTitle(), e.getMessage());
                failCount++;
            }
        }

        Map<String, Object> result = Map.of(
                "total", requests.size(),
                "success", successCount,
                "failed", failCount
        );

        log.info("批量同步 Prompt 完成: {}", result);
        return Result.success(result);
    }

    /**
     * 分页查询 Prompt 列表
     *
     * @param status    状态（可选：active/draft/archived）
     * @param category  分类（可选）
     * @param ageGroup  年龄分组（可选）
     * @param keyword   搜索关键词（可选）
     * @param pageNum   页码（从1开始，默认1）
     * @param pageSize  每页大小（默认20，最大100）
     * @return Prompt 分页列表
     */
    @GetMapping
    @Operation(
        summary = "分页查询 Prompt 列表",
        description = "支持按状态、分类、年龄分组、关键词筛选"
    )
    public Result<Page<MagicPromptEntity>> listPrompts(
            @Parameter(description = "状态（active/draft/archived）")
            @RequestParam(required = false) String status,

            @Parameter(description = "分类（education/game/productivity/creative/social）")
            @RequestParam(required = false) String category,

            @Parameter(description = "年龄分组（elementary/middle_school/high_school/university）")
            @RequestParam(required = false) String ageGroup,

            @Parameter(description = "搜索关键词（标题/描述）")
            @RequestParam(required = false) String keyword,

            @Parameter(description = "页码（从1开始）")
            @RequestParam(defaultValue = "1") int pageNum,

            @Parameter(description = "每页大小（最大100）")
            @RequestParam(defaultValue = "20") int pageSize
    ) {
        log.info("查询 Prompt 列表: status={}, category={}, ageGroup={}, keyword={}, pageNum={}, pageSize={}",
                status, category, ageGroup, keyword, pageNum, pageSize);

        // 限制每页大小
        if (pageSize > 100) {
            pageSize = 100;
        }
        if (pageNum < 1) {
            pageNum = 1;
        }

        try {
            Page<MagicPromptEntity> result = promptService.listPrompts(
                    status, category, ageGroup, keyword, pageNum, pageSize);
            log.info("Prompt列表查询成功: total={}, current={}", result.getTotal(), result.getCurrent());
            return Result.success(result);
        } catch (Exception e) {
            log.error("Prompt列表查询失败: error={}", e.getMessage(), e);
            return Result.error("500", "Prompt列表查询失败: " + e.getMessage());
        }
    }

    /**
     * 获取 Prompt 详情
     *
     * @param promptId Prompt ID
     * @return Prompt 实体
     */
    @GetMapping("/{promptId}")
    @Operation(
        summary = "获取 Prompt 详情",
        description = "根据 ID 查询 Prompt 详细信息"
    )
    public Result<MagicPromptEntity> getPrompt(
            @Parameter(description = "Prompt ID")
            @PathVariable UUID promptId
    ) {
        log.info("查询 Prompt 详情: promptId={}", promptId);

        try {
            MagicPromptEntity prompt = promptService.getPrompt(promptId);
            if (prompt == null) {
                return Result.error("404", "Prompt 不存在");
            }
            return Result.success(prompt);
        } catch (Exception e) {
            log.error("Prompt详情查询失败: promptId={}, error={}", promptId, e.getMessage(), e);
            return Result.error("500", "Prompt详情查询失败: " + e.getMessage());
        }
    }

    /**
     * 删除 Prompt（硬删除）
     *
     * @param promptId Prompt ID
     * @param operator 操作人（请求参数）
     * @return 操作结果
     */
    @DeleteMapping("/{promptId}")
    @Operation(
        summary = "删除 Prompt",
        description = "硬删除 Prompt（谨慎使用）"
    )
    public Result<Void> deletePrompt(
            @Parameter(description = "Prompt ID")
            @PathVariable UUID promptId,

            @Parameter(description = "操作人")
            @RequestParam(defaultValue = "SYSTEM") String operator
    ) {
        log.info("删除 Prompt: promptId={}, operator={}", promptId, operator);

        try {
            promptService.deletePrompt(promptId, operator);
            return Result.success(null);
        } catch (IllegalArgumentException e) {
            log.warn("删除 Prompt 失败: promptId={}, error={}", promptId, e.getMessage());
            return Result.error("400", e.getMessage());
        } catch (Exception e) {
            log.error("删除 Prompt 失败: promptId={}, error={}", promptId, e.getMessage(), e);
            return Result.error("500", "删除 Prompt 失败: " + e.getMessage());
        }
    }

    /**
     * 统计 Prompt 数量
     *
     * @param status   状态（可选）
     * @param category 分类（可选）
     * @return Prompt 数量统计
     */
    @GetMapping("/count")
    @Operation(
        summary = "统计 Prompt 数量",
        description = "按状态、分类统计 Prompt 数量"
    )
    public Result<Map<String, Object>> countPrompts(
            @Parameter(description = "状态")
            @RequestParam(required = false) String status,

            @Parameter(description = "分类")
            @RequestParam(required = false) String category
    ) {
        log.info("统计 Prompt 数量: status={}, category={}", status, category);

        try {
            long count = promptService.countPrompts(status, category);
            return Result.success(Map.of("count", count));
        } catch (Exception e) {
            log.error("统计 Prompt 数量失败: error={}", e.getMessage(), e);
            return Result.error("500", "统计失败: " + e.getMessage());
        }
    }

    /**
     * 获取 Prompt 分类列表
     *
     * @return 分类列表
     */
    @GetMapping("/categories")
    @Operation(
        summary = "获取 Prompt 分类列表",
        description = "返回系统支持的 Prompt 分类"
    )
    public Result<Map<String, String>> getCategories() {
        return Result.success(Map.of(
                "education", "教育学习",
                "game", "游戏娱乐",
                "productivity", "效率工具",
                "creative", "创意设计",
                "social", "社交互动"
        ));
    }

    /**
     * 获取年龄分组列表
     *
     * @return 年龄分组列表
     */
    @GetMapping("/age-groups")
    @Operation(
        summary = "获取年龄分组列表",
        description = "返回系统支持的年龄分组"
    )
    public Result<Map<String, String>> getAgeGroups() {
        return Result.success(Map.of(
                "elementary", "小学",
                "middle_school", "初中",
                "high_school", "高中",
                "university", "大学"
        ));
    }

    /**
     * 获取难度级别列表
     *
     * @return 难度级别列表
     */
    @GetMapping("/difficulty-levels")
    @Operation(
        summary = "获取难度级别列表",
        description = "返回系统支持的难度级别"
    )
    public Result<Map<String, String>> getDifficultyLevels() {
        return Result.success(Map.of(
                "beginner", "入门",
                "intermediate", "进阶",
                "advanced", "高级"
        ));
    }
}
