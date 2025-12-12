package com.ingenio.backend.controller;

import cn.dev33.satoken.annotation.SaCheckLogin;
import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ingenio.backend.common.Result;
import com.ingenio.backend.dto.request.GenerateFullRequest;
import com.ingenio.backend.dto.response.AnalysisProgressMessage;
import com.ingenio.backend.dto.response.GenerateFullResponse;
import com.ingenio.backend.dto.response.TaskListResponse;
import com.ingenio.backend.dto.response.TaskStatusResponse;
import com.ingenio.backend.service.GenerateService;
import com.ingenio.backend.service.IGenerationTaskService;
import com.ingenio.backend.service.NLRequirementAnalyzer;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

/**
 * 代码生成API控制器
 *
 * 功能：
 * 1. 完整生成流程（同步） - Plan → Execute → Validate → CodeGen
 * 2. 异步生成任务 - 创建任务并异步执行
 * 3. 任务状态查询 - 实时查询任务进度
 * 4. 任务取消 - 取消正在运行的任务
 * 5. 任务列表查询 - 分页查询用户所有任务
 *
 * 所有接口需要登录（除同步生成接口外）
 *
 * @author Ingenio Team
 * @since 1.0.0
 */
@Slf4j
@RestController
@RequestMapping("/v1/generate")
@RequiredArgsConstructor
public class GenerateController {

    private final GenerateService generateService;
    private final IGenerationTaskService generationTaskService;
    private final NLRequirementAnalyzer nlRequirementAnalyzer;
    private final ObjectMapper objectMapper;

    // 线程池用于异步执行分析任务
    private final ExecutorService executorService = Executors.newCachedThreadPool();

    /**
     * 完整生成流程（同步接口）
     *
     * 请求示例：
     * {
     *   "userRequirement": "创建一个校园二手交易平台，支持商品发布、搜索、聊天和交易评价",
     *   "skipValidation": false,
     *   "qualityThreshold": 70,
     *   "generatePreview": false
     * }
     *
     * 响应示例：
     * {
     *   "code": 200,
     *   "message": "成功",
     *   "data": {
     *     "appSpecId": "550e8400-e29b-41d4-a716-446655440000",
     *     "planResult": { ... },
     *     "validateResult": { ... },
     *     "isValid": true,
     *     "qualityScore": 85,
     *     "status": "completed",
     *     "durationMs": 12345,
     *     "generatedAt": "2025-11-09 10:30:00"
     *   }
     * }
     *
     * @param request 生成请求
     * @return 生成结果
     */
    @PostMapping("/full")
    public Result<GenerateFullResponse> generateFull(@Valid @RequestBody GenerateFullRequest request) {
        log.info("收到完整生成请求 - userRequirement: {}", request.getUserRequirement());

        try {
            GenerateFullResponse response = generateService.generateFull(request);
            return Result.success(response);
        } catch (IllegalArgumentException e) {
            log.warn("生成请求参数错误: {}", e.getMessage());
            return Result.error(e.getMessage());
        } catch (Exception e) {
            log.error("完整生成失败", e);
            return Result.error("生成失败: " + e.getMessage());
        }
    }

    /**
     * 创建异步生成任务
     *
     * 适用场景：
     * - 用户需要生成较大规模的应用（预计耗时>30秒）
     * - 前端需要展示实时进度（轮询getTaskStatus接口）
     * - 避免同步接口超时
     *
     * 请求示例：
     * {
     *   "userRequirement": "创建一个校园二手交易平台，支持商品发布、搜索、聊天和交易评价",
     *   "skipValidation": false,
     *   "qualityThreshold": 70,
     *   "generatePreview": true
     * }
     *
     * 响应示例：
     * {
     *   "code": 200,
     *   "message": "成功",
     *   "data": "550e8400-e29b-41d4-a716-446655440000"
     * }
     *
     * @param request 生成请求
     * @return 任务ID
     */
    @PostMapping("/async")
    @SaCheckLogin
    public Result<String> createAsyncTask(@Valid @RequestBody GenerateFullRequest request) {
        log.info("收到异步生成请求 - userRequirement: {}", request.getUserRequirement());

        try {
            UUID taskId = generationTaskService.createAsyncTask(request);
            return Result.success(taskId.toString());
        } catch (IllegalArgumentException e) {
            log.warn("异步生成请求参数错误: {}", e.getMessage());
            return Result.error(e.getMessage());
        } catch (Exception e) {
            log.error("创建异步任务失败", e);
            return Result.error("创建任务失败: " + e.getMessage());
        }
    }

