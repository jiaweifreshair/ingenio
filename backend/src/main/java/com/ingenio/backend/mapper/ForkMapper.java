package com.ingenio.backend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ingenio.backend.entity.ForkEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 派生关系Mapper接口
 * 提供派生关系表的数据库访问操作
 */
@Mapper
public interface ForkMapper extends BaseMapper<ForkEntity> {

    /**
     * 根据源项目ID查询所有派生记录
     *
     * @param sourceProjectId 源项目ID
     * @return 派生记录列表
     */
    @Select("SELECT * FROM forks WHERE source_project_id = #{sourceProjectId} ORDER BY created_at DESC")
    List<ForkEntity> findBySourceProjectId(@Param("sourceProjectId") UUID sourceProjectId);

    /**
     * 根据派生项目ID查询派生记录
     *
     * @param forkedProjectId 派生项目ID
     * @return 派生记录（Optional）
     */
    @Select("SELECT * FROM forks WHERE forked_project_id = #{forkedProjectId} LIMIT 1")
    Optional<ForkEntity> findByForkedProjectId(@Param("forkedProjectId") UUID forkedProjectId);

    /**
     * 查询用户是否已派生某项目
     *
     * @param sourceProjectId 源项目ID
     * @param userId 用户ID
     * @return 派生记录（Optional）
     */
    @Select("SELECT * FROM forks WHERE source_project_id = #{sourceProjectId} AND forked_by_user_id = #{userId} LIMIT 1")
    Optional<ForkEntity> findBySourceProjectIdAndUserId(
        @Param("sourceProjectId") UUID sourceProjectId,
        @Param("userId") UUID userId
    );

    /**
     * 根据用户ID查询所有派生记录
     *
     * @param userId 用户ID
     * @return 派生记录列表
     */
    @Select("SELECT * FROM forks WHERE forked_by_user_id = #{userId} ORDER BY created_at DESC")
    List<ForkEntity> findByUserId(@Param("userId") UUID userId);
}
