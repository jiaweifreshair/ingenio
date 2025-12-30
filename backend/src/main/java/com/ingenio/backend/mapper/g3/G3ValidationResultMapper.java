package com.ingenio.backend.mapper.g3;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ingenio.backend.entity.g3.G3ValidationResultEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.UUID;

/**
 * G3引擎验证结果Mapper接口
 * 提供验证结果的数据访问操作
 */
@Mapper
public interface G3ValidationResultMapper extends BaseMapper<G3ValidationResultEntity> {

    /**
     * 根据任务ID查询所有验证结果
     *
     * @param jobId 任务ID
     * @return 验证结果列表
     */
    List<G3ValidationResultEntity> selectByJobId(@Param("jobId") UUID jobId);

    /**
     * 根据任务ID和轮次查询验证结果
     *
     * @param jobId 任务ID
     * @param round 验证轮次
     * @return 验证结果列表
     */
    List<G3ValidationResultEntity> selectByJobIdAndRound(
            @Param("jobId") UUID jobId,
            @Param("round") Integer round
    );

    /**
     * 查询任务最新一轮的验证结果
     *
     * @param jobId 任务ID
     * @return 最新验证结果列表
     */
    List<G3ValidationResultEntity> selectLatestByJobId(@Param("jobId") UUID jobId);

    /**
     * 根据任务ID和验证类型查询
     *
     * @param jobId          任务ID
     * @param validationType 验证类型
     * @return 验证结果列表
     */
    List<G3ValidationResultEntity> selectByJobIdAndType(
            @Param("jobId") UUID jobId,
            @Param("validationType") String validationType
    );

    /**
     * 查询任务中失败的验证结果
     *
     * @param jobId 任务ID
     * @return 失败的验证结果列表
     */
    List<G3ValidationResultEntity> selectFailedByJobId(@Param("jobId") UUID jobId);

    /**
     * 查询某轮次失败的验证结果
     *
     * @param jobId 任务ID
     * @param round 验证轮次
     * @return 失败的验证结果列表
     */
    List<G3ValidationResultEntity> selectFailedByJobIdAndRound(
            @Param("jobId") UUID jobId,
            @Param("round") Integer round
    );

    /**
     * 检查任务某轮次是否全部通过
     *
     * @param jobId 任务ID
     * @param round 验证轮次
     * @return 是否全部通过
     */
    Boolean isAllPassedByJobIdAndRound(
            @Param("jobId") UUID jobId,
            @Param("round") Integer round
    );

    /**
     * 统计任务验证通过次数
     *
     * @param jobId 任务ID
     * @return 通过次数
     */
    Integer countPassedByJobId(@Param("jobId") UUID jobId);

    /**
     * 统计任务验证失败次数
     *
     * @param jobId 任务ID
     * @return 失败次数
     */
    Integer countFailedByJobId(@Param("jobId") UUID jobId);

    /**
     * 删除任务的所有验证结果
     *
     * @param jobId 任务ID
     * @return 删除的行数
     */
    Integer deleteByJobId(@Param("jobId") UUID jobId);
}