    /**
     * 查询任务状态
     *
     * 前端建议轮询间隔：
     * - 任务运行中：每2秒轮询一次
     * - 任务完成/失败：停止轮询
     *
     * 响应示例：
     * {
     *   "code": 200,
     *   "message": "成功",
     *   "data": {
     *     "taskId": "550e8400-e29b-41d4-a716-446655440000",
     *     "taskName": "校园二手交易平台...",
     *     "status": "executing",
     *     "statusDescription": "AppSpec生成中",
     *     "currentAgent": "execute",
     *     "progress": 45,
     *     "agents": [
     *       {
     *         "agentType": "plan",
     *         "status": "completed",
     *         "progress": 100,
     *         "durationMs": 5234
     *       },
     *       {
     *         "agentType": "execute",
     *         "status": "running",
     *         "progress": 45,
     *         "durationMs": null
     *       }
     *     ],
     *     "startedAt": "2025-11-09T10:30:00",
     *     "completedAt": null,
     *     "estimatedRemainingSeconds": 120,
     *     "appSpecId": null,
     *     "qualityScore": null,
     *     "downloadUrl": null,
     *     "errorMessage": null,
     *     "createdAt": "2025-11-09T10:30:00",
     *     "updatedAt": "2025-11-09T10:32:15"
     *   }
     * }
     *
     * @param taskId 任务ID
     * @return 任务状态
     */
    @GetMapping("/status/{taskId}")
    @SaCheckLogin
    public Result<TaskStatusResponse> getTaskStatus(@PathVariable String taskId) {
        log.info("查询任务状态 - taskId: {}", taskId);

        try {
            UUID taskUUID = UUID.fromString(taskId);
            TaskStatusResponse response = generationTaskService.getTaskStatus(taskUUID);
            return Result.success(response);
        } catch (IllegalArgumentException e) {
            log.warn("任务ID格式错误 - taskId: {}", taskId);
            return Result.error("任务ID格式错误");
        }
        // BusinessException会被GlobalExceptionHandler自动处理，返回正确的业务错误码
    }

    /**
     * 取消任务
     *
     * 适用场景：
     * - 用户主动取消正在运行的任务
     * - 任务运行时间过长，用户希望重新生成
     *
     * 限制条件：
     * - 只能取消状态为PENDING、PLANNING、EXECUTING、VALIDATING的任务
     * - 已完成（COMPLETED）、已失败（FAILED）、已取消（CANCELLED）的任务无法取消
     *
     * 响应示例：
     * {
     *   "code": 200,
     *   "message": "成功",
     *   "data": "任务已取消"
     * }
     *
     * @param taskId 任务ID
     * @return 成功消息
     */
    @PostMapping("/cancel/{taskId}")
    @SaCheckLogin
    public Result<String> cancelTask(@PathVariable String taskId) {
        log.info("取消任务 - taskId: {}", taskId);

        try {
            UUID taskUUID = UUID.fromString(taskId);
            generationTaskService.cancelTask(taskUUID);
            return Result.success("任务已取消");
        } catch (IllegalArgumentException e) {
            log.warn("取消任务失败 - taskId: {}, error: {}", taskId, e.getMessage());
            return Result.error(e.getMessage());
        }
        // BusinessException会被GlobalExceptionHandler自动处理，返回正确的业务错误码
    }

