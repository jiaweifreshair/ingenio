package com.ingenio.backend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ingenio.backend.entity.GenerationTaskEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 生成任务Mapper接口
 * 提供生成任务的数据访问操作
 */
@Mapper
public interface GenerationTaskMapper extends BaseMapper<GenerationTaskEntity> {

    /**
     * 根据租户ID和任务ID查询任务
     *
     * @param tenantId 租户ID
     * @param taskId 任务ID
     * @return 生成任务实体
     */
    GenerationTaskEntity selectByTenantIdAndTaskId(@Param("tenantId") UUID tenantId, @Param("taskId") UUID taskId);

    /**
     * 根据用户ID分页查询任务列表
     *
     * @param page 分页参数
     * @param userId 用户ID
     * @return 任务分页结果
     */
    IPage<GenerationTaskEntity> selectPageByUserId(Page<GenerationTaskEntity> page, @Param("userId") UUID userId);

    /**
     * 根据租户ID分页查询任务列表
     *
     * @param page 分页参数
     * @param tenantId 租户ID
     * @return 任务分页结果
     */
    IPage<GenerationTaskEntity> selectPageByTenantId(Page<GenerationTaskEntity> page, @Param("tenantId") UUID tenantId);

    /**
     * 根据状态查询任务列表
     *
     * @param tenantId 租户ID
     * @param status 任务状态
     * @return 任务列表
     */
    List<GenerationTaskEntity> selectByStatus(@Param("tenantId") UUID tenantId, @Param("status") String status);

    /**
     * 查询指定时间范围内的任务
     *
     * @param tenantId 租户ID
     * @param startTime 开始时间
     * @param endTime 结束时间
     * @return 任务列表
     */
    List<GenerationTaskEntity> selectByTimeRange(@Param("tenantId") UUID tenantId,
                                                @Param("startTime") LocalDateTime startTime,
                                                @Param("endTime") LocalDateTime endTime);

    /**
     * 统计用户任务数量（按状态分组）
     *
     * @param tenantId 租户ID
     * @param userId 用户ID
     * @return 状态统计结果
     */
    List<Map<String, Object>> selectTaskStatusCount(@Param("tenantId") UUID tenantId, @Param("userId") UUID userId);

    /**
     * 查询正在运行的任务数量
     *
     * @param tenantId 租户ID
     * @return 运行中的任务数量
     */
    Integer selectRunningTaskCount(@Param("tenantId") UUID tenantId);

    /**
     * 更新任务状态和进度
     *
     * @param taskId 任务ID
     * @param status 新状态
     * @param currentAgent 当前Agent
     * @param progress 进度
     * @return 更新影响的行数
     */
    Integer updateStatusAndProgress(@Param("taskId") UUID taskId,
                                   @Param("status") String status,
                                   @Param("currentAgent") String currentAgent,
                                   @Param("progress") Integer progress);

    /**
     * 更新任务执行结果
     *
     * @param taskId 任务ID
     * @param planResult 规划结果
     * @param appSpecContent AppSpec内容
     * @param validateResult 验证结果
     * @return 更新影响的行数
     */
    Integer updateExecutionResult(@Param("taskId") UUID taskId,
                                 @Param("planResult") Map<String, Object> planResult,
                                 @Param("appSpecContent") Map<String, Object> appSpecContent,
                                 @Param("validateResult") Map<String, Object> validateResult);

    /**
     * 更新任务最终结果
     *
     * @param taskId 任务ID
     * @param appSpecId AppSpec ID
     * @param qualityScore 质量评分
     * @param downloadUrl 下载链接
     * @param previewUrl 预览链接
     * @param tokenUsage Token使用统计
     * @return 更新影响的行数
     */
    Integer updateFinalResult(@Param("taskId") UUID taskId,
                             @Param("appSpecId") UUID appSpecId,
                             @Param("qualityScore") Integer qualityScore,
                             @Param("downloadUrl") String downloadUrl,
                             @Param("previewUrl") String previewUrl,
                             @Param("tokenUsage") Map<String, Object> tokenUsage);

    /**
     * 记录任务错误信息
     *
     * @param taskId 任务ID
     * @param status 失败状态
     * @param errorMessage 错误信息
     * @return 更新影响的行数
     */
    Integer recordTaskError(@Param("taskId") UUID taskId,
                           @Param("status") String status,
                           @Param("errorMessage") String errorMessage);

    /**
     * 清理过期任务
     *
     * @param tenantId 租户ID
     * @param beforeTime 清理时间点之前的任务
     * @return 清理的任务数量
     */
    Integer deleteExpiredTasks(@Param("tenantId") UUID tenantId, @Param("beforeTime") LocalDateTime beforeTime);
}