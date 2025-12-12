package com.ingenio.backend.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.ingenio.backend.common.annotation.RequireOwnership;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ingenio.backend.common.exception.BusinessException;
import com.ingenio.backend.common.exception.ErrorCode;
import com.ingenio.backend.dto.request.GenerateFullRequest;
import com.ingenio.backend.dto.response.GenerateFullResponse;
import com.ingenio.backend.dto.response.TaskStatusResponse;
import com.ingenio.backend.entity.GenerationTaskEntity;
import com.ingenio.backend.mapper.GenerationTaskMapper;
import com.ingenio.backend.service.GenerateService;
import com.ingenio.backend.service.GenerationTaskStatusManager;
import com.ingenio.backend.service.IGenerationTaskService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * 生成任务服务实现类
 *
 * 核心功能：
 * 1. 异步任务创建和执行（使用@Async注解）
 * 2. Redis缓存 + 数据库持久化双写
 * 3. 任务状态实时更新和查询
 * 4. 支持任务取消（优雅中断）
 *
 * 设计模式：
 * - 采用"创建任务→异步执行→实时更新"的三阶段模式
 * - 参考BuildStatusManager的Redis缓存策略
 * - 异步任务使用CompletableFuture，支持链式编排
 *
 * @author Ingenio Team
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GenerationTaskServiceImpl implements IGenerationTaskService {

    private final GenerationTaskMapper taskMapper;
    private final GenerateService generateService;
    private final GenerationTaskStatusManager statusManager;

    /**
     * 存储正在运行的异步任务Future，用于取消
     */
    private final Map<UUID, CompletableFuture<Void>> runningTasks = new HashMap<>();

    /**
     * 租户ID（TODO: 从多租户上下文获取，当前硬编码）
     */
    private static final UUID DEFAULT_TENANT_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Override
    @Transactional(rollbackFor = Exception.class)
    public UUID createAsyncTask(GenerateFullRequest request) {
        log.info("创建异步生成任务 - userRequirement: {}", request.getUserRequirement());

        // 获取当前用户ID
        UUID userId = UUID.fromString(StpUtil.getLoginIdAsString());

        // 创建任务实体
        GenerationTaskEntity task = GenerationTaskEntity.builder()
                .id(UUID.randomUUID())
                .tenantId(DEFAULT_TENANT_ID)
                .userId(userId)
                .taskName(generateTaskName(request.getUserRequirement()))
                .userRequirement(request.getUserRequirement())
                .status(GenerationTaskEntity.Status.PENDING.getValue())
                .progress(0)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();

        // 保存到数据库
        taskMapper.insert(task);
        log.info("任务创建成功 - taskId: {}, userId: {}", task.getId(), userId);

        // 保存到Redis缓存
        statusManager.saveTaskStatus(task.getId(), task);

        // 提交异步任务执行
        CompletableFuture<Void> future = executeAsyncTask(task.getId(), request);
        runningTasks.put(task.getId(), future);

        // 异步任务完成后从runningTasks移除
        future.whenComplete((result, throwable) -> {
            runningTasks.remove(task.getId());
            if (throwable != null) {
                log.error("异步任务执行异常 - taskId: {}", task.getId(), throwable);
            } else {
                log.info("异步任务执行完成 - taskId: {}", task.getId());
            }
        });

        return task.getId();
    }

    @Override
    public TaskStatusResponse getTaskStatus(UUID taskId) {
        log.debug("查询任务状态 - taskId: {}", taskId);

        // 优先从Redis缓存读取
        GenerationTaskEntity task = statusManager.getTaskStatus(taskId);

        // 缓存miss，从数据库加载
        if (task == null) {
            log.debug("Redis缓存miss，从数据库加载 - taskId: {}", taskId);
            task = taskMapper.selectById(taskId);

            if (task == null) {
                log.warn("任务不存在 - taskId: {}", taskId);
                throw new BusinessException(ErrorCode.NOT_FOUND, "任务不存在");
            }

            // 写入Redis缓存
            statusManager.saveTaskStatus(taskId, task);
        }

        // 转换为DTO
        return convertToResponse(task);
    }

    @Override
    @RequireOwnership(resourceType = "generation_task", idParam = "taskId")
    @Transactional(rollbackFor = Exception.class)
    public void cancelTask(UUID taskId) {
        log.info("取消任务 - taskId: {}", taskId);

        // 查询任务
        GenerationTaskEntity task = getTaskEntity(taskId);

        // 检查是否可以取消
        if (!canCancelTask(task)) {
            String message = String.format("任务无法取消（当前状态：%s）", task.getStatus());
            log.warn("{} - taskId: {}", message, taskId);
            throw new BusinessException(ErrorCode.PARAM_ERROR.getCode(), message);
        }

        // 尝试取消正在运行的Future
        CompletableFuture<Void> future = runningTasks.get(taskId);
        if (future != null) {
            boolean cancelled = future.cancel(true);
            log.info("取消异步任务Future - taskId: {}, cancelled: {}", taskId, cancelled);
            runningTasks.remove(taskId);
        }

        // 更新任务状态为CANCELLED
        task.setStatus(GenerationTaskEntity.Status.CANCELLED.getValue());
        task.setCompletedAt(Instant.now());
        task.setUpdatedAt(Instant.now());

        // 更新数据库
        taskMapper.updateById(task);

        // 更新Redis缓存
        statusManager.saveTaskStatus(taskId, task);

        log.info("任务已取消 - taskId: {}", taskId);
    }

    @Override
    public IPage<TaskStatusResponse> getUserTasks(Integer pageNum, Integer pageSize, String status) {
        log.info("分页查询用户任务 - pageNum: {}, pageSize: {}, status: {}", pageNum, pageSize, status);

        // 获取当前用户ID
        UUID userId = UUID.fromString(StpUtil.getLoginIdAsString());

        // 构建分页参数
        Page<GenerationTaskEntity> page = new Page<>(pageNum, pageSize);

        // 构建查询条件
        QueryWrapper<GenerationTaskEntity> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("user_id", userId);
        queryWrapper.eq("tenant_id", DEFAULT_TENANT_ID);

        if (status != null && !status.isEmpty()) {
            queryWrapper.eq("status", status);
        }

        // 按创建时间倒序
        queryWrapper.orderByDesc("created_at");

        // 查询分页数据
        IPage<GenerationTaskEntity> entityPage = taskMapper.selectPage(page, queryWrapper);

        // 转换为DTO
        List<TaskStatusResponse> responses = entityPage.getRecords().stream()
                .map(this::convertToResponse)
                .collect(Collectors.toList());

        // 构建DTO分页结果
        Page<TaskStatusResponse> responsePage = new Page<>(pageNum, pageSize, entityPage.getTotal());
        responsePage.setRecords(responses);

        log.info("用户任务查询成功 - userId: {}, total: {}", userId, entityPage.getTotal());

        return responsePage;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateTaskStatus(UUID taskId, String status, String currentAgent, Integer progress) {
        log.debug("更新任务状态 - taskId: {}, status: {}, agent: {}, progress: {}",
                taskId, status, currentAgent, progress);

        // 更新数据库
        GenerationTaskEntity task = taskMapper.selectById(taskId);
        if (task == null) {
            log.warn("任务不存在 - taskId: {}", taskId);
            return;
        }

        if (status != null) {
            task.setStatus(status);
        }
        if (currentAgent != null) {
            task.setCurrentAgent(currentAgent);
        }
        if (progress != null) {
            task.setProgress(progress);
        }
        task.setUpdatedAt(Instant.now());

        // 如果任务完成，设置完成时间
        if (task.isFinished() && task.getCompletedAt() == null) {
            task.setCompletedAt(Instant.now());
        }

        taskMapper.updateById(task);

        // 更新Redis缓存
        statusManager.saveTaskStatus(taskId, task);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateTaskError(UUID taskId, String errorMessage) {
        log.error("记录任务错误 - taskId: {}, error: {}", taskId, errorMessage);

        // 更新数据库
        GenerationTaskEntity task = taskMapper.selectById(taskId);
        if (task == null) {
            log.warn("任务不存在 - taskId: {}", taskId);
            return;
        }

        task.setStatus(GenerationTaskEntity.Status.FAILED.getValue());
        task.setErrorMessage(errorMessage);
        task.setCompletedAt(Instant.now());
        task.setUpdatedAt(Instant.now());

        taskMapper.updateById(task);

        // 更新Redis缓存
        statusManager.updateTaskError(taskId, errorMessage);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateTaskResult(UUID taskId, UUID appSpecId, Integer qualityScore, String downloadUrl) {
        log.info("更新任务结果 - taskId: {}, appSpecId: {}, qualityScore: {}, downloadUrl: {}",
                taskId, appSpecId, qualityScore, downloadUrl);

        // 更新数据库
        GenerationTaskEntity task = taskMapper.selectById(taskId);
        if (task == null) {
            log.warn("任务不存在 - taskId: {}", taskId);
            return;
        }

        task.setAppSpecId(appSpecId);
        task.setQualityScore(qualityScore);
        task.setDownloadUrl(downloadUrl);
        task.setStatus(GenerationTaskEntity.Status.COMPLETED.getValue());
        task.setProgress(100);
        task.setCompletedAt(Instant.now());
        task.setUpdatedAt(Instant.now());

        taskMapper.updateById(task);

        // 更新Redis缓存
        statusManager.saveTaskStatus(taskId, task);
    }

    @Override
    public GenerationTaskEntity getTaskEntity(UUID taskId) {
        // 优先从Redis缓存读取
        GenerationTaskEntity task = statusManager.getTaskStatus(taskId);

        if (task == null) {
            // 从数据库加载
            task = taskMapper.selectById(taskId);

            if (task == null) {
                throw new BusinessException(ErrorCode.NOT_FOUND, "任务不存在");
            }

            // 写入Redis缓存
            statusManager.saveTaskStatus(taskId, task);
        }

        return task;
    }

    @Override
    public boolean canCancelTask(GenerationTaskEntity task) {
        // 只有PENDING和运行中的任务可以取消
        return task.getStatus().equals(GenerationTaskEntity.Status.PENDING.getValue())
                || task.isRunning();
    }

    /**
     * 异步执行生成任务
     *
     * 使用@Async注解，任务在独立线程池中执行
     *
     * @param taskId 任务ID
     * @param request 生成请求
     * @return CompletableFuture
     */
    @Async("generationTaskExecutor")
    protected CompletableFuture<Void> executeAsyncTask(UUID taskId, GenerateFullRequest request) {
        return CompletableFuture.runAsync(() -> {
            log.info("异步任务开始执行 - taskId: {}", taskId);

            try {
                // 更新任务状态：开始执行
                updateTaskStatus(taskId, GenerationTaskEntity.Status.PLANNING.getValue(),
                        GenerationTaskEntity.AgentType.PLAN.getValue(), 10);

                // 调用同步生成服务
                GenerateFullResponse response = generateService.generateFull(request);

                // 检查是否被取消
                if (Thread.currentThread().isInterrupted()) {
                    log.warn("异步任务被中断 - taskId: {}", taskId);
                    updateTaskStatus(taskId, GenerationTaskEntity.Status.CANCELLED.getValue(), null, 0);
                    return;
                }

                // 根据生成结果更新任务状态
                if ("completed".equals(response.getStatus())) {
                    updateTaskResult(
                            taskId,
                            response.getAppSpecId(),
                            response.getQualityScore(),
                            response.getCodeDownloadUrl()
                    );
                    log.info("异步任务执行成功 - taskId: {}, appSpecId: {}", taskId, response.getAppSpecId());
                } else {
                    updateTaskError(taskId, response.getErrorMessage() != null
                            ? response.getErrorMessage()
                            : "生成失败，状态：" + response.getStatus());
                    log.warn("异步任务执行失败 - taskId: {}, status: {}", taskId, response.getStatus());
                }

            } catch (Exception e) {
                log.error("异步任务执行异常 - taskId: {}", taskId, e);
                updateTaskError(taskId, "执行异常: " + e.getMessage());
            }
        });
    }

    /**
     * 将实体转换为响应DTO
     *
     * @param task 任务实体
     * @return 任务状态响应
     */
    private TaskStatusResponse convertToResponse(GenerationTaskEntity task) {
        // 获取状态描述
        String statusDescription;
        try {
            statusDescription = GenerationTaskEntity.Status.fromValue(task.getStatus()).getDescription();
        } catch (IllegalArgumentException e) {
            statusDescription = task.getStatus();
        }

        // 计算预估剩余时间（简单估算）
        Long estimatedRemainingSeconds = null;
        if (task.isRunning() && task.getStartedAt() != null) {
            long elapsedSeconds = java.time.Duration.between(
                    task.getStartedAt(),
                    Instant.now()
            ).getSeconds();

            // 假设总耗时 = (已用时间 / 当前进度) * 100
            if (task.getProgress() != null && task.getProgress() > 0) {
                long totalEstimated = (elapsedSeconds * 100) / task.getProgress();
                estimatedRemainingSeconds = totalEstimated - elapsedSeconds;
            }
        }

        // 构建Token使用信息（如果有）
        TaskStatusResponse.TokenUsageInfo tokenUsage = null;
        if (task.getTokenUsage() != null) {
            tokenUsage = TaskStatusResponse.TokenUsageInfo.builder()
                    .planTokens(getLongFromMap(task.getTokenUsage(), "planTokens"))
                    .executeTokens(getLongFromMap(task.getTokenUsage(), "executeTokens"))
                    .validateTokens(getLongFromMap(task.getTokenUsage(), "validateTokens"))
                    .totalTokens(getLongFromMap(task.getTokenUsage(), "totalTokens"))
                    .estimatedCost(getDoubleFromMap(task.getTokenUsage(), "estimatedCost"))
                    .build();
        }

        // 构建Agent状态列表（如果有）
        List<TaskStatusResponse.AgentStatusInfo> agents = null;
        if (task.getAgentsInfo() != null) {
            agents = buildAgentStatusList(task.getAgentsInfo());
        }

        return TaskStatusResponse.builder()
                .taskId(task.getId())
                .taskName(task.getTaskName())
                .userRequirement(task.getUserRequirement())
                .status(task.getStatus())
                .statusDescription(statusDescription)
                .currentAgent(task.getCurrentAgent())
                .progress(task.getProgress())
                .agents(agents)
                .startedAt(task.getStartedAt())
                .completedAt(task.getCompletedAt())
                .estimatedRemainingSeconds(estimatedRemainingSeconds)
                .appSpecId(task.getAppSpecId())
                .qualityScore(task.getQualityScore())
                .downloadUrl(task.getDownloadUrl())
                .previewUrl(task.getPreviewUrl())
                .tokenUsage(tokenUsage)
                .errorMessage(task.getErrorMessage())
                .createdAt(task.getCreatedAt())
                .updatedAt(task.getUpdatedAt())
                .build();
    }

    /**
     * 构建Agent状态列表
     *
     * @param agentsInfo Agent信息Map
     * @return Agent状态列表
     */
    @SuppressWarnings("unchecked")
    private List<TaskStatusResponse.AgentStatusInfo> buildAgentStatusList(Map<String, Object> agentsInfo) {
        List<TaskStatusResponse.AgentStatusInfo> agents = new ArrayList<>();

        for (Map.Entry<String, Object> entry : agentsInfo.entrySet()) {
            String agentType = entry.getKey();
            Map<String, Object> agentData = (Map<String, Object>) entry.getValue();

            agents.add(TaskStatusResponse.AgentStatusInfo.builder()
                    .agentType(agentType)
                    .agentName(getStringFromMap(agentData, "agentName"))
                    .status(getStringFromMap(agentData, "status"))
                    .statusDescription(getStringFromMap(agentData, "statusDescription"))
                    .startedAt(getLocalDateTimeFromMap(agentData, "startedAt"))
                    .completedAt(getLocalDateTimeFromMap(agentData, "completedAt"))
                    .durationMs(getLongFromMap(agentData, "durationMs"))
                    .progress(getIntegerFromMap(agentData, "progress"))
                    .resultSummary(getStringFromMap(agentData, "resultSummary"))
                    .errorMessage(getStringFromMap(agentData, "errorMessage"))
                    .metadata((Map<String, Object>) agentData.get("metadata"))
                    .build());
        }

        return agents;
    }

    /**
     * 生成任务名称
     *
     * 从用户需求中提取前20个字符作为任务名称
     *
     * @param userRequirement 用户需求
     * @return 任务名称
     */
    private String generateTaskName(String userRequirement) {
        if (userRequirement == null || userRequirement.isEmpty()) {
            return "生成任务";
        }

        String name = userRequirement.length() > 20
                ? userRequirement.substring(0, 20) + "..."
                : userRequirement;

        return name.replaceAll("\n", " ");
    }

    // ========== 辅助方法：从Map中安全获取值 ==========

    private String getStringFromMap(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value != null ? value.toString() : null;
    }

    private Integer getIntegerFromMap(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        return null;
    }

    private Long getLongFromMap(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        return null;
    }

    private Double getDoubleFromMap(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        return null;
    }

    private Instant getLocalDateTimeFromMap(Map<String, Object> map, String key) {
        Object value = map.get(key);
        if (value instanceof String) {
            return Instant.parse((String) value);
        } else if (value instanceof Instant) {
            return (Instant) value;
        }
        return null;
    }
}
