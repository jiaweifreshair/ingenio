package com.ingenio.backend.mapper.g3;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ingenio.backend.entity.g3.G3ArtifactEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.UUID;

/**
 * G3引擎产物Mapper接口
 * 提供G3产物的数据访问操作
 */
@Mapper
public interface G3ArtifactMapper extends BaseMapper<G3ArtifactEntity> {

    /**
     * 根据任务ID查询所有产物
     *
     * @param jobId 任务ID
     * @return 产物列表
     */
    List<G3ArtifactEntity> selectByJobId(@Param("jobId") UUID jobId);

    /**
     * 根据任务ID和轮次查询产物
     *
     * @param jobId           任务ID
     * @param generationRound 生成轮次
     * @return 产物列表
     */
    List<G3ArtifactEntity> selectByJobIdAndRound(
            @Param("jobId") UUID jobId,
            @Param("generationRound") Integer generationRound
    );

    /**
     * 根据任务ID查询最新版本的产物
     *
     * @param jobId 任务ID
     * @return 最新版本产物列表
     */
    List<G3ArtifactEntity> selectLatestByJobId(@Param("jobId") UUID jobId);

    /**
     * 根据任务ID和产物类型查询
     *
     * @param jobId        任务ID
     * @param artifactType 产物类型
     * @return 产物列表
     */
    List<G3ArtifactEntity> selectByJobIdAndType(
            @Param("jobId") UUID jobId,
            @Param("artifactType") String artifactType
    );

    /**
     * 根据任务ID和文件路径查询产物
     *
     * @param jobId    任务ID
     * @param filePath 文件路径
     * @return 产物实体
     */
    G3ArtifactEntity selectByJobIdAndFilePath(
            @Param("jobId") UUID jobId,
            @Param("filePath") String filePath
    );

    /**
     * 根据任务ID和文件路径查询最新版本
     *
     * @param jobId    任务ID
     * @param filePath 文件路径
     * @return 最新版本产物
     */
    G3ArtifactEntity selectLatestByJobIdAndFilePath(
            @Param("jobId") UUID jobId,
            @Param("filePath") String filePath
    );

    /**
     * 查询有错误的产物
     *
     * @param jobId 任务ID
     * @return 有错误的产物列表
     */
    List<G3ArtifactEntity> selectWithErrors(@Param("jobId") UUID jobId);

    /**
     * 查询某轮次有错误的产物
     *
     * @param jobId           任务ID
     * @param generationRound 生成轮次
     * @return 有错误的产物列表
     */
    List<G3ArtifactEntity> selectWithErrorsByRound(
            @Param("jobId") UUID jobId,
            @Param("generationRound") Integer generationRound
    );

    /**
     * 标记产物有错误
     *
     * @param artifactId     产物ID
     * @param compilerOutput 编译器输出
     * @return 更新影响的行数
     */
    Integer markError(
            @Param("artifactId") UUID artifactId,
            @Param("compilerOutput") String compilerOutput
    );

    /**
     * 标记产物验证通过
     *
     * @param artifactId 产物ID
     * @return 更新影响的行数
     */
    Integer markValid(@Param("artifactId") UUID artifactId);

    /**
     * 批量标记产物验证通过
     *
     * @param artifactIds 产物ID列表
     * @return 更新影响的行数
     */
    Integer batchMarkValid(@Param("artifactIds") List<UUID> artifactIds);

    /**
     * 批量插入产物
     *
     * @param artifacts 产物列表
     * @return 插入的行数
     */
    Integer batchInsert(@Param("artifacts") List<G3ArtifactEntity> artifacts);

    /**
     * 删除任务的所有产物
     *
     * @param jobId 任务ID
     * @return 删除的行数
     */
    Integer deleteByJobId(@Param("jobId") UUID jobId);

    /**
     * 统计任务产物数量
     *
     * @param jobId 任务ID
     * @return 产物数量
     */
    Integer countByJobId(@Param("jobId") UUID jobId);

    /**
     * 统计任务错误产物数量
     *
     * @param jobId 任务ID
     * @return 错误产物数量
     */
    Integer countErrorsByJobId(@Param("jobId") UUID jobId);

    /**
     * 查询产物的修复历史链
     *
     * @param artifactId 当前产物ID
     * @return 修复历史链（从旧到新）
     */
    List<G3ArtifactEntity> selectRepairHistory(@Param("artifactId") UUID artifactId);
}
