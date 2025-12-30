package com.ingenio.backend.mapper.g3;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ingenio.backend.entity.g3.G3JobEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * G3引擎任务Mapper接口
 * 提供G3任务的数据访问操作
 */
@Mapper
public interface G3JobMapper extends BaseMapper<G3JobEntity> {

    /**
     * 根据用户ID分页查询任务列表
     *
     * @param page   分页参数
     * @param userId 用户ID
     * @return 任务分页结果
     */
    IPage<G3JobEntity> selectPageByUserId(Page<G3JobEntity> page, @Param("userId") UUID userId);

    /**
     * 根据租户ID分页查询任务列表
     *
     * @param page     分页参数
     * @param tenantId 租户ID
     * @return 任务分页结果
     */
    IPage<G3JobEntity> selectPageByTenantId(Page<G3JobEntity> page, @Param("tenantId") UUID tenantId);

    /**
     * 根据状态查询任务列表
     *
     * @param status 任务状态
     * @return 任务列表
     */
    List<G3JobEntity> selectByStatus(@Param("status") String status);

    /**
     * 根据租户ID和状态查询任务列表
     *
     * @param tenantId 租户ID
     * @param status   任务状态
     * @return 任务列表
     */
    List<G3JobEntity> selectByTenantIdAndStatus(@Param("tenantId") UUID tenantId, @Param("status") String status);

    /**
     * 查询正在运行的任务
     *
     * @return 运行中的任务列表
     */
    List<G3JobEntity> selectRunningJobs();

    /**
     * 查询用户正在运行的任务数量
     *
     * @param userId 用户ID
     * @return 运行中的任务数量
     */
    Integer selectRunningJobCountByUser(@Param("userId") UUID userId);

    /**
     * 更新任务状态
     *
     * @param jobId  任务ID
     * @param status 新状态
     * @return 更新影响的行数
     */
    Integer updateStatus(@Param("jobId") UUID jobId, @Param("status") String status);

    /**
     * 更新任务契约（OpenAPI + DB Schema）
     *
     * @param jobId        任务ID
     * @param contractYaml OpenAPI契约
     * @param dbSchemaSql  数据库Schema
     * @return 更新影响的行数
     */
    Integer updateContract(
            @Param("jobId") UUID jobId,
            @Param("contractYaml") String contractYaml,
            @Param("dbSchemaSql") String dbSchemaSql
    );

    /**
     * 锁定任务契约
     *
     * @param jobId 任务ID
     * @return 更新影响的行数
     */
    Integer lockContract(@Param("jobId") UUID jobId);

    /**
     * 更新沙箱信息
     *
     * @param jobId      任务ID
     * @param sandboxId  沙箱ID
     * @param sandboxUrl 沙箱URL
     * @return 更新影响的行数
     */
    Integer updateSandboxInfo(
            @Param("jobId") UUID jobId,
            @Param("sandboxId") String sandboxId,
            @Param("sandboxUrl") String sandboxUrl
    );

    /**
     * 进入下一轮修复
     *
     * @param jobId 任务ID
     * @return 更新影响的行数
     */
    Integer incrementRound(@Param("jobId") UUID jobId);

    /**
     * 记录任务错误
     *
     * @param jobId     任务ID
     * @param lastError 错误信息
     * @return 更新影响的行数
     */
    Integer recordError(@Param("jobId") UUID jobId, @Param("lastError") String lastError);

    /**
     * 标记任务完成
     *
     * @param jobId       任务ID
     * @param completedAt 完成时间
     * @return 更新影响的行数
     */
    Integer markCompleted(@Param("jobId") UUID jobId, @Param("completedAt") Instant completedAt);

    /**
     * 标记任务失败
     *
     * @param jobId       任务ID
     * @param lastError   错误信息
     * @param completedAt 完成时间
     * @return 更新影响的行数
     */
    Integer markFailed(
            @Param("jobId") UUID jobId,
            @Param("lastError") String lastError,
            @Param("completedAt") Instant completedAt
    );

    /**
     * 清理过期任务
     *
     * @param beforeTime 清理时间点之前的任务
     * @return 清理的任务数量
     */
    Integer deleteExpiredJobs(@Param("beforeTime") Instant beforeTime);

    /**
     * 查询超时未完成的任务
     *
     * @param timeout 超时时间点
     * @return 超时任务列表
     */
    List<G3JobEntity> selectTimedOutJobs(@Param("timeout") Instant timeout);
}
