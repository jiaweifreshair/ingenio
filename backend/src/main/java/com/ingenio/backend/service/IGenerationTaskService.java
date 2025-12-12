package com.ingenio.backend.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.ingenio.backend.dto.request.GenerateFullRequest;
import com.ingenio.backend.dto.response.TaskStatusResponse;
import com.ingenio.backend.entity.GenerationTaskEntity;

import java.util.UUID;

/**
 * 生成任务服务接口
 *
 * 功能：
 * - 创建异步生成任务
 * - 查询任务状态（支持Redis缓存）
 * - 取消正在运行的任务
 * - 分页查询用户任务列表
 * - 更新任务状态和进度
 *
 * @author Ingenio Team
 * @since 1.0.0
 */
public interface IGenerationTaskService {

    /**
     * 创建异步生成任务
     *
     * 流程：
     * 1. 创建数据库任务记录（状态：PENDING）
     * 2. 提交到异步线程池执行
     * 3. 返回任务ID供前端轮询
     *
     * @param request 生成请求
     * @return 任务ID
     */
    UUID createAsyncTask(GenerateFullRequest request);

    /**
     * 查询任务状态
     *
     * 优先从Redis缓存读取，如果缓存miss则从数据库加载
     *
     * @param taskId 任务ID
     * @return 任务状态响应
     */
    TaskStatusResponse getTaskStatus(UUID taskId);

    /**
     * 取消任务
     *
     * 仅能取消正在运行或待执行的任务，已完成/已失败的任务无法取消
     *
     * @param taskId 任务ID
     * @throws IllegalArgumentException 当任务不存在或不可取消时抛出
     */
    void cancelTask(UUID taskId);

    /**
     * 分页查询用户任务列表
     *
     * 按创建时间倒序排列，支持状态筛选
     *
     * @param pageNum 页码（从1开始）
     * @param pageSize 页大小
     * @param status 任务状态（可选，不传则查询所有状态）
     * @return 任务分页结果
     */
    IPage<TaskStatusResponse> getUserTasks(Integer pageNum, Integer pageSize, String status);

    /**
     * 更新任务状态和进度
     *
     * 同时更新数据库和Redis缓存
     *
     * @param taskId 任务ID
     * @param status 新状态
     * @param currentAgent 当前Agent
     * @param progress 进度（0-100）
     */
    void updateTaskStatus(UUID taskId, String status, String currentAgent, Integer progress);

    /**
     * 更新任务错误信息
     *
     * @param taskId 任务ID
     * @param errorMessage 错误信息
     */
    void updateTaskError(UUID taskId, String errorMessage);

    /**
     * 更新任务最终结果
     *
     * @param taskId 任务ID
     * @param appSpecId AppSpec ID
     * @param qualityScore 质量评分
     * @param downloadUrl 下载链接
     */
    void updateTaskResult(UUID taskId, UUID appSpecId, Integer qualityScore, String downloadUrl);

    /**
     * 查询任务实体（不转换为DTO）
     *
     * @param taskId 任务ID
     * @return 任务实体
     */
    GenerationTaskEntity getTaskEntity(UUID taskId);

    /**
     * 检查任务是否可以取消
     *
     * @param task 任务实体
     * @return 是否可以取消
     */
    boolean canCancelTask(GenerationTaskEntity task);
}