    /**
     * 获取用户任务列表
     *
     * 支持分页和状态筛选
     *
     * 查询参数：
     * - pageNum: 页码（从1开始，默认1）
     * - pageSize: 页大小（默认10，最大100）
     * - status: 任务状态（可选，不传则查询所有状态）
     *   可选值：pending, planning, executing, validating, generating, completed, failed, cancelled
     *
     * 响应示例：
     * {
     *   "code": 200,
     *   "message": "成功",
     *   "data": {
     *     "total": 25,
     *     "pageNum": 1,
     *     "pageSize": 10,
     *     "pages": 3,
     *     "tasks": [
     *       {
     *         "taskId": "550e8400-e29b-41d4-a716-446655440000",
     *         "taskName": "校园二手交易平台...",
     *         "status": "completed",
     *         "statusDescription": "任务完成",
     *         "progress": 100,
     *         "currentAgent": "generate",
     *         "appSpecId": "660e8400-e29b-41d4-a716-446655440001",
     *         "qualityScore": 85,
     *         "downloadUrl": "https://minio.example.com/...",
     *         "errorMessage": null,
     *         "createdAt": "2025-11-09T10:30:00",
     *         "completedAt": "2025-11-09T10:35:00",
     *         "updatedAt": "2025-11-09T10:35:00"
     *       },
     *       ...
     *     ]
     *   }
     * }
     *
     * @param pageNum 页码
     * @param pageSize 页大小
     * @param status 任务状态（可选）
     * @return 任务列表
     */
    @GetMapping("/tasks")
    @SaCheckLogin
    public Result<TaskListResponse> getUserTasks(
            @RequestParam(defaultValue = "1") Integer pageNum,
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) String status
    ) {
        log.info("查询用户任务列表 - pageNum: {}, pageSize: {}, status: {}", pageNum, pageSize, status);

        try {
            // 限制页大小
            if (pageSize > 100) {
                pageSize = 100;
            }

            // 查询分页数据
            IPage<TaskStatusResponse> page = generationTaskService.getUserTasks(pageNum, pageSize, status);

            // 转换为TaskListResponse
            List<TaskListResponse.TaskItem> taskItems = page.getRecords().stream()
                    .map(this::convertToTaskItem)
                    .collect(Collectors.toList());

            TaskListResponse response = TaskListResponse.builder()
                    .total(page.getTotal())
                    .pageNum((int) page.getCurrent())
                    .pageSize((int) page.getSize())
                    .pages(page.getPages())
                    .tasks(taskItems)
                    .build();

            return Result.success(response);
        } catch (Exception e) {
            log.error("查询任务列表失败", e);
            return Result.error("查询失败: " + e.getMessage());
        }
    }

    /**
     * 流式分析需求（SSE接口）
     *
     * 功能：
     * - 实时推送需求分析的5个步骤进度
     * - 使用Server-Sent Events (SSE)技术
     * - 前端通过EventSource订阅
     *
     * 请求示例：
     * POST /v1/generate/analyze-stream
     * {
     *   "requirement": "创建一个智能健康管理应用，支持体重记录、运动打卡、饮食分析"
     * }
     *
     * SSE响应示例：
     * event: progress
     * data: {"step":1,"stepName":"需求解析","status":"RUNNING","progress":0,"description":"正在调用AI模型理解你的需求..."}
     *
     * event: progress
     * data: {"step":1,"stepName":"需求解析","status":"COMPLETED","progress":20,"description":"AI已成功理解你的需求"}
     *
     * event: progress
     * data: {"step":2,"stepName":"实体建模","status":"RUNNING","progress":20,"description":"正在提取数据模型和实体..."}
     *
     * ...
     *
     * event: complete
     * data: {"requirementId":"550e8400-e29b-41d4-a716-446655440000","message":"分析完成"}
     *
     * @param request 包含需求描述的请求对象
     * @return SSE Emitter
     */
    @PostMapping(value = "/analyze-stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter analyzeRequirementStream(@Valid @RequestBody AnalyzeRequirementRequest request) {
        log.info("收到流式分析请求 - requirement: {}", request.getRequirement());

        // 创建SSE Emitter（超时时间5分钟）
        SseEmitter emitter = new SseEmitter(300_000L);
        log.info("[SSE] 创建SSE连接，超时时间: 300秒");

        // V2.0优化：SSE端点已加入白名单，允许匿名访问
        // 意图分析作为产品核心功能，应该允许未登录用户体验
        // 注释掉登录检查，与白名单配置保持一致
        /*
        // 手动检查登录状态（SSE端点已从拦截器白名单中排除，需内部验证）
        // 这样可以返回SSE格式的错误消息，而非JSON格式的401响应
        if (!StpUtil.isLogin()) {
            log.warn("SSE分析请求未登录");
            try {
                emitter.send(SseEmitter.event()
                        .name("error")
                        .data("{\"error\":\"未登录或登录已失效，请先登录\"}"));
                emitter.complete();
            } catch (IOException e) {
                log.error("SSE发送未登录错误失败", e);
                emitter.completeWithError(e);
            }
            return emitter;
        }
        */

        // 异步执行分析任务
        executorService.execute(() -> {
            try {
                // 调用流式分析方法，每个进度通过SSE推送
                nlRequirementAnalyzer.analyzeWithProgress(
                        request.getRequirement(),
                        progressMessage -> {
                            try {
                                // 将AnalysisProgressMessage转换为JSON并通过SSE发送
                                String json = objectMapper.writeValueAsString(progressMessage);
                                emitter.send(SseEmitter.event()
                                        .name("progress")
                                        .data(json));

                                log.debug("SSE推送进度: step={}, status={}, progress={}",
                                        progressMessage.getStep(),
                                        progressMessage.getStatus(),
                                        progressMessage.getProgress());

                            } catch (IOException e) {
                                log.error("SSE发送进度消息失败", e);
                                emitter.completeWithError(e);
                            }
                        }
                );

                // 分析完成，发送完成事件
                emitter.send(SseEmitter.event()
                        .name("complete")
                        .data("{\"message\":\"分析完成\"}"));
                emitter.complete();

                log.info("[SSE] 流式分析完成，连接正常关闭");

            } catch (Exception e) {
                log.error("流式分析失败", e);
                try {
                    emitter.send(SseEmitter.event()
                            .name("error")
                            .data("{\"error\":\"" + e.getMessage() + "\"}"));
                } catch (IOException ioException) {
                    log.error("SSE发送错误消息失败", ioException);
                }
                emitter.completeWithError(e);
            }
        });

        // 设置超时和错误处理
        emitter.onTimeout(() -> {
            log.warn("[SSE] 连接超时（300秒），自动关闭连接");
            emitter.complete();
        });

        emitter.onError(throwable -> {
            log.error("[SSE] 连接发生错误，错误类型: {}", throwable.getClass().getSimpleName(), throwable);
            emitter.completeWithError(throwable);
        });

        return emitter;
    }

    /**
     * 流式分析请求DTO
     */
    @lombok.Data
    public static class AnalyzeRequirementRequest {
        /**
         * 自然语言需求描述
         */
        @jakarta.validation.constraints.NotBlank(message = "需求描述不能为空")
        @jakarta.validation.constraints.Size(min = 10, max = 2000, message = "需求描述长度必须在10-2000字符之间")
        private String requirement;
    }

    /**
     * 将TaskStatusResponse转换为TaskItem（简化版）
     *
     * @param response 任务状态响应
     * @return 任务项
     */
    private TaskListResponse.TaskItem convertToTaskItem(TaskStatusResponse response) {
        return TaskListResponse.TaskItem.builder()
                .taskId(response.getTaskId())
                .taskName(response.getTaskName())
                .status(response.getStatus())
                .statusDescription(response.getStatusDescription())
                .progress(response.getProgress())
                .currentAgent(response.getCurrentAgent())
                .appSpecId(response.getAppSpecId())
                .qualityScore(response.getQualityScore())
                .downloadUrl(response.getDownloadUrl())
                .errorMessage(response.getErrorMessage())
                .createdAt(response.getCreatedAt())
                .completedAt(response.getCompletedAt())
                .updatedAt(response.getUpdatedAt())
                .build();
    }
}
