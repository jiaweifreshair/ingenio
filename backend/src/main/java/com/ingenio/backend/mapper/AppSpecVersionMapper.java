package com.ingenio.backend.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ingenio.backend.entity.AppSpecVersionEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.UUID;

/**
 * AppSpec版本Mapper接口
 * 提供AppSpec版本表的数据库访问操作（时光机调试核心）
 */
@Mapper
public interface AppSpecVersionMapper extends BaseMapper<AppSpecVersionEntity> {

    /**
     * 根据AppSpec ID查询所有版本（按版本号降序）
     *
     * @param appSpecId AppSpec ID
     * @return 版本列表
     */
    @Select("SELECT * FROM app_spec_versions WHERE app_spec_id = #{appSpecId} ORDER BY version_number DESC")
    List<AppSpecVersionEntity> findByAppSpecIdOrderByVersionDesc(@Param("appSpecId") UUID appSpecId);

    /**
     * 根据AppSpec ID和版本号查询版本
     *
     * @param appSpecId AppSpec ID
     * @param versionNumber 版本号
     * @return 版本实体
     */
    @Select("SELECT * FROM app_spec_versions WHERE app_spec_id = #{appSpecId} AND version_number = #{versionNumber} LIMIT 1")
    AppSpecVersionEntity findByAppSpecIdAndVersionNumber(
        @Param("appSpecId") UUID appSpecId,
        @Param("versionNumber") Integer versionNumber
    );

    /**
     * 查询AppSpec的最新版本
     *
     * @param appSpecId AppSpec ID
     * @return 最新版本实体
     */
    @Select("SELECT * FROM app_spec_versions WHERE app_spec_id = #{appSpecId} ORDER BY version_number DESC LIMIT 1")
    AppSpecVersionEntity findLatestByAppSpecId(@Param("appSpecId") UUID appSpecId);

    /**
     * 根据变更类型查询版本列表
     *
     * @param appSpecId AppSpec ID
     * @param changeType 变更类型
     * @return 版本列表
     */
    @Select("SELECT * FROM app_spec_versions WHERE app_spec_id = #{appSpecId} AND change_type = #{changeType} ORDER BY created_at DESC")
    List<AppSpecVersionEntity> findByAppSpecIdAndChangeType(
        @Param("appSpecId") UUID appSpecId,
        @Param("changeType") String changeType
    );
}
