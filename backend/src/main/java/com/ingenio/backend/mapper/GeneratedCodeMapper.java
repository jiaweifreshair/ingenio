package com.ingenio.backend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ingenio.backend.entity.GeneratedCodeEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 生成代码Mapper接口
 * 提供生成代码记录表的数据库访问操作
 */
@Mapper
public interface GeneratedCodeMapper extends BaseMapper<GeneratedCodeEntity> {

    /**
     * 根据AppSpec ID查询生成代码记录
     *
     * @param appSpecId AppSpec ID
     * @return 生成代码实体
     */
    @Select("SELECT * FROM generated_code WHERE app_spec_id = #{appSpecId} ORDER BY created_at DESC LIMIT 1")
    Optional<GeneratedCodeEntity> findByAppSpecId(@Param("appSpecId") UUID appSpecId);

    /**
     * 根据项目ID查询生成代码记录
     *
     * @param projectId 项目ID
     * @return 生成代码实体
     */
    @Select("SELECT * FROM generated_code WHERE project_id = #{projectId} LIMIT 1")
    Optional<GeneratedCodeEntity> findByProjectId(@Param("projectId") String projectId);

    /**
     * 根据构建状态查询生成代码记录列表
     *
     * @param buildStatus 构建状态
     * @return 生成代码列表
     */
    @Select("SELECT * FROM generated_code WHERE build_status = #{buildStatus} ORDER BY created_at ASC")
    List<GeneratedCodeEntity> findByBuildStatus(@Param("buildStatus") String buildStatus);

    /**
     * 根据渲染器类型查询生成代码记录列表
     *
     * @param rendererType 渲染器类型
     * @return 生成代码列表
     */
    @Select("SELECT * FROM generated_code WHERE renderer_type = #{rendererType} ORDER BY created_at DESC")
    List<GeneratedCodeEntity> findByRendererType(@Param("rendererType") String rendererType);
}
