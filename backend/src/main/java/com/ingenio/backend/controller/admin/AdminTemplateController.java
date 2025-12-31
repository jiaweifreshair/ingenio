package com.ingenio.backend.controller.admin;

import com.ingenio.backend.common.response.Result;
import com.ingenio.backend.dto.request.admin.TemplateSyncRequest;
import com.ingenio.backend.entity.IndustryTemplateEntity;
import com.ingenio.backend.service.admin.AdminTemplateService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * Admin API - 模板管理控制器
 *
 * 用途：接收 JeecgBoot 推送的模板同步请求
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
@RequestMapping({"/admin/templates", "/api/admin/templates"})
@RequiredArgsConstructor
@Tag(name = "Admin - 模板管理", description = "接收 JeecgBoot 推送的模板同步请求")
public class AdminTemplateController {

    private final AdminTemplateService adminTemplateService;

    /**
     * 模板同步接口
     *
     * 接收 JeecgBoot 发布/下线/更新模板的请求
     *
     * 请求示例：
     * <pre>
     * POST /api/admin/templates/sync
     * Authorization: Bearer {service-jwt}
     * {
     *   "action": "publish",
     *   "template": {
     *     "id": "uuid",
     *     "name": "电商平台模板",
     *     "category": "电商",
     *     "blueprintSpec": {...}
     *   },
     *   "operator": "admin@jeecg.com"
     * }
     * </pre>
     *
     * @param request 模板同步请求
     * @return 同步结果
     */
    @PostMapping("/sync")
    @Operation(
        summary = "模板同步",
        description = "接收 JeecgBoot 推送的模板发布/下线/更新请求"
    )
    public Result<IndustryTemplateEntity> syncTemplate(
            @Valid @RequestBody TemplateSyncRequest request
    ) {
        log.info("收到模板同步请求: action={}, templateId={}, operator={}",
                request.getAction(),
                request.getTemplate() != null ? request.getTemplate().getId() : null,
                request.getOperator());

        if (!request.isValidAction()) {
            log.warn("无效的同步动作: {}", request.getAction());
            return Result.error("400", "无效的同步动作: " + request.getAction());
        }

        try {
            IndustryTemplateEntity result = adminTemplateService.syncTemplate(request);
            log.info("模板同步成功: action={}, templateId={}",
                    request.getAction(), result.getId());
            return Result.success(result);
        } catch (Exception e) {
            log.error("模板同步失败: action={}, error={}",
                    request.getAction(), e.getMessage(), e);
            return Result.error("500", "模板同步失败: " + e.getMessage());
        }
    }

    /**
     * 批量同步模板
     *
     * 支持一次性同步多个模板（用于初始化或全量同步）
     *
     * @param requests 批量同步请求
     * @return 同步结果列表
     */
    @PostMapping("/sync/batch")
    @Operation(
        summary = "批量模板同步",
        description = "批量接收 JeecgBoot 推送的模板"
    )
    public Result<List<IndustryTemplateEntity>> syncTemplatesBatch(
            @Valid @RequestBody List<TemplateSyncRequest> requests
    ) {
        log.info("收到批量模板同步请求: count={}", requests.size());

        try {
            List<IndustryTemplateEntity> results = adminTemplateService.syncTemplatesBatch(requests);
            log.info("批量模板同步成功: count={}", results.size());
            return Result.success(results);
        } catch (Exception e) {
            log.error("批量模板同步失败: error={}", e.getMessage(), e);
            return Result.error("500", "批量模板同步失败: " + e.getMessage());
        }
    }

    /**
     * 查询模板列表（供 JeecgBoot 确认同步状态）
     *
     * @param category 分类筛选（可选）
     * @param isActive 是否启用（可选）
     * @return 模板列表
     */
    @GetMapping
    @Operation(
        summary = "查询模板列表",
        description = "查询 Ingenio 中的模板列表，供 JeecgBoot 确认同步状态"
    )
    public Result<List<IndustryTemplateEntity>> listTemplates(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) Boolean isActive
    ) {
        log.info("查询模板列表: category={}, isActive={}", category, isActive);

        try {
            List<IndustryTemplateEntity> templates = adminTemplateService.listTemplates(category, isActive);
            log.info("查询模板列表成功: count={}", templates.size());
            return Result.success(templates);
        } catch (Exception e) {
            log.error("查询模板列表失败: error={}", e.getMessage(), e);
            return Result.error("500", "查询模板列表失败: " + e.getMessage());
        }
    }

    /**
     * 查询单个模板详情
     *
     * @param id 模板ID
     * @return 模板详情
     */
    @GetMapping("/{id}")
    @Operation(
        summary = "查询模板详情",
        description = "查询单个模板的详细信息"
    )
    public Result<IndustryTemplateEntity> getTemplate(@PathVariable UUID id) {
        log.info("查询模板详情: id={}", id);

        try {
            IndustryTemplateEntity template = adminTemplateService.getTemplate(id);
            if (template == null) {
                return Result.error("404", "模板不存在: " + id);
            }
            return Result.success(template);
        } catch (Exception e) {
            log.error("查询模板详情失败: id={}, error={}", id, e.getMessage(), e);
            return Result.error("500", "查询模板详情失败: " + e.getMessage());
        }
    }

    /**
     * 删除模板（硬删除，谨慎使用）
     *
     * @param id 模板ID
     * @return 删除结果
     */
    @DeleteMapping("/{id}")
    @Operation(
        summary = "删除模板",
        description = "硬删除模板（谨慎使用，建议使用 sync + unpublish）"
    )
    public Result<Void> deleteTemplate(@PathVariable UUID id) {
        log.warn("收到模板删除请求: id={}", id);

        try {
            adminTemplateService.deleteTemplate(id);
            log.info("模板删除成功: id={}", id);
            return Result.success(null);
        } catch (Exception e) {
            log.error("模板删除失败: id={}, error={}", id, e.getMessage(), e);
            return Result.error("500", "模板删除失败: " + e.getMessage());
        }
    }
}
