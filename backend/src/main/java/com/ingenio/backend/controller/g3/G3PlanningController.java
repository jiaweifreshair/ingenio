package com.ingenio.backend.controller.g3;

import com.ingenio.backend.dto.ApiResponse;
import com.ingenio.backend.entity.g3.G3PlanningFileEntity;
import com.ingenio.backend.service.g3.G3PlanningFileService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * G3规划文件 REST API
 *
 * G3引擎任务规划增强 - 规划文件管理接口
 *
 * 接口列表：
 * - GET    /v1/g3/jobs/{jobId}/planning             获取所有规划文件
 * - GET    /v1/g3/jobs/{jobId}/planning/{type}      获取单个规划文件
 * - PUT    /v1/g3/jobs/{jobId}/planning/{type}      更新规划文件
 * - POST   /v1/g3/jobs/{jobId}/planning/{type}/append  追加内容
 * - GET    /v1/g3/jobs/{jobId}/planning/context/compact  获取精简上下文
 *
 * 文件类型：
 * - task_plan: 任务计划文件
 * - notes: 笔记文件
 * - context: 上下文文件
 *
 * @author Claude
 * @since 2025-01-08 (G3引擎任务规划增强)
 */
@Slf4j
@RestController
@RequestMapping("/v1/g3/jobs/{jobId}/planning")
public class G3PlanningController {

    @Autowired
    private G3PlanningFileService planningFileService;

    /**
     * 允许的 updatedBy 枚举
     *
     * 说明：与数据库迁移 V035 的 check constraint 保持一致，避免写入失败。
     */
    private static final Set<String> ALLOWED_UPDATED_BY = Set.of(
            G3PlanningFileEntity.UPDATER_SYSTEM,
            G3PlanningFileEntity.UPDATER_ARCHITECT,
            G3PlanningFileEntity.UPDATER_CODER,
            G3PlanningFileEntity.UPDATER_COACH,
            G3PlanningFileEntity.UPDATER_USER
    );

    /**
     * 规范化 updatedBy
     *
     * 规则：
     * - 为空：默认 user（前端手动操作的默认语义）
     * - 非法值：降级为 user（避免触发数据库约束失败）
     */
    private String normalizeUpdatedBy(String updatedBy) {
        if (updatedBy == null || updatedBy.isBlank()) {
            return G3PlanningFileEntity.UPDATER_USER;
        }
        String normalized = updatedBy.trim();
        return ALLOWED_UPDATED_BY.contains(normalized) ? normalized : G3PlanningFileEntity.UPDATER_USER;
    }

    /**
     * 获取任务的所有规划文件
     *
     * @param jobId 任务ID
     * @return 规划文件列表
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<G3PlanningFileEntity>>> listPlanningFiles(
            @PathVariable UUID jobId
    ) {
        log.info("API: 获取任务规划文件列表, jobId={}", jobId);

        List<G3PlanningFileEntity> files = planningFileService.listByJob(jobId);

        return ResponseEntity.ok(ApiResponse.success(files));
    }

    /**
     * 获取任务的所有规划文件（Map格式）
     *
     * @param jobId 任务ID
     * @return 文件类型 -> 文件实体 的映射
     */
    @GetMapping("/map")
    public ResponseEntity<ApiResponse<Map<String, G3PlanningFileEntity>>> getPlanningFilesAsMap(
            @PathVariable UUID jobId
    ) {
        log.info("API: 获取任务规划文件Map, jobId={}", jobId);

        Map<String, G3PlanningFileEntity> files = planningFileService.getByJobAsMap(jobId);

        return ResponseEntity.ok(ApiResponse.success(files));
    }

    /**
     * 获取单个规划文件
     *
     * @param jobId 任务ID
     * @param fileType 文件类型：task_plan/notes/context
     * @return 规划文件
     */
    @GetMapping("/{fileType}")
    public ResponseEntity<ApiResponse<G3PlanningFileEntity>> getPlanningFile(
            @PathVariable UUID jobId,
            @PathVariable String fileType
    ) {
        log.info("API: 获取规划文件, jobId={}, type={}", jobId, fileType);

        return planningFileService.getByJobAndType(jobId, fileType)
            .map(file -> ResponseEntity.ok(ApiResponse.success(file)))
            .orElse(ResponseEntity.notFound().build());
    }

    /**
     * 获取规划文件内容（仅内容）
     *
     * @param jobId 任务ID
     * @param fileType 文件类型
     * @return 文件内容
     */
    @GetMapping("/{fileType}/content")
    public ResponseEntity<ApiResponse<String>> getPlanningFileContent(
            @PathVariable UUID jobId,
            @PathVariable String fileType
    ) {
        log.info("API: 获取规划文件内容, jobId={}, type={}", jobId, fileType);

        String content = planningFileService.getContent(jobId, fileType);

        if (content.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(ApiResponse.success(content));
    }

    /**
     * 更新规划文件内容
     *
     * @param jobId 任务ID
     * @param fileType 文件类型
     * @param request 更新请求
     * @return 更新后的文件
     */
    @PutMapping("/{fileType}")
    public ResponseEntity<ApiResponse<G3PlanningFileEntity>> updatePlanningFile(
            @PathVariable UUID jobId,
            @PathVariable String fileType,
            @RequestBody UpdateContentRequest request
    ) {
        log.info("API: 更新规划文件, jobId={}, type={}", jobId, fileType);

        try {
            G3PlanningFileEntity file = planningFileService.updateContent(
                jobId,
                fileType,
                request.content,
                normalizeUpdatedBy(request.updatedBy)
            );

            return ResponseEntity.ok(ApiResponse.success(file));

        } catch (IllegalArgumentException e) {
            log.warn("更新规划文件失败: {}", e.getMessage());
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    /**
     * 追加内容到规划文件
     *
     * @param jobId 任务ID
     * @param fileType 文件类型
     * @param request 追加请求
     * @return 更新后的文件
     */
    @PostMapping("/{fileType}/append")
    public ResponseEntity<ApiResponse<G3PlanningFileEntity>> appendToPlanningFile(
            @PathVariable UUID jobId,
            @PathVariable String fileType,
            @RequestBody AppendContentRequest request
    ) {
        log.info("API: 追加规划文件内容, jobId={}, type={}", jobId, fileType);

        try {
            G3PlanningFileEntity file = planningFileService.appendContent(
                jobId,
                fileType,
                request.content,
                normalizeUpdatedBy(request.updatedBy)
            );

            return ResponseEntity.ok(ApiResponse.success(file));

        } catch (IllegalArgumentException e) {
            log.warn("追加规划文件失败: {}", e.getMessage());
            return ResponseEntity.badRequest().body(ApiResponse.error(e.getMessage()));
        }
    }

    /**
     * 获取精简上下文
     *
     * 供Agent使用，只包含必要信息以减少Token消耗
     *
     * @param jobId 任务ID
     * @return 精简上下文内容
     */
    @GetMapping("/context/compact")
    public ResponseEntity<ApiResponse<String>> getCompactContext(
            @PathVariable UUID jobId
    ) {
        log.info("API: 获取精简上下文, jobId={}", jobId);

        String compactContext = planningFileService.getCompactContext(jobId);

        return ResponseEntity.ok(ApiResponse.success(compactContext));
    }

    // ==================== task_plan 专用操作 ====================

    /**
     * 更新阶段状态
     *
     * @param jobId 任务ID
     * @param request 更新请求
     * @return 操作结果
     */
    @PostMapping("/task_plan/phase")
    public ResponseEntity<ApiResponse<Void>> updatePhaseStatus(
            @PathVariable UUID jobId,
            @RequestBody UpdatePhaseRequest request
    ) {
        log.info("API: 更新阶段状态, jobId={}, phase={}, completed={}",
            jobId, request.phase, request.completed);

        planningFileService.updatePhaseStatus(
            jobId,
            request.phase,
            request.completed,
            normalizeUpdatedBy(request.updatedBy)
        );

        return ResponseEntity.ok(ApiResponse.success(null));
    }

    /**
     * 追加决策记录
     *
     * @param jobId 任务ID
     * @param request 决策请求
     * @return 操作结果
     */
    @PostMapping("/task_plan/decision")
    public ResponseEntity<ApiResponse<Void>> appendDecision(
            @PathVariable UUID jobId,
            @RequestBody AppendDecisionRequest request
    ) {
        log.info("API: 追加决策记录, jobId={}, decision={}", jobId, request.decision);

        planningFileService.appendDecision(
            jobId,
            request.decision,
            request.reason,
            normalizeUpdatedBy(request.updatedBy)
        );

        return ResponseEntity.ok(ApiResponse.success(null));
    }

    /**
     * 追加错误记录
     *
     * @param jobId 任务ID
     * @param request 错误请求
     * @return 操作结果
     */
    @PostMapping("/task_plan/error")
    public ResponseEntity<ApiResponse<Void>> appendError(
            @PathVariable UUID jobId,
            @RequestBody AppendErrorRequest request
    ) {
        log.info("API: 追加错误记录, jobId={}, error={}", jobId, request.error);

        planningFileService.appendError(
            jobId,
            request.error,
            request.solution,
            normalizeUpdatedBy(request.updatedBy)
        );

        return ResponseEntity.ok(ApiResponse.success(null));
    }

    // ==================== context 专用操作 ====================

    /**
     * 添加已生成文件
     *
     * @param jobId 任务ID
     * @param request 文件信息
     * @return 操作结果
     */
    @PostMapping("/context/file")
    public ResponseEntity<ApiResponse<Void>> addGeneratedFile(
            @PathVariable UUID jobId,
            @RequestBody AddGeneratedFileRequest request
    ) {
        log.info("API: 添加已生成文件, jobId={}, file={}", jobId, request.filePath);

        planningFileService.addGeneratedFile(
            jobId,
            request.filePath,
            request.className,
            request.type,
            request.status,
            normalizeUpdatedBy(request.updatedBy)
        );

        return ResponseEntity.ok(ApiResponse.success(null));
    }

    /**
     * 更新Import索引
     *
     * @param jobId 任务ID
     * @param request 索引信息
     * @return 操作结果
     */
    @PostMapping("/context/imports")
    public ResponseEntity<ApiResponse<Void>> updateImportIndex(
            @PathVariable UUID jobId,
            @RequestBody UpdateImportIndexRequest request
    ) {
        log.info("API: 更新Import索引, jobId={}, type={}", jobId, request.type);

        planningFileService.updateImportIndex(
            jobId,
            request.type,
            request.imports,
            normalizeUpdatedBy(request.updatedBy)
        );

        return ResponseEntity.ok(ApiResponse.success(null));
    }

    /**
     * 添加类签名
     *
     * @param jobId 任务ID
     * @param request 签名信息
     * @return 操作结果
     */
    @PostMapping("/context/signature")
    public ResponseEntity<ApiResponse<Void>> addClassSignature(
            @PathVariable UUID jobId,
            @RequestBody AddClassSignatureRequest request
    ) {
        log.info("API: 添加类签名, jobId={}, class={}", jobId, request.className);

        planningFileService.addClassSignature(
            jobId,
            request.type,
            request.className,
            request.signature,
            normalizeUpdatedBy(request.updatedBy)
        );

        return ResponseEntity.ok(ApiResponse.success(null));
    }

    // ==================== 请求类 ====================

    public static class UpdateContentRequest {
        public String content;
        public String updatedBy;
    }

    public static class AppendContentRequest {
        public String content;
        public String updatedBy;
    }

    public static class UpdatePhaseRequest {
        public int phase;
        public boolean completed;
        public String updatedBy;
    }

    public static class AppendDecisionRequest {
        public String decision;
        public String reason;
        public String updatedBy;
    }

    public static class AppendErrorRequest {
        public String error;
        public String solution;
        public String updatedBy;
    }

    public static class AddGeneratedFileRequest {
        public String filePath;
        public String className;
        public String type;
        public String status;
        public String updatedBy;
    }

    public static class UpdateImportIndexRequest {
        public String type;
        public List<String> imports;
        public String updatedBy;
    }

    public static class AddClassSignatureRequest {
        public String type;
        public String className;
        public String signature;
        public String updatedBy;
    }

}
